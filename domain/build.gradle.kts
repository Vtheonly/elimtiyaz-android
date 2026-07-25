plugins {
    alias(libs.plugins.kotlin.android) apply false
    id("java-library")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Pure Kotlin — only kotlinx-datetime, kotlinx-serialization, coroutines core.
    // NO Android dependencies. NO Hilt. NO Supabase.
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
