# I10 PDFium isolated Subject integration — current disposition PASS_WITH_LIMITATIONS

Current status as of 2026-10-10: the production import workflow and minified
Release service runtime are now exercised; the earlier prototype/blocked
decisions below are historical and superseded by `I10_FINAL_ACCEPTANCE.md`.
Current measured resource profiles are bounded observations, not a hard
native-memory guarantee.

## Decision

**Experimental integration complete; parser production gate remains BLOCKED.** The selected PDFium artifacts now have a narrow JNI adapter and an Android `isolatedProcess` service. The service is not connected to the SAF picker, catalog codec/import authority, or persistence workflow. Import remains unavailable to users. This is not I10 acceptance and does not authorize a commit.

The contractual gates require hostile-input confinement and time/memory/resource limits before external import activation. The current prototype enforces bounded input/output, one active request, and a process-killing deadline, and empirically verifies UID isolation on the API 36 emulator. It does **not** establish a hard ceiling for PDFium's internal native allocations or measure peak native memory under adversarial documents. That remains a release-blocking qualification gap.

## Implementation and boundaries

- `infrastructure/src/main/cpp/pdf_subject_jni.cpp` exports one JNI operation. It accepts a read-only descriptor, reads at most 25 MiB, initializes PDFium, loads the in-memory document, reads only the `Subject` metadata tag, and returns the bounded UTF-16LE Subject bytes. The input buffer remains alive until `FPDF_CloseDocument`, as required by the public PDFium API. PDFium calls are serialized. The adapter does not render pages, extract page text, inspect embedded files, read paths, or access database/session/business APIs.
- `infrastructure/src/main/kotlin/com/vibe/store/infrastructure/documents/parser/PdfSubjectIsolatedService.kt` uses a private Messenger/Binder protocol. The merged manifest declares `exported=false` and `isolatedProcess=true`. The request carries only a `ParcelFileDescriptor`; the result contains bounded Subject bytes or a failure code. The service rejects concurrent work, applies a 5,000 ms wall deadline, and terminates its own isolated process if native parsing/read does not return. Binder death is the timeout result; later binding starts a fresh parser process.
- The parser's technical caps match the existing STORE_DATA_V1 format envelope: 25 MiB PDF input; at most 5,333,366 bytes returned by PDFium including its UTF-16LE terminator (5,333,364 returned bytes without it), corresponding to the 14-character prefix plus the existing 2,666,668-character encoded-payload bound. These are codec/resource bounds, not catalog business limits.
- The isolated service does not invoke `StoreApplication` service getters. `StoreApplication` has no `onCreate` work and its persistence/authorities are lazy. No INTERNET permission is declared. The service has no code path to the Room owner, identity authority, or business writes.
- The four official PDFium Android `.so` files and their bundled notices are included under infrastructure assets/native libs. NDK `27.2.12479018` (r27c/Clang 18.0.3) and CMake `3.22.1` built the JNI shim and APK variants for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`. Only x86_64 was run on a device/emulator. The official archive digests and Sigstore verification are recorded in `I10_PARSER_QUALIFICATION.md` and `ADR10_R1_REVIEW.md`; the attestation still does not bind the resolved PDFium source commit. No reproducible source build was completed. The contract requires provenance/advisory/notice review but does not explicitly make a reproducible build a separate I10 gate; source-to-binary traceability remains a documented residual risk, not a claim of source reproducibility.

## Test evidence

Device: `STORE_API36_Main(AVD)`, API 36, x86_64. The emulator was launched `-read-only -no-snapshot`; no profile wipe, user database, or business data was used.

Final instrumentation report: [`ANDROID_PdfSubjectIsolatedServiceTest.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest.xml), XML timestamp `2026-10-09T19:38:57Z`, 11 tests, 0 failures, 0 errors, 0 skipped. Source timestamps precede the retained report. The cases establish:

