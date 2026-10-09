# I09-B-R2 — Reporting filter integrity evidence

Status: targeted corrections prepared for I09-B-GATE repeat audit; this does not claim I09-B acceptance.

## P1-A — Product price history filters

Persisted `product_price_history` rows contain product, price and recorded time, but no category snapshot. When a PRODUCTS filter supplies both `productId` and `category`, the repository now first selects a candidate through validated invoice lines using the line's historical category snapshot. It then returns only that product's persisted price observations in the requested time interval. A current product category is never used to satisfy the category filter, and an empty historical match produces no price series. Product/category ranking projections continue to use the same invoice-line snapshot semantics. Candidate selection remains keyset-paged, bounded to one selected product, and lease-checked before every DAO operation; price observations remain capped at 200.

Synthetic Room regression coverage checks a matching historical category, a nonmatching category equal to the product's current category, and the persisted price point. The fixture's current category is `Test`, while its historical sold-line category is `Historical category`.

## P1-B — Legacy dashboard filters

`DashboardRequest(asOfDate,timeZoneId,rowLimit)` remains the canonical dashboard contract. The legacy `dashboard(ReportFilter)` overload is source-compatible but accepts only a `SUMMARY` filter with `DAY` grain, a one-day half-open interval, and no unrelated dimensions/status. The one-day start is mapped directly to `asOfDate`; zone and row limit are preserved. Multi-day intervals, WEEK/MONTH grain, and incompatible dimensions fail with `INVALID_INPUT` before repository projections. Both legacy and explicit request paths still execute inside `DASHBOARD:READ` authorization and current session-scoped read handling; effective `PRESENCE:READ` controls presence inclusion.

## Validation

- `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :infrastructure:assembleDebugAndroidTest` — PASS; architecture verification, 78 JVM tests (0 failures/errors/skips), and Android test compilation passed.
- `adb devices -l` plus `adb -s emulator-5554 shell getprop ro.build.version.sdk` — verified `emulator-5554`, API 36.
- `bash ./gradlew --offline :infrastructure:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.infrastructure.persistence.ReportingNativeTest` — PASS; all 6 synthetic Room tests passed.
- `git diff --check` — PASS.

## Limitations

Price-history observations themselves have no category provenance. A category filter therefore constrains which products are eligible through historical validated sale-line snapshots; it cannot establish a category per individual price observation. Products without a matching historical sold line in the requested period yield no filtered price history. Historical purchase amounts remain unavailable where currency provenance is absent, as documented by R1-A.
