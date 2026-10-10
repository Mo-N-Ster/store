# STORE autonomous delivery checkpoint — current status 2026-10-10

Entries below are chronological historical status. The latest section records
the current I10 disposition; earlier BLOCKED decisions are superseded.

Updated: 2026-10-09. This checkpoint supersedes the earlier I10-B renderer evidence where page counts, hashes, SAF test counts, or raster evidence differ, incorporates `I10_PARSER_QUALIFICATION.md`, and records the parser-independent import foundation and ADR10 reopen request in `ADR10_R1_REVIEW.md`.

## Verified repository state

- Repository: `~/Projects/STORE`
- Branch: `feature/store-i10`
- HEAD: `65cac9345a1762874b50a427487b69335b7cc40a` (the expected I09 base)
- Protected `main`: `3691eccedc9855686b011e34c7420f17aaf6c085`
- Index: empty. No commit has been created.
- Worktree before parser review: 44 paths; three parser qualification records made 47. Parser-independent import source/tests, diagnostic/final reports, and ADR10 review were added later. Current `git status --short --untracked-files=all` lists 60 paths, all under `store-android`; index remains empty.

## Completed and verified in this continuation

- Closed the Help timestamp overflow and catalog table-header wrapping defects in the native renderer.
- Added pre-allocation row bounds for invoice and report rendering.
- Final offline checks: architecture PASS; JVM 86/86 PASS; affected production and Android-test compilation PASS.
- API 36 emulator `STORE_API36_Main(AVD)`: renderer 2/2 PASS (44 pages rasterized), SAF UI 5/5 PASS. SAF UI includes actual DocumentsUI cancellation and a real selected Downloads-provider URI with synthetic write/read/delete, alongside denial and simulated write-failure cases.
- Every final page was reviewed through per-document contact sheets; full-size invoice, report, catalog and help samples were also inspected. Final archive/hash and sheets are retained in `rendered-pages/`.
- The emulator has only synthetic fixtures. No DB reset, account-data mutation, network request, or production data access occurred.
- Parser-independent import foundation: `:verifyArchitecture` PASS; `CatalogImportPolicyTest` 5/5 PASS; `CatalogImportNativeTest` 4/4 PASS on verified API 36 emulator. Final XML retained in `test-results/JVM_CatalogImportPolicyTest.xml` and `test-results/ANDROID_CatalogImportNativeTest.xml`; earlier failed diagnostics remain separate. No parser dependency or import UI was added.

## Contract blocker

I10 is **not accepted**. The contract makes PDFBox-Android 2.0.27.0 the required Subject transport and requires maintenance/advisory/notices review, isolated parser confinement, and time/memory/resource tests before import activation. A bounded official-source review fetched the exact 3,254,019-byte Maven Central AAR to `/tmp`, verified its SHA-256 against the published checksum, and inspected the official POM. The POM pins Bouncy Castle `bcprov`, `bcpkix`, and `bcutil` at 1.72; official advisories identify affected versions at that level, while the last stable PDFBox-Android release observed is January 2023 and the project has no published security policy. The intended metadata-only call path may not reach every vulnerable BC API, but reachability and mitigation have not been demonstrated. No dependency was added. See `I10_PARSER_QUALIFICATION.md` for sources, CVEs, artifact hash, limitations, and decision. Replacing the locked parser with a custom parser or another library would violate ADR10/G-STOP.

Consequently, external PDF import is still disabled and I10 is not accepted. A parser-independent preview/authorization/domain-validation/atomic Room batch foundation has now been implemented and focused-tested, but is not reachable from SAF, UI, or app composition. PDF Subject extraction, isolated process confinement, dependency qualification, parser malformed/encrypted/oversized/resource tests, and end-to-end tests remain outstanding. The codec is structural only and grants no authority.

Further I10-B limits: storage-full behavior was not tested because filling emulator storage would be unsafe; callback-level write failure is covered. A real DocumentsUI URI was successfully written, but app-side revocation did not revoke the system provider's temporary grant, so revoked-URI behavior remains unverified against a revoking provider.

## ADR10-R2 update — 2026-10-09

