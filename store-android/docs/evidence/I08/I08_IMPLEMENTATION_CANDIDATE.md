# STORE I08 — Implementation candidate (not accepted yet)

Source baseline: `589ea20ec2c668e2a25bfc666779d1a70f06cb84`, branch `feature/store-i08`.
This file and its patch are **not** a PASS gate, commit, or release.

## Scope implemented in the candidate
- `SupportService` allowlisted DTOs and `SupportAuthority` behind I03's current-session mutex and UnitOfWork.
- Messages direct/broadcast, exact recipient snapshot at send, UUID request replay detection, read receipts, per-user hiding, paged inbox/unread; Owner-only broadcast; no network.
- Owner administration uses already accepted `IdentityService.permissions/restrict` and shop settings; no new permission-grant operation.
- Owner-only filtered, paginated audit with no raw `details`, verifier or free-form diagnostics, safe database integrity/status and bounded failures.
- Durable LOW_STOCK notifications on threshold entry, resolved on recovery, with per-Owner read state (reserved settings keys) and controlled resolution audit.
- Offline FR/EN searchable help including explicit unimplemented I10–I12 features, navigation safeguards on unsent drafts.
- No Room schema version change; existing v2 tables and constraints reused; no rounding or Double monetary bridge added.

## Validation performed outside Android environment
- Kotlin/JVM source compilation of domain+application-api+application using host Kotlin 1.9: PASS (not Android Gradle build).
- Three synthetic I08 authority methods compiled and executed using local lightweight JUnit assertion stubs: PASS (not official `:testing:test`).

## Mandatory gates after applying patch to UM890
- Exact baseline, branch, clean worktree and patch preflight before modifications.
- Gradle offline architecture + all JVM tests + debug compilation, including Room/KSP and Compose compile.
- 3 new real Room native instrumentation tests `SupportNativeTest`.
- 2 new UI instrumentation tests `SupportUiTest`.
- Android regression selection: Identity/admin, catalog-stock, lifecycle, navigation; update details in this report with exact commands/codes, not guessed PASS.
- Human compact/medium/expanded FR/EN and role checks, content/IME, no inaccessible primary actions.
- I08 known risks to evaluate: broadcast recipient concurrency/snapshot, low-stock transition after sale/purchase, older synthetic messages with no receipt, very large paging, storage low-space, failed read/worker interactions.
- Confirm disabled users, withdrawn permissions, read-after-restart and owner-only audit/diagnostics directly; log redaction.

**STATUS: IMPLEMENTATION_CANDIDATE / ANDROID_TESTS_NOT_RUN / NO_COMMIT / NO_PUSH**
