import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.elimtiyazstaff.bxmzlx"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "2.0.0"
    multiDexEnabled = true

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Default Supabase config — overridden by .env via the secrets plugin.
    buildConfigField("String", "SUPABASE_URL", "\"https://your-project.supabase.co\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"your-anon-key\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    val debugKeystoreFile = file("${rootDir}/debug.keystore")
    if (debugKeystoreFile.exists()) {
      create("debugConfig") {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val customDebug = signingConfigs.findByName("debugConfig")
      if (customDebug != null) {
        signingConfig = customDebug
      }
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
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/INDEX.LIST"
      excludes += "META-INF/io.netty.versions.properties"
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))

  // ── AndroidX core ─────────────────────────────────────────────────────
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // ── Compose ───────────────────────────────────────────────────────────
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)

  // ── Hilt (DI) ─────────────────────────────────────────────────────────
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.hilt.work)
  ksp(libs.hilt.compiler)
  ksp(libs.hilt.androidx.compiler)

  // ── Supabase Kotlin SDK ───────────────────────────────────────────────
  implementation(libs.supabase.kt)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  implementation(libs.supabase.storage)
  implementation(libs.supabase.functions)
  implementation(libs.ktor.client.android)
  implementation(libs.ktor.client.core)

  // ── Kotlinx Serialization + Datetime ──────────────────────────────────
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.datetime)

  // ── Coroutines ────────────────────────────────────────────────────────
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // ── Room (offline cache + sync queue) ─────────────────────────────────
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)

  // ── WorkManager (sync) ────────────────────────────────────────────────
  implementation(libs.androidx.work.runtime.ktx)

  // ── CameraX ───────────────────────────────────────────────────────────
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // ── DataStore + EncryptedPreferences ──────────────────────────────────
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.multidex)

  // ── Accompanist permissions ───────────────────────────────────────────
  implementation(libs.accompanist.permissions)

  // ── Coil (image loading) ──────────────────────────────────────────────
  implementation(libs.coil.compose)

  // ── Play Services Location (optional, for driver dashboards) ──────────
  implementation(libs.play.services.location)

  // ── Firebase (FCM push notifications) ─────────────────────────────────
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.appcheck.recaptcha)

  // ── Networking (kept for compatibility; Supabase SDK uses Ktor) ───────
  implementation(libs.okhttp)
  implementation(libs.logging.interceptor)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  ksp(libs.moshi.kotlin.codegen)

  // ── Testing ───────────────────────────────────────────────────────────
  testImplementation(kotlin("test"))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
