# I09-E-R1 — Qualification gap closure

Date: 2026-10-09. Branch `feature/store-i09`; HEAD `3691eccedc9855686b011e34c7420f17aaf6c085`. This is an evidence addendum to `I09_E_QUALIFICATION.md`, not an I09 acceptance or release decision. No production or test source was modified.

## Restart protocol source review and execution

The four guard failures in `I09_E_infrastructure_full_initial.xml` were missing-argument guards, not business-assertion failures. Each test intentionally requires an explicit phase. Protocols use the existing synthetic infrastructure test package `com.vibe.store.infrastructure.test` and runner `androidx.test.runner.AndroidJUnitRunner`. Device verification: `emulator-5554`, AVD `STORE_API36_Main`, API 36. The package was absent before setup; after installing the test APK its `no_backup` fixture directory was empty. No app profile, production database, or existing test fixture was deleted or cleared.

| Class and method | Required arguments / phases | Synthetic data and process-death contract | Result |
|---|---|---|---|
| `CatalogRestartTest.realInterruptionPreservesMediaReferences` | `catalogPhase=prepare|verify`, `catalogBoundary=before|after`, unique `catalogRun` matching `[a-z0-9-]{1,40}` | `no_backup/i04-restart-$run-$boundary`; `prepare` creates owner/product/media and blocks at the selected reference boundary after writing `ready` with PID. External force-stop is required. `verify` requires a different PID and checks products, movement, media references/orphan rules, login and DB integrity. No fixture cleanup is performed. | `before`: prepare reached READY, externally stopped; verify **1/1 PASS**. `after`: same, verify **1/1 PASS**. Runs `e-r1-20261009-before` and `e-r1-20261009-after`. |
| `I07RestartTest.realDeathPreservesPurchaseInventoryAndCompensationAtomicity` | `i07Phase=prepare|verify`, `i07Operation=purchase|inventory|compensation`, `i07Boundary=before|after`, unique `i07Run` matching `[A-Za-z0-9_-]{1,64}` | `no_backup/i07-p6-$run-$operation-$boundary`; `prepare` seeds a synthetic owner/product and operation, writes PID/package/operation/boundary/IDs/token to `ready`, then blocks. External force-stop is required. `verify` requires a different PID, logs in, checks atomic durable state and then checks idempotent/explicit resolution behavior. No cleanup is performed. | All six operation × boundary pairs reached READY, were externally stopped, and their corresponding verify each reported **1/1 PASS**. Run IDs are `e-r1-20261009-{purchase,inventory,compensation}-{before,after}`. |
| `SaleRestartTest.realDeathPreservesSubmittedCommandWithoutAutomaticSale` | `salePhase=prepare|verify`, `saleBoundary=before|after`, unique `saleRun` matching `[A-Za-z0-9_-]{1,64}` | `no_backup/i06-restart-$run-$boundary`; prepare seeds synthetic sale/cash state, writes READY and blocks at commit boundary. External force-stop is required. Verify requires a different PID, checks no automatic replay, then explicitly resolves the pending command once and checks integrity. No cleanup is performed. | `before` and `after`: prepare reached READY, externally stopped; verify **1/1 PASS** for each. Runs `e-r1-20261009-before` and `e-r1-20261009-after`. |
| `IdentityNativeTest.realProcessDeathRequiresAuthenticationWithoutCashOrAttendanceChanges` | `identityRestartPhase=prepare|verify`; no boundary argument | Fixed test-only root `no_backup/i03-process`. Preflight confirmed the path absent. Prepare bootstraps synthetic owner/open cash and persists the PID. External force-stop is required. Verify checks changed PID, no restored session, authentication refusal, and unchanged cash/attendance. The fixture remains in the test package; no cleanup/reset was done. | Prepare **1/1 PASS**, external force-stop, verify **1/1 PASS**. |

`CatalogRestartTest`, `I07RestartTest`, and `SaleRestartTest` deliberately suspend with `awaitCancellation()` after writing READY. Their prepare invocation therefore ends as `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` / `INSTRUMENTATION_CODE: 0` after the required external stop; that is expected orchestration, not a JUnit PASS. Each subsequent verify invocation returned `OK (1 test)`. The identity native prepare is a normal passing test followed by an external stop and passing verify. The previously retained application-level `IdentityUiTest.applicationSessionNotRestoredAfterRealDeath` evidence remains `I09_E_Identity_restart_verify.xml` (1/1), with its prepare contained in the configured app run.

The restart phases were driven with the source-defined `am instrument -e class ...` arguments, READY-marker checks, and external `am force-stop`; no argument keys or enum values were invented. Standalone `am instrument` emits instrumentation status text, not Gradle JUnit XML. No synthetic XML was manufactured: separate phase-level XML files were therefore not emitted for these newly executed direct-runner phases. Existing aggregate diagnostic XML remains explicitly failed/non-acceptance evidence. The direct invocation results above are the observed phase results; a retained per-phase XML artifact is an evidence-format limitation.

## Currency failure coverage

`RoomReportingRepository.salesMetrics()` reads each invoice currency and throws `WorkflowFailure(UNAVAILABLE)` for a missing currency or a currency different from the first invoice before returning the aggregate. This is fail-closed in source. `ReportingNativeTest` verifies exact sales in one known currency and separately verifies cancelled-sale exclusion and unknown historical purchase-currency handling. Neither `ReportingNativeTest` nor the JVM reporting tests constructs a validated invoice with null currency or a mixed-currency set. Thus the explicit missing/mixed-currency Room regression and the “no partial finance snapshot” behavior are **UNVERIFIED by dedicated automated test**, despite the source guard. No source or test changes were made.

