# I03 work in progress — NOT ACCEPTED

Baseline 439a92ff0b5bfae0e315d2c7ea437b785846896b, main, precheck clean.
Only store-android. No schema delta: I02 owner/UoW/Coordinator/scoped repositories
are extended with trusted security operations; public API receives explicit DTOs.
No I04, no Desktop mutation or real profile access.

## Dependency decision before addition

Use the architecture-selected at.favre.lib:bcrypt 0.10.2, pinned; runtime transitive
at.favre.lib:bytes 1.5.0 from its official Maven POM. Java 7 Android-compatible,
no Android permission or native service. Apache-2.0 upstream; jBCrypt attribution
must remain with packaged library notices. No second bcrypt implementation.
Maintainer documents UTF-8 and an explicit long-password truncate strategy;
use that strategy, cost 10, compare synthetic vectors with installed Desktop
bcryptjs without editing Desktop. No prehash or 72-character restriction.

References verified 2026-09-30:
- https://github.com/patrickfav/bcrypt
- https://repo.maven.apache.org/maven2/at/favre/lib/bcrypt/0.10.2/bcrypt-0.10.2.pom

## Source semantics inspected narrowly

Baseline A/B/C/T/U/AB, roles, FLOW-01/11, XINV-01–08/19; architecture
7–8/12–15; I03/global/security gates. Source identity.ts, loginPolicy.ts,
userPermissions.ts, adminPasswordPolicy.ts, user.validators.ts, settingsPolicy.ts,
auditWriter.ts and targeted auth functions in storeDatabase.ts are read-only.
Identity NFC/ECMAScript whitespace/locale-independent lowercase, accents retained;
both name orders, ambiguity refused. Password unchanged; recovery answer only
trim/lowercase (no identity NFC). Default lockout 5 failures/15 minutes, bounded
3–10 attempts / 1–1440 minutes. Recovery only active owner/manager.

Sessions remain application-memory only. Authority mutex prevents overlapping
switch/login; protected reads and mutations obtain current account/rights from
the accepted owner scope. Transactional failures return after committing only
the permitted lockout/audit; business refusals throw inside the UoW.
No UI input supplies actor/responsible/cash. Navigation is not an authority.
