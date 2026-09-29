plugins {
    alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose); alias(libs.plugins.serialization)
}
android {
    namespace = "com.vibe.store.presentation"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 23 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":application-api"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.material3)
    implementation(libs.adaptive); implementation(libs.navigation.compose); implementation(libs.serialization)
    implementation(libs.lifecycle.compose); implementation(libs.lifecycle.viewmodel); implementation(libs.coroutines)
}
