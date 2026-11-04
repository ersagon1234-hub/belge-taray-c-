plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ornek.belgetarayici"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ornek.belgetarayici"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
}

dependencies {
    // Belge tarama arayüzü (Google Play hizmetleri üzerinden sağlanır).
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // Çevrimdışı OCR: model uygulamanın içine gömülüdür, internet gerekmez.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ComponentActivity / registerForActivityResult, FileProvider, WindowInsets.
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
