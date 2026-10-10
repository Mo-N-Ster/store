# I10 final-unblock qualification update — 2026-10-09

## Decision

**I10 remains BLOCKED; no acceptance commit.** The native parser prototype now has measured resource behavior on the authorized API 36 x86_64 synthetic AVD and a sampled PSS kill guard. The contract requires time/memory/resource budgets and confinement before external import activation; it does not prescribe a numeric hard native-memory ceiling. The observations below do not establish a hard OS memory cap or qualify arbitrary hostile PDF object graphs. External user import remains unexposed. The PDFium service still has no SAF caller, and parser output is not connected to `CatalogImportAuthority` or the transactional catalog import.

## Scope and contract

I10 §§5, 9 and the I10 phase clause require a bounded Subject-only parser, isolated from database/session/network authority, plus demonstrated confinement and time/memory budgets before import is enabled. The clause does not expressly require a hard `RLIMIT` ceiling or reproducible source build. `G-PDF` still requires versioned native documents, the 2.0.1 fixture, malformed/excessive rejection, authorization and validation before a zero-partial-effect transaction. `G-PASS` additionally requires the actual authorized import workflow and all required regressions.

## Resource controls and observed measurements

- Read-only PDF descriptor, maximum input **25,000,000 bytes**; exact-at-limit valid synthetic PDF accepted and one-byte-over rejected.
- One in-flight native parse per isolated service. Concurrent request receives a structured rejection and its descriptor is closed.
- Subject cap **5,333,364 bytes UTF-16LE** (terminator excluded), matching the existing STORE_DATA_V1 codec envelope. A Subject at the cap transfers through a bounded pipe FD rather than a Binder byte array; one byte over is rejected. This avoids Binder's transaction-size ceiling.
- Wall deadline **5,000 ms**. A watchdog terminates only the isolated parser process; the test observed Binder disconnection at about **5.25 s**, then a new bind/request succeeded.
- A 25 ms monitor samples `Debug.getPss()` and native heap allocation in the isolated process. At/above **262,144 KiB PSS** it kills only that process. This is a sampled fail-closed watchdog, **not a hard kernel memory ceiling**; transient peaks between samples and memory mapped outside measured native heap remain residual risks.
- Debug-only log fields contain resource metrics only, no document data. Retained sample: [`ANDROID_PdfSubjectMemoryProfile_R2.txt`](test-results/ANDROID_PdfSubjectMemoryProfile_R2.txt), 24 parser operations. Observed maximum: **61,659 KiB PSS**, **39,651,312 bytes native heap**, **175 ms** per operation. The workload included the valid 25 MB input, maximum Subject metadata, malformed/truncated/encrypted input, repeated requests and timeout. These measurements describe this emulator/run only and are not a performance guarantee.
- An experimental `RLIMIT_DATA=256 MiB` implementation caused parser response timeouts in 4 tests because the process-wide cap destabilized ART/native service operation. It was removed; that failed experiment is not part of the final implementation and is not reported as a passing control.

## Parser and isolation

The one-operation JNI adapter verifies an `O_RDONLY` descriptor, reads only the PDF Subject tag, retains PDFium's input bytes through document close, and serializes PDFium calls. The Android service is non-exported and `isolatedProcess=true`; it rejects work unless the runtime reports isolated execution (API 29+). It has no application database/session authority or network permission. On timeout or client loss no import result is committed. The service protocol returns either bounded Subject metadata over a pipe or a result code.

## Test evidence

