plugins { alias(libs.plugins.android.test) }

android {
    namespace = "com.vibe.store.releasequalification"
    compileSdk = 36
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "com.vibe.store.releasequalification.PdfiumReleaseInstrumentation"
    }
    buildTypes {
        create("release") {
            isDebuggable = false
            isMinifyEnabled = true
        }
    }
    variantFilter { if (buildType.name != "release") ignore = true }
}
