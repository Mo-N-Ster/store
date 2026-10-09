# STORE I08 — Acceptance Evidence

Date: 2026-10-09

## Repository baseline

- Project: STORE 3.0
- Branch: `feature/store-i08`
- Baseline HEAD: `589ea20ec2c668e2a25bfc666779d1a70f06cb84`
- Target: Android API 36 emulator
- Execution: Gradle offline

## Implementation scope

- Administration and supporting services
- Local messages and acknowledgements
- Alerts and notifications
- Audit and diagnostic facilities
- Help/support UI
- Existing authorization boundaries preserved

## Validation results

| Validation | Result |
|---|---|
| Seven-module architecture | PASS |
| Kotlin compilation | PASS |
| Android APK assembly | PASS |
| JVM test suite | PASS |
| I08 native Room tests | 3/3 PASS |
| I08 Compose tests | 2/2 PASS |
| Ordinary Room regression | 72 tests without reported failures |
| Real process-death recovery scenarios | 11/11 PASS |
| Ordinary UI regression | 41 tests without reported failures |
| Adaptive UI requalification | 4/4 PASS |
| Identity UI process-death preparation | PASS |
| Identity UI process-death verification | PASS |
| Git diff integrity | PASS |

## Qualification notes

The original unrestricted instrumentation runs contained
tests requiring explicit execution parameters.

Four Room tests were requalified through eleven distinct
prepare/force-stop/verify scenarios.

Four adaptive UI tests were requalified separately using
their required instrumentation parameters and display
configuration.

The identity UI restart test was executed in separate
prepare and verify phases with a real external force-stop.

All reported requalification scenarios passed.

## Evidence locations

Host-side logs:

- `~/STORE-I08-recovery.2klZiR/`
- `~/STORE-I08-recovery-batch.38UJ6k/`
- `~/STORE-I08-ui-regression.rKVIzp/`
- `~/STORE-I08-ui-requalification.y3VVBh/`
- `~/STORE-I08-identity-ui.wKwTbK/`

Additional Gradle evidence:

- `store-android/app/build/reports/androidTests/`
- `store-android/infrastructure/build/reports/androidTests/`

The host-side evidence paths are local references; those logs
are not embedded in this Markdown report.

## Acceptance status

Executed validation campaigns: PASS.

Source review, documentation consistency and Git staging
review: PENDING.

Final Git commit and integration into main: NOT PERFORMED.

No production data was used for the recovery scenarios.
