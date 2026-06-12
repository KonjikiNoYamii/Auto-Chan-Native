package com.silica.assistant

import android.app.Application
import com.google.firebase.FirebaseApp
import com.silica.assistant.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SilicaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        
        startKoin {
            androidLogger()
            androidContext(this@SilicaApplication)
            modules(appModule)
        }
    }
}
