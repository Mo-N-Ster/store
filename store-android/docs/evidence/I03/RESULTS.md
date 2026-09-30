# I03 — BLOCKED (G-STOP)

Date: 2026-09-30. Baseline/HEAD: 439a92ff0b5bfae0e315d2c7ea437b785846896b;
branch main; clean precheck. No acceptance commit, push, tag or I04.

## Exact blocker

Required Android artifact build fails at `:app:checkDebugAarMetadata`:
`Could not move temporary workspace ... to immutable location` for both:

```text
V:\.gradle-home\caches\8.13\transforms\83b348e91d8b2cce564170d52991b701
V:\.gradle-home\caches\8.13\transforms\83db30852feed829314290b232d63b7f
```

Reproduced after using the accepted short-path alias V:, one worker and disabling
Gradle filesystem watching. The OS-level reason for the failed moves has NOT
been established; do not assume a particular antivirus/OneDrive cause. No cache
deletion, toolchain substitution or architecture redesign attempted. Required
Android tests are unavailable until artifact generation is restored. STOP;
current implementation and tests are incomplete/unqualified, not accepted.

## Executed commands / results

Environment sourced with `V:/scripts/environment.ps1`; V: maps store-android.
Local logs under ignored out/ are preserved. No full final gate was attempted.

| Command | Exit | Result / local log |
|---|---|---|
| gradlew.bat :infrastructure:testDebugUnitTest --tests com.vibe.store.infrastructure.security.BcryptParityTest --no-daemon --console=plain (long workspace path) | 1 | Worker main class not found, no assertion executed; i03-bcrypt.log |
| V:/gradlew.bat -p V:/ :infrastructure:testDebugUnitTest --tests com.vibe.store.infrastructure.security.BcryptParityTest --no-daemon --console=plain | 0 | 1 PASS, 0 FAIL, 0 SKIP; i03-bcrypt-shortpath.log |
| V:/gradlew.bat -p V:/ :testing:test --tests com.vibe.store.testing.SecurityAuthorityTest :app:assembleDebug :infrastructure:assembleDebugAndroidTest --no-daemon --console=plain | 1 | Android cache move failure; i03-targeted.log |
| V:/gradlew.bat -p V:/ :testing:test --tests com.vibe.store.testing.SecurityAuthorityTest --no-daemon --max-workers=1 --console=plain | 0 | 7 PASS, 0 FAIL, 0 SKIP; i03-authority.log |
| V:/gradlew.bat -p V:/ :app:assembleDebug :app:assembleDebugAndroidTest :infrastructure:assembleDebugAndroidTest --no-daemon --max-workers=1 -Dorg.gradle.vfs.watch=false --console=plain | 1 | Unquoted PowerShell option split; no tests; i03-android-compile.log |
| V:/gradlew.bat -p V:/ :app:assembleDebug :app:assembleDebugAndroidTest :infrastructure:assembleDebugAndroidTest --no-daemon --max-workers=1 '-Dorg.gradle.vfs.watch=false' --console=plain | 1 | Same two cache move failures, 48s; i03-android-compile-corrected.log |
| git diff --check | 0 | Tracked diff clean; no staging/acceptance |

JUnit XML inspected: infrastructure/build/test-results/testDebugUnitTest/
TEST-com.vibe.store.infrastructure.security.BcryptParityTest.xml (1/0/0),
testing/build/test-results/test/TEST-com.vibe.store.testing.SecurityAuthorityTest.xml
(7/0/0). These test inputs/implementations were unchanged after their successful
runs. Subsequent UI/resource/test-source edits are NOT covered by those results.
verifyArchitecture passed in the successful JVM runs: seven modules, allowed
dependency graph. Gradle configuration tasks marked SKIPPED are not test results.

Total executed assertions suites: **8 tests PASS / 0 test FAIL / 0 test SKIP**.
Android build gate: **FAIL**. Remaining required native/UI/lint/regression/final
gates: **NOT EXECUTED**, not PASS and not a waived/skipped requirement.

