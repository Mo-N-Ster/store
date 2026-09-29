plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "test.store.spike"
    compileSdk = 36
    defaultConfig { applicationId = "test.store.spike"; minSdk = 23; targetSdk = 36; versionCode = 1; versionName = "spike-only" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes.add("META-INF/DEPENDENCIES") }
}
dependencies {
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
