# STORE autonomous delivery checkpoint

Date: 2026-10-09

## Repository state

- Repository: `~/Projects/STORE`
- Branch / HEAD: `feature/store-i10` / `65cac9345a1762874b50a427487b69335b7cc40a`
- Protected `main`: `3691eccedc9855686b011e34c7420f17aaf6c085`
- Index: empty
- Worktree: intentional I10 changes, all under `store-android`; latest exact inventory is recorded in the newest continuation below.
- I10 commit: none. No branch change, staging, commit, push, or merge.

## Current phase

I10 remains in progress. I09 is accepted and committed. Do not start I11–I14 until the I10 contractual gates pass.

## Existing implementation and evidence

- I10-A codec/contracts and I10-B native PDF renderer/SAF export exist.
- Catalog import policy, authority, bounded SAF read, isolated Subject extraction, strict codec, preview, confirmation and transactional Room batch operation are connected in source. The product UI action remains explicitly disabled pending acceptance gates.
- PDFium Subject-only JNI and an `isolatedProcess` Android service exist. The service receives a bounded read-only descriptor, accepts one operation at a time, returns bounded metadata via pipe, and has timeout plus sampled PSS watchdog controls.
- Latest verification: 92/92 JVM tests; architecture and debug/release builds pass. Parser instrumentation 16/16 and Room import instrumentation 8/8 pass on API 36 x86_64 and disposable API 23 x86. Catalog/export UI regression is 20/20 on API 36. A real DocumentsUI create/open + bounded read + isolated parser/codec test passes 1/1 on API 36; it does not confirm import through production UI or mutate the app database.
- Final parser evidence: `I10/test-results/ANDROID_PdfSubjectIsolatedServiceTest_R3.xml` and `ANDROID_PdfSubjectMemoryProfile_R3.txt` (24 operations; max observed PSS 66,897 KiB, native heap 39,652,352 bytes, operation 171 ms).
- API 23 evidence: `I10/test-results/ANDROID_PdfSubjectIsolatedServiceTest_API23_x86_R1.xml`; JVM suite XML is retained under `I10/test-results/JVM_API23Compatibility_R1/`. API 36 x86_64 rerun is `I10/test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R1.xml`.
- Four PDF outputs and rendered-page evidence are retained under `I10/rendered-pages/`.

## Security and acceptance status

- G-PASS: BLOCKED — production import UI remains disabled and no single UI-driven DocumentsUI→preview→confirm→Room test exists. Parser→codec→preview→Room is covered by synthetic native tests; real DocumentsUI selection and isolated parser/codec are covered separately.
- G-PDF: BLOCKED — broad decompression/resource campaign, native crash injection, ARM64 runtime and minified PDFium-service runtime remain unverified. PSS observation is sampled and is not a hard OS memory cap.
- G-EVIDENCE: PASS_WITH_LIMITATIONS — final parser XML and sampled measurements retained; unsupported dimensions are documented.
- G-COMMIT: NOT READY. No I10 acceptance commit while mandatory gates are blocked.
- Import remains unavailable to ordinary users.

## Known risks / limitations

- PDFium archive/build provenance does not bind the exact source revision; no reproducible source build was completed.
- Runtime qualification exists for API 23 x86 and API 36 x86_64; packaged ABI and release builds do not prove runtime compatibility on other ABIs or release runtime.
- Application authority/session revalidation, Room rollback, and real DocumentsUI selection are covered separately; the combined production UI path has not been exercised.
- A sampled process-memory guard can miss transient peaks; resource observations apply only to the recorded synthetic emulator workload.
- API 23 x86 parser runtime is tested. ARMv7 image was not bootable with installed QEMU2; Android 36 ARM64 image transfer was stopped before install, so ARM64 and minified PDFium-service runtime remain untested.
- A locally test-signed minified release package started successfully on the disposable API 23 AVD; this is app-startup evidence only, not PDFium service runtime evidence.
- API 23 testing found and fixed `java.util.Base64`, `Map.putIfAbsent`, and `AtomicLong.updateAndGet` incompatibilities. The domain codec now has a pure canonical RFC 4648 Base64 implementation; tests compare against the JDK reference encoder.

