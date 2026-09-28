# STORE — VIBE : préparation de la RC privée

## RF-003 remediation / reprise du gel — 2026-09-28

**RF-003 CLOSED. État de livraison : RELEASE BLOCKED — NEW BLOCKER RF-004.** Les corrections RF-001/RF-002 sont conservées. Aucun commit de livraison, aucun nouvel installateur, aucune donnée personnelle modifiée.

### RF-003 : correction et périmètre

`domain/rbac/historyOperation.ts` valide les types à l’exécution. Les lectures acceptent exclusivement `sales`, `purchases`, `personnel`. La suppression accepte exclusivement `purchases` ; `personnel` reste FORBIDDEN pour tous, propriétaire compris, conformément au comportement antérieur explicite. `sales` est refusé sur ce canal : les ventes utilisent l’annulation dédiée. Types inconnus, manquants, null, vides, valeurs non textuelles et aliases sont rejetés. Payload non objet/tableau et identifiants non entiers positifs sont rejetés avant mutation.

Dans le handler IPC, validation du discriminateur avant application de la politique fixe FINANCES:DELETE, puis attribution de userId depuis la session. L’API revalide indépendamment. La requête DELETE cible une table constante `stock_movements`, sans branche vers attendances ni interpolation de nom de table. Le sélecteur de lecture `history` est également fermé aux valeurs inconnues au lieu de revenir implicitement aux ventes.

Reproducer RF-003 conservé et étendu : Manager personnel refusé, types invalides/missing/null/vides/objets/tableaux/booléens/nombres refusés, userId forgé ignoré, compte de présence inchangé après chaque refus, type purchases avec un identifiant de présence ne sélectionnant jamais attendances, suppression historique autorisée fonctionnelle, Owner toujours interdit de suppression personnel. Tests unitaires supplémentaires sur les discriminateurs.

Audit des sélecteurs adjacents : action de présence allowlist CLOCK_IN/CLOCK_OUT avant mutation de présence ; rôles saveUser allowlist et protection serveur des cibles ; état des achats et motifs des mouvements validés ; destinataire all/user allowlist et expéditeur lié à la session ; permissions individuelles limitées aux codes hérités existants ; paramètres sur allowlist ; action de filtre audit paramétrée sans choix de table. Grain de rapport utilise des expressions SQL fixes sous une même autorisation FINANCES:READ (fallback de présentation, pas de privilège supplémentaire). Aucun nouveau contournement confirmé de ces seuls discriminateurs pendant cette revue ; ce constat n’est pas une certification exhaustive de tous les IPC.

### Validation RF-003 avant RF-004

- Test ciblé IPC RF-003 PASS, assertions de non-suppression conservées ; nouvelle matrice unitaire incluse dans la suite finale.
- `npm run lint` : PASS.
- `npm run build` : TypeScript et Vite PASS.
- `npx vitest run --config vitest.j6ra.config.ts --reporter=json --outputFile=artifacts/rf003-final-tests.json` : **55 fichiers, 289 PASS, 0 FAIL, 0 ignoré**, incluant RF-001, RF-002, SQLite/FK et backup/restore.
- `npm audit --omit=dev --json` : 0 alerte dans la réponse npm.
- `npm start` sur profil isolé caché `artifacts/store-j6ra-rf003-386b0a43817444bf97f50056402a59d5` : sortie 0 ; module natif Electron compatible. Aucun profil personnel utilisé.
- `git diff --check` PASS, index vide.

### RF-004 — faux succès de vente par réutilisation d’une clé existante

**Nouveau blocage d’intégrité métier confirmé pendant la reprise de revue.** Dans `createInvoice`, une clé d’idempotence existante renvoie immédiatement la facture trouvée, avant vérification de la caisse, de l’identité propriétaire de la requête et de la correspondance du panier. La lecture `invoice` expose cette clé via `i.*`, aux rôles autorisés à consulter une facture.

Frontière : renderer authentifié → IPC createInvoice/POS:VALIDATE (employeeId serveur) → raccourci idempotency_key → succès avec une facture étrangère au panier demandé. Ce n’est pas la création d’une nouvelle facture non autorisée : c’est l’acquittement trompeur d’une demande différente par une ancienne facture. Le flux UI traite normalement ce retour comme un succès et vide le panier.

