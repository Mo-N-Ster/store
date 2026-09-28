# STORE 3.0 — PHASE I RELEASE & RELIABILITY HARDENING REPORT

Audit date: 2026-09-24/25
Candidate package version: 2.0.1
Public release/version bump: not performed

## 1. Executive summary

The source candidate is reproducible, its full automated suite passes, the Windows x64 NSIS package builds, the native SQLite module is packaged, an unpacked production executable launches independently, and an isolated backup/restore round trip preserves tested data. STORE is nevertheless not releasable: real visual validation is unavailable, a complete packaged upgrade and installer lifecycle are untested, the artifact is unsigned, and the production dependency audit reports a directly reachable high-severity Nodemailer finding.

## 2. Baseline

Input baseline: 26 files, 131 tests, zero skipped, version 2.0.1. Final Phase I suite: 27 files, 134 tests, zero skipped.

## 3. Repository state

Branch `main`, commit `d46c56c8af570fe96f9aba875726c1ca2c5abd53`. At I0: 55 tracked paths modified/deleted and 84 untracked paths. Generated/ignored: `dist/`, `release/`, `node_modules/`, TypeScript build-info files. This is a understood but non-clean candidate; no reset/clean was performed.

## 4. Environment

- Windows NT 10.0.26200 x64
- Node 22.17.0; npm 11.5.2
- Electron package 43.1.1 (Electron runtime reports Node 24.18.0)
- Vite 8.1.5; TypeScript 6.0.3
- problematic environment: `ELECTRON_RUN_AS_NODE=1`
- restrictive environment: `NPM_CONFIG_OFFLINE=true`
- no required application environment variable identified

## 5. Reproducibility

Candidate copied to `store-phase-i-fff236d8c5374cbfaddc6bce18b7eb7b` excluding `.git`, dependencies and outputs. `npm ci` installed 510 packages without lockfile edits. Isolated lint, tests and build passed. Lockfile SHA-256 before build: `6E5DCA04431FE9F01DE69935C03A2290E6A848779F402BE77310AB3AE13E18E8`.

## 6. Full regression

PASS: 27 files, 134/134 tests, zero skipped/todo. Setup, security, POS model, operations, reports, Administration, migrations, policies, backup validation and new disaster tests included.

## 7. Security regression

E.5 regression tests pass: unknown IPC deny, explicit public entry points, role boundaries, stale elevation fail-closed, strict preload allowlist, actor binding and minimal session payload.

## 8. Authorization matrix

| Actor | Setup bootstrap | POS validate | Stock/purchase | Administration | Restore/reset |
|---|---|---|---|---|---|
| Unauthenticated | Only while no Owner | DENY | DENY | DENY | DENY |
| Owner | Completed guard | ALLOW | ALLOW | ALLOW | ALLOW |
| Manager | DENY | ALLOW | ALLOW | settings/root admin denied | DENY |
| Employee | DENY | ALLOW per CASHIER matrix | DENY mutation | DENY | DENY |
| Elevated employee | DENY | Manager-like until expiry | Manager-like | According to effective permissions | DENY |
| Expired elevation | DENY | Base role | Base role | DENY | DENY |

## 9. Secret review

No renderer persistence or verification UI contains Owner password/recovery answer. Settings suppress SMTP secret. Session payload excludes hashes, SMTP password and session reference. Temporary credentials remain limited to their intended 60-second renderer lifecycle. No input arguments are written by the technical logger.

## 10. SQLite configuration

Startup code sets WAL, `synchronous=FULL`, 5000ms busy timeout and foreign keys ON. Packaged fresh DB reported WAL and foreign keys ON. `synchronous` is connection-scoped; the read-only post-restart observation (`1`) cannot validate the original writer connection, while source and tests verify the startup assignment.

## 11. Database integrity

Packaged isolated fresh database: 28 tables, `PRAGMA integrity_check=ok`. Performance fixture and restored backup also returned `ok`.

## 12. Business invariants

Existing tests verify sale transaction rollback, stock validation, purchase draft/validation rules, inventory quantity rules, employee status, cash differences and migration data preservation.

## 13. Idempotency

Invoice request identifiers are unique. UI locks cover checkout, purchase/inventory validation and sensitive Administration actions. Backend status guards protect validation replay. Automated tests cover representative duplicates; delayed real-device taps remain part of the visual/manual gate.