The user authorized a narrow reopening of ADR10 to reevaluate PDF parser security and maintenance. The reopening does not waive the Subject-only contract, parser isolation, bounded hostile-input/resource tests, current-right validation, or atomic import requirements. See `ADR10_R1_REVIEW.md` and `I10_PARSER_QUALIFICATION.md` for the candidate matrix and evidence.

Direct PDFium binary release 156.0.8076.0 remains an evaluation candidate: official Android ABI archives and published hashes were checked; source ref `chromium/8076` resolved to `8ca5b735df4263f43c830479b78213854a392c22` (2026-09-25); minSdk 23, 16-KiB ARM64 ELF segment alignment and Subject metadata API are present, with license texts in the archives. Sigstore verification now passes offline for the official builder identity and ARM64 artifact digest, although the attestation omits the resolved PDFium source commit. Named PDFium security fixes were mapped to source commits and reviewed; this does not establish general parser safety. A temporary bounded Subject smoke harness passed on the synthetic API36 x86_64 emulator for valid, missing, oversized Subject, malformed, truncated and over-input-limit cases. It was not production code or an isolated-process test; no XML retained. NDK r27c (`27.2.12479018`, Clang 18.0.3) and CMake 3.22.1 are installed. There is still no JNI bridge, isolated-process service, hard native resource proof, ABI/release test, or PDF-to-import end-to-end test. No parser binary/dependency entered the repository; PDF import remains disabled.

Reproducible build attempt: official `depot_tools` was cloned under `/tmp` with metrics disabled and the exact PDFium revision was synced as far as available dependencies permitted. `gclient sync` did not complete because large corpus/Skia/V8/toolchain dependencies continued fetching; attempts were interrupted before compilation. No build was made. Partial source/tool directories were later removed from `/tmp`; no STORE build files or dependencies changed. No full source checkout was restarted.

Latest parser-independent import evidence remains: architecture PASS; JVM policy 5/5; synthetic API 36 Room import 4/4 with rollback/no-partial-effect assertions. These do not pass G-PDF/G-PASS. I10 remains blocked; no acceptance commit. Branch `feature/store-i10`, HEAD `65cac9345a1762874b50a427487b69335b7cc40a`, protected `main` `3691eccedc9855686b011e34c7420f17aaf6c085`, staged index empty. The pre-R2 evidence/source inventory was 60 dirty paths, all under `store-android`; R2 edits only existing evidence files.

## Gate and continuation decision

- I10-B rendering/export implementation: complete with the documented provider/storage limitations.
- I10 G-PASS: BLOCKED (mandatory catalog import absent).
- I10 G-PDF: BLOCKED (required parser qualification and import confinement absent).
- I10 G-EVIDENCE: PASS_WITH_LIMITATIONS for implemented export scope; import evidence is absent by design.
- I10 G-COMMIT: NOT READY; no I10 acceptance commit is authorized until the phase gates pass.
- I11, I12, I13 and I14 are not started. Their contractual prerequisites depend on accepted preceding phases, beginning with I10.

Next action: resolve source-to-binary reproducibility and demonstrate enforceable memory/time limits before integrating a parser. Do not activate external import while these gates remain open. The parser-independent import foundation can be composed only after parser qualification. No acceptance commit while any mandatory gate is blocked. I11–I14 remain gated by accepted I10.

## Checkpoint update — experimental PDFium integration, 2026-10-09

- Branch `feature/store-i10`, HEAD `65cac9345a1762874b50a427487b69335b7cc40a`; `main` remains `3691eccedc9855686b011e34c7420f17aaf6c085`; index empty; no commit.
- NDK `27.2.12479018` and CMake `3.22.1` build Subject-only JNI and an isolated-process service. Four ABI archives, notices and native shims are present under infrastructure; `.cxx` native build cache is ignored.
- API36 `STORE_API36_Main` read-only x86_64 instrumentation: 11/11 PASS; retained `test-results/ANDROID_PdfSubjectIsolatedServiceTest.xml`. The suite covers V1/legacy 2.0.1, exact decimal and strict codec rejection, missing/empty, malformed/truncated/encrypted, oversized input/metadata, concurrency and timeout process death. Current JVM suite: 91/91; architecture PASS; app Android-test assembly and minified release assembly PASS across four ABIs.
- Two implementation defects found through review/testing were fixed: PDFium's input bytes now remain alive until document close; asynchronous service responses copy request IDs before Android recycles the inbound `Message`.
- Import remains disabled. Mandatory blocker: no hard/measured PDFium native-memory ceiling or resource-exhaustion campaign. ARM64/API23/release runtime and the complete SAF-to-atomic-import flow are also unverified/not implemented. G-PDF/G-PASS BLOCKED; G-COMMIT NOT READY. Do not begin I11–I14 until I10 acceptance.