Preuve indépendante : `tests/unit/backend/invoiceIdempotencyRF004.test.ts`, comptes et produits fictifs, vrais handlers IPC et SQLite. Le propriétaire ouvre une caisse et vend A×1. Un autre employé, sans caisse ouverte, lit légitimement la facture, reprend sa clé et demande B×2. Résultat : aucune réjection, ancienne facture A renvoyée comme succès à la demande B. Deux assertions échouent : rejet attendu absent et identifiant de facture existante renvoyé au lieu d’aucun acquittement.

Commande : `npx vitest run tests/unit/backend/invoiceIdempotencyRF004.test.ts --config vitest.j6ra.config.ts --reporter=verbose` : **1 test FAIL**, sortie 1. Ce test reste en échec, sans modification de la logique d’idempotence. Les 289 PASS décrivent l’état avant ajout de ce nouveau reproducer, pas l’état entièrement vert de la suite actuelle.

Arrêt après confirmation RF-004 suivant la règle de release : pas de commit, pas de packaging, pas de correction hors périmètre. La revue de diff/sécurité complète et le gel restent à terminer après remédiation de cette frontière de vente.

## RF-002 remediation / reprise du gel — 2026-09-27

**État actuel : RELEASE BLOCKED — NEW BLOCKER RF-003.** Aucun commit de livraison ni nouvel installateur. Aucun profil personnel modifié, aucune modification de schéma ou réinitialisation des identifiants.

### RF-002 : correction implémentée et testée

Le helper `stripPassword` qui étalait toutes les colonnes sauf le hash du mot de passe est remplacé par `domain/user/publicIdentity.ts`. Liste explicite : id, username, email, role, first_name, last_name, initials, phone, hire_date, active, photo. Aucun champ inconnu n’est recopié. `publicUser` ne sélectionne désormais que ces colonnes SQL ; login et switch conservent leur lecture interne nécessaire à la vérification, mais retournent exclusivement le DTO.

Exclusions explicites par construction : password_hash, security_answer_hash, failed_login_attempts, locked_until, failed_recovery_attempts, recovery_locked_until, last_login_at, security_question, et toute future colonne interne non autorisée. Les questions publiques de récupération restent disponibles uniquement par leurs routes dédiées et le listing administratif existant ; elles ne sont pas les réponses secrètes.

Audit des chemins : setupAdmin/publicUser, login, switchUser, saveUser création/modification ; users avec projection SQL de champs métier ; messageRecipients et présences avec projections SQL limitées ; userPermissions avec id/username/role/active ; session IPC construite explicitement avec id/displayName/role et permissions. verifyAdmin et les changements de mot de passe renvoient des booléens ; recovery renvoie id/question et jamais hash/réponse. La délivrance explicite d’un mot de passe temporaire pour un compte créé/réinitialisé reste un contrat séparé existant, pas un champ de l’identité. Aucun nouveau secret délivré.

Le preload relaie les méthodes autorisées sans reconstruire de ligne DB. Le renderer reçoit ces projections et son type User ne comporte aucun champ de hash. Les diagnostics utilisent leurs propres projections ; les logs IPC n’enregistrent pas les réponses d’identité et les audits de connexion contiennent des identifiants, pas la ligne utilisateur. Les sauvegardes SQLite destinées à la restauration restent distinctes des DTO/UI et conservent nécessairement les données internes d’authentification.

Tests : le reproducer RF-002 reste présent, avec ses assertions inchangées. **Rectification de la preuve précédente** : sa fixture omettait l’e-mail obligatoire du propriétaire et échouait sur INVALID_USER avant l’assertion ; ce n’était donc pas une reproduction dynamique valide de l’exposition, contrairement au rapport précédent. La cause était toutefois visible dans `stripPassword`. L’e-mail fictif `owner@fixture.invalid` a été ajouté pour atteindre réellement les assertions. Aucun allègement de confidentialité.

Nouveaux tests : liste exacte du DTO face à des colonnes sensibles/futures, appels de création/login/switch/session et création/modification/listing utilisateurs via les vrais handlers IPC et SQLite, destinataires, présences, permissions, récupération, diagnostics/audit, vérification backend et récupération toujours fonctionnelles. Les assertions ne publient aucune valeur de hash.

