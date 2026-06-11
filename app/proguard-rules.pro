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

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# General optimization
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
