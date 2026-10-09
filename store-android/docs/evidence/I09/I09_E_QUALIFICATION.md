# I09-E — Cross-cutting qualification

Date: 2026-10-09. Repository remains on `feature/store-i09`, HEAD `3691eccedc9855686b011e34c7420f17aaf6c085`. This is a qualification record, not an I09 final commit, release qualification, or I10 start.

## Contract alignment and scope

The authoritative implementation contract defines I09 as the dashboard and seven-report phase, with G-PASS, G-NR, and G-EVIDENCE acceptance criteria. It does not name an I09-E subphase. The I09 implementation plan divides the work into A–F and defines E as regression, date/DST, scale, and adaptive validation. The requested cross-cutting checks extend that validation but do not conflict with I09's business, security, or architecture boundaries. The subsequent phases remain I10 and later; I14 is the separate final release qualification.

The prior acceptance statuses A1 ACCEPTED, B/C/D ACCEPTED_WITH_LIMITATIONS are taken from the approved baseline in the mission and their corresponding I09 evidence files. No source was changed during this qualification. Only I09 evidence and retained test XML were written or refreshed.

## Qualification findings

### Security and lifecycle

- `ReportingAuthority` gates every report with effective `FINANCES:READ`; PURCHASES and TEAM add effective `PURCHASES:READ` and `PRESENCE:READ`. Dashboard access uses `DASHBOARD:READ`, while presence context is independently included only when currently effective. The repository is called inside `authorizedRead`; UI identity/role claims are not used to grant backend access.
- JVM authorization tests and the 73-test app suite cover permission denials, no-data denied states, logout/account transitions, stale response rejection, lifecycle STOP/RESUME, and independent presence behavior. Dashboard tests cover local midnight, timezone changes, and 23/25-hour DST deadlines. The identity process-death test's `prepare` phase ran in the full app suite and its `verify` phase passed after a real app force-stop; no data clear/reset was performed.
- These tests use synthetic authorities/fixtures and the authorized synthetic emulator. They do not establish a penetration test against a production identity or database.

### Financial and reporting integrity

- Source inspection confirms invoice currencies are required and cross-invoice currency differences fail closed before aggregation. Recognized sales revenue uses validated invoices; cancelled invoices increment their own count and are skipped from revenue/margin. Purchase currency provenance remains explicitly unavailable; FINANCES reports the validated-sales amount separately and does not calculate purchase-derived profit.
- Domain tests cover exact decimal inputs, repeat-ratio discount allocation, conservation/bounds, zero and tiny amounts, cancelled sales, missing-cost estimated-margin disclosure, and non-terminating average-basket precision. Room tests cover exact totals across batches, cancelled rows, unavailable historical purchase currency, current stock versus period movements, completed attendance, purchase counts, calendar boundaries, and lease enforcement.
- Price observations come from persisted history and are bounded; category filters use historical sold-line snapshots. UI numeric chart alternatives are drawn from the same series; report tables display the returned `DocumentModel` rather than recalculating business metrics.
- Coverage limitation: source code has explicit mixed/missing invoice-currency fail-closed branches, but there is no focused synthetic Room regression test for both mixed-currency and null-currency invoices.

### Bounds and performance

- Source-backed bounds: report filters cap rows at 200, the Room repository pages in batches of 200 with keyset cursors, product price points are capped at 200 per query, and report series validate at most 3,660 points. Aggregates iterate all keyset pages and the native 201-invoice fixture proves totals do not stop at the display-row limit. The UI table is bounded to 200 rows (not backend pagination); it currently composes those rows with a regular `Column`, not a lazy list.
- No reproducible latency, memory, database-read-count, or frame-time benchmark was run. Therefore performance is source-bounded but not performance-qualified. The 201-row boundary fixture and bounded UI test are correctness checks, not a stress benchmark.

### Presentation and accessibility

- The full app instrumentation suite exercises Dashboard, all seven reports, FR/EN presentation, permission states, table expansion, chart equivalents, navigation, and existing I01–I08 UI suites. Adaptive Team presentation passed at 320 dpi. Existing retained screenshots cover all seven report tabs across compact, medium, and expanded examples, with English/French and light/dark represented; final R2 FINANCES captures are `screenshots/compact_en_light_finances*.png` and `screenshots/compact_fr_dark_finances*.png`.
- Screenshot coverage is representative, not a full tab × language × theme × width matrix. TalkBack was not manually tested; semantics have automated assertions only. Some earlier viewport captures show content continuing beyond the scroll edge, with companion screenshots for full KPI/table regions; no overlapping or unreadable content was observed in the inspected captures.

