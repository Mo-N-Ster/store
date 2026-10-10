plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp) }
android {
    namespace = "com.vibe.store.infrastructure"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-fvisibility=hidden") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    testOptions { targetSdk = 36 }
    packaging { resources { merges += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":application")); implementation(project(":domain"))
    implementation(libs.room.runtime); implementation(libs.sqlite.bundled)
    implementation(libs.coroutines)
    implementation(libs.bcrypt)
    implementation(libs.angus.mail); implementation(libs.angus.activation)
    testImplementation(libs.junit)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.test.runner); androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.test.core)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