Résultats avant découverte RF-003 : **3 tests RF-002 PASS ; suite complète 53 fichiers, 276 PASS, 0 FAIL, 0 ignoré** (`artifacts/rf002-full.json`), incluant RF-001 et ses tests SQLite/FK/backup. `npm run lint`, `npm run build` (TypeScript et Vite) PASS. Les gates de démarrage Electron et audit npm de cette nouvelle itération n’ont pas été relancées avant l’arrêt RF-003 ; les anciens résultats ne sont pas présentés comme des validations fraîches. La clôture complète de livraison reste impossible.

### RF-003 : contournement de suppression des présences — CONFIRMÉ

Frontière affectée : renderer → preload `deleteHistory` → IPC autorisé par `FINANCES:DELETE` → choix de table backend.

Le type TypeScript `'purchases' | 'personnel'` n’est pas une validation des arguments IPC à l’exécution. Le backend rejette seulement `type === 'personnel'`, puis choisit `attendances` pour toute valeur différente de `'purchases'`. Un Manager possède `FINANCES:DELETE`, mais seulement `PRESENCE:READ`. Il peut donc soumettre un type invalide et supprimer une présence d’un autre utilisateur malgré l’interdiction explicite du type personnel.

Preuve : `tests/unit/backend/historyAuthorizationRF003.test.ts`, profil temporaire exclusivement, vrais handlers IPC et vraie base SQLite. Appel Manager avec `type: 'personnel'` → FORBIDDEN. Même identifiant avec `type: 'invalid-type'` → aucune réjection, ligne supprimée. Lecture SQL en readonly après appel : **0 ligne au lieu de 1**. Commande : `npx vitest run tests/unit/backend/historyAuthorizationRF003.test.ts --config vitest.j6ra.config.ts --reporter=verbose` : **1 test FAIL**, deux assertions constatant le contournement et la suppression. Premier résultat JSON conservé dans `artifacts/rf003-regression.json`.

Arrêt immédiat du gel après constat, conformément à la mission RF-002. Aucun correctif RF-003 appliqué, aucune autre phase engagée. La suite courante contient désormais ce reproducer en échec ; les 276 PASS sont le résultat antérieur à son ajout, pas une déclaration de conformité de l’arbre actuel.

## RF-001 — Article Media Atomicity — 2026-09-27

Statut du défaut reproduit : **RF-001 CLOSED** après réussite du reproducer conservé et de la régression complète de 273 tests. Statut de livraison : **RELEASE BLOCKED — NEW BLOCKER RF-002** (voir ci-dessous). Aucun commit de livraison ni nouvel installateur.

### Cycle de vie audité et cause exacte

1. Le sélecteur importe une copie dans `media/articles/.staging` sous un nom UUID généré ; il ne gère pas le fichier original.
2. Validation existante : taille maximale de 5 Mio, signatures JPEG/PNG/WebP, référence gérée contrôlée. Il s’agit d’une validation de signature, pas d’un décodage graphique complet.
3. L’ancienne implémentation copiait le staging vers sa destination, puis écrivait `products.image_ref` dans une transaction SQLite.
4. Après commit, elle supprimait le staging et éventuellement l’ancienne image dans le même bloc `try`.
5. Si ce nettoyage échouait, le `catch` supprimait la destination nouvelle déjà référencée : RF-001.

Sans image, aucune copie n’est créée. Une mise à jour ordinaire conserve la référence existante. Le remplacement change la référence avant nettoyage de l’ancienne. Le retrait explicite enregistre NULL avant nettoyage. L’archivage ne supprime pas la référence ni l’image. Toutes les lignes, archivées comprises, participent au contrôle des références partagées.

### Séquence corrigée

Validation → copie exclusive de B → flush fichier et vérification des octets → transaction SQLite → indicateur de commit → nettoyage best-effort du staging et de A.

