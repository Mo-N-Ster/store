# I01 evidence — BLOCKED

Baseline: `d69074792e8ef05f9b9d7bd414d5115149f3e981`, branch `main`.
Only `store-android/**` is added. No acceptance commit, push or tag.
No I02 work; no Desktop/root configuration, migrations, dependencies, spikes,
real profiles, backups or business data changed.

## Blocking gate

The native instrumentation runner reports **9 PASS / 0 FAIL / 0 SKIP** (three
tests at each width), but visual review rejects **all three screenshots**:
Android displays **System UI isn't responding** above the smoke app. Therefore
the native visual gate is **BLOCKED**, not PASS. Automated assertions alone are
insufficient; `native-results.json` records instrumentation results only.

Preserved captures: [COMPACT](compact.png), [MEDIUM](medium.png),
[EXPANDED](expanded.png). Do not treat these as accepted UI evidence.
Read-only event log capture confirmed:

```text
com.android.systemui: executing service .SystemUIService, waited 20970ms
com.android.phone: REFRESH_SAFETY_SOURCES broadcast ANR
```

These events identify Android system processes, not a proven STORE exception.
The underlying emulator failure has not been diagnosed. Per the mission's
required-test-unreliable STOP rule, no remediation or acceptance follows this
discovery. The dedicated emulator was stopped; evidence and synthetic AVD retained.
Next authorization must address native validation reliability and rerun the
visual gate before I01 acceptance; I02 is not authorized.

## Foundation and versions

Seven modules: app, presentation, application-api, application, domain,
infrastructure, testing. Graph and commands: [README](../../../README.md).
Identity `com.vibe.store`, debug `com.vibe.store.debug`; min/compile/target 23/36/36.
Local repository/emulator collision checks found no pre-existing package.
This does not establish public Play Store ownership.

JDK 21.0.1 (JVM target 17), Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21,
KSP 2.2.21-2.0.4, Compose BOM 2025.10.01, adaptive 1.2.0,
Room 2.8.5, bundled SQLite 2.7.1. Resolved Compose UI/foundation **1.9.4**,
Material3 **1.4.0**, Kotlin stdlib **2.2.21**.
Full direct pins, purposes and licences: [DEPENDENCIES](DEPENDENCIES.md)
and `gradle/libs.versions.toml`.

## Commands and observed results

Commands executed from the isolated project, using temporary `V:` ASCII alias,
JDK 21 and project-local caches/SDK/home. Large logs remain ignored under `out/`.

| Requirement / command | Exit / result |
|---|---|
| Official Gradle `wrapper --gradle-version 8.13 --distribution-type bin` | 0 |
| `gradlew.bat -p V:\ :testing:test --no-daemon --console=plain` | 0; 2 PASS, 0 FAIL/SKIP |
| `gradlew.bat -p V:\ verifyArchitecture :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain` | 0; debug, unsigned release, instrumentation APK and lint built |
| `gradlew.bat -p V:\ verifyArchitecture -PboundaryProbe=true --no-daemon --console=plain` | 1 EXPECTED; explicit `FORBIDDEN_MODULE_DEPENDENCY: :presentation -> :infrastructure (implementation)` |
| `gradlew.bat -p V:\ :app:dependencies --configuration debugRuntimeClasspath --no-daemon --console=plain` | 0; resolved versions above |
| `python scripts/verify_foundation.py` | 0; 5 PASS, 0 FAIL/SKIP: source boundaries, no schema, backup/transfer rules, source manifest, FR/EN key parity |
| `python scripts/verify_merged_manifest.py` | 0; debug and release platform checks PASS |
| `python scripts/native_smoke.py` | 0; 9 instrumentation PASS, but 3 visual captures REJECTED (system ANR) |
| `apksigner verify app/build/outputs/apk/debug/app-debug.apk` | 0; debug signature only |
| `apksigner verify app/build/outputs/apk/release/app-release-unsigned.apk` | 1 EXPECTED; unsigned, missing signing manifest |
| `git diff --check` | 0 for tracked tree; new files remain untracked, acceptance diff review not completed |

JVM cases verify injected-port composition and propagation of infrastructure
errors. Instrumentation checks width class, typed forward/back navigation,
FR/EN resource lookup, debug package, backup flag, launcher and zero databases.
Room KSP/driver wiring compiles without entities, schema, DAO, DB opening or
business tables. No production key exists; generated debug key is ignored.

Merged manifests export the launcher and AndroidX ProfileInstallReceiver only;
the latter requires system/shell `android.permission.DUMP`. Startup provider and
Room invalidation service are non-exported. The sole merged uses-permission is
the package-specific signature-level dynamic-receiver permission. Backup and
transfer exclusions cover all nine storage domains; cleartext is disabled.

## Native environment / known limitations

Dedicated AVD `store_i01_api36`, emulator-5554, Android 16/API36 x86_64,
emulator 37.1.11, WHPX, SwiftShader, 2 cores / 2048 MB. Requested sizes at
density320: COMPACT 800x1800, MEDIUM 1400x2000, EXPANDED 2000x1600;
actual screenshots reflect Android display limits. No physical device tested.

Lint: **0 errors, 16 warnings** (15 newer-version notices and one missing final
application icon). Versions are deliberately pinned, not claimed latest. Final
branding is not implemented in this foundation. Gradle deprecation warnings and
unstripped native-library notices are preserved in local logs, not hidden.

During validation, fixed a boundary-check false positive for AGP same-module
test references. Cross-module restrictions remain strict and negative-tested.
Accented Windows paths initially prevented worker/test class loading; using the
ASCII alias resolved it (documented). Missing local debug key initially blocked
debug signing; explicit ignored debug-key preparation resolved it. Neither
failure involved STORE business behavior or personal data.

**Decision: BLOCKED. Worktree intentionally dirty; no acceptance commit.**
