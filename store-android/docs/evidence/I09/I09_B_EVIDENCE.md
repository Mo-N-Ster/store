# I09-B — Reporting backend implementation evidence

Status: backend implementation for acceptance review; I09 is not accepted.

## Authorization boundary

- `ReportingAuthority` runs each request through the current `IdentityAuthority.authorizedRead` lease.
- Dashboard requests require `DASHBOARD:READ`; report snapshots require `FINANCES:READ`.
- Purchases additionally require effective `PURCHASES:READ`; team reports additionally require effective `PRESENCE:READ`.
- Dashboard presence rows are omitted unless the same trusted current identity has effective `PRESENCE:READ`.
- Effective rights are loaded from the trusted security repository and role policy. The request DTO contains no role or rights claim. Rejection happens before the reporting repository is called.
- Filter validation rejects statuses and dimensions that do not apply to the selected tab.

## Projection and consistency behavior

- `RoomReportingRepository` is exposed by the existing `RoomRepositories` object and checks the current read lease before each query batch. `DatabaseOwner.read` keeps the complete operation inside its Room read transaction, so pages and detail projections share one database snapshot.
- Invoice, stock, movement, purchase, and attendance aggregates use keyset pages of 200. Display rows are independently capped by `ReportFilter.rowLimit` (1–200); totals continue across all keyset pages. A native fixture verifies 201 validated sales aggregate to `0.201` while displaying three rows.
- Product rankings stream ordered line pages, bulk-load invoice headers and lines for each page, and retain only one page, one active group, and the requested top-N groups. Existing workflow limits cap an individual document at `WorkflowRules.MAX_LINES` (10,000); no report loads an unbounded table result or issues per-line queries.
- Sales time-series carry explicit date labels alongside exact `BigDecimal` points. DAY/WEEK/MONTH filters aggregate within the requested half-open interval; missing buckets are emitted as zero, and a week label identifies its Monday start. The optional labels field preserves compatibility for non-time-series metrics.
- Stock reports allocate the display row cap between current product state and period movements. Stock counts describe current product state; movement counts are filtered by the requested half-open period.
- Dates use `[fromInclusive,toExclusive)`. Sales, purchase and movement dates use the stored timestamp's calendar-date prefix, consistent with the existing user-facing date filters. Attendance uses system-local day starts converted to UTC, as `PresenceAuthority` does, including daylight-saving transitions.
- Product/category sales dimensions use invoice-line name/category snapshots. Stock category filtering uses the current product category. Purchase period filtering uses creation date and the purchase's current status, as required by the I09 semantics.
- Sales include validated invoices in revenue and transaction count; cancellations are separately counted and can be shown in the sales detail projection. Discounts use accepted A1 `ReportingCalculations` on all invoice lines before the requested line allocations are selected. Estimated margin remains explicitly disclosed and uses snapshot cost, with price fallback marked separately.
- Purchases have no stored currency snapshot in the existing schema. R1-A therefore marks purchase and finance document currency `UNAVAILABLE` (`XXX` sentinel), omits purchase monetary values from rows, and does not aggregate them in FINANCES. Sales retain their invoice snapshot currency and mixed/missing sales currencies fail closed. No schema change was introduced.
- Attendance hours include completed sessions only. Open sessions contribute to the open count and no hours. No historical inventory valuation or accounting profit is calculated.
- All report reads are projections only. No writes, analytics tables, Room schema changes, or migrations were added.

## Verification

- `:verifyArchitecture` — PASS (seven-module dependency allowlist).
- `:testing:test --rerun-tasks` — PASS (application authorization/filter tests and domain calculation tests).
- `:infrastructure:assembleDebugAndroidTest` — PASS.
- `:infrastructure:connectedDebugAndroidTest` targeting `ReportingNativeTest` — PASS on the existing synthetic API 36 emulator. Fixtures use UUID-scoped test databases and cover validated/cancelled sales, historical category filtering, exact purchase decimals, current stock plus movements, completed attendance hours, and aggregate totals beyond a three-row display cap.
- Native fixtures also check Room foreign-key/integrity verification. Authorization denial and no-repository-call behavior are covered by JVM tests.
- `git diff --check` — PASS after final edits (see final command output).

## Remaining acceptance review

Historical purchase amounts remain unavailable until authoritative currency provenance exists. Dashboard/report UI integration is outside I09-B and remains for later phases.

## R1-B / R2 date and filter clarification

The date-mapping description above records an earlier implementation state. Current reporting uses the selected `ReportFilter.timeZoneId` to map `[fromInclusive,toExclusive)` local calendar bounds to UTC instants; dashboard projections use the same explicit zone in `DashboardRequest`. R2 also makes the legacy dashboard filter overload accept only one complete local day with `DAY` grain. See `I09_B_R2_EVIDENCE.md` for the current filter corrections and tests.