## Next actions

1. Continue feasible malformed/compression/resource and process-recovery testing on API 36 x86_64 and API 23 x86 without overstating hard native-memory limits.
2. Resolve or explicitly retain the minified PDFium-service runtime test-harness failure; do not infer runtime behavior from release assembly.
3. Obtain ARM64 runtime evidence only on an ARM-capable authorized device/host; the current x86_64 QEMU2 host cannot boot ARM images.
4. Reassess G-PASS/G-PDF/G-EVIDENCE from retained reports. Do not create an acceptance commit or begin I11–I14 unless contractual I10 gates pass.

## Safety

No real user database/profile was opened or changed. Protected `main` is unchanged. No network, merge, push, deployment, or destructive Git operation was performed in this checkpoint.

## Latest autonomous continuation — 2026-10-09

- State remains `feature/store-i10` at `65cac9345a1762874b50a427487b69335b7cc40a`; `main` protected at `3691eccedc9855686b011e34c7420f17aaf6c085`; index empty. Dirty paths remain confined to `store-android`; no commit or external Git operation.
- I10 import is now wired in source from SAF bounded selection through isolated PDFium Subject extraction, strict STORE_DATA_V1 decode, current-right/session-bound preview, explicit confirmation, revalidation and one authorized Room transaction. The UI's import action remains disabled pending parser security gates.
- A race guard invalidates prior preview tokens when a newer preview starts. Session switch, permission denial, cancellation/discard, duplicates, invalid data and injected mid-transaction failure remain fail-closed.
- Final latest offline validation passed: `:verifyArchitecture`, `:testing:test --rerun-tasks` (92/92, 14 suites), debug app compile, infrastructure Android-test compile and R8 `:app:assembleRelease`.
- Targeted native suites passed on both `STORE_API36_Main` API36 x86_64 and disposable `STORE_I10_API23_x86` API23 x86: isolated parser 16/16 each; Room catalog import 8/8 each. The latest Room test traverses the real bundled synthetic 2.0.1 PDF → isolated parser → codec → preview → confirmation → Room and verifies no pre-confirmation writes, exact imported data, duplicate replay rejection, discard without mutation, and transaction integrity. XML is retained under `I10/test-results/ANDROID_PdfSubjectIsolatedServiceTest_*_R3.xml` and `...CatalogImportNativeTest_*_R3.xml`.
- API36 x86_64 UI regression: CatalogUiTest 3/3, CatalogPresentationUiTest 2/2, CatalogFormUiTest 10/10, DocumentExportUiTest 5/5; retained combined XML `I10/test-results/ANDROID_CatalogAndExportUi_API36_R1.xml`. It includes real SAF export cancellation and selected-provider write/read/delete with synthetic data, not the disabled import picker.
- At this checkpoint I10 remained BLOCKED: production import UI was disabled; no real picker-to-production import flow was tested; hostile recursive-object/decompression resource campaign, native crash injection, ARM64 runtime and minified PDFium service runtime were incomplete. The sampled 256 MiB PSS watchdog is not a hard native memory ceiling.
- Next safe work at that time: continue adversarial/parser resource and available release/ABI qualification, then reassess G-PASS/G-PDF/G-EVIDENCE. The 2026-10-10 continuation below records the later real DocumentsUI provider-selection test and current remaining gaps. No I11–I14 work or I10 acceptance commit before I10 gates pass.
- Follow-up environment attempt: the official API36 ARM64 Google APIs image is now installed, but its temporary AVD cannot boot on this x86_64 QEMU2 host (`CPU Architecture 'arm64' is not supported`). API23 ARMv7 has the same emulator-host restriction. Both ABI runtimes remain NOT TESTED.
- A minified Release AndroidTest attempt failed before device execution during R8 of the test APK (`javax.lang.model.element.Modifier` missing from Android); the production release APK assembles. Temporary release test signing/build settings were reverted. Minified PDFium service runtime remains UNVERIFIED.
- Current inventory is 131 dirty paths, all under `store-android`, index empty, `git diff --check` passing. No changes outside I10 scope; no user data, production profile or main branch touched.

