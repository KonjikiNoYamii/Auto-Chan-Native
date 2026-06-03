package com.silica.assistant.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateDownloader {

    data class DownloadResult(
        val file: File,
        val totalBytes: Long
    )

    suspend fun download(context: Context, url: String): DownloadResult? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates")
            dir.mkdirs()

            val file = File(dir, "update.apk")
            if (file.exists()) file.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val totalBytes = conn.contentLengthLong
            val input = conn.inputStream
            val output = FileOutputStream(file)

            input.use { inp ->
                output.use { out ->
                    inp.copyTo(out)
                }
            }

            DownloadResult(file, totalBytes)
        } catch (_: Exception) {
            null
        }
    }
}
