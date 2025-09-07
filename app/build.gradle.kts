// ================================================================================
// ### FILE: app/build.gradle.kts
// ================================================================================
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hamoon.uncleted"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.hamoon.uncleted"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    // Kotlin standard library is implicitly included by the Kotlin plugin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)

    // For sending emails
    implementation(libs.sun.mail.android)
    implementation(libs.sun.activation.android)

    // For the Settings screen
    implementation(libs.androidx.preference.ktx)

    // Optional: For future web dashboard integration
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.converter.gson)

    // Coroutines for running tasks in the background smoothly
    implementation(libs.kotlinx.coroutines.android)

    // Jetpack Security
    implementation(libs.androidx.security.crypto)

    // Location Services for getting GPS coordinates
    implementation(libs.google.play.services.location)

    // CameraX for modern, easy-to-use camera functions
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // Lifecycle
    implementation(libs.androidx.lifecycle.service)

    // WorkManager for robust background tasks
    implementation(libs.androidx.work.runtime.ktx)

    // ### FIX: Added the missing Biometric library dependency ###
    implementation("androidx.biometric:biometric:1.1.0")

    // Default test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}