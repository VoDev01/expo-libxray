package net.libxray

import libXray.LibXray
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import hev.sockstun.TProxyService
import android.content.Intent
import android.net.VpnService
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class ExpoLibxrayModule : Module() {

  companion object {
    private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 10
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoLibxray")

    val json = Json { 
      ignoreUnknownKeys = true 
      encodeDefaults = true
    }

    AsyncFunction("convertShareLinksToXrayJson") { links: String ->
      val request = InvokeRequest(
        method = XrayMethod.CONVERT_SHARE_LINKS_TO_JSON,
        payload = ConvertLinksRequest(links)
      )
      return@AsyncFunction LibXray.invoke(json.encodeToString(request))
    }

    AsyncFunction("runXrayFromJson") { configJson: String ->
      val context = appContext.reactContext ?: 
      return@AsyncFunction InvokeResponse(success = false, error = "No context.").toString()
      val activity = appContext.currentActivity ?: 
      return@AsyncFunction InvokeResponse(success = false, error = "No activity.").toString()
      
      val vpnPermissionIntent = VpnService.prepare(activity)
      
      if (vpnPermissionIntent != null) {
        activity.startActivityForResult(vpnPermissionIntent, 1002)
      }

      if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
        
      }
      
      val intent = Intent(context, XrayVpnService::class.java).apply {
        action = "START_VPN"
        putExtra("CONFIG_JSON", configJson)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
      } else {
          context.startService(intent)
      }

      return@AsyncFunction InvokeResponse(success = true).toString()
    }

    AsyncFunction("stopXray") {
        val context = appContext.reactContext ?: 
        return@AsyncFunction null
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        context.startService(intent)
        return@AsyncFunction null
    }

    AsyncFunction("getXrayState") {
      val request = InvokeRequest(
        method = XrayMethod.GET_XRAY_STATE,
        payload = ""
      )
      return@AsyncFunction LibXray.invoke(json.encodeToString(request))
    }

    AsyncFunction("pingXrayConfig") { configJson: String ->
      val tempConfigFile = File(appContext.reactContext?.cacheDir, "temp_ping_config.json")
      try {
        tempConfigFile.writeText(configJson)

        val request = InvokeRequest(
            method = XrayMethod.PING,
            payload = PingRequest(
                configPath = tempConfigFile.absolutePath,
                timeout = 5,
                url = "https://google.com",
                proxy = "socks5://127.0.0.1:10808"
            )
        )

        return@AsyncFunction LibXray.invoke(json.encodeToString(request))
      } catch (e: Exception) {
        val err = InvokeResponse(
          success = false,
          error = e.message
        )
        return@AsyncFunction err.toString()
      } finally {
        if (tempConfigFile.exists()) {
            tempConfigFile.delete()
        }
      }
    }
  }
}

