# STORE 3.0 — I06 : rapport de qualification et proposition d’acceptation

**État : qualification documentée ; acceptation formelle EN ATTENTE de validation explicite.**  
**Périmètre :** I06 — caisse, vente POS atomique, replay RF004, annulation, reçu/historique local, journal des commandes soumises, autorisations, interface adaptative et cycle de vie Android.  
**Branche :** `main`  
**Référence de départ (I05 acceptée) :** `9e0065f3313d5ad4297a1f4f4e45f785dc360c27`  
**Commit final I06 :** non créé ; les modifications restent locales et non indexées.  
**Date des vérifications :** 3–4 octobre 2026.  
**Sources contractuelles :** `../../../../docs/STORE_3_IMPLEMENTATION_CONTRACT.md`, `../../../../docs/STORE_3_FUNCTIONAL_BASELINE.md`, `../../../../docs/STORE_3_ANDROID_ARCHITECTURE.md` (chemins relatifs à ce rapport vers le dossier `docs` de la racine Git `STORE`). Le plan historique `docs/evidence/I05/I06_IMPLEMENTATION_PLAN.md` ne tient pas lieu de constat d'implémentation.

## 1. Identification de la version et de l’environnement

| Élément | Valeur vérifiée ou déclarée |
|---|---|
| Application | `versionCode=1`, `versionName=3.0.0` ; configuration `debug` : `applicationIdSuffix=.debug`, `versionNameSuffix=-I01-debug` |
| JDK | 21, conformément au contrôle Gradle |
| Environnement Android | Émulateur synthétique `STORE_I03_API36`, API 36, série `emulator-5554` |
| Projet qualifié | `%TEMP%\store-i06-qual-r6-20261003-224544` |
| APK qualifié | `app/build/outputs/apk/debug/app-debug.apk` dans la copie de qualification |
| Taille de l’APK | `16 843 969` octets |
| SHA-256 APK | `91DB20878C123FD6FA56358A3224DDA02E7CF49F7300ECAB0C4374DF470C05F4` |
| Parité dépôt / copie qualifiée, R35 | **86 fichiers vérifiés ; 0 différence** |
| Contrôle des espaces Git, R37 | `git diff --check` : **PASS** |
| Index Git, R37 | **0 fichier indexé** |

**Portée de la parité :** sources et configurations Gradle inventoriées par R35. Les tests supplémentaires R32/R34 ainsi que la correction historique R29 ont été qualifiés dans la copie temporaire, puis reportés dans le dépôt avec vérification des empreintes. Les avertissements Git CRLF/LF constatés sur `StoreApplication.kt` et `StoreDatabase.kt` n'ont pas fait échouer `git diff --check`.

## 2. Résultats de qualification et références des preuves

Les fichiers cités ci-dessous sont conservés dans `docs/evidence/I06/logs/`, sauf indication contraire. L’index `I06_EVIDENCE_INDEX.txt` contient leurs SHA-256. Chaque résultat se rapporte à la campagne indiquée : les rapports XML Android les plus récents ont remplacé ceux des campagnes précédentes et ne constituent **pas** un rapport cumulatif.

