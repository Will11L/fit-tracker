import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
}

// Hotes serveur : lus depuis local.properties (non versionne) pour ne pas publier
// l'infrastructure reelle. Valeurs de repli = placeholders, a surcharger localement :
//   server.pi.host=mon-hote.mon-tailnet.ts.net
//   server.dev.host=192.168.x.x:8000
val serverProps = Properties()
val serverPropsFile = rootProject.file("local.properties")
if (serverPropsFile.exists()) {
    serverPropsFile.inputStream().use { serverProps.load(it) }
}
val piHost: String = serverProps.getProperty("server.pi.host") ?: "pi.example.ts.net"
val devHost: String = serverProps.getProperty("server.dev.host") ?: "10.0.2.2:8000"

android {
    namespace = "com.example.sportapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sportapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Presets de serveur selectionnables a l'execution (ecran Sync)
        buildConfigField("String", "PC_LAN_API", "\"http://$devHost/api/v1/\"")
        buildConfigField("String", "PC_LAN_WS", "\"ws://$devHost/api/v1/ws\"")
        buildConfigField("String", "PI_PROD_API", "\"https://$piHost/api/v1/\"")
        buildConfigField("String", "PI_PROD_WS", "\"wss://$piHost/api/v1/ws\"")
    }

    buildTypes {
        debug {
            // Dev local : serveur FastAPI sur le PC (hote dans local.properties)
            buildConfigField("String", "API_BASE_URL", "\"http://$devHost/api/v1/\"")
            buildConfigField("String", "WS_BASE_URL", "\"ws://$devHost/api/v1/ws\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Prod : serveur sur la Pi via Tailscale (hote dans local.properties)
            buildConfigField("String", "API_BASE_URL", "\"https://$piHost/api/v1/\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://$piHost/api/v1/ws\"")
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
            freeCompilerArgs.addAll(
                "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // T1.1.e (2026-05-06) : Robolectric a besoin des resources Android packagées
        // pour simuler l'env Android sans émulateur (Room in-memory, Context, etc.).
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // T1.1.f (2026-05-06) : MigrationTestHelper cherche les schemas JSON Room
        // dans les assets de l'APK de test. Le ksp les génère dans $projectDir/schemas.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

// T1.1.f (2026-05-06) : Room 2.8.4 demande kotlinx-serialization 1.8.1, mais
// Kotlin 2.3.0 force transitive 1.7.3 → AbstractMethodError sur
// `GeneratedSerializer.typeParametersSerializers()` au loadSchema.
// Force la version 1.8.1 partout pour matcher Room.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // i18n (Session A 2026-05-11) : fournit AppCompatDelegate.setApplicationLocales()
    // qui persiste la locale per-app (API 33+ via LocaleManager système, API < 33
    // via backport). N'oblige PAS à passer MainActivity en AppCompatActivity --
    // l'API est statique et marche depuis ComponentActivity.
    implementation("androidx.appcompat:appcompat:1.7.0")

    // DataStore (pour persister tes settings)
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Health Connect : lecture read-only des données santé (pas, distance,
    // calories actives, fréquence cardiaque, sommeil), vendor-agnostic.
    implementation(libs.androidx.health.connect)

    // Wearable Data Layer : réception du canal live montre → téléphone
    // (WearableListenerService) + envoi de la requête pull (NodeClient/MessageClient).
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Scan de code-barres nutrition (Google Code Scanner) : UI de scan fournie par
    // Play services, module téléchargé à la demande, aucune permission caméra à
    // gérer soi-même. Formats produits (EAN-13/8, UPC-A/E) → lookup Open Food Facts.
    implementation(libs.play.services.code.scanner)

    // V8.2-3 : EncryptedSharedPreferences pour stocker les JWT (access + refresh)
    // chiffres au repos via Android Keystore (AES256_GCM).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-paging:2.8.4")
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.ui.unit)
    ksp("androidx.room:room-compiler:2.8.4")

    // Paging (Sync Settings data grid 2026-05-26)
    implementation("androidx.paging:paging-runtime:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    // Hilt pour ViewModel Compose pour l'injection de dépendances
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-compiler:2.58")

    // Hilt pour Jetpack Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Phase 3 (2026-05-12) : WorkManager pour scheduler les rappels Task
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // Retrofit pour les appels API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // swipeRefresh pour reactualiser le pull to refresh
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")

    // pour minSdk 26
    implementation("com.jakewharton.threetenabp:threetenabp:1.4.9")

    // pour les drag and drop
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // pour bottomSheet
    implementation("androidx.compose.material3:material3:1.4.0") // ou version plus récente

    // pour check le focus avec le clavier
    implementation ("com.google.accompanist:accompanist-insets:0.30.1")

    // pour les images
    implementation("androidx.compose.foundation:foundation:1.10.1")

    // pour les charts
    implementation("com.patrykandpatrick.vico:compose-m3:2.4.1")
    implementation("com.patrykandpatrick.vico:core:2.4.1")

    // pour les icons
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    // T1.1.d (2026-05-06) : fondation tests JVM (cf. TODO_FEATURES.md §0).
    // Mockk : mock des dépendances (Retrofit APIs, DAOs Room) pour les VMs.
    // kotlinx-coroutines-test : runTest + StandardTestDispatcher pour Flow/suspend.
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    // T1.1.e (2026-05-06) : Room in-memory tests via Robolectric (env Android
    // simulé en JVM, pas d'émulateur). room-testing fournit MigrationTestHelper.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // T1.1.f (2026-05-06) : MigrationTestHelper pour tests Room migrations
    // sur device réel (vraie SQLite Android, pas Robolectric).
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}