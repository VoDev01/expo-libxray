package net.libxray.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import kotlinx.coroutines.*
import io.ktor.client.plugins.logging.*
import io.ktor.utils.io.jvm.javaio.copyTo

class GeoWorker(
    appContext: Context, 
    workerParams: WorkerParameters,
): CoroutineWorker(appContext, workerParams){

    companion object {
        public val TAG = "GeoWorker"
        public fun isGeoFresh(file: File, maxGeoAgeMillis: Long): Boolean {
            if (!file.exists()) {
                return false
            }
            
            val fileAge = System.currentTimeMillis() - file.lastModified()

            return fileAge in 0 until maxGeoAgeMillis
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting scheduled work: updating geoip and geosite.")

        val client = HttpClient(Android) {
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.INFO
            }
        }   

        val geoIpUrl = inputData.getString("GEOIP_URL") ?: return Result.failure(
            workDataOf("ERROR_MESSAGE" to "GEOIP_URL is missing")
        )
        val geoSiteUrl = inputData.getString("GEOSITE_URL") ?: return Result.failure(
            workDataOf("ERROR_MESSAGE" to "GEOSITE_URL is missing")
        )   
        val maxGeoAgeMillis = inputData.getLong("MAX_GEO_AGE_MILLIS", 3600000L) ?: return Result.failure(
            workDataOf("ERROR_MESSAGE" to "MAX_GEO_AGE_MILLIS is missing")
        )   

        val geoIpFile = File(applicationContext.filesDir, "geoip.dat")
        val geoSiteFile = File(applicationContext.filesDir, "geosite.dat")  

        return withContext(Dispatchers.IO) {
            try {
                if (isGeoFresh(geoIpFile, maxGeoAgeMillis) && isGeoFresh(geoSiteFile, maxGeoAgeMillis)) {
                    Log.i(TAG, "Files are already up to date")
                    Result.success()
                } else {
                    val tempIpFile = File(applicationContext.filesDir, "geoip.dat.tmp")
                    val tempSiteFile = File(applicationContext.filesDir, "geosite.dat.tmp")

                    client.prepareGet(geoIpUrl).execute { response ->
                        val channel = response.bodyAsChannel()
                        tempIpFile.outputStream().use { output ->
                            channel.copyTo(output)
                        }
                    }

                    client.prepareGet(geoSiteUrl).execute { response ->
                        val channel = response.bodyAsChannel()
                        tempSiteFile.outputStream().use { output ->
                            channel.copyTo(output)
                        }
                    }

                    if (tempIpFile.exists() && tempIpFile.length() > 0 && tempSiteFile.exists() && tempSiteFile.length() > 0) {
                        
                        tempIpFile.setReadable(true, false)
                        tempSiteFile.setReadable(true, false)

                        if (tempIpFile.renameTo(geoIpFile) && tempSiteFile.renameTo(geoSiteFile)) {
                            Log.i(TAG, "Updated geoip and geosite successfully.")
                            Result.success()
                        } else {
                            Log.e(TAG, "Failed to rename temporary geo files.")
                            Result.retry()
                        }
                    } else {
                        Log.e(TAG, "Downloaded files are empty or missing.")
                        Result.retry()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to update geoip and geosite. Error: ${e.localizedMessage}. Retrying...")
                Result.retry()
            } finally {
                client.close()
            }
        }
   }
}