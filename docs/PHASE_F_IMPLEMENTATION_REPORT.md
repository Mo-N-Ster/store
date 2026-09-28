# STORE 3.0 — PHASE F IMPLEMENTATION REPORT

## 1. Executive summary
Phase F modernizes Home and Reports using only existing authorized APIs. It adds no SQL, IPC, schema, accounting engine or dependency. Phase G was not started.

## 2. Baseline
23 test files, 99/99 tests, 9 security tests, lint/build/diff/Electron passing, version 2.0.1.

## 3. F0 reporting capability audit
Audited `dashboard`, `reports`, invoices/history, products, purchases, attendance history, export PDF/e-mail, existing SVG/CSS charts and all current filters.

## 4. Existing reporting architecture
SQLite aggregations are returned through protected IPC. The renderer formats and presents them; it does not persist an analytical store.

## 5. Existing IPC/reporting capabilities
`dashboard` supplies today/month/30-day overview. `reports` supplies validated-sales summaries, product/category rankings, price observations, movements and current stock. Existing list APIs support status-explicit Purchases and privacy-limited Team views. No new endpoint was added.

## 6. KPI contract

| KPI | Exact definition | Source | Period | Cancellation treatment | Permission |
|---|---|---|---|---|---|
| Revenue today | Sum `total_amount` for invoices with `status='validated'` on SQLite local date | dashboard | Local today | Cancelled excluded | DASHBOARD:READ |
| Transactions today | Count of validated invoices on SQLite local date | dashboard | Local today | Cancelled excluded | DASHBOARD:READ |
| Average basket today | Revenue today / validated invoice count; zero when count is zero | dashboard values | Local today | Cancelled excluded | DASHBOARD:READ |
| Out-of-stock products | Active products where current `stock_quantity=0` | products | Current state | N/A | PRODUCTS:READ |
| Month revenue | Sum validated invoice totals from current local calendar month | dashboard | Local month-to-date | Cancelled excluded | DASHBOARD:READ |
| Period revenue | Sum allocated line revenue `line_total × invoice_total / invoice_subtotal` for validated invoices | reports.salesSummary | Inclusive selected dates | Cancelled excluded | FINANCES:READ |
| Period transactions | Distinct validated invoice count matching filters | reports.salesSummary | Inclusive selected dates | Cancelled excluded | FINANCES:READ |
| Period average basket | Period revenue / distinct validated invoices | reports.salesSummary | Inclusive selected dates | Cancelled excluded | FINANCES:READ |
| Units sold | Sum line quantities on validated invoices | reports.salesSummary | Inclusive selected dates | Cancelled excluded | FINANCES:READ |
| Estimated gross margin | Allocated validated line revenue minus `quantity × COALESCE(unit_cost, unit_price)` | reports.salesSummary | Inclusive selected dates | Cancelled excluded | FINANCES:READ |
| Product/category ranking | Sum validated allocated revenue, aggregated by product/category | reports topProducts/topCategories | Inclusive selected dates | Cancelled excluded | FINANCES:READ |
| Low-stock products | Current active products with quantity ≤ minimum threshold | reports.stockSummary | Current state | N/A | FINANCES:READ |
| Out-of-stock report | Current active products with quantity = 0 | reports.stockSummary | Current state | N/A | FINANCES:READ |
| Stock movement entries/exits | Positive quantities / absolute negative quantities grouped by selected grain | reports.movements | Inclusive selected dates | Reversals retain their explicit reason | FINANCES:READ |
| Validated purchase total | Sum `total_amount` of currently VALIDATED purchases whose `created_at` falls in selected period | purchases | Inclusive creation dates | Draft/cancelled excluded | PURCHASES:READ |
| Purchase status counts | Count of current DRAFT/VALIDATED/CANCELLED rows created in period | purchases | Inclusive creation dates | Shown separately | PURCHASES:READ |
| Attendance sessions | Count of attendance rows whose start date is in the selected period | history(personnel) | Inclusive start dates | N/A | FINANCES:READ + PRESENCE:READ UI visibility |
| Completed hours | Sum existing `hours` only for rows with an end time | history(personnel) | Inclusive start dates | Open sessions excluded | FINANCES:READ + PRESENCE:READ |

No inventory valuation or accounting profit KPI is displayed. `stockValue` is intentionally omitted because the backend uses current selling price, not a validated inventory-cost method.

## 7. Dashboard information architecture
Priority alert → four today/current KPIs → 30-day sales context → stock attention → recent transactions → month context/drill-down.

## 8. Dashboard implementation
Uses Design System Alert, Button, Empty/Error states, Skeleton and KpiCard. Three batched IPC calls feed the full page; no per-card request.

## 9. Operational attention model
Out-of-stock outranks low-stock; statuses include text and counts, never color alone. Action reuses Products/Reports rather than duplicating mutation workflows.

## 10. Sales report
Validated revenue/count/average plus explicit cancelled count and transaction list. Cancelled invoices never enter sales KPIs.

