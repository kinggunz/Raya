plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ayaraya.voicegunz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ayaraya.voicegunz"
        minSdk = 26          // Android 8.0
        targetSdk = 34       // berjalan di Android 8 s/d 15
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
