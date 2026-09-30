# I04 — Catalogue, médias et stock

Evidence review and targeted adaptive closure: 2026-10-01.
Baseline: `fb4237bcb8e1a36d8504e4f069494bec881ad9ea`. Branch: `main`.

## Provenance and scope

VERIFIED FACT: HEAD and branch match the contract. All tracked modifications and untracked source paths are under `store-android/`. The original evidence-only mission created four evidence files without modifying implementation/tests. The subsequent authorized adaptive closure changes only `CatalogScreen.kt`, `CatalogUiTest.kt`, this summary and `qualification.txt`; RF001 evidence remains unchanged.

The active repository is `C:\Users\sterl\OneDrive - Università degli Studi di Parma\SCHOOL\PROGETTI_APP\STORE`. The mission's alternate `PROGETTI\_APP\STORE` path does not exist on this machine. The active worktree has the required baseline. Qualification copy: `C:\Users\sterl\AppData\Local\Temp\store-i03-qual`; all changed I04 source files compared equal to that copy.

## Qualification matrix

| Check | Result | Evidence provenance |
| --- | --- | --- |
| Architecture allowlist | PASS | Previous qualification supplied in mission; unchanged module build configuration inspected |
| Android-only / forbidden-scope review | PASS | Current Git path inventory |
| App and Android test APK builds | PASS | Targeted app/debug-test packaging rerun for adaptive closure; infrastructure APK remains previously qualified |
| CatalogPolicyTest | PASS 4/4 | Existing JUnit XML: zero failures/errors/skips |
| CatalogNativeTest | PASS 5/5 | Previous verified result supplied in mission; five test methods and assertions inspected; not rerun |
| CatalogUiTest | PASS 3/3 | Direct instrumentation: existing FR/EN tests unchanged plus adaptive test; `OK (3 tests)` |
| Adaptive catalogue | PASS | Actual windows 599, 600, 839, 840, then 599 dp; 1/2/2/3/1 columns and usable search/detail/form asserted |
| App / infrastructure / presentation lint | PASS (prior qualification) | Existing reports: app 0 errors, 16 warnings; other two modules no issues; not rerun after adaptive-only changes |
| RF001 BEFORE_REFERENCE | PASS 1/1 | Existing marker 4760; fresh VERIFY-only execution, `OK (1 test)` |
| RF001 AFTER_REFERENCE | PASS 1/1 | Existing marker 4861; fresh VERIFY-only execution, `OK (1 test)` |
| Sensitive catalog/media projection scan | PASS | Current narrow source scan: no matches; public DTO inspected |
| Required I04 test sources | PASS (source presence) | Current policy/native/UI/restart test sources; not a claim of additional test execution |
| Diff whitespace | PASS | `git diff --check`, no output, exit 0 |

VERIFIED FACT and historical supplied results are explicitly distinguished above. No absent console transcript is reconstructed. Adaptive closure ran only required app/test packaging and CatalogUiTest; no broad suite, RF001, native or policy rerun and no data reset.

## Adaptive closure

EXPECTED/CONTRACT INVARIANT: window width, not padded content width, determines COMPACT <600 dp, MEDIUM 600–839 dp and EXPANDED >=840 dp.

VERIFIED FACT: `CatalogScreen` reads observable `LocalWindowInfo.containerSize` with current density before insets/margins. The same computed column count drives the actual rows and class tag; no device-model detection or injected test width is used.

`adaptiveWindowThresholdsRecomputeAndKeepCatalogUsable` resizes the real emulator window to 599 → 600 → 839 → 840 → 599 dp. It checks Android WindowMetrics, the class tag, actual first-row product membership, search, detail attributes and editable fields/save availability at every step. This observes all three classes and recalculation in both directions. Activity recreation, when triggered by resizing, reattaches the same synthetic fixture without replacing window information.

Direct `CatalogUiTest` result: `OK (3 tests)`, 44.724 seconds. Original FR/EN, continuous-entry and cancelled-picker test bodies remain intact and passed. Emulator restored to physical 320x640, density 160, no size override. Exact commands/output are recorded in `qualification.txt`.

## Stock and RF001 semantics

EXPECTED/CONTRACT INVARIANT: durable managed media precedes the reference transaction; cleanup follows commit. No negative stock, broken reference or forbidden mutation is acceptable. Authorization remains in application authority and mutations use the existing coordinator/UoW.

VERIFIED FACT from the fresh passing restart assertions:

- BEFORE: no committed product or movement; exactly one private orphan allowed; missing references = 0.
- AFTER: one product survives, stock = 3, image resolves, exactly one movement and one price-history entry; missing references = 0.
- Both phases assert a different current PID from the persisted preparation PID, no restored authenticated session, and passing SQLite quick/full/FK checks.

The prior external `adb force-stop` and marker-before-stop sequence is supplied as verified history in the mission. Existing preparation logs end with `Process crashed.`; that alone is not proof of a successful recovery. Fresh VERIFY results provide the recovery evidence. See `rf001-before.txt` and `rf001-after.txt` for actual commands and output.

The supplied native 5/5 result covers CRUD/archive history, duplicates/search, invalid stock and history targets, Employee refusal, Owner-authorized history deletion without attendance deletion, rollback of stock when movement persistence fails, cleanup failure and media-input faults. Source assertions were checked; this review does not claim an additional native run.

## Security and limits

VERIFIED FACT (source review): I04 authorization uses the existing session mutex, current actor checks and transaction coordinator. Fixed movement deletion does not dispatch to attendance. Cleanup checks references including archived products. No schema, dependency, Desktop, spikes or tooling source change appears in the Git inventory. No sensitive values are reproduced in these evidence files.

EXPECTED/CONTRACT INVARIANT: preserve auth, permissions and UoW non-regression. The bounded tests above support this; they are not a new full I01–I03 regression campaign or a production certification. Adaptive proof is instrumentation/semantics-based, not screenshot-based. Business logic, authorization, persistence, media and stock were not changed by the adaptive closure.

No commit, staging, push or tag performed. Expected acceptance commit (not created):
`feat(android): I04 implement catalog media and stock`

Next: final I04 acceptance review only.
