plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    testImplementation(project(":domain"))
    testImplementation(project(":application-api"))
    testImplementation(project(":application"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines)
}
tasks.test { useJUnit() }
