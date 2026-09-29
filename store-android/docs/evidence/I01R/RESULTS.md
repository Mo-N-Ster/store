# I01R — PASS

Continues the uncommitted I01 foundation on `main` at
`d69074792e8ef05f9b9d7bd414d5115149f3e981`. Earlier I01 evidence is retained
unchanged; this report supersedes its visual BLOCKED decision only.

## ANR diagnosis / environment remediation

The original failures were in `com.android.systemui` and `com.android.phone`.
A new isolated API36 AVD (`store_i01r_api36`, Pixel 5 profile, heap256M instead
of the generic profile's heap32M, 4 cores / 3072 MB) initially booted cleanly.
A subsequent SwiftShader cold boot reproduced the failure **before STORE was
installed**, while `pm list packages com.vibe.store` returned no packages:

```text
09-29 22:06:17.205 am_anr: com.android.systemui ... failed to complete startup
```

Thus STORE is not causal. The failure is emulator/System UI startup instability;
the exact internal Android timeout cause is not claimed to have been proved.
Using the same dedicated AVD with **GPU host**, and no concurrent Gradle build,
produced a clean cold boot (60 seconds) and the entire clean visual run below.
No ANR was dismissed, log buffer cleared or overlay hidden. The runner checks
the whole boot's `am_anr` event buffer before installation and around every
capture, and requires STORE MainActivity to own the current focus.
No STORE code was changed to remediate ANR. The emulator is stopped after testing.

Reproducible launch after sourcing `scripts/environment.ps1` via ASCII `V:`:

```powershell
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd store_i01r_api36 -no-window -no-audio -no-snapshot -gpu host -cores 4 -memory 3072
```

Use a dedicated test AVD; the runner refuses to overwrite an existing package.
Do not start emulator and Gradle simultaneously on this validation host.

## U1 / U2

Displayed brand/author is **57€|2£!/v9** (label `STORE · 57€|2£!/v9`).
Package/application IDs remain `com.vibe.store` / `com.vibe.store.debug`.
No namespaces, permissions, persistence or historical documents were renamed.

Reusable `SpatialTheme`, `SpatialPanel` and `SpatialTokens` provide a restrained
Material3 foundation: canvas/panel/badge hierarchy, 28dp panels, 20/32dp responsive
insets, 24dp panel padding, 20dp gaps, 1dp tonal/2dp shadow elevation and semantic
light/dark colours. Material typography and buttons preserve standard focus,
pressed/ripple/disabled states; targets are at least48dp. Navigation fades are
160ms, using Compose's system duration scale (tested at zero). No perpetual
animation, glass effects, separate phone/tablet implementation or new dependency.
Compact/medium stack the same panels; expanded arranges them side by side.
FR/EN resource parity and existing locale assertions remain PASS.

## Affected validation only

| Check | Result |
|---|---|
| `gradlew.bat -p V:\ :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain` | exit0; 167 tasks (120 up-to-date); lint0 errors,16 existing warnings |
| `python scripts/verify_foundation.py FoundationContract.test_presentation_has_no_privileged_imports FoundationContract.test_locale_key_parity FoundationContract.test_manifest_has_only_nonprivileged_launcher` | exit0; 3 PASS |
| `python scripts/native_smoke.py` on host-GPU AVD | exit0; 9 PASS,0 FAIL,0 SKIP |
| [COMPACT](compact.png), 400dp wide, light | reviewed PASS; brand/panels/content/button visible, no error overlay |
| [MEDIUM](medium.png), 640dp wide, dark | reviewed PASS; readable contrast, no truncation/error overlay |
| [EXPANDED](expanded.png), 900dp wide, light, animator scale0 | reviewed PASS; two-column layout, complete content, no error overlay |

The JSON's `visual_review: required` is the runner's reminder, fulfilled by the
explicit visual reviews above. Native screenshots are unedited. Normal Android
status/navigation/task bars are present; no error/dialog overlays occur.
The initial SwiftShader gate correctly rejected ANR before installing STORE;
it is not hidden or counted as a successful validation run.

Unaffected I01 JVM, Room wiring, release-configuration and negative-boundary
results are reused, not rerun. Release APK from I01 is not a new branded build
or distribution artifact. No release packaging or I02 work performed.

Only `store-android/**` is included in acceptance. Desktop/root/spikes and real
data remain untouched. One acceptance commit, no push/tag; no business features.
Staged review: 48 files, all under the authorized directory; `git diff --cached
--check` and `git diff --check` exit0. No APK, DB, keystore, tool/cache or log is
staged; the local debug key remains ignored. Historical evidence is retained.