| Référence | Vérification | Résultat |
|---|---|---|
| R6 | Construction dans une nouvelle copie de qualification, après abandon de l'ancienne copie affectée par un verrou Windows sur `classes.jar` | **PASS** ; `store-i06-r6-build-20261003-224544.log` |
| R7 | Architecture à sept modules et dépendances autorisées ; JVM `:testing:test` ; compilation des tests Android d'infrastructure et d'application ; APK `:app:assembleDebug` | **PASS** ; 24 cas JVM, 0 échec et 0 ignoré ; `store-i06-r7-qualification.log` |
| R8 | `SaleNativeTest` : sept scénarios ordinaires de vente | **7/7 PASS** ; `store-i06-r8-infrastructure.log`. Le scénario de mort réelle exigeait des phases externes et a été qualifié séparément en R14 |
| R10, R12b | POS : deux tests ordinaires, puis test adaptatif avec largeur explicite `posWidth` | **PASS après invocation correcte** ; `store-i06-r10-pos.log`, `store-i06-r12b-{COMPACT,MEDIUM,EXPANDED}.log` |
| R14 | Reprise après mort réelle du processus, scénarios `BEFORE_COMMIT` et `AFTER_COMMIT`, avec préparation, interruption externe et vérification séparée | **2/2 phases PASS**, PID témoins `7059` (avant) et `7148` (après) ; journaux `store-i06-r14-*-prepare.log` et `store-i06-r14-*-verify.log` |
| R19 | `:infrastructure:lintDebug` et `:app:lintDebug` | **PASS** ; `store-i06-r19-lint.log` |
| R20 | Non-régression native I04–I05 (`CatalogNativeTest`, `TeamNativeTest`) | **11/11 PASS**, 0 échec et 0 ignoré ; `store-i06-r20-native.log` |
| R20 | Non-régression UI I04–I05, six classes sélectionnées | **28/28 PASS**, 0 échec et 0 ignoré ; `store-i06-r20-ui.log` |
| R25 | Non-régression UI ordinaire I02–I03 | **5/5 PASS**, 0 échec et 0 ignoré ; `store-i06-r25-ui.log` |
| R28–R29 | Non-régression native ordinaire I02–I03 après correction de la connexion SQLite adversaire | **15/15 PASS**, 0 échec et 0 ignoré ; `store-i06-r28-native.log` ; correction synchronisée avec le dépôt en R29 |
| R32 | Calcul de caisse, motifs invalides, audit et maintien du snapshot après annulation | **1/1 PASS** ; `store-i06-r32-targeted.log` |
| R34 | Refus du replay et de la résolution après révocation de `POS:VALIDATE` ou désactivation de l'acteur | **1/1 PASS** ; `store-i06-r34-replay.log` |
| R35–R37 | Inventaire, sauvegarde des preuves, parité source, empreintes et contrôle Git | **PASS** ; 86/86 fichiers identiques, 21 journaux archivés, trois captures vérifiées |

**Interprétation des campagnes partielles :** R8 avait exécuté le test de reprise sans son protocole externe, et R10 le test adaptatif sans son argument de largeur obligatoire. Ces invocations initiales ne sont pas des qualifications positives ; les scénarios concernés ont ensuite été exécutés selon leur protocole (R12b et R14). R22/R25 et leur correction sont détaillés en § 6. Aucune réussite historique n'est utilisée pour masquer un échec final non résolu.

## 3. Évaluation des critères contractuels

| Critère | Conclusion sur les preuves réunies | Éléments de preuve |
|---|---|---|
| **G-SALE** | **Qualifié** pour les scénarios testés | Validation des entrées/stock/caisse/droits ; facture, lignes, paiement, stock et mouvements dans une unité de travail ; injections d'échec à chaque frontière ; R7, R8, R32 |
| **G-RF004** | **Qualifié** pour les scénarios testés | Canonique versionné ; multiensemble de lignes avec doublons conservés et tri stable ; montant reçu omis distinct du montant explicite ; refus des changements d'intention, de version et de preuve historique ; replay concurrent et après épuisement du stock ; journal de commande et résolution explicite ; R7, R8, R14, R34 |
| **G-CANCEL** | **Qualifié** pour les scénarios testés | Droit `POS:DELETE`, motif de 3 à 1 000 caractères après `trim`, compensation stock/paiement, événement d'audit, retour idempotent à la deuxième demande, rollback injecté et snapshot de caisse clôturée préservé ; R8, R32 |
| **G-NR** | **Qualifié sur les campagnes requises exécutées** | I02–I03 : 15/15 natifs (R28) et 5/5 UI (R25) ; I04–I05 : 11/11 natifs et 28/28 UI (R20) ; architecture et 24 cas JVM (R7). Les protocoles à phases externes ne sont pas assimilés aux tests ordinaires |
| **G-AUDIT** | **Qualifié pour les événements et traces prévus** | Audit `cash_session_opened`, `cash_session_closed`, `invoice_cancelled` ; contrôle antérieur I03 `auditUsesTrustedResponsibleCashSnapshot` inclus dans R28 ; facture, lignes et mouvements portant la référence de vente. Aucun événement générique de vente supplémentaire n'a été inventé |
| **G-LIFE** | **Qualifié pour les scénarios exécutés** | Tests POS compacts/moyens/étendus, captures ; interruption réelle avant/après commit avec PID distincts, reprise et contrôle des effets durables ; R12b, R14 |
| **G-PASS / dossier de preuves** | **Prêt pour revue finale, non approuvé formellement** | Tests requis exécutés en campagnes positives sans échec ni ignoré, `lintDebug` et build positifs, 86/86 fichiers identiques et preuves archivées. La décision d'acceptation et le commit demeurent en attente |

