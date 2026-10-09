# I09-F-P1 — Final evidence and G-PASS review

Review date: 2026-10-09. Repository: `feature/store-i09`, expected/current HEAD `3691eccedc9855686b011e34c7420f17aaf6c085`. Index empty; 84 dirty paths, all under `store-android`; the existing I09 work is preserved. No source, test, schema, or prior evidence file was changed by this review.

## Acceptance baseline

This review records the acceptance baseline expressly supplied in the I09-F-P1 mission. It does not convert earlier implementation reports into stronger acceptance claims.

| Phase | Recorded acceptance | Supporting phase evidence and preserved limits |
|---|---|---|
| I09-A1 | **ACCEPTED** | `I09_A1_EVIDENCE.md`: public contracts, immutable document model, exact pure calculations, targeted JVM fixtures. Its stated allocation and repeating-average policies remain computational/reporting policies, not tax/accounting rules. |
| I09-B | **ACCEPTED_WITH_LIMITATIONS** | `I09_B_EVIDENCE.md`, `I09_B_R1_A_EVIDENCE.md`, `I09_B_R1_B_EVIDENCE.md`, `I09_B_R2_EVIDENCE.md`: authorized Room projections, bounded keyset aggregation, seven report contracts, filter semantics and currency-unavailable purchase handling. Historical purchase currency remains unavailable; it is not restored by a current setting. |
| I09-C | **ACCEPTED_WITH_LIMITATIONS** | `I09_C_EVIDENCE.md`, `I09_C_R1_EVIDENCE.md`: application-lifetime wiring, dashboard privacy/lifecycle and local-date/timezone refresh. The timezone is device-local; no timezone picker is provided. |
| I09-D | **ACCEPTED_WITH_LIMITATIONS** | `I09_D_EVIDENCE.md`, `I09_D_R1_EVIDENCE.md`, `I09_D_R2_EVIDENCE.md`: seven-report UI, filters, KPIs, tables, currency disclosures and final synthetic FINANCES screenshots. Screenshot matrix is representative, and TalkBack was not manually audited. |
| I09-E | **ACCEPTED_WITH_LIMITATIONS** | Explicitly accepted at this mission's baseline. `I09_E_QUALIFICATION.md` and `I09_E_R1_EVIDENCE.md` remain qualification records, not themselves acceptance decisions. Preserved limitations include no currency-failure Room regression, no performance measurements, 420-dpi Team-boundary test fragility, no full visual matrix/TalkBack run, and incomplete restart transcript/XML retention. |

## Contractual G-PASS matrix

I09 section 6 of `STORE_3_IMPLEMENTATION_CONTRACT.md` requires the dashboard/seven reports, applicable data/security invariants, concordance among chart/table/snapshot, G-NR and G-EVIDENCE. G-PASS requires required assertions and relevant regressions to pass, no required test to be skipped, and states that NOT TESTED is not PASS. I09 A–F gates are explicitly enumerated in `I09_IMPLEMENTATION_PLAN.md`; F is the evidence review, G-PASS and controlled commit gate.

| Criterion | Finding | Evidence / limitation |
|---|---|---|
| Reporting correctness: seven tabs, applied filters, rows/series and snapshot concordance | **PASS_WITH_LIMITATION** | `ReportingNativeTest.xml` 6/6; `ReportingUiTest.xml` 14/14; native 201-invoice and 206-purchase fixtures cross display/batch boundaries. Bounded result rows and price-history limitations are disclosed. |
| Dashboard correctness and authorization | **PASS** | `DashboardUiTest.xml` 12/12 and configured app suite 73/73; one canonical dashboard request/snapshot, independent presence restriction and lifecycle invalidation are covered. |
| Financial precision, disclosures and currency integrity | **BLOCKED** | Exact decimal Room persistence `ExactMoneyPersistenceTest.xml` 8/8 and finance UI tests pass; purchases with unknown historical currency stay unavailable. However, missing/mixed sales-currency fail-closed behavior has source guards but no Room/JVM regression that exercises those branches; see Currency disposition. It cannot be silently treated as covered under G-PASS. |
| Role-based access, individual denials and isolation | **PASS** | `ReportingAuthorityTest` JVM suite and configured app/Reporting UI reports exercise effective rights and denial-before-repository/UI leakage paths. No UI role claim is an authorization source. |
| Date, timezone and DST semantics | **PASS_WITH_LIMITATION** | Dashboard UI 12/12 and native reporting tests cover local calendar boundaries, year/month transitions, timezone and DST behavior. Team's 420-dpi 599/839dp exact-width assertion is quantization-brittle; that particular Team boundary is unverified at 420dpi, although the retained Team suite passed at 320dpi. |
| I01–I08 non-regression | **PASS_WITH_LIMITATION** | Configured app suite 73/73 includes existing destinations; Store Navigation 6/6, Workflow Navigation 6/6, and SaleNative 9/9 pass. No business-write/schema changes were made for I09. The 420dpi Team test issue remains an inherited test-evidence limitation, not a demonstrated I09 layout regression. |
| Native integration and restart behavior | **PASS_WITH_LIMITATION** | Reporting Room 6/6, ExactMoney 8/8, Sale 9/9; I09-E-R1 reports successful specific restart verifications. Durable per-phase command/result logs and exact verify PID values are absent for R1, so restart evidence is not fully auditable from repository artifacts. |
| Performance bounds | **PASS_WITH_LIMITATION** | Bounded by design: 200-row Room keyset batches/display cap; 200 price-history observations/query; at most 10 product series; 3,660 series points. UI tables cap at 200 but use a regular `Column`. No latency/heap/frame/query-count benchmark exists. I09 does not specify numerical performance targets; I13 explicitly owns measured performance budgets. |
| Accessible UI coverage | **PASS_WITH_LIMITATION** | Automated semantics, expansion controls and numeric chart alternatives are covered by UI tests; screenshots cover all seven tabs and representative sizes/themes/languages. No manual TalkBack review or full cross-product screenshot matrix exists. I13 owns TalkBack/contrast/reduced-motion consolidation; I14 owns physical phone/tablet acceptance. |
| Existing accepted limitations | **PASS_WITH_LIMITATION** | Unknown historical purchase currency, estimated margin, bounded/truncated history, and no accounting profit or historical stock valuation remain explicit. These do not become certified values. |