## Continuous recovery — 2026-10-10

- Verified branch `feature/store-i10`, HEAD `65cac9345a1762874b50a427487b69335b7cc40a`, protected `main` `3691eccedc9855686b011e34c7420f17aaf6c085`, empty index, and no background Gradle/emulator/test processes at recovery start. Worktree inventory before this continuation: 133 paths, all under `store-android`; `git diff --check` passed.
- Added `app/src/androidTest/kotlin/com/vibe/store/CatalogImportDocumentsUiTest.kt`. It uses real DocumentsUI `CreateDocument` and `OpenDocument`, the production bounded `MainActivity.readBoundedPdf` reader, isolated PDFium Subject extraction, and exact STORE_DATA_V1/decimal-text checks. It does not access or mutate the application database. Latest source timestamp: 2026-10-10 00:08:30 +0200; final API 36 x86_64 XML: `I10/test-results/ANDROID_CatalogImportDocumentsUiTest_API36_x86_64_R1.xml`, 1/1, 0 failures/errors/skips, timestamp 00:10:07 +0200.
- Added a synthetic valid PDF with a page stream that expands to 32 MiB if inflated. The metadata-only parser suite passes 17/17 on API 36 x86_64; the new case completed in 0.302s. XML: `I10/test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R4.xml`. This single case does not prove general decompression-bomb safety or a hard native-memory ceiling.
- Two early automation attempts failed before a passing run; their evidence was diagnostic only. The final test creates its PDF under a UUID filename and deletes it via the provider. Four exact synthetic leftovers from failed attempts were identified and removed from this authorized emulator; no profile was cleared.
- This closes real provider-selection evidence for SAF→bounded read→isolated parser→codec output, but it does not run the production catalog screen import action or combine DocumentsUI selection with `CatalogImportAuthority.confirm`. The action remains disabled. Existing `CatalogImportNativeTest` supplies Room/authorization/rollback coverage separately.
- Current status remains G-PASS BLOCKED and G-PDF BLOCKED. Remaining feasible work: hostile compressed-stream/resource tests, parser-process crash/restart behavior, and investigate release-test runtime through a synthetic variant without touching an existing profile. ARM64 runtime is not feasible on this x86_64 QEMU2 host; do not repeat the same AVD attempt. No I11–I14 work or I10 commit until I10 gates pass.
- Final reconciliation for this continuation: 136 changed/untracked paths, all within `store-android`; index empty; `git diff --check` PASS; branch and both commit IDs unchanged. No build outputs, APK/AABs, extra app Gradle/proguard edits or release-test helper source are in the worktree. The AUTOPILOT checkpoint and I10 evidence remain uncommitted and unstaged.

## Autonomous recovery — 2026-10-10 latest

