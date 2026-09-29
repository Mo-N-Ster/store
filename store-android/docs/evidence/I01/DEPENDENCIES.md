# I01 dependency decisions

Exact direct versions are in `gradle/libs.versions.toml`; no dynamic version.
This is the foundation's compatibility evidence, not a release security audit.
No dependency for bcrypt, PDF, SMTP or background business jobs is added.

| Component | Pinned version | Purpose / licence / compatibility rationale |
|---|---|---|
| JDK | 21, tested 21.0.1 | Existing installed build tool, bytecode target 17; Oracle installation licence, not redistributed |
| Gradle | 8.13 | Official Wrapper/build; Apache-2.0; AGP 8.13 line |
| AGP | 8.13.2 | Android build/API36, release minification; Apache-2.0 |
| Kotlin / Compose compiler / serialization plugin | 2.2.21 | Native JVM and matching compiler plugins; Apache-2.0 |
| KSP | 2.2.21-2.0.4 | Kotlin-matched symbol processor, Room pipeline; Apache-2.0 |
| Compose BOM | 2025.10.01 | Stable coordinated UI/foundation/Material3/test artifacts; Apache-2.0 |
| Material3 adaptive | 1.2.0 | Real window-class APIs; Apache-2.0 |
| Activity Compose | 1.11.0 | Native activity/content/edge-to-edge; Apache-2.0 |
| Lifecycle Compose / ViewModel | 2.9.4 | Lifecycle-aware StateFlow/ViewModel; Apache-2.0 |
| Navigation Compose | 2.9.5 | Serializable typed destinations; Apache-2.0 |
| Serialization core | 1.9.0 | Typed navigation serializers, not storage; Apache-2.0 |
| Coroutines | 1.10.2 | StateFlow, no background business job; Apache-2.0 |
| Room runtime/compiler | 2.8.5 | Wiring only; KSP, minimum API23; Apache-2.0 |
| SQLite bundled | 2.7.1 | Selected driver, not platform SQLite substitution; AndroidX Apache-2.0, bundled SQLite public domain |
| JUnit | 4.13.2 | JVM assertions; EPL-1.0 |
| AndroidX test runner/core | 1.7.0 | Native instrumentation infrastructure; Apache-2.0 |
| AndroidX test JUnit | 1.3.0 | Android JUnit integration; Apache-2.0 |
| Espresso | 3.7.0 | Compose test synchronization dependency; Apache-2.0 |

Versions chosen from stable maintained project lines, not a claim to be the newest.
Compatibility is gated by actual compilation, lint and native tests in RESULTS.md.
Resolved transitive versions must not be inferred solely from BOM names; capture
the resolved dependency report before acceptance. No public repository is added
except Google Maven, Maven Central and Gradle Plugin Portal for plugins.

Primary references consulted for I01:

- [KSP release tied to Kotlin 2.2.21](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4).
- [Compose Kotlin compiler plugin compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
- [Android backup and transfer rules](https://developer.android.com/identity/data/autobackup).
- [AGP 8.13 requirements](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes) (page fetch unavailable during initial lookup; actual local build is required, not assumed).

The copied SDK/Gradle tooling is ignored and stays under store-android; no spike
tool/profile is executed with a writable spike cache/home. No release key is
created. Local debug keystore is disposable/ignored and is not release identity.
