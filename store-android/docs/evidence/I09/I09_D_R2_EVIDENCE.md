# I09-D-R2 — Final visual evidence closure

Date: 2026-10-09. This evidence covers the minimal R2 presentation correction and final synthetic FINANCES captures; it is not a claim of I09 or I09-D acceptance.

## Changes

- `ReportingScreen` renders currency metadata only when the report actually presents monetary content. STOCK and TEAM no longer display a configured currency for nonmonetary rows/series; monetary metrics and monetary values remain visible with their currency.
- `SpatialTheme` now synchronizes status- and navigation-bar icon appearance with its Material3 light/dark scheme. `ReportingScreenshotTest` wraps the screen in the same Material3 `Surface` used by the integrated app and asserts status-bar appearance.
- `ReportingUiTest.currencyLabelRequiresRenderedMonetaryContentForStockAndTeam` covers suppression for nonmonetary STOCK/TEAM and retention for actual monetary content.

## Final screenshots

Captured from the built integrated app on the synthetic `STORE_API36_Main` emulator (API 36), at its original 1080×2400 / 420 dpi profile. The screenshot test uses synthetic report values and a synthetic identity. Source files were last modified before the relevant test runs: `SpatialTheme.kt` at 11:49:18Z, `ReportingScreenshotTest.kt` at 11:51:58Z, and `ReportingUiTest.kt` at 11:37:37Z. Final retained instrumentation XML timestamps are 11:54:38Z, 11:55:19Z and 11:55:48Z.

| File | Size | Review |
|---|---:|---|
| `screenshots/compact_en_light_finances.png` | 183,019 bytes | Integrated light Material3 surface; report filters, historical-purchase currency-unavailable disclosure, and validated revenue KPI are visible. |
| `screenshots/compact_en_light_finances_metrics.png` | 165,341 bytes | Scrolled KPI section shows revenue (`EUR 1234.56789`), 18 validated transactions, average basket (`EUR 68.587105`), 2 cancelled sales, estimated gross margin (`EUR 45.005`) and its non-certified-profit disclosure. |
| `screenshots/compact_fr_dark_finances.png` | 183,945 bytes | Integrated dark Material3 surface; dark-theme status/navigation icons have light contrast; French unavailable-currency disclosure and revenue KPI are readable. |
| `screenshots/compact_fr_dark_finances_metrics.png` | 163,738 bytes | French KPI values use comma decimal separators; cancellations and estimated margin are labeled; margin disclosure is readable. |

All four are valid 1080×2400 RGB PNG files. The primary and KPI captures are separate scroll positions, so the KPI capture starts partway down the report by design. Neither contains user data. Visual review found no clipping of displayed report text or controls; the primary captures show the top of the first KPI card partially at the viewport bottom, while the dedicated KPI captures show the complete KPI section. This does not constitute a screen-reader audit.

## Validation

- `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :app:assembleDebugAndroidTest` — PASS; architecture passed, 78 JVM tests passed, Android test APK compiled/assembled. One initial invocation before adding the missing JUnit `assertEquals` import failed Android-test compilation; the corrected final invocation passed all gates.
- Focused API 36 instrumentation: `ReportingUiTest` 14/14, `DashboardUiTest` 12/12, `StoreNavigationUiTest` 6/6; zero failures, errors or skips. Retained reports: `test-results/android/ReportingUiTest.xml`, `DashboardUiTest.xml`, `StoreNavigationUiTest.xml`.
- Screenshot instrumentation: `ReportingScreenshotTest` passed once for English/light and once for French/dark. Both generated the primary and KPI captures above.
- Emulator display profile rechecked after test/capture: 1080×2400, 420 dpi; unchanged.
- `git diff --check` — PASS.

## Limitations

The captures demonstrate only compact FINANCES in English/light and French/dark. They do not claim a full language/theme/width matrix. They use synthetic data; actual user data and production identities were not accessed. Accessibility semantics are covered by existing UI tests but visual screenshots alone cannot establish screen-reader quality. No backend, business calculations, database schema or dependencies were changed for R2.

Next: I09-D final differential acceptance.
