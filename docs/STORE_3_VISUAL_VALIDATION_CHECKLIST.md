# STORE 3.0 — Visual release gate checklist

Status: **REQUIRED BEFORE RELEASE**
Candidate version during audit: `2.0.1`

## Test environment

Record before starting:

- Windows edition/version and x64 architecture
- device model, touch capability and display scaling
- packaged artifact SHA-256
- clean or existing STORE data profile
- theme and accessibility settings

Use the packaged executable or installed NSIS candidate, never the Vite development server. Protect existing data with an external backup before using an existing profile.

## Resolutions

Run every critical screen at:

- 1280×800 landscape
- 1024×768 landscape
- 800×1280 portrait
- 1366×768 desktop
- 1920×1080 desktop

For each cell record `OBSERVED PASS` or `OBSERVED FAIL`, tester, date and screenshot reference.

## Checks for every screen

- no clipped content or overlapping controls
- no global horizontal scrollbar
- primary action visible and reachable
- 44px touch targets where applicable
- readable labels, errors and statuses
- Tab, Shift+Tab, Enter, Space and Escape behavior
- logical focus after navigation/dialog close
- drawers and modals stay inside the viewport
- portrait and virtual-keyboard use remain operable

## Required screens

- Login and password recovery
- Setup: all six steps, invalid fields, password eye, review, commit, Ready and recoverable failure
- Shell navigation and back flow
- POS: catalog, quantity, persistent cart, cash session, checkout, change, invoice, print and history drawer
- Products and product details
- Stock, adjustment and inventory validation
- Purchases, supplier, draft, validation and cancellation
- Team, accounts and account detail
- Presence and correction
- Dashboard and KPI states
- Reports, filters, tables and charts
- Administration home
- Users and temporary-password lifecycle
- Permissions
- Settings and SMTP section
- Backups, restore confirmation and failure state
- Diagnostics
- Danger Zone confirmation

## Themes and accessibility

Repeat critical flows in:

- light
- dark
- Windows high contrast
- reduced motion
- keyboard only
- touch where hardware supports it

## Release evidence

Attach screenshots for each required resolution and at least one recording of:

1. fresh Setup through Ready;
2. complete POS sale through invoice;
3. backup → mutation → restore verification;
4. Administration backup/restore and Danger Zone confirmation without destructive reset.

Any unresolved clipping, inaccessible primary action, unusable portrait flow or hidden destructive consequence blocks release.
