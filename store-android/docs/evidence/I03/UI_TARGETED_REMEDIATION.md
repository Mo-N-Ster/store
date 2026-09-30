# I03 targeted UI remediation — 2026-09-30

Baseline: 439a92ff0b5bfae0e315d2c7ea437b785846896b. No commit/push.
Only IdentityUiTest.kt changed, mirrored into the existing isolated qualification
copy at %TEMP%/store-i03-qual. No production code, schema, dependency, .tools,
Desktop, spikes or real user data changed.

## Diagnosis

- continuousPasswordEntryAndVisibility: test defect. KeyboardType.Password
  retains the sensitive Password semantics when the eye reveals text. Replaced
  the incorrect absence assertion with actual TextLayoutResult checks: eight
  mask characters -> exact synthetic input -> eight mask characters. The
  Password sensitivity assertion is retained in every state; continuous input
  and focus assertions remain.
- failedLoginRetryAndRecoveryCleanForm: test clicked access-store before scrolling
  it into view at 320x640 / 160 dpi. Added performScrollTo + assertIsDisplayed.
- rotationBackgroundAndProtectedNavigation: same initial off-screen click.
  Same correction; recreation/background/authentication assertions unchanged.
- applicationSessionNotRestoredAfterRealDeath: orchestration, not UI defect.
  Requires appRestartPhase=prepare/verify and external process death. Unchanged,
  not included in the three ordinary methods, and not claimed PASS here.

## Targeted validation

Qualified SecurityApp/MainActivity/StoreApplication copies match workspace hashes.
Changed test source SHA-256 (workspace and qualification copy identical):
F2B72BA7523C5C654478CC08DAEE1F7D1B9F7FE99FDEED7CF8F493D2731CE63C.

With JAVA_HOME=C:/Program Files/Java/jdk-21,
GRADLE_USER_HOME=%TEMP%/store-i03-qual/.gradle-home and
ANDROID_HOME/ANDROID_SDK_ROOT=%LOCALAPPDATA%/store-i03-sdk:

`gradlew.bat -p %TEMP%/store-i03-qual :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 --console=plain`

Exit 0, BUILD SUCCESSFUL in 4m 3s (7 executed, 91 up-to-date).
No full suite. Local log: out/i03-ui-targeted-compile.log.

Installed test APK with adb install -r. First instrumentation attempt executed
zero tests because target com.vibe.store.debug was absent. Installed the existing
qualified app-debug.apk with install -r; no rebuild/reset/clear/uninstall.

Runner com.vibe.store.debug.test/androidx.test.runner.AndroidJUnitRunner;
device emulator-5554; `am instrument -w -r -e class` with the three fully-qualified
IdentityUiTest#method selectors above, comma-separated.
Result: **OK (3 tests), 23.041s; 3 PASS / 0 FAIL / 0 SKIP**, orchestration exit 0.
Log: out/i03-ui-targeted-tests-installed.log. Original 10s waits unchanged.

## Separate process-death orchestration (not executed in this remediation)

```powershell
$adb = "$env:LOCALAPPDATA\store-i03-sdk\platform-tools\adb.exe"
$runner = 'com.vibe.store.debug.test/androidx.test.runner.AndroidJUnitRunner'
$test = 'com.vibe.store.IdentityUiTest#applicationSessionNotRestoredAfterRealDeath'
& $adb -s emulator-5554 shell am instrument -w -r -e class $test -e appRestartPhase prepare $runner
# Require OK (1 test) before continuing; PID also persisted by the test.
& $adb -s emulator-5554 logcat -d -s System.out:I | Select-String 'I03 APP PREPARED PID='
& $adb -s emulator-5554 shell am force-stop com.vibe.store.debug
& $adb -s emulator-5554 shell pidof com.vibe.store.debug
# pidof must return no PID.
& $adb -s emulator-5554 shell am instrument -w -r -e class $test -e appRestartPhase verify $runner
& $adb -s emulator-5554 logcat -d -s System.out:I | Select-String 'I03 APP (PREPARED|VERIFIED) PID='
```

Require OK (1 test) in each phase and different PIDs. Kill target .debug, not
infrastructure.test or release com.vibe.store. Never pm clear, uninstall, wipe
the emulator or remove private DB/preferences between phases. No global I03
acceptance or new lifecycle PASS inferred from this targeted remediation.
