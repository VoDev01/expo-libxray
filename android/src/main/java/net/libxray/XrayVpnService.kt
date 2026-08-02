package net.libxray

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
import libXray.LibXray
import libXray.DialerController
import hev.sockstun.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class XrayVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "vpn_service_channel"

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null

    private val json = Json { 
      ignoreUnknownKeys = true 
      encodeDefaults = true
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
                    android.util.Log.d("XrayVpnService", "Сеть изменилась.")
                    currentNetwork = network

                    handleNetworkChange()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (network == currentNetwork) {
                    android.util.Log.e("XrayVpnService", "Интернет-соединение потеряно.")
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

    private fun handleNetworkChange() {
        Thread {
            try {
                android.util.Log.d("XrayVpnService", "Обновление сетевых интерфейсов ядра Go...")
                
                LibXray.resetDNS()

                val dialerController = object : DialerController {
                    override fun protectFd(fd: Long): Boolean {
                        return protect(fd.toInt())
                    }
                }
                LibXray.setDNS(dialerController, "8.8.8.8:53")

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                android.util.Log.d("XrayVpnService", "Сетевые интерфейсы обновлены.")
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == "START_VPN") {
            val configJson = intent.getStringExtra("CONFIG_JSON") ?: ""
            startVpn(configJson)
        } else if (action == "STOP_VPN") {
            stopVpn()
        }

        return START_STICKY 
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
            
        startForeground(NOTIFICATION_ID, notification)

        val dialerController = object : DialerController {
            override fun protectFd(fd: Long): Boolean {
                return protect(fd.toInt()) 
            }
        }
        LibXray.setDNS(dialerController, "8.8.8.8:53")

        Thread {
            try {
                val request = InvokeRequest(
                    method = XrayMethod.RUN_XRAY,
                    payload = RunXrayRequest(configJsonString)
                )
                LibXray.invoke(json.encodeToString(request))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        try {
            val appName = getString(applicationInfo.labelRes) 

            val builder = Builder()
                .setSession(appName)
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                val fd = vpnInterface!!.fd
                
                val targetFile = File(cacheDir, "tun2socks_embedded.yaml")
                
                copyAssetFile("tun2socks.yaml", targetFile)

                Thread {
                    try {
                        val isStarted = TProxyService.TProxyStartService(
                            targetFile.absolutePath, 
                            fd
                        )
                        if (isStarted) {
                            registerNetworkCallback()

                            android.util.Log.d("XrayVpnService", "Сетевой туннель tun2socks успешно запущен и работает в фоне!")
                        } else {
                            android.util.Log.e("XrayVpnService", "Не удалось запустить нативный туннель TProxyStartService")
                            stopVpn()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun copyAssetFile(assetName: String, targetFile: File) {
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

    private fun stopVpn() {
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

        Thread {
            val stopRequest = InvokeRequest(
                method = XrayMethod.STOP_XRAY,
                payload = ""
            )
            LibXray.invoke(json.encodeToString(stopRequest))
            LibXray.resetDNS()
        }.start()

        try {
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }
        } catch (e: Exception) { e.printStackTrace() }

        val targetFile = File(cacheDir, "tun2socks_embedded.yaml")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
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
        stopVpn()
        super.onDestroy()
    }
}
