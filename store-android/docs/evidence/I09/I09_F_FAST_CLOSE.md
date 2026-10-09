# I09-F FAST-CLOSE — Currency regression and evidence disposition

Date: 2026-10-09. Repository: `feature/store-i09`, HEAD `3691eccedc9855686b011e34c7420f17aaf6c085`. This addendum preserves the supplied A1–E acceptance baseline and does not claim global I09 acceptance. Index was empty at preflight. The only edits in this mission are `infrastructure/src/androidTest/kotlin/com/vibe/store/infrastructure/persistence/ReportingNativeTest.kt`, this evidence file, and the refreshed final Room test XML.

## Currency safety regression

Source review of `RoomReportingRepository.salesMetrics()` found the intended fail-closed checks already in production: a null invoice currency or any currency differing from the first validated sale throws `WorkflowFailure(UNAVAILABLE)` while the report is being assembled. No production defect was demonstrated, so production code was not changed.

Added `financeRejectsMissingOrMixedSalesCurrencyWithoutReturningPartialSnapshot` to the existing synthetic `ReportingNativeTest`. Each case uses an isolated UUID database and the existing Room owner/read lease. Before adding the incompatible invoice, the test confirms that the fixture's single-currency XAF FINANCES snapshot succeeds with revenue `25.0`. It then adds either a validated sale with null currency or a validated EUR sale alongside the XAF sale. Both FINANCES reads must fail with `UNAVAILABLE`; the test verifies that no `DocumentModel` was returned. This exercises both failure branches without aggregating incompatible amounts or exposing partial rows/series/metrics. Existing `ReportingAuthorityTest` remains the separate authorization-denial coverage; the new test specifically covers atomic failure of an authorized repository projection.

Final retained report: `test-results/android/ReportingNativeTest.xml`, class `com.vibe.store.infrastructure.persistence.ReportingNativeTest`, **7 tests, 0 failures, 0 errors, 0 skipped**, including the new method. XML timestamp: `2026-10-09T12:56:57`; file mtime: `2026-10-09 14:57:12 +0200`, 1,628 bytes. Test source mtime: `2026-10-09 14:55:25 +0200`, before execution. Device: `emulator-5554`, AVD `STORE_API36_Main`, API 36. Exact commands and results:

```text
bash ./gradlew --offline :verifyArchitecture :infrastructure:assembleDebugAndroidTest
  BUILD SUCCESSFUL; :verifyArchitecture PASS; Android test APK assembled.

bash ./gradlew --offline :infrastructure:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.infrastructure.persistence.ReportingNativeTest
  BUILD SUCCESSFUL; Starting 7 tests on STORE_API36_Main(AVD) - 16;
  Finished 7 tests; 0 failures/errors/skips.
```

No full JVM or 73-test app suite was rerun. No user database or emulator profile was cleared.

## Restart evidence matrix

| Domain | Preserved evidence and observed result | Evidence limitation |
|---|---|---|
| Catalog — 2/2 | `I04/rf001-before.txt` and `rf001-after.txt` retain verify invocations and `OK (1 test)` results; recorded prepare PIDs are 4760 and 4861, and passing assertions require a different verifier PID. `I09_E_R1_EVIDENCE.md` reports the later before/after synthetic runs 1/1 each, with READY PIDs 18016/18107. | For the later R1 run IDs, raw prepare/force-stop/verify terminal transcripts and numeric verifier PIDs were not archived. Earlier I04 raw verify evidence is preserved and must not be misrepresented as the R1 transcript. |
| I07 — 6/6 | `I07/runtime/p6a-r3/` preserves actual prepare/verify output for five of six operation/boundary pairs; each retained verify log reports `OK (1 test)`. `I07_EVIDENCE_INDEX.txt` identifies the archived artifacts. `I09_E_R1_EVIDENCE.md` records all six later R1 verification outcomes and READY PIDs: purchase 18191/18270, inventory 18351/18429, compensation 18511/18591. | The older p6a-r3 bundle lacks purchase-before prepare/verify logs. The later R1 run has no archived per-invocation terminal logs or numeric verifier PIDs. The reported 6/6 result is retained in the contemporaneous R1 evidence, not independently reconstructed here. |
| Sale — 2/2 | `I06/logs/store-i06-r14-*` and `I06/I06_EVIDENCE_INDEX.txt` preserve before/after prepare outputs (expected process interruption) and verify outputs (`OK (1 test)`). `I09_E_R1_EVIDENCE.md` separately reports later R1 before/after verifies 1/1 each, READY PIDs 18670/18748. | Later R1 terminal/force-stop transcripts and verifier PIDs are not archived; I06 run evidence is historical, not the R1 run. |
| Identity — prepare/verify | `I09_E_app_full_configured.xml` (73/73) includes the configured app identity prepare; `I09_E_Identity_restart_verify.xml` is a retained 1/1 verify report. R1 records native identity prepare and verify 1/1 each and the changed-PID/session-loss checks. | The direct native R1 prepare/verify output and exact PID pair are not retained as separate artifacts. The app XML does not itself contain the PID transcript. |