1. Valid V1 Subject passes through the isolated service and strict codec, preserving `Café` and exact `12.3456789`.
2. The repository's synthetic legacy 2.0.1 PDF yields the exact established Subject and decodes to three products.
3. Missing and empty Subject are rejected.
4. Invalid Base64, invalid UTF-8 and unsupported payload version are rejected by the strict codec after metadata extraction.
5. Corrupt and truncated PDFs are rejected.
6. A password-protected synthetic PDF is rejected without credentials.
7. A PDF larger than the 25 MiB input cap is rejected.
8. Subject metadata above the canonical codec bound is rejected.
9. Repeated malformed inputs do not reuse an earlier success.
10. A stalled input terminates the isolated service process at the five-second deadline; following tests successfully bind/use a restarted isolated process.
11. Concurrent requests admit at most one parser operation; the second descriptor is rejected and closed.

Earlier failed diagnostics are retained separately and are not final evidence:

- `DIAGNOSTIC_PdfSubjectIsolatedServiceTest_concurrency-request-id.xml`: initial test used a recycled/wrong request identifier; test harness corrected.
- `DIAGNOSTIC_PdfSubjectIsolatedServiceTest_concurrent-message-recycled.xml`: caught the actual asynchronous Binder `Message` recycling issue; service now copies the request ID before dispatch, and the final 11/11 suite passes.
- `DIAGNOSTIC_PdfSubjectIsolatedServiceTest_pre-buffer-lifetime-fix.xml`: superseded smoke-suite result; code review found and fixed early release of the PDFium-retained input buffer.

Other final validation after the last source changes: `:verifyArchitecture` PASS; `:testing:test --rerun-tasks` PASS, 91/91 tests across 14 XML suites; `:infrastructure:assembleDebugAndroidTest` PASS; `:app:assembleDebugAndroidTest` PASS; `:app:assembleRelease` PASS with R8/minification and all four native ABIs. APK assembly is not a release runtime test. The 91 JVM count includes five parser-independent catalog-import tests added after the older 86-test evidence.

## Remaining qualification gaps and gate status

- **Native memory:** no enforceable PDFium heap/working-set ceiling has been set or measured. A 25 MiB descriptor/input cap, 5.33 MB metadata cap, single active request and process deadline do not bound decompression/object-graph allocations. No memory-exhaustion or peak-PSS stress test was executed. This is the principal mandatory blocker.
- **Parser robustness:** no fuzz corpus, recursive-object/resource-exhaustion campaign, native crash injection, or password-protected inputs with malformed encryption structures. One valid encrypted synthetic PDF was rejected without credentials.
- **Cancellation:** no user-facing caller exists, so explicit SAF cancellation, caller cancellation/unbind during native parse, revocation and provider errors are not qualified. Timeout kills the isolated process; no business mutation is reachable.
- **ABI/release runtime:** all ABIs compile and are packaged; only x86_64 debug service execution is proven. The minified release APK builds and R8 mapping preserves the JNI class/method names, but release APK runtime/ARM64/API23 behavior is not tested.
- **Import integration:** no parser caller, SAF import UI, current-right handoff, Room atomic operation, or end-to-end zero-partial-effect PDF test is wired. Existing parser-independent `CatalogImportAuthority`/Room tests remain separate evidence.
- **Provenance:** signed official publisher workflow and artifact hashes were verified offline; the Sigstore predicate does not bind the exact PDFium source commit. Named upstream fixes were mapped, but this does not establish general parser safety or absence of vulnerabilities.

| Gate | Result | Basis |
|---|---|---|
| Android process isolation | PASS for API 36 x86_64 prototype | Successful result is guarded by `Process.isIsolated()`; private manifest service; timeout process-death test. |
| Subject-only JNI / bounded descriptor and result | PASS for tested path | Static code review and 11-case suite. |
| Wall-time termination | PASS for tested stalled read | Emulator test observed process disconnect at about 5.1 s. |
| Native memory budget | BLOCKED | No enforceable/measured PDFium allocation ceiling. |
| ABI/release runtime | PASS_WITH_LIMITATIONS | Four-ABI debug/release build; runtime only x86_64 debug. |
| G-PDF | BLOCKED | Mandatory resource qualification and device matrix incomplete. |
| G-PASS | BLOCKED | End-to-end authorized atomic PDF import remains unconnected. |
| G-EVIDENCE | PASS_WITH_LIMITATIONS | Actual 11/11 XML, ABI builds, and source/provenance records retained; memory/ABI limitations explicit. |
| G-COMMIT | NOT READY | I10 mandatory G-PASS/G-PDF blockers remain. |

