# STORE 3.0 — I07-A : architecture et plan d’implémentation

Date : 4 octobre 2026 (Europe/Rome).
Référence inspectée : `main`, `bf2ddd419c654b4c3eda8b6e9017f6ee43035775` (I06 acceptée).
Statut : plan documentaire établi ; **aucune implémentation I07 autorisée par ce document**.

## 1. Périmètre et vérification initiale

HEAD et branche correspondent à la mission. Index vide ; aucun fichier suivi
modifié au départ. Seuls les quatre dossiers non suivis connus sont présents :
`store-android/{application-api,application,domain,testing}/bin/`. Ils sont conservés.
Ce document est l’unique ajout de cette étape. Aucun build, test, accès réseau,
changement applicatif, staging, commit, push, tag, nettoyage ou travail I08.

Autorités consultées :

- Contrat racine `docs/STORE_3_IMPLEMENTATION_CONTRACT.md`, I07, règles
  transversales et gates §9 : G-INVENTORY, G-PURCHASE, G-STOCK, G-DENY, G-LIFE,
  G-NR, G-AUDIT, G-MEDIA, G-PASS/G-EVIDENCE.
- `docs/STORE_3_FUNCTIONAL_BASELINE.md`, matrice §4, domaines L/M/N,
  FLOW-05/06, XINV-15–17.
- `docs/STORE_3_ANDROID_ARCHITECTURE.md`, frontières des couches, §§9–13/18.
- Code Android accepté décrit ci-dessous. Lecture Desktop strictement ciblée,
  sans modification : `backend/src/domain/rbac/ipcPermissions.ts`,
  `backend/src/domain/purchase/purchasePolicy.ts` et opérations
  fournisseur/achat/inventaire de `backend/src/database/storeDatabase.ts`.
  Cette lecture précise la parité ; elle ne remplace pas les invariants Android.

Les résultats ci-dessous sont des **constats de lecture**, pas des qualifications
exécutées. Tous les tests proposés restent À EXÉCUTER lors de l’implémentation.

## 2. État réel : fondations présentes et capacités absentes

Chemins de cette section relatifs à `store-android/`.

