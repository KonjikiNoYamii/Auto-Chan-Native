# JSch optional dependencies not on Android
-dontwarn com.jcraft.jzlib.**
-dontwarn org.ietf.jgss.**

# Keep JSch (used via reflection)
-keep class com.jcraft.jsch.** { *; }

# Keep uCrop
-keep class com.yalantis.ucrop.** { *; }

# Keep your app models and entities (Room/Serialization need these)
-keep class com.silica.assistant.core.llm.model.** { *; }
-keep class com.silica.assistant.model.** { *; }

# Obfuscate internal logic but keep Entry points
-keep @interface androidx.room.*
-keep class * extends androidx.room.RoomDatabase
-keep interface * extends androidx.room.RawDao
-keep @androidx.room.Dao interface *
-keep @androidx.room.Entity class *

# Koin
-keep class org.koin.** { *; }

# Ktor & Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# SLF4J (Ktor/OkHttp dependencies)
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Ktor engine (OkHttp)
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep enum classes (used in serialization)
-keepclassmembers enum * { *; }

# General optimization
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
