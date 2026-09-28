# RF-004 — Closure report

Date : 2026-09-28. Correction RF-004 uniquement, après autorisation additive.

Root cause:
- Retour par clé avant vérification acteur/caisse/commande ; remise demandée non persistée.

Migration:
- YES — 18, sale-idempotency-command : invoices.idempotency_request TEXT nullable. Migration ordonnée/transactionnelle existante ; aucun backfill/reset.

Canonical command definition:
- Tuples JSON versionnés : [1, [[productId, quantity], ...], requestedDiscount, receivedMode].
- IDs/quantités entiers positifs sûrs ; tri ID puis quantité ; doublons conservés sans fusion. Ordre des propriétés JSON et ordre des lignes sans effet.
- Remise demandée finie non négative, défaut 0, avant plafonnement/désactivation ; montant reçu fini non négatif sous ['explicit', valeur], ou ['default'] si omis. Aucun nouvel arrondi : calculs de vente inchangés.
- Aucun timestamp, prix courant ou numéro généré. Persistance dans la transaction de vente.
- Acteur serveur identique à employee_id, caisse actuellement ouverte de cet acteur identique au cash_session_id original, statut validated et commande strictement identique requis avant retour existant. Sinon refus.
- Replay valide : résultat existant sans seconde écriture métier ni revalidation du stock/prix courant.

Historical NULL policy:
- Refus, sans reconstruction ni modification historique. Test NULL avec contexte original rétabli seulement dans la fixture, pour isoler cette garde.

Files changed:
- backend/src/domain/sale/canonicalSale.ts
- backend/src/database/storeDatabase.ts
- backend/src/database/migrations.ts
- tests/unit/backend/invoiceIdempotencyRF004.test.ts
- tests/unit/backend/migrations.test.ts
- docs/CURRENT_APPLICATION_BASELINE.md
- docs/RF004_CLOSURE_REPORT.md

RF-004 reproducer:
- PASS ; assertions originales conservées.

Replay-equivalence tests:
- PASS : nouvelle vente, replay identique/lignes inversées, autre acteur avec/sans caisse, identité renderer forgée, panier/remise/montant différents, montant omis, caisse fermée/remplacée, réglage remises modifié après commit.
- Comparaison intégrale invoices/invoice_lines/payments/stock_movements/products : aucun second effet durable après replays testés.

Historical migration test:
- PASS : fixture pré-18 sur disque avec vente/lignes/paiement/mouvements ; ajout NULL seul, comparaison historique avant/après, integrity_check=ok et foreign_key_check vide, réouverture/idempotence migration, replay historique refusé sans mutation.
- Base neuve couverte par migrations.test.ts. Aucun profil personnel utilisé.

Targeted tests:
- PASS : 4 fichiers, 28 tests (RF004, cashPolicy, migrations, maintenanceIntegration).
- Première tentative sandbox bloquée avant tests par spawn EPERM ; exécution autorisée ensuite via configuration SQLite Node isolée existante.

Full suite:
- PASS : npm test, 56 fichiers, 291 tests, 0 FAIL, 0 skipped ; une seule passe complète exécutée.
- Une invocation préalable avec arguments a été mal transmise par npm/PowerShell : aucun test trouvé/exécuté. Commande sans arguments ensuite ; rebuild SQLite Node réussi sans arrêter de processus utilisateur.

Lint:
- PASS : npm run lint, zéro warning.

Build:
- PASS : npm run build, TypeScript + Vite ; pas de packaging.

git diff --check:
- PASS.

SQLite/integration:
- Inclus dans la suite complète : RF001/RF002/RF003, maintenance, migration et recovery ; bases isolées.

Data/history preserved:
- PASS sur fixtures comparées ; aucune donnée personnelle ouverte/modifiée. Version applicative 2.0.1 inchangée.

Protected-feature regressions:
- Aucune détectée par les 291 tests ; pas de qualification humaine supplémentaire. UI, RBAC, J.4, calculs, achats, médias, présence, SMTP/PDF/packaging inchangés.

RF-004: CLOSED

Remaining production blockers:
- Artefact aligné source non généré ; revue/gel et qualification humaine/install/upgrade/restauration physique encore requis selon baseline ; audit tooling historique à clarifier. Aucun audit global supplémentaire ni qualification production revendiquée.

RF-004 CLOSED — ready for production qualification subject to remaining release gates.