## 14. Crash/interruption strategy

Only isolated DBs/temporary user-data were used. Atomic transaction rollback and deterministic restart states were tested where the harness permits; no production process or real user DB was intentionally crashed.

## 15. Setup interruption

Owner commit followed by failed preferences and restart yields one active Owner, no storeName setting and an integral DB. Backend reports setup complete because Owner exists. Classification: **DEGRADED BUT RECOVERABLE MANUALLY** through login/Administration defaults; exact wizard resume is unavailable.

## 16. Sale interruption

Automated transaction fault after stock mutation rolls stock back. A real OS kill during packaged checkout was not executed.

## 17. Purchase interruption

Policy/status tests cover exactly-once validation semantics. Real OS kill during commit: NOT OBSERVED.

## 18. Inventory interruption

Validation policies and draft semantics are covered. Real OS kill during commit: NOT OBSERVED.

## 19. Backup interruption

Engine writes to `.tmp`, validates, then renames atomically; stale `.tmp` cleanup is implemented. Forced packaged termination showed recoverable backup sidecars; real kill during the exact backup copy was not synchronized/observed.

## 20. Restore interruption

Restore engine safety backup and rollback paths were inspected; invalid candidate rejection leaves active test data unchanged. A real process kill mid-copy was not executed.

## 21. Backup validation

PASS: SQLite integrity, required tables and active Owner. Manual `Database.backup()` produced a 3,727,360-byte validated performance backup.

## 22. Restore validation

PASS for valid, unrelated SQLite and missing Owner fixtures. Incompatible-version semantics beyond current schema checks remain limited.

## 23. Backup/restore round trip

PASS on isolated files: known state A (Owner, storeName, product stock 7) → backup → state B (name changed, stock 2) → restore A → integrity, Owner, settings and stock all verified.

## 24. Disaster recovery findings

Local backup/restore is technically functional. Owner reauthentication and encryption remain deferred. External media discipline is still operationally necessary for device loss.

## 25. Fresh-install validation

Packaged executable launched with explicit temporary `--user-data-dir`, created a fresh 28-table DB and automatic backup, and remained alive after seven seconds. Full UI Setup completion was NOT OBSERVED.

## 26. Primary Owner validation

Backend and isolated tests validate forced Owner role, active Owner backup invariant and authentication rules. Primary Owner creation/login through the packaged UI was NOT OBSERVED.

## 27. First restart

Source/test contract proves Owner count suppresses Setup. Packaged first-Owner restart was not performed because the UI could not be observed.

## 28. STORE 2.0.1 upgrade validation

Migration suite upgrades representative legacy users, products, stock movements, invoices, roles, employees, purchases and cash schema without loss. A full packaged candidate over a complete real 2.0.1 fixture was NOT COMPLETED.

## 29. Historical-data preservation

Automated legacy fixture preserves movement quantity, derives unit price, creates product history/reference and required tables/columns. Complete historical invoices/purchases/reports through packaged UI remain unobserved.

## 30. Downgrade statement

Downgrade is not supported or promised. Always back up before upgrade.

## 31. Production packaging

PASS: `npm run package:win:x64` built production frontend, rebuilt native dependencies, packaged ASAR and created NSIS installer plus blockmap.

## 32. Installer

Installer built (`114,869,453` bytes) but was not installed/uninstalled to avoid colliding with an existing STORE registration/profile. Installer lifecycle gate remains BLOCKED.

## 33. Native modules

`better-sqlite3` rebuilt for Electron 43 x64, exists in `app.asar.unpacked`, and functioned when packaged startup created the DB.

## 34. User-data paths

Explicit `--user-data-dir` places DB, backups, logs/cache outside the package. APPDATA environment override alone did not isolate Electron in this harness and the first launch created an automatic backup in the normal STORE profile; no user records were deleted/reset.

## 35. Uninstall/reinstall

NOT TESTED. NSIS is per-user and permits installation-directory change, but exact data/registry/shortcut retention requires a controlled machine/profile.

## 36. Packaged Windows execution

PASS limited: `win-unpacked/STORE.exe` launched without Node/npm/dev server, stayed alive, initialized SQLite and native module. Real visible workflows were not observed.

## 37. Offline execution