| Élément inspecté | Disponible aujourd’hui | Complément I07 nécessaire |
|---|---|---|
| `infrastructure/.../persistence/Entities.kt`, `SupplierEntity` | ID, nom unique, actif, dates, téléphone/email/adresse | Requêtes paginées, détail, validation et écriture autorisée, UI |
| Même fichier, `PurchaseEntity` | Référence unique, fournisseur nullable, DRAFT/VALIDATED/CANCELLED, total, facture fournisseur/note, acteurs/dates création/validation/annulation, motif, clé nullable unique | Service complet, transitions transactionnelles, lignes/détail/filtres et compensation |
| `PurchaseLineEntity` | Article, quantité entière, coût, total ; unicité `(purchaseId, productId)` ; FK | Enregistrement/remplacement de ligne DRAFT, recalcul total, lectures bornées |
| `InventoryEntity` / `InventoryLineEntity` | Référence/statut/acteur/dates/note ; stock attendu et compté ; unicité inventaire/article | Démarrage, enregistrement explicite du comptage, revue, validation unique, détail |
| `StoreDatabase.kt`, `StoreDao` | Inserts des cinq entités ; `findPurchase`, `findInventory` | Listes/détails avec lignes, recherche fournisseur, upsert ciblé, transitions conditionnelles, IDs, lectures nécessaires à la validation |
| `StoreConstraints` | FK, statuts, quantités/coûts non négatifs, quantités achat entières bornées ; fournisseur actif booléen | Ne prouve ni autorisation, ni confirmation physique, ni atomicité métier ; validations applicatives indispensables |
| `application/.../persistence/PersistenceContracts.kt` | `PurchaseRepository.purchase(id)` et `InventoryRepository.inventory(id)` | Ports métier minimaux ; les lectures actuelles ne sont pas des services d’achat/inventaire |
| `RoomRepositories.kt` | Projections sommaires `PurchaseRecord` / `InventoryRecord`, même owner et leases | Adaptateurs des nouveaux ports ; ne pas exposer DAO/entités à presentation |
| `CatalogPorts.kt`, `RoomCatalogRepository.kt` | `find`, `write`, `movement`, prix/historique, pagination, références médias ; contrôle de lease et lecture/écriture | Réutiliser ces écritures dans la transaction I07, sans second moteur de stock |
| `IdentityAuthority.authorizedRead/authorizedWrite` | Session courante, droits effectifs relus, sérialisation par gate, UoW pour mutation | Appliquer aux nouveaux endpoints ; ne jamais autoriser depuis un DTO de session mis en cache |
| `DatabaseRuntime.kt`, `CommandCoordinator` | Room/BundledSQLite, owner unique, WAL/FULL, FK, transactions sérialisées, backup pré-migration, refus de downgrade | Aucun nouveau propriétaire ou gestionnaire de transaction |
| `SaleAuthority`, `RoomSaleOperations` | Opérations critiques au scope application ; `latestCost` lit les mouvements positifs `purchase`/`initial` ; snapshots vente durables | Reprendre le modèle de durée de vie et les ports existants, **pas** le journal/canonicalisation RF004 pour les achats |
| `SecurityRepository.appendAudit`, `RoomSecurityRepository` | Acteur, responsable connecté, référence/théorique/devise caisse au moment de l’action | Événements I07 dans la même transaction que leur opération ; aucune invention rétroactive |
| `CatalogScreen`, `CatalogScreenState`, `CatalogService.save` | Éditeur, saisie, validation, médias I04 ; labels des mouvements `purchase`, `purchase_cancellation`, `inventory` | Retour contextuel avec article créé ; l’écran actuel n’est pas un éditeur embarquable avec callback de sélection déjà établi |
| `StoreApplication`, `SecurityApp`, `StoreNavigation`, `SpatialTheme`, `PosState` | Composition unique, navigation par capacités, FR/EN, thème/adaptation et exemple ViewModel | Ajouter uniquement destinations et états I07 ; conserver POS, session et navigation acceptés |

La recherche fonctionnelle dans `application-api`, `application`, `domain`,
`presentation` et `app` trouve les ports sommaires, les motifs de mouvements et
les projections, mais aucun endpoint Android de démarrage/validation inventaire,
réception/annulation achat ou gestion fournisseur. Les DAO ne contiennent pas
leurs transitions. L’absence est donc fondée sur les appels et responsabilités,
pas seulement sur l’absence de noms de fichiers.

Points à ne pas confondre :

- Le type historique `purchases` accepté par `CatalogPolicy.historyType` sert à
  la suppression d’historique existante ; ce n’est pas un service d’achat et il
  ne doit jamais devenir une voie implicite de compensation/suppression I07.
- `latestCost` permet aux **futures** ventes de prendre le coût reçu ; aucun
  achat ne recalcule `InvoiceLineEntity.unitCost` ou les autres snapshots existants.
- La contrainte SQLite autorise `InventoryEntity.status = CANCELLED`, mais cela
  n’autorise pas à créer un parcours d’annulation inventaire, explicitement exclu.

## 3. Architecture minimale proposée

Conserver les sept modules et l’allowlist actuelle : `app` compose ;
`presentation → application-api` ; `application → application-api/domain` ;
`infrastructure → application/domain` ; `testing → application-api/application/domain`.
Aucune nouvelle dépendance, permission Android, connexion, tâche WorkManager,
architecture ou migration n’est présumée nécessaire.

### API publique et autorisation

Trois interfaces spécialisées proposées dans `application-api/.../api/` :

