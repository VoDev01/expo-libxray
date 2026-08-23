package net.libxray.workers

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import android.util.Log

object WorkManagerInitializationProvider {

    fun initialize(context: Context) {
        val mainProcessName = context.packageName

        val configBuilder = Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)

       configBuilder.setDefaultProcessName("$mainProcessName:xray_vpn")

        try {
            WorkManager.initialize(context, configBuilder.build())
            Log.i("ExpoLibxrayModule", "WorkManager is initialized in process: $mainProcessName:xray_vpn")
        } 
        catch(e: IllegalStateException) {
            Log.w("ExpoLibxrayModule", "WorkManager is already initialized")
        } 
        catch (e: Exception) {
            Log.e("ExpoLibxrayModule", e.message ?: "Unknown error")
        }
    }
}
