package com.silica.assistant.core.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API = "https://api.github.com/repos/KonjikiNoYamii/Auto-Chan-Native/releases/latest"

    data class UpdateInfo(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val downloadUrl: String,
    )

    suspend fun check(currentVersionCode: Int, currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${conn.responseCode}")
                return@withContext null
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(json)

            val tag = release.optString("tag_name", "") ?: return@withContext null
            val raw = tag.removePrefix("v")

            val isNewer = when {
                raw.toIntOrNull() != null -> raw.toInt() > currentVersionCode
                raw.toDoubleOrNull() != null -> raw.toDouble() > (currentVersionName.toDoubleOrNull() ?: 0.0)
                else -> compareSemver(raw, currentVersionName) > 0
            }

            if (!isNewer) return@withContext null

            val assets = release.optJSONArray("assets") ?: return@withContext null
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url")
                    break
                }
            }

            val url = downloadUrl ?: return@withContext null
            Log.d(TAG, "Update found: $tag")
            UpdateInfo(
                latestVersionCode = raw.toIntOrNull() ?: 0,
                latestVersionName = tag,
                downloadUrl = url
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    private fun compareSemver(a: String, b: String): Int {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val aVal = aParts.getOrElse(i) { 0 }
            val bVal = bParts.getOrElse(i) { 0 }
            if (aVal != bVal) return aVal - bVal
        }
        return 0
    }
}
