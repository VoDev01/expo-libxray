package net.libxray.workers

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import android.util.Log
import android.os.Build
import android.app.Application

object WorkManagerInitializationProvider {

    fun initialize(context: Context) {
        val mainProcessName = context.packageName

        val configBuilder = Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)

        val currentProcessName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            context.packageName
        }

        configBuilder.setDefaultProcessName(currentProcessName)

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
