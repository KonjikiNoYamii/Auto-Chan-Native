package com.silica.assistant.core.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppLauncher {

    // aliases for common speech-recognition variants / abbreviations
    private val appAliases = mapOf(
        "wa" to "whatsapp",
        "wesap" to "whatsapp",
        "whatsap" to "whatsapp",
        "wa saap" to "whatsapp",
        "wa sap" to "whatsapp",
        "ig" to "instagram",
        "fb" to "facebook",
        "yt" to "youtube"
    )

    /** Returns true if app was found and launched, false otherwise. */
    fun open(
        context: Context,
        appName: String
    ): Boolean {

        val normalizedName = appAliases[appName.lowercase().trim()] ?: appName.lowercase().trim()

        val packageManager = context.packageManager

        val installedApps =
            packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            )

        val targetApp = installedApps.firstOrNull { app ->

            val label =
                packageManager.getApplicationLabel(app)
                    .toString()
                    .lowercase()

            label.contains(normalizedName) ||
                    app.packageName.lowercase().contains(normalizedName)
        }

        if (targetApp != null) {

            val pkg = targetApp.packageName

            var launchIntent = packageManager.getLaunchIntentForPackage(pkg)

            if (launchIntent == null) {
                // fallback: resolve MAIN/LAUNCHER activity manually
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(pkg)
                }
                val activities = packageManager.queryIntentActivities(mainIntent, 0)
                if (activities.isNotEmpty()) {
                    val act = activities[0].activityInfo
                    launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        setClassName(act.packageName, act.name)
                    }
                }
            }

            if (launchIntent != null) {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(launchIntent)

                return true
            }
        }

        return false
    }
}