## Latest controlled implementation checkpoint — 2026-10-09

The experimental Subject JNI/service now measures per-operation PSS/native heap in debug builds and kills its isolated process if sampled PSS reaches 256 MiB. API36 x86_64 synthetic profiling across 24 operations observed max PSS 61,659 KiB, native heap 39,651,312 bytes, max successful parse 175 ms; stalled read is killed at about 5.25 s. The 13/13 final XML and metrics log are retained as `test-results/ANDROID_PdfSubjectIsolatedServiceTest_R2.xml` and `test-results/ANDROID_PdfSubjectMemoryProfile_R2.txt`. Maximum valid 25,000,000-byte input and max bounded Subject were tested; large Subject uses a bounded PFD pipe. A `RLIMIT_DATA` experiment caused four parser timeouts and was removed.

This evidence does not establish a hard kernel memory ceiling or robust behavior for decompression/object-graph attacks. API23, ARM64 runtime and minified-release runtime remain untested; only x86_64 API36 debug instrumentation ran. The full SAF → isolated parser → codec → preview/current authorization → atomic Room path is still absent and user import remains disabled. I10 G-PDF/G-PASS BLOCKED; G-COMMIT NOT READY. No I11–I14 progression.

Final exact-source R3 retest confirms 13/13 parser tests with zero failures/errors/skips, XML timestamp `2026-10-09T20:15:06Z`; 24 metrics samples peak at 66,897 KiB PSS, 39,652,352 bytes native heap, 171 ms. Final retained reports: `test-results/ANDROID_PdfSubjectIsolatedServiceTest_R3.xml` and `test-results/ANDROID_PdfSubjectMemoryProfile_R3.txt`. These measurements do not close adversarial parser qualification or the missing PDF-to-atomic-import workflow.

## Current recovery checkpoint — 2026-10-10, after final targeted qualification

- Repository: `/home/sterling_dev/Projects/STORE`; branch `feature/store-i10`; HEAD `65cac9345a1762874b50a427487b69335b7cc40a`; protected main remains `3691eccedc9855686b011e34c7420f17aaf6c085`.
- Current dirty inventory at checkpoint: 168 paths, all under `store-android`; index empty. `git diff --check` passes. There is no I10 commit yet. Prior inventory counts in this file are historical.
- Complete synthetic production UI path is exercised by DocumentsUI through atomic Room persistence; latest SAF failure/import UI XML is 6/6 PASS. API 23 and API 36 Room import XMLs each report 8/8. API 36 parser corpus R5 reports 19/19. JVM suite is 92/92. Architecture, debug/release assembly pass. The final R8-minified Release isolated service/JNI/Binder Subject run passes 1/1; see `ANDROID_PdfiumMinifiedRelease_API36_x86_64_R1.txt`.
- Current phase decision: G-PASS PASS; G-PDF PASS_WITH_LIMITATIONS; G-EVIDENCE PASS_WITH_LIMITATIONS. G-COMMIT awaits complete explicit-path review and staged-diff check. I11 remains locked until I10 commit/acceptance.
- P2: no ARM64 runtime (I13/I14 device qualification); no hard kernel-enforced PDFium memory ceiling; measured memory applies to the retained synthetic corpus; attestation lacks resolved-source revision binding; no physical low-space or provider-specific revoked-grant test. These are disclosed, not claimed as PASS.
- Next: finish individual file/size/sensitivity review, reconcile the exact 168-path manifest, stage only reviewed paths, run `git diff --cached --check`, commit with the contract message only if all gates remain satisfied, verify clean tree, then read I11 clauses and proceed sequentially.