Device: `STORE_API36_Main(AVD)`, API 36, x86_64; launched read-only with no snapshot mutation. Final XML: [`ANDROID_PdfSubjectIsolatedServiceTest_R2.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_R2.xml), 13 tests, **0 failures, 0 errors, 0 skipped**, timestamp `2026-10-09T20:03:19Z`.

The 13 cases cover: valid canonical Subject/strict codec/exact decimal and Unicode; synthetic legacy 2.0.1 PDF; missing/empty Subject; invalid Base64/UTF-8/version; malformed/truncated and encrypted PDFs; oversized input and metadata; exact maximum valid input; metadata at the output limit over pipe FD; repeated malformed input; timeout process termination followed by a fresh successful request; and concurrent request rejection.

The preceding diagnostic run with `RLIMIT_DATA` had 4 timeouts; it was superseded after removing that unstable limiter. Another diagnostic fixture with zero padding after EOF was rejected by PDFium; it was replaced by a valid PDF comment/xref fixture at exactly 25,000,000 bytes. Only the final passing XML is retained for this run.

Final-run build checks: `:verifyArchitecture` PASS; `:infrastructure:assembleDebugAndroidTest` PASS; `:app:assembleRelease` PASS with R8 and all four configured ABIs. The 13-test instrumentation suite passed on x86_64 debug only. APK assembly is not release runtime qualification.

## Compatibility and supply chain

- All four artifacts (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) build/package; runtime remains tested only on x86_64/API 36.
- API 23 runtime, ARM64 runtime, and minified release runtime are **NOT TESTED**; no such authorized AVD/device was available. The available AVD inventory contained only `STORE_API36_Main`.
- Official archive hashes and builder identity evidence are recorded in `I10_PARSER_QUALIFICATION.md` and `ADR10_R1_REVIEW.md`. The attestation does not bind the exact PDFium source commit. No reproducible source build was completed. Reproducible build is not a separate explicit I10 criterion, but this provenance limitation remains disclosed.
- Named upstream advisories/fixes are reviewed in the existing qualification record. That review is not a claim that PDFium is free of vulnerabilities.

## Remaining blockers and gates

1. **G-PDF BLOCKED:** no adversarial object-graph/decompression-bomb campaign or native crash injection; sampled PSS guard is not a hard OS memory bound; API 23/ARM64/release runtime is untested; source-to-binary traceability is incomplete.
2. **G-PASS BLOCKED:** no SAF selection → isolated extraction → strict codec → authorized preview → current-right/session revalidation → confirmation → atomic Room import flow. Existing policy and Room batch tests remain separate; no end-to-end PDF import or zero-partial-mutation evidence exists.
3. **G-EVIDENCE PASS_WITH_LIMITATIONS:** final 13/13 XML and 24-operation resource log retained; the unsupported runtime matrix and parser stress limits are explicit.
4. **G-COMMIT NOT READY:** no commit. Do not start I11–I14 until I10 acceptance.

## Repository disposition

Branch `feature/store-i10`, HEAD `65cac9345a1762874b50a427487b69335b7cc40a`; protected `main` remains `3691eccedc9855686b011e34c7420f17aaf6c085`. Index remains empty. Existing I10 changes are preserved. No user database/profile was used or changed, and no push, merge, or deployment occurred.

## Final source-identical rerun

After the initial final-unblock report was drafted, the exact current source passed again: JVM **91/91** across 14 suites (0 failed/errors/skipped); `:verifyArchitecture`, `:app:assembleDebugAndroidTest`, and R8 `:app:assembleRelease` PASS; targeted isolated parser **13/13** PASS. Final retained XML is [`ANDROID_PdfSubjectIsolatedServiceTest_R3.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_R3.xml), timestamp `2026-10-09T20:15:06Z`. Its paired synthetic metrics are [`ANDROID_PdfSubjectMemoryProfile_R3.txt`](test-results/ANDROID_PdfSubjectMemoryProfile_R3.txt), 24 operations, maximum **66,897 KiB PSS**, **39,652,352 bytes native heap**, and **171 ms** observed operation time. These R3 values supersede the earlier R2 sample values above. Source timestamps precede the R3 instrumentation report.

## API 23 compatibility follow-up — 2026-10-09

