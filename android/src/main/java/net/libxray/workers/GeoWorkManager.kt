package net.libxray.workers

import android.content.Context
import androidx.work.*
import androidx.work.multiprocess.RemoteWorkManager
import java.util.concurrent.TimeUnit

class GeoWorkManager(private val remoteWorkManager: RemoteWorkManager) {
    fun schedulePeriodicDownload(
        geoIpUrl: String, 
        geoSiteUrl: String, 
        downloadEvery: Long = 1L, 
        timeUnit: TimeUnit = TimeUnit.HOURS,
        maxGeoAgeMillis: Long = 3600000L
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            "GEOIP_URL" to geoIpUrl,
            "GEOSITE_URL" to geoSiteUrl,
            "MAX_GEO_AGE_MILLIS" to maxGeoAgeMillis
        )

        val downloadRequest = PeriodicWorkRequest.Builder(
           GeoWorker::class.java, downloadEvery, timeUnit
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        remoteWorkManager
            .enqueueUniquePeriodicWork(
                "GeoFilesUpdate",
                ExistingPeriodicWorkPolicy.KEEP,
                downloadRequest
            )
    }

    fun immediateUpdate(
        geoIpUrl: String, 
        geoSiteUrl: String, 
        maxGeoAgeMillis: Long = 3600000L
    ) {
        val inputData = workDataOf(
            "GEOIP_URL" to geoIpUrl,
            "GEOSITE_URL" to geoSiteUrl,
            "MAX_GEO_AGE_MILLIS" to maxGeoAgeMillis
        )

        val downloadRequest = OneTimeWorkRequest.Builder(GeoWorker::class.java)
            .setInputData(inputData)
            .build()

        remoteWorkManager
            .enqueueUniqueWork(
                "GeoFilesUpdateImmediate",
                ExistingWorkPolicy.REPLACE,
                downloadRequest
            )
    }
}