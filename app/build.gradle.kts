@file:Suppress("DEPRECATION")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.css_apps_m3.password_manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.css_apps_m3.password_manager"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // JDBC drivers bundle META-INF files that conflict with each other
            excludes += listOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Compose Material3
    implementation("androidx.compose.material3:material3:1.3.2")

    // Extended Material Icons (Visibility, Error, CheckCircle, etc.)
    implementation("androidx.compose.material:material-icons-extended")

    // Compose
    implementation("androidx.navigation:navigation-compose:2.9.3")

    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    // CSV Parser
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("com.google.code.gson:gson:2.13.1")

    // AndroidX Security für EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0")

    // --- SQL Cloud Sync (JDBC) ---
    // MySQL / MariaDB direct JDBC driver (Android-compatible legacy line)
    implementation("mysql:mysql-connector-java:5.1.49")
    // Kotlin Coroutines (für Dispatchers.IO bei DB-Operationen)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