La qualification porte sur la version et les scénarios spécifiés, **pas** sur une certification générale de sécurité ou une validation de production. Les données et l'émulateur utilisés par les tests sont synthétiques.

## 4. Valeurs observables : persistance, stock et caisse

| Situation contrôlée | Avant | Après ou invariant |
|---|---|---|
| Vente ordinaire de deux articles à `10.00` | Stock produit `20`, caisse ouverte `5.00` | Stock `18` ; reçu de `20.00` ; caisse attendue `25.00` |
| Clôture avec espèces comptées | Attendu `25.00` | Compté `23.00` ; différence `-2.00` ; statut `CLOSED` |
| Refus de motif d'annulation vide, blanc ou de deux caractères | Facture, stock et mouvements après vente | Snapshot identique ; aucune annulation durable |
| Annulation valide après clôture | Stock `18`, paiement `CAPTURED` | Stock `20`, paiement `REFUNDED`, facture annulée ; caisse clôturée toujours attendue `25.00` |
| Deuxième annulation | Compensation déjà effectuée | Même résultat retourné ; aucune nouvelle compensation ni mutation du snapshot |
| Vente concurrente consommant tout le stock | Stock `20` | Stock `0`, un reçu durable et un seul effet ; replay identique, y compris après checkpoint/réouverture |
| Injection de panne `INVOICE`, `LINE`, `STOCK`, `MOVEMENT`, `PAYMENT` ou `BEFORE_COMMIT` | Snapshot avant la tentative | Snapshot identique après rollback et après checkpoint/réouverture |
| Replay après révocation ou désactivation synthétique | Une facture validée ; stock `18` | `sell` et `resolve` refusés, même facture et même stock, sans deuxième effet durable |

Les valeurs proviennent des assertions des tests natifs R8/R32/R34 et des résultats des campagnes correspondantes. Les tests ne constituent pas, à eux seuls, une extraction exhaustive des tables SQLite : les journaux et les sources de test constituent la preuve détaillée des assertions avant/après. Les comptes réels, bases de production et sauvegardes historiques n'ont pas été sollicités.

## 5. Matrice des refus et de la reprise

| Condition | Comportement attendu et vérifié dans les scénarios disponibles | Référence |
|---|---|---|
| Stock insuffisant, quantités invalides et dépassement de la quantité cumulée | Refus sans facture ni mouvement partiel | R7, R8 |
| Acteur non autorisé, droit `POS:VALIDATE` révoqué, session/compte invalidé | Vente, replay et résolution refusés ; aucune mutation métier supplémentaire | R8, R34 |
| Caisse absente, autre caisse ou caisse fermée | Vente/replay refusés ; pas de réattribution implicite de la facture | R8 |
| Clé identique, intention modifiée (lignes, doublons, quantités, remise, montant reçu) | Conflit/refus sans deuxième facture | R8 |
| Requête historique sans canonique, version absente/inconnue, preuve divergente | Refus sans réécriture de la preuve historique | R8 |
| Reçu d'origine annulé | Replay refusé ; absence de nouvelle vente | R8 |
| Motif d'annulation invalide et acteur sans `POS:DELETE` | Refus sans compensation ni réécriture | R8, R32 |
| Panne synthétique dans une unité de travail | Rollback complet ; journal de commande soumis conservé ou traité explicitement selon l'étape | R8 |
| Mort réelle avant ou après commit | Vérification indépendante après redémarrage, sans duplication des effets durables | R14 |
| Modification du profil/droits avec caisse ouverte | Refus par les gardes administratifs ordinaires ; R34 simule volontairement une modification adverse dans une base isolée pour éprouver la défense du replay | R8, R34 et tests I03–I05 |

## 6. Incident historique de contention SQLite : diagnostic et remédiation

