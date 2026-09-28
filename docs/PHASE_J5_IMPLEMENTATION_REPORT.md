# STORE 3.0 — Rapport d’implémentation de la phase J.5

## 1. Executive summary

J.5 expose la traçabilité persistée du stock et des achats, rend les filtres d’achats cohérents et retire les métriques comptables non fondées. **J.5 COMPLETE — AUTOMATED VALIDATION PASSED, VISUAL REVIEW REQUIRED**.

## 2. Pre-J.5 baseline

J.4 : 36 fichiers, 178/178 tests, 0 skip, chaîne technique verte, version 2.0.1.

## 3. Repository data-model audit

Audit de `products`, `stock_movements`, `invoices/invoice_lines`, `purchases/purchase_items`, `suppliers`, `inventory_counts/lines`, utilisateurs et audits. Clés, dates, quantités, statuts, acteurs et références ont été vérifiés dans le schéma et les écritures.

## 4. Relationship matrix

| Relation | Persistée | Base |
|---|---:|---|
| Achat → Fournisseur | Oui | `purchases.supplier_id` FK |
| Achat → Article | Oui | `purchase_items` FK |
| Achat → Mouvement | Oui | type `purchase` + `reference_id` |
| Vente → Article | Oui | `invoice_lines` FK nullable + instantané produit |
| Vente → Mouvement | Oui | type `sale` + identifiant facture |
| Inventaire → Article | Oui | `inventory_count_lines` FK |
| Ajustement → Article | Oui | `stock_movements.product_id` |
| Mouvement → Acteur | Non | aucun champ acteur |
| Mouvement → Source | Selon type | `reason` + `reference_id` |

## 5. Files changed

DB read-side, IPC/RBAC/preload, services, Stock, Achats, Rapports, traductions, tests et documentation J.5.

## 6. Schema status

**NO SCHEMA CHANGE — NO MIGRATION.**

## 7. Dependency status

Aucune dépendance ajoutée.

## 8. Stock information architecture

Les sections réelles restent État actuel, Mouvements, Inventaire et Alertes; les ajustements sont intégrés à l’état actuel sans créer d’onglet vide.

## 9. Current-stock presentation

Article, catégorie, quantité actuelle, seuil et état proviennent de `products`.

## 10. Stock movement model

Chaque ligne expose quantité signée, sens textuel, type, date persistée et référence source éventuelle.

## 11. Movement types

Types réels : vente, achat, annulation d’achat, inventaire, ajustement, initialisation et suppression Article.

## 12. Movement actor

Non affiché : aucun acteur n’est persisté sur `stock_movements`.

## 13. Movement source/reference

Résolution conditionnelle par type vers facture, achat ou inventaire. Ajustements conservent leur référence/raison sans lien fictif.

## 14. Stock cross-navigation

La référence exacte est présentée. Aucun lien heuristique n’est créé; le détail d’achat expose ses mouvements persistés.

## 15. Article movement history

Le filtre `productId` backend réutilise la requête canonique bornée, sans seconde histoire de stock.

## 16. Purchase lifecycle

Brouillon → lignes → validation → mouvements/stock; annulation distincte. Statuts existants uniquement.

## 17. Purchase detail

Fournisseur, statut, dates, acteurs persistés, référence, total, lignes, quantités, coûts et mouvements source.

## 18. Purchase exactly-once invariant

Le moteur existant est inchangé : `VALIDATED` retourne avant toute nouvelle incrémentation; transaction et idempotency key demeurent.

## 19. Purchase→stock traceability

Les mouvements `purchase` et `purchase_cancellation` sont sélectionnés par l’identifiant d’achat persisté.

## 20. Stock→purchase traceability

Le read model résout `reference_id` vers la référence d’achat seulement pour les types achat.

## 21. Supplier traceability

FK réelle et filtrage backend par fournisseur; nombre/lignes/totaux utilisent la même population.

## 22. Inventory traceability

FK inventaire/article, quantités attendue/comptée, acteurs et dates demeurent persistés; mouvements résolus par identifiant d’inventaire.

## 23. Adjustment/correction traceability

Quantité, date, raison et référence persistées. Avant/après restent disponibles dans l’audit métier mais ne sont pas attribués à une ligne par rapprochement heuristique.

## 24. Reporting architecture

Questions séparées par onglet : synthèse, ventes, Articles, stock, achats, équipe et périmètre financier non comptable.

## 25. Report inventory

Ventes validées, classements Articles/catégories, prix observés, flux stock, achats persistés et présences explicites.

## 26. Contextual filters

Date/granularité et Article/catégorie pour ventes/stock; fournisseur pour achats; permissions pour achats/présence.

## 27. Filter semantics

Le filtre fournisseur est transmis à la requête d’achats et réappliqué au modèle KPI/lignes. Article/catégorie restent backend pour les datasets concernés.

## 28. Date semantics

Bornes inclusives. Vente : `invoice_date`; mouvement : `created_at`; achat : validation, annulation ou création selon état; présence : timestamps de présence.

## 29. Granularity

Jour, semaine et mois utilisent des regroupements SQLite déterministes.

## 30. KPI definitions