The minimum-supported-API run exposed three compatibility defects in the prior parser/codec source: `java.util.Base64` is unavailable on API 23, `Map.putIfAbsent` is unavailable there, and `AtomicLong.updateAndGet` caused the isolated service to crash before its timeout watchdog could operate. The codec now uses a strict platform-neutral RFC 4648 Base64 implementation (with canonical re-encoding checks); duplicate-field detection uses `containsKey`; and watchdog maxima use an API-23-compatible compare-and-set loop. The Android instrumentation fixture uses the platform Base64 implementation.

Final follow-up verification:

- Synthetic disposable AVD: `STORE_I10_API23_x86`, Android 6.0 / API 23 / x86.
- `:infrastructure:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.infrastructure.documents.parser.PdfSubjectIsolatedServiceTest`: **13/13**, 0 failures/errors/skips. Retained report: [`ANDROID_PdfSubjectIsolatedServiceTest_API23_x86_R1.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_API23_x86_R1.xml), timestamp `2026-10-09T20:48:43`.
- `:verifyArchitecture :testing:test --rerun-tasks :app:compileDebugKotlin :app:assembleRelease`: PASS. JVM: **92/92** across 14 suites, 0 failures/errors/skips. Retained suite XMLs: [`JVM_API23Compatibility_R1/`](test-results/JVM_API23Compatibility_R1/).
- Release assembly includes R8 and all configured ABI outputs; this is not release-runtime qualification.
- The unsigned minified release APK was signed only for this synthetic test with the repository's local debug/test key, signature verified, installed on the API 23 x86 disposable AVD, and `com.vibe.store/.MainActivity` started successfully (`am start -W`: 471 ms). The activity remained resumed. This verifies app startup under that minified package on API 23, not release PDFium service invocation or other ABIs.

The first two API 23 diagnostic executions failed on the compatibility defects above. Gradle replaced their transient test XML on the final execution; their failures were observed from command output but no XML for those diagnostic attempts is retained. The final API 23 report is preserved separately from the prior API 36 x86_64 R3 report.

The legacy ARMv7 system image was downloaded, but the installed emulator rejected ARM CPU virtualization (`CPU Architecture 'arm' is not supported by the QEMU2 emulator`). The API 36 ARM64 image download was stopped because transfer progress was materially too slow; no ARM64 runtime test was performed. API 23 x86 runtime support and API 23 minified-app startup are demonstrated, but ARM64 runtime and minified PDFium-service runtime remain unverified.

The API 36 x86_64 targeted parser suite was also rerun after the fixes: **13/13**, 0 failures/errors/skips, retained as [`ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R1.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R1.xml), timestamp `2026-10-09T20:55:51`.

I10 remains **BLOCKED**: user-facing SAF import is still unintegrated, and hostile object-graph/decompression, native crash injection, ARM64 runtime and minified PDFium-service runtime qualification remain open. No I10 acceptance commit is ready.

## Integrated import continuation — 2026-10-09

The SAF selection/read adapter, isolated parser client, codec, session-bound import authority, preview/confirmation presentation flow, and Room transaction are now wired in source. The import UI is still disabled by an explicit constant pending mandatory parser qualification; no ordinary user can invoke the unfinished feature. `previewPdf` checks current `PRODUCTS:UPDATE` before and after parsing; `confirm` rechecks current rights/session and all product/duplicate rules inside the existing authorized write transaction. A later preview invalidates any earlier preview token. Cancellation/discard invalidates the preview without business writes.

Final targeted instrumentation after these changes:

