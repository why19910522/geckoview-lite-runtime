plugins {
    id("com.android.application")
}

android {
    namespace = "dev.geckolite.smoke"
    compileSdk = 35
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = providers.gradleProperty("smokeApplicationId").getOrElse("dev.geckolite.smoke")
        minSdk = 26
        targetSdk = 30
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("boolean", "EXTERNAL_CORE", providers.gradleProperty("externalCore").getOrElse("false"))
        buildConfigField("boolean", "ISOLATED_CORE", providers.gradleProperty("isolatedCore").getOrElse("false"))
        buildConfigField("boolean", "ZYGOTE_CORE", providers.gradleProperty("zygoteCore").getOrElse("false"))
        buildConfigField("boolean", "RUNTIME_PROBE", providers.gradleProperty("runtimeProbe").getOrElse("false"))
        buildConfigField("boolean", "EXPECT_TRIMMED", providers.gradleProperty("expectTrimmed").getOrElse("false"))
    }

    sourceSets.getByName("main") {
        if (providers.gradleProperty("externalCore").getOrElse("false").toBoolean()) {
            java.srcDir("src/external/java")
            assets.srcDir(providers.gradleProperty("probeTrustAssets").get())
        } else {
            java.srcDir("src/bundled/java")
        }
    }
    buildFeatures { buildConfig = true }

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
    if (providers.gradleProperty("externalCore").getOrElse("false").toBoolean()) {
        implementation(project(":runtime-external"))
    }
}