- **R22 et R25 :** `PersistenceTest.boundedContentionNoRetry` échouait dans la campagne groupée sur `database is locked` à l'instruction de préparation adversaire `BEGIN IMMEDIATE`, avant l'appel métier censé rencontrer la contention ; les autres 14 tests natifs étaient positifs.
- **R24 :** ce test réussissait lorsqu'il était exécuté isolément.
- **R26–R27 :** l'inspection a confirmé que la fixture utilise sa propre base temporaire, que Room applique `PRAGMA busy_timeout=750` et que la connexion SQLite adverse du test ne configurait pas ce délai avant `BEGIN IMMEDIATE`.
- **R28 :** expérience contrôlée dans la copie de qualification, avec **un seul changement au test** : `adversary.execSQL("PRAGMA busy_timeout=750")` avant `BEGIN IMMEDIATE`. La campagne native groupée a réussi **15/15**, zéro échec et zéro test ignoré.
- **R29 :** correction test-only appliquée au dépôt. La parité SHA-256 dépôt/copie qualifiée du test était **PASS** (`CCD61AA2BC6000357F144AE551F8FA3C139A8EAE1120C54ADA96D8AC79D1C5B5`). Aucun code de production, assertion, délai métier maximal (`< 5 s`) ou mécanisme de retry n'a été modifié.

Cette séquence établit une remédiation efficace dans la campagne groupée. Elle constitue une **forte indication** que l'initialisation de la connexion adverse expliquait la fragilité observée ; elle ne prétend pas prouver l'absence de toute autre source théorique de contention. Les journaux R22/R25 sont conservés explicitement comme historique, et R28 comme campagne positive après correction.

## 7. Interface, captures et reprise de processus

| Pièce | État et SHA-256 |
|---|---|
| `i06-COMPACT.png` | Présente ; `BB4E2F03757AB13498D50A8F643AC94A7DFF790147DDEB50EBCFA16F4A20FBED` |
| `i06-MEDIUM.png` | Présente ; `471EC2CE7E123BAB87C8525B5A90BEBCC93C2E4ACBCF73BF104E65E8BA641084` |
| `i06-EXPANDED.png` | Présente ; `DE9FA9BED8A8DE9B186BA389D376696B50D333A22DA9EE5CF1A5142AAD4D1322` |

Le test adaptatif a été exécuté avec largeur explicite à **599 dp (COMPACT), 839 dp (MEDIUM) et 840 dp (EXPANDED)** ; taille et densité de l'émulateur restaurées ensuite. Les deux phases R14 de mort réelle du processus ont été vérifiées séparément (`BEFORE_COMMIT` et `AFTER_COMMIT`), avec témoins PID `7059` et `7148`. Consulter les journaux `*-prepare.log` et `*-verify.log` archivés pour le protocole, les identifiants de phase et les résultats exacts.

## 8. Inventaire Git et exclusion explicite des artefacts générés

**Fichiers suivis modifiés :**

- `app/src/main/kotlin/com/vibe/store/MainActivity.kt`
- `app/src/main/kotlin/com/vibe/store/StoreApplication.kt`
- `application/src/main/kotlin/com/vibe/store/application/persistence/PersistenceContracts.kt`
- `infrastructure/src/androidTest/kotlin/com/vibe/store/infrastructure/persistence/PersistenceTest.kt` (correction historique R29)
- `infrastructure/src/main/kotlin/com/vibe/store/infrastructure/persistence/RoomRepositories.kt`
- `infrastructure/src/main/kotlin/com/vibe/store/infrastructure/persistence/StoreDatabase.kt`
- `presentation/src/main/kotlin/com/vibe/store/presentation/SecurityApp.kt`

**Sources et tests I06 nouveaux, non suivis avant indexation :**

