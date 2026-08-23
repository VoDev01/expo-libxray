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

class GeoWorker(
    appContext: Context, 
    workerParams: WorkerParameters,
): CoroutineWorker(appContext, workerParams){

    companion object {
        public val TAG = "GeoWorker"
    }

    private fun isGeoFresh(file: File, maxGeoAgeMillis: Long): Boolean {
        if (!file.exists()) {
            return false
        }
        
        val fileAge = System.currentTimeMillis() - file.lastModified()

        return fileAge in 0 until maxGeoAgeMillis
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
                if(isGeoFresh(geoIpFile, maxGeoAgeMillis) && isGeoFresh(geoSiteFile, maxGeoAgeMillis)) {
                    Log.i(TAG, "Files are already up to date")
                    Result.success()
                } else {
                    client.prepareGet(geoIpUrl).execute { response ->
                        val channel: ByteReadChannel = response.bodyAsChannel()
                        FileOutputStream(geoIpFile).use { output ->
                            channel.toInputStream().copyTo(output)
                        }
                    }   

                    client.prepareGet(geoSiteUrl).execute { response ->
                        val channel: ByteReadChannel = response.bodyAsChannel()
                        FileOutputStream(geoSiteFile).use { output ->
                            channel.toInputStream().copyTo(output)
                        }
                    }   
                    
                    Log.i(TAG, "Updated geoip and geosite")
                    Result.success()
                }
            }catch(e: Exception) {
                Log.w(TAG, "Unable to update geoip and geosite. Retrying...")
                Result.retry()
            } finally {
                client.close()
            }
        }
   }
}