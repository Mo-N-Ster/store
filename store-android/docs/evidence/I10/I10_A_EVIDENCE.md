# I10-A — Secure PDF / Structured Catalog Foundation

## Scope and implementation

Implemented the I10-A foundation only. No renderer, PDF parser, SAF workflow, catalog persistence/import use case, Room change, business-data mutation, or dependency was added.

- `application-api/.../DocumentContracts.kt` defines `DocumentType` (invoice, report, catalog, help), immutable request/result snapshots, and defensive-copy bounded document bytes. Reports reuse the accepted `DocumentModel`; invoice/catalog snapshots reuse existing API DTOs. The file-name field is a safe display name, not a path.
- `application/.../catalog/StructuredCatalogCodec.kt` defines the inward port for encoding and producing an explicitly structural-only import preview. It receives Subject text only; it has no PDF, Android, database, session, filesystem, or network dependency.
- `domain/.../StructuredCatalog.kt` defines immutable V1 catalog values and the pure deterministic codec. The import preview explicitly sets `authenticityVerified=false`; a structurally valid payload remains untrusted and has not passed business validation.
- `testing/.../StructuredCatalogCodecTest.kt` covers canonical round-trip, exact decimal scale/value, Unicode and FCFA-safe content, legacy property ordering, the real synthetic 2.0.1 Subject, malformed Base64/UTF-8/JSON, unsupported versions, unknown and duplicate fields, structural bounds, defensive input copies, and valid tampering remaining unauthenticated.
- `testing/.../synthetic-2.0.1-subject.txt` contains the Subject extracted from the repository's synthetic PDF fixture at `spikes/android/native-validation/app/src/main/assets/synthetic-2.0.1-envelope.pdf`; the PDF itself remains unchanged.

## STORE_DATA_V1 codec contract

Subject grammar is `STORE_DATA_V1:` followed by canonical padded standard Base64 of strict UTF-8 JSON. Canonical output has ordered root fields `kind`, `version`, `products`; `kind` is `stocks`, `version` is integer `1`, and each product has ordered fields `name`, `hashtag`, `category`, `description`, `price`, `stockQuantity`, `minStockThreshold`. Legacy input property order is accepted, but unknown/duplicate keys, missing/wrongly typed fields, malformed JSON, unsupported version, noncanonical Base64, malformed UTF-8 and numeric exponents are rejected. Product order is retained.

Prices parse directly from decimal text into `BigDecimal`; output uses `toPlainString()` after checking the output size, preserving decimal value and scale without a `Double` conversion, rounding, or truncation. Stock fields are exact `Long` integer tokens. The codec deliberately performs structural parsing only: it does not certify names, prices, quantities, duplicates in the live catalog, permissions, or persistence safety. V1 has no product identifier field, so duplicate identifier validation is not applicable at this format layer; repeated JSON property names are rejected.

Resource caps implemented for this parser are 2,000,000 decoded payload bytes, 2,666,668 Base64 characters, 10,000 products, 64,000 UTF-16 characters per string, 256 characters per numeric token, and a 25,000,000-byte document input ceiling. The first three and the string cap are derived from SP-02's prototype controls; the document cap matches the spike's PDF input cap. These are provisional technical safeguards, **not approved business/document policy**, and must be reviewed before a production workflow is enabled. The 256-character numeric-token cap is a new parser resource bound, likewise provisional. The codec does not parse PDF bytes.

## PDFBox-Android dependency gate

No PDFBox dependency was added. Existing evidence establishes that `2.0.27.0` worked in the isolated SP-02 API 36 x86_64 harness and that the maintainer documents Apache-2.0 and API 19+ (`spikes/android/native-validation/README.md`; `docs/STORE_3_ARCHITECTURE_SPIKES.md`, SP-02). The checked-in spike records a synthetic fixture and successful Subject round-trip/rejection assertions.

The available local evidence does **not** establish current maintenance/advisory status, a complete transitive license/NOTICE inventory, production dependency footprint, ARM64 behavior, encrypted/excessive-metadata time and memory limits, parser isolation using a read-only descriptor, or release/minification compatibility. The artifact is not present in the local Gradle dependency cache. These items remain **UNVERIFIED**; the production PDF parser/import gate is **BLOCKED** pending qualification and must not be enabled on this evidence.

## Validation

Command from `store-android`:

```text
bash ./gradlew --offline :verifyArchitecture :testing:test --tests com.vibe.store.testing.StructuredCatalogCodecTest
```

Final result: `BUILD SUCCESSFUL`; `:verifyArchitecture` reported `ARCHITECTURE_PASS`; `StructuredCatalogCodecTest` XML reports 8 tests, 0 skipped, 0 failures, 0 errors, timestamp `2026-10-09T13:24:17.367Z`. XML: `store-android/testing/build/test-results/test/TEST-com.vibe.store.testing.StructuredCatalogCodecTest.xml` (generated report, not added to the evidence change). No unrelated Android suites were run. `git diff --check` passed; index remained empty.

## Remaining work / limitations

I10-A does not satisfy G-PDF or authorize I10-B. Before external PDF input can be enabled, qualify the dependency and isolated parser budgets, then implement actual PDF Subject transport, document rendering/export, current-rights authorization, full catalog business validation and atomic authorized persistence in later I10 work. Import must remain non-persistent until those gates pass. No authenticity is provided by V1; structurally valid edits are intentionally accepted as untrusted previews.
