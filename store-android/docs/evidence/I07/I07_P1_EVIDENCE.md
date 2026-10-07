# I07-P1 — Contrats, politiques pures et ports

Date : 2026-10-04. Statut : **PASS P1 uniquement**.
Baseline et HEAD vérifiés : `bf2ddd419c654b4c3eda8b6e9017f6ee43035775`, branche `main`.
Index vide ; aucun staging, commit, push ou tag.

## Changements exacts

Chemins relatifs à `store-android/` ; tous nouveaux sauf le plan I07-A préexistant :

- `application-api/src/main/kotlin/com/vibe/store/api/WorkflowContracts.kt`
- `application-api/src/main/kotlin/com/vibe/store/api/SupplierService.kt`
- `application-api/src/main/kotlin/com/vibe/store/api/PurchaseService.kt`
- `application-api/src/main/kotlin/com/vibe/store/api/InventoryService.kt`
- `domain/src/main/kotlin/com/vibe/store/domain/WorkflowRules.kt`
- `domain/src/main/kotlin/com/vibe/store/domain/PurchasePolicy.kt`
- `domain/src/main/kotlin/com/vibe/store/domain/InventoryPolicy.kt`
- `application/src/main/kotlin/com/vibe/store/application/purchases/PurchasePorts.kt`
- `application/src/main/kotlin/com/vibe/store/application/inventory/InventoryPorts.kt`
- `testing/src/test/kotlin/com/vibe/store/testing/PurchasePolicyTest.kt`
- `testing/src/test/kotlin/com/vibe/store/testing/InventoryPolicyTest.kt`
- `docs/evidence/I07/I07_IMPLEMENTATION_PLAN.md` : arbitrages §5 et avancement P1 seulement.
- `docs/evidence/I07/I07_P1_EVIDENCE.md` : ce rapport, sans écrasement de preuve antérieure.

## Décisions appliquées et limites de preuve

- D1 : règle pure Owner/Manager ET `STOCKS:READ`, Employee refusé ; son droit de consultation du stock général reste inchangé. Les services décrivent les droits, sans implémenter l'autorité opérationnelle.
- D2 : attestation complète comparée aux lignes durables et à une revue volatile fiable. Absence, doublon, omission, différence et revue périmée refusent. Port de revue lié à l'acteur/session/génération ; invalidation après édition (y compris ABA), validation ou perte de session. Aucun schéma modifié. Tests purs d'absence de revue après perte de contexte ; **pas de preuve native de mort de processus**, registre non implémenté.
- D3 : collision de clé → `CONFLICT`, jamais replay RF004. Test pur du refus ; absence de mutation Room à prouver plus tard.
- D4 : réception nouvelle exige fournisseur sélectionné actif et articles admissibles ; inventaire refuse article absent/archivé. Compensation historique ne dépend pas du fournisseur actif ; stock suffisant requis. Aucun mécanisme de réactivation ajouté.
- Coûts : multiplication et somme binary64, sans arrondi POS. Source consultée en lecture seule : `backend/src/domain/purchase/purchasePolicy.ts` et `updatePurchaseTotal` dans `backend/src/database/storeDatabase.ts` (`SUM(total_line)`).
- Pages 1–200, corps complet maximum 10 000 lignes, quantités/IDs dans les entiers sûrs source, champs et dates bornés/validés. Dépassement refusé, pas de validation partielle.
- Création contextuelle d'article : commande sans stock initial ; contrat de réutilisation future de CatalogService avec zéro, comportement général inchangé.

## Exécution réellement observée

Copie de qualification neuve, isolée, sans données métier :
`C:/Users/sterl/AppData/Local/Temp/store-i07-p1-qual-20261004-170202`.
JDK existant : `C:/Program Files/Java/jdk-21`.
SDK existant : `C:/Users/sterl/AppData/Local/store-i03-sdk`.
Cache existant : `C:/Users/sterl/AppData/Local/Temp/store-i03-qual/.gradle-home`.
Aucun téléchargement ; Gradle exécuté avec `--offline`.

Première commande dans cette copie :

```text
gradlew.bat -p <copie> :application-api:compileKotlin :application:compileKotlin :testing:test --tests '*PurchasePolicyTest' --tests '*InventoryPolicyTest' verifyArchitecture --offline --console=plain
```

Compilation PASS ; tests 19/20. L'unique échec comparait le littéral `0.999`
au résultat binary64 `3 * 0.333 = 0.9990000000000001` avec tolérance nulle.
L'attendu du nouveau test a été corrigé en `3 * 0.333`, toujours avec tolérance
nulle : maintien exact du calcul source, aucun arrondi ajouté à la politique.

Après synchronisation de ce seul test, commande ciblée finale :

```text
gradlew.bat -p <copie> :testing:test --tests '*PurchasePolicyTest' --tests '*InventoryPolicyTest' verifyArchitecture --offline --console=plain
```

Résultat : `BUILD SUCCESSFUL in 30s`, code 0.
`ARCHITECTURE_PASS: seven modules, explicit dependency allowlist`.

| Contrôle | Résultat observé |
|---|---|
| PurchasePolicyTest | 10 tests, 0 failure, 0 error, 0 skipped |
| InventoryPolicyTest | 10 tests, 0 failure, 0 error, 0 skipped |
| Compilation domain/application-api/application et tests JVM | PASS |
| Graphe des sept modules | PASS |
| Imports des nouveaux fichiers | Domain Java/Kotlin seulement ; API sans application/Room/Android ; ports vers API/domain/persistance acceptée |
| `git diff --check` et vérification `--no-index --check` des nouveaux fichiers | PASS |

XML finaux consultés : `testing/build/test-results/test/TEST-com.vibe.store.testing.PurchasePolicyTest.xml`
(2026-10-04T15:13:16.813Z) et `TEST-com.vibe.store.testing.InventoryPolicyTest.xml`
(2026-10-04T15:13:16.986Z), dans la copie ci-dessus.
Les tâches Gradle de configuration Kotlin marquées SKIPPED ne sont pas des tests ignorés.
Avertissements Gradle de dépréciation/option Android expérimentale existants ; aucune configuration changée.

## Périmètre et suite

Aucun fichier suivi du baseline modifié ; ajouts P1 et plan déjà non suivi exclusivement
sous `store-android/`. Quatre dossiers `bin/` préexistants conservés sans intervention.
Aucun changement Entities/Room/DAO/migration/stockage/Gradle/I06/Desktop/contrats racine.
Pas de données personnelles, autorité opérationnelle, réception, compensation, écran ou navigation.
Ports uniquement : même UnitOfWork/lease/owner à raccorder en P2, pas de moteur parallèle.

G-PURCHASE, G-INVENTORY, G-STOCK, G-DENY, G-LIFE et G-NR complets **non acquis** ici :
atomicité Room, refus sans mutation, droits actuels et vraie mort de processus restent
à qualifier aux phases prévues, notamment P6. Les tests purs ne les remplacent pas.

Aucun blocage P1 constaté. P2 prêt sur le plan contractuel, non démarré ; poursuite
soumise à l'autorisation de la phase suivante.