La compensation de B est limitée à l’échec avant commit et vérifie aussi l’absence de référence en base. L’échec du nettoyage ne masque plus l’erreur SQLite d’origine et ne remet pas en cause une sauvegarde déjà validée. Les références gérées sont validées avant suppression ; chemins absolus, traversées et liens symboliques/jonctions aux emplacements contrôlés sont refusés. Les noms UUID sont canoniques et en minuscules ; A et B sont comparés sous cette représentation, et toute suppression reste conditionnée par la recherche des références persistées.

Les incidents de nettoyage sont enregistrés sous `article-media-cleanup-deferred` avec leur phase, sans chemin ni erreur système brute. Les copies non référencées sont classées comme orphelines récupérables par `inspectArticleMedia`, diagnostic strictement en lecture seule. Aucun collecteur destructif ni réparation automatique ajouté.

### Pannes et interruptions

- Préparation/copie échouée : aucune nouvelle référence en base ; une éventuelle copie incomplète reste non référencée et détectable.
- SQLite échoue : A reste référencée et présente ; compensation de B seulement si non référencée. Si cette compensation échoue, B reste orpheline plutôt que de toucher A.
- Après commit, avant nettoyage : B reste référencée et présente ; A et/ou staging peuvent subsister.
- Pendant nettoyage de A : B reste inchangée ; A peut rester orpheline.
- Retrait explicite : avant commit, A reste ; après commit, NULL est autoritaire et A peut être nettoyée sans invalider la base.
- Redémarrage : staging périmé nettoyé de manière tolérante aux erreurs ; fichiers gérés non référencés seulement inventoriés, pas supprimés automatiquement.

Le flush et SQLite FULL améliorent la durabilité mais ne constituent pas une certification contre toute panne matérielle, cache disque défectueux ou coupure réelle ; essais physiques toujours à réaliser.

### Sauvegarde, restauration et rendu

Énumération des références depuis le snapshot SQLite conservée. Une image requise manquante fait échouer le bundle (`BACKUP_FAILED`) ; aucune tolérance silencieuse ajoutée. Les vérifications de chemins, empreintes, manifest et correspondance DB/médias lors de la restauration restent inchangées. Le renderer conserve son placeholder sûr lorsque la lecture échoue ; les erreurs IPC restent filtrées par le mécanisme existant.

### Vérifications réalisées

- Reproducer RF-001 original conservé sans affaiblissement : PASS.
- Dernière exécution ciblée médias/maintenance/bundles : **3 fichiers, 29 PASS, 0 FAIL, 0 ignoré**, rapport `artifacts/rf001-targeted-final.json`.
- Tests d’intégration ajoutés : création sans image, mise à jour inchangée, remplacement réussi, nettoyage A échoué, SQLite échoué avec compensation réussie/échouée, retrait réussi/échoué, collision du token, références partagées archivées, original préservé, redémarrage, bundle/restauration réelle, intégrité SQLite/FK, média manquant et diagnostic en lecture seule.
- Tests existants conservés : signatures JPEG/PNG/WebP, format invalide, taille excessive, références dangereuses, manifest altéré et média absent. Ils ne remplacent pas une validation visuelle de tous les formats.
- Ancien contrat statique J.2 adapté aux nouvelles frontières de compensation ; assertions renforcées sur l’ordre du commit et l’absence de suppression brute de B. Aucun test de panne supprimé.
- Suite complète : `npx vitest run --config vitest.j6ra.config.ts --reporter=json --outputFile=artifacts/rf001-full.json` : **52 fichiers, 273 PASS, 0 FAIL, 0 ignoré**, avant ajout du reproducer RF-002.
- Lint, TypeScript, build renderer/application : PASS. Audit production : 0 alerte dans la réponse npm de cette exécution.
- `npm start` dans le profil isolé `artifacts/store-j6ra-rf001-29ea222369e149f4bfa09b27e97f528f` : sortie 0, module SQLite Electron compatible ; aucun lancement sur profil personnel. Ce smoke n’est pas une recette d’installation Windows propre.
- Diagnostic sur fixture saine : 0 référence manquante ; fixture volontairement endommagée : 1 détectée, référence laissée inchangée. Profils personnels NON inspectés, aucune réparation de données réelles.

### RF-002 — nouveau blocage de sécurité confirmé

