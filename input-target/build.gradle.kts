plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.zenda.inputbridge.target"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zenda.inputbridge.target"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation(libs.junit)
}
