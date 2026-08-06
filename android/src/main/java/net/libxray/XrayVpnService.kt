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
import org.json.JSONArray
import org.json.JSONObject

class XrayVpnService : VpnService() {

    private var vpnPfd: ParcelFileDescriptor? = null
    private var intent: Intent? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "vpn_service_channel"

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var dialerController: AndroidDialerController? = null

    private val json = Json { 
      ignoreUnknownKeys = true 
      encodeDefaults = true
    }

    private val netScope = CoroutineScope(Dispatchers.IO)
    private var cachedConfigJsonString: String? = null

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
            stopVpn(this@XrayVpnService.intent)

            if(!cachedConfigJsonString.isNullOrBlank())
                startVpn(cachedConfigJsonString!!)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent == null) return 0
        this.intent = intent
        val action = intent?.action
        
        if (action == "START_VPN") {
            val configJson = intent.getStringExtra("CONFIG_JSON") ?: ""
            startVpn(configJson)
            return START_STICKY 
        } else if (action == "STOP_VPN") {
            stopVpn(this.intent)
        }

        return START_NOT_STICKY
    }

    private fun startVpn(configJsonString: String) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xray VPN Подключен")
            .setContentText("Защищенный туннель активен")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID, 
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if(cachedConfigJsonString == null) {
            val completeConfig = addTUNInboundToClientXrayConfig(
                context = this, 
                initialConf = configJsonString
            )
            cachedConfigJsonString = completeConfig
        }

        try {
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

            this.vpnPfd = builder.establish()
            val fd = vpnPfd!!.detachFd()
                
            dialerController = AndroidDialerController(this)
            dialerController!!.protectFd(fd.toLong())
            LibXray.registerDialerController(dialerController)

            LibXray.setDNS(dialerController, "8.8.8.8:53")

            val socksConf = File(this.filesDir.absolutePath, "tun2socks_embedded.yaml")
            val geosite = File(this.filesDir.absolutePath, "geosite.dat")
            val geoip = File(this.filesDir.absolutePath, "geoip.dat")
            
            runBlocking {
                copyAssetFile("tun2socks.yaml", socksConf)
                copyAssetFile("geosite.dat", geosite)
                copyAssetFile("geoip.dat", geoip)
            }

            // val isStarted = TProxyService.TProxyStartService(
            //     targetFile.absolutePath, 
            //     fd
            // )

            //if (isStarted) {
                registerNetworkCallback()

                cachedConfigJsonString = setFd(cachedConfigJsonString!!, fd)
                
                val request = InvokeRequest(
                    method = XrayMethod.RUN_XRAY,
                    payload = RunXrayRequest(cachedConfigJsonString ?: throw Exception("Cached config is empty."))
                )
                val response = LibXray.invoke(json.encodeToString(request))
                val responseObj = JSONObject(response)

                if(responseObj.getBoolean("success") == false)
                    throw Exception(responseObj.getString("error"))

                Log.d("XrayVpnService", "Vpn started!")
            //} else {
            //    Log.e("XrayVpnService", "Unable to start tun2socks.")
            //    stopVpn()
            //}
        } catch (e: Exception) {
            Log.d("XrayVpnService", cachedConfigJsonString!!)
            Log.e("XrayVpnService", e.message ?: "Unknown error")
            stopVpn(this.intent)
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

    private fun stopVpn(intent: Intent?) {
        try{
            val stopRequest = InvokeRequest(
                method = XrayMethod.STOP_XRAY,
                payload = ""
            )
            val response = LibXray.invoke(json.encodeToString(stopRequest))
            val responseObj = JSONObject(response)

            if(responseObj.getBoolean("success") == false)
               throw Exception(responseObj.getString("error"))

            LibXray.resetDNS()
        } catch(e: Exception) {
            Log.e("XrayVpnService", e.message ?: "Unknown error")
        }

        try {
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }
        } catch (e: Exception) { e.printStackTrace() }

        val socksConfig = File(this.filesDir, "tun2socks_embedded.yaml")
        if (socksConfig.exists()) {
            socksConfig.delete()
        }
        val xrayLogs = File(this.filesDir, "xray_error.log")
        if(xrayLogs.exists()) {
            xrayLogs.delete()
        }
        val geosite = File(this.filesDir, "geosite.dat")
        if (geosite.exists()) {
            geosite.delete()
        }
        val geoip = File(this.filesDir, "geoip.dat")
        if (geoip.exists()) {
            geoip.delete()
        }

        try {
            vpnPfd?.close()
        } catch (e: Exception) {
            Log.e("XrayVpnService", e.message ?: "Uknown error.")
        }
        finally {
            vpnPfd = null
        }
        dialerController = null
        ServiceCompat.stopForeground(
            this, 
            STOP_FOREGROUND_REMOVE
        )
        
        if(intent != null)
            this.stopService(intent)

        try {
            unregisterNetworkCallback()
        } catch (e: Exception) {
            Log.e("XrayVpnService", e.message ?: "Uknown error.")
        }

        stopSelf()
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
        stopVpn(this.intent)
        super.onDestroy()
    }
}
