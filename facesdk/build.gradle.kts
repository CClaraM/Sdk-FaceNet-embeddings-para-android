plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dcl.facesdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        targetSdk = 36
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources.excludes += setOf("META-INF/**", "LICENSE*", "licenses/**")
    }

}

dependencies {
    // LiteRT 1.4.0 (equivalente TFLite 1.4.0)
    // TensorFlow Lite dependencies
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.litert.gpu.api)
    implementation(libs.litert.support)
    // MediaPipe Tasks – Face Detection
    implementation(libs.tasks.vision)

    // ML KIT
    implementation(libs.face.detection)
    implementation(libs.kotlinx.coroutines.play.services)

    // Exif para corrección de rotación si alimentas por URI o JPEG
    implementation(libs.androidx.exifinterface)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
}