## 11. Product report
Revenue-based product/category ranking and existing price observations. Criterion is visible; detailed tables supplement charts.

## 12. Stock report
Current state is explicitly separated from period-filtered movements. No invented stock-cost valuation.

## 13. Purchase report
Uses the existing purchase list once, filters bounded rows locally, and separates DRAFT/VALIDATED/CANCELLED. Only current VALIDATED rows enter the displayed total.

## 14. Team report
Uses existing attendance history once. Shows operational session/duration facts only; open-session hours are excluded from totals and no productivity score is created.

## 15. Finance presentation
Shows validated sales revenue, explicitly named estimated gross margin and validated purchases separately. It states that this is not accounting and never computes `sales - purchases = profit`.

## 16. Period/filter model
Shared draft filters: inclusive local date inputs, grain, product and category; explicit Apply, 7-day, 30-day and Reset. Applied filters remain authoritative until a load succeeds.

## 17. Current-state vs period metrics
Stock counts are labelled current; stock movements, sales, purchases and attendance use period semantics documented above.

## 18. Cancellation semantics
Cancelled sales are listed/countable but excluded from validated sales summaries. Cancelled purchases and drafts are separately counted and excluded from validated purchase totals.

## 19. Currency/number/date formatting
Currency comes from STORE preferences; Intl/local locale is used. No currency symbol is hardcoded. Local calendar helpers avoid UTC day drift.

## 20. Chart strategy
No dependency added. Existing local SVG/CSS line/bar/ranking capabilities are reused only for trend/ranking questions.

## 21. Chart accessibility
Charts have accessible labels and text/list/table equivalents. Important information is never chart-only.

## 22. Tables/data lists
ResponsiveTable wraps dense data; renderer caps displayed operational tables at 250 rows.

## 23. Drill-down/navigation
Home links to Reports and Products using existing reversible navigation. Report tabs retain period/filter state.

## 24. Export/PDF compatibility
Existing `exportReportPdf` and e-mail PDF flow remain unchanged. The applied period and selected report name the output.

## 25. Permission model
FINANCES:READ controls Reports navigation/backend data. PURCHASES:READ and PRESENCE:READ control corresponding tabs/calls. No role-based authorization was introduced.

## 26. Direct-access protection
E.5 registry remains default-deny; `reports`, `history` and purchase APIs retain backend protection.

## 27. Elevation expiry behavior
App clears permission state immediately before refresh; mounted privileged report content unmounts while backend independently rejects expired elevation.

## 28. Data-leak prevention
Loading clears prior report data. Permission loss sets permissions to null before refresh. Employee attendance scoping from E.5 remains tested.

## 29. Responsive behavior
Four KPI columns collapse to two then one; analytical two-column layouts become vertical; filter bar becomes two then one column.

## 30. Touch
Design System controls retain minimum touch targets; native filter controls use the same minimum.

## 31. Keyboard
Tabs retain arrows/Home/End behavior; controls are native keyboard controls; responsive tables are focusable regions.

## 32. Focus
Report changes focus the report heading; modal Escape/outside behavior remains inherited from existing backdrop.

## 33. Accessibility
One page heading, labelled tabs/filters/tables, text statuses, semantic loading/empty/error states and chart alternatives.

## 34. Light/dark/high-contrast
Only design tokens are used. High contrast adds stronger borders/chart outlines.

## 35. Reduced motion
No required animation; reduced-motion disables chart transitions.

## 36. i18n
All new visible strings exist in FR and EN.

## 37. Performance
No measured runtime speed claim is made. Report render lists are capped at 250; ranking source is already backend-limited to 200. No virtualization dependency.

## 38. IPC-call analysis
Home: 3 parallel calls total. Reports initial/apply: 3 base calls + at most 2 permission-dependent calls. No N+1 request pattern and no call per KPI/row.

## 39. Large-dataset observations
Backend report aggregation remains SQL-side. Purchases/attendance list APIs are unpaginated current limitations; DOM rendering is capped, but transport size remains a functional/performance debt.

## 40. Files added
`reportingModel.ts`, `reporting.css`, Phase F tests and this report.

## 41. Files modified
Home, ChartsPage, DashboardPage permission plumbing, local date/number helpers, App expiry fail-close, i18n and style imports.

## 42. Dependencies
None.

## 43. Tests added
14 Phase F tests: metric formulas/status exclusions, dates, filters, permissions, stale-data prevention, accessibility, responsiveness, IPC count and Presence security regression.

## 44. Full test result
PASS — 24 files, 113/113 tests, 0 failed, 0 skipped.

## 45. Security test result
PASS — the 9 E.5 security tests remain green inside the full suite.

## 46. ESLint
PASS — zero warnings.

## 47. Build
PASS — TypeScript project build and Vite production build; 1,980 modules transformed. Final JS 395.13 kB (117.44 kB gzip), CSS 83.61 kB (15.96 kB gzip).

## 48. git diff --check
PASS.

## 49. Electron startup
PASS — remained active without console startup error until deliberate SIGINT.