`NPM_CONFIG_OFFLINE=true` did not prevent the packaged application launch. Core local startup/DB works without network; complete offline UI workflows remain unobserved. SMTP is expected to fail offline without blocking core modules.

## 38. Visual-validation methodology

Computer Use inventory was queried after packaged execution. It returned `apps: []`, `browsers: []`. Unit tests, CSS and process liveness were explicitly not substituted.

## 39. Visual results

No required screen was observed. VISUAL RELEASE GATE: BLOCKED.

## 40. Tablet validation

1280×800, 1024×768 and 800×1280: NOT OBSERVED.

## 41. Desktop validation

1366×768 and 1920×1080: NOT OBSERVED.

## 42. Theme/accessibility visual checks

Light, dark, high contrast, reduced motion, keyboard and touch: structurally covered but NOT OBSERVED visually.

## 43. Performance dataset

Isolated SQLite fixture: 2,000 products, 10,000 validated invoices, 30,000 invoice lines, 20 categories. Build time 345.09ms; backup 3.73MB. These are SQL measurements, not full renderer benchmarks.

## 44. Startup performance

Packaged process remained alive within 7 seconds and initialized DB/backup. Exact cold-start-to-interactive time unavailable without UI observation.

## 45. POS performance

Product search query: 0.93ms for 50 rows. Full POS render/checkout latency NOT OBSERVED.

## 46. Reporting performance

Dashboard aggregate 2.28ms; top-products aggregate 12.90ms; 500-row history 1.63ms. Full React charts NOT OBSERVED.

## 47. Backup performance

34.54ms for the 3.73MB isolated fixture backup. Hardware/cache-specific raw result only.

## 48. Memory observations

Not available: the hidden packaged process was stopped after startup and no repeat-navigation UI measurement was possible.

## 49. Dependency audit

`npm audit`: 14 total findings (1 low, 3 moderate, 10 high). `npm audit --omit=dev`: one direct production package, Nodemailer, severity high. No force fix applied.

## 50. Vulnerability triage

Nodemailer is runtime reachable through configured SMTP/report sending. Findings include address parser DoS and content/domain validation bypasses. A fix is available (>=9.1.x), but compatibility needs a separate controlled patch/gate. Remaining findings are build/test/transitive tooling and are not shipped as application runtime dependencies, though they affect CI/build security.

## 51. License review

Direct production licenses: BSD-3-Clause (bcryptjs), MIT (better-sqlite3, i18next, pdf-lib, React, react-i18next), ISC (Lucide), MIT-0 (Nodemailer). Direct dev licenses are MIT except TypeScript Apache-2.0. Electron package includes Chromium/Electron license files. No copyleft direct dependency found; formal legal review remains external.

## 52. Release artifacts

- `STORE Setup 2.0.1-x64.exe`
- `.exe.blockmap`
- `win-unpacked/STORE.exe` and resources
- Phase I report, visual checklist and draft notes
- no portable target configured

## 53. Checksums

NSIS installer SHA-256: `C1B5972200F24516FEF22D313C645A8B8C6F41A4E6154E129CD09A9C267791FC`.

## 54. Code-signing status

`Get-AuthenticodeSignature`: **NotSigned** for installer and packaged executable. Windows reputation/SmartScreen warnings are likely distribution risks.

## 55. Release-notes draft

See `docs/RELEASE_NOTES_3.0_DRAFT.md`; explicitly not published.

## 56. Release gate matrix

| Gate | Result | Evidence | Blocking |
|---|---|---|---|
| Clean install | PASS | isolated `npm ci`, 510 packages | No |
| Full tests | PASS | 134/134 | No |
| Security | PARTIAL | E.5 pass; runtime audit finding | Yes |
| DB integrity | PASS | three isolated integrity checks | No |
| Crash recovery | PARTIAL | transactions/setup characterized; real kills limited | Yes |
| Backup/restore | PASS | isolated round trip | No |
| Fresh install | PARTIAL | packaged DB/start; UI flow unobserved | Yes |
| Upgrade | PARTIAL | migration fixtures; no full packaged 2.0.1 upgrade | Yes |
| Packaging | PASS | NSIS x64 + verify-package | No |
| Packaged execution | PASS | executable alive/native DB | No |
| Visual validation | BLOCKED | `apps: []` | Yes |
| Performance | PARTIAL | SQL raw measurements; no UI/memory | No |
| Dependencies | FAIL | direct high runtime finding | Yes |
| Release artifacts | PARTIAL | unsigned; installer lifecycle untested | Yes |

