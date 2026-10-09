# I09-D-R1 Reporting UI Completeness and Visual Evidence

This records the controlled I09-D-R1 presentation changes and their verification. It is not a claim of global I09 acceptance.

## Corrections

- Reset now restores the current tab's valid seven-day default (`DAY`, six days before the current local date through today, no dimensions), clears the previous document by entering `Loading`, advances the request generation, and reloads that filter. Existing request-key/generation checks discard stale responses.
- Report KPI cards render available `DocumentModel.metrics` for summary, sales, products, and finances. Revenue, validated transaction count, average basket, cancellations, and estimated margin retain the snapshot's metric currency and exact `BigDecimal` display. A known sales metric remains renderable when a finance document marks purchase currency unavailable; unavailable purchase values remain an explicit unavailable disclosure and are never substituted with zero. No financial metric is recomputed in Compose.
- Detailed rows are behind a semantic expand/collapse button with an explicit state description. At usable medium/expanded widths, headers and values use aligned weighted columns; compact layouts use label/value rows to avoid clipping. The document's already bounded row set is displayed, while charts' numeric alternative is lazy and capped at 200 values. Expansion does not imply backend pagination.
- Report sections follow title/period, filters, KPI summary, relevant chart, detail table, and methodology/disclosures. Series include readable labels and monetary units where the source metric has currency provenance. One-point series are represented as KPI-style values rather than an empty chart. System insets are respected.

## Visual coverage and retained screenshots

All images below were captured from rendered UI on the synthetic `STORE_API36_Main` emulator (API 36), using the fake `ReportingService` fixture in `ReportingScreenshotTest`; no real account or user data was used. The capture matrix covers all seven tabs in compact English/light, then adds expanded-table, French/dark, French/light medium, and expanded-width variants.

| Coverage | Screenshot |
|---|---|
| Compact, EN/light — SUMMARY | [compact_en_light_summary.png](screenshots/compact_en_light_summary.png) |
| Compact, EN/light — SALES | [compact_en_light_sales.png](screenshots/compact_en_light_sales.png) |
| Compact, EN/light — PRODUCTS | [compact_en_light_products.png](screenshots/compact_en_light_products.png) |
| Compact, EN/light — STOCK | [compact_en_light_stock.png](screenshots/compact_en_light_stock.png) |
| Compact, EN/light — PURCHASES | [compact_en_light_purchases.png](screenshots/compact_en_light_purchases.png) |
| Compact, EN/light — TEAM | [compact_en_light_team.png](screenshots/compact_en_light_team.png) |
| Compact, EN/light — FINANCES | [compact_en_light_finances.png](screenshots/compact_en_light_finances.png) |
| Compact, EN/light — expanded PURCHASES table | [compact_en_light_purchases_table.png](screenshots/compact_en_light_purchases_table.png) |
| Compact, FR/dark — FINANCES | [compact_fr_dark_finances.png](screenshots/compact_fr_dark_finances.png) |
| Medium, FR/light — PURCHASES and expanded table | [medium_fr_light_purchases.png](screenshots/medium_fr_light_purchases.png), [medium_fr_light_purchases_table.png](screenshots/medium_fr_light_purchases_table.png) |
| Expanded, EN/light — PRODUCTS | [expanded_en_light_products.png](screenshots/expanded_en_light_products.png) |

The reviewed captures showed a clear vertical hierarchy on compact screens, readable unavailable-currency disclosure, no title/status-bar overlap, and separated table values at medium width after the column-weight adjustment. The table test also asserts headers and value cells at medium width. Automated screenshot review does not substitute for a TalkBack audit.

## Security and lifecycle regression

No authority, reporting projection, database, or session code was changed by this R1. The screen continues to consume one immutable `DocumentModel`; its applied filter and generation key the rendered result. Existing denied-access, purchase/presence disclosure, STOP hiding, RESUME refresh, account-switch disposal, stale-response rejection, and cancellation behavior are covered by retained UI tests and were not weakened by the presentation changes. Screenshot fixtures are deliberately synthetic.

## Validation

Final required offline command, after the final source/test edits:

```text
bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :app:assembleDebugAndroidTest
BUILD SUCCESSFUL
```

`verifyArchitecture` passed. The JVM XML set has 12 suites, 78 tests, 0 failures/errors. Android test compilation and assembly passed.

Focused instrumentation was run on `emulator-5554`, verified as `STORE_API36_Main`, API 36. Final retained XML:

- [ReportingUiTest.xml](test-results/android/ReportingUiTest.xml): 13 tests, 0 failures/errors/skips.
- [DashboardUiTest.xml](test-results/android/DashboardUiTest.xml): 12 tests, 0 failures/errors/skips.
- [StoreNavigationUiTest.xml](test-results/android/StoreNavigationUiTest.xml): 6 tests, 0 failures/errors/skips.
- JVM XML files: [test-results/jvm/](test-results/jvm/).

The test source includes Reset reload/clearing behavior, KPI labels and values, unavailable currency, expandable semantics, responsive table layout, 200-row bounded rendering, chart units/numeric alternative, authorization and lifecycle regressions. An earlier first run exposed an incorrect test expectation for the English decimal separator; the failed report is retained as `test-results/android/ReportingUiTest_R1_failed-decimal-locale.xml`. The assertion was corrected to the actual exact English display (`EUR 12.3456789`) and the final suite passed.

`git diff --check` passed. The emulator's original display settings were verified after capture: physical size 1080x2400, density 420.

## Limitations

- The synthetic screenshot service cannot demonstrate real backend/session integration; authorization and lifecycle regression are exercised separately in UI tests.
- Product names are absent from the current report contract for price history; the UI therefore uses a readable “Price history · product #ID” label rather than inventing a product name.
- Manual screen-reader/TalkBack verification was not performed. Semantic labels and table alternatives have automated coverage only.
- Compact screens naturally require vertical scrolling through tabs, filters and report sections; no horizontal clipping was observed in the retained captures.

## Worktree inventory

The final root worktree inventory contains 69 dirty paths, all under `store-android`; this includes the pre-existing I09 work, retained historical/failed test evidence, 12 screenshots, and this R1 evidence file. The index remains untouched. R1's code/test changes are limited to `presentation/ReportingScreen.kt` and `app/src/androidTest/kotlin/com/vibe/store/ReportingUiTest.kt`; screenshot and report artifacts are under this evidence directory. No changes were made to reporting calculations, Room projections/schema, dependencies, or business operations.
