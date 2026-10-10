# I10 final acceptance — current decision: PASS_WITH_LIMITATIONS

Current decision date: 2026-10-10. The dated review below and all subsequent
differentials are historical unless explicitly marked current. Current gates:
G-PASS **PASS**; G-PDF **PASS_WITH_LIMITATIONS**; G-EVIDENCE
**PASS_WITH_LIMITATIONS**; G-COMMIT is pending final path-by-path manifest and
staged-diff review. This is not a claim of ARM64 runtime qualification or a hard
native-memory ceiling. See the 2026-10-10 final acceptance differential below.

Review date: 2026-10-09. Branch `feature/store-i10`; HEAD `65cac9345a1762874b50a427487b69335b7cc40a`.

## Gate decision

| Gate | Decision | Evidence |
|---|---|---|
| G-PASS | **BLOCKED** | Catalog PDF import, current authorization, full validation, and atomic persistence are mandatory and absent. |
| G-PDF | **BLOCKED** | PDFium candidate provenance and known fix mapping improved; temporary native Subject smoke passed bounded basic inputs. No production JNI/service, OS isolation proof, hard memory/time budgets, encrypted hostile-input tests, release/R8/ARM64 qualification, or integrated import exists. See [I10_PARSER_QUALIFICATION.md](I10_PARSER_QUALIFICATION.md). |
| G-EVIDENCE | **PASS_WITH_LIMITATIONS** for the implemented export/codec scope only | [I10_A_EVIDENCE.md](I10_A_EVIDENCE.md), [I10_B_EVIDENCE.md](I10_B_EVIDENCE.md), retained renderer/SAF/JVM XML, PDF hashes, 44 rendered pages and contact sheets. Import proof is absent by design. |
| G-COMMIT | **NOT READY** | The phase cannot be committed as accepted while G-PASS/G-PDF are blocked. No I10 commit exists. |

## Verified implementation and test evidence

- I10-A codec: 8 targeted JVM cases, included in the reported 86/86 JVM suite; no PDF parsing or persistence.
- I10-B: native PDF renderer 2/2; Android SAF/export UI 5/5, including DocumentsUI cancellation and a synthetic Downloads-provider URI write/read/delete. Four output types produced 44 pages and were inspected as documented in I10-B evidence.
- Architecture: `:verifyArchitecture` PASS; JVM: 86/86 PASS; relevant production and Android-test compilation PASS.
- Export evidence limitations remain: storage-full behavior was not exercised; revocation from a provider that actually revokes the selected URI grant is unverified; no full system-provider matrix.
- These are the results recorded in existing evidence. This acceptance review did not rerun those suites.

## P0/P1/P2

- P0: none identified in the reviewed export/codec changes.
- P1 / gate blockers: mandatory import path absent; required parser qualification, parser isolation, hostile input/resource tests, and atomic all-or-nothing import proof absent.
- P2 limitations: provider-specific SAF behavior, storage-full path, ARM64, release/minification and installed dependency footprint are unverified; the exact PDFBox release is old and its declared Bouncy Castle 1.72 transitives carry published vulnerabilities (scope and disposition documented separately).

## ADR10-R2 parser decision

ADR10 was reopened by explicit user authorization for the narrow parser security/maintenance problem. Direct PDFium 156.0.8076.0 artifacts have offline-verified Sigstore workflow identity and digest, but their SLSA predicate does not bind the resolved PDFium source revision; the binaries are only an evaluation candidate, not approved or integrated. PDFBox-Android with unverified BC overrides, SAMBox, and pCloud `pdfium-core` are not qualified. No security or functional gate was waived. See `ADR10_R1_REVIEW.md` R2 addendum and `I10_PARSER_QUALIFICATION.md`.

## Current repository and commit disposition

The worktree has 60 changed/untracked paths before this R2 evidence update; all are under `store-android`, and the staged index is empty. HEAD and protected `main` remain unchanged from their expected values. The R2 changes are evidence-only; no source, tests, Gradle files, manifest, database or schema changed in this review. Final tree remains intentionally dirty and uncommitted. Do not create the contract-defined I10 acceptance commit until all mandatory gates pass.

## Next required action

Resolve the remaining parser provenance and enforceable resource-limit requirements before selecting a production parser. Then implement and prove the isolated parser, connect it to the existing codec, preview, current authorization and atomic import, and complete the I10 gates. I11–I14 remain gated behind I10 acceptance and were not started.

## Final acceptance differential — 2026-10-10

This section supersedes the earlier dated gate decisions above. They remain historical evidence of the work at those points.

