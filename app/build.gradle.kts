plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Build number from GitHub Actions so every new APK installs as an update.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "de.zeroxion.einkaufsliste"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.zeroxion.einkaufsliste"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // Fixed key so that updates install over older versions.
        create("release") {
            storeFile = file("keystore/einkaufsliste.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "einkaufsliste"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "einkaufsliste"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "einkaufsliste"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    // Gemini / AppFunctions
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)
}
