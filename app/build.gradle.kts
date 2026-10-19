plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release builds are signed with signing/release.p12 when SIGNING_PASSWORD is set
// (a GitHub Actions secret). The keystore is useless without that password.
// Without it, release builds fall back to the debug key.
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotEmpty() }

android {
    namespace = "dk.benjy.ytdlp"
    compileSdk = 35

    defaultConfig {
        applicationId = "dk.benjy.ytdlp"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (signingPassword != null) {
            create("release") {
                storeFile = rootProject.file("signing/release.p12")
                storeType = "pkcs12"
                storePassword = signingPassword
                keyAlias = "ytdlp"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingPassword != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    // One APK per CPU type keeps each download small (Python + ffmpeg are big).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // youtubedl-android runs Python/ffmpeg from extracted native libraries.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
}