Lors de la reprise de revue, `stripPassword` a été constaté supprimant uniquement `password_hash`, pas `security_answer_hash`. `setupAdmin` via `publicUser`, puis `login` et `switchUser`, utilisent cette projection. Le hash interne de la réponse de récupération est donc inutilement exposé au renderer par les réponses IPC d’identité. Ce n’est pas un mot de passe en clair et aucune exfiltration externe n’a été observée ; c’est une violation de la frontière de confidentialité des données d’authentification.

Reproducer : `tests/unit/backend/publicIdentitySecrets.test.ts`. Il utilise exclusivement un propriétaire fictif dans un dossier temporaire et n’affiche que les noms de propriétés. Commande : `npx vitest run tests/unit/backend/publicIdentitySecrets.test.ts --config vitest.j6ra.config.ts --reporter=json --outputFile=artifacts/rf002-identity-regression.json`. Résultat : **1 FAIL, 0 PASS, 0 ignoré**. Le fichier de test reste volontairement en échec ; la suite actuelle ne peut donc pas être annoncée entièrement verte.

Arrêt conformément au §31 de la mission RF-001 : pas de modification des règles d’authentification dans cette correction médias, pas de commit ni packaging. La prochaine remédiation doit définir une projection publique explicite des identités, couvrir tous ses consommateurs et prouver l’absence de secrets de récupération dans leurs réponses. La revue exhaustive de gel et les qualifications post-commit restent à reprendre ensuite.

## Tentative de gel — 2026-09-27 — BLOCKER RF-001

Cette section actualise le statut sans effacer les preuves de la préparation précédente.

**GEL ARRÊTÉ AVANT COMMIT ET PACKAGING**, conformément au §26 du contrat de gel (test en échec).

Baseline : branche `main`, HEAD `d46c56c8af570fe96f9aba875726c1ca2c5abd53`, version `2.0.1`. Avant cette tentative : 68 modifications/suppressions suivies, 356 fichiers non suivis, soit 424 entrées individuelles ; index vide. Listes complètes dans `RELEASE_FREEZE_BASELINE.md`. Aucun changement d’identité, aucune suppression de données, aucun commit ni installateur nouveau.

### VERIFIED — défaut reproductible

Dans `backend/src/database/storeDatabase.ts`, `saveProduct` termine la transaction SQLite avant le nettoyage du fichier temporaire d’image. Si `fs.rmSync(committed.staged)` échoue, le `catch` commun supprime `committed.target`, alors que la base a déjà enregistré sa référence. Résultat : article conservé en base, image référencée supprimée, opération signalée en erreur. Une erreur de nettoyage de l’ancienne image partage cette même frontière dangereuse ; ce second cas n’a pas encore été reproduit.

Un test d’intégration supplémentaire injecte uniquement un `EPERM` sur le nettoyage du staging dans un profil temporaire, utilise la vraie API et SQLite, puis constate que le produit référence le token mais que le fichier final n’existe plus.

Commande : `npx vitest run tests/unit/backend/maintenanceIntegration.test.ts --config vitest.j6ra.config.ts --reporter=json --outputFile=artifacts/release-freeze-media-regression.json`.

Résultat : **9 tests, 8 PASS, 1 FAIL, 0 ignoré**, code de sortie 1. Échec de l’assertion de conservation du média. Ce résultat n’est pas une nouvelle exécution de la suite complète ; les 260 PASS précédents ne couvrent pas cette panne de nettoyage. `git diff --check` reste réussi.

### PENDING — reprise requise

Corriger la séparation transaction/compensation/nettoyage : ne jamais supprimer un média référencé après commit ; gérer les échecs de nettoyage sans annoncer à tort un échec de sauvegarde. Vérifier aussi rollback avant commit, remplacement d’image et nettoyage de l’ancienne image. Le code applicatif n’a pas été modifié pendant cette tentative ; le test de régression est conservé en échec pour rendre le blocage observable.

Revue exhaustive fichier par fichier, classification finale, scan de secrets, indexation, commit, qualification post-commit et packaging restent NON TERMINÉS. Le scan préliminaire a identifié des occurrences de mots de passe dans les fixtures/tests et le code d’authentification ; cela ne constitue ni la preuve d’un secret réel ni une certification d’absence de secrets.

