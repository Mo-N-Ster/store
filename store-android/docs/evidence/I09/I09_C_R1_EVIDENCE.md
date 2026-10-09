# I09-C-R1 — Dashboard date and timezone refresh

Status: scoped implementation and validation complete. This evidence does not claim global I09 acceptance.

## Refresh and lifecycle behavior

- `DashboardTimeContext.nextLocalMidnight()` computes the next local day boundary with `LocalDate.atStartOfDay(ZoneId)`, so the wait follows the actual 23-, 24-, or 25-hour calendar day.
- `AndroidDashboardTimeSource` waits for that deadline and listens for Android date, wall-clock, and timezone change broadcasts. Every wakeup rereads the current system zone and local date, then schedules the next local midnight from the updated context.
- The dashboard rechecks on `ON_RESUME`. Its Compose effects are cancelled when the screen stops, loses permission, changes identity, or is disposed; the receiver is unregistered and the timer coroutine cancelled in `finally`.
- A changed date or zone clears the visible snapshot, changes the canonical `DashboardRequest`, and cancels the old request effect. Content is rendered only for the current request. Cancellation exceptions continue to propagate.
- The dashboard request is remembered by date/timezone context to avoid recreating the identity-based request object on ordinary recompositions.
- Instrumentation injects a deterministic `DashboardTimeSource`; tests do not change device time/timezone or wait for midnight in real time.

## Validation

- `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :app:assembleDebugAndroidTest` — PASS; architecture check, 78 JVM tests, and Android test compilation.
- Verified `emulator-5554` is `STORE_API36_Main`, API 36. No user profile or database reset was performed.
- `DashboardUiTest` — PASS, 12 tests, 0 failures/errors/skips. Retained XML: `I09_C_R1_DashboardUiTest.xml`. Coverage includes same-day no-refresh, local midnight refresh and privacy clearing, timezone changes with and without date change, 23/25-hour DST deadlines, stale response discard, disposal cancellation, and existing dashboard permission/presentation/navigation-adjacent checks.
- `StoreNavigationUiTest` — PASS, 6 tests, 0 failures/errors/skips. Retained XML: `I09_C_R1_StoreNavigationUiTest.xml`.
- An earlier dashboard run failed one assertion because the test expected `10.10` while the established exact-money display canonicalizes trailing zeroes to `10.1`; the expectation was corrected and the complete final dashboard suite passed. The earlier XML is retained as `I09_C_R1_DashboardUiTest_failed_attempt.xml`.
- `git diff --check` — PASS.

## Limitations

- Device clock/timezone changes are detected through Android broadcasts plus the next-midnight deadline. If an OEM fails to deliver those system broadcasts, the next scheduled local midnight and the next `ON_RESUME` still revalidate context.
- This work only refreshes dashboard calendar context; it does not alter reporting calculations, projections, or global I09 acceptance status.