## Currency safety disposition — COVERAGE_GAP

`RoomReportingRepository.salesMetrics()` reads each invoice's persisted currency and throws `WorkflowFailure(UNAVAILABLE)` when it is null or differs from the first invoice currency (source around lines 201–203). The aggregation is not returned after this failure. This is good source-level fail-closed behavior, but `ReportingNativeTest` seeds only a single known validated sales currency for its exact/revenue fixtures; it contains no validated null-currency invoice and no mixed-currency invoice set. The JVM reporting tests exercise domain/authority rules, not these Room persisted-currency branches. Existing XML therefore does not demonstrate that no finance `DocumentModel` or partial aggregate leaks on either failure.

**Decision: COVERAGE_GAP; G-PASS remains blocked.** The minimal required regression is a focused synthetic Room test (or two test cases in the existing `ReportingNativeTest`) that seeds: (1) a validated invoice with null currency and otherwise valid lines; and (2) two validated invoices with distinct persisted currencies. For both cases, call the FINANCES snapshot through the authorized read path, assert the documented `UNAVAILABLE` failure, and assert that no partial `DocumentModel`, revenue, currency label, rows, or series is returned. Keep fixtures UUID-scoped, read-only after seeding, and under the existing owner/read lease. No test was implemented in this evidence-review phase.

## Restart evidence disposition

### Existing retained artifacts

- **Catalog/I04:** `I04/rf001-before.txt` and `rf001-after.txt` contain exact verify commands, `OK (1 test)`, the persisted prepare PIDs (4760 and 4861), and assertions that the verifier PID differed. They document the prior force-stop as history; the original prepare/kill transcript and exact verification PID are not present. These are prior I04 runs, not the I09-E-R1 run IDs.
- **I07:** `I07_P6_EVIDENCE.md` records the six-case matrix and result. `I07_EVIDENCE_INDEX.txt` references `runtime/p6a-r3/*-prepare.log` and `*-verify.log`, but those files are absent from the current evidence inventory. The retained P6-C2 bundle contains ordinary persistence test logs, not the P6-A restart phase logs. Do not treat the missing indexed files as present proof.
- **Sale/I06:** `I06_ACCEPTANCE_REPORT.md` and `I06_EVIDENCE_INDEX.txt` record the before/after process-death results and old PIDs 7059/7148 with hashes for named prepare/verify logs. Those raw `store-i06-r14-*-prepare.log` and `*-verify.log` files are not present under the current repository evidence inventory. The report remains a historical accepted I06 result; the missing files limit independent reinspection here.
- **Application identity:** `test-results/android/I09_E_Identity_restart_verify.xml` is a real 1/1 verify report; the prepare is part of `I09_E_app_full_configured.xml` (73/73). The XML has no PID transcript. `I09_E_R1_EVIDENCE.md` additionally reports native identity prepare/verify 1/1 each but retains neither direct-runner transcript nor exact PIDs.
- **I09-E-R1 infrastructure runs:** the test-only emulator currently retains the READY markers for the run IDs in `I09_E_R1_EVIDENCE.md`. Read-only marker values establish prepare PIDs: Catalog before 18016 / after 18107; I07 purchase before 18191 / after 18270, inventory before 18351 / after 18429, compensation before 18511 / after 18591; Sale before 18670 / after 18748. The verification tests passed according to R1 and their source asserts a different PID. Exact verifier PIDs, retained `am force-stop` output, and standalone instrumentation transcripts are not in the repository. The test code defines the exact required phase keys and allowed boundary/operation values; R1 records the run IDs, but not full per-invocation command transcripts. Standalone `am instrument` did not create JUnit XML, and none has been fabricated.

**Assessment:** restart outcomes are supported by source assertions, R1's contemporaneous result report, and extant READY markers, but G-EVIDENCE's expected process/phase durable trace is only partially retained. No replay is justified for this read-only review. If the final I09 reviewer requires full traceability, rerun only the missing synthetic restart cases under a separately authorized, safely isolated protocol and retain each real prepare/verify log plus PIDs; never reuse or delete the existing runs.

