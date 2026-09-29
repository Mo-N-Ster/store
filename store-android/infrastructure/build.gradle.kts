plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp) }
android {
    namespace = "com.vibe.store.infrastructure"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 23 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":application")); implementation(project(":domain"))
    implementation(libs.room.runtime); implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)
}
