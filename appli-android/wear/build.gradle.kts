import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.sportapp.wear"
    compileSdk = 36

    defaultConfig {
        // applicationId PARTAGÉ avec l'app téléphone : le Wearable Data Layer
        // apparie montre <-> téléphone par packageName commun + même signature.
        applicationId = "com.example.sportapp"
        // Galaxy Watch 4 = Wear OS 3 = API 30.
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signé avec le keystore debug (comme l'app téléphone) → même
            // signature, indispensable au pairing Data Layer.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Wear OS Compose (Material + Foundation)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)

    // Capteurs santé montre (FC live) + canal Data Layer vers le téléphone
    implementation(libs.androidx.health.services.client)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    // health-services-client expose des ListenableFuture (guava) → requis au classpath.
    implementation("com.google.guava:guava:33.6.0-android")
    // await() sur les ListenableFuture de Health Services (getCapabilitiesAsync).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
