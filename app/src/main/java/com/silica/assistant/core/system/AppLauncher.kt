package com.silica.assistant.core.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

object AppLauncher {

    fun open(
        context: Context,
        appName: String
    ) {

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

            label.contains(appName.lowercase())
        }

        if (targetApp != null) {

            val launchIntent =
                packageManager.getLaunchIntentForPackage(
                    targetApp.packageName
                )

            if (launchIntent != null) {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(launchIntent)

            } else {

                Toast.makeText(
                    context,
                    "App tidak bisa dibuka",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } else {

            Toast.makeText(
                context,
                "App tidak ditemukan",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