| Gate | Current decision | Evidence |
|---|---|---|
| G-PASS | **PASS** | `CatalogImportEndToEndUiTest` exercises the real CatalogScreen, system DocumentsUI selection/cancellation, bounded Activity reader, isolated PDFium, strict codec, preview without writes, explicit confirmation, current-right/session authority, atomic Room persistence and reopen. The latest 4-test catalog UI run includes this scenario and the default visibility/permission assertion. Room-native tests separately cover duplicate/invalid/session/rollback zero-effect paths. |
| G-PDF | **PASS_WITH_LIMITATIONS** | Parser corpus and bounded protocol, isolated process, timeout/recovery, 2.0.1 compatibility, real-rendered PDF archive/page review, official artifact/notice/advisory evidence, API 23/API 36 parser runtime, plus minified Release JNI/Binder/Subject runtime. No hard kernel native-memory ceiling is claimed. |
| G-EVIDENCE | **PASS_WITH_LIMITATIONS** | Final matrix and retained XML/report references listed below; earlier diagnostics are distinct. Limitations are explicit and assigned to I13/I14 or retained as accepted parser/resource/provider risks. |
| G-COMMIT | **READY AFTER MANIFEST REVIEW** | No commit has yet been created. Stage only the reviewed I10 inventory, run cached diff check, then create the contract-defined I10 commit. |

### Latest source and test evidence

- Catalog import UI and end-to-end: `ANDROID_CatalogImportQualifyingUi_API36_x86_64_R2.xml`, 4/4 PASS, 0 failures/errors/skips, API 36 x86_64. It includes `defaultImportActionRequiresProductUpdatePermission` and `realPickerPreviewConfirmationAndRoomPersistenceAreOneWorkflow`. Source change to activate the accepted import action preceded this test run.
- Minified Release runtime: `test-results/ANDROID_PdfiumMinifiedRelease_API36_x86_64_R1.txt`, 1/1 PASS using the exact R8-minified `:app:release` APK, locally test-signed only, on `emulator-5554` API 36 x86_64. The real isolated service bound through Binder, its isolation guard permitted success, JNI resolved, and the extracted Subject matched byte-for-byte. The standard AGP connected task cannot install an unsigned release APK; this was separately recorded as a 0-test harness diagnostic. Production release signing configuration was not changed.
- Parser: latest retained API 36 x86_64 corpus 19/19 PASS (`ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R5.xml`); API 23 x86 parser and Room suites 16/16 and 8/8 PASS (`...API23_x86_R3.xml`). Native measured profile remains the recorded API 36 synthetic measurements, including 67,174 KiB peak sampled PSS, 39,652,880-byte peak native heap and 174 ms maximum operation for the R5 corpus. These are observations, not a hard memory cap.
- Room catalog import: 8/8 PASS on API 36 and API 23; XMLs `ANDROID_CatalogImportNativeTest_API36_x86_64_R3.xml` and `ANDROID_CatalogImportNativeTest_API23_x86_R3.xml` cover exact persistence, permission/session/duplicate rejection, cancellation and rollback with zero partial product/stock/history effects.
- JVM: latest complete suite 92/92 PASS across 14 XML suites; no JVM production logic changed in the final UI/R8 qualification increment. Architecture and minified Release assembly passed after the final UI change. The Release runtime test above was run against the final assembled source.
- Existing I10-B renderer/export evidence remains in `I10_B_EVIDENCE.md`: four document types, all 44 retained synthetic pages visually reviewed, exact output hashes and page review. SAF cancellation/write-failure and actual provider selection evidence remain; provider-specific URI revocation and real storage exhaustion are limitations already recorded for I10-B.

### Current limitations and severity

- P0: none identified.
- P1: none identified against I10's phase-owned acceptance requirements.
- P2 / later gate: no ARM64 runtime was possible on this x86_64 host; all four ABI libraries/package outputs and ARM64 ELF/API/alignment metadata were verified. Runtime ARM64/16-KiB device coverage is I13/I14, not claimed here.
- P2: sampled PSS/watchdog is not a kernel-enforced ceiling; measurements are bounded to the synthetic corpus/device. No general claim that all hostile PDFs are safe.
- P2: attestation does not bind the exact resolved PDFium source revision and no reproducible source build completed; official artifacts, signatures/digests, named fixes and notices are nevertheless documented.
- P2: provider-specific revoked-URI and physical storage-full behavior were not exercised on a real external provider/device; error handling and synthetic write-failure are covered. This accepted SAF limitation remains for I13/I14 review.
- P2: no physical printer/phone/tablet qualification in I10; I11/I14 own the relevant later gates.

User-facing catalog PDF import is now enabled in the normal composition; `PRODUCTS:UPDATE`, current session checks, preview and explicit confirmation are still enforced by the existing authority. No import is performed from document contents without user confirmation. Protected `main` remains unchanged; no push/merge/deployment occurred.