| Interface/opération proposée | Droit vérifié côté application |
|---|---|
| `SupplierService.list/detail` | `PURCHASES:READ` |
| `SupplierService.save` (création/édition) | `PURCHASES:UPDATE`, conformément au mapping existant |
| `PurchaseService.list/detail` | `PURCHASES:READ` |
| `PurchaseService.createDraft` | `PURCHASES:CREATE` |
| `PurchaseService.saveLine` | `PURCHASES:UPDATE` |
| `PurchaseService.validate` | `PURCHASES:VALIDATE` |
| `PurchaseService.cancel` | `PURCHASES:DELETE` |
| `InventoryService.start` | `STOCKS:CREATE` |
| `InventoryService.recordCount` | `STOCKS:UPDATE` |
| `InventoryService.validate` | `STOCKS:VALIDATE` |
| `InventoryService.list/detail/review` | Politique de lecture à arrêter en D1 ci-dessous ; jamais `STOCKS:READ` seul pour Employee |
| Création contextuelle d’article | `CatalogService.save`, donc `PRODUCTS:UPDATE`, indépendamment des droits achat |

DTO allowlistés : résumé/détail fournisseur, achat et lignes, inventaire et lignes,
page bornée, filtres typés et erreurs stables (`INVALID_INPUT`, `NOT_FOUND`,
`CONFLICT`, `INSUFFICIENT_STOCK`, refus de sécurité existant). Aucun acteur,
statut final, total autoritaire, SQL, nom de table, chemin ou secret choisi par UI.
L’acteur et l’heure viennent de l’application ; prix/coûts sont validés selon
la nature de l’opération. Identifiants positifs, entiers bornés, montants finis,
filtres/statuts allowlistés, dates et limites de texte contrôlés.

Étendre les ports `PurchaseRepository` / `InventoryRepository` existants avec
les lectures et écritures nécessaires ; ajouter un port fournisseur. Éviter
une deuxième famille de ports concurrents pour les mêmes opérations. Les
adaptateurs Room conservent `check(false/true)`, la lease et l’owner existants.
Le code UI consomme uniquement les trois interfaces publiques et CatalogService.

### Transactions et durée de vie

Chaque mutation passe par `authorizedWrite(droit)` → `CommandCoordinator` →
transaction Room. Dans cette transaction : droit actuel, cible/état/lignes,
stock courant, écritures métier, mouvements et audit prévu. Aucun appel imbriqué
à un autre use case qui ouvrirait une nouvelle transaction ; utiliser ses ports
internes déjà acceptés. Aucune coroutine détachée **dans** l’UoW.

Les opérations acceptées vivent au scope application, comme I06. La composition
agrège les indicateurs critiques achat/inventaire avec celui des ventes pour les
guards de changement de session ; ne pas remplacer ou relâcher J.4. Les résultats
UI sont liés à l’acteur/génération ayant lancé la requête et écartés après switch.
Le ViewModel conserve le formulaire et la navigation contextuelle à rotation,
pas une session persistante. Après mort : authentification, relecture explicite
du draft/détail/statut ; jamais réception, validation ou création automatique.

## 4. Contrats d’opération et limites

### Fournisseurs et création contextuelle

Nom non vide et unique avec la sémantique de comparaison existante (ne pas
introduire silencieusement une unicité insensible aux accents/à la casse).
Email normalisé selon source ; contacts et état actif contrôlés. Pas de suppression
physique d’un fournisseur référencé. Fournisseur actif facultatif à la sélection.

Créer depuis l’achat constitue une opération indépendante : retour avec l’ID
persisté, sélection de ce résultat et restauration des champs achat. Annuler ou
échouer dans le sous-formulaire ne modifie pas le draft parent. Une création
d’article réutilise médias et validation I04 ; le chemin contextualisé doit
imposer un stock initial nul côté frontière métier pour que la quantité reçue
ne soit appliquée que par la validation achat. L’appel général de création
catalogue conserve son comportement accepté : pas de suppression globale du
stock initial. Définir un adaptateur/use case contextualisé étroit, sans dupliquer
le moteur média ni ajouter de transaction imbriquée.

### Achats

1. Créer DRAFT avec référence unique, auteur/date, fournisseur facultatif,
   facture fournisseur et note ; zéro effet stock/mouvement de réception.
