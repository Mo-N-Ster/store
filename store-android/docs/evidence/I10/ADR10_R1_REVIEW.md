# ADR10-R1 — Secure PDF import architecture review

Date: 2026-10-09<br>
Repository: `feature/store-i10`, base HEAD `65cac9345a1762874b50a427487b69335b7cc40a`<br>
Disposition: **REOPEN REQUEST PREPARED — explicit decision pending**

## Locked decision and governance

The I10 contract (§I10, lines 297–315) requires PDFBox-Android as the PDF `Subject` transport, forbids a home-grown parser or automatic replacement, and requires parser maintenance/dependency review plus isolated hostile-input and resource-limit proof before external import is enabled. Android architecture §17 and decision-table row 10 also mark this choice `LOCKED`. The stated failure path is to reopen ADR10, not silently substitute.

The contract's §13 `Architecture Reopen Procedure` says a locked ADR is reconsidered only with concrete evidence, the affected phase and dependent chain must stop, and an explicit decision is required before modifying ADR/contract. This document prepares that request but does not approve it. I10 parser integration and dependent I11–I14 remain gated.

## Evidence prompting reopening

`I10_PARSER_QUALIFICATION.md` records the official Maven Central candidate `com.tom-roush:pdfbox-android:2.0.27.0`, artifact size/hash and its POM. The POM pins Bouncy Castle `bcprov`, `bcpkix`, and `bcutil` 1.72. Official project notices identify vulnerabilities affecting versions in that range. The intended metadata-only path has not been shown to avoid vulnerable behavior under hostile input. The latest stable upstream release observed is January 2023; no project security policy was found. Minification, ARM64, installed footprint, encrypted/malformed input, parser time/memory limits, and isolated process behavior remain unverified. No override or mitigation has been tested. This evidence meets the contract's reopening trigger; it does **not** prove exploitability of the intended call path.

## Option comparison

| Option | Provenance, maintenance, security | Compatibility and behavior | Isolation and bounds | Assessment |
|---|---|---|---|---|
| A. Retain PDFBox-Android 2.0.27.0 and remediate its dependency graph | Exact artifact provenance/hash are recorded in `I10_PARSER_QUALIFICATION.md`. Old stable release; BC 1.72 has relevant published advisories. Apache-2.0 identified; complete transitive notice/SBOM is absent. | Upstream documents API 19+ (STORE min API 23). Actual ARM64 and R8/release behavior untested. SP-02 shows functional Subject/2.0.1 compatibility for the spike only. | No isolated process, timeout/termination, hostile input, metadata bound or resource-exhaustion proof. BC override has not been tested for compatibility. | **Not qualified.** Investigate only after ADR decision; require exact patched graph compatibility and isolation/resource evidence. |
| B. Maintained Android-compatible parser/fork | SAMBox (`torakiki/sambox`) is an active Apache-2.0 PDFBox fork intended for Sejda/PDFsam, but its README describes a fork from a PDFBox 2.0.0 snapshot and says upstream alignment is challenging. Its README requires JDK 17; no Android release/artifact or Android-specific advisory disposition was established. | Android min API, ARM64, R8, Subject fidelity and synthetic 2.0.1 compatibility are unknown. | No Android isolated-process or resource tests. | **Not selectable as Android candidate.** Maintainer activity alone does not demonstrate Android suitability/security. |
| C. Independently maintained metadata-only Subject extractor | No such qualified component is identified in local evidence/cache. A homemade general PDF parser is expressly forbidden. | Provenance, license, Subject fidelity, format compatibility and Android compatibility unknown. | Isolation and resource behavior unknown. | **Unassessed / not selectable.** Do not substitute custom parsing. |

