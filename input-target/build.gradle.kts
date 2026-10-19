plugins {
    id("com.android.application")
}

android {
    namespace = "dev.zenda.inputbridge.target"
    compileSdk = 37

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

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