### Final I/O failure and post-enable regression — 2026-10-10

The later source edit adds explicit stream boundaries used by both SAF operations. The Activity uses the same bounded reader and SAF output writer as before; output exceptions now remain a failure result, cancellation propagates, and streams close through `use`. The import UI was already default-enabled and remains permission-gated.

Latest focused API 36 x86_64 report: [`ANDROID_I10_SafFailuresAndImportUi_API36_x86_64_R1.xml`](test-results/ANDROID_I10_SafFailuresAndImportUi_API36_x86_64_R1.xml), **6/6 PASS**, 0 failures/errors/skips. It includes synthetic revoked-read `SecurityException` rejection before parsing, synthetic `ENOSPC` mapping to a failed export result with stream closure, import action permission visibility, denial at confirmation, preview/cancel without mutation, and the real DocumentsUI-to-atomic-Room end-to-end success/cancel scenario. No emulator storage was filled and no business profile was reset.

This tests provider failure semantics without claiming a real external provider revoked a previously issued grant or physically ran out of storage. The system DocumentsUI path and selected provider are exercised for cancellation and successful read/write; provider-specific grant revocation and actual low-space hardware remain limitations for later device qualification.

The same final source was then rebuilt and rerun through the dedicated minified Release check: `:verifyArchitecture :app:assembleRelease :releaseQualification:assembleRelease` passed, and the locally test-signed R8 Release APK again returned 1/1 PASS from `emulator-5554`. Its final SHA-256 is recorded in `ANDROID_PdfiumMinifiedRelease_API36_x86_64_R1.txt`; the source `MainActivity.kt` timestamp precedes both the 6/6 Android UI report and final Release APK.

### Final manifest and evidence-integrity review

The reviewed change set contains 169 paths, all under `store-android`; the
index is empty before this final staging review and there are no generated APKs,
build directories, caches, credentials, or user data in the manifest. It
contains the four required PDFium ABI libraries/notices, synthetic PDF
fixtures, retained renderer screenshots/archive, final and clearly named
diagnostic XML, source/tests, and I10 evidence. All retained XML reports parse;
the newest focused report is 6/6 with zero failures/errors/skips. CRLF in 36
retained XML files was normalized to LF after confirming parsed XML trees and
test metadata were byte-equivalent at the XML information-set level. The
synthetic encrypted PDF retains significant fixed-width xref/padding spaces and
is marked binary in `store-android/.gitattributes`; no fixture bytes were
rewritten. `git diff --cached --check` passes after these evidence-only
normalizations. The contract-defined I10 commit is now ready after a final exact
staged-path comparison.

## Differential update — experimental PDFium integration, 2026-10-09

The earlier statement that no JNI/service existed is superseded by `I10_PDFIUM_INTEGRATION_EVIDENCE.md`. A private isolated-process Subject extractor now exists, with bounded read-only descriptor input/output and a five-second process deadline. The final API36 x86_64 instrumentation suite is 11/11 PASS, and debug Android-test/minified-release builds compile four ABIs.

Gate outcomes remain: G-PASS **BLOCKED** (the parser is not wired to SAF/preview/current authorization/atomic Room import); G-PDF **BLOCKED** (PDFium internal native memory has no enforceable or measured ceiling, and ARM64/API23/release runtime/security-stress coverage is incomplete); G-EVIDENCE **PASS_WITH_LIMITATIONS** for the experimental adapter and tests; G-COMMIT **NOT READY**. External import remains disabled. No I10 commit was created.

## Final-unblock differential — 2026-10-09

Current resource measurements and test outcomes are in [`I10_FINAL_UNBLOCK_EVIDENCE.md`](I10_FINAL_UNBLOCK_EVIDENCE.md). The 13-case parser suite passes, including exact max input, max Subject over a pipe, timeout/rebind, and process isolation. Sampled PSS is guarded at 256 MiB; observed maxima for this API36 x86_64 synthetic run were 61,659 KiB PSS, 39,651,312 bytes native heap, and 175 ms. This is not a hard memory ceiling, and adversarial decompression/object graphs remain untested. More decisively, SAF/codec/preview/current-right/atomic-Room PDF import is not integrated. Thus G-PASS **BLOCKED**, G-PDF **BLOCKED**, G-EVIDENCE **PASS_WITH_LIMITATIONS**, G-COMMIT **NOT READY**; no commit and no I11–I14 work.

The final exact-source R3 rerun supersedes the preceding measurements: 13/13 parser tests passed at `2026-10-09T20:15:06Z`; observed maximum was 66,897 KiB PSS, 39,652,352 bytes native heap, and 171 ms. See the R3 XML/log and `I10_FINAL_UNBLOCK_EVIDENCE.md`. Gate decisions remain unchanged.
