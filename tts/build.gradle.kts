plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.liveai.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Don't compress ONNX models or SentencePiece model in assets
    androidResources {
        noCompress += listOf("onnx", "model")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // ONNX Runtime
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.0")
}
