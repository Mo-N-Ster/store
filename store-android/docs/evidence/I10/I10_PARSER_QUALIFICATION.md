# I10 PDF parser dependency qualification — current: PASS_WITH_LIMITATIONS

Current disposition (2026-10-10): PDFium is approved only for the
Subject-only isolated I10 adapter under ADR10's reopened scope. Current
G-PDF is PASS_WITH_LIMITATIONS; it is not a general PDF parser safety claim.
The historical BLOCKED assessment below is retained as a record of the
pre-integration state. See the final differential in `I10_FINAL_ACCEPTANCE.md`.

Review date: 2026-10-09. Repository state reviewed: `feature/store-i10`, base HEAD `65cac9345a1762874b50a427487b69335b7cc40a`.

## Decision

**BLOCKED — do not add PDFBox-Android to production and do not enable external PDF import.** This review does not satisfy the I10 parser gate. No production dependency, parser, manifest component, import workflow, or catalog persistence was changed.

The contract fixes PDFBox-Android as the Subject transport and says not to substitute a home-grown/general parser. It also requires maintenance/advisory/transitive review and isolated hostile-input/resource testing before enabling external import. The locked candidate cannot be qualified as safe on the evidence currently available.

## Candidate artifact and provenance

Candidate: `com.tom-roush:pdfbox-android:2.0.27.0`.

