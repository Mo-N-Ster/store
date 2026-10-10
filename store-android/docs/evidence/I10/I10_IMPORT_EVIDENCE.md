# I10 catalog import evidence — current: PASS_WITH_LIMITATIONS

Current disposition (2026-10-10): the production CatalogScreen import flow is
enabled after the constrained parser and authorization gates, and the complete
synthetic picker-to-atomic-Room path is retained in the final 6/6 UI report.
The parser-independent and disabled-UI statements below are historical and
superseded by the final acceptance section at the end of this document.

Review date: 2026-10-09. This status follows [I10_PARSER_QUALIFICATION.md](I10_PARSER_QUALIFICATION.md) and the governance review in [ADR10_R1_REVIEW.md](ADR10_R1_REVIEW.md).

## Workflow state

External PDF import remains disabled: there is no PDF reader, SAF import path, parser isolation, or application/UI entry point. The Subject codec remains pure and unauthenticated. A parser-independent import authority and atomic Room batch port now exist as unexposed foundations; they do not make PDF import available.

The contractual sequence remains:

`SAF selection → bounded read-only PDF → isolated Subject extraction → strict STORE_DATA_V1 codec → preview → current-right/session revalidation → full catalog/domain validation → one authorized atomic Room transaction → result`.

Completed without parser integration: immutable API request/preview/result contracts; pure validation of names, categories, duplicate keys, exact nonnegative prices and integer quantities; one volatile session-bound preview; current `PRODUCTS:UPDATE` checks at preview and confirmation; duplicate recheck and validation inside the authorized owner write transaction; insert-only batch persistence for products, initial stock movements, price history, and low-stock alerts. No PDF/SAF/app composition reaches this service. Preview sets `authenticityVerified=false`; no authority is derived from the payload.

## Executed focused evidence

| Suite | Result | Retained evidence |
|---|---:|---|
| `:verifyArchitecture` | PASS, seven-module allowlist | Gradle output (no XML) |
| `:testing:test --tests com.vibe.store.testing.CatalogImportPolicyTest --rerun-tasks` | 5/5 PASS | `test-results/JVM_CatalogImportPolicyTest.xml` |
| `:infrastructure:compileDebugAndroidTestKotlin` | PASS | Compile-only result; no XML |
| `:infrastructure:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.infrastructure.persistence.CatalogImportNativeTest` | 4/4 PASS on `STORE_API36_Main(AVD) - 16`, API 36 | `test-results/ANDROID_CatalogImportNativeTest.xml` |

The JVM tests cover catalog field normalization and exact decimal preservation, duplicate handling, invalid price/quantity, required fields, bounded request rows/fields, and defensive copying. The Room tests cover exact decimal import plus initial stock/price/alert history; invalid, in-file duplicate, and existing duplicate inputs without partial effects; account/session change rejection; and rollback after an injected mid-batch repository failure. The rollback assertion checks products, stock movements, price history, and alerts remain absent after failure. The emulator was verified as API 36; tests use isolated synthetic databases and synthetic identities.

Two diagnostic runs failed due to test assumptions, then were corrected: the first expected a database-duplicate diagnostic to be reported alongside an in-file duplicate; the authority now reports both independent errors. The next expected an initial movement for an existing fixture whose starting stock was zero; the assertion now checks zero baseline movements. Raw reports are retained separately:

- `test-results/DIAGNOSTIC_CatalogImportNativeTest_existing-duplicate-assertion.xml`
- `test-results/DIAGNOSTIC_CatalogImportNativeTest_baseline-movement-assertion.xml`

Only the final 4/4 report is passing evidence. No test was suppressed.

## Remaining NOT TESTED requirements

- PDF Subject extraction for the synthetic 2.0.1 PDF and canonical V1 PDF.
- Missing Subject, unsupported prefix/version as PDF metadata, malformed/truncated/encrypted PDF, excessive input/metadata, timeout, and memory/resource exhaustion.
- SAF import cancellation/revocation; parser process isolation and process-death behavior.
- Full end-to-end current-right validation from imported PDF through commit.
- Process interruption during import and contractually required import idempotency.

The 8/8 `StructuredCatalogCodecTest` in `I10_A_EVIDENCE.md` tests codec-only synthetic Subject inputs, not PDF transport. I10-B SAF tests cover export and selected-provider write/read, not import.

## Acceptance closure — 2026-10-10