2. Sauver une ligne uniquement en DRAFT : article admissible, quantité entière
   strictement positive, coût fini non négatif. L’unicité achat/article impose
   un remplacement de la ligne, comme la source, pas une addition implicite.
   `totalLine = quantity × unitCost` ; total achat dérivé des lignes avec les
   points d’arrondi source, sans réutiliser arbitrairement l’arrondi vente.
3. Valider DRAFT non vide : relire toutes les lignes et tous les stocks, vérifier
   les bornes d’addition, appliquer réception + mouvements `purchase` référencés
   par l’ID achat + statut/auteur/date + `purchase_validated`, atomiquement.
   Un appel déjà VALIDATED retourne le résultat existant après droit courant,
   sans nouvel effet ; CANCELLED ou état inconnu refuse.
4. Annuler avec motif trim >=3 caractères : DRAFT → CANCELLED sans mouvement ;
   VALIDATED → contrôle de tous stocks puis soustraction, mouvements
   `purchase_cancellation`, statut/auteur/date/motif et audit dans une UoW.
   Une insuffisance, même sur la dernière ligne, annule **toute** la transaction.
   Déjà CANCELLED : résultat existant autorisé, aucune deuxième compensation.
   Aucun paiement fournisseur, changement de caisse ou réécriture de facture vente.

### Inventaires

1. Démarrer DRAFT et lignes persistées des articles du périmètre source ; stock
   attendu initial conservé. Un `countedQuantity` prérempli égal au stock attendu
   demeure une suggestion, jamais une attestation de comptage physique.
2. Enregistrer explicitement un comptage entier >=0 sur une ligne existante en
   DRAFT ; aucun ajustement de stock. Traiter les modifications concurrentes
   sans écraser silencieusement une revue ancienne (comparaison de valeurs
   attendues/état du draft ou révision si justifiée, sans décision de schema tacite).
3. Revue : présenter séparément attendu initial, compté et stock courant. À la
   validation, relire le stock courant dans la même transaction et calculer
   `delta = compté confirmé - stock courant`, jamais contre le snapshot initial.
   Écrire stock/deltas `inventory`/statut/auteur/date/audit ensemble. Delta nul :
   pas de mouvement artificiel. Seconde validation : CONFLICT, non réapplication.
4. Après redémarrage : draft, lignes et détail consultables après autorisation.
   Cela ne promet pas une restauration complète du parcours éditeur, qui reste
   DEFER. Ni annulation inventaire nouvelle, ni reprise automatique.

### Création achat : ne pas transposer RF004

`PurchaseEntity.idempotencyKey` existe, mais ni preuve canonique/version ni
contrat équivalent aux ventes ne sont présents. Distinguer :

- validation/annulation **du même ID durable**, protégée par état transactionnel ;
- création d’un draft, dont la réponse peut être perdue avant que l’UI connaisse l’ID.

Ne pas réutiliser `SalePolicy`, `pending_commands` ou `SaleService.resolve` pour
prétendre résoudre ce second cas. Bloquer les doubles soumissions UI ; après
résultat ambigu, proposer la consultation des drafts, sans recréation automatique.
Le comportement exact de collision de clé reste à arrêter en D3.

## 5. Décisions explicites avant les lots concernés

Décisions verrouillées par la mission I07-P1 du 4 octobre 2026. Cette section
remplace les questions/recommandations encore mentionnées dans le plan initial.
Elles sont définies dans les contrats/règles P1 ; leur exécution aux frontières
opérationnelles et leur preuve Room restent dues aux lots suivants.

**D1 — Lecture inventaire :** Owner/Manager ET droit effectif `STOCKS:READ`.
Employee refusé, même si ce droit lui permet toujours de consulter le stock
général. Aucune modification de RolePolicy. La future autorité fournit rôle et
droits actuels ; aucune commande publique ne transporte une autorisation.