Audit npm actuel : `npm audit --json` et `npm audit --include=dev --json` renvoient 0 alerte depuis le registre configuré `https://registry.npmjs.org/`, sans mise à jour du lockfile. Cet écart avec les 13 alertes historiques ne prouve pas une correction : analyse individuelle et vérification de la couverture encore nécessaires avant gel. Aucun avis historique n’est déclaré corrigé par ce seul résultat.

ACCEPTED LIMITATION : distribution privée non signée autorisée ; ce n’est pas la cause du blocage. Tests Windows isolés et acceptation humaine toujours PENDING. Android reste non implémenté.

Date : 2026-09-27. Statut : PRÉPARATION PARTIELLE — AUCUN NOUVEL INSTALLATEUR FIGÉ.

## A. Baseline avant modification

- Branche `main`, commit `d46c56c8af570fe96f9aba875726c1ca2c5abd53`.
- Arbre fortement modifié et nombreux fichiers non suivis, hérités des phases précédentes. Ce commit seul ne représente pas les sources actuelles.
- STORE / package `store-desktop`, version `2.0.1`.
- Node `22.17.0`, npm `11.5.2`, Electron `43.1.1`, electron-builder `26.15.3`, React `18.3.1`, Vite `8.1.5`.
- Cibles configurées : Windows NSIS, macOS DMG ; scripts Windows x64 et ARM64. Seule Windows x64 est visée ici. Autres architectures non validées.
- Icônes : `build/icon.ico`, `build/icon.png`.

## B. Marque et identité

`package.json.author` passe de `Mo-N-Ster` à `VIBE`. Copyright configurable de l’installateur : VIBE. En-tête de l’aide : STORE · VIBE.

Identifiants conservés : `store-desktop`, `STORE`, `com.monster.store`, nom du raccourci STORE, point d’entrée et version. Aucun déplacement de profil, aucune suppression de données. Ne pas renommer l’appId historique malgré son ancien préfixe de marque : il participe à l’identité NSIS. Le nom d’auteur ne constitue pas une signature Authenticode.

## C. Thèmes

Infrastructure existante conservée : `design-system/tokens.css`, chargée après les anciens styles, puis composants/fondations. Palette claire ardoise/blanc/émeraude et sombre ardoise/émeraude appliquée aux variables `--color-*` existantes. Distribution des rapports convertie de couleurs littérales vers six tokens analytiques.

Adaptations de lisibilité : texte sombre sur le fond primaire clair #059669 ; texte blanc au survol #047857. Succès et avertissement textuels clairs utilisent des variantes plus foncées (#047857/#92400E), avec accents approuvés séparés. Danger sombre utilise un texte sombre. Focus et mécanisme de contraste renforcé conservés.

Ce lot n’est PAS une validation visuelle exhaustive. Certains anciens styles littéraux et couleurs générées de courbes subsistent ; leur réconciliation, mesures de contraste et observations de tous les écrans restent à terminer. Exports clairs existants inchangés.

## D. État client initial et exclusions

Le programme initialise `store.db` sous `app.getPath('userData')`, pas dans app.asar. Médias : `media/articles`, sauvegardes : `backups`, diagnostics : `logs/technical.jsonl`, sous ce même profil. Le chemin absolu dépend de l’environnement Electron ; aucun nouveau nom de dossier n’est imposé.

Packaging applicatif : `dist/**/*`, `package.json`, dépendances de production ajoutées par electron-builder. Exclusions explicites supplémentaires : maps, .db, .sqlite, .env. Ressources natives SQLite décompressées. L’inspection du prochain ASAR est OBLIGATOIRE : ces motifs ne prouvent pas seuls l’absence de secrets ou fixtures.

Comptes, articles, stocks, ventes, achats, présences, messages, SMTP, médias et sauvegardes : chacun reste À VÉRIFIER sur la nouvelle installation isolée. Aucune affirmation de propreté d’un installateur non encore produit. Aucun profil personnel n’a été effacé pour simuler cette vérification.

## E. Mises à jour

