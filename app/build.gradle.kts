plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "in.adroit.indoresunclock"
    compileSdk = 35
    defaultConfig {
        applicationId = "in.adroit.indoresunclock"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
