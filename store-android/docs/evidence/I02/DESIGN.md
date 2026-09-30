# I02 persistence design (validation status in RESULTS.md)

Baseline `8bbbbc8f5be767522b3e02a798417b376c83f15a`, main; initially clean.
Scope: domain values, application persistence ports, infrastructure Room/runtime,
infrastructure native tests/exported schemas and this evidence. No UI/app wiring
change. No database opens from the I01 smoke screen. No I03 use case is provided.

## Logical model / A–U coverage

| Domains | Authoritative storage / relationship |
|---|---|
| A/B | users, roles, permissions, role_permissions, user_roles, user_permission_denials; account username/email unique; role code unique; permission module/action unique; join PKs and FK deletes follow source |
| C | live session remains volatile; pending_commands holds only ambiguous submitted command intent, never a logged-in session |
| D | dashboard derived from stored operational records, no second truth |
| E | cash_sessions → users (operator/closer); unique reference; conditional one-open-per-user enforced on INSERT/UPDATE |
| F/G | invoices → users/cash_sessions, invoice_lines → invoices/products, payments → invoices/cash_sessions; key and payment-per-invoice unique; store/currency/product/category/price/cost snapshots; nullable versioned replay proof without backfill |
| H/I/J | products with unique nullable reference, imageRef, stock and threshold; user photo retained; managed media content protocol is later I04, no new media authority/table |
| K | stock_movements → products; original reason/reference/price/time history |
| L | inventory_counts → users; inventory_count_lines → inventory/products; unique reference and inventory/product pair |
| M/N | suppliers name unique; purchases → suppliers/users; purchase_items → purchases/products; references/keys and purchase/product pair unique |
| O | employees → nullable unique user, unique code; SET NULL on account deletion |
| P | attendances → users/corrector; unique nullable sessionRef; original times/reason/correction fields |
| Q | reports derived; no analytic copy |
| R | messages → users; request key unique; message_reads/deletions → message/user with composite PK; notifications → products |
| S | email_report_logs: durable queue state, attempts/dates/error and attachment bytes; no SMTP/Worker implementation |
| T | settings typed key/value repository, no SQL/table selection |
| U | audit_logs with actor/responsible/cash historical snapshots; intentionally no FK on historical actor IDs, matching source |

Technical: generation_metadata and pending_commands; private AtomicFile migration
journal outside SQLite is readable before opening Room. 29 tables / 37 FK edges.
Production Room schema **1**, independent from Desktop18. The exported JSON is
under `infrastructure/schemas/...StoreDatabase/1.json`. SyntheticV2/schema2 is
test-only and adds just a marker table; never shipped in main sources.

Source parity inspected read-only, not re-audited: Desktop schema.ts, targeted
RBAC/snapshot/canonical-proof additions in migrations.ts, cashPolicy.ts and the
sale rounding call sites. No source DB copied or Desktop migration implemented.
Binary64 storage retained; injected MoneyParity uses ties toward +infinity,
not Kotlin banker's rounding. CalendarParity explicitly selects UTC/local per
caller; no universal calendar correction. Quantities retain safe integer bounds.

## Boundaries / lifecycle

Application-api/presentation receive no new authority. Internal Room entities
and StoreDao remain infrastructure-only. Typed application repositories project
safe domain records rather than exposing entity/hash objects. No generic CRUD,
raw SQL, filesystem path or table name is accepted by the public factory.

AndroidPersistence creates a RoomDatabaseOwner rooted in noBackupFilesDir under
generations/main. An OS file lock excludes another owner. Exactly one Room
instance is held; repositories never open connections. Owner-controlled temporary
connections exist only before Room opening (version/recovery/snapshot checks).
WAL's pool is internal to that same owner, not independent repository databases.

CommandCoordinator → UnitOfWork → Room writer IMMEDIATE transaction. One mutation
mutex plus a read/write/maintenance mutex serialize accepted scopes. Scope leases
expire on exit; repository calls from another coroutine job fail. No retries,
intermediate commit or external effect is added. Maintenance waits for accepted
work, checkpoints with result verification, closes the pool, then reopens through
the same owner. Nested owner operations are rejected instead of deadlocking.

SQLite driver initialization applies FK, FULL and bounded busy timeout (750ms).
Room's onOpen reapplies FULL after Room's WAL defaults. These settings require
effective reader/writer tests; configuration alone is not treated as proof.
Room also overrides the reader busy timeout; the owner reapplies 750ms on
each leased reader before repository access. Reader synchronous=NORMAL is
allowed; the required FULL durability is verified on the writer.

Room has no CHECK/partial-index annotation: fixed internal INSERT/UPDATE triggers
encode source status/value constraints and conditional cash uniqueness, alongside
Room-exported unique indices/FKs. Triggers live in StoreConstraints; later schema
changes must version both this contract and exported Room schema, never silently
rewrite an accepted v1. No claim of globally immutable history is added.

## Migration / recovery

Acquire ownership → inspect supported version → recovery hook → for an existing
older DB, checkpoint via an exclusive maintenance connection → close it → copy
stable main DB into private pre-migration.db → sync file/directory → verify hash,
integrity and FKs → sync AtomicFile PREPARED journal → let Room migrate in its
transaction → validate opening. No copy occurs while Room is open. No media/file
copy occurs in a business transaction. Snapshot and journal are retained.
Both reside in noBackupFilesDir/maintenance/<generation>, outside the active
generation directory, not in shared storage or the database being migrated.

On restart the hook validates the snapshot digest and old/target version before
Room opening; corruption or an unrecognized migration plan blocks, never resets.
Future schema is refused before constructing Room. No destructive fallback,
automatic downgrade or artificial history reconstruction. Full generation
switching, archives, retention, restore/reset UI and scheduling remain I12.

API references: [Room callbacks](https://developer.android.com/reference/kotlin/androidx/room/RoomDatabase.Callback),
[SQLite pragmas](https://www.sqlite.org/pragma.html). Driver's pinned local API
was inspected with javap; no dependency substitution or new library was needed.
