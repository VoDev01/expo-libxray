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
import android.content.BroadcastReceiver
import android.content.IntentFilter

class XrayVpnService : VpnService() {
    private val networkDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val scope = CoroutineScope(networkDispatcher + SupervisorJob())

    private var vpnPfd: ParcelFileDescriptor? = null
    private val CHANNEL_ID = "xray_vpn_channel"

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var dialerController: AndroidDialerController? = null

    private var cachedConfigJsonString: String? = null

    private val vpnNetId = "172.16.0.0"
    private val vpnPrefixIp = 16
    private val mtu = 1500
    private val allowedTrafficSubnet = arrayOf(
        "1.0.0.0" to 8,
        "11.0.0.0" to 8,
        "12.0.0.0" to 6,
        "128.0.0.0" to 3,
        "160.0.0.0" to 5,
        "172.32.0.0" to 11,
        "172.64.0.0" to 10
    )
    private val dnsIp = "8.8.8.8"
    private val dnsPort = "53"

    private var isRunning = false

    companion object {
        public val TAG = "XrayVpnService"
        private val NOTIFICATION_ID = 102
        private val json = Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        }
    }

    private fun logThread(name: String) {
        Log.d(TAG, "$name running on ${Thread.currentThread().name}")
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
            Log.e(TAG, e.message ?: "Unknown error")
        }
    }

    private fun handleNetworkChange() {
        try {
            stopXray()
            if(cachedConfigJsonString != null) {
                createForegroundNotification(NOTIFICATION_ID)
                registerNetworkCallback()
                scope.launch {
                    startVpn(cachedConfigJsonString!!)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent?.action

        return when(action) {
            "START_VPN" -> {
                if(isRunning) START_STICKY
                else {
                    val configJson = intent.getStringExtra("CONFIG_JSON") ?: ""
                    createForegroundNotification(NOTIFICATION_ID)
                    registerNetworkCallback()

                    scope.launch {
                        startVpn(configJson)
                    }

                    START_STICKY
                }
            }
            "STOP_VPN" -> {
                logThread("VpnService stopping")
                if(isRunning)
                    stopXray()
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private suspend fun startVpn(configJson: String) {
        try {
            val builder = Builder()
                .setSession(TAG)
                .setMtu(mtu)
                .addAddress(vpnNetId, vpnPrefixIp)
                .addDnsServer(dnsIp)
                .addDisallowedApplication(this.packageName)

            for (subnet in allowedTrafficSubnet) {
                builder.addRoute(subnet.first, subnet.second)
            }

            vpnPfd = builder.establish()
            vpnPfd?.let { pfd ->
                prepareProxy(configJson)
                startProxy(pfd.getFd())
            }

            Log.d(TAG, "Vpn started!")
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error")
            stopXray()
        }
    }

    private fun createForegroundNotification(serviceId: Int) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xray VPN Подключен")
            .setContentText("Защищенный туннель активен")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(serviceId, notification)
        } else {
            startForeground(
                serviceId, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        }
    }

    private fun prepareProxy(configJson: String) {
        try {
            if(cachedConfigJsonString == null) {
                cachedConfigJsonString = configJson
            }
            val socksConf = File(this.filesDir.absolutePath, "tun2socks.yaml")
            val geosite = File(this.filesDir.absolutePath, "geosite.dat")
            val geoip = File(this.filesDir.absolutePath, "geoip.dat")
            
            runBlocking {
                if(!socksConf.exists())
                    copyAssetFile("tun2socks.yaml", socksConf)
                if(!geosite.exists())
                    copyAssetFile("geosite.dat", geosite)
                if(!geoip.exists())
                    copyAssetFile("geoip.dat", geoip)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error.")
        }
    }

    private suspend fun startProxy(fd: Int) {
        if(!TProxyService.TProxyIsRunning())
        {
            scope.launch {
                try {
                    logThread("Starting tun2socks")
                    val socksConf = File(this@XrayVpnService.filesDir, "tun2socks.yaml")
                    TProxyService.TProxyStartService(socksConf.absolutePath, fd)
                } catch (e: Exception) {
                    Log.e(TAG, e.message ?: "Unknown error")
                    stopXray()
                }
            }
        }
        
        scope.launch {
            try {
                logThread("Starting xray")
                if(cachedConfigJsonString == null) throw Exception("Unable to start xray: config null.")
                                
                dialerController = AndroidDialerController(this@XrayVpnService)

                LibXray.registerDialerController(dialerController)
                LibXray.setDNS(dialerController, "$dnsIp:$dnsPort")

                cachedConfigJsonString = setFd(cachedConfigJsonString!!, fd)

                val request = InvokeRequest(
                    method = XrayMethod.RUN_XRAY,
                    payload = RunXrayRequest(cachedConfigJsonString ?: throw Exception("Cached config is empty."))
                )

                val response = LibXray.invoke(json.encodeToString(request))
                val responseObj = JSONObject(response)

                if(responseObj.getBoolean("success") == false) throw Exception(responseObj.getString("error"))
                else isRunning = true

                Log.d(TAG, "Proxy started!")
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Unknown error")
                stopXray()
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
            Log.e(TAG, e.message ?: "Unknown error.")
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
            Log.e(TAG, e.message ?: "Unknown error.")
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

    private fun stopXray() {
        Log.d(TAG, "stopXray start")
        try{
            val stopRequest = InvokeRequest(
                method = XrayMethod.STOP_XRAY,
                payload = ""
            )
            val response = LibXray.invoke(json.encodeToString(stopRequest))
            val responseObj = JSONObject(response)
            Log.d(TAG, "LibXray.invoke stop responded")

            if(responseObj.getBoolean("success") == false)
                Log.e(TAG, responseObj.getString("error"))

            LibXray.resetDNS()
                    
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }

            Log.d(TAG, "socks proxy stopped")

            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "stopForeground")
            unregisterNetworkCallback()
            Log.d(TAG, "unregisterNetworkCallback")
        } catch(e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error")
        } finally {
            dialerController = null
            vpnPfd?.close()
            vpnPfd = null

            cachedConfigJsonString = null
            isRunning = false

            stopSelf()
        }
    }

    override fun onDestroy() {
        networkDispatcher.close()
        super.onDestroy()
    }
}
