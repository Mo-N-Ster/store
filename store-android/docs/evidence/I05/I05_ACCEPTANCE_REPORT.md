# I05 acceptance gate — 2026-10-03

Status: PASS, ready for explicit acceptance; NOT yet formally accepted.
Baseline: `98053aa33ae40912797c646183c7a4440d95fd6e`; branch: `main`.
Contract: root `docs/STORE_3_IMPLEMENTATION_CONTRACT.md`, I05, G-PASS,
G-EVIDENCE, G-AUTH/G-OWNER/G-PRESENCE/G-MEDIA and non-regression rules.

## Closure changes and scope

Only `TeamPresentationUiTest.kt` and this evidence directory were changed during
closure. No production code, dependency, permission, schema or business rule
was changed. All prior modified/untracked files and external backups were
preserved. Generated untracked `bin/` files are excluded from staging, not deleted.
Existing I05 and authorized UX work remains part of the proposed acceptance.

The new synthetic instrumentation test observes the actual Android window,
checks card column geometry, opens the real profile action, reaches identity and
reference fields, and returns without signing or correcting attendance.
It resizes 599 -> 600 -> 839 -> 840 -> 599 dp, so classification and recomputation
are exercised rather than mocked. The initial 320x640 / 160 dpi configuration
was restored. Captures were read back and visually inspected: clean rendered UI,
one/two/three-column cards, no error or ANR dialog. Ordinary Android system bars
remain visible. Final captures: COMPACT 599x1100, MEDIUM 839x1100,
EXPANDED 840x1100 pixels at density 1.0. These are synthetic component-level
UI evidence, not a claim of production-user or physical-device qualification.

## Qualification and provenance

`Q` below is `C:\Users\sterl\AppData\Local\Temp\store-i05-qual-20261001-031127`.
No full suite was rerun. Existing source/build-script hashes matched Q before
qualification except the newly edited UI test; only that file was synchronized.

| Check | Result | Evidence/provenance |
|---|---|---|
| CatalogFormUiTest | 10/10 PASS | User-confirmed qualification and original fast-closure report; not rerun, raw output not independently located during closure |
| I04 / UX4-C1 UI regression | 17/17, 0 failures/errors/skips | Existing `Q/app/build/outputs/androidTest-results/connected/debug/TEST-STORE_I03_API36(AVD) - 16-_app-.xml` inspected |
| TeamNativeTest | 6/6, 0 failures/errors/skips | Corresponding `Q/infrastructure/build/outputs/androidTest-results/connected/debug/TEST-STORE_I03_API36(AVD) - 16-_infrastructure-.xml` inspected; `%TEMP%/store-i05-ux2-native.log` also records successful 6-test run |
| TeamAuthorityTest | 8/8, 0 failures/errors/skips | Existing `Q/testing/build/test-results/test/TEST-com.vibe.store.testing.TeamAuthorityTest.xml` inspected |
| SecurityAuthorityTest | 7/7, 0 failures/skips | Existing JVM XML inspected |
| CatalogPolicyTest / FoundationTest | 4/4 and 2/2, 0 failures/skips | Existing JVM XML inspected |
| Architecture / compilation / app build | PASS, reused | User-confirmed baseline and original fast-closure evidence; production unchanged |
| App / presentation lint | 0 Error/Fatal | Existing lint XML reports inspected; not rerun |
| New adaptive test | 1/1 PASS, 0 failure/ignored | Direct instrumentation run below; COMPACT, MEDIUM, EXPANDED all asserted |
| Test APK compilation/package | PASS | Targeted assembleDebugAndroidTest, final build successful in 35 seconds |
| Scope / diff whitespace | PASS | Only `store-android/**`; final `git diff --check` exit 0 |

The 17 existing UI cases comprise CatalogUiTest 3, CatalogPresentationUiTest 2,
StoreNavigationUiTest 6, TeamPresentationUiTest 3 and TeamUiTest 3. The new adaptive
case is additional; this is not a claim that all 18 were rerun together.

## Requirements to existing executed tests

- G-AUTH/G-OWNER: reused SecurityAuthorityTest and
  `managerRestrictionsAndOpenCashBlockSensitiveChanges`; managed accounts use
  I03 authorization, target restrictions and the same UoW, not a second authority.
