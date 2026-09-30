plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.compose) }
android {
    namespace = "com.vibe.store"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.vibe.store"
        minSdk = 23; targetSdk = 36
        versionCode = 1; versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs.getByName("debug") { storeFile = rootProject.file(".android-home/debug.keystore") }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-I01-debug" }
        release {
            isMinifyEnabled = true
            signingConfig = null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":presentation")); implementation(project(":application-api"))
    implementation(project(":application")); implementation(project(":infrastructure"))
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test); androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit); androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.coroutines)
}