The workflow is now user-reachable after qualification. The production default shows the action only when the existing service and picker are composed and effective `PRODUCTS:UPDATE` is present. The full synthetic real-picker flow is retained in `ANDROID_CatalogImportQualifyingUi_API36_x86_64_R2.xml` (4/4 at that checkpoint) and the later combined final rerun `ANDROID_I10_SafFailuresAndImportUi_API36_x86_64_R1.xml` (6/6). The latter also verifies the disabled-by-permission boundary, confirmation denial, synthetic revoked-read failure, synthetic ENOSPC failure/closure, and includes the full DocumentsUI→PDFium→codec→preview→confirmation→Room scenario.

Room authorization, session replacement, duplicate/invalid input, and transaction rollback remain independently covered by `ANDROID_CatalogImportNativeTest_API36_x86_64_R3.xml` and API23 R3, each 8/8. The UI/import source never writes before confirmation; the final authority still revalidates current session/rights and validates the complete batch inside the existing atomic transaction. The payload remains unauthenticated and never grants authority.

Physical storage exhaustion and a real external provider revoking an issued read grant were not induced; synthetic `ENOSPC` and `SecurityException` cover safe failure handling without destructive device operations. These are explicit environment/provider limitations, not claims of physical-provider qualification.

## Persistence and security disposition

There is no user-reachable import call path. The internal authority performs current permission/session checks, binds a volatile preview to actor and generation, and uses one existing owner write transaction for final duplicate recheck and batch catalog persistence. Focused synthetic tests demonstrate rollback and no partial catalog/stock/history/alert effects for the covered failure. No schema change was made. Parser isolation and all parser-originated rejection paths remain absent and untested.

**Gate:** I10 G-PASS and G-PDF remain **BLOCKED**. ADR10 has been reopened narrowly by the user for parser evaluation; no candidate is qualified or approved. The focused tests above validate only the parser-independent foundation. Do not add a PDF picker or expose a preview until parser qualification, isolation/resource controls, and complete end-to-end gates pass.

## ADR10-R2 status

The direct upstream PDFium 156.0.8076.0 binary is the preferred candidate for additional security qualification only. No parser or dependency was integrated. Current-right checks, preview, validation and transactional batch persistence remain unconnected to PDF/SAF, so there is no external import path and no import acceptance claim. Details, hashes, advisory caveats and missing qualification evidence are in `I10_PARSER_QUALIFICATION.md` and the R2 addendum in `ADR10_R1_REVIEW.md`.

## Parser smoke update — not import evidence

A temporary NDK harness called the PDFium metadata API on the synthetic API 36 x86_64 emulator. It returned the expected UTF-16LE Subject from a synthetic 588-byte PDF, returned an empty Subject for missing metadata, rejected a 5,000-character Subject at the harness's 4,096-byte output bound, rejected malformed and truncated PDFs with PDFium error 3, and rejected a 1,100,588-byte input before parsing at a 1-MiB input cap. This was superseded by the Android JNI/service suite below; the old harness did not call SAF or Room and is not import evidence.

## 2026-10-09 isolated parser prototype update

The Android JNI/service passed 11/11 synthetic API36 x86_64 cases and hands extracted metadata through the strict `StoreDataV1Codec` in instrumentation tests, including the repository's synthetic 2.0.1 PDF fixture and exact decimal preservation. See `I10_PDFIUM_INTEGRATION_EVIDENCE.md` and retained XML `test-results/ANDROID_PdfSubjectIsolatedServiceTest.xml`.

This is **not import evidence**: no production SAF import caller exists; parser output is not handed to `CatalogImportAuthority`; there is no current-right/session revalidation from an imported document; and no end-to-end PDF-to-atomic-Room test was run. Native memory exhaustion remains unqualified. User import stays disabled; I10 G-PDF/G-PASS remain BLOCKED.

## 2026-10-09 integrated-flow update

The latest source now wires the bounded SAF `OpenDocument` callback, copied bounded input, isolated `AndroidPdfSubjectExtractor`, strict `StoreDataV1Codec`, session-bound preview, and the existing authorized Room batch transaction. The catalog import button remains deliberately disabled by `CATALOG_PDF_IMPORT_UI_ENABLED = false`; therefore this is an internal qualification path, not an enabled user feature. `CatalogImportAuthority` rechecks `PRODUCTS:UPDATE` before parsing, after parsing, and at confirmation, binds the preview to actor/session generation/currency, and invalidates older preview tokens when a newer preview starts.

