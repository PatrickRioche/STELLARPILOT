plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun gitShortSha(): String {
    return try {
        ProcessBuilder(
            "git",
            "rev-parse",
            "--short",
            "HEAD"
        )
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
            .ifBlank { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}

val gitSha = gitShortSha()

val buildTimestamp =
    System.getenv("STELLARPILOT_BUILD_TIMESTAMP")
        ?: "local"

android {
    namespace = "fr.stellarpilot.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.stellarpilot.app"
        minSdk = 26
        targetSdk = 36

        versionCode = 13
        versionName = "0.6.7"

        buildConfigField(
            "String",
            "GIT_SHA",
            "\"$gitSha\""
        )
    }

    flavorDimensions += "backend"

    productFlavors {
        create("simulation") {
            dimension = "backend"
            applicationIdSuffix = ".simulation"
            versionNameSuffix = "-simulation-$buildTimestamp"

            buildConfigField(
                "String",
                "BACKEND_MODE",
                "\"SIMULATION\""
            )

            buildConfigField(
                "String",
                "SERVER_BASE_URL",
                "\"http://10.42.0.1:8008/\""
            )
        }

        create("device") {
            dimension = "backend"
            versionNameSuffix = "-device-$buildTimestamp"

            // Field default for StellarPilot Pi hotspot. The Preparation
            // screen exposes one editable address field and persists any
            // alternative address chosen by the observer.
            buildConfigField(
                "String",
                "BACKEND_MODE",
                "\"DEVICE\""
            )

            buildConfigField(
                "String",
                "SERVER_BASE_URL",
                "\"http://10.42.0.1:8000/\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
