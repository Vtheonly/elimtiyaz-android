import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.elimtiyaz.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testOptions.targetSdk = 35

        // Read Supabase credentials from `.env` or `local.properties` if present,
        // otherwise default to empty strings (DataModule runs in mock mode with offline seed data).
        val localProps = Properties()
        val envFile = rootProject.file(".env")
        val localFile = rootProject.file("local.properties")
        if (envFile.exists()) {
            envFile.inputStream().use { localProps.load(it) }
        } else if (localFile.exists()) {
            localFile.inputStream().use { localProps.load(it) }
        }
        val supabaseUrl = localProps.getProperty("SUPABASE_URL", System.getenv("SUPABASE_URL") ?: "").trim()
        val supabaseAnonKey = localProps.getProperty("SUPABASE_ANON_KEY", System.getenv("SUPABASE_ANON_KEY") ?: "").trim()
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    api(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Supabase + Ktor
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.core)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.content)
    implementation(libs.ktor.json)
    implementation(libs.ktor.auth)
    implementation(libs.ktor.logging)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt-Work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Logging
    implementation(libs.kermit)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.arch.core.testing)
}