Local Gradle/Maven caches contained no alternate parser artifact. A bounded review of official GitHub project pages identified SAMBox and a PDFBox-Android compatibility fork; neither furnished a qualified Android metadata-only artifact. SAMBox's project states its intended desktop processor use, Apache-2.0 license, JDK 17 requirement, and that its fork diverged from PDFBox such that merging current upstream is challenging ([project README](https://github.com/torakiki/sambox)). The existing PDFBox-Android release page lists 2.0.27.0 as latest and dates it 2023-01-02 ([official releases](https://github.com/TomRoush/PdfBox-Android/releases)). No dependency artifact was retrieved or added. This bounded review does not establish that no other candidate exists.

## Requested minimum ADR decision

```text
ARCHITECTURE REOPEN REQUEST
- decision: ADR10 / Android architecture decision 10 Documents
- evidence: I10_PARSER_QUALIFICATION.md; exact 2.0.27.0 POM pins Bouncy Castle 1.72;
  relevant official advisories exist; maintenance and isolated hostile-input/resource
  qualification remain insufficient. Exploitability on the intended call path is unproven.
- affected requirement: I10 parser maintenance/advisory review, isolated Subject-only
  extraction, and bounded hostile-input handling before import activation
- affected phase: I10; dependent I11, I12, I13, I14 remain gated
- minimum decision requiring reconsideration: authorize a bounded evaluation of either
  (1) retaining PDFBox-Android with a specifically reviewed compatible patched dependency
  graph and demonstrated isolated-process/resource controls, or (2) evaluating a named,
  maintained metadata-only replacement against the same security/compatibility gates.
  This request alone does not authorize import activation or waive any gate.
```

**Human decision required:** authorize reopening ADR10 for this limited evaluation, or direct retention of the locked candidate with an acceptable security-disposition path. Until then, no parser dependency or alternative will be integrated and PDF import stays disabled.

## Independent work completed without parser coupling

A parser-independent preview/validation and transactional batch-import foundation was added. It is not composed into the app, has no PDF/SAF entry point, and does not enable external import. It uses current `PRODUCTS:UPDATE` checks at preview and confirmation, binds a volatile preview to actor/session generation/currency, revalidates duplicates and domain rules inside the authorized write transaction, inserts records and initial catalog history in one owner transaction, and explicitly reports `authenticityVerified=false`. Focused evidence is in `I10_IMPORT_EVIDENCE.md`. This does not satisfy G-PASS or G-PDF.

## Decision

**ADR10 unchanged. No option selected. I10 remains blocked; no commit is ready.** Do not expose import to users before parser qualification and all import gates pass.

## ADR10-R2 authorized reopening addendum — 2026-10-09

The user authorized reconsideration of the parser implementation only. This reopens evaluation; it does not amend the contractual security requirements. PDF import remains disabled unless dependency provenance, maintenance/advisory review, isolated hostile-input behavior, resource limits, compatibility, authorization and atomic import all pass.

### Options and decision

| Candidate | Review result | Decision |
|---|---|---|
| PDFBox-Android 2.0.27.0 with BC dependency remediation | Published artifact is old (2023-01-02) and pins Bouncy Castle 1.72 modules with relevant official advisories. No patched graph compatibility, call-path reachability proof, isolated parser, or resource tests exist. | Not qualified; no override. |
| SAMBox / pCloud `pdfium-core` 2.1.0 | SAMBox has no qualified Android artifact. pCloud exposes Subject metadata but its inspected build targets minSdk 24 (STORE minSdk 23); the claimed Maven Central coordinate returned 404, and release/R8/security/fixture behavior is unverified. | Not qualified as a drop-in. |
| Official PDFium binary release `chromium/8076` (156.0.8076.0) with a minimal Subject-only JNI binding | Official Android archives' published hashes matched local SHA-256. The upstream ref resolved to `8ca5b735df4263f43c830479b78213854a392c22` (2026-09-25). Sigstore bundle verification passes offline for the official GitHub workflow identity and artifact digest, but the SLSA predicate does not bind the PDFium source commit as a material. Build args specify Android min SDK 23; four ABIs are present; `FPDF_GetMetaText` is exported; ARM64 ELF segments have 16-KiB alignment. Archive includes third-party license texts. | Best candidate for *continued qualification*, not production selection. CVE-2026-6305/6306 and 5889 fixes were mapped to source commits in the candidate branch history; the CVE-2026-95338 fix is also present. October Chrome 106421/106204 fixes are in Chromium's PDF wrapper rather than standalone PDFium. JNI, process isolation, hard resource bounds, notices, release/R8 and end-to-end tests remain open. |