- Verified again: branch `feature/store-i10`, HEAD `65cac9345a1762874b50a427487b69335b7cc40a`, main `3691eccedc9855686b011e34c7420f17aaf6c085`, index empty. Only paths beneath `store-android` are dirty. All changes/evidence remain local and unstaged.
- Final current inventory: **142** modified/untracked paths, all under `store-android`; no staged paths, generated build/APK/database artifacts, or paths outside scope. `git diff --check` passes.
- Added two bounded parser stress cases: 2,048 nested metadata levels and a 4,096-object indirect graph/reference chain. On the authorized API 36 x86_64 emulator, `PdfSubjectIsolatedServiceTest` is **19/19 PASS**, zero failures/errors/skips; final XML `I10/test-results/ANDROID_PdfSubjectIsolatedServiceTest_API36_x86_64_R5.xml`. Per-request debug observations for this run: peak sampled PSS 67,174 KiB, native heap 39,652,880 bytes, maximum elapsed 174 ms. The 25 ms PSS sampler is not a hard memory ceiling. The 32 MiB Flate-expanded stream test also passes; no generalized fuzz/decompression safety claim.
- Added a Compose test seam for default-disabled catalog import (`catalogPdfImportEnabled` defaults false); app wiring does not override it. `CatalogImportWorkflowUiTest` **3/3 PASS**; it covers visibility, synthetic preview/decimal display, discard on cancel, and denial without success. Final XML `I10/test-results/ANDROID_CatalogImportWorkflowUiTest_API36_x86_64_R2.xml`. The UI harness uses a fake service. Real DocumentsUI→bounded reader→parser→codec, and parser→authority→confirmation→Room are evidenced separately; no production-UI-to-Room single-flow pass is claimed. A test-module attempt to use internal Room/domain fixtures from app AndroidTest failed at compile time and was reverted; adding a dependency would violate module architecture.
- `:app:assembleRelease` PASS after presentation change with R8 enabled and no suppression changes. This remains packaging evidence; minified PDFium service runtime is UNVERIFIED. Connected release-test APK R8 failure was not repeated because no new evidence suggests its test harness changed.
- The project contract §§6–7 makes phase acceptance strictly sequential and requires each predecessor clean/accepted. I11–I14 cannot proceed as accepted or independent implementation phases while I10 is blocked; none was started.
- I10 status remains G-PASS **BLOCKED**, G-PDF **BLOCKED**, G-EVIDENCE **PASS_WITH_LIMITATIONS**, G-COMMIT **NOT READY**. Import remains disabled in production composition. Open: full production UI-to-Room test, hostile native resource campaign and crash injection, minified parser-service runtime, ARM64 runtime on an ARM-capable authorized target, and meaningful native resource containment/limits. API 36 x86_64 and API 23 x86 are the only demonstrated parser runtimes; prior ARM attempts failed due x86_64 QEMU host limitations and were not repeated.
- Evidence updated in `I10_FINAL_UNBLOCK_EVIDENCE.md` and `I10_IMPORT_EVIDENCE.md`. Latest source changes compile in debug instrumentation and release assembly; architecture verification passes as part of Gradle tasks. Diagnostic UI XML for the corrected trailing-zero assertion is retained separately from the final 3/3 XML.

## Autonomous recovery — 2026-10-10, real import workflow

- Added `app/src/androidTest/kotlin/com/vibe/store/CatalogImportEndToEndUiTest.kt` with an isolated UUID Room root under the app's private no-backup directory. It uses the actual CatalogScreen, MediaStore-created synthetic PDF, Android DocumentsUI `OpenDocument`, production bounded SAF reader, isolated PDFium extractor, codec/import authority, explicit preview/confirmation, and Room persistence verification after checkpoint-close-reopen. It verifies picker cancellation creates zero writes and success creates exactly the expected product, price, stock, price-history and movement.
- Verified `emulator-5554`: AVD `STORE_API36_Main`, API 36, x86_64. Final command `ANDROID_SERIAL=emulator-5554 bash ./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vibe.store.CatalogImportEndToEndUiTest` passed **1/1**, zero failures/errors/skips. XML retained at `I10/test-results/ANDROID_CatalogImportEndToEndUiTest_API36_x86_64_R1.xml`; source/report timestamps are 12:11:24/12:11:52 +0200.
- The first test attempts exposed only harness setup issues (bootstrap session not logged in; DocumentsUI row can auto-open; assertion assumed a composed text grouping). These were corrected; final result is retained separately. ADB could not restart from the later sandbox shell due to host socket restrictions, but Gradle connected to the verified emulator and completed the final test.
- Negative authorization/session/duplicate/invalid/rollback behavior remains evidenced by `CatalogImportNativeTest` separately. No claim that those were all picker-level E2E cases.
- Current inventory is now 144 paths, all under `store-android`; index remains empty; `main` and branch/HEAD unchanged. The E2E success/cancel gap is closed. I10 stays blocked: minified PDFium runtime remains unverified, ARM64 runtime unavailable on the x86_64 QEMU host, source-to-binary reproducible provenance is incomplete, and sampled PSS watchdog is not a hard native memory limit. Production import is still disabled; no I11–I14 work or I10 commit.
- Final local validation after the E2E source/evidence updates: `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks :app:assembleDebugAndroidTest :app:assembleRelease` PASS; architecture PASS, JVM **92/92** with zero failures/errors/skips, debug AndroidTest assembly PASS, minified release assembly PASS. The focused E2E device report is separately retained as `I10/test-results/ANDROID_CatalogImportEndToEndUiTest_API36_x86_64_R1.xml`. No actual minified parser invocation or ARM runtime result is claimed.

