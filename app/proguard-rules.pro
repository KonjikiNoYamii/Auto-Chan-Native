# JSch optional dependencies not on Android
-dontwarn com.jcraft.jzlib.**
-dontwarn org.ietf.jgss.**

# Keep JSch (used via reflection)
-keep class com.jcraft.jsch.** { *; }

# Keep uCrop
-keep class com.yalantis.ucrop.** { *; }

# Keep your app models
-keep class com.silica.assistant.model.** { *; }

# Keep all app classes (prevents R8 from stripping/obfuscating)
-keep class com.silica.assistant.** { *; }