### Security disposition

The source review mapped advisories to source commits: CVE-2026-5889 / issue 486906037 to PDFium commit `9c97da0` (stronger IV for `PDF_CryptoHandler::EncryptContent`); CVE-2026-6305 / issue 496618639 to `237bc015b08696881bf0a2007bec95d0aecf8e67` (Little CMS overflow); CVE-2026-6306 / issue 496907110 to the official Chromium PDFium autoroll ending at `ca8a943c247c208fd7a9cd21b4de049f22b93070` (libtiff overflow); CVE-2026-4455 to `1765e514...` and `ee83ca8...`; CVE-2026-95338 / issue 556535630 to `4a366282...`. The cited 6305, 6306, 4455 and 95338 fixes were checked as ancestors of `chromium/8076`; the September source snapshot postdates the mapped 5889 fix. This establishes inclusion of these named fixes only, not general parser safety.

The October CVE-2026-106421 and CVE-2026-106204 fixes reviewed change Chromium's PDFium integration wrappers (`pdfium_engine.cc`, `pdfium_page.cc`), not standalone PDFium. STORE's planned calls are limited to memory-document loading and Subject metadata; this scope distinction is not proof that other core parsing paths are safe. Malformed PDFs still exercise core parsing. See the [March 18 release](https://chromereleases.googleblog.com/2026/03/stable-channel-update-for-desktop_18.html), [April 7 release](https://chromereleases.googleblog.com/2026/04/stable-channel-update-for-desktop.html), [April 15 release](https://chromereleases.googleblog.com/2026/04/stable-channel-update-for-desktop_15.html), [September 22 release](https://chromereleases.googleblog.com/2026/09/stable-channel-update-for-desktop_0856730748.html), and [October 6 release](https://chromereleases.googleblog.com/2026/10/stable-channel-update-for-desktop_086471744.html).

The candidate still needs a thin native Subject-only binding and platform isolated-process service. Neither exists. A temporary NDK smoke harness was run on the synthetic API 36 x86_64 emulator: a 588-byte PDF yielded the expected 148-byte UTF-16LE Subject; missing Subject yielded empty UTF-16; a 5,000-character Subject exceeded and was rejected by the harness's 4,096-byte output cap; malformed 18-byte and truncated 100-byte files returned PDFium error 3; a 1,100,588-byte input was rejected before parsing by the harness's 1-MiB input cap. This is smoke evidence only, not production parser/JNI/isolation qualification and has no retained XML.

The official Sigstore bundle was verified offline against GitHub workflow identity `bblanchon/pdfium-binaries/.github/workflows/build-all.yml@refs/heads/master`, repository/ref/workflow commit and ARM64 digest; verifier returned `OK` including transparency proof. The attestation binds artifacts to the publisher workflow but omits PDFium source commit `8ca5...`, so source-to-binary reproducibility remains unverified. NDK `27.2.12479018` (Clang 18.0.3) and CMake `3.22.1` are installed. There is no Binder confinement test, enforceable native memory bound, timeout/cancellation or crash recovery test, encrypted hostile-input test, ARM64 runtime, release/R8 integration, STORE_DATA_V1 decode, or actual 2.0.1 PDF compatibility test. No candidate is approved; PDF import remains disabled.

A reproducible source build was attempted with official `depot_tools` and the pinned PDFium revision, but `gclient sync` did not complete its dependencies: test corpus, Skia, V8, instrumentation and toolchain/sysroot checkouts continued. The attempt was interrupted before a complete source tree or build. It is **NOT COMPLETED**, not a build failure. Exact command and state are in `I10_PARSER_QUALIFICATION.md`.

### Decision

ADR10 is reopened for bounded parser evaluation. No candidate presently satisfies all mandatory gates. The direct PDFium candidate above is only the preferred qualification target; it is not an approved production dependency. No contract, security, compatibility or functional requirement is waived. I10 G-PDF and G-PASS remain **BLOCKED**, G-EVIDENCE is **PASS_WITH_LIMITATIONS** only for existing codec/export and parser-independent import foundation evidence, and G-COMMIT is **NOT READY**. Do not start I11–I14 until I10 is accepted.

## Experimental implementation status — 2026-10-09

The direct PDFium candidate has since been integrated experimentally as a Subject-only JNI adapter and private Android isolated-process service. It is **not approved for production**. The service passes the retained 11/11 API36 x86_64 synthetic test suite, and debug/release builds package all four ABIs with notices; only x86_64 debug runtime was executed. The attested publisher workflow and archive digests are verified, but the bundle still omits the exact PDFium source commit and a source build was not completed.

## ADR10 final resolution — 2026-10-10

**Decision: select the verified official PDFium Android binary release for the narrow Subject-only import adapter, with the existing `isolatedProcess` boundary and bounded protocol.** This resolves the authorized, narrow ADR10 reopening; it does not replace PDFium's role with general page parsing and does not waive STORE_DATA_V1 validation, current authorization, preview confirmation, or single-transaction persistence.

Evidence now includes publisher workflow/digest verification and named advisory-fix review in `I10_PARSER_QUALIFICATION.md`; bundled license notices; four ABI builds; API 23 x86 and API 36 x86_64 parser runs; 19 adversarial/compatibility parser cases on API 36; an API 23 parser regression; the real DocumentsUI-to-atomic-Room synthetic flow; and a minified Release runtime check that binds to the actual isolated service and extracts the exact Subject through JNI. The parser accepts only a read-only descriptor and returns bounded Subject metadata/failure, has no database/session/network authority, admits one request, applies an input/output cap, terminates itself on deadline or sampled PSS budget, and is exercised with malformed, encrypted, nested, cyclic, indirect-reference, compressed-stream and timeout/recovery fixtures.

The contract requires demonstrated confinement and time/memory/resource budgets; it does not specify a hard kernel memory ceiling or a reproducible source build as separate I10 gates. The 256 MiB PSS guard samples every 25 ms and is **not** a hard native-memory ceiling. The official attestation binds the publisher workflow and artifact digest but not the exact source revision; a reproducible source build remains incomplete. These are disclosed residual supply-chain/resource limitations, not claims of universal PDF safety. ARM64 runtime and 16-KiB device qualification belong to the integrated I13 gate, and final physical-device/release qualification belongs to I14; neither is represented as passed here.

ADR10 is therefore **RESOLVED for I10's constrained Subject-only use**, with the named limitations retained for later gates. See `I10_FINAL_ACCEPTANCE.md` for current phase gate outcomes and the exact Release instrumentation record.

The I10 integration and minified Release runtime have now been exercised as
recorded in `I10_PDFIUM_INTEGRATION_EVIDENCE.md` and
`I10_FINAL_ACCEPTANCE.md`. The resolution approves only this metadata-only
adapter in the isolated process; it does not approve page parsing, a general
PDF import facility, or an assertion of universal safety. The sampled memory
guard remains distinct from a hard kernel limit, and the artifact attestation
source-revision gap remains disclosed.

The input/output caps, one-request gate and five-second process deadline are demonstrated. PDFium internal native memory remains without an enforceable or measured ceiling; hostile resource-exhaustion coverage, ARM64/API23/minified-runtime tests, and the SAF → parser → codec → current-rights → atomic Room end-to-end flow remain absent. Therefore the architecture decision is only a qualified **experimental candidate**, not final parser selection: G-PDF/G-PASS BLOCKED; import remains disabled; no I10 commit. Details: `I10_PDFIUM_INTEGRATION_EVIDENCE.md`.