## Current status — 2026-10-10 final I10 differential

This section supersedes the older blocker statements above. Branch/HEAD remain
`feature/store-i10` / `65cac9345a1762874b50a427487b69335b7cc40a`; `main` remains
`3691eccedc9855686b011e34c7420f17aaf6c085`. The index is empty and 168 dirty
paths are all under `store-android`; no commit exists yet.

The actual CatalogScreen → Android DocumentsUI → bounded SAF reader → isolated
PDFium Subject JNI → strict STORE_DATA_V1 → preview → explicit confirmation →
current authority → atomic Room path is now exercised with synthetic data.
Latest combined SAF/import UI tests: 6/6; PDFium API36 x86_64: 19/19; Room
catalog import API23 x86 and API36 x86_64: 8/8 each; JVM: 92/92. Architecture,
debug and minified Release assembly pass. A test-only instrumentation runner
executed the actual minified Release app's isolated service/JNI/Binder flow on
API36 x86_64: 1/1 PASS. Evidence paths are in `docs/evidence/I10/I10_FINAL_ACCEPTANCE.md`.

Current I10 gates: G-PASS PASS; G-PDF PASS_WITH_LIMITATIONS;
G-EVIDENCE PASS_WITH_LIMITATIONS. G-COMMIT is pending exact manifest review.
P2 limits: no ARM64 runtime (contractual runtime coverage belongs to I13/I14),
no kernel-enforced PDFium native-memory ceiling (sampled measurements and
watchdog are explicitly not represented as one), no source-revision binding in
the artifact attestation, and no physical low-storage/provider-specific grant
revocation exercise. No P0/P1 is currently evidenced. Do not begin I11 until
I10's explicit-path commit and post-commit clean-tree verification complete.

## Current status — 2026-10-10 I11 delivery

This section supersedes the preceding “do not begin I11” instruction: I10 was
accepted and committed at `07c9be3a6080e32ee9f7c3bb25cb8d6a6cf64fb9` on
`feature/store-i10`; protected `main` remains
`3691eccedc9855686b011e34c7420f17aaf6c085`. I11 is now implemented and its
synthetic acceptance matrix passes, pending final exact-path review and local
I11 commit. The index is empty; all I11 changes are under `store-android`.

- I11 adds PrintManager/PrintDocumentAdapter, private FileProvider sharing,
  durable actor/generation-bound SMTP report delivery, Keystore-backed secret
  handling, mandatory TLS, bounded retry policy, a unique network-constrained
  WorkManager chain, and FR/EN configuration/queue UI.
- Final architecture + JVM regressions: 108/108, zero failures/errors/skips.
  Final minified `:app:assembleRelease` passed.
- API36 synthetic Android tests: PersistenceTest 12/12; queue persistence after
  writer-process death 1/1; DocumentExportUiTest 7/7; SAF provider failures and
  external shell-UID denial 4/4; queue UI 1/1; unique scheduler 1/1; offline →
  reconnect 1/1. Direct installed-runner phases also verified the WorkManager
  entry survived force-stopping `com.vibe.store.debug` (both 1/1). Emulator
  Wi-Fi/airplane state was restored.
