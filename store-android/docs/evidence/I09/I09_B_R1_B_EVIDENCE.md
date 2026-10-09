# I09-B-R1-B — Reporting Functional Completeness Evidence

Status: implementation gates pass; pending I09-B acceptance review. This evidence does not claim I09 acceptance.

## Requirement coverage

| Requirement | Implemented behavior | Evidence / limitation |
|---|---|---|
| Dashboard snapshot | Added an authorized `DashboardRequest` carrying one as-of local date and IANA zone. The repository returns today's validated revenue/count/average, current zero/low-stock counts, current-month revenue, a 30-calendar-day daily trend, and monthly presence context only when the current identity has `PRESENCE:READ`. All projections run in the existing read lease/transaction. | `ReportingNativeTest.dashboardIsOneLocalCalendarSnapshotWithMonthAndRollingTrend`; `ReportingAuthorityTest.calendarDashboardUsesExplicitZoneAndEffectivePresenceRight`. Caller supplies the as-of date and zone; date bounds are `[from,to)` and translated to UTC instants, including DST-length days. |
| Purchase counts | Added keyset-paged counts for total, `VALIDATED`, `DRAFT`, and `CANCELLED`, applying the same date/status/supplier/product filters as displayed rows. Historical amounts remain unavailable (`XXX`/unavailable) and do not enter finance totals. | `ReportingNativeTest.purchaseStatusCountsCrossKeysetBatchesAndCalendarGrainsHaveZeroBuckets` crosses the 200-row batch boundary. Supplier names remain in bounded display rows; no supplier-count distribution was added because the I09 source contract requires purchase status/period semantics, not a supplier aggregation. |
| Product/category rankings and price history | Existing ranking uses invoice-line product/category snapshots and exact discount allocation. Added up to ten bounded product price series using only persisted `product_price_history` observations in the applied interval. An explicit product filter can show its source history even without an in-period sale. | `ReportingNativeTest.reportTabsUseExactValidatedTotalsCurrentStockAndCompletedHours` verifies snapshot-category ranking and a persisted historical price point. Curves are capped at 200 observations total; without an explicit product, curve candidates are the period's top ranked products. No price observations are synthesized. |
| Temporal grouping | DAY/WEEK/MONTH revenue buckets are generated over the full selected calendar interval and include zero buckets. Dashboard trend labels use local dates; sales/finance/summary share the same grouping rules. | Native test verifies DAY zero buckets, WEEK label across the 2025/2026 boundary, MONTH label, and Los Angeles DST bounds. WEEK/MONTH are rejected for PRODUCTS, STOCK, PURCHASES and TEAM: their implemented period values are discrete source observations, current-state/movement status, status counts, or completed attendance totals, for which regrouping would invent a metric. Product price observations keep their persisted timestamp labels. |
| Authorization and non-disclosure | Dashboard uses `DASHBOARD:READ`; reports use `FINANCES:READ`; purchase/team access applies current effective additional rights. Individual denials are re-evaluated before repository calls. Dashboard monthly presence is omitted without `PRESENCE:READ`. | Seven `ReportingAuthorityTest` cases pass, including effective denials and no repository invocation for unauthorized requests. Room access remains read-only and lease-checked. |
| Bounds and consistency | Invoice, purchase-count, product/category line, movement, attendance and stock scans are keyset-paged; display rows and price history have fixed caps. Totals are calculated over all matching batches inside the owner's single read snapshot. | Native coverage includes 201 sales and 206 filtered purchases with output row limits below the data count; synthetic Room integrity checks remain green. No schema, entity, migration or business-write changes were made for this task. |
| Money / currency integrity | Monetary values remain `BigDecimal`; invoice currencies are checked for mixed-currency periods. Purchase currency provenance remains unavailable and purchase totals are excluded from financial arithmetic. No SQL aggregation over monetary text was introduced. | Exact dashboard and sales values are asserted in synthetic fixtures. No accounting profit, historical stock valuation, or purchase-derived profit is claimed. |

## Validation results

- `bash ./gradlew --offline :verifyArchitecture`: PASS (`ARCHITECTURE_PASS`).
- `bash ./gradlew --offline :testing:test --rerun-tasks`: PASS, 76 tests across 12 suites; 0 failures, 0 errors, 0 skipped.
- `bash ./gradlew --offline :infrastructure:assembleDebugAndroidTest`: PASS.
- Verified `emulator-5554` is API 36, then ran only `ReportingNativeTest`: PASS, 6 tests.
- `git diff --check`: PASS.
- No network, schema/migration, UI, production database, or write-operation changes.

During validation, the first Android test assembly invocation had a transient KSP `MissingType` report for `StoreDao`; the diagnostic retry and subsequent required assembly completed successfully. The first instrumentation run exposed two fixture assertion mistakes (canonical `25` scale and a pre-midnight local timestamp); both fixtures were corrected, then all six native tests passed.

## Remaining limitations

- Dashboard as-of date and zone are caller inputs; consumers should provide the intended local calendar context. `ReportFilter.timeZoneId` defaults to the current system zone only for source compatibility.
- Price curves are bounded to ten ranked products and 200 persisted observations in the selected interval. They are not exhaustive export data.
- Historical purchase monetary amounts remain unavailable because the existing schema has no currency snapshot.
- Unsupported temporal grains are rejected for tabs without an implemented meaningful grouped metric.
- Existing I09-B/R1-A lifecycle and presentation work remains outside this task; this change does not establish I09-B acceptance.
