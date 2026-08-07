package net.libxray

import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import libXray.LibXray
import libXray.DialerController
import hev.sockstun.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class XrayVpnService : VpnService() {
    private val xrayDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var vpnJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + vpnJob)

    private var vpnPfd: ParcelFileDescriptor? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "vpn_service_channel"

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var dialerController: AndroidDialerController? = null

    private var cachedConfigJsonString: String? = null

    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        }
        public fun stopXray() {
            val stopRequest = InvokeRequest(
                method = XrayMethod.STOP_XRAY,
                payload = ""
            )
            val response = LibXray.invoke(json.encodeToString(stopRequest))
            val responseObj = JSONObject(response)

            if(responseObj.getBoolean("success") == false)
                Log.e("XrayVpnService", responseObj.getString("error"))

            LibXray.resetDNS()
                    
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }
        }
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                if (currentNetwork == null) {
                    currentNetwork = network
                    return
                }

                if (currentNetwork != network) {
                    currentNetwork = network

                    MainScope().launch(Dispatchers.Main) {
                        handleNetworkChange()
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (network == currentNetwork) {
                    currentNetwork = null
                }
            }
        }

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun handleNetworkChange(): Unit = withContext(Dispatchers.IO) {
        try {
            stopSelf()

            if(!cachedConfigJsonString.isNullOrBlank())
                startVpn(cachedConfigJsonString!!)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnPfd != null) return 0
        if (intent == null) return 0

        val action = intent?.action
        
        val configJson = intent.getStringExtra("CONFIG_JSON") ?: ""
        startVpn(configJson)

        return START_NOT_STICKY 
    }

    private fun startVpn(configJsonString: String) {
        scope.launch {
            if(cachedConfigJsonString == null) {
                val completeConfig = addSocksInboundToClientXrayConfig(
                    context = this@XrayVpnService, 
                    initialConf = configJsonString
                )
                cachedConfigJsonString = completeConfig
            }
            try {
                withContext(xrayDispatcher) {
                    val appName = getString(applicationInfo.labelRes)
                    val builder = Builder()
                        .setSession(appName)
                        .setMtu(1500)
                        .addAddress("172.19.0.1", 30)
                        .addRoute("0.0.0.0", 5)
                        .addRoute("8.0.0.0", 7)
                        .addRoute("11.0.0.0", 8)
                        .addRoute("12.0.0.0", 6)
                        .addDnsServer("8.8.8.8")
                        .addDisallowedApplication(packageName)
                    this@XrayVpnService.vpnPfd = builder.establish()
                    val fd = vpnPfd!!.detachFd()
                        
                    dialerController = AndroidDialerController(this@XrayVpnService)
                    dialerController!!.protectFd(fd.toLong())
                    LibXray.registerDialerController(dialerController)
                    LibXray.setDNS(dialerController, "8.8.8.8:53")

                    val socksConf = File(this@XrayVpnService.filesDir.absolutePath, "tun2socks.yaml")
                    val geosite = File(this@XrayVpnService.filesDir.absolutePath, "geosite.dat")
                    val geoip = File(this@XrayVpnService.filesDir.absolutePath, "geoip.dat")
                    
                    runBlocking {
                        copyAssetFile("tun2socks.yaml", socksConf)
                        copyAssetFile("geosite.dat", geosite)
                        copyAssetFile("geoip.dat", geoip)
                    }
                    registerNetworkCallback()
                    cachedConfigJsonString = setFd(cachedConfigJsonString!!, fd)
                    
                    createNotificationChannel()
                    val notification = NotificationCompat.Builder(this@XrayVpnService, CHANNEL_ID)
                        .setContentTitle("Xray VPN Подключен")
                        .setContentText("Защищенный туннель активен")
                        .setSmallIcon(android.R.drawable.ic_menu_share)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                    ServiceCompat.startForeground(
                        this@XrayVpnService,
                        NOTIFICATION_ID, 
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                    )

                    if(!TProxyService.TProxyIsRunning())
                    {
                        val socksConf = File(this@XrayVpnService.filesDir, "tun2socks.yaml")
                        TProxyService.TProxyStartService(socksConf.absolutePath, fd)
                    }

                    val request = InvokeRequest(
                        method = XrayMethod.RUN_XRAY,
                        payload = RunXrayRequest(cachedConfigJsonString ?: throw Exception("Cached config is empty."))
                    )
                    val response = LibXray.invoke(json.encodeToString(request))
                    val responseObj = JSONObject(response)

                    if(responseObj.getBoolean("success") == false) throw Exception(responseObj.getString("error"))

                    Log.d("XrayVpnService", "Vpn started!")
                }
            } catch (e: Exception) {
                Log.d("XrayVpnService", cachedConfigJsonString!!)
                Log.e("XrayVpnService", e.message ?: "Unknown error")
                stopSelf()
            }
        }
    }

    private suspend fun copyAssetFile(assetName: String, targetFile: File): Unit = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = assets.open(assetName)
            outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            if (connectivityManager != null && networkCallback != null) {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            networkCallback = null
            currentNetwork = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Connection Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try{
            stopXray()
            stopForeground(STOP_FOREGROUND_REMOVE)
            unregisterNetworkCallback()

            scope.launch(Dispatchers.IO) {
                val xrayLogs = File(this@XrayVpnService.filesDir, "xray_error.log")
                if(xrayLogs.exists()) {
                    xrayLogs.delete()
                }
            }

            this.vpnJob.complete()
            this.xrayDispatcher.close()

        } catch(e: Exception) {
            Log.e("XrayVpnService", e.message ?: "Unknown error")
        } finally {
            dialerController = null
            vpnPfd?.close()
            vpnPfd = null
        }

        Log.d("XrayVpnService", "Vpn stopped!")
    
        super.onDestroy()
    }
}