## Query, UI and performance bounds

| Concern | Classification | Evidence |
|---|---|---|
| Report aggregate pagination | **BOUNDED_BY_DESIGN; correctness boundary tested** | `RoomReportingRepository` uses 200-row keyset batches and advances the cursor for invoice, purchase, product-line, stock/movement and attendance scans. Native `ReportingNativeTest.validatedSalesTotalsContinueAcrossDisplayPageBoundary` seeds 201 invoices with `rowLimit=3` and expects all 201 transactions and exact `0.201` revenue. `expiredLeaseIsCheckedBeforeProductRankingQuery` separately tests the pre-DAO lease check. The purchase fixture seeds 206 records with `rowLimit=1` and verifies complete status counts. |
| Returned report rows | **BOUNDED_BY_DESIGN** | `ReportFilter.rowLimit` is 1–200; repository applies the limit to display rows, while aggregates iterate all matching batches. Stock rows are divided between current stock and movements. |
| Price history / series | **BOUNDED_BY_DESIGN** | Price history query limit is 200; only up to 10 product series are returned. `ReportSeries` rejects more than 3,660 points. |
| Compose rendering | **BOUNDED_BY_DESIGN; not stress-measured** | Detailed rows are capped at 200. The detailed table uses a regular `Column` (not lazy virtualization); a separate numeric chart alternative uses `LazyColumn`. The capped table has a width/rendering UI test. |
| Runtime performance | **UNVERIFIED** | No reproducible latency, heap, DB-read-count, scroll-frame, or large-dataset stress measurement was found. Correctness tests at 201/206 records are not performance benchmarks. |

## Adaptive test diagnosis

`TeamPresentationUiTest.adaptiveTeamWindowAndEvidence` uses `abs(actualDp - requestedDp) < 0.1f` at target widths 599 and 839 dp. At 420 dpi, density is 2.625: 599 dp rounds to 1,572 px and measures 598.857 dp; 839 dp rounds to 2,202 px and measures 838.857 dp. The 0.143 dp quantization error exceeds the test's 0.1 dp threshold, so the timeout is a brittle exact-width assertion at that density. The test checks layout class/card behavior only after this gate; therefore the 420-dpi run is **not evidence** that those boundary layouts passed or that a real clipping defect exists. The retained test passed 4/4 at 320 dpi, where those widths map to integral pixels. Do not weaken the threshold silently; record 420-dpi boundary layout as **UNVERIFIED** pending an explicitly accepted tolerance/protocol.

## Test report audit

| Evidence | Observed result |
|---|---|
| JVM XML set `test-results/jvm/TEST-*.xml` | 12 XML files, **78 tests / 0 failures / 0 errors / 0 skipped**. |
| Configured app suite `I09_E_app_full_configured.xml` | **73 / 0 / 0 / 0**; includes configured adaptive and app identity prepare arguments. |
| Reporting Room `ReportingNativeTest.xml` | **6 / 0 / 0 / 0**. |
| Exact money Room `ExactMoneyPersistenceTest.xml` | **8 / 0 / 0 / 0**. |
| Sale Room `SaleNativeTest.xml` | **9 / 0 / 0 / 0**. |
| Identity UI restart verify `I09_E_Identity_restart_verify.xml` | **1 / 0 / 0 / 0**; earlier successful application target process-death protocol. |
| Initial unconfigured app/infrastructure aggregates | Kept separate as diagnostics: app **73 tests, 5 argument/setup failures**; infrastructure **82 tests, 4 explicit-argument guard failures**. They are not successful final suites. |
| Newly run infrastructure restart verifications | Catalog **2 PASS**, I07 **6 PASS**, sale **2 PASS**, native identity prepare + verify **2 PASS**. Prepare invocations that were intentionally killed are described above, not counted as JUnit passes. Direct-runner outputs were not emitted as separate XML, as noted. |

The accepted XML suite timestamps and source mtimes were checked: reporting source/test edits predate the final focused Reporting UI XML and configured app XML; reporting repository and native-test sources predate the retained 6-test Room XML. Exact money and sale sources predate their respective retained XML. This mission made no source changes. The configured application suite is the 73-test integration result; targeted Reporting UI, Dashboard, Store Navigation, and Team XML are retained separately for their focused counts.

## Outcome and remaining gaps

- No P0/P1 defect was identified in this scoped qualification. The restart protocol guard gaps are closed by the source-defined isolated synthetic protocols above.
- P2 / unverified: dedicated null/mixed sales currency Room regression; performance targets and stress measurements; 420-dpi exact-boundary Team layout (test tolerance is quantization-brittle); manual TalkBack coverage and full visual matrix remain outside this remediation; no per-phase XML exists for direct-runner restart phases.
- The API 36 emulator remained on its existing physical display profile; no size/density override or profile reset was used. Test-only fixture data was not deleted.
- Qualification does not establish formal I09 acceptance or release readiness.

## Final repository checks

`git diff --check`: PASS. `git diff --cached --name-status`: empty. No source/test files were changed in this remediation. Worktree remains intentionally dirty from earlier I09 work, with existing changes confined to `store-android`; no staging, commit, reset, clean, merge, or push occurred.
