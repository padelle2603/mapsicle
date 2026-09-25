plugins {
    id("com.android.application")
}

val maptilerApiKey = System.getenv("MAPTILER_API_KEY").orEmpty()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val keystorePath = System.getenv("KEYSTORE_PATH")

val releaseVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 2
val releaseVersionName = (findProperty("versionName") as String?) ?: "0.1"

android {
    namespace = "com.padelle.mapsicle"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.padelle.mapsicle"
        minSdk = 23
        targetSdk = 36
        buildConfigField("String", "MAPTILER_API_KEY", "\"$maptilerApiKey\"")
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.maplibre.gl:android-sdk-opengl:13.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