17 migrations ordonnées, journal `schema_migrations`, transaction par migration. Sauvegarde pré-migration si base existante et migrations en attente. Aucune réinitialisation sur changement de version. `deleteAppDataOnUninstall: false` explicite la conservation du profil à la désinstallation.

Réinstallation même version, passage version précédente → nouvelle, désinstallation et conservation effective de toutes les données : À EXÉCUTER en Windows isolé. Les tests de migration ne remplacent pas ces essais NSIS.

## F. Windows RC et traçabilité

Packaging final NON EXÉCUTÉ : contrat §29 exige un arbre propre et un commit exact. Ne pas commiter aveuglément `artifacts/` : contient des résultats et fichiers techniques qui ne doivent pas devenir une livraison client.

Nom prévu par configuration : `STORE Setup 2.0.1-x64.exe`. Nouveau chemin, taille, date, commit de livraison, SHA-256 : NON DISPONIBLES. L’ancien installateur et son empreinte ne qualifient pas les présentes modifications.

## G–H. Qualification automatisée et sécurité

Résultats de cette exécution dans `artifacts/private-rc-tests.json` : 260 tests réussis, 0 échec, 0 ignoré. Lint réussi ; TypeScript et build Vite réussis. Compilation et tests initialement bloqués par `spawn EPERM` en sandbox, puis réussis avec permission adaptée. `git diff --check` réussi. Les smoke tests Electron et essais visuels de cette nouvelle palette restent à exécuter.

Audit production relancé : 0 alerte. Ne couvre pas à lui seul Electron/Chromium, déclaré dépendance de développement. Les 13 alertes d’outillage du rapport précédent restent à réévaluer individuellement : versions, chaînes, inclusion ASAR, exploitabilité, correctifs et risque. Aucune clôture implicite.

## I–J. Installation et tablettes

Installation sans dépôt/Node, premier Setup, redémarrage, persistance, upgrade, réinstallation, désinstallation : NON EXÉCUTÉS pour cette RC.

Matrice 1920×1080, 1366×768, 1280×800, 1024×768, 800×1280 en clair/sombre/contraste renforcé : NON OBSERVÉE pour ce lot. Tester toutes les pages et dialogues du contrat, clavier, tactile, débordements et actions accessibles.

## K. Android

ANDROID NOT YET IMPLEMENTED — ARCHITECTURAL ADAPTATION REQUIRED

React et les validateurs purs sont réutilisables sous réserve de découplage. Preload/IPC : adaptateur requis. Node/fs, better-sqlite3 natif, dialogues Electron, cycle de vie, impression/PDF Electron : incompatibles directement avec Android et nécessitent des remplacements. Nodemailer : remplacement de transport requis ; pas de secret SMTP dans une WebView. Médias et sauvegardes : adaptateurs au stockage privé Android et sélection de documents. safeStorage : remplacement par stockage protégé Android/Keystore. Installation NSIS : remplacée par modèle APK signé et migrations locales.

Une étude dédiée peut comparer Capacitor avec plugins natifs SQLite/fichiers/impression et shell Android dédié. Aucun choix définitif ni APK ici. Par défaut envisager une instance locale indépendante ; partager en direct les données Windows nécessiterait un projet distinct de synchronisation, hors périmètre. Aucune réécriture Windows engagée.

## L. Acceptation humaine

À renseigner pour l’empreinte du futur artefact : observateur, date, Windows, appareil/VM, résolution, thème, scénario, PASS/FAIL, défaut et gravité. Aucun PASS visuel automatique. Défaut critique métier/sécurité/intégrité bloque livraison.

## M. Distribution et étapes restantes

ACCEPTED DEPLOYMENT LIMITATION — PRIVATE CONTROLLED DISTRIBUTION : livraison non signée autorisée par le contrat. Windows peut afficher Unknown Publisher/SmartScreen. Canal de confiance et SHA-256 obligatoires ; reconsidérer signature avant diffusion publique/multi-client.

Restent : réconciliation CSS et revue visuelle, audit complet des dépendances, tests premier lancement/upgrade et native runtime, revue et commit des sources intentionnelles, build depuis arbre propre, inspection ASAR, gel SHA-256, essais Windows isolés, acceptation humaine. Ni « RC générée » ni « livraison approuvée » à ce stade.
