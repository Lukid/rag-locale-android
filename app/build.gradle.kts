import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jlleitschuh.gradle.ktlint")
}

// Client ID dell'OAuth app HuggingFace, letto da local.properties (non committato — vedi
// .env.example e README). Se assente, resta vuoto: il login HF degrada con grazia e i modelli
// pubblici (es. l'LLM) restano scaricabili lo stesso (design download-modelli-in-app, D5).
val hfOauthClientId: String =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }.getProperty("hfOauthClientId", "")

android {
    namespace = "it.netseven.raglocale"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.netseven.raglocale"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Download in-app: Client ID OAuth HF (vuoto = login non configurato, vedi sopra) e
        // schema di redirect per AppAuth (deve combaciare con HuggingFaceOAuthConfig.REDIRECT_URI).
        buildConfigField("String", "HF_OAUTH_CLIENT_ID", "\"$hfOauthClientId\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "it.netseven.raglocale"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Demo: niente offuscamento per non rischiare lo stripping di simboli
            // nativi/reflection di LiteRT-LM e Hilt. Da rivedere a valle dell'M1.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Richiesto da LiteRT-LM per le librerie native (GPU/OpenCL).
        jniLibs {
            useLegacyPackaging = true
            // Da localagents-rag usiamo solo l'embedder Gemma (vedi design M2, D2):
            // escludiamo le JNI dei moduli non usati (~46 MB sull'APK).
            excludes +=
                setOf(
                    "**/libgecko_embedding_model_jni.so",
                    "**/libsqlite_vector_store_jni.so",
                    "**/libtext_chunker_jni.so",
                )
        }
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/DEPENDENCIES",
                    "/META-INF/INDEX.LIST",
                )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric ha bisogno delle risorse/manifest mergiati per girare in JVM.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // --- Inferenza on-device ---
    // LiteRT-LM: runtime principale (artefatti .litertlm). Vedi memory reference-anti-vocale.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.12.0")
    // MediaPipe GenAI: fallback text-only (artefatti .task).
    implementation("com.google.mediapipe:tasks-genai:0.10.33")
    // AI Edge RAG SDK: usiamo SOLO il modulo embedder (GemmaEmbeddingModel + tokenizer
    // sentencepiece via JNI self-contained). Vector store e chunker restano fatti in casa
    // (design M2, D1/D2); le JNI non usate sono escluse nel blocco packaging.
    implementation("com.google.ai.edge.localagents:localagents-rag:0.3.0")

    // --- Jetpack Compose ---
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    // --- AndroidX core / lifecycle / activity ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Tema XML Material3 referenziato dal manifest (@style/Theme.RagLocale).
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- Persistenza preferenze ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Rete (download modelli — usato a partire dalla rifinitura download in-app) ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- Download in-app: OAuth HuggingFace ---
    // AppAuth: OAuth 2.0 Authorization Code + PKCE via Custom Tabs (porting da anti-vocale).
    implementation("net.openid:appauth:0.11.1")
    // EncryptedSharedPreferences: persistenza cifrata dei token HF (access/refresh/scadenza).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- Ingestion documenti (sorgenti PDF e URL — design M2 D9) ---
    // PdfBox-Android: estrazione del layer testo dei PDF. Niente OCR (fuori scope): un PDF
    // senza testo estraibile viene rilevato e segnalato all'utente.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Jsoup + Readability4J: estrazione del contenuto principale da una pagina web, con
    // fallback al testo grezzo se Readability non isola l'articolo. jsoup è dichiarato a
    // versione esplicita (più recente di quella che trascinerebbe Readability4J).
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("net.dankito.readability4j:readability4j:1.0.8")

    // --- Hilt DI ---
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // --- Debug/preview Compose ---
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Test unitari JVM (logica pura: backend, contesto chat, cap, stato modello, storage) ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Robolectric: SQLite reale in-process per il round-trip del vector store senza device
    // (la lezione M1 sconsiglia connectedAndroidTest sul Poco — wipe dei dati). Vedi design M2 D3.
    testImplementation("org.robolectric:robolectric:4.16")

    // --- Test strumentati on-device (smoke inferenza reale, vedi InferenceSmokeTest) ---
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
