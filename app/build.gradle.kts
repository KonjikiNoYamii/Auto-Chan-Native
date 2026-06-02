import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystoreFile.exists()) {
    keystoreProps.load(keystoreFile.inputStream())
}

android {
    namespace = "com.silica.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.silica.assistant"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreProps["storeFile"]?.toString()?.let { rootProject.file(it) }
            storePassword = keystoreProps["storePassword"]?.toString()
            keyAlias = keystoreProps["keyAlias"]?.toString()
            keyPassword = keystoreProps["keyPassword"]?.toString()
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.jcraft:jsch:0.1.55")

    implementation("com.github.yalantis:ucrop:2.2.8")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation(
        "androidx.core:core-ktx:1.12.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.8.2"
    )

    implementation(
        platform(
            "androidx.compose:compose-bom:2024.02.00"
        )
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    implementation(
        "androidx.compose.material:material-icons-extended"
    )
}
