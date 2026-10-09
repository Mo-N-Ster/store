# STORE I09 — Implementation Plan

Status: IMPLEMENTATION PLANNED — NOT ACCEPTED
Branch: feature/store-i09
Baseline: 3691eccedc9855686b011e34c7420f17aaf6c085

## 1. Authority

- docs/STORE_3_IMPLEMENTATION_CONTRACT.md — I09
- docs/STORE_3_FUNCTIONAL_BASELINE.md — D/Q/Z
- docs/STORE_3_ANDROID_ARCHITECTURE.md
- Accepted I01–I08 implementation and tests

Higher-priority invariants prevail over implementation convenience.

## 2. Deliverables

- Authorized dashboard and operational KPIs.
- Seven reports:
  1. Synthèse
  2. Ventes
  3. Articles
  4. Stock
  5. Achats
  6. Équipe
  7. Finances
- Explicit filters and applied filter state.
- Accessible Compose charts and equivalent detailed tables.
- Immutable report DocumentModel snapshots for I10.
- Empty, loading, error and retry states.

No PDF generation, SMTP or accounting engine.

## 3. Architecture

domain:
- Pure, independently testable arithmetic and aggregation rules.
- Exact monetary calculations using BigDecimal.

application-api:
- ReportingService and immutable public DTOs.
- Validated filters and bounded output models.
- DocumentModel containing report identity, applied filters,
  period, currency, columns, rows and chart datasets.

application:
- ReportingAuthority using IdentityAuthority.authorizedRead.
- Dashboard: DASHBOARD:READ.
- Reports: FINANCES:READ.
- Additional domain permissions for restricted report sections.
- Authorization verified against the effective current identity.

infrastructure:
- ReportingRepository backed by Room projections.
- Existing DatabaseOwner read transaction and lease rules.
- Parameterized bounded queries.
- No new analytics database or unapproved migration.

presentation:
- Existing StoreHome extended.
- Seven reporting views with explicit Apply/Reset.
- Responsive Compose charts, legends and tables.
- Reject stale asynchronous results after filters/identity changes.

app:
- Manual composition using existing application lifetime services.
- No direct DAO or business arithmetic in Activity or Compose.

testing:
- JVM domain, filtering and RBAC tests.
- Room synthetic fixture tests.
- Compose navigation, accessibility and adaptive tests.

## 4. Calculation invariants

- Validated invoices alone contribute to revenue.
- Cancelled invoices remain visible separately.
- Average basket is zero for zero validated transactions.
- Discounts allocated consistently to invoice lines.
- Gross margin is labelled ESTIMATED when supported.
- Unsupported cost observations must not become certified margin.
- Stock counts describe current state, not historical stock.
- Purchases distinguish current status and period semantics.
- Open attendance sessions contribute no completed hours.
- No accounting profit, taxes, ROI or stock valuation claim.
- No money conversion to Double for calculations.
- Date/time boundaries are explicit and fixture-tested.

## 5. Safety

- Never infer complete totals from a truncated page.
- No unbounded fetch, N+1 queries or data warehouse.
- No unauthorized fields in aggregates or DocumentModel.
- No old report attached to newly failed filters.
- No data writes to repair reporting inconsistencies.
- No changes to accepted Room schema without explicit review.
- No mutation outside store-android.
- No destructive database/test-profile operations.
- No commit, merge or push before acceptance.

## 6. Gates

A: Contracts and exact-calculation unit tests.
B: Authorized Room projections and native fixtures.
C: Dashboard Compose and permission tests.
D: Seven reports, filters, charts, tables and snapshots.
E: Regression, date/DST, scale and adaptive validation.
F: Evidence review, G-PASS and controlled commit.

Required commit message:
feat(android): I09 implement dashboard and reporting
