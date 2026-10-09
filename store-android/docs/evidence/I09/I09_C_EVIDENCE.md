# I09-C — Native dashboard integration evidence

Status: I09-C implementation and scoped validation complete; this is not global I09 acceptance and does not authorize I09-D.

## Service wiring

- `StoreApplication.reporting` exposes one lazy `ReportingAuthority` built from the application-lifetime `IdentityAuthority`.
- `MainActivity` passes that service into `SecurityApp`; Compose and Activity do not access DAOs or create another identity/session owner.
- Production `ReportingAuthority` resolves `ReadRepositories.reporting` inside `IdentityAuthority.authorizedRead`, preserving the current identity gate and Room read lease/transaction. Tests can still inject an in-memory `ReportingRepository`.

## Dashboard behavior

- `DashboardScreen` requests one canonical `DashboardRequest` with an explicit local `asOfDate`, system IANA timezone and bounded default row limit. It renders the returned snapshot and does not recalculate KPIs.
- It displays validated sales revenue/count, average basket, current stock indicators, month-to-date revenue, the 30-day trend, and presence rows only when returned and locally permitted.
- Currency code is explicit. Monetary values and numeric trend labels use `DecimalInput.display`, preserving stored decimal precision and using French comma separators. The chart has an accessible description and an equivalent dated numeric series.
- Disclosures state that revenue includes validated sales only, cancellations are excluded, and no accounting profit is calculated; any margin shown elsewhere is estimated.
- SpatialTheme/Material3 colors are used. The metric grid adapts at compact (<600dp), medium (<840dp), and expanded widths. Labels are available in French and English; UI tests render both light and dark themes.

## Security and lifecycle

- UI omits dashboard requests without `DASHBOARD:READ`; the backend independently reauthorizes every request. Backend denial renders a denied state without displaying cached content.
- Presence rows are omitted without `PRESENCE:READ`; this UI check only hides data. Backend effective rights remain authoritative.
- Dashboard state is keyed by service, account id, and permissions, is never saveable, and is disposed on account-route changes. On `ON_STOP`, private identity and dashboard state are hidden/cleared. `ON_RESUME` refreshes preferences and current identity even if another UI action had marked the screen busy.
- Request effects are cancelled when account/request/lifecycle changes; cancellation propagates, and a post-response coroutine activity check prevents a late response from replacing the current account's content. Error and retry, loading, denied, and empty states are distinct.

## Validation

- `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :app:assembleDebugAndroidTest` — PASS; seven-module architecture check, 78 JVM tests (0 failures/errors/skips), and app Android test compilation.
- `emulator-5554` verified connected with Android API 36 before instrumentation runs.
- `bash ./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.DashboardUiTest` — PASS; 6 tests, 0 failures/errors/skips. Covers role variants, unauthorized UI/backend denial, independently gated presence, empty and error/retry states, account switch with delayed stale response, logout disposal, localization, theme, and three width classes.
- `bash ./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.StoreNavigationUiTest` — PASS; 6 tests, 0 failures/errors/skips, checking existing I01–I08 navigation behavior.
- `git diff --check` — PASS.

## Limitations

- The dashboard uses the device's current local timezone and date as its canonical request context; it does not expose a timezone/date picker in this phase.
- The trend’s numeric series is authoritative; the canvas is a visual summary. No PDF or seven-report UI is included.
- AndroidX adaptive window class types already used by the existing shell emit deprecation warnings; no dependency/toolchain changes were made.
