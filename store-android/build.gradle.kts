import org.gradle.api.artifacts.ProjectDependency
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
val allowed = mapOf(
    ":app" to setOf(":presentation", ":application-api", ":application", ":infrastructure"),
    ":presentation" to setOf(":application-api"),
    ":application-api" to emptySet(),
    ":application" to setOf(":application-api", ":domain"),
    ":domain" to emptySet(),
    ":infrastructure" to setOf(":application", ":domain"),
    ":testing" to setOf(":application-api", ":application", ":domain")
)
val verifyArchitecture = tasks.register("verifyArchitecture") {
    group = "verification"
    doLast {
        check(JavaVersion.current().majorVersion == libs.versions.jdk.get()) { "JDK 21 required" }
        val errors = mutableListOf<String>()
        subprojects.forEach { p ->
            p.configurations.forEach { c ->
                c.dependencies.withType(ProjectDependency::class.java).forEach { d ->
                    // AGP test configurations refer to the tested module itself.
                    // This is not an inter-module edge; all cross-module edges remain allowlisted.
                    if (d.dependencyProject.path != p.path && d.dependencyProject.path !in allowed.getValue(p.path))
                        errors += "${p.path} -> ${d.dependencyProject.path} (${c.name})"
                }
            }
        }
        check(errors.isEmpty()) { "FORBIDDEN_MODULE_DEPENDENCY: ${errors.joinToString()}" }
        println("ARCHITECTURE_PASS: seven modules, explicit dependency allowlist")
    }
}
gradle.projectsEvaluated {
    // Negative verification only: inject a real forbidden Gradle edge in memory.
    if (providers.gradleProperty("boundaryProbe").orNull == "true") {
        project(":presentation").dependencies.add("implementation", project(":infrastructure"))
    }
    subprojects.forEach { p ->
        p.tasks.matching { it.name == "preBuild" || it.name == "check" || it.name == "compileKotlin" }
            .configureEach { dependsOn(verifyArchitecture) }
    }
}
tasks.wrapper {
    gradleVersion = libs.versions.gradle.get()
    distributionType = Wrapper.DistributionType.BIN
}
