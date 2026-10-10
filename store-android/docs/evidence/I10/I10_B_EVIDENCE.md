# I10-B — Native PDF Rendering and SAF Export Evidence

## Result and scope

Implemented native PDF rendering for invoice, report, catalog, and offline help snapshots using Android `PdfDocument`; export uses the Android `ACTION_CREATE_DOCUMENT` contract with `application/pdf`. The renderer consumes immutable service snapshots only. It does not write business data, access Room, or perform printing, sharing, email, or PDF import. No dependency, Room schema, or migration was added. I10-A changes and evidence remain intact.

The report source is the authorized `DocumentModel`, including its report identity, applied filters, local-date interval, time zone, dimensions, currency availability, metrics, rows, and series. Invoice export re-reads the receipt through `SaleService.receipt`; report export requests a fresh authorized `ReportingService.snapshot`; catalog export obtains one `PRODUCTS:READ` snapshot, including configured currency, under the existing authorized read lease. Help export uses the selected offline help article. PDF creation itself is a pure rendering operation over those snapshots.

## Renderer and bounds

- A4 page geometry: 595 × 842 points, white background independent of app theme, stable margins, document header, request timestamp, and page-number footer.
- Long paragraphs wrap; tables repeat headers on continuation pages; numeric cells are right aligned. Table text uses explicit readable sizes independent of preceding titles. Labels are rendered in English or French. Monetary values use exact `BigDecimal` plain decimal text with the selected language's decimal separator; the renderer does not round or convert money through binary floating point. XOF/FCFA text is included in synthetic fixtures.
- Technical ceilings: 10,000 detail rows, 10,000 catalog products per authorized snapshot, 8 columns, 200 pages, 64,000 characters per text field, and 25,000,000 output bytes. Oversized input fails closed. These are renderer resource safeguards, not business limits.
- Report PDFs retain the half-open applied date interval and time zone. They disclose bounded displayed rows versus complete filtered metrics, unavailable currency without substituting zero, estimated margin method, and that estimated margin is not certified profit. Product price history is labeled as recorded observations only and bounded.
- A pixel-level instrumentation check rasterized every page and checked the white background, visible content, and page margins. It found a Help timestamp overflow caused by measuring and drawing with different font sizes; the renderer was corrected. Manual contact-sheet review then found the catalog header `Catégorie` breaking at the final letter; explicit table font sizes fixed it. Final renders were reviewed: all 44 pages show consistent margins, no clipping/overlap, readable repeated headers, and visible currency/disclosures. The full page PNG set and contact sheets are retained below.

## Synthetic PDF inventory

Final renderer log: [`test-results/ANDROID_DocumentRendererNativeTest.log`](test-results/ANDROID_DocumentRendererNativeTest.log). SHA-256 is over the exact generated PDF byte array; page counts are from Android `PdfRenderer`. The inspectable 44-page raster archive is [`rendered-pages/I10_B_pdf_pages_api36.zip`](rendered-pages/I10_B_pdf_pages_api36.zip), SHA-256 `0b43b84f81d08096f80ffd9ef6228d7a80ef9ce6c4b758547033bfbcf4ffa564` (2,720,480 bytes). Contact sheets for invoice, report, catalog and help are retained in `rendered-pages/contact-sheets/`.

| Type | Synthetic content | Pages | Bytes | SHA-256 |
|---|---|---:|---:|---|
| Invoice | 90 lines, French, accented text, XOF, exact decimal amounts | 4 | 70,368 | `5d7e8912c08aaf5e4076686a0c0cd7850786af15f5e2284f41b07259e2aeffa6` |
| Report | 200 detail rows, 90 series points, filter/date/time-zone metadata, metrics and disclosures | 11 | 90,124 | `18bc6539a45541eb8243e801f600e341da56c15c3a1d4f5a44fd495a1a81c67c` |
| Catalog | 90 products, descriptions, French, FCFA | 5 | 60,045 | `d10051f1bcbbbabe801cb0cf79651e57263c2db23365b4f20948630e6404ae83` |
| Help | 1,000 repeated synthetic long-text lines, French, FCFA | 24 | 75,414 | `e836dd5e383daff139f3e2be52ea58c4cb55ea5d1ad700083842b3765c4d1588` |

