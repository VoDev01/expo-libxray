package net.libxray

import libXray.LibXray
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import hev.sockstun.TProxyService
import android.content.Intent
import android.net.VpnService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context

class ExpoLibxrayModule : Module() {
  companion object {
    private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 10
  }

  private val context: Context
    get() = appContext.reactContext?.applicationContext ?: throw Exception("No application context provided.")

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


    AsyncFunction("runXray") { configJson: String, promise: Promise ->
      val activity = appContext.currentActivity

      if(activity == null) {
        promise.reject("ERR_NO_ACTIVITY", "No active android activity found.", null)
        return@AsyncFunction
      }

      val intent = Intent(context, XrayVpnService::class.java).apply {
        putExtra("CONFIG_JSON", configJson)
        setAction("START_VPN")
        setPackage(context.packageName)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
          }
      }

      val vpnPermissionIntent = VpnService.prepare(activity)
      if (vpnPermissionIntent != null) {
        activity.startActivityForResult(vpnPermissionIntent, 1002)
        promise.resolve(false)
      } else {
        context.startForegroundService(intent)
        promise.resolve(true)
      }

      return@AsyncFunction
    }

    AsyncFunction("stopXray") {
      val intent = Intent(context, XrayVpnService::class.java).apply {
        setAction("STOP_VPN")
        setPackage(context.packageName)
      }
      context.startService(intent)
      return@AsyncFunction true
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