- `PdfSubjectIsolatedServiceTest`: API 36 x86_64 **15/15** and disposable API 23 x86 **15/15**, zero failures/errors/skips. Retained as `ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R2.xml` and `ANDROID_PdfSubjectIsolatedServiceTest_API23_x86_R2.xml`.
- `CatalogImportNativeTest`: API 36 x86_64 **8/8** and disposable API 23 x86 **8/8**, zero failures/errors/skips. Retained as `ANDROID_CatalogImportNativeTest_API36_x86_64_R3.xml` and `ANDROID_CatalogImportNativeTest_API23_x86_R3.xml`. Added coverage uses the real synthetic 2.0.1 PDF through isolated PDFium → codec → preview → explicit confirmation → atomic Room import; it checks no mutation before confirmation, exact/legacy values, history/stock, integrity, duplicate replay, and cancellation/discard with zero mutation.
- Final offline `:verifyArchitecture :testing:test --rerun-tasks :app:compileDebugKotlin :infrastructure:compileDebugAndroidTestKotlin :app:assembleRelease`: PASS. JVM reports **92/92** across 14 suites with zero failures/errors/skips. Release build includes R8 and all configured ABI packaging; no minified PDFium runtime test was run.

Still **BLOCKED**: no actual DocumentsUI-to-import UI run, hostile recursive-object/decompression stress campaign, native crash injection, ARM64 runtime, or minified PDFium isolated-service runtime. The PSS monitor is sampled and cannot establish a hard native heap ceiling. The product import action remains disabled. Therefore G-PASS and G-PDF remain BLOCKED, G-EVIDENCE is PASS_WITH_LIMITATIONS, G-COMMIT is NOT READY, and I11–I14 remain contractually gated.

An attempted `:app:connectedReleaseAndroidTest` qualification for the minified service did not reach device execution: AGP/R8 failed while minifying the release AndroidTest APK because `javax.lang.model.element.Modifier` from the optional Error Prone annotation metadata is not present on Android. The production `:app:assembleRelease` task passes; this test-APK build failure is not evidence of an app runtime failure, but minified PDFium service behavior remains **UNVERIFIED**. Temporary local signing/test-build settings were reverted, and no test import button was enabled.

The official Android 36 Google APIs ARM64 system image was installed successfully. A temporary AVD was created under `/tmp/store-i10-avd`, but the emulator refused to boot it on this x86_64 host (`CPU Architecture 'arm64' is not supported by the QEMU2 emulator`). The already installed API 23 ARMv7 image has the equivalent host-architecture limitation. Thus ARM64/ARMv7 runtime remains **NOT TESTED**; all ABI libraries do compile/package.

## Final acceptance closure follow-up — integrated synthetic UI/Room flow

- Added `app/src/androidTest/kotlin/com/vibe/store/CatalogImportEndToEndUiTest.kt`. It composes the real `CatalogScreen` with a synthetic owner identity and a UUID-named temporary Room root. It creates one synthetic PDF in the emulator Downloads provider, launches the system `OpenDocument` picker, reads the selected URI through `MainActivity.readBoundedPdf`, invokes the isolated PDFium extractor and real `CatalogImportAuthority`, then removes the exact synthetic Downloads item and temporary database root in `finally`.
- The test cancels the actual DocumentsUI picker and verifies no catalog mutation. It then selects the fixture, verifies preview and exact decimal presentation with no product or movement writes, confirms the import once, checks the persisted product/price/stock/history/movement, and confirms persistence after Room checkpoint-close-reopen. The UI feature flag is enabled only in this synthetic test composition; normal application composition remains default-disabled.
- Final command: `ANDROID_SERIAL=emulator-5554 bash ./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.CatalogImportEndToEndUiTest` from `store-android/`. **1/1 PASS**, zero failures/errors/skips. Device: `STORE_API36_Main`, API 36, x86_64. Source timestamp `2026-10-10 12:11:24 +0200`; retained report timestamp `2026-10-10 12:11:52 +0200`; report: [`ANDROID_CatalogImportEndToEndUiTest_API36_x86_64_R1.xml`](test-results/ANDROID_CatalogImportEndToEndUiTest_API36_x86_64_R1.xml).
- Existing `CatalogImportNativeTest` separately verifies current-right denial, session replacement, duplicate rejection/replay, invalid input, preview cancellation and transactional rollback. Those negative cases were not all repeated through the picker UI in this E2E run.
- Release-runtime status is unchanged: minified `:app:assembleRelease` passes, while invoking PDFium from the minified release package remains **UNVERIFIED**. The prior `connectedReleaseAndroidTest` run failed during R8 processing of the release test APK (`javax.lang.model.element.Modifier` from optional Error Prone metadata is unavailable on Android), before device execution. No production R8/security rules were weakened or hidden by a suppression.
- Parser metrics remain observations from the API 36 x86_64 synthetic corpus in `ANDROID_PdfSubjectStressProfile_API36_x86_64_R1.txt`: the 25 ms PSS sampling/watchdog is not a kernel-enforced native allocation ceiling. The official binary attestation binds publisher workflow and artifact digest but does not bind the exact PDFium source revision; reproducible source build remains incomplete.
- I10 remains **BLOCKED**. The integrated success/cancel path is now demonstrated, but the outstanding parser provenance/resource and runtime qualification requirements prevent G-PDF/G-PASS acceptance. Production import remains disabled; G-COMMIT is not ready.

