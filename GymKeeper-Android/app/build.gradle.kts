plugins {
    id("com.android.application")
}

android {
    namespace = "ru.gymkeeper.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.gymkeeper.offline"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("offline") {
            storeFile = file("gymkeeper-offline.jks")
            storePassword = "GymKeeper2026Offline"
            keyAlias = "gymkeeper"
            keyPassword = "GymKeeper2026Offline"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("offline")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("offline")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