- Maven Central lists the AAR, sources, POM, checksums, and signatures, all published 2023-01-02: [Maven Central artifact directory](https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/).
- The AAR was fetched from the official Maven Central host to `/tmp` for this bounded review only. Size: 3,254,019 bytes. SHA-256 computed locally: `30277f879cfd571db2a137582c95516a0d4ea6778e945519bc58ca93d57d88c7`; it matches Maven Central's published `.aar.sha256` file.
- The POM identifies the expected GitHub project and Apache License 2.0, and declares `bcprov-jdk15to18`, `bcpkix-jdk15to18`, and `bcutil-jdk15to18`, each version `1.72`.
- The release page identifies v2.0.27.0 as released 2023-01-02 and says it updates to Apache PDFBox 2.0.27: [official release](https://github.com/TomRoush/PdfBox-Android/releases/tag/v2.0.27.0). The release page reports the signing key expired; this review did not validate the detached Maven signature against an independently verified key.
- The AAR contains `classes.jar` and `AndroidManifest.xml`, but no bundled `LICENSE` or `NOTICE` file. Upstream has `LICENSE.txt` and `NOTICE.txt`; delivery would need to retain the notices required for the complete dependency graph. A full transitive license/notice SBOM was not established.

## Maintenance and vulnerability review

- The latest stable release visible in the official project release list is v2.0.27.0 from January 2023. GitHub reports no published repository security advisories and no `SECURITY.md` policy: [project Security page](https://github.com/TomRoush/PdfBox-Android/security). Absence of an advisory is not evidence that the parser is secure. The long stable-release gap is not sufficient maintenance evidence for a parser of hostile files under this contract.
- Apache's security table lists the old PDFBox parser DoS issues as fixed by 2.0.24, so the upstream 2.0.27 base is newer than those affected ranges. The 2026 path-traversal CVEs concern the `ExtractEmbeddedFiles` example code, not the core library; that functionality is expressly excluded from STORE's parser: [Apache PDFBox security](https://pdfbox.apache.org/security.html), [NVD CVE-2026-23907](https://nvd.nist.gov/vuln/detail/cve-2026-23907).
- More significantly, the exact POM pins Bouncy Castle 1.72. The Bouncy Castle project states that BC 1.72 and earlier are affected by CVE-2023-33202 (crafted ASN.1 through `PEMParser` can cause `OutOfMemoryError`; fixed in 1.73): [BC advisory](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902023%E2%80%9033202). BC before 1.78 is also affected by CVE-2024-29857 in elliptic-curve parameter evaluation: [official BC release note](https://www.bouncycastle.org/resources/java-release-1-78-1-is-now-available-for-download/). The intended Subject-only PDF call path does not deliberately use PEM parsing, certificates, or signature validation; however, the dependency graph embeds the affected cryptographic libraries, and no source-level reachability proof plus isolated hostile-input testing has been produced. We therefore do not waive these findings based on presumed non-reachability.
- Newer BC releases exist, but overriding three transitive modules or changing their artifact coordinates would be a separate compatibility change. It has not been applied or tested against this PDFBox build. No mitigation is claimed.

## Compatibility, footprint, and confinement

- Upstream README documents API 19+; STORE's minimum is API 23, so the declared minimum is compatible. This is vendor documentation, not a STORE-device test.
- The fetched AAR contains no native ABI directory, but ARM64 behavior is **UNVERIFIED** because the production dependency was not integrated or run on ARM64.
- The 3.25 MB AAR alone is not total installed footprint. Its three Bouncy Castle dependencies and release APK delta were not measured.
- Release/minification compatibility is **UNVERIFIED**.
- No parser process/service isolation was implemented or tested. Module boundaries alone are not OS isolation. The contract requires an isolated parser boundary receiving only bounded, read-only bytes/descriptors and no session, database, or network authority.
- Malformed/encrypted/oversized PDF CPU and memory budgets, timeout/termination behavior, process isolation under resource exhaustion, and real SAF-revoked input behavior are **UNVERIFIED**.

## Compliant path / blocker

Android `PdfRenderer` renders pages but does not provide a supported PDF Subject metadata reader. A hand-written PDF parser, OCR, page-text extraction, or an unreviewed alternate dependency would violate the locked architecture. The compliant path is to reopen ADR10 under the repository contract to select a maintained and supportable parser/dependency, or to obtain a security/maintenance disposition and a tested compatible mitigation for the exact PDFBox/BC graph. Until then, imports must remain absent and disabled.

Sources consulted were limited to Maven Central, the PDFBox-Android project, Apache PDFBox security pages, the Bouncy Castle project, and NVD. No unrelated network access occurred.

## ADR10-R2 follow-up: PDFium candidate (not approved)

On 2026-10-09 the user authorized narrow ADR10 reopening. This section evaluates alternatives without changing the locked functional/security gates. No parser was integrated.

### Candidate comparison

- **PDFBox-Android 2.0.27.0 plus dependency overrides:** remains unqualified. The POM's Bouncy Castle 1.72 pins and 2023 release age remain unresolved; no compatible patched graph, reachability analysis or isolated hostile-input test was established.
- **SAMBox and pCloud `pdfium-core` 2.1.0:** SAMBox lacks a qualified Android artifact. pCloud's project exposes metadata Subject, but the inspected 2.1.0 build has minSdk 24, above STORE's minSdk 23; the claimed Maven Central coordinate was unavailable at the reviewed path, and its security/release/R8 and STORE fixture compatibility remain unverified.
- **Direct PDFium:** official `bblanchon/pdfium-binaries` Android release `chromium/8076`, version 156.0.8076.0 (release dated 2026-09-29), is the best candidate to qualify next. Its four Android ABI archives matched their official SHA-256 values: ARM64 `b7a6919dc130dfdf7e409bd1762ed2a306408c4faee1173668837fc446217bf7`; ARMv7 `4cabe64372ec9a8f9a7e023ae1c2802068abdb5ed79d4ef5cd42f2bbde80927a`; x64 `9d043689096c7cdd4ca98fff95599be115b2a38feba0c68d2a79ba5eedbda415`; x86 `eb0aa52696094f66c86994220d45b00b63cd90b13b8e76bd9b201d7f89825214`. Build arguments specify min SDK 23; `FPDF_GetMetaText` is exported; native runtime dependencies observed are Android system libraries. ARM64 `libpdfium.so` is approximately 6.2 MB. Archives include third-party license texts. These observations are not equivalent to integrating the full notices/SBOM or validating a release APK.

The official PDFium source ref `refs/heads/chromium/8076` resolved via `git ls-remote` to commit `8ca5b735df4263f43c830479b78213854a392c22` (commit date 2026-09-25). The release page states the binary was built from branch `chromium/8076`; its Sigstore bundle predicate identifies the `pdfium-binaries` build-workflow source commit `f2e9a1c45bb17b85b540abf1af30146ef65416ac` and lists the Android artifact hashes, but the predicate does not record the resolved PDFium source commit. The bundle was independently verified offline with Sigstore against the GitHub workflow identity, repository/ref, workflow revision and ARM64 artifact digest; verification returned `OK`, including the transparency proof. Publisher/workflow authenticity and artifact integrity are **VERIFIED**. The predicate omits the resolved PDFium source commit, so cryptographic source-to-binary reproducibility remains **UNVERIFIED**.

### Advisory-to-source disposition

The source review mapped reported issues to fixes in the candidate source history: CVE-2026-5889 / issue 486906037 to PDFium commit `9c97da0` (“Use better IV in PDF_CryptoHandler::EncryptContent”); CVE-2026-6305 / issue 496618639 to `237bc015b08696881bf0a2007bec95d0aecf8e67` (Little CMS overflow); CVE-2026-6306 / issue 496907110 to the official Chromium PDFium roll ending at `ca8a943c247c208fd7a9cd21b4de049f22b93070` (libtiff overflow); CVE-2026-4455 to `1765e514...` and `ee83ca8...`; CVE-2026-95338 / issue 556535630 to `4a366282...`. The 6305, 6306, 4455 and 95338 fix commits were checked as ancestors of `chromium/8076`; the September source snapshot postdates the mapped 5889 fix. These mappings prove inclusion of the named fixes, not general safety of PDF parsing.

The October CVE-2026-106421 and CVE-2026-106204 fixes examined are in Chromium's PDFium integration wrappers (`pdfium_engine.cc` and `pdfium_page.cc`), not standalone PDFium. STORE's proposed metadata-only calls would not invoke those wrapper methods. This limits direct applicability but does not qualify PDFium's document-loading core against malicious files.

The ARM64 library's ELF `LOAD` segment alignment is `0x4000` (16 KiB); its observed dynamic dependencies are `libdl.so`, `libm.so`, and `libc.so`. This is a static binary inspection only, not Android load/runtime qualification. The installed official toolchain is Android NDK `27.2.12479018` (Clang 18.0.3) and CMake `3.22.1`.

### Advisory, isolation and qualification status

Official Chrome release notes disclose PDFium issues CVE-2026-4455, CVE-2026-5889, CVE-2026-6305/6306, and CVE-2026-95338. The named fixes and scope analysis are recorded above; they do not establish vulnerability-free status. References: [March 18 Chrome release](https://chromereleases.googleblog.com/2026/03/stable-channel-update-for-desktop_18.html), [April 7 release](https://chromereleases.googleblog.com/2026/04/stable-channel-update-for-desktop.html), [April 15 release](https://chromereleases.googleblog.com/2026/04/stable-channel-update-for-desktop_15.html), [September 22 release](https://chromereleases.googleblog.com/2026/09/stable-channel-update-for-desktop_0856730748.html), and [October 6 release](https://chromereleases.googleblog.com/2026/10/stable-channel-update-for-desktop_086471744.html).

The candidate requires a thin native Subject-only binding and a platform isolated-process service. Neither exists. A temporary NDK smoke harness on the synthetic API 36 x86_64 emulator verified: a 588-byte PDF yielded the expected 148-byte UTF-16LE Subject; missing Subject yielded empty UTF-16; a 5,000-character Subject exceeded and was rejected by the harness's 4,096-byte output cap; malformed (18-byte) and truncated (100-byte) files returned PDFium error 3; and a 1,100,588-byte input was rejected before parsing by the harness's 1-MiB input cap. This is smoke evidence only, not application code or process-isolation qualification; it has no retained XML. Temporary harness files were removed from emulator `/data/local/tmp` without wiping its profile.

No actual test demonstrates Binder/process confinement, hard native memory limits, timeout/cancellation, crash recovery, encrypted hostile input, ARM64 runtime, release/R8, STORE_DATA_V1 decoding, or compatibility with the actual synthetic 2.0.1 PDF fixture. NDK `27.2.12479018` (Clang 18.0.3) and CMake `3.22.1` are installed. The candidate is **not approved for production**; external import remains disabled.

### Reproducible-source-build attempt

The official Chromium `depot_tools` repository was cloned to `/tmp/store-depot-tools` with metrics disabled. `gclient sync --nohooks -j4 --revision 8ca5b735df4263f43c830479b78213854a392c22` was attempted in `/tmp/store-pdfium-repro`. The pinned PDFium source and many dependencies were fetched, but synchronization did not complete: large test-corpus, Skia, V8, instrumented-library and toolchain/sysroot dependencies continued to fetch, and attempts were interrupted before a complete source/dependency tree or build was available. No build was produced; no source-reproducibility claim is made. The partial source/tool directories were later removed from `/tmp` to free temporary storage. No full source checkout was restarted for the current integration.

## 2026-10-09 experimental JNI/service update — still BLOCKED

The later implementation and test results supersede the earlier statements that no JNI bridge or isolated service existed. See [I10_PDFIUM_INTEGRATION_EVIDENCE.md](I10_PDFIUM_INTEGRATION_EVIDENCE.md). A Subject-only NDK adapter uses the four already verified official ABI artifacts and a private `isolatedProcess` service. API 36 x86_64 instrumentation verified the isolated UID, bounded Subject extraction, synthetic 2.0.1 compatibility, strict downstream codec failures, malformed/truncated/encrypted/oversized rejection, concurrency rejection, and a five-second process-killing deadline. Final suite: 11/11, no failures/errors/skips, retained as `test-results/ANDROID_PdfSubjectIsolatedServiceTest.xml`.

After the last source changes: `:verifyArchitecture` PASS; JVM 91/91; Android-test assembly PASS; minified release assembly PASS for four ABIs. Only x86_64 debug runtime was executed. The decoder remains disconnected from SAF and catalog import.

**Current decision remains BLOCKED.** Input/output are bounded and the isolated process is terminated on timeout, but no hard or measured ceiling for PDFium's internal native memory use has been established. Resource-exhaustion/fuzz testing, ARM64 runtime, API 23 runtime, and minified-release runtime are absent. Official artifact attestations verify publisher workflow identity and archive digests but omit the exact resolved PDFium source commit; no reproducible source build was completed (and reproducibility is not an explicit separate I10 contract criterion). G-PDF and G-PASS remain BLOCKED, G-EVIDENCE is PASS_WITH_LIMITATIONS for the prototype evidence, and G-COMMIT is NOT READY. User-facing PDF import remains disabled.

## Final I10 differential — 2026-10-10

The blocked status above is historical and superseded by `I10_FINAL_ACCEPTANCE.md`. The current bounded Subject-only PDFium implementation and isolation tests meet I10's explicit confinement and resource-budget criteria on the tested API 23 x86/API 36 x86_64 targets. API 36 corpus result is 19/19; the final minified Release service executes successfully under JNI/Binder on API 36 x86_64, retained in `ANDROID_PdfiumMinifiedRelease_API36_x86_64_R1.txt`. The real synthetic DocumentsUI-to-atomic-import path also passes. The selected PDFium decision, limits, and named provenance evidence are recorded in the final ADR10 resolution.

No hard kernel native-memory cap, ARM64 runtime, physical-device test, or exact source-commit attestation is claimed. The official binary's workflow/digest and relevant named advisories/notices were reviewed; no reproducible source build was completed. ARM64/16-KiB integration is assigned to I13 and physical-device/final-release qualification to I14. These remain disclosed limitations; no assertion of universal parser safety is made.

### R2 qualification update — 2026-10-09

The preceding status is historical. See [`I10_FINAL_UNBLOCK_EVIDENCE.md`](I10_FINAL_UNBLOCK_EVIDENCE.md). The current service has a sampled 256 MiB PSS kill threshold and 5-second wall deadline; 13/13 synthetic API 36 x86_64 parser tests pass, including exactly 25 MB input and the full bounded Subject output over a pipe. Twenty-four debug resource samples peaked at 61,659 KiB PSS and 39,651,312 bytes native heap; the maximum successful operation took 175 ms. This is measured evidence plus a sampled process kill guard, not a hard OS memory limit. The unsuccessful `RLIMIT_DATA` experiment was removed because it destabilized ART and caused four timeouts. No adversarial object-graph/decompression stress, crash injection, API23/ARM64/release runtime, or end-to-end import test is established. G-PDF/G-PASS remain BLOCKED; import remains disabled.

Final exact-source R3 rerun: 13/13 tests, zero failures/errors/skips, timestamp `2026-10-09T20:15:06Z`; 24 metrics samples peaked at 66,897 KiB PSS, 39,652,352 bytes native heap, and 171 ms. Retained files: `test-results/ANDROID_PdfSubjectIsolatedServiceTest_R3.xml` and `test-results/ANDROID_PdfSubjectMemoryProfile_R3.txt`. See `I10_FINAL_UNBLOCK_EVIDENCE.md`; gate status remains BLOCKED.
