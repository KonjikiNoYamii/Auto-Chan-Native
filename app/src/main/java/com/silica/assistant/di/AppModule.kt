package com.silica.assistant.di

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.silica.assistant.core.llm.db.SilicaDatabase
import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import com.silica.assistant.core.llm.LlmRepository
import com.silica.assistant.core.llm.KtorLlmRepository
import com.silica.assistant.core.llm.MoodManager
import com.silica.assistant.core.auth.AuthRepository
import com.silica.assistant.core.ActivityDetector

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            SilicaDatabase::class.java,
            "silica_database"
        ).fallbackToDestructiveMigration().build()
    }
    
    single { get<SilicaDatabase>().chatDao() }
    single { get<SilicaDatabase>().userFactDao() }
    single { get<SilicaDatabase>().userProfileDao() }
    single { get<SilicaDatabase>().questDao() }
    single { get<SilicaDatabase>().achievementDao() }
    single { get<SilicaDatabase>().friendDao() }
    single { get<SilicaDatabase>().socialMessageDao() }

    single { com.silica.assistant.core.llm.AchievementManager(get()) }
    single { MoodManager(get(), get(), get(), get(), get()) }
    single { AuthRepository(get(), get(), get(), get(), get(), get(), androidContext()) }
    single { com.silica.assistant.core.auth.SocialRepository(get(), get(), get()) }
    single { ActivityDetector(androidContext()) }

    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    prettyPrint = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 60000
            }
        }
    }
    
    single<LlmRepository> { KtorLlmRepository(get(), get(), get()) }
    
    factory { com.silica.assistant.ui.chat.ChatViewModel(get(), get()) }
    factory { com.silica.assistant.ui.chat.SocialViewModel(get(), get()) }
    factory { (otherUserId: String) -> com.silica.assistant.ui.chat.SocialChatViewModel(otherUserId, get(), get()) }
}