Final local validation after adding that test: `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :app:assembleDebugAndroidTest :app:assembleRelease` PASS. Architecture passed; JVM XML totals **92 tests, 0 failures, 0 errors, 0 skipped**; debug AndroidTest APK assembled; minified/R8 release APK assembled with the configured four native ABIs. This was not a release parser runtime test. No product source, Gradle, dependency, manifest, Room schema, or business-write code changed in this closure follow-up.

## Autonomous recovery update — 2026-10-10, parser/UI follow-up

- The API 36 parser suite now contains 19 tests, including a 2,048-level nested Info value and a valid 4,096-indirect-object graph with a 4,096-reference chain. Both assert that a subsequent valid request succeeds. The existing 32 MiB Flate-expanded unused page-stream case remains included. Final XML: [`ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R5.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R5.xml), 19/19, 0 failures/errors/skips, instrumentation timestamp `2026-10-09T22:18:59` UTC (2026-10-10 00:18:59 +0200 device host time). Deep nesting took 0.227 s, compressed stream 0.327 s, and graph case 0.278 s in this run.
- Debug isolated-process log observations from that run: maximum sampled PSS 67,174 KiB, native heap 39,652,880 bytes, and logged request elapsed time 174 ms. Sampling is at 25 ms and is not a hard OS/native allocation ceiling; these numbers describe one API 36 x86_64 synthetic run only.
- Concise retained measurement record: [`ANDROID_PdfSubjectStressProfile_API36_x86_64_R1.txt`](test-results/ANDROID_PdfSubjectStressProfile_API36_x86_64_R1.txt).
- `CatalogScreen` gained a test seam `catalogPdfImportEnabled`, defaulting to the existing constant `false`. The app composition does not set it; ordinary users still cannot see the import action. A new synthetic presentation test confirms default-off and missing `PRODUCTS:UPDATE` hiding, bounded selection callback → preview display with significant decimal precision, cancel/discard without confirmation, and a permission failure at confirmation never shows success. Final report: [`ANDROID_CatalogImportWorkflowUiTest_API36_x86_64_R2.xml`](test-results/ANDROID_CatalogImportWorkflowUiTest_API36_x86_64_R2.xml), 3/3, 0 failures/errors/skips, timestamp `2026-10-09T22:24:23` UTC. A diagnostic run initially asserted insignificant trailing zeros (`12.3400`) despite `DecimalInput.display` intentionally rendering the numerically identical `12.34`; the final test uses `12.34001` to verify significant precision. Diagnostic XML is retained separately.
- The UI test uses a synthetic `CatalogImportService` fake; it validates presentation behavior, not production repository integration. Real DocumentsUI → bounded reader → isolated parser → codec remains separately tested (1/1, see preceding addendum); actual parser → authority → confirmation → Room and rollback remain in `CatalogImportNativeTest`. No single production-UI-to-Room picker test is claimed. Attempting to pull `RoomDatabaseOwner` and domain internals into the app test module was rejected by Kotlin visibility/module boundaries; adding app→infrastructure/domain dependencies would violate the seven-module architecture and was reverted.
- `:app:assembleRelease` PASS after the presentation API change; R8 remains enabled and no suppression rule was added. This proves minified release packaging only, not execution of PDFium service in a minified APK. The earlier release AndroidTest APK R8 harness failure remains unresolved. The authorized `emulator-5554` was API 36 x86_64; the other connected emulator was not used because it is not in the authorized synthetic-device set.
- Contract disposition: I11–I14 remain unavailable for acceptance because §6 requires each phase to follow a clean accepted preceding commit. No downstream phase implementation or acceptance was started. ARM64 runtime, minified parser-service runtime, production UI→Room unified test, broad hostile-input/resource campaign and hard native memory ceiling remain unresolved; therefore G-PASS/G-PDF remain **BLOCKED**, G-EVIDENCE remains **PASS_WITH_LIMITATIONS**, G-COMMIT remains **NOT READY**, and user import stays disabled.

The isolated parser suite now also exercises a synthetic 256-level nested Info value and a cyclic PDF page-tree reference, then verifies a subsequent valid parse succeeds. Final rerun on API36 x86_64 and API23 x86 is **16/16 PASS** each, 0 failures/errors/skips, retained as `ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R3.xml` and `ANDROID_PdfSubjectIsolatedServiceTest_API23_x86_R3.xml`. This tests bounded malformed structure handling and service recovery; it is not a decompression-bomb campaign or a general PDF fuzzing result.

The final inventory at this checkpoint contains 131 dirty paths, all beneath `store-android`; index empty; `git diff --check` passes; branch/HEAD/main remain unchanged. The four PDFium ABI shared objects and renderer/test evidence are expected I10 artifacts, with no APK/build cache staged or tracked.

## Continuous recovery addendum — 2026-10-10

This update supersedes earlier statements above that the SAF/parser/authority source path did not exist. That path is wired in source, including current-session preview, explicit confirmation, authority revalidation, duplicate checks, and the existing single Room transaction. The product UI action remains disabled; acceptance is not claimed.

- Added a real system DocumentsUI test on `STORE_API36_Main` (API 36 x86_64). It creates a synthetic canonical PDF through `ACTION_CREATE_DOCUMENT`, writes it through the returned provider URI, selects it through `ACTION_OPEN_DOCUMENT`, reads it using `MainActivity.readBoundedPdf`, extracts the Subject through isolated PDFium, and checks the exact canonical STORE_DATA_V1 payload including decimal text. Final result: **1/1**, 0 failures/errors/skips; XML: `test-results/ANDROID_CatalogImportDocumentsUiTest_API36_x86_64_R1.xml`. It does not touch the app database or exercise production import confirmation.
- Added a valid PDF with a 32 MiB Flate-expanded page stream and bounded Subject. API 36 x86_64 parser suite: **17/17**, 0 failures/errors/skips; new case completed in 0.302 seconds. XML: `test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R4.xml`. This is one metadata-only scenario, not broad decompression-bomb/fuzz qualification or proof of a hard memory cap.
- The existing timeout test terminates only the isolated process for a stalled request and verifies that a fresh isolated process handles the next request. An uncontrolled native crash/SIGSEGV injection remains untested.
- Two earlier DocumentsUI test runs failed in automation before the final selector/provider interaction was fixed; only the passing XML is retained as final evidence. Four exact synthetic PDF leftovers were listed and deleted from the authorized emulator; no app profile was cleared.
- I10 remains **BLOCKED**: production import UI is disabled; provider selection/parser and Room authorization/atomic-import tests are separate, not a single production UI-to-confirmation test. Broad hostile-stream/resource qualification, crash injection, ARM64 runtime, and minified PDFium-service runtime remain open. The known ARM emulator limitation was not retried. No I10 commit or I11–I14 work was started.