## 57. Platform matrix

| Environment | Install | Launch | Setup | Login | POS | Backup/Restore | Result |
|---|---|---|---|---|---|---|---|
| Windows 10 x64 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | BLOCKED |
| Windows 11 x64 (NT 10.0.26200) | BUILT, install not run | PROCESS PASS | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | harness PASS | PARTIAL |

## 58. Visual matrix

| Screen | 1920×1080 | 1366×768 | 1280×800 | 1024×768 | 800×1280 |
|---|---|---|---|---|---|
| Setup | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| POS | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| Products | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| Stock | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| Purchases | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| Dashboard | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| Reports | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |
| Administration | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED | NOT OBSERVED |

## 59. Crash matrix

| Operation | Interruption point | Result | Integrity | Recovery |
|---|---|---|---|---|
| Setup | Owner after commit/settings before commit | degraded, no wizard after restart | OK | login + configure settings manually |
| Sale | exception after stock mutation inside transaction | rollback | OK | retry with unique request id |
| Purchase | replay/status policy | duplicate denied | tests pass | inspect/retry draft |
| Inventory | replay/status policy | duplicate denied | tests pass | retain/inspect count |
| Backup | before rename/model inspection | target not published until validation | atomic design | next hourly/manual retry |
| Restore | invalid candidate | rejected; active test state unchanged | OK | select valid backup |

Real OS kill during sale/purchase/inventory/restore commit remains NOT OBSERVED.

## 60. Dependency matrix

| Finding | Severity | Runtime reachable | Fix | Breaking risk | Decision |
|---|---|---|---|---|---|
| Nodemailer <=9.1.0 advisories | High | Yes, SMTP/report | Available | Low/unknown; test required | Release blocker |
| Vitest/mocker path read | Moderate | Dev only | Available | Low | Update before trusted CI exposure |
| brace-expansion/glob/tooling DoS | High | Build only | Available | Transitive | Harden build chain |
| xmldom XML issues | High | Build/package transitive | Not app runtime | Available | Harden build chain |
| fast-uri/undici/js-yaml/tar | High | Build/install transitive | Available | Transitive | Controlled dependency refresh |
| postcss | Moderate | Build only | Available | Low | Controlled refresh |
| joi | Low | Build tooling | Available | Low | Controlled refresh |

## 61. Debt matrix

| Debt | Security impact | Data impact | User impact | Release blocker | Future phase |
|---|---|---|---|---|---|
| Operation-scoped elevation absent | Medium | Low | Broad temporary access | No | Security evolution |
| Generic authenticated file picker | Medium | Low | Broader picker surface | No | IPC hardening |
| Owner reauth before restore | High | High | Sensitive restore easier after session compromise | Recommended blocker review | Security/recovery |
| Backup encryption | Medium | High if media lost | External copies readable | Policy-dependent | Backup security |
| Inventory draft persistence | Low | Medium | restart loses draft | No | Reliability |
| Purchase draft persistence | Low | Medium | restart loses draft | No | Reliability |
| Partial Owner audit | Medium | Low | weak investigation | No | Audit backend |
| Two-commit Setup | Low security | Medium configuration | manual recovery | No, classified degraded | Setup transaction redesign |
| Unsigned binaries | Distribution/reputation | None | warnings/trust | Yes for public distribution | Release engineering |

## 62. Fixes made during Phase I

No product behavior changed. Three isolated reliability tests and documentation artifacts were added.

## 63. Files modified

Added only: release reliability tests, Phase I report, visual checklist, draft release notes.

## 64. Tests added

Three: real SQLite backup/restore round trip, Setup two-commit restart state, invalid restore candidate preservation.

## 65. Final test results

PASS — 27 files, 134 tests, zero skipped.

## 66. ESLint

Final gate executed after Phase I test addition: see final command evidence; zero warnings required.

## 67. Build

Isolated clean build PASS; production package build PASS.

## 68. git diff --check

PASS before final report; final recheck required after documentation creation.

## 69. Release blockers

### I-B01 — Real visual validation not completed

Evidence: Computer Use `apps: []`. Impact: clipping, overlap, touch, portrait and critical UI states unverified. Reproduction: query Windows app inventory after launch. Resolution: execute and record the supplied checklist on real Windows displays/devices.

