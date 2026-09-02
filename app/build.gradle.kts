import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.gradle.api.tasks.testing.Test

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
  // AGP 8.8.0 officially supports compileSdk = 35; AGP 8.9.1+ is required
  // for compileSdk = 36. We pin to 35 to avoid the "compileSdk 36 is higher
  // than the maximum supported 35" warning that could become a hard error
  // under strict mode. To target Android 16 (API 36), bump `agp` to 8.9.1+
  // and restore compileSdk/targetSdk to 36.
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.elimtiyazstaff.bxmzlx"
    minSdk = 24
    targetSdk = 35
    versionCode = 2
    versionName = "2.0.0"
    multiDexEnabled = true

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // T-082 / ARCH-008: core-library desugaring — the ONLY correct fix for
    // the 337 NewApi errors (all API-26 java.time symbols: LocalDate,
    // Instant, ZoneOffset… used throughout the financial engine on minSdk
    // 24). Without it the lint gate was inoperable (339 pre-existing
    // errors, no baseline ever existed); with it lint itself acknowledges
    // "or core library desugaring" for every one of these findings.
    isCoreLibraryDesugaringEnabled = true
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    // T-082 / ARCH-008 (the T-078 desktop precedent): the lint gate is
    // RESTORED. Errors abort the build; the committed baseline pins the
    // pre-existing WARNING backlog to exact findings (116 at creation:
    // GradleDependency 90 [dependency-version suggestions], UnusedResources
    // 8, AndroidGradlePluginVersion 3, DiscouragedApi 2, IconDipSize 2,
    // UnusedAttribute 1, RedundantLabel 1, SelectedPhotoAccess 1,
    // LockedOrientationActivity 1, NonResizeableActivity 1,
    // ComposableNaming 1, ModifierParameter 1, UnnecessaryComposedModifier
    // 1, SuspiciousIndentation 0 [both fixed in code], NewApi 0 [fixed by
    // core-library desugaring]). Any NEW finding fails the gate — the
    // backlog shrinks by EDITING the baseline, never by widening it.
    baseline = file("lint-baseline.xml")
    abortOnError = true
    warningsAsErrors = false
  }
  sourceSets {
    // T-046-gap: the exported Room schema history (app/schemas/*.json) must
    // reach Robolectric's asset manager. Robolectric resolves assets from
    // android_merged_assets (build/intermediates/assets/debug/mergeDebugAssets
    // — see generateDebugUnitTestConfig), which merges the MAIN + DEBUG
    // sourceSets, NOT the test sourceSet. Scoping to `debug` keeps the
    // RELEASE APK free of test fixtures while letting MigrationTestHelper
    // createDatabase(name, N) from the committed history.
    getByName("debug").assets.srcDir("$projectDir/schemas")
  }
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

// ARCH-012 (19th session, 2026-09-02): the release variant's applicationId
// suffix (.bxmzlx) defeats Robolectric's launcher-activity resolution
// (robolectric/robolectric#4736) — createComposeRule() fails with
// "Unable to resolve activity for Intent { … cmp=…bxmzlx/ComponentActivity }"
// ONLY under the suffixed package (the debug variant of the same test is
// green). The screenshot smoke test pins the Compose rendering pipeline
// (theme/typography — variant-independent), so it is a DEBUG-variant gate by
// design; the release variant gets this documented exclusion instead of a
// permanently red suite. Removing this exclusion requires fixing Robolectric's
// release-manifest resolution (needs a manifest-merge investigation).
//
// ARCH-012 second exclusion (same session): RoomSchemaUpgradeT046GapTest needs
// the committed app/schemas/*.json as Robolectric ASSETS, and the schemas are
// scoped to the DEBUG sourceSet DELIBERATELY (see the sourceSets comment —
// the release APK must stay free of test fixtures). Robolectric's release
// variant resolves assets from main+release only, so the MigrationTestHelper
// schema history is unreachable there by design. The upgrade test's canonical
// gate is the debug variant (where it runs 4/4 on the real committed history).
tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
  filter {
    excludeTestsMatching("com.example.GreetingScreenshotTest")
    excludeTestsMatching("com.example.infrastructure.room.RoomSchemaUpgradeT046GapTest")
  }
}

// T-046-gap: Room schema export location (companion to exportSchema=true on
// ElImtiyazDatabase). Every schema bump lands as app/schemas/<db>/<N>.json
// and MUST be committed — MigrationTestHelper upgrade tests depend on the
// committed history.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
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

  // ── multiplatform-settings (Supabase Auth session persistence) ───────
  // Required by SettingsSessionManager — backs the JWT refresh-token
  // store so users stay signed in across app cold-starts.
  implementation(libs.multiplatform.settings)
  implementation(libs.multiplatform.settings.coroutines)

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

  // ── Core-library desugaring (T-082 / ARCH-008) ─────────────────────
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

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
  // T-046-gap: MigrationTestHelper for schema-upgrade data-preservation tests.
  testImplementation(libs.androidx.room.testing)
  // FIX (broken screenshot test): AppNavHost uses hiltViewModel() — the test
  // needs the Hilt test environment or it crashes with
  // "GeneratedComponentManager" IllegalStateException.
  testImplementation(libs.hilt.android.testing)
  kspTest(libs.hilt.compiler)
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