- Controlled local SMTP suite 4/4: authenticated implicit-TLS message and PDF
  accepted by synthetic server; untrusted cert, absent STARTTLS and stalled
  greeting fail closed; nested timeout is surfaced as `SMTP_TIMEOUT`.
- Evidence and reports: `I11/I11_IMPLEMENTATION_EVIDENCE.md` and
  `I11/test-results/`. Gradle's split-process test invocation cleared the
  target package between separate restart runs; this harness diagnostic is
  retained separately, while the direct two-phase restart outputs are retained
  as text. No restart PASS is inferred from the Gradle failure.
- I11 limitations: physical printer/provider behavior is deferred to I14 as
  specified; no live third-party SMTP provider was contacted; delivery is not
  exactly-once. No known I11 P0/P1 remains; these are contractual limitations.

Next: inspect complete I11 diff and path inventory, run final `git diff
--check`, stage only reviewed I11 paths, verify the staged manifest, commit with
the contract message `feat(android): I11 integrate print share and delivery`,
verify clean worktree/main protection, then read I12's exact contract before
implementation. Do not push or merge.

## Current status — 2026-10-10 I12 recovery implementation

This is the current continuation and supersedes the stale I11 `Next` above.
Branch/HEAD are still `feature/store-i10` /
`0589c779f4fb0fa523b8d01569eda91959723b40`; protected `main` remains
`3691eccedc9855686b011e34c7420f17aaf6c085`. I10/I11 remain committed and
unmodified. I12 changes are uncommitted; the index remains empty.

I12 now has a bounded recovery archive, active-generation AtomicFile pointer,
durable transition journal, single Room owner/maintenance gate, first-read
startup validation, catalog-media snapshotting, internal verified backup,
candidate restore and reset generation switching, Owner/password service
checks, SAF settings workflows, unique daily Worker scheduling, and automatic
backup retention limited to seven. Latest focused evidence: `ActiveStoreOwnerTest`
11/11 on API 36 x86_64, including schema-manifest mismatch rejection before
inspection/restore with no active-generation switch; non-empty product/stock-history/media restore and
actor-linked restore/reset audit assertions; `RecoveryArchiveTest` 3/3 JVM;
`RecoveryUiTest` 2/2 API 36 (including `RecoveryPanel` cancellation and a real
DocumentsUI CreateDocument → Downloads provider byte-write/read/delete flow);
`RecoveryBackupSchedulerTest`
1/1 API 36; and `:verifyArchitecture :testing:test --rerun-tasks` 103/103 JVM
tests. Reports are retained under `I12/test-results/`. Failed picker-selection
diagnostics are retained separately; the product OpenDocument/restore flow is
still unqualified.

I12 is **not accepted**. Contractual blockers remain: seven product-level SP-01
commit boundaries with external process death/recovery ×3; full sale/purchase/
team/reporting/delivery-queue plus media before/after proof; real DocumentsUI/
provider export-import, revoked URI, low-space and interrupted-export tests;
Owner-vs-employee/reset lockout and session integration tests; WorkManager
catch-up and actual worker-execution-after-process-restart evidence; candidate
migration/corruption, concurrent POS/Worker and wider domain regressions. Unique
scheduling and retention are partly covered; manual backup, restore and reset
outcomes now append an actor-attributed audit row. Do not start I13 until I12
G-PASS/G-RECOVERY/G-EVIDENCE and its contractual local commit pass. No push,
merge or deployment.

## Latest I12 continuation — 2026-10-10