Ventes/nombre/montant/panier moyen, achats validés/nombre/montant, brouillons/annulations, unités actuelles, alertes et mouvements.

## 31. Chart-selection rationale

Évolution ordonnée pour séries temporelles, barres de classement pour entités, tableaux pour valeurs exactes; équivalents textuels conservés.

## 32. Sales reporting

Uniquement factures validées, quantités et montants réellement persistés; annulations signalées séparément.

## 33. Purchase reporting

Comptage et total des achats validés; brouillons/annulations exclus du total et explicitement nommés.

## 34. Stock reporting

État courant distinct de la période; mouvements entrants/sortants réels. Aucun instantané historique reconstruit.

## 35. Attendance reporting regression

Les données restent issues des présences explicites J.3, jamais des connexions.

## 36. Explicitly unsupported metrics

Profit, marge comptable, ROI, score employé, dette fournisseur, taxes et valorisation stock.

## 37. Explicitly unsupported relationships

Acteur et avant/après par mouvement; relation générique pour types sans source; état historique du stock.

## 38. Query architecture

SQL paramétré, jointures en une requête, détails en trois requêtes bornées par identifiant, sans N+1.

## 39. Query validation

Dates ISO, ordre des bornes, identifiants entiers positifs, types autorisés et limite 1–500 validés backend.

## 40. Authorization

`stockMovements` exige `STOCKS:READ`; `purchaseDetail` exige `PURCHASES:READ`.

## 41. IPC/preload changes

Deux capacités étroites ajoutées dans définition, registre, handler API, preload et service. Aucun SQL générique.

## 42. Performance/query bounds

Mouvements limités à 500, 250 par défaut, tri `created_at DESC,id DESC`; historiques UI limités.

## 43. Accessibility

Filtres nommés, boutons avec libellés, statuts textuels, détails structurés et tableaux complémentaires aux graphiques.

## 44. Responsive behavior

Réutilisation des listes/modales/tableaux responsives existants; revue visuelle aux cinq tailles encore requise.

## 45. Tests added

Ajout de `traceabilityJ5.test.ts` avec 10 contrôles ciblés.

## 46. Stock tests

Relations, types, quantité/sens, référence, date, bornage et absence de champs inventés.

## 47. Purchase exactly-once tests

Garde `VALIDATED`, mouvement transactionnel et relation d’achat conservés; anciennes suites idempotence passent.

## 48. Traceability tests

FK réelles et résolutions type+référence vérifiées; aucun rapprochement par proximité.

## 49. Filter-coherence tests

Fixture mixte fournisseur/date : KPI et lignes retournent uniquement la même population.

## 50. Date-range tests

Bornes début/fin inclusives existantes conservées; avant/après exclus.

## 51. Empty/error-state tests

Chargement efface les anciennes données; EmptyState et ErrorState technique/interdit restent distincts.

## 52. Security tests

Canaux protégés, Cashier refusé sur achats, IPC inconnu `runSql` refusé, preload strict.

## 53. J.4 regression

Switch, blocages caisse/panier/checkout, acteurs, absence d’élévation/inactivité : PASS.

## 54. J.3 regression

Présence explicite multi-utilisateur et découplage : PASS.

## 55. J.2 regression

Médias, backups v2/legacy et mots de passe : PASS.

## 56. Full regression

**37 fichiers, 188/188 tests PASS, 0 skipped**.

## 57. SQLite integrity

PASS sur profils temporaires représentatifs.

## 58. Foreign-key integrity

PASS; relations testées via `foreign_key_list` et contrôles existants.

## 59. ESLint

PASS, zéro warning.

## 60. TypeScript/Vite

PASS; 1984 modules transformés.

## 61. git diff --check

PASS.

## 62. Production dependency audit

PASS : 0 vulnérabilité.

## 63. Electron smoke test

PASS : démarrage sans exception fatale puis arrêt propre.

## 64. Visual validation status

**AUTOMATED VISUAL VALIDATION: NOT OBSERVABLE**. Zéro fenêtre/app contrôlable retournée.

## 65. Human visual checklist

- État actuel et alertes stock.
- Mouvements : type, sens, date, source/référence et absence de faux acteur.
- Historique Article via filtre canonique.
- Liste/détail achat, fournisseur, lignes et effet stock.
- Achat→stock uniquement si persistant.
- Filtres, KPI/table/chart cohérents; résultat vide et erreur distincts.
- Desktop, tablette paysage/portrait, clavier et contraste élevé.

## 66. Known limitations

`stock_movements` ne persiste ni acteur ni avant/après. `reference_id` est typé texte sans FK polymorphe; sa résolution est sûre seulement combinée au type connu. Aucun historique complet de niveau de stock n’est reconstruit.

## 67. Deferred requirements

Toute valorisation comptable, nouveau format export, nouvelle relation ou mutation métier exige une décision/schema futur. J.6 non commencé.

## 68. Version confirmation

Version **2.0.1**, aucun installer, aucune publication.

## 69. Readiness for J.6

Critères automatisables J.5 réussis; revue visuelle humaine requise. **J.5 COMPLETE — AUTOMATED VALIDATION PASSED, VISUAL REVIEW REQUIRED**. STORE reste **NOT READY — RELEASE BLOCKERS REMAIN**.
