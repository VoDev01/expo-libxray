package net.libxray.service

import android.util.Log
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
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
import net.libxray.model.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.guava.await
import android.content.Intent
import android.app.PendingIntent
import android.os.IBinder
import android.app.Notification
import kotlin.collections.HashMap

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
        "10.0.2.0" to 24,
        "0.0.0.0" to 0
    )
    private val dnsIps = arrayOf(
        "8.8.8.8",
    )
    private val dnsPort = "53"
    private var isRunning = false

    private var appsSplitTunneling: Array<String>? = null
    private var notificationStatuses: HashMap<String, String>? = null
    private var notificationTitle: String = "VPN Connection"
    private var notificationContent: String = "Status:"

    companion object {
        public val TAG = "XrayVpnService"
        private val NOTIFICATION_ID = 102
        private val json = Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    private fun logThread(name: String) {
        Log.i(TAG, "$name is running on ${Thread.currentThread().name} thread")
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
                updateNotification(NOTIFICATION_ID, "Network change...")
                registerNetworkCallback()
                scope.launch {
                    startVpn(cachedConfigJsonString!!, appsSplitTunneling, notificationStatuses)
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
                    appsSplitTunneling = intent?.getStringArrayExtra("APPS_SPLIT_TUNNELING")
                    notificationTitle = intent?.getStringExtra("NOTIFICATION_TITLE") ?: "VPN Connection"
                    notificationContent = intent?.getStringExtra("NOTIFICATION_CONTENT") ?: "Status: "
                    notificationStatuses = intent?.getSerializableExtra("NOTIFICATION_STATUSES", HashMap::class.java) as? HashMap<String, String>

                    val configJson = intent.getStringExtra("CONFIG_JSON") ?: ""
                    val currentStatuses = notificationStatuses
                    val waitingText = if (currentStatuses != null) {
                        currentStatuses["waiting"] ?: "Waiting..."
                    } else {
                        "Waiting..."
                    }

                    updateNotification(
                        NOTIFICATION_ID, 
                        waitingText, 
                        notificationTitle, 
                        notificationContent
                    )
                    registerNetworkCallback()

                    scope.launch {
                        try {
                            startVpn(configJson, appsSplitTunneling, notificationStatuses)
                        } catch(e: Exception) {
                            Log.e(TAG, e.message ?: "Uknown error")
                            stopXray()
                            START_NOT_STICKY
                        }
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

    private suspend fun startVpn(configJson: String, appsSplitTunneling: Array<String>?, notificationStatuses: HashMap<String, String>?) {
        try {
            val builder = Builder()
                .setSession(TAG)
                .setMtu(mtu)
                .addAddress(vpnNetId, vpnPrefixIp)
                .addDisallowedApplication(this.packageName)

            for (subnet in allowedTrafficSubnet) {
                builder.addRoute(subnet.first, subnet.second)
            }
            for(dnsIp in dnsIps) {
                builder.addDnsServer(dnsIp)
            }
            if(appsSplitTunneling !== null) {
                for(appPackageName in appsSplitTunneling) {
                    builder.addDisallowedApplication(appPackageName)
                }
            }

            vpnPfd = builder.establish()
            vpnPfd?.let { pfd ->
                prepareProxy(configJson)
                startProxy(pfd.getFd(), notificationStatuses)
            }

            Log.i(TAG, "Vpn started!")
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error")
            stopXray()
        }
    }


    private fun updateNotification(
        serviceId: Int, 
        status: String = "Waiting...",
        notificationTitle: String? = null, 
        notificationContent: String? = null
    ) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Connection Status",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        
        this.notificationTitle = notificationTitle ?: this.notificationTitle
        this.notificationContent = notificationContent ?: this.notificationContent

        val packageName = getPackageName();
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this, 
                0, 
                launchIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(this.notificationTitle)
            .setContentText("${this.notificationContent} $status")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)

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
            
            runBlocking {
                copyAssetFile("tun2socks.yaml", socksConf)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error.")
        }
    }

    private suspend fun startProxy(fd: Int, notificationStatuses: HashMap<String, String>?) {
        sendVpnStatus("CONNECTING")
        updateNotification(
            NOTIFICATION_ID, 
            notificationStatuses?.get("connecting") ?: "Connecting...",
        )
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
                for(dnsIp in dnsIps) {
                    LibXray.setDNS(dialerController, "$dnsIp:$dnsPort")
                }

                val request = InvokeRequest(
                    method = XrayMethod.RUN_XRAY,
                    payload = RunXrayInvokeRequest(
                        cachedConfigJsonString ?: throw Exception("Cached config is empty.")
                    )
                )

                val response = LibXray.invoke(json.encodeToString(request))
                val responseObj = JSONObject(response)

                if(responseObj.getBoolean("success") == false) throw Exception(responseObj.getString("error"))
                else isRunning = true

                Log.i(TAG, "Proxy started!")

                updateNotification(NOTIFICATION_ID, notificationStatuses?.get("connected") ?: "Connected!")
                sendVpnStatus("CONNECTED")
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Unknown error")
                updateNotification(NOTIFICATION_ID, notificationStatuses?.get("error") ?: "Unable to establish connection")
                sendVpnStatus("ERROR", e.message)
                stopXray()
            }
        }
    }

    private fun sendVpnStatus(status: String, error: String? = null) {
        val intent = Intent("net.libxray.VPN_STATUS").apply {
            putExtra("status", status)
            putExtra("error", error)
            setPackage(packageName)
        }
        sendBroadcast(intent)
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

    private fun stopXray() {
        Log.i(TAG, "stopXray called")
        try{
            val stopRequest = InvokeRequest(
                method = XrayMethod.STOP_XRAY,
                payload = ""
            )
            val response = LibXray.invoke(json.encodeToString(stopRequest))
            val responseObj = JSONObject(response)
            Log.i(TAG, "LibXray.invoke stop responded")

            if(responseObj.getBoolean("success") == false)
                Log.e(TAG, responseObj.getString("error"))

            LibXray.resetDNS()
                    
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }

            Log.i(TAG, "socks proxy stopped")

            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.i(TAG, "stopForeground")
            unregisterNetworkCallback()
            Log.i(TAG, "unregisterNetworkCallback")
        } catch(e: Exception) {
            Log.e(TAG, e.message ?: "Unknown error")
        } finally {
            dialerController = null
            vpnPfd?.close()
            vpnPfd = null

            cachedConfigJsonString = null
            isRunning = false

            
            sendVpnStatus("DISCONNECTED")
            stopSelf()
        }
    }

    override fun onDestroy() {
        networkDispatcher.close()
        super.onDestroy()
    }
}