## 50. Visual/manual validation
Automated Windows UI remains `NOT OBSERVABLE` if `apps: []`; no DOM/CSS test is reported as a visual pass.

## 51. Responsive matrix

| Area | 1920×1080 | 1366×768 | 1280×800 | 1024×768 | 800×1280 |
|---|---|---|---|---|---|
| Dashboard | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Sales | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Products | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Stock | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Purchases | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Team | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Finance | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |

Structural CSS tests cover the required breakpoints, contrast and reduced-motion, but do not replace visual observation.

## 52. KPI matrix
The complete implemented KPI contract is section 6. Unsupported candidates: inventory differences over time, supplier trend, accounting profit, tax, expenses, cash flow and cost-based inventory valuation.

## 53. Report matrix

| Report | Data source | Permission | Filters | Export | Status |
|---|---|---|---|---|---|
| Summary | reports + invoices | FINANCES:READ | Period/product/category/grain | PDF/e-mail | IMPLEMENTED |
| Sales | reports + invoices | FINANCES:READ/POS:READ | Period/product/category/grain | PDF/e-mail | IMPLEMENTED |
| Products | reports | FINANCES:READ | Period/product/category/grain | PDF/e-mail | IMPLEMENTED |
| Stock | reports | FINANCES:READ | Current state + period movements | PDF/e-mail | IMPLEMENTED |
| Purchases | purchases | FINANCES:READ + PURCHASES:READ | Period/status semantics | PDF/e-mail | IMPLEMENTED |
| Team | history(personnel) | FINANCES:READ + PRESENCE:READ visibility | Period | PDF/e-mail | IMPLEMENTED |
| Finance | reports + purchases | FINANCES:READ (+ PURCHASES:READ for purchase value) | Period/product/category | PDF/e-mail | IMPLEMENTED |

## 54. Acceptance matrix

| Criterion | Result | Evidence |
|---|---|---|
| F-AC-001–004 | PASS | DS Home and separate seven-domain Reports IA. |
| F-AC-005–010 | PASS | Sections 6, 17 and 18 define source/formula/status/current-vs-period. |
| F-AC-011–015 | PASS | Sales/Product/Stock/Purchase/Team implementations and tests. |
| F-AC-016–018 | PASS | Finance warning; no profit; margin explicitly estimated and defined. |
| F-AC-019–020 | PASS | Shared controlled filters and applied-filter snapshot. |
| F-AC-021–023 | PASS | Separate Empty, Skeleton, Forbidden and Technical states. |
| F-AC-024–025 | PASS | Existing PDF/e-mail IPC unchanged and exercised by build/regression. |
| F-AC-026–028 | PASS | Chart text/table equivalents and textual statuses. |
| F-AC-029–033 | PASS | Preference currency, Intl/local dates, defined comparison only. |
| F-AC-034–037 | NOT OBSERVABLE | Structural responsive tests pass; no observable UI surface. |
| F-AC-038–040 | PASS | Minimum targets, native keyboard controls, tab focus behavior. |
| F-AC-041–044 | PASS | Token colors, dark inheritance, contrast/reduced-motion CSS. |
| F-AC-045 | PASS | FR/EN additions. |
| F-AC-046–052 | PASS | Effective-permission UI, backend E.5 authority/default deny, fail-closed expiry/loading. |
| F-AC-053–056 | PASS | No migration, transaction/core/accounting/dependency change. |
| F-AC-057 | PASS | Original 99-test surface remains green. |
| F-AC-058 | PASS | 9 E.5 security tests remain green. |
| F-AC-059 | PASS | 14 new Phase F tests. |
| F-AC-060–064 | PASS | Lint/build/diff/startup passed; version 2.0.1. |
| F-AC-065 | PASS | Visual limitation reported honestly. |
| F-AC-066 | PASS | Phase G not started. |

## 55. Deviations
Expected purchase/team aggregate endpoints do not exist. Actual: existing list APIs are fetched once and aggregated with tested pure functions. Impact: correct present scope, but transport is unpaginated. Risk: large local datasets. Follow-up: propose a backend aggregation API in a separately authorized phase.

## 56. Known limitations
No historical inventory differences, supplier trend or purchase validation-date filter. Backend comparison uses its existing equal-length previous period. Product ranking remains limited to the backend top-200 rows.

## 57. Security debt
Operation-scoped elevation, generic authenticated file pickers and second Owner authentication before Restore remain unresolved.

## 58. Functional debt
Persistent inventory/purchase draft resumption remains unresolved. Purchase report periods use creation time because that is the exposed data contract.

## 59. Tooling limitations
Automated native-window visual inspection historically returns no app surfaces.

## 60. Files intentionally untouched
Database schema/migrations, reporting SQL, IPC registry/preload, RBAC semantics, transactions, backup/restore, SMTP, authentication and version.

## 61. Recommendation for Phase G
READY FOR PHASE G authorization after human review. All final gates are green. Phase G requires separate authorization and was not started.