- `app/src/androidTest/kotlin/com/vibe/store/PosUiTest.kt`
- `application-api/src/main/kotlin/com/vibe/store/api/SaleService.kt`
- `application/src/main/kotlin/com/vibe/store/application/sales/`
- `domain/src/main/kotlin/com/vibe/store/domain/SalePolicy.kt`
- `infrastructure/src/androidTest/kotlin/com/vibe/store/infrastructure/persistence/SaleNativeTest.kt`
- `infrastructure/src/androidTest/kotlin/com/vibe/store/infrastructure/persistence/SaleRestartTest.kt`
- `infrastructure/src/main/kotlin/com/vibe/store/infrastructure/persistence/RoomSaleOperations.kt`
- `presentation/src/main/kotlin/com/vibe/store/presentation/PosScreen.kt`
- `testing/src/test/kotlin/com/vibe/store/testing/SalePolicyTest.kt`
- `docs/evidence/I06/` (captures, index, journaux et présent rapport après placement dans le dépôt)

**Exclusions à préserver :** `application-api/bin/`, `application/bin/`, `domain/bin/`, `testing/bin/` sont des dossiers non suivis déjà présents et **ne doivent pas être indexés ni supprimés automatiquement**. Aucun `git clean`, `git reset`, `git add .`, commit, push ou tag n'a été autorisé. Les données utilisateur, bases réelles, sauvegardes historiques et ressources cloud/staging sont hors périmètre.

## 9. Traçabilité de l'exécution et intégrité des preuves

Commandes Gradle représentatives des campagnes enregistrées (à exécuter, le cas échéant, depuis la copie de qualification, avec JDK 21 et SDK Android configurés) :

```text
R7 : :verifyArchitecture :testing:test :infrastructure:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug
R19 : :infrastructure:lintDebug :app:lintDebug
R20 : :infrastructure:connectedDebugAndroidTest et :app:connectedDebugAndroidTest avec filtres de classes I04–I05
R25 : :app:connectedDebugAndroidTest avec filtres de tests I02–I03
R28 : :infrastructure:connectedDebugAndroidTest avec filtres de tests I02–I03
R32/R34 : :infrastructure:connectedDebugAndroidTest avec filtre sur la méthode ciblée
Options employées pour les campagnes de qualification : --offline --no-daemon --max-workers=1 --console=plain
```

Les codes de sortie des campagnes qualifiées citées ont été **0** ; les exécutions historiques R22/R25 natives et les invocations initiales R8/R10 partiellement paramétrées ne constituent pas des campagnes positives. Les journaux contiennent le détail des étapes exécutées et des résultats Gradle. Les cas de mort réelle R14 utilisent en complément des phases externes Android, dont les journaux séparés de préparation et de vérification font foi ; les lignes ci-dessus ne sont pas présentées comme leur protocole complet.

**Archive des preuves :** `docs/evidence/I06/logs/`, **21/21 journaux** archivés en R36 ; empreintes SHA-256 de chaque fichier dans `docs/evidence/I06/I06_EVIDENCE_INDEX.txt`. SHA-256 de cet index contrôlé en R37 : `5D2A3622A74753E8D760FB3FA886258BFDCF59B8E059515DE01FC113272C55A7`. Les trois captures et leurs empreintes figurent aussi dans l'index. Aucun journal d'erreur vide n'a été ajouté à l'archive sélectionnée.

## 10. Décision et conditions de clôture

**Conclusion technique :** les contrôles I06 inventoriés ont été qualifiés sur l'émulateur synthétique, avec correction documentée du test historique SQLite, parité source **86/86**, contrôles de compilation/lint positifs, tests ciblés et de non-régression positifs, et preuves R36 archivées. Le suffixe de version debug reste celui de la configuration existante (`-I01-debug`) ; ce rapport n'en modifie pas la valeur.

**Décision administrative : EN ATTENTE.** La réussite technique n'autorise pas à elle seule le commit de clôture :

1. Relire et valider explicitement le présent rapport et son index ; vérifier que le dossier de preuves répond aux exigences du contrat I06.
2. Vérifier avant indexation que seuls les chemins I06 explicitement approuvés sont inclus, sans aucun dossier `bin/` ni fichier utilisateur.
3. Après autorisation distincte, créer **un seul commit local** d'acceptation, avec le message prévu `feat(android): I06 implement atomic sales and cash` ; consigner son SHA dans le dossier après création.
4. **Ne pas** pousser, taguer, nettoyer les dossiers non suivis ni commencer I07 sans autorisation distincte.

**Statut courant :** rapport proposé pour validation ; **I06 non encore formellement accepté**.
