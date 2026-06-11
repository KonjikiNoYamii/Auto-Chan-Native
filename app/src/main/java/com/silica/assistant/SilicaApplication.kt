package com.silica.assistant

import android.app.Application
import com.silica.assistant.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SilicaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@SilicaApplication)
            modules(appModule)
        }
    }
}