The renderer also rejects an invoice with 10,001 synthetic lines before creating its render-row projection. This exercises the row guard without a large PDF allocation.

## SAF, permission, and cancellation behavior

The app launcher requests `application/pdf`, uses `ActivityResultContracts.CreateDocument`, and writes only to the user-selected `Uri` through `ContentResolver`; no arbitrary path is accepted. Picker cancellation becomes a visible cancelled result. A failed destination stream becomes a retryable error. Pending bytes are invalidated on logout/account change. `CancellationException` is propagated from rendering and destination writing. A second pending export is rejected rather than replacing the first.

The source request is refreshed through the current authorized service before rendering: POS receipt through the sale authority, report through the reporting authority, catalog through `PRODUCTS:READ`. Authorization failures show an access/session message and do not invoke the save callback. The UI tests exercise denial, simulated cancellation, and simulated destination-write failure. They do **not** drive the operating-system document picker or a real storage provider end-to-end; that SAF platform integration remains a limitation.

## Test evidence

All copied XML is retained under [`test-results/`](test-results/), with the suite's original test cases and timestamps.

Commands executed from `store-android` (all `--offline`):

```text
bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :infrastructure:compileDebugKotlin :presentation:compileDebugKotlin :app:compileDebugKotlin :infrastructure:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin
bash ./gradlew --offline -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.infrastructure.documents.DocumentRendererNativeTest :infrastructure:connectedDebugAndroidTest
bash ./gradlew --offline -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.DocumentExportUiTest :app:connectedDebugAndroidTest
```

The architecture/JVM/compile bundle preceded the final renderer typography adjustment; the JVM module sources were unchanged afterward. The final infrastructure renderer test recompiled infrastructure and re-ran architecture, and the final app instrumentation run recompiled the changed Android-test source and app. No import implementation exists to validate.

- Offline JVM suite: `:testing:test --rerun-tasks` — 86 tests, 0 failures, 0 errors, 0 skipped. This includes 8 `StructuredCatalogCodecTest` cases. Per-class JVM XML files are retained.
- Architecture: `:verifyArchitecture` — PASS (seven modules and dependency allowlist).
- Production and Android-test Kotlin compilation for infrastructure, presentation, and app — PASS.
- Native renderer on `STORE_API36_Main(AVD)`, API 36: 2 tests, 0 failures/errors/skips, timestamp `2026-10-09T14:37:14`; [`ANDROID_DocumentRendererNativeTest.xml`](test-results/ANDROID_DocumentRendererNativeTest.xml). It covers all four PDF types, all 44 rasterized pages, exact English/French decimal strings, page content/background/margins, and oversized invoice rejection.
- SAF/export UI on `STORE_API36_Main(AVD)`, API 36: 5 tests, 0 failures/errors/skips, timestamp `2026-10-09T15:21:10`; [`ANDROID_DocumentExportUiTest.xml`](test-results/ANDROID_DocumentExportUiTest.xml). It covers actual DocumentsUI picker cancellation and successful Downloads-provider URI creation, synthetic stream write/read/delete, simulated destination failure/retry, and denial before save.
- Earlier harness diagnostic XMLs are retained with `DIAGNOSTIC_` names; they include an initialization error and the first provider-write/revocation experiments. They are not the accepted final results. Final passing XMLs and the matching hash log above are retained.

Commands were run offline. The API 36 emulator identity was verified before instrumentation. No profile reset or user data mutation was performed.

## Limitations / next gate

- A real storage-full condition was not induced because filling emulator storage would be unsafe; callback-level write failure is covered. App-side revocation of the temporary URI grant was attempted but did not revoke the DocumentsUI grant, so revoked-URI behavior remains unverified against a provider that revokes access.
- SAF success and cancellation were exercised on the emulator's DocumentsUI/Downloads provider with synthetic bytes. This does not cover every Android provider or manufacturer picker.
- PDF output is generated in memory, bounded by the stated page and byte ceilings; no streaming PDF writer is available through `PdfDocument`.
- External PDF parsing/import remains blocked by I10-A's dependency and parser qualification findings. This phase does not enable import or catalog persistence.

This evidence records I10-B implementation and validation only; it does not claim I10-B acceptance.
