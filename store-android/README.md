# STORE Android — I01 foundation

Isolated native foundation only. No authentication, setup, permissions, business
screen, database/schema/table, migration or data import exists in I01. The two
smoke destinations describe wiring, not working STORE business features.

Current visual acceptance: [I01R evidence](docs/evidence/I01R/RESULTS.md).
Displayed author/brand: `57€|2£!/v9`; technical identity remains `com.vibe.store`.
The Spatial UI foundation uses shared Material3 tokens/panels and system-scaled
short navigation transitions; no additional UI dependency or business screen.

## Build

Run from this directory with JDK 21 (validated installation: 21.0.1), SDK platform
36 / build-tools 35.0.0, and a dedicated synthetic emulator. No root npm command.
`scripts/environment.ps1` keeps caches/debug keystore/Android home local. The local
`.tools` directory reuses copied official tools; it is not versioned or shipped.
For another workstation configure these paths to equivalent installed tools.

On Windows, an accented workspace path can prevent Gradle test workers from
loading their classes. The I01 validation uses a temporary ASCII `subst` alias
to this directory (no files are moved). Choose an unused drive letter; never
replace an existing mapping. For example, after verifying `V:` is free:

```powershell
subst V: "$PWD"
Set-Location V:\
. ./scripts/environment.ps1
# Run the commands below from V:\; pass -p V:\ to Gradle if needed.
```

Keep both the project path and GRADLE_USER_HOME under that alias. Remove only
your own alias with `subst V: /D` after all Gradle/emulator processes finish
and after leaving that drive. This is local tooling, not an application setting.

```powershell
. ./scripts/environment.ps1
./scripts/prepare_debug_key.ps1
./gradlew.bat verifyArchitecture :testing:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
python scripts/verify_foundation.py
./gradlew.bat verifyArchitecture -PboundaryProbe=true --no-daemon --console=plain
# Last command MUST reject with FORBIDDEN_MODULE_DEPENDENCY; negative test only.
python scripts/native_smoke.py
```

Native runner requires a fresh dedicated emulator with no existing
`com.vibe.store.debug` package. It installs debug and test APKs only, resizes that
emulator for three widths, runs tests and captures screenshots; resets size and
density afterward. It does not clear any existing profile. Production identity
reserved by the user: `com.vibe.store`; debug identity: `com.vibe.store.debug`.
No proof of public store name ownership is claimed by a local collision check.
On the validation host use the dedicated Pixel-profile API36 AVD with `-gpu host`
and no concurrent compilation. SwiftShader reproduced a System UI startup ANR
even before STORE installation. The visual runner fails on ANR or lost app focus;
screenshots still require visual review. Do not dismiss errors to pass this gate.

## Boundaries

```text
app -> presentation, application-api, application, infrastructure
presentation -> application-api
application -> application-api, domain
infrastructure -> application, domain
testing (tests only) -> application-api, application, domain
domain / application-api -> no project dependencies
```

`verifyArchitecture` inspects actual Gradle project dependencies across all
configurations; build/check tasks depend on it. The negative probe injects one
forbidden edge in memory only. Source checks additionally prohibit privileged
imports in presentation. No production dependency points to testing.

Room/driver wiring accepts a future typed Room builder and applies its bundled
driver. There is intentionally no concrete Room database or entity to generate
yet; KSP/Room compiler are configured for I02. Starting the smoke app creates no
database. The exported activity is only the Android launcher, not a privileged
command endpoint; no external intent data is interpreted.

Release compiles/minifies without signing; no production signing identity exists.
Do not distribute the unsigned foundation as STORE. Android backup and device
transfer are excluded for every storage domain (legacy and API31+ policies).
OEM transfer behavior and full business/security/release qualification belong to
later phases; this foundation does not grant a user session or claim production
readiness. Do not start I02 without its explicit mission.
