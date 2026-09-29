import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// O google-services.json vem do console do Firebase e não é versionado (ver README). Sem ele o
// plugin quebraria o build; o app compila e o login só avisa que o Firebase não está configurado.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Lê o local.properties (que NÃO vai para o GitHub)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.projeto.marvel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.projeto.marvel"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "COMIC_VINE_API_KEY",
            "\"${localProperties.getProperty("COMIC_VINE_API_KEY") ?: ""}\""
        )
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
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("detekt-baseline.xml")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil)
    // Splash oficial do Android (API de SplashScreen com compat), sem tela extra no app.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.palette)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.ai)
    // JsonObject/JsonElement da API de function calling do firebase-ai (só a lib, sem plugin).
    implementation(libs.kotlinx.serialization.json)
    // App Check: o AI Logic está "aplicado" no console e recusa chamada sem token. Release prova
    // que é o app de verdade (Play Integrity); debug usa um token cadastrado no console.
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    // Login com Google: seletor de contas nativo (Credential Manager) + token do Google ID
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
