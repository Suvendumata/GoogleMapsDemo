import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Load local.properties to read MAPS_API_KEY if defined
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
    ?: System.getenv("MAPS_API_KEY")
    ?: "YOUR_API_KEY_HERE"

if (mapsApiKey == "YOUR_API_KEY_HERE" || mapsApiKey.isBlank()) {
    logger.warn("WARNING: MAPS_API_KEY is not configured in local.properties. Google Maps will render as a blank beige screen until a valid API key is set.")
}

android {
    namespace = "com.example.googlemapsdemo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.googlemapsdemo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}