## Test matrix and retained evidence

| Gate / suite | Result | Retained evidence |
|---|---|---|
| `:verifyArchitecture` | PASS | Gradle output from final qualification run |
| `:testing:test --rerun-tasks` | PASS, 78 JVM tests, zero failures/errors/skips | `test-results/jvm/TEST-*.xml` refreshed from the final run |
| `:app:assembleDebugAndroidTest` | PASS | Final offline qualification build |
| Complete app instrumentation, configured runner arguments | PASS, 73/73 | `test-results/android/I09_E_app_full_configured.xml` |
| Reporting UI | Included in 73/73 app suite; prior isolated 14/14 XML retained | `test-results/android/ReportingUiTest.xml` |
| Dashboard UI | Included in 73/73 app suite; prior isolated 12/12 XML retained | `test-results/android/DashboardUiTest.xml` |
| Store navigation | Included in 73/73 app suite; prior isolated 6/6 XML retained | `test-results/android/StoreNavigationUiTest.xml` |
| Team adaptive UI, exact-width profile | PASS, 4/4 | `test-results/android/TeamPresentationUiTest.xml` |
| Identity process-death verify phase | PASS, 1/1 after force-stop; prepare phase passed in the full app suite | `test-results/android/I09_E_Identity_restart_verify.xml` |
| Synthetic Room reporting | PASS, 6/6 | `test-results/android/ReportingNativeTest.xml` |
| Exact money persistence | PASS, 8/8 | `test-results/android/ExactMoneyPersistenceTest.xml` |
| Sale persistence/idempotency | PASS, 9/9 | `test-results/android/SaleNativeTest.xml` |
| Initial complete app instrumentation without fixture arguments | 73 tests; 5 setup/fixture failures, no skips | `test-results/android/I09_E_app_full_initial.xml` |
| Initial complete Room instrumentation without special restart parameters | 82 tests; 4 explicit-argument guard failures, no skips | `test-results/android/I09_E_infrastructure_full_initial.xml` |

The app retry supplied `expectedWidth`, `posWidth`, `workflowWidth`, `workflowSize`, and `appRestartPhase=prepare`; all 73 tests passed. The identity `verify` phase then passed after process death. The initial Room aggregate's four failures were parameter guards in `CatalogRestartTest`, `I07RestartTest`, `IdentityNativeTest`, and `SaleRestartTest`; these special multi-phase scenarios were not rerun with their distinct orchestration parameters. The standalone I09 reporting, exact-money, and sale/idempotency Room classes all passed.

The strict Team width test's 0.1 dp tolerance cannot be met at 420 dpi for 599/839 dp boundaries because pixel rounding yields a 0.143 dp difference. It passed at temporary 320 dpi, where boundary widths map to integral pixels. The workflow fixture left a temporary `786x1600` display override; it was explicitly reset. Final emulator verification: API 36 `STORE_API36_Main`, physical 1080×2400, density 420; no profile or database reset occurred.

## Worktree, risks, and release readiness

Preflight and final checks confirmed the expected branch/HEAD, empty index, 0 dirty paths outside `store-android`, and `git diff --check` PASS. The pre-existing worktree remains intentionally dirty. No schema version/migration, Gradle dependency, or business-write path was changed in I09-E.

P0: none found. P1: none found in this qualification. P2 limitations: no measured performance budget; no manual TalkBack run/full visual matrix; no focused mixed/null sales-currency regression; four special existing Room restart suites need their explicit phase/boundary arguments; the Team test needs a pixel-quantization-aware tolerance for the 420 dpi target.

Release readiness is **not established**. Suggested release gates remain: accept all I09 evidence; close or explicitly accept the P2 gaps; complete the remaining authoritative phases; freeze and identify the exact candidate commit/artifact; run required full and special restart suites with preserved XML; measure latency/memory/frame budgets on declared synthetic datasets/devices; complete TalkBack and device review; and, at I14, verify signing, artifact hash, install/update/restart/restore requirements and human attestations. No release artifact or signature was created here.