- G-PRESENCE: `personalProofPersistsWithoutSwitchingFacilitatorOrImplicitPunch`,
  `signRequiresPersonalPasswordAndPersistsLockoutWithoutSwitchingSession`,
  `ownerWithoutHrProfileIsOnDailySheetAndCanSign`,
  `rejectsDoubleEntryExitWithoutEntryAndUnprofiledNonOwner`.
- Corrections/history: `correctionPreservesOriginalsAndRefusedIntervalsDoNotMutateRoom`,
  `correctionsPreserveOriginalsAndEmployeeHistoryIsServerScoped`; real Room
  reopening preserves originals, corrector and completed duration.
- Atomicity: `injectedWriterFailureRollsBackAttendanceAndAuditAtomically` proves
  synthetic exception rollback and reopen. It is not a new process-kill test;
  accepted process-death behavior remains reused, not requalified here.
- Profiles/photo/reference: `accountProfilePasswordAndPhotoSurviveRealRoomReopen`,
  `automaticReferenceIsUniqueAndDurableInRealRoom` plus eight team JVM cases.
- UI: existing TeamUiTest covers continuous entry, cancelled photo selection,
  one-shot temporary result, personal signing and absence of employee admin
  controls; presentation tests cover identity/role separation and original audit
  details. New adaptive case closes the previously unrecorded width/capture gate.
- DTO review: explicit EmployeeSummary/EmployeeDetail/AttendanceView projections;
  internal verifier remains in trusted security code. Temporary result is
  specialized and redacted by toString; no sensitive payload copied into evidence.

## Exact additional qualification

PowerShell environment (existing tools, no upgrades):

```powershell
$q = "$env:TEMP/store-i05-qual-20261001-031127"
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$env:ANDROID_HOME = 'C:/Users/sterl/AppData/Local/store-i03-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "$env:TEMP/store-i03-qual/.gradle-home"
& "$q/gradlew.bat" -p $q :app:assembleDebugAndroidTest --offline --console=plain
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
& $adb -s emulator-5554 install -r "$q/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
& $adb -s emulator-5554 shell am instrument -w -r -e class 'com.vibe.store.TeamPresentationUiTest#adaptiveTeamWindowAndEvidence' com.vibe.store.debug.test/androidx.test.runner.AndroidJUnitRunner
foreach ($mode in 'COMPACT','MEDIUM','EXPANDED') {
  & $adb -s emulator-5554 pull "/sdcard/Android/data/com.vibe.store.debug/files/i05-$mode.png" "store-android/docs/evidence/I05/i05-$mode.png"
}
& $adb -s emulator-5554 shell wm size
git diff --check
git status --short
```

Build/install/pull exit 0. Final actual instrumentation output:

```text
INSTRUMENTATION_STATUS: test=adaptiveTeamWindowAndEvidence
INSTRUMENTATION_STATUS_CODE: 0
Time: 38.762
OK (1 test)
INSTRUMENTATION_CODE: -1
Physical size: 320x640
```

Android's final instrumentation code -1 is the normal completed-run code here;
the test result is the status 0 and explicit OK, not shell exit alone.
Final raw local outputs: `%TEMP%/store-i05-closure-adaptive-build-r3.log` and
`%TEMP%/store-i05-closure-adaptive-test-r3.log`.

Two initial test-development runs failed: clicking a non-clickable card instead
of its profile button, then requesting scroll on a fixed footer button. Both
test orchestration errors were corrected without changing production code,
weakening assertions, sleeps or increased waits. Their logs remain in TEMP as
`store-i05-closure-adaptive-test.log` and `store-i05-closure-adaptive-test-r2.log`.

## Artifact and acceptance boundary

Existing qualified app APK SHA-256 was independently checked:
`6AB4D43DB93992622AE8786032B7D043B8B4E912B4147C4BBA60DE03C78054B6`.
It is not a new release APK. Test-only changes did not require rebuilding it.

Exact proposed acceptance paths: `SELECTIVE_STAGING.txt` (no blanket add).
No paths have been staged, committed, pushed or tagged by this closure.
Expected acceptance message, only after authorization:
`feat(android): I05 implement team and attendance`.

Remaining I05 technical blockers: none identified in this focused closure.
Formal acceptance: awaiting user. Nonblocking cosmetic polish remains I13.
I06 plan: `I06_IMPLEMENTATION_PLAN.md`; no I06 implementation started.