The restore/reset interruption matrix is now exercised against product
`ActiveStoreOwner` + Room on the authorized API 36 x86_64 emulator. The final
`I12RecoveryProcessDeathTest` reports 1/1 JUnit and retains 30 external process
kills: seven restore boundaries and three reset boundaries, each repeated
three times. Each interrupted service PID is observed gone; a distinct new
verifier PID/token reopens the durable pointer/journal, checks expected old/new
state, Owner count, SQLite quick/full/FK integrity, and referenced media. The
final reports are `I12/test-results/I12RecoveryProcessDeathTest_restore_reset_boundaries_x3.xml`
and `.logcat.txt`; a prior reused-verifier diagnostic is retained separately.

`RecoveryAuthorityNativeTest_API36.xml` passes 2/2 using the real application
RecoveryAuthority, AndroidRecoveryBackend, synthetic Room generations and
Owner/employee sessions. It verifies backup/preview/confirmation/restore,
session invalidation, plus employee and signed-out denial. The normal Room
owner suite remains 11/11; the actual RecoveryPanel/real DocumentsUI destination
and provider cancellation flow remains 2/2 but uses a synthetic service.

An attempted next instrumentation build first hit a temporary quota while
Room/KSP extracted its SQLite verifier library, then stale KSP storage state.
The affected stale, user-owned `/tmp/sqlite-3.41.2.2-*` extraction files were
removed after confirming no KSP worker was running; no app/build/evidence,
AVD/APK, or unrelated core file was removed. A fresh Gradle daemon then built
and ran the targeted RecoveryAuthority suite successfully. The current branch
and HEAD remain `feature/store-i10` / `0589c779f4fb0fa523b8d01569eda91959723b40`,
`main` remains protected, and the index is empty.

I12 remains **IN PROGRESS / not commit-ready**. Remaining mandatory items are
production-path SAF archive selection/restore and provider-failure/revocation,
partial export, low-space/candidate corruption/migration, concurrent POS/Worker
and actual WorkManager catch-up/restart behavior, reset-password/lockout and
session-race cases, and the required broad I10/I11 non-regression matrix. No
I13 work may begin before contractual I12 acceptance and its local commit.

## Authoritative recovery — I12 gate closure candidate, 2026-10-11

The preceding open-items paragraph is historical and superseded by
`I12/I12_IMPLEMENTATION_EVIDENCE.md` final continuation. Branch/HEAD are still
`feature/store-i10` / `0589c779f4fb0fa523b8d01569eda91959723b40`; `main` remains
`3691eccedc9855686b011e34c7420f17aaf6c085`; index empty. The production
RecoveryAuthority/Room path through DocumentsUI now passes 3/3 on the verified
synthetic API36 x86_64 AVD `STORE_I12_API36_CLEAN` / `emulator-5558`. Recovery
process death: 30 external kills across ten restore/reset boundaries ×3. Actual
WorkManager delayed process-restart and retry evidence is retained. Final
affected-domain G-NR classes: Sale 9/9, Purchase 8/8, Inventory 9/9, Reporting
7/7, Catalog 5/5, Team 6/6 and ExactMoney/migration 8/8. ActiveStoreOwner is
15/15, including an Android Keystore backup secret-envelope/plaintext regression.
JVM 103/103; Reporting UI 14/14; architecture, KSP-enabled build/AndroidTest
assembly and release assembly passed. The six StoreNavigation UTP tests are
marked PASSED in textproto/logs; runner gRPC ended without XML, so no Gradle XML
pass is asserted.

Gate assessment: G-PASS PASS; G-RECOVERY PASS; G-NR PASS for the I12 impact
matrix; G-EVIDENCE PASS_WITH_LIMITATIONS. P0=0, P1=0 observed in I12 scope.
P2 limitations: no physical power-loss/OEM storage guarantee (absolute power
loss is contractually excluded), no new API23/physical ARM64 claim, no manual
TalkBack, representative dark/French captures, unknown cause for prior AVD /
DocumentsUI fsync event, and no final StoreNavigation gRPC response. Final
screenshots and individual XMLs are under `I12/`. Current worktree has 147
status entries, all within `store-android`; last diff check passed. I12 may be
committed only after the final path/evidence review; I13 is unlocked only by
the resulting clean contractual I12 commit. No push, merge or deployment.