## Requirement → current proof

| Mission requirements | Test / current status |
|---|---|
| 1 bootstrap unique/no default | SecurityAuthorityTest.bootstrapUniqueNoDefaultAndDistinctSetup PASS (JVM) |
| 2–5 login/inactive/ambiguity/normalization | identityNfcCaseSpacesAccentsAmbiguityAndInactive PASS (JVM) |
| 6 bcrypt cost/UTF-8/72 bytes | BcryptParityTest.sourceUtf8And72ByteVectors PASS against synthetic bcryptjs outputs |
| 7 lockout/recovery | boundedLockoutRecoveryAndRetry PASS (JVM); durable native test pending |
| 8–9 direct calls/Manager→Owner | directCallsOwnerManagerAndZeroMutation PASS (JVM) |
| 10–12 subtractive/conflict/cash/no mutation | subtractiveDenialsConflictCashAndRightsReread PASS (JVM); native transaction proof pending |
| 13–16 switch/guards/memory/generation | switchingGuardsAndSessionGeneration PASS (JVM); real lifecycle proof pending |
| 17–19 rotation/background/process death/no cash or attendance effects | IdentityUiTest and IdentityNativeTest authored, NOT EXECUTED; no PID evidence claimed |
| 20 Keystore loss/tamper/reconfiguration | IdentityNativeTest authored, NOT EXECUTED |
| 21 public DTO/logs | settingsAllowlistAndPublicResponses PASS for tested projections/input toString/audit fixture; complete scan/native evidence pending |
| 22 password continuous input/eye | IdentityUiTest authored, NOT EXECUTED |
| 23 navigation grants no authority | direct-call tests PASS; UI/lifecycle proof pending |
| 24 I02 impacted regression | NOT EXECUTED; schema JSON and DatabaseRuntime unchanged against baseline |
| 25 I01 impacted regression | NOT EXECUTED; existing smoke assertions retained |
| 26 build/lint/architecture | architecture PASS, Android artifact build FAIL, lint NOT EXECUTED |
| 27 diff | tracked git diff --check PASS; final acceptance review not performed |

JVM role/direct-call matrix: anonymous settings/password changes refused;
Manager cannot define Owner/Manager password or edit denials; Manager may define
Employee password with current EMPLOYEES:UPDATE; Owner cannot restrict Owner or
administratively redefine Owner password; subtractive changes to staff require
current role/denial snapshot and no target open cash. Revocation after login
is observed before mutation. Business mutations remain unchanged on those
tested refusals; permitted failed-auth counters/audit are separate.

## Worktree preserved / non-regression status

Changes only under store-android:
- domain IdentityPolicy; public input-only credentials and allowlisted DTO/service;
- application security ports/authority; scoped persistence security port;
- Room DAO/repository extensions (no schema delta), bcrypt and Keystore adapters;
- application-lifetime composition; I03 login/bootstrap/recovery/switch/settings UI;
- shared theme override, existing informational foundation entry/copy;
- JVM/native/Compose test sources and I03 evidence;
- module build files/catalog for pinned bcrypt and existing test/coroutine libs.

Application uses java-library to export its existing application-api dependency
because its public service implementation exposes those types; no new module
edge or dependency substitution. Full Android linkage remains unverified.
No Room entity/schema, migration/runtime owner or destructive fallback change.
I01 tokens/branding and technical IDs remain; I01/I02 regression PASS is NOT
claimed until impacted tests run. Desktop, root config/lockfiles and spikes
untouched. No real profile/DB/backup/secret used, no emulator mutation in I03 so
far. No I04 business use case introduced. Worktree intentionally dirty.

## Resume boundary

Resolve the Gradle cache move blocker within authorized tooling scope first.
Then continue this worktree, not baseline reconstruction: complete/review I03
implementation, compile Android tests, run targeted native/UI tests (including
real process deaths), finish secret/projection/packaged-notice review and I03
visual evidence, then the required final gate once. No acceptance until all
remaining requirements pass. NOT READY FOR I04.