`I09_E_R1_EVIDENCE.md` is the preserved contemporaneous account of the R1 phase sequences, run IDs, READY marker PIDs, external process stops and verification counts. The older I04/I06/I07 artifacts are actual retained outputs, but they are not substitutes for raw R1 output. Direct `am instrument` phases are not JUnit XML; no synthetic XML or missing PID was created. No restart replay was needed to evaluate this scoped close: protocol code asserts process identity and durable post-death state, R1 records the successful phase outcomes, and the requested fast-close did not require repeating already executed restart suites. Remaining traceability gap is explicit rather than treated as absent test execution.

## Consolidated gate disposition

| Gate / criterion | Result | Basis and limitation |
|---|---|---|
| Reporting and dashboard correctness | **PASS_WITH_LIMITATIONS** | Existing A1–E evidence and retained suites; bounded pagination, filter, date and snapshot tests. |
| Financial precision and currency integrity | **PASS** | Exact money Room evidence plus the new null/mixed-currency FINANCES fail-closed test (7/7). Unknown historical purchase currency remains unavailable by design; it is not a zero or inferred currency. |
| Authorization and user isolation | **PASS_WITH_LIMITATIONS** | Existing authority/UI denial and session tests. New regression tests atomic failure of an authorized projection, not authorization itself. |
| Date/timezone/DST and lifecycle | **PASS_WITH_LIMITATIONS** | Existing Dashboard and reporting tests; representative coverage only. |
| I01–I08 non-regression | **PASS_WITH_LIMITATIONS** | Existing configured app/native evidence; no business write, schema, or dependency change in this mission. |
| Restart durability | **PASS_WITH_LIMITATIONS** | Reported prepare/kill/verify outcomes pass; later R1 raw transcripts and verifier PIDs are incomplete as detailed above. |
| Performance / UI bounds | **PASS_WITH_LIMITATIONS** | Bounds are source-backed and correctness-tested; no latency, heap, frame-time or memory budget measurement. |
| Accessibility / screenshots | **PASS_WITH_LIMITATIONS** | Automated semantics and representative screenshots exist; no manual TalkBack audit or complete locale/theme/width matrix. Team's 420-dpi exact-width assertion remains quantization-sensitive. |
| G-PASS | **PASS_WITH_LIMITATIONS** | Currency failure invariant now has direct Room regression. No new P0/P1 found in scope. Preserved P2 limits remain. |
| G-EVIDENCE | **PASS_WITH_LIMITATIONS** | New XML, source-before-test timestamps and this matrix are retained. Exact R1 restart command/output and verifier PID transcripts remain the specific trace limitation; no result was fabricated. |

### Findings

- **P0:** none identified.
- **P1:** none remaining in this scoped close. The known missing/mixed currency coverage gap is closed by the new native regression.
- **P2:** no measured performance budget; no manual TalkBack review; representative rather than exhaustive screenshots; Team 420-dpi boundary assertion sensitivity; historical purchase currency unavailable; incomplete raw transcripts/numeric verifier PIDs for later R1 restart phases.

### Remaining prerequisites and worktree

No source/test suite beyond the targeted Room class was required. Final repository checks are recorded in the mission result. Before any G-COMMIT decision, a reviewer should inspect the complete accumulated I09 diff and decide whether the R1 restart trace limitation is acceptable under G-EVIDENCE. This document does not authorize staging or commit.

Final dirty-path inventory at close: **86 paths**, all under `store-android`; index empty. The 86 paths are the pre-existing 85 I09 paths plus this new evidence document; the existing `ReportingNativeTest.kt` path was already dirty and the retained `ReportingNativeTest.xml` path was already present. No paths outside the allowed repository area, production reporting implementation, Room schema/migration, dependencies or business-write operations were changed.
