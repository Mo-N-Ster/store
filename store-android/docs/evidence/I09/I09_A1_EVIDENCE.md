# I09-A1 implementation evidence

Scope completed: public reporting API contracts, immutable document snapshot,
and pure domain calculations only. No report data source, application wiring,
UI, PDF renderer, persistence, or I09 acceptance is claimed.

`ReportFilter` has an explicit `[fromInclusive, toExclusive)` calendar-date
interval (maximum 3,660 days) and a display row cap (1–200). The row cap is not
an aggregation limit: the domain calculation consumes the complete filtered
sale set supplied by its caller. `DocumentModel` copies and wraps its column,
row, cell, series, and point lists for a stable future I10 handoff.

Validated invoice totals alone contribute revenue and transaction count;
cancelled invoices are counted separately. Average basket is zero for no
validated sales; zero transactions produce zero. Average basket preserves exact
division when terminating. For a repeating quotient it uses `MathContext`
DECIMAL128 (34 significant digits, HALF_EVEN); this is internal report
precision, not currency display formatting. The reporting authorities require
source-compatible monetary behavior but do not specify average-basket rounding
for repeating quotients.

Discount allocation uses the existing
`line_total × invoice_total / invoice_subtotal` formula. Terminating
proportions remain exact. Repeating non-negative proportions use DECIMAL128
precision with `DOWN`, so each intermediate line does not exceed its exact
proportional share. The final source line receives the exact residual. Every
result is checked for `0 <= allocation <= line_total` and the allocated sum
must compare exactly equal to the invoice total; invalid bounds fail rather
than silently changing values. Output order follows the immutable invoice-line
order, making residual placement deterministic. This is internal allocation
precision; display formatting belongs to a later presentation layer. The
source does not define a different residual distribution policy, and no
currency or tax allocation rule is inferred here.
Estimated margin is labeled as estimated and records whether any absent
snapshot cost fell back to unit price; it is not certified accounting profit.

The JVM tests cover high-precision values, exact decimal aggregation, exact
and repeating average division, three-line repeating allocations, tiny totals,
zero-value lines, full discounts, per-line bounds, exact conservation,
deterministic repeatability, zero sales, cancelled sales, disclosed missing
costs, date boundaries, bounded filter validation, and defensive copies.

Known limitation: DECIMAL128 is finite for mathematically repeating results.
The residual guarantees exact invoice-total conservation but places the final
finite-precision remainder on the last invoice line. The authoritative
reporting sources do not specify a distinct rounding mode or residual-line
policy; this deterministic computational policy is exposed here and does not
claim tax/accounting allocation semantics. Currency display formatting remains
outside these domain calculations.

Validation (offline, after final source edits):

- `bash ./gradlew --offline :verifyArchitecture :testing:test --rerun-tasks` — PASS
- `git diff --check` — PASS
- Seven-module architecture allowlist — PASS

The first direct `./gradlew` attempt could not execute because the wrapper
script lacks executable permission in this checkout; invoking it through bash
worked. No I09 acceptance is claimed by this A1 evidence.