### I-B02 — Full packaged 2.0.1 upgrade not completed

Evidence: migration fixtures pass, but no complete representative installed 2.0.1 profile was upgraded and exercised. Impact: user historical data compatibility is not proven end-to-end. Resolution: controlled upgrade lab with backed-up comprehensive fixture.

### I-B03 — Installer lifecycle not validated

Evidence: NSIS artifact built but install/uninstall/reinstall not run. Impact: install paths, shortcuts, registry and user-data retention unknown. Resolution: disposable Windows user/VM lifecycle test.

### I-B04 — Runtime dependency vulnerability

Evidence: `npm audit --omit=dev` reports direct Nodemailer high finding with fix available. Impact: crafted address/content paths may affect availability/confidentiality when SMTP is used. Resolution: controlled upgrade to fixed version plus SMTP/email regression.

### I-B05 — Unsigned Windows artifacts

Evidence: Authenticode `NotSigned`. Impact: SmartScreen/reputation warnings and weaker publisher assurance. Resolution: approved code-signing certificate and signed-candidate verification.

### I-B06 — Packaged fresh Setup/login/POS flow not observed

Evidence: packaged process/DB creation pass, UI inventory unavailable. Impact: first-use release path not proven. Resolution: execute full visual and functional packaged flow on isolated profile.

## 70. Non-blocking limitations

Raw SQL performance is healthy on the documented fixture but is not a renderer benchmark. ARM64, Windows 10 and downgrade are not claimed.

## 71. Security debt

Restore reauth, generic picker, broad elevation, partial audit and backup encryption remain open; no security control was weakened.

## 72. Reliability debt

Real kill-point tests, two-commit Setup recovery, draft persistence and memory/navigation soak remain future work.

## 73. Functional debt

No feature debt was addressed in Phase I; functional scope is frozen.

## 74. UX debt

All critical real visual/device validation is outstanding and release-blocking.

## 75. Acceptance matrix I-AC-001 → I-AC-085

| Range | Result | Evidence/notes |
|---|---|---|
| I-AC-001–006 | PASS | baseline, isolated npm ci, lock, build, env audit |
| I-AC-007–017 | PASS | 131 historical + new tests; E.5 and IPC controls |
| I-AC-018–024 | PASS | integrity, invariants, idempotency, Setup classified |
| I-AC-025 | PARTIAL | transactional fault, not real OS kill |
| I-AC-026–029 | PARTIAL | policy/inspection, real synchronized kills absent |
| I-AC-030–035 | PASS | backup validation and round trip |
| I-AC-036–039 | PARTIAL | packaged fresh DB/start; UI Owner/restart unobserved |
| I-AC-040–041 | PARTIAL | migration fixture, not full packaged upgrade |
| I-AC-042–043 | PASS | package and NSIS built |
| I-AC-044–047 | PASS | packaged launch, no Node, user-data isolation, native SQLite |
| I-AC-048–049 | PARTIAL | offline startup only, workflows unobserved |
| I-AC-050–057 | BLOCKED | no real visual observation |
| I-AC-058 | PASS | dataset documented |
| I-AC-059–062 | PARTIAL | raw startup/query/backup evidence, no UI/memory |
| I-AC-063–065 | PASS | audit rerun, triage, no blind upgrade |
| I-AC-066 | PASS | direct licenses inventoried |
| I-AC-067–068 | PASS | artifacts/signing status documented |
| I-AC-069–070 | BLOCKED | installer lifecycle not tested |
| I-AC-071–076 | PASS | debts/blockers, no features/schema/IPC/security weakening |
| I-AC-077 | PASS | version remains 2.0.1 |
| I-AC-078–083 | PASS | required matrices completed |
| I-AC-084 | PASS | classification follows mandatory gates |
| I-AC-085 | PASS | no next phase started |

## 76. FINAL RELEASE CLASSIFICATION

**NOT READY — RELEASE BLOCKERS REMAIN**

## 77. Required next action

Do not bump, publish or install broadly. First close I-B01 through I-B06 on disposable Windows 10/11 x64 environments, update Nodemailer through a controlled patch, sign the candidate, rerun all gates and obtain human approval.

**NOT READY — RELEASE BLOCKERS REMAIN**
