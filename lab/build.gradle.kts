plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.zenda.inputbridge.lab"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.zenda.inputbridge.lab"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.libadb)
    implementation(libs.sun.security.android)
    implementation(libs.conscrypt)

    testImplementation(libs.junit)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
