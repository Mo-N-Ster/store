plugins { alias(libs.plugins.kotlin.jvm); `java-library` }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { implementation(project(":domain")); api(project(":application-api")); implementation(libs.coroutines) }