No I10 acceptance commit was created. I11–I14 remain gated behind I10 acceptance.

## R2 measurement update — 2026-10-09

The 11/11 report above is historical and is superseded for current parser source by [`I10_FINAL_UNBLOCK_EVIDENCE.md`](I10_FINAL_UNBLOCK_EVIDENCE.md) and [`ANDROID_PdfSubjectIsolatedServiceTest_R2.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_R2.xml): **13/13 PASS**. The isolated service now monitors PSS/native heap, terminates only itself at a sampled 256 MiB PSS threshold, and uses a bounded pipe FD for large Subject metadata so it does not exceed Binder transaction limits. The exact 25,000,000-byte synthetic PDF and maximum allowed Subject now pass; oversize inputs reject. Across 24 measured parser operations on API 36 x86_64, maximum observed PSS was 61,659 KiB, maximum native heap 39,651,312 bytes, and maximum elapsed time 175 ms, excluding the separately measured 5.25-second stalled-input termination. The PSS guard is sampled, not a hard kernel memory ceiling.

The contract has no numeric hard-memory-cap clause, but it requires demonstrated budgets. Adversarial PDF object-graph/decompression exhaustion, native crash injection, API 23/ARM64/minified-release runtime, and full SAF-to-atomic-import integration remain unverified. Therefore G-PDF/G-PASS remain BLOCKED; user-facing import stays disabled and G-COMMIT is not ready.

### R3 final source-identical run

The R2 run is superseded by `I10_FINAL_UNBLOCK_EVIDENCE.md`: final parser XML [`ANDROID_PdfSubjectIsolatedServiceTest_R3.xml`](test-results/ANDROID_PdfSubjectIsolatedServiceTest_R3.xml) reports 13/13 PASS at `2026-10-09T20:15:06Z`; its 24-operation metrics are in [`ANDROID_PdfSubjectMemoryProfile_R3.txt`](test-results/ANDROID_PdfSubjectMemoryProfile_R3.txt), max 66,897 KiB PSS, 39,652,352 bytes native heap, 171 ms. This source-identical run does not change the blocked gates.

## Current qualification result — 2026-10-10

The implementation and acceptance deltas after the historical prototype are
recorded in `I10_FINAL_ACCEPTANCE.md`, `I10_IMPORT_EVIDENCE.md`, and
`I10_PARSER_QUALIFICATION.md`. Current evidence establishes the complete
synthetic DocumentsUI → bounded SAF read → isolated Subject-only PDFium/JNI →
STORE_DATA_V1 codec → preview → explicit confirmation → authority → atomic
Room import path, including a no-write cancellation path. The latest combined
SAF/import suite is 6/6 PASS (`ANDROID_I10_SafFailuresAndImportUi_API36_x86_64_R1.xml`);
Room import rollback/authorization is 8/8 on API 23 x86 and API 36 x86_64;
the parser corpus is 19/19 on API 36 x86_64. The minified Release service was
executed on API 36 x86_64 and passed 1/1; exact output and APK hashes are in
`test-results/ANDROID_PdfiumMinifiedRelease_API36_x86_64_R1.txt`.

The observed R5 profile (67,174 KiB sampled PSS, 39,652,880-byte native heap,
174 ms maximum operation) and 32 MiB decompression fixture are test-corpus
measurements, not a universal bound or kernel-enforced native-memory limit.
API 23 Release runtime, ARM64 runtime, physical provider URI revocation and
actual low-storage behavior remain untested. Supply-chain attestation does not
bind the resolved PDFium source revision. These are disclosed limitations; the
contractual I10 gate requires reviewed provenance, bounded behavior and
confinement, not a reproducible source build or ARM64 runtime matrix. G-PDF is
therefore PASS_WITH_LIMITATIONS, not unconditional PASS. No I10 commit is made
until the separate final manifest review is complete.
