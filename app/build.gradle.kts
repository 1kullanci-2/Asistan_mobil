plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.edith.asistan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edith.asistan"
        minSdk = 26
        targetSdk = 35
        // Her GitHub derlemesinde artar; telefon yeni APK'yı güncelleme olarak görür.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + run
        versionName = "0.3.$run"
    }
    // Sabit imza: her derleme aynı anahtarla imzalanır, böylece üstüne kurulunca ayarlar silinmez.
    signingConfigs {
        getByName("debug") {
            storeFile = file("edith-imza.jks")
            storePassword = "edith2026"
            keyAlias = "edith"
            keyPassword = "edith2026"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