The final synthetic Room suite now includes a real bundled 2.0.1 PDF passed through PDFium's isolated Android service, codec, preview, confirmation, and Room persistence. It asserts no mutations before confirmation, exact legacy values/stock/history after confirmation, referential integrity, and duplicate replay rejection. A second test confirms that discarding the extracted preview makes confirmation fail with no products, stock movements, or price rows.

| Current final result | API 36 x86_64 | API 23 x86 |
|---|---:|---:|
| `PdfSubjectIsolatedServiceTest` | 16/16, 0 failures/errors/skips | 16/16, 0 failures/errors/skips |
| `CatalogImportNativeTest` | 8/8, 0 failures/errors/skips | 8/8, 0 failures/errors/skips |

Retained parser reports: `test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R3.xml` and `..._API23_x86_R3.xml`; the nested/cyclic structure regression is included. Room reports: `test-results/ANDROID_CatalogImportNativeTest_API36_x86_64_R3.xml` and `..._API23_x86_R3.xml`. The latest offline architecture/JVM/Android compile/minified release build run passed; JVM XML totals 92/92 across 14 suites, zero failures/errors/skips. The API23 AVD is synthetic and disposable. The integration fixture does not exercise the actual Android system picker, app UI confirmation flow, process death between preview/confirmation, ARM64 runtime, or minified PDFium service runtime.

I10 remains **BLOCKED**: G-PDF lacks hostile object-graph/decompression resource qualification, native crash injection, ARM64 runtime, and minified parser-service runtime evidence. The 256 MiB PSS monitor is sampled, not a hard native-memory ceiling. The user-facing import button remains disabled. No acceptance commit or I11 progression is allowed.

Focused UI regression run after the current CatalogScreen/MainActivity changes: `CatalogUiTest` 3/3, `CatalogPresentationUiTest` 2/2, `CatalogFormUiTest` 10/10, and `DocumentExportUiTest` 5/5 on API36 x86_64; combined XML `test-results/ANDROID_CatalogAndExportUi_API36_R1.xml` records 20/20 with zero failures/errors/skips. The document export test uses the system SAF picker with synthetic content and deletes its selected-provider fixture. No import picker UI test was run because the production import action remains intentionally disabled pending security gates.
# Continuous recovery update — 2026-10-10

The real DocumentsUI provider path is now exercised by `CatalogImportDocumentsUiTest`: synthetic PDF creation with CreateDocument, selection with OpenDocument, the production bounded `MainActivity.readBoundedPdf` reader, isolated PDFium Subject extraction, and exact canonical payload verification. API 36 x86_64 result **1/1**, XML `test-results/ANDROID_CatalogImportDocumentsUiTest_API36_x86_64_R1.xml`. The test intentionally does not use or mutate the application database; `CatalogImportNativeTest` separately verifies authority, confirmation, exact persistence and transaction rollback. The product import action remains disabled pending all I10 acceptance gates.

## UI and adversarial follow-up — 2026-10-10

`CatalogScreen` has a `catalogPdfImportEnabled` composition parameter whose default is the existing `false` gate; the app composition leaves the default intact. A Compose test sets the parameter only in its synthetic harness and passes **3/3** on API 36 x86_64. It covers hidden-by-default/permission-denied action visibility, preview display and exact significant decimal text, cancellation/discard without confirmation, and confirmation denial with no success state. XML: `test-results/ANDROID_CatalogImportWorkflowUiTest_API36_x86_64_R2.xml`. The test service is fake; it is not represented as a production UI-to-Room integration test.

The parser suite passes **19/19** on API 36 x86_64, including nested metadata depth 2,048, an indirect-object/reference graph of 4,096 nodes, the existing 32 MiB expanded Flate stream, process recovery after complex inputs, timeout, concurrency and the bounded-input/output cases. XML: `test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R5.xml`. Per-request debug observations in this run peaked at 67,174 KiB sampled PSS, 39,652,880 bytes native heap and 174 ms elapsed. These are sampled results for the synthetic emulator workload, not a hard memory limit or general hostile-PDF qualification.

The final debug UI source is still default-disabled; the synthetic switch is not supplied by production composition. Room authorization/atomicity and real DocumentsUI picker selection/parser transport are covered by separate tests, not one combined production workflow. I10 remains **BLOCKED** pending mandatory broader parser/resource qualification and required runtime compatibility evidence; no user-facing import or acceptance commit is enabled.
