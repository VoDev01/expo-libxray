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
import android.app.Activity
import android.net.VpnService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import net.libxray.service.XrayVpnService
import net.libxray.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import androidx.work.*
import net.libxray.workers.WorkManagerInitializationProvider
import android.content.IntentFilter
import android.content.BroadcastReceiver

class ExpoLibxrayModule : Module() {
  private var vpnDeferred: CompletableDeferred<Boolean>? = null
  companion object {
    private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1010
    private const val VPN_SERVICE_REQUEST_CODE = 1011
  }

  private val context: Context
    get() = appContext.reactContext?.applicationContext ?: throw Exception("No application context provided.")

  private val statusReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
          val status = intent.getStringExtra("status") ?: "UNKNOWN"
          val error = intent.getStringExtra("error")
          
          sendEvent("onVpnStatusChange", mapOf(
              "status" to status,
              "error" to error
          ))
      }
  }


  override fun definition() = ModuleDefinition {
    Name("ExpoLibxray")

    Events("onVpnStatusChange")

    val json = Json { 
      ignoreUnknownKeys = true 
      encodeDefaults = true
    }

    OnCreate {
      WorkManagerInitializationProvider.initialize(context)

      val filter = IntentFilter("net.libxray.VPN_STATUS")
      context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    OnDestroy {
        try {
          context.unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
          
        }
    }

    OnActivityResult { _, payload ->
      if (payload.requestCode == VPN_SERVICE_REQUEST_CODE) {
        val isGranted = payload.resultCode == Activity.RESULT_OK
        vpnDeferred?.complete(isGranted)
      }
    }

    AsyncFunction("convertShareLinksToXrayJson") { links: String ->
      val request = InvokeRequest(
        method = XrayMethod.CONVERT_SHARE_LINKS_TO_JSON,
        payload = ConvertLinksRequest(links)
      )
      return@AsyncFunction LibXray.invoke(json.encodeToString(request))
    }


    AsyncFunction("runXray") { request: RunXrayRequest ->
      val activity = appContext.currentActivity

      if(activity == null) {
        return@AsyncFunction RunXrayResponse(
          success = false,
          error = "No app activity found."
        )
      }

      val intent = Intent(context, XrayVpnService::class.java).apply {
        putExtra("CONFIG_JSON", request.xrayJson)
        if(request.geoIpUrl != null) putExtra("GEOIP_URL", request.geoIpUrl)
        if(request.geoSiteUrl != null) putExtra("GEOSITE_URL", request.geoSiteUrl)
        if(request.downloadEvery != null) putExtra("DOWNLOAD_EVERY", request.downloadEvery.toLongOrNull())
        if(request.timeUnit != null) putExtra("TIME_UNIT", request.timeUnit.value)
        if(request.maxGeoAgeMillis != null) putExtra("MAX_GEO_AGE_MILLIS", request.maxGeoAgeMillis.toLongOrNull())
        if(request.appsSplitTunneling != null) putExtra("APPS_SPLIT_TUNNELING", request.appsSplitTunneling)
        if(request.vpnServiceNotificationTitle != null) putExtra("NOTIFICATION_TITLE", request.vpnServiceNotificationTitle)
        if(request.vpnServiceNotificationContent != null) putExtra("NOTIFICATION_CONTENT", request.vpnServiceNotificationContent)
        if(request.vpnServiceNotificationStatuses != null) putExtra("NOTIFICATION_STATUSES", request.vpnServiceNotificationStatuses)
        setAction("START_VPN")
        setPackage(context.packageName)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
          activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)

          var vpnPermissionIntent = VpnService.prepare(activity)
          if (vpnPermissionIntent != null) {
            vpnDeferred = CompletableDeferred()

            activity.startActivityForResult(vpnPermissionIntent, VPN_SERVICE_REQUEST_CODE)

            var vpnGranted = false
            runBlocking {
              vpnGranted = vpnDeferred!!.await()
              vpnDeferred = null
            }
            if(!vpnGranted) {
              return@AsyncFunction RunXrayResponse(
                success = false,
                error = request.vpnServiceErrorLocalized
              )
            }
          }
        }
      }

      context.startForegroundService(intent)
      return@AsyncFunction RunXrayResponse(true, null)
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

    AsyncFunction("testXray") { configJson: String ->
      val request = InvokeRequest(
        method = XrayMethod.TEST_XRAY,
        payload = ConvertXrayJsonRequest(configJson)
      )
      val response = json.decodeFromString<TestXrayResponse>(LibXray.invoke(json.encodeToString(request)))
      return@AsyncFunction response
    }

    AsyncFunction("xrayVersion") {
      val request = InvokeRequest(
        method = XrayMethod.VERSION,
        payload = ""
      )
      return@AsyncFunction LibXray.invoke(json.encodeToString(request))
    }

    AsyncFunction("pingBatch") { request: PingBatchRequest ->
      if(request.configs.size > 5) {
        return@AsyncFunction PingBatchResponse(
          results = listOf(
            PingBatchItemResponse(
              success = false,
              delay = 0L,
              error = "Request should not contain more than 5 configurations."
            )
          )
        )
      }
      val request = InvokeRequest(
        method = XrayMethod.PING_BATCH,
        payload = request
      )
      val response = json.decodeFromString<InvokeResponse<PingBatchResponse>>(LibXray.invoke(json.encodeToString(request)))
      return@AsyncFunction response?.data ?: PingBatchResponse(results = emptyList())
    }
  }
}

