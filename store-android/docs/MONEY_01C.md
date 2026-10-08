# MONEY-01C persistence contract

Room production version 2 stores monetary BigDecimal values using MoneyText:
strip trailing zeros, then toPlainString. Zero is `0`; exponents, whitespace,
commas, leading plus signs, redundant leading/trailing zeros and negative zero
are rejected on reading. Decoded BigDecimal values strip trailing zeros as the
domain arithmetic does, preserving equality across immediate responses and
replay/restart. Application write responses use the same normalization. Null remains null. No new monetary value passes through
Double, REAL arithmetic, rounding or truncation. INSERT/UPDATE triggers validate
canonical text with string predicates, independently of the Room converter.

The 20 monetary columns are:

| Table | Columns |
| --- | --- |
| cash_sessions | openingAmount, closingAmount, expectedAmount, difference |
| products | price |
| product_price_history | price |
| stock_movements | unitPrice |
| invoices | subtotal, totalAmount, discount |
| invoice_lines | unitPrice, totalLine, unitCost |
| payments | amount, received, change |
| purchases | totalAmount |
| purchase_items | unitCost, totalLine |
| audit_logs | cashAmount |

Cash expectedAmount/difference and historical audit cashAmount permit signs.
Other monetary fields are nonnegative. Existing nullability is unchanged.
Quantities, IDs, timestamps, settings and opaque historical details/canonical
command strings keep their existing representations.

Migration(1,2) executes inside Room's upgrade transaction after DatabaseRuntime's
exclusive ownership, WAL checkpoint, verified snapshot and durable PREPARED
journal. It copies every source table into unconstrained temporary backups before
replacing tables, so foreign-key cascade actions cannot discard dependent rows.
It preserves the original table definitions, changing only the listed REAL
column affinities to TEXT, and recreates indexes and constraints. Foreign keys
remain enabled; checks are deferred during replacement and verified before
migration completes. Temporary backups and DDL roll back together on failure.
No destructive fallback, source-file removal or journal bypass is used.

For legacy finite REAL values only, BigDecimal.valueOf(storedDouble) chooses the
stored binary64 value's shortest round-trip decimal representation, normalized
to plain TEXT. This deterministic rule does **not** recover previously lost
input precision. Malformed storage, nonfinite values and invalid monetary signs
abort migration. The version-1 source and verified recovery snapshot remain
available; automatic retry cannot overwrite the first recovery snapshot.

Captured payment amounts are aggregated using BigDecimal addition in keyset
pages of at most 256 rows. Existing CAPTURED/REFUNDED status semantics determine
inclusion. Reads hold a DEFERRED SQLite transaction within the existing owner
lease, and writes retain their IMMEDIATE transaction, so pagination cannot mix
balance snapshots. Repeated cancellation/refund retains its existing idempotent
behavior.

SyntheticV2 remains an independent version-2 test database with its marker table.
Its migration tests start from the committed v1 schema and constraints, run the
production monetary migration, and add the marker. Production StoreDatabase v2
is never used as a fake v1 input.
