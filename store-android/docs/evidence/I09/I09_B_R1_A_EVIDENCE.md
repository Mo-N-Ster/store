# I09-B-R1-A — Reporting Integrity Hardening Evidence

Status: focused R1-A corrections implemented; I09-B acceptance is not claimed.

## Corrections

- Purchase currency provenance does not exist in the current schema. Purchase rows preserve reference, creation date, current status, and supplier, but amount cells now say `Unavailable` and contain no `BigDecimal`. PURCHASES and FINANCES `DocumentModel`s use `currencyAvailability=UNAVAILABLE` and the `XXX` no-currency sentinel. FINANCES does not read or aggregate purchase amounts, and it reports sales revenue with the invoice snapshot currency in its row and metric snapshot. The former sales-minus-purchases row explicitly says it is not calculated and has no monetary value. No schema or migration change was made.
- Sales detail labels the column “Recognized revenue”. Validated rows contain their accepted line allocation; cancelled rows preserve the `cancelled` status and show `Excluded` with a null monetary value. Cancelled invoices remain excluded from revenue and estimated margin.
- Product-ranking keyset iteration checks the Room repository lease before its first DAO page. Source review verified lease checks before all other I09 query batches and follow-up detail queries. The native lease-injection test fails at that first ranking check before an empty product query could make the missing guard pass unnoticed.
- Filters reject incompatible dimensions/statuses. WEEK/MONTH grain is rejected for PRODUCTS, STOCK, PURCHASES, and TEAM because those reports do not implement grain aggregation. `[fromInclusive,toExclusive)` date filtering is unchanged. Supporting those grains in these tabs is an R1-B requirement; this change adds no report feature.

## Tests actually executed

- `bash ./gradlew --offline :verifyArchitecture` — PASS.
- `bash ./gradlew --offline :testing:test --rerun-tasks` — PASS. `ReportingAuthorityTest`: 6 tests, 0 failures, including individual FINANCES denial, protected PURCHASES/TEAM rights, and incompatible filters. `ReportingCalculationsTest`: 12 tests, 0 failures.
- `bash ./gradlew --offline :infrastructure:assembleDebugAndroidTest` — PASS (compilation only).
- `bash ./gradlew --offline :infrastructure:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.infrastructure.persistence.ReportingNativeTest` — PASS on verified `emulator-5554` API 36. Android XML report records 3 tests, 0 failures: unavailable historical purchase currency and finance behavior, cancelled-row labeling/exclusion, first product-ranking lease check, and the existing exact report/page-boundary fixtures.
- `git diff --check` — PASS after final edits; final `git status --short --untracked-files=all` recorded for this run.

## Limitations retained

- Purchase monetary values remain unavailable until an approved source of historical currency provenance exists. Existing purchase history is not relabeled or estimated.
- Week/month grain is not implemented outside SUMMARY, SALES, and FINANCES; requests are rejected there rather than silently ignored.
- Stale-result invalidation after logout/user switch still belongs to the later reporting consumer/UI lifecycle and is not guaranteed by a retained immutable `DocumentModel` in this backend-only scope.
- Dashboard dual-period requirements and product price-history curves remain outside this focused correction and require later I09-B follow-up.
