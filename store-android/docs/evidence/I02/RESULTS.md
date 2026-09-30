# I02 acceptance evidence — PASS

2026-09-30; baseline `8bbbbc8f5be767522b3e02a798417b376c83f15a`, branch `main`.
Only I02 changes under store-android; no I03 implementation. See DESIGN.md for
the A–U mapping, ownership model and source-parity decisions.

## Evidence attribution

Preserved successful logs were inspected, not merely inferred from the resume
request. Latest source/test modification: 09:17:16 local; final build completed
09:20:39; final native runs completed 09:22:39–09:22:56. No implementation or
test changed afterwards. Finalization edits only this evidence. Native logcat
was independently read back during finalization; values below match that run.

Local raw logs (ignored): out/i02-final-build.log, i02-targeted-final.log,
i02-gate.log, i02-restart-prepare.log, i02-restart-verify.log.
Lint report: infrastructure/build/reports/lint-results-debug.txt.

## Commands and results

Commands run with scripts/environment.ps1; V: is the short path to store-android.

```text
gradlew.bat -p V:\ verifyArchitecture :app:assembleDebug :infrastructure:assembleDebugAndroidTest :infrastructure:lintDebug --no-daemon --console=plain
BUILD SUCCESSFUL in 3m 1s; exit 0; 138 tasks (32 executed, 106 up-to-date)
lint: No issues found.
```

Native Android API 36 x86_64, isolated store_i01r_api36 emulator. Test APK only
installed with adb install -r; synthetic infrastructure.test data, no real profile.
Instrumentation runner: com.vibe.store.infrastructure.test/androidx.test.runner.AndroidJUnitRunner.
Base test class: com.vibe.store.infrastructure.persistence.PersistenceTest.

```text
adb -s emulator-5554 shell am instrument -w -e class <class>#<method-list> <runner>
targeted: effectiveConnectionsAndFreshV1, serializationAndScopeLifetime,
          boundedContentionNoRetry, migrationSnapshotRestartAndFutureRefusal
OK (4 tests), 7.028s

adb -s emulator-5554 shell am instrument -w -e class <class> <runner>
OK (11 tests), 8.569s

adb -s emulator-5554 shell am instrument -w -e class <class>#processRestartPersistence -e restartPhase prepare <runner>
OK (1 test), 0.876s; PREPARED PID=2190
adb -s emulator-5554 shell am force-stop com.vibe.store.infrastructure.test
adb -s emulator-5554 shell am instrument -w -e class <class>#processRestartPersistence -e restartPhase verify <runner>
OK (1 test), 0.727s; VERIFIED PID=2229
```

Native orchestration exit 0, with explicit OK-count checks (not only adb exit).
Acceptance: **13 PASS / 0 FAIL / 0 SKIP**, plus 4 targeted PASS before acceptance.
No expensive rerun during finalization: evidence remains valid for unchanged code.

## Requirement → proof

All methods below are in PersistenceTest; schemas are exported Room JSON.

| Requirement | Proof / observed result |
|---|---|
| Schema v1 / A–U | DESIGN mapping; StoreDatabase/1.json; 29 tables, 37 FK edges |
| Fresh DB / effective pragmas | effectiveConnectionsAndFreshV1: writer WAL, synchronous=2/FULL, FK=1, busy=750ms; reader WAL, synchronous=1/NORMAL, FK=1, busy=750ms |
| FK enforcement | schemaCoverageAndEveryForeignKey: all 37 invalid references rejected across 29 tables |
| Uniqueness / constraints / snapshots | uniquenessConstraintsAndHistoricalSnapshots: cash, payment, replay key, inventory pair, value/status/proof constraints; historical snapshots unchanged |
| Atomicity | rollbackAtEveryWriteBoundary: 30 injected write-boundary failures; all tables empty after rollback, integrity PASS |
| UoW / Coordinator / safe scope | serializationAndScopeLifetime: 20 serialized updates, expired/foreign-job/nested scopes rejected |
| One owner / maintenance barrier | maintenanceDrainsAndSingleOwnership: second owner denied, accepted write drained, maintenance and reads ordered, reopen persists |
| Bounded contention / no retry | boundedContentionNoRetry: competing test-only writer rejected in 860ms, below 5000ms bound, no blocked mutation |
| Migration / private quiescent snapshot | migrationSnapshotRestartAndFutureRefusal: test-only v1→v2, snapshot hash equals closed pre-migration DB, integrity/FK PASS |
| Restart / future refusal | same migration test: new owner reopens v2; v1 owner rejects future schema before factory, DB hash unchanged; separate process test PID 2190→2229 preserves value |
| Recovery fail closed | corruptRecoverySnapshotRefusesBeforeRoomOpen: corrupt synthetic snapshot rejected before factory |
| Source money/calendar parity | sourceMoneyAndCalendarParity: rounding edge cases and UTC/local day boundary |
| Architecture / compilation | verifyArchitecture, app debug and infrastructure test APK builds PASS |
| Impacted lint | infrastructure:lintDebug PASS, no issues |
| I01 non-regression | app, presentation, application-api and gradle catalog diff against baseline empty; no UI wiring or visual changes |

Reader busy=3000 was discovered during development, corrected on the effective
lease, and retested as 750. Earlier failed development logs are not acceptance
evidence. No assertion was weakened to accept that defect.

## Scope / finalization

Changed paths: application/build.gradle.kts and persistence/PersistenceContracts.kt;
domain/PersistenceValues.kt; infrastructure/build.gradle.kts; infrastructure
persistence/{Entities,StoreDatabase,RoomRepositories,DatabaseRuntime,SourceCalendar}.kt;
androidTest persistence/{PersistenceTest,SyntheticFixture}.kt; exported production
v1 and test-only v2 schemas; docs/evidence/I02/{DESIGN,RESULTS}.md.

DAOs/entities internal; typed projections exclude credential material. One owner,
one Room pool, controlled temporary maintenance connections, no public SQL/path
selection. No destructive migration/fallback, downgrade reset, open-DB copy or
personal data access. Snapshot/journal retained in private maintenance storage.

Final scope/diff checks: only store-android I02 paths; git diff --check and staged
equivalent PASS. No cache, APK, database, profile or raw log staged. Desktop/root
config/lockfiles/spikes untouched. I01 Spatial UI, STORE, creator 57€|2£!/v9,
com.vibe.store and com.vibe.store.debug preserved. No business workflow added.
One acceptance commit required; no push/tag; no I03 work.

## Limits

Native evidence is API 36 x86_64 emulator evidence, not physical power-loss or
ARM64/minimum-API qualification. Production schema remains v1; v2 is test-only.
Foundation is deliberately not wired to UI/business workflows. Full generation
switching, archive/restore/reset policy and scheduling remain later work, not
silently implemented here. No unresolved I02 blocker.
