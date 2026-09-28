# STORE 3.0 — Draft release notes

Status: **DRAFT — NOT PUBLISHED**
Audit package version remains `2.0.1`; a human decision is required before any `3.0.0` version change.

## Highlights

- tablet-first application shell and reversible navigation
- redesigned offline-first POS and invoice workflow
- daily operations for stock, inventories, purchases, team and presence
- backend-derived permissions and default-deny IPC authorization
- dashboards and reporting from existing local business data
- structured Administration for accounts, settings, backups and diagnostics
- six-step first-run wizard for shop, Primary Owner and essential preferences
- improved keyboard, touch, contrast, reduced-motion and FR/EN support

## Data compatibility

- local SQLite remains the source of truth
- migrations and legacy-data preservation have automated coverage
- automatic daily backups remain local with seven retained copies
- backup validation requires SQLite integrity, essential tables and an active Owner
- downgrade compatibility is not promised

## Known limitations before release approval

- real visual validation is not complete
- full installer/uninstaller/reinstall lifecycle is not complete
- a representative full STORE 2.0.1 packaged upgrade has not been executed
- Setup persists Primary Owner and preferences in two commits
- restore does not require a second Owner reauthentication
- backups are not encrypted
- detailed Owner audit remains partial
- runtime dependency audit currently reports a high-severity Nodemailer finding
- Windows artifacts are not code-signed and may trigger reputation/security warnings

## Release decision

Do not distribute this draft as a public STORE 3.0 release until the blockers in the Phase I report are closed and independently revalidated.
