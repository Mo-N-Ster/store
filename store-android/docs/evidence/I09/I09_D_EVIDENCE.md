# I09-D — Native reporting UI evidence

## Implemented

- `ReportingScreen` consumes `ReportingService.snapshot(ReportFilter)` and renders the immutable `DocumentModel` for SUMMARY, SALES, PRODUCTS, STOCK, PURCHASES, TEAM, and FINANCES. It renders supplied rows, series, labels, currency availability, and disclosures; it does not calculate report metrics.
- Reports is reachable from StoreHome and native navigation when the reporting service is composed and the identity has `FINANCES:READ`. The reporting authority remains responsible for authorization, including purchase and presence restrictions.
- The filter panel supports inclusive start/end dates, DAY/WEEK/MONTH on supported tabs, product/category/supplier/employee/status filters where the service accepts them, 7/30-day presets, Apply, and Reset. Invalid and unsupported combinations disable Apply and show a localized explanation. The draft and applied filters are shown separately, with all applied dimensions visible.
- The result view preserves the service snapshot's row and series bounds, uses `DecimalInput.display` for exact monetary values, labels estimated margin, marks unavailable historical purchase currency as unavailable, and explains historical category and recorded price-history semantics. Series charts have matching localized numeric labels and an accessibility content description.
- Lifecycle STOP hides report data; START/RESUME reloads the applied filter. Service requests are cancelled when keys change, cancellation exceptions propagate, and account-keyed Compose state plus request generation checks prevent stale account/filter responses from becoming visible. Report snapshots are not saveable state.
- No PDF, renderer, email, export, report-calculation, projection, or schema work was added.

## Validation

All required local gates passed after the final source change:

- `:verifyArchitecture` — PASS.
- `:testing:test --rerun-tasks` — 78 tests, 0 failures/errors/skips.
- `:app:assembleDebugAndroidTest` — PASS.
- `git diff --check` — PASS.

The authorized `emulator-5554` was verified as API 36 (`STORE_API36_Main`). Final targeted instrumentation results:

- `ReportingUiTest.xml` — 10/10 passed; covers all seven tabs, filters and applied/draft distinction, filter rejection, report authorization and individual denials, stale filter/account responses, STOP/RESUME, empty/error/retry, exact money, unavailable currency, chart numeric/accessibility equivalent, FR/EN, and compact/medium/expanded widths.
- `StoreNavigationUiTest.xml` — 6/6 passed.
- `DashboardUiTest.xml` — 12/12 passed.
- `WorkflowNavigationUiTest.xml` — 6/6 passed when supplied that existing suite's required `workflowWidth=400` and `workflowSize=800x1600` runner arguments. Its temporary display overrides were reset; verified afterward at physical 1080x2400, density 420.

Each suite XML is retained under `test-results/android/`; the 12 JVM XML suites are under `test-results/jvm/`. Earlier targeted runs exposed test-harness assertion issues; their failed XML is retained with `_failed`, `_attempt`, or `_missing_args` names. The final named suite reports above are the passing runs.

## Limitations

- Purchase historical amounts remain unavailable because the accepted backend has no currency provenance for them; the UI does not substitute zero or the current currency.
- Report rows are capped at the service's 200-row display limit; the UI states that aggregates cover the complete filtered result. This UI does not add pagination or export.
- No production/user database was touched. No global I09 acceptance claim is made by this evidence.
