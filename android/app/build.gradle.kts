import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// coach.properties (public, versionné) puis local.properties (poste du développeur) qui peut le surcharger
val localProps = Properties().apply {
    listOf("coach.properties", "local.properties").forEach { name ->
        val f = rootProject.file(name)
        if (f.exists()) f.inputStream().use { load(it) }
    }
}
fun cfg(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name.uppercase().replace('.', '_')) ?: "")

android {
    namespace = "fr.coachsportif.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.coachsportif.app"
        minSdk = 29          // Android 10+ (Galaxy S25 Ultra : Android 15/16)
        targetSdk = 35
        // numéro de compilation GitHub → chaque APK est une version plus récente que la précédente
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        val sha = (System.getenv("GITHUB_SHA") ?: "local").take(7)
        versionCode = build
        versionName = "0.2.$build ($sha)"
        // URL + clé PUBLIQUE Supabase (publishable/anon). Aucun secret : la sécurité repose sur le RLS.
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("coach.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("coach.supabaseAnonKey")}\"")
        buildConfigField("String", "UPDATE_URL",
            "\"https://github.com/lucienbigot07/coach-sportif/releases/download/apk-latest/coach-sportif.apk\"")
    }

    signingConfigs {
        // Clé de DEBUG partagée (dans Git) : chaque APK compilé par GitHub s'installe par-dessus le précédent
        // sans perdre les données locales. Ne jamais l'utiliser pour une publication Play Store.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
