plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Load API key from local.properties (gitignored)
import java.io.FileInputStream
import java.util.Properties

val localProperties = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    FileInputStream(localFile).use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.example.yooni"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.yooni"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Makes API key available as BuildConfig.OPENAI_API_KEY
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("OPENAI_API_KEY", "")}\"")
        // Makes Picovoice Access Key available as BuildConfig.PICOVOICE_ACCESS_KEY
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${localProperties.getProperty("PICOVOICE_ACCESS_KEY", "")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // OpenAI API (Whisper, Chat, TTS)
    implementation("com.aallam.openai:openai-client:4.1.0")
    implementation("io.ktor:ktor-client-okhttp:3.0.0")

    // Picovoice Porcupine (Wake Word)
    implementation("ai.picovoice:porcupine-android:4.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}