**D2 — Confirmation :** aucune migration. Attestation explicite de TOUTES les
lignes, identifiants/quantités identiques au brouillon durable relu dans l’UoW.
Préremplissage, absence, doublon, omission, ligne ajoutée, quantité différente
ou revue périmée refusent la validation. Une revue opaque volatile est liée à
acteur/session/génération ; elle n’atteste rien par elle-même. Le port
`InventoryReviews` prévoit invalidation après chaque édition de comptage (même
retour à l’ancienne valeur), validation ou perte de session/génération. Après
mort du processus, aucune revue n’est restaurée : nouvelle revue ET confirmation.
Le stock courant n’est pas une valeur attestée : il sera relu pour calculer le
delta, même s’il a changé depuis le comptage. Le registre reste à implémenter,
sous le gate d’identité existant, sans moteur transactionnel parallèle.

**D3 — Collision achat :** TOUTE collision de clé → `CONFLICT`, même pour une
création apparemment identique. Aucun retour par clé seule, aucune création ou
réception supplémentaire. Résultat ambigu : consultation autorisée des brouillons,
pas replay RF004 ni réutilisation du journal de vente.

**D4 — Admissibilité :** nouvelle réception = fournisseur sélectionné existant
et actif (ou aucun fournisseur), articles existants et non archivés ; validation
inventaire = articles admissibles. Annulation historique selon droit/état/stock
suffisant, sans condition sur l’activité ultérieure du fournisseur et sans
réactivation implicite d’article. Un résultat déjà validé n’est pas une nouvelle
réception ; il ne produit aucun nouvel effet.

### Migration : décision verrouillée

Aucune migration ni modification du schéma v1 en P1 et pour D2. Aucune colonne de
confirmation, aucun backfill, aucune reconstruction d’attestation historique.

### Avancement P1

Contrats publics spécialisés fournisseur/achat/inventaire, DTO et pages bornées,
politiques pures, ports complémentaires et tests JVM ciblés ajoutés. Les ports
achat/inventaire étendent les interfaces de lecture acceptées sans modifier
`PersistenceContracts.kt` ni leurs adaptateurs ; raccordement au même owner en P2.
Coûts achat : `quantité × coût`, puis somme binary64, sans arrondi POS ajouté.
Création contextuelle : commande sans champ stock initial ; futur adaptateur
CatalogService imposera zéro sans modifier le catalogue général.

Limites techniques explicites P1 : pages 1–200, corps de lignes/attestation
<=10 000 (refus explicite au-delà, jamais troncature), quantités/IDs bornés aux
entiers sûrs source ; nom 200, téléphone 120, email 320, adresse 2 000,
facture fournisseur 200, note/motif 4 000, clé ASCII 1–128 caractères.
Une future prise en charge de plus de 10 000 lignes nécessitera un protocole
de revue borné approprié, sans fractionner le commit de stock.

Qualification P1 et liste exacte : `I07_P1_EVIDENCE.md`. Pas d’autorité métier,
DAO, réception, compensation, registre de revue, écran ou navigation implémenté.
G-PURCHASE/G-INVENTORY/G-LIFE complets ne sont pas acquis en P1.

## 6. Lots ordonnés (six phases d’implémentation proposées)

Les noms des nouveaux fichiers sont des propositions, pas des fichiers créés.
Tous les chemins ci-dessous sont sous `store-android/`.

