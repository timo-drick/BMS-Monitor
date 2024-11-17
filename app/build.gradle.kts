import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.drick.bmsmonitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.drick.bmsmonitor"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        val mapboxToken = System.getenv("MAPBOX_TOKEN") ?:
        Properties().let { properties ->
            try {
                properties.load(rootProject.file("local.properties").bufferedReader())
                properties.getProperty("MAPBOX_TOKEN")
            } catch (err: Throwable) {
                "NO_TOKEN"
            }
        }
        buildConfigField("String", "MAPBOX_TOKEN", """"$mapboxToken"""")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)

    implementation(libs.play.services.location) //Fused location
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.maps.annotation)

    implementation(libs.vico.compose)    // Chart library
    implementation(libs.vico.compose.m3)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    lintChecks(libs.compose.lint.checks) // https://slackhq.github.io/compose-lints
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.foundation:foundation:1.7.0-beta07")
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}