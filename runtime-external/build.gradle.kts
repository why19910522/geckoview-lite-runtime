plugins { id("com.android.library") }
android {
    namespace = "dev.geckolite.runtime"
    compileSdk = 35
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { compileOnly(providers.gradleProperty("geckoCoordinate").get()) }