## G-EVIDENCE audit and inventory

Actual retained XML reports have zero errors/failures/skips in their final named runs:

| Suite | Result | File |
|---|---:|---|
| JVM | 78/78 | `test-results/jvm/TEST-*.xml` (12 suites) |
| Configured app instrumentation | 73/73 | `test-results/android/I09_E_app_full_configured.xml` |
| Reporting UI | 14/14 | `test-results/android/ReportingUiTest.xml` |
| Dashboard UI | 12/12 | `test-results/android/DashboardUiTest.xml` |
| Store navigation | 6/6 | `test-results/android/StoreNavigationUiTest.xml` |
| Workflow navigation | 6/6 | `test-results/android/WorkflowNavigationUiTest.xml` |
| Team presentation | 4/4 | `test-results/android/TeamPresentationUiTest.xml` |
| Reporting Room | 6/6 | `test-results/android/ReportingNativeTest.xml` |
| Exact money Room | 8/8 | `test-results/android/ExactMoneyPersistenceTest.xml` |
| Sale Room | 9/9 | `test-results/android/SaleNativeTest.xml` |

The first unconfigured app and infrastructure runs remain separately named diagnostics (73 tests/5 setup-argument failures and 82 tests/4 argument-guard failures); later configured app and targeted restart results supersede them for pass counts. Earlier failed UI XMLs are preserved and clearly named as attempts. Historical A1/B/C/D docs sometimes describe their then-current counts; R1/R2 addenda and the later named XML are the controlling evidence for later corrections.

Fourteen screenshots are valid PNGs: all seven reports are represented; examples cover compact/medium/expanded, English/French and light/dark. D-R2 documents final FINANCES capture provenance and review. The images are synthetic and have no reported real-user data. There is no full tab × language × theme × width matrix, and screenshots do not prove TalkBack accessibility.

The worktree inventory is 84 dirty paths, all under `store-android`; index empty. It contains the expected accumulated I09 source/evidence and the one existing implementation plan path. No out-of-scope path was found. No source paths, schema/migration files, or dependencies were changed by this review. Evidence is versioned across phase and remediation files; older reports are historical rather than redundant deletions. The missing I06/I07 raw logs and I09-R1 per-phase traces remain actual gaps. No evidence file was deleted or altered.

## Deferrals and ownership

| Limitation | Owner / handling |
|---|---|
| Unknown currency provenance for legacy purchases | Accepted I09-B limitation. Do not infer currency, zero, or profit. Any schema/provenance redesign is outside I09 and requires its own approved future scope. |
| Missing/mixed **sales** currency test | I09-F targeted closure: add the minimal Room regression before claiming G-PASS; do not defer the financial invariant silently. |
| Numerical performance budgets, low-memory/weak-device and broader stress profile | I13 explicitly owns measured performance budgets and broader integrated qualification. I09 source bounds are evidenced, not measured. |
| Manual TalkBack, contrast/reduced-motion consolidation and full accessibility interaction audit | I13. Existing automated semantics remain valid but are not a manual assistive-technology result. |
| Physical phone/tablet and release artifact acceptance | I14. I09 does not authorize release, deployment, signing or publication. |
| 420-dpi exact Team threshold test brittleness | Preserve as a documented inherited test limitation for its owning UI qualification/I13 non-regression review; do not weaken its threshold without an explicit decision. |
| Complete restart command/PID transcript retention | I09-F evidence closure if required for G-EVIDENCE; currently partial, not silently PASS. |

## P0/P1/P2 and final decision

- **P0:** none found in this evidence review.
- **P1:** no demonstrated reporting source defect found. G-PASS is nevertheless **BLOCKED** by the untested missing/mixed-currency failure invariant and incomplete restart traceability for a durable/lifecycle evidence gate.
- **P2:** no measured performance budgets; no manual TalkBack/full screenshot matrix; quantization-brittle 420-dpi Team exact-width assertion; historical purchase currency unavailable by accepted design. These are deferred only to the owners above and remain visible limitations.

**G-PASS decision: BLOCKED.** The accepted A1–E baseline is preserved, including E ACCEPTED_WITH_LIMITATIONS, but it does not waive G-PASS or G-EVIDENCE. Required focused currency failure coverage is absent. Restart test results are reported as passed but their durable trace package is partial. A final reviewer must decide whether the restart evidence gap requires a focused rerun or can be accepted with the extant marker/source assertions and R1 report.

**Final commit prerequisites:** resolve the currency coverage gap with the focused native Room regression; retain the actual test XML and source/test timestamps; close or explicitly disposition restart trace completeness with existing non-destructive evidence or authorized isolated reruns; update the final evidence matrix with exact commands/results and remaining accepted limitations; rerun `git diff --check`; review the entire diff and allowed paths; then, only under explicit commit authorization, stage the reviewed I09 paths and create the single local G-COMMIT `feat(android): I09 implement dashboard and reporting`. The tree must be clean before the next phase. No merge, push, tag, release, deployment, I10, I13 or I14 is authorized here.
