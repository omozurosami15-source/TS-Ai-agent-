plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tsagent.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.tsagent.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1"
    }
    // Real signing key (optional): set via GitHub secrets so that updates install over older versions.
    val ks = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ks != null && File(ks).exists()) {
            create("release") {
                storeFile = File(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
