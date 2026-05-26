package com.silica.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.silica.assistant.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkWriteSettingsPermission()

        setContent { MainScreen() }
    }

    private fun checkWriteSettingsPermission() {

        if (!Settings.System.canWrite(this)) {

            val intent =
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))

            startActivity(intent)
        }
    }
}
