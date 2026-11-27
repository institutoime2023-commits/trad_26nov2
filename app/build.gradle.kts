plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.orvex.demucstest"
    compileSdk = 35
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.orvex.demucstest"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-demucs-prueba"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            cppFlags += listOf("-O3", "-std=c++17")
            arguments += listOf("-DANDROID_STL=c++_shared", "-DSDENGINE_JNI=ON", "-DSDENGINE_JNI_CLASS=com/orvex/demucstest/NativeEngine")
        } }
    }
    buildTypes { release { isMinifyEnabled = false }; debug { isDebuggable = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
