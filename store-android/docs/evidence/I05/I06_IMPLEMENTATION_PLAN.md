# I06 plan — not implementation authorization

Baseline: `98053aa33ae40912797c646183c7a4440d95fd6e`, branch `main`.
Authority: `docs/STORE_3_IMPLEMENTATION_CONTRACT.md`, I06 and section 9.
Start condition: explicit I05 acceptance. No I06 source was added here.

## Reuse confirmed in current source

- `IdentityAuthority.authorizedRead/authorizedWrite`: current session, effective
  rights and transaction-local authorization; volatile identity and J.4 guards.
- `OperationGuards`: existing critical-operation/cart hooks; wire them to real
  I06 state, not independent UI authorization.
- `DatabaseOwner`, `CommandCoordinator`, `UnitOfWork`, Room repository leases:
  one owner and serialized atomic writes. No parallel database or nested UoW.
- Catalog products, stock movements, current price records and media ownership;
  existing team actors and attendance remain unchanged.
- Existing Room `CashEntity`, `InvoiceEntity`, `InvoiceLineEntity`, `PaymentEntity`
  and submitted-command entity; invoice canonical version/request fields already
  exist. Their presence does not mean I06 use cases are implemented.
- Cash/sales application repositories currently expose read-only `session` and
  `invoice` operations. Existing DAO inserts are infrastructure building blocks,
  not an authorized sales API.
- Spatial theme, adaptive navigation, FR/EN conventions, safe typed failures.

## Ordered implementation scope after acceptance

1. Define narrow cash/sale/receipt/cancellation API DTOs and typed results. Port
   the authoritative rounding, discount and cash rules; do not invent policy.
2. Extend trusted repository ports and fixed Room operations for current cash,
   products, payments, invoices/lines and submitted-command journal. Reuse schema
   where sufficient; any newly necessary migration requires contract review.
3. Implement cash open/close/history, one open cash session per actor, expected
   versus counted totals and immutable closing snapshot; connect switch guards.
4. Freeze the normalized command and stable key before submission. Implement
   versioned deterministic canonicalization and application-scope execution.
   In one UoW recheck current actor/rights/cash and write invoice, lines, payment,
   stock, movements and canonical proof. Exact replay must not recheck already
   consumed stock as a new sale. Unknown or historical-null proof rejects replay.
5. Implement cancellation with reason >=3 characters, authorized single
   compensation and preserved history. No invented retroactive cash accounting.
6. Add adaptive POS, receipt/history and explicit ambiguous-result resolution
   after reauthentication. Zero-quantity lines are removed; do not automatically
   resubmit after restart or generate a new key following a timeout.
7. Qualify targeted JVM rules, real Room atomicity, then lifecycle/Compose. Include
   G-SALE/G-RF004/G-CANCEL, direct denied calls, concurrent double submission,
   failure between every durable write, kill before commit/after commit before
   response, exact replay and changed actor/cash/cart/discount rejection, no
   automatic attendance/cash closure, rotation/IME and three window classes.

## Boundaries

No PDF/printing until I10/I11, bank/Mobile Money, fractional quantities, durable
draft cart, new discount rules, new architecture or dependencies by default.
No transaction performs PDF, SMTP or worker operations. Preserve I02–I05,
existing audit scope, RF001–004, subtractive permissions and owner protection.
Evidence must record actual commands/results, durable counts before/after,
canonical equivalence, real process IDs and clean adaptive captures.

Readiness: planning prepared; implementation remains gated by I05 acceptance.
