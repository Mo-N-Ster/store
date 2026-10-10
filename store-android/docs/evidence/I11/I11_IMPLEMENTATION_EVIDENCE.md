# I11 implementation and qualification evidence

**Status:** implementation and synthetic qualification complete; local acceptance commit pending final manifest review.
**Branch/base:** `feature/store-i10`, I10 commit `07c9be3a6080e32ee9f7c3bb25cb8d6a6cf64fb9`.
**Device:** authorized `emulator-5554`, `STORE_API36_Main`, API 36, x86_64.
**Validation date:** 2026-10-10.

## Contract mapping

I11 implements the contract's PrintManager/PrintDocumentAdapter, read-only FileProvider chooser, Angus SMTP configuration and delivery, durable queue, unique WorkManager scheduling, queue UI and authorized receipt/report/help actions. Print/share/send failures are independent of local sales; none retries or mutates a sale. Sharing does not change queue status. SMTP acceptance is recorded only after the transport returns successfully. The UI and authority report server acceptance, not recipient delivery; exactly-once delivery is not claimed.

The Room queue is schema v3. Migration v2→v3 adds actor/generation ownership and the due-work index; rows whose old sender cannot be established are failed closed (`legacy_sender_unknown`) rather than sent under a new identity. The migration handles missing columns idempotently and was tested against both the frozen v2 shape and the current synthetic migration fixture.

SMTP secrets are sealed by the existing Android Keystore adapter and never returned by configuration reads. The transport requires implicit TLS or STARTTLS before authentication, verifies server identity, uses 10-second connect/read/write timeouts, disables protocol debug, and emits only bounded error codes. Missing or unavailable secrets do not consume retry attempts. The SMTP adapter's synthetic trust pin is available only through its internal test factory; application composition uses the default platform trust configuration.

The Worker revalidates the current identity and `FINANCES:CREATE` through `EmailAuthority`, binds queue rows to actor and generation, recovers interrupted `sending` rows to `pending`, requires network connectivity, and uses one unique appended chain. The fixed retry policy allows five transport failures with bounded exponential delay; unconfigured SMTP is delayed without incrementing that count. No dedicated background service is kept alive.

## Final test results

All reports below are retained under [`test-results/`](test-results/). Counts come from the XML, except the two direct restart phases, whose exact instrumentation output is retained as text.

| Suite | Result |
|---|---:|
| Full JVM and infrastructure unit regressions | 108/108 passed; 0 failures, errors or skips. Command: `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :infrastructure:testDebugUnitTest --rerun-tasks` |
| `EmailAuthorityTest` | 8/8 |
| `EmailDeliveryPolicyTest` | 3/3 |
| `AngusSmtpTransportTest` | 4/4: authenticated loopback TLS delivery with PDF attachment; untrusted certificate rejected before delivery; STARTTLS downgrade rejected before AUTH; stalled greeting classified `SMTP_TIMEOUT` |
| `PersistenceTest` | 12/12 on API 36, including v2→v3 migration and rollback/persistence regressions |
| `EmailDeliveryPersistenceTest` | 1/1: a synthetic writer process is killed after durable queue insert; reopened Room state is verified |
| `DocumentExportUiTest` | 7/7: system DocumentsUI cancellation/write error, distinct print/share actions, Android PrintManager job cancellation |
| `SafProviderFailureTest` | 4/4: storage failure, revoked read, constrained FileProvider, and shell-UID read without grant returns no document bytes |
| `EmailDeliveryUiTest` | 1/1: failed status and explicit retry action |
| `EmailDeliverySchedulerUiTest` | 1/1: repeated enqueue remains one non-parallel unique chain |
| `EmailDeliveryConnectivityUiTest` | 1/1: constrained work waits offline, runs after network returns; original Wi-Fi/airplane state restored |
| WorkManager app-process restart | 2/2 direct instrumentation phases passed; exact output in `EmailDeliveryRestartPrepare.txt` and `EmailDeliveryRestartVerify.txt` |
| Architecture | `:verifyArchitecture` passed in each Gradle validation command |
| Minified Release assembly | `:app:assembleRelease` passed after final production transport change; APK is unsigned and remains a build artifact, not committed |

The restart phases were executed with the already installed synthetic app/test APKs using `adb shell am instrument`: the first stored a two-hour delayed unique request; only `com.vibe.store.debug` was force-stopped; the second verified the same unique work entry after application process restart and canceled it in `finally`. The Gradle-connected two-invocation attempt was retained separately in [`diagnostics/EmailDeliveryRestartUiTest_failed_gradle_reinstall.xml`](diagnostics/EmailDeliveryRestartUiTest_failed_gradle_reinstall.xml); the connected-test harness removed the target package between invocations. This harness result is not represented as a product failure or as passing restart evidence.

## Security and side-effect coverage

- Effective rights are rechecked at queue creation, retry and delivery; explicit finance denials and account/generation changes reject work before SMTP.
- Queue queries are actor-scoped; legacy rows with unknown actor identity cannot be sent.
- The SMTP test server accepts only after TLS and authentication. Invalid certificates, absent STARTTLS, and a stalled greeting fail before message delivery. Synthetic recipients use `.invalid`; no real recipient or external SMTP service was used.
- Attachment bytes are private to the authorized document/queue adapters. FileProvider is non-exported, grants read-only URI access, and exposes only the shared-document cache directory. A distinct shell UID received no shared-document bytes without a URI grant.
- Print cancellation, export cancellation, storage-full failure and URI/provider denial leave business operations untouched. Queue state and reports do not invoke sale write services.
- The isolated process-death persistence test and WorkManager restart test use synthetic queue entries only. The scheduled two-hour request was canceled after verification.

## Limitations and disposition

- The real Android print service and system DocumentsUI were exercised on the API 36 emulator. A physical printer and physical-device behavior remain for final device qualification I14, as the I11 contract specifies.
- No live third-party SMTP provider was contacted. The controlled loopback server covers authenticated TLS delivery, attachment encoding, certificate failure, downgrade and timeout; provider-specific behavior remains outside this synthetic qualification.
- SMTP may deliver a message and the app may stop before persisting `sent`; the contract explicitly does not guarantee exactly-once delivery. UI wording says “accepted by server.”
- The unsigned minified Release variant assembled successfully; this evidence is not a release-signing or physical-device acceptance claim.

**Defects fixed during I11:** the Room v2→v3 migration now tolerates the frozen v2 table shape and fails legacy unowned sends closed; nested Jakarta Mail timeout causes are now classified as `SMTP_TIMEOUT` rather than generic network failure. Both corrections have focused regression evidence above.

**Defect inventory:** no known I11 P0/P1 remains. Physical-printer and provider-specific checks are owned by I14; duplicate SMTP delivery remains the documented P2 limitation.
