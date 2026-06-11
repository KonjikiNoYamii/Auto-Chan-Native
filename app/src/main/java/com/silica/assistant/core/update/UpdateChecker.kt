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

    suspend fun check(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
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

            // Try to parse version from tag (e.g., "v1.2.3" or "v15")
            val latestVersionStr = tag.removePrefix("v")
            
            // If it's a simple integer (legacy support)
            val latestVersionCode = latestVersionStr.toIntOrNull()
            
            val isNewer = if (latestVersionCode != null) {
                latestVersionCode > currentVersionCode
            } else {
                // If it contains dots, it might be a version name (e.g. 1.2)
                // For simplicity, we compare strings if not integers, 
                // but better to compare against versionName from context if available.
                // However, since we only have currentVersionCode here, 
                // let's assume the user uses integer tags for auto-update logic.
                false
            }

            if (!isNewer && latestVersionCode != null) return@withContext null
            // If we can't parse an int, we might want to check if the tag is different 
            // from a stored "last seen tag", but currentVersionCode is safer.

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
                latestVersionCode = latestVersionCode ?: 0,
                latestVersionName = tag,
                downloadUrl = url
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

}
