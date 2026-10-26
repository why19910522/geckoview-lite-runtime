plugins {
    id("com.android.application")
}

android {
    namespace = "dev.geckolite.smoke"
    compileSdk = 35
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.geckolite.smoke"
        minSdk = 26
        targetSdk = 30
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
    }

    lint {
        // This local verification host intentionally retains the requested targetSdk 30.
        // Only the Play publication policy check is excluded; runtime/API checks remain enabled.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(providers.gradleProperty("geckoCoordinate").get())
}