### I12 update — focused Room success, SAF E2E diagnostic

- Latest `ActiveStoreOwnerTest_migration_low_space_13of13.xml`: 13/13 PASS, adding candidate-only legacy Room migration and simulated low-space rejection before publication/switch.
- Latest `RecoveryAuthorityNativeTest_provider_failures_3of3.xml`: 3/3 PASS, including revoked-source/interrupted-destination no-generation-change checks.
- Restore/reset process death remains 30 external kills across 10 boundaries ×3 with distinct verifier process identities and integrity/media checks.
- A debug-only real-authority DocumentsUI restore test was added and compiled. The first run failed before preview (selected archive did not produce displayed preview); another provider UI test then failed `SQLITE_IOERR_FSYNC` while deleting its synthetic document and emulator-5554 went offline. Failure XML is retained at `I12/test-results/diagnostics/RecoveryUiTest_20261010_real_authority_e2e/RecoveryUiTest_picker_callback_timeout_and_provider_fsync.xml`. An explicit OpenDocument “Open” tap was added after this run and remains UNTESTED.
- No emulator profile was wiped/recreated and the unresponsive second device was not used. Re-establish and verify only the authorized API 36 emulator before another focused native test. I12 remains blocked/uncommitted; do not start I13 until G-PASS/G-RECOVERY/G-NR/G-EVIDENCE pass.

## Current I12 continuation — 2026-10-11

Supersedes the previous I12 blocker snapshot above. Branch/HEAD: `feature/store-i10` / `0589c779f4fb0fa523b8d01569eda91959723b40`; protected `main` unchanged; index empty. All 122 changed paths remain under `store-android`. The authorized disposable API36 x86_64 AVD is `STORE_I12_API36_CLEAN` on `emulator-5558`; the older AVD was not wiped. KSP ran normally with a dedicated temporary extraction directory.

Completed final focused evidence: actual RecoveryPanel + DocumentsUI + production RecoveryAuthority/Room restore 3/3; Room ActiveStoreOwner 14/14; RecoveryAuthority 6/6; archive codec 4/4; exact-money persistence 8/8; JVM 103/103; reporting UI 14/14; navigation UTP textproto six PASSED; external restore/reset crash matrix 30 kills × ten boundaries; actual delayed Worker restart and failed-write retry. Artifacts are retained in `docs/evidence/I12/test-results/final_20261011/`.

I12 no longer has an identified feature/recovery defect in the tested synthetic scope. Gate assessment is G-PASS PASS, G-RECOVERY PASS, affected-scope G-NR PASS, G-EVIDENCE PASS_WITH_LIMITATIONS pending complete manifest review. The unconfigured full legacy app/infrastructure attempts remain separately retained diagnostics, not acceptance suites. Physical power-loss and OEM storage behavior are not claimed. Next: complete the source/worktree/evidence review and only then decide G-COMMIT; I13 remains locked until the contractual I12 local commit exists.

### Final verification delta — 2026-10-11

The paragraphs above are historical snapshots. Current `RecoveryUiTest` is 4/4 PASS after fixing stale preview/source retention on a failed second archive inspection and synchronizing DocumentsUI selection on an actionable row/SAF callback. One intermediate 1-failure run is preserved in `I12/test-results/diagnostics/20261011/`; it is excluded from final counts. Latest XML: `I12/test-results/final_20261011/RecoveryUiTest_4of4_final.xml`. `ActiveStoreOwnerTest` is 15/15 with the synthetic Keystore envelope/plaintext exclusion test. I12 gates are G-PASS PASS, G-RECOVERY PASS, affected-impact G-NR PASS, G-EVIDENCE PASS_WITH_LIMITATIONS. Current status inventory is 168 entries, 10 modified +158 untracked, all under `store-android`; empty index, main unchanged, and diff check passes. No I13 implementation begins until I12 is committed locally and the worktree is verified clean.
