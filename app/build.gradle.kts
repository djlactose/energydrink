import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing credentials come from keystore.properties (local, git-ignored)
// or, failing that, from environment variables (CI). When neither is present the
// release build is simply left unsigned instead of failing.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingSetting(propertyName: String, envName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

// Release builds get their version from CI so every bundle uploaded to Play has
// a higher versionCode than the last. The fallback keeps local builds on the
// version committed here. versionName follows the existing "1.<code>" pairing.
val fallbackVersionCode = 10
val appVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: fallbackVersionCode
val appVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "1.$appVersionCode"

android {
    namespace = "com.djlactose.energydrink"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.djlactose.energydrink"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val store = signingSetting("storeFile", "KEYSTORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingSetting("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingSetting("keyAlias", "KEY_ALIAS")
                keyPassword = signingSetting("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}