| Lot / dépendances | Fichiers et travail prévus | Critère de sortie |
|---|---|---|
| **P1 — Contrats et règles** ; décisions D1–D4 pour les interfaces concernées | `application-api/.../api/{Supplier,Purchase,Inventory}Service.kt`, `domain/.../{Purchase,Inventory}Policy.kt`, ports spécialisés dans `application/.../{purchases,inventory}/`, extension ciblée de `PersistenceContracts.kt` ; tests JVM dans `testing` | DTO/erreurs/droits/transitions explicites, bornes et parité documentées ; aucune API générique ou acteur fourni par UI |
| **P2 — Persistance et fournisseurs** ; P1 | `StoreDatabase.kt`, `RoomRepositories.kt`, nouveaux `RoomPurchaseRepositories.kt` / `RoomInventoryRepositories.kt`, `SupplierAuthority.kt` ; migration/Entities/DatabaseRuntime seulement si D2 l’exige | Requêtes/leases/contraintes, recherche et écritures fournisseur autorisées ; fixtures persistantes, refus sans effets |
| **P3 — Achats complets** ; P1/P2 | `PurchaseAuthority.kt`, ports/DAO achat ; tests `PurchaseAuthorityTest`, `PurchaseNativeTest` | Draft/lignes/total/détail/filtres, validation une fois et annulation atomique ; audits/refus et snapshots vente inchangés |
| **P4 — Inventaires complets** ; P1/P2, D2 arrêté | `InventoryAuthority.kt`, ports/DAO inventaire ; `InventoryPolicyTest`, `InventoryNativeTest` | Comptages distincts du préremplissage, validation contre stock courant une fois, preuve rollback/concurrence |
| **P5 — UI et intégration** ; P3/P4 | `presentation/.../{PurchaseScreen,InventoryScreen,SupplierScreen}.kt` et ViewModels ; adaptation minimale `CatalogScreen.kt` pour retour contextuel ; `SecurityApp.kt`, `StoreNavigation.kt`, éventuellement `StoreHome.kt` ; composition `StoreApplication.kt`/`MainActivity.kt` | Parcours réversibles, FR/EN/thèmes, chargement/erreurs/retry, scopes critiques ; formulaire parent conservé, aucune réception implicite, Employee refusé |
| **P6 — Qualification et preuves** ; P1–P5 | tests natifs interruption, tests Compose sous `app/src/androidTest`, régressions impactées, `docs/evidence/I07/` | Tous gates requis exécutés, 0 FAIL/0 requis SKIPPED, captures trois largeurs, DB/PID, build/lint/architecture/diff/scope ; revue finale, pas d’acceptation automatique |

Pas de modifications attendues du contrat racine, Desktop, Gradle/dépendances,
sécurité/RBAC, sources de vente ou médias hors adaptation d’intégration strictement
nécessaire et couverte. Les tests existants restent intacts sauf évolution
justifiée d’un port, jamais pour affaiblir une assertion.

## 7. Matrice de qualification à exécuter ultérieurement

| ID / gate | Scénarios minimum | Preuve attendue |
|---|---|---|
| I07-DRAFT / G-PURCHASE,G-INVENTORY | Création, lignes, édition, restart ; chaque brouillon et comptage sans effet stock | Avant/après : stock et mouvements identiques, header/lignes durables, FK/intégrité |
| I07-COUNT / G-INVENTORY | Prérempli non confirmé refusé ; ligne manquante/invalide ; comptage zéro ; stock vendu/reçu/ajusté entre démarrage et validation ; deuxième validation refusée | Attendu initial conservé, delta calculé contre courant, un statut final et mouvements exacts ; aucun second effet |
| I07-RECEIVE / G-PURCHASE | Vide refusé ; lignes remplacées et non doublées ; validation répétée/concurrente ; statut annulé refusé | Une réception par achat, sommes exactes, pas deux séries de mouvements/audits |
| I07-COMPENSATE / G-PURCHASE,G-STOCK | Annulation DRAFT ; VALIDATED avec stocks suffisants ; insuffisance sur dernière ligne ; répétition/concurrence ; motif trop court | Aucune compensation DRAFT ; compensation unique VALIDATED ; refus conserve tous stocks/lignes/statuts/audits |
| I07-BOUND / G-STOCK,G-DENY | Quantités négatives/fractionnaires/hors bornes, dépassement à l’addition, coûts NaN/infini/négatifs, ID/type/statut/filtres malformés | Erreurs sûres et zéro mutation métier ; aucun SQL/table piloté par UI |
| I07-RIGHTS / G-DENY | Owner/Manager autorisés, Employee par appels directs pour toutes API I07, refus individuel, session absente/inactive, droit/rôle retiré avant mutation, acteur forgé | Droit relu dans l’UoW ; corpus de refus compare DB avant/après ; aucune permission issue de la navigation |
| I07-ATOMIC / G-INVENTORY,G-PURCHASE | Injection après chaque ligne/stock/mouvement/statut/audit ; concurrence vente/réception/comptage/compensation | Rollback complet ou commit complet ; pas de deadlock UoW imbriquée, pas de stock négatif |
| I07-DEATH / G-LIFE | Préparation synthétique + kill externe avant commit puis après commit avant réponse, pour réception/inventaire/compensation ; restart lecture seule puis action explicite | Marqueur durable, PID avant/disparu/après, nouveau login, comptes DB/stock/statuts/mouvements, integrity/FK ; pas auto-recréation/validation ni pointage/clôture caisse |
| I07-CONTEXT / G-NR,G-MEDIA | Fournisseur/article créés ou annulés depuis achat, refus de droit contextuel, échec média ; rotation/clavier/retry | Champs parent conservés, résultat sélectionné, article créé à stock nul dans ce parcours, aucun achat validé ; invariants RF001 préservés |
| I07-UI / G-EVIDENCE | COMPACT <600, MEDIUM 600–839, EXPANDED >=840 dp réels ; seuils/redimensionnement, portrait/paysage, IME, virgule/point, libellés FR/EN, light/dark | Assertions disposition et accès aux attributs, focus continu, captures propres ; zones scroll indépendantes, cibles >=48dp |
| I07-NR / G-NR | Vente avant achat puis réception/annulation/inventaire ; prochaine vente avec coût reçu ; replay/annulation I06 ; catalogue/auth/équipe selon fichiers affectés | Factures/lignes/paiements/preuves I06 existants inchangés ; coût futur seulement ; guards J.4, attribution audit et session préservés |

Réutiliser les fixtures et patrons `SaleNativeTest`, `SaleRestartTest`,
`CatalogNativeTest`/restart médias et tests de sécurité/présentation existants.
Réutiliser le moteur réel et les probes, pas leurs résultats comme preuve I07.
Mort de processus ≠ simple `Activity.recreate()` ; tests prepare/verify pilotés
explicitement. Aucun test de phase multi-étapes ne devient un faux PASS ignoré.

Tests ciblés pendant chaque lot. En P6, compilation/APK/lint des modules touchés,
`verifyArchitecture`, régressions I02–I06 réellement impactées, `git diff --check`
et revue du périmètre ; pas de campagne Desktop ni réseau. Les commandes exactes,
versions, fichiers qualifiés et résultats réels seront consignés dans la preuve
I07, avec PASS/FAIL/SKIP distincts. Ce plan n’en annonce aucun comme exécuté.

## 8. Acceptation, exclusions et arrêt

I07 complet seulement après résolution des décisions ci-dessus, six lots achevés,
preuves liées au code qualifié et respect de G-PASS. La proposition de staging
sera sélective, hors `bin/`, accompagnée de la liste exacte des fichiers modifiés.
Le commit prévu par contrat reste `feat(android): I07 implement purchases and inventories`,
uniquement après autorisation distincte ; pas de commit dans I07-A.

Hors périmètre : reprise éditeur complète après mort, nouvelle annulation
inventaire, dettes/banque fournisseur, PDF achat autonome, extension RF004 aux
achats, changement des snapshots ventes, nouvelles permissions ou délégation,
I08. Les raffinements cosmétiques non bloquants restent I13.

STOP d’implémentation si : draft modifie stock ; double réception/compensation ;
confirmation physique supposée ; refus après mutation partielle ; Employee
accède par appel direct ; perte de contexte entraînant une autre opération ;
régression I02–I06 ; migration/architecture non justifiée ; preuve requise non
fiable. Ne jamais contourner ces cas par reset, purge, test affaibli ou succès simulé.

**Première phase proposée : P1**, en commençant par la validation explicite des
décisions D1–D4 puis les contrats publics et règles pures. Aucun code à écrire
avant une mission d’implémentation I07 autorisée.
