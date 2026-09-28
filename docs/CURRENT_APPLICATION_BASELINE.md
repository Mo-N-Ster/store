# STORE — Current Application Baseline
## Pre-Production Functional, Technical & Safety Reference

Établi le 28 septembre 2026. Référence de l'arbre de travail, pas certification ni spécification d'une future version.

Qualification du 28 septembre 2026 : **BLOCKED avant gate final/RC**, source non gelée (64 modifications suivies, 4 suppressions, 144 entrées non suivies au contrôle). Audits actuels : production 0 ; tooling inclus 13 paquets signalés (9 high, 3 moderate, 1 low), tous DEV dans le lockfile, exposition de l'artefact non encore vérifiée. Ce résultat remplace toute lecture d'un audit tooling antérieur « zéro » comme preuve actuelle. RF004 et gate 291 PASS restent les dernières preuves logicielles, non relancées. Voir `PRODUCTION_QUALIFICATION_REPORT.md` et `PRODUCTION_ACCEPTANCE_CHECKLIST.md` ; aucun nouveau binaire, commit ou changement fonctionnel.

## 1. Portée, preuves et vocabulaire

Le code courant prime sur les contrats IPC/SQLite, puis les tests, les rapports récents et enfin les anciennes spécifications. Ce document ne change aucun comportement. Aucun test, profil personnel, schéma ou artefact de livraison n'a été modifié pour le produire. Aucune suite n'a été relancée simplement pour découvrir les fonctions.

Révision Git de base : `d46c56c8af570fe96f9aba875726c1ca2c5abd53`, branche `main`. L'arbre contient de nombreuses modifications non commitées : cette référence décrit donc **HEAD + arbre de travail**, et non seulement ce commit. Une future révision doit dater ses nouvelles preuves.

Statuts : IMPLEMENTED = chemin présent ; PARTIAL = chemin présent avec limite explicite ; SUPPORTED = cible opérationnelle prévue, sans présumer sa qualification ; EXPERIMENTAL = configuration/prototype non qualifié ; DEFERRED = hors périmètre actuel ; NOT SUPPORTED = aucun fonctionnement opérationnel fourni ; UNKNOWN = preuve insuffisante. « Test PASS », « buildable », « packaged », « RC », « signé » et « qualifié production » ne sont jamais synonymes.

### Index des sources

Les chemins suivants sont relatifs à la racine du dépôt ; les noms de tests ci-dessous sont dans `tests/unit/backend/` ou `tests/unit/frontend/`. Une mention de test signifie preuve ciblée, pas couverture exhaustive du domaine.

| Repère | Source autoritative ou preuve |
|---|---|
| DB | `backend/src/database/storeDatabase.ts`, `migrations.ts` ; logique métier, SQL, sauvegardes et file e-mail |
| IPC | `backend/src/main/ipcHandlers.ts`, `backend/src/domain/rbac/ipcPermissions.ts`, `backend/src/ipc/channels.ts`, preload |
| RBAC | `backend/src/domain/rbac/permissionMatrix.ts`, `historyOperation.ts`, tests `permissions`, `ipcPermissions`, `securityHardening`, `ipcRefreshJ6RA` |
| AUTH | `backend/src/domain/auth/`, `domain/user/publicIdentity.ts`, tests `loginPolicy`, `publicIdentitySecrets`, `passwordWorkflowJ2`, `sessionRemediationJ4` |
| UI | `frontend/src/App.tsx`, `navigation/navigation.ts`, `pages/Cashier/`, `pages/Dashboard/`, `design-system/` |
| OPS | tests `cashPolicy`, `purchasePolicy`, `stockPolicy`, `traceabilityJ5`, `explicitAttendanceJ3`, `attendanceDecouplingJ3`, `maintenanceIntegration` |
| MEDIA | `backend/src/domain/media/articleMedia.ts`, tests `articleMedia`, `backupBundleJ2`, `maintenanceIntegration` |
| RECOVERY | tests `backupValidation`, `restoreRecovery`, `resetRecovery`, `resilience`, `migrations`, `integratedValidationJ6` |
| REPORT | `frontend/src/pages/Dashboard/charts/`, tests `reportingPhaseF`, `reportDisclosure`, `articleHistory`, `printLight` |
| RELEASE | `package.json`, `electron-builder.yml`, `docs/FINAL_PRIVATE_RELEASE_CANDIDATE_REPORT.md` |
| S1 | `docs/S1_SECURITY_HARDENING_REPORT.md`, `artifacts/s1-targeted.json` |

Rapports F/G/H/J et `GUIDE_UTILISATEUR.md` réutilisés uniquement lorsque compatibles. Les anciennes phrases « phase suivante non commencée » ne décrivent pas l'état présent.

## 2. Identité du produit et périmètre d'exploitation

- Application **STORE**, marque **VIBE**, nom de paquet `store-desktop`, version technique **2.0.1**. « STORE 3.0 » désigne les travaux fonctionnels/documentaires, pas une version npm 3.0.
- Desktop Electron, React 18.3.1/TypeScript, SQLite local via better-sqlite3. Manifestes : Electron `^43.1.1`, Vite `^8.1.5`, electron-builder `^26.15.3` ; les plages ne remplacent pas une attestation de chaque binaire installé.
- Cible principale : **Windows x64**, installateur NSIS. Windows ARM64 possède un script, macOS une cible DMG configurée : non qualifiés. Android/iOS et navigateur autonome : NOT SUPPORTED. **ANDROID NOT YET IMPLEMENTED — ARCHITECTURAL ADAPTATION REQUIRED.**
- Local-first : ventes, stock, présences, comptes, rapports, messages locaux et sauvegardes locales ne nécessitent pas un hébergement distant. Les e-mails exigent réseau et serveur SMTP ; la génération d'un PDF n'exige pas Internet.
- Une base par profil/installation, sans réplication, synchronisation multi-poste ou multi-boutique. Le chat n'est pas un réseau inter-installations.
- Répertoire de données : `app.getPath('userData')` d'Electron ; ne pas confondre nom de produit et chemin effectivement utilisé en développement/installation. `store.db`, `media/articles`, `backups`, `logs/technical.jsonl` et zones de récupération résident dans ce profil. Le remplacement par `STORE_TEST_PROFILE` est réservé au développement et refusé dans l'application packagée.
- L'absence prolongée du propriétaire ne crée pas de délégation automatique. Le Manager poursuit les opérations de son rôle, mais ne récupère pas les droits Administration/Restore/Reset.
- Coupures électriques, stockage défaillant et perte du poste restent des risques physiques : WAL et sauvegardes sur le même disque ne remplacent ni alimentation secourue ni copie externe protégée.

## 3. Architecture et frontières de confiance

```text
React / état UI non fiable
  → preload : méthodes fermées
    → IPC Electron : identité du webContents + politique + validation
      → domaine/backend : invariants, transactions, projections sûres
        → SQLite / fichiers gérés / file SMTP
Electron main → fenêtres, dialogues natifs, impression/PDF, cycle de vie
```

| Couche | Rôle / dépendances | Autorité et responsabilités interdites |
|---|---|---|
| React | Affichage, formulaires, panier, filtres, thème, navigation | Peut masquer des actions, jamais octroyer un droit ni déclarer un paiement durable à lui seul |
| Preload | API explicitement exposée, canaux connus | Pas de SQL, accès arbitraire au disque ou invocation IPC générique |
| Main/IPC | Session liée à l'émetteur, validation, attribution serveur de l'acteur, sérialisation des appels concernés | Ne doit pas croire un `userId`, rôle ou discriminateur fourni par React |
| Domaine/DB | Politiques métier, projections, transactions SQLite | Pas de choix de table arbitraire par le renderer ; les erreurs publiques restent sûres |
| SQLite | Source durable des comptes, stock, factures, présences, audit, paramètres, queue | Le navigateur/localStorage n'est pas source d'autorité |
| Médias | Copie contrôlée, noms gérés, staging et vérification des références | Ne jamais effacer l'original sélectionné ni un fichier encore référencé |
| PDF/impression | Rendu du document par Electron, dialogue fichier/imprimante | Une impression n'est pas une seconde vente ; un PDF n'est pas une sauvegarde intégrale |
| SMTP | Nodemailer, secret déchiffré côté backend, pièces jointes Buffer | Pas de mot de passe retourné dans les paramètres UI ; pas de pièces jointes référencées par URL/chemin externe |
| Backup/recovery | Snapshot DB, médias et journaux de remplacement | Pas de restauration d'un fichier non validé ni de reset implicite à l'ouverture |
| Diagnostic/logs | Événements techniques, état stockage, metadata | Ne pas y copier credentials, objets utilisateurs bruts ou réponses SMTP |
| Packaging | Dist, dépendances de production, module natif, icônes | Ne pas incorporer profils personnels, `.env`, DB ou sources maps |

Fenêtre : isolation de contexte, sandbox, absence de Node dans le renderer et CSP. Ces protections n'exonèrent pas de valider chaque entrée IPC. Les services de fichiers et dialogues natifs sont des capacités privilégiées ; `selectFile`/`saveExport` authentifiés ne constituent pas une API SQL générique.

## 4. Sécurité, sessions et permissions

### 4.1 Modèle actuel

Sessions en mémoire, attachées au `webContents`, non aux valeurs React. Chaque appel authentifié vérifie compte actif et droits courants. Le renderer reçoit une identité minimale et `effectivePermissions`. Il attend ces permissions avant le montage métier, les rafraîchit au focus/événement et toutes les 10 secondes. Une action refusée reste refusée même si le bouton est visible ou le payload forgé.

Le registre classe les méthodes PUBLIC, AUTHENTICATED, PROTECTED ou SYSTEM_INTERNAL. Méthode inconnue/interne : refus. PUBLIC est limité au bootstrap/login/récupération ; AUTHENTICATED ne signifie pas anonyme. Les méthodes PROTECTED possèdent un code fixe. Des guards métier supplémentaires protègent les cibles et états.

**J.4 : aucune élévation temporaire**, aucun `elevatedUntil`, `authorizedById` ou `dropElevation`. Le changement d'utilisateur remplace réellement l'identité et ses droits. Pas d'expiration d'inactivité établie ; sécuriser physiquement le poste partagé.

Passwords bcrypt, sensibles à la casse ; identifiants/nom complet normalisés (NFC, espaces normalisés, minuscules), accents conservés. Recherche username, e-mail, prénom+nom ou nom+prénom ; identité ambiguë refusée. Défaut de protection login : 5 échecs, 15 minutes ; politique interne bornée 3–10 tentatives, 1–1440 minutes, non exposée par l'allowlist courante des paramètres.

Récupération par question/réponse pour les comptes concernés, réponse normalisée et hashée, compteur/verrou de récupération. Ce n'est pas une récupération par e-mail. Le formulaire revient à un état utilisable après récupération ; les secrets saisis ne deviennent pas état persistant du navigateur.

### 4.2 Matrice des droits hérités

R = READ, C = CREATE, U = UPDATE, D = DELETE, V = VALIDATE ; `RCUDV` est une permission générique, **pas la preuve qu'un endpoint correspondant existe**.

| Module backend | Owner / ADMIN (`owner`) | Manager / MANAGER (`manager`) | Employee / CASHIER (`employee`) |
|---|---|---|---|
| DASHBOARD | RCUDV | RCUDV | R |
| PRESENCE | RCUDV | R | R |
| CASH | RCUDV | RCUDV | RCUV |
| POS | RCUDV | RCUDV | RCUV |
| PRODUCTS | RCUDV | RCUDV | R |
| STOCKS | RCUDV | RCUDV | R |
| PURCHASES | RCUDV | RCUDV | — |
| EMPLOYEES | RCUDV | RCUDV | — |
| FINANCES | RCUDV | RCUDV | — |
| ADMINISTRATION | RCUDV | — | — |
| SETTINGS | RCUDV | — | — |
| BACKUPS | RCUDV | — | — |
| RESTORE | RCUDV | — | — |
| RESET | RCUDV | — | — |

Actions effectives : saveProduct et sélection image demandent PRODUCTS:UPDATE ; import PRODUCTS:CREATE ; archive PRODUCTS:DELETE. Vente POS:VALIDATE ; annulation POS:DELETE. Ouverture caisse CASH:CREATE, fermeture CASH:VALIDATE. Achats création/ligne/validation/annulation : PURCHASES:C/U/V/D. Fournisseurs PURCHASES:UPDATE. Inventaire STOCKS:C/U/V. Comptes/mots de passe EMPLOYEES:UPDATE avec restrictions de cible. Présence signée PRESENCE:READ + mot de passe personnel ; correction PRESENCE:UPDATE. Rapports FINANCES:READ, e-mail FINANCES:CREATE. Restore RESTORE:VALIDATE ; reset RESET:VALIDATE + preuve propriétaire.

Permissions individuelles : **retrait uniquement**, droits hérités moins refus persistés (migration 16). Aucun ajout hors rôle. Seul le propriétaire modifie ces refus ; son autorité est protégée. Contrôle optimiste rôle/refus attendus et refus de modification du compte ayant une caisse ouverte. Un compte inactif n'acquiert aucun accès. Le Manager ne peut pas administrer le propriétaire ou s'attribuer un rôle supérieur ; les guards backend priment sur la matrice générique.

### 4.3 Secrets et champs publics

RF-002 : DTO utilisateur allowlist `id, username, email, role, first_name, last_name, initials, phone, hire_date, active, photo`. Aucun hash de mot de passe/réponse, compteur de verrouillage ou future colonne interne propagée automatiquement. Sessions, listes, destinataires, présences et permissions ont leurs projections spécifiques. La question de sécurité peut être exposée par ses routes prévues ; **pas sa réponse ni son hash**.

La délivrance explicite d'un mot de passe temporaire à un administrateur autorisé est une exception fonctionnelle distincte, pas un champ d'identité. Il peut être affiché pour transmission ; ne pas l'enregistrer dans les logs/export UI. Les backups contiennent nécessairement les tables d'authentification : ce ne sont pas des DTO publics, ni des archives chiffrées garanties.

RF-003 : `history` accepte `sales`, `purchases`, `personnel`. `deleteHistory` accepte seulement `purchases` et des IDs entiers positifs. `personnel` est interdit **même au propriétaire**, `sales` passe par l'annulation dédiée. Valeur absente/null/vide/objet/alias/inconnue : zéro mutation. Suppression historique cible la table fixe `stock_movements`, pas `attendances` et pas l'entité achat.

## 5. Scénarios détaillés d'authentification

| Préconditions / acteur | Action | Résultat et rejets | Persistance / audit |
|---|---|---|---|
| Compte actif, sans session | Credentials valides | Session émetteur, permissions serveur ; nom insensible à la casse, password sensible | Métadonnées login/compteurs ; événements auth, pas de password |
| Compte existant, mauvais password | Login répété | Erreur sûre, verrou selon politique ; pas de session nouvelle | Échecs/verrou persistés ; traces auth |
| Compte inactif ou statut incompatible | Login | Refus ; identité ambiguë également refusée | Pas de privilège accordé |
| Session, pas de caisse ouverte | Changer d'utilisateur valide | Identité remplacée, permissions rechargées | Pas de pointage automatique ; événement de changement |
| Session, password cible erroné | Changer d'utilisateur | Ancienne session conservée ; formulaire réessayable | Échec auth, pas de changement d'acteur |
| Session avec caisse ouverte | Switch ou logout | Refus backend jusqu'à fermeture | Caisse et propriétaire conservés |
| Paiement en cours / panier non vide | Switch UI | Verrou pendant opération critique ; confirmation de perte du panier | Verrous UI, pas délégation ni autorisation backend |
| Session sans caisse ouverte | Logout | Session supprimée, écran auth | Aucun checkout/pointage automatique |
| Compte récupérable | Question, réponse correcte, nouveau password conforme | Hash remplacé ; réponse erronée/verrou refuse | Compteurs/récupération audités ; pas de secret dans la réponse |
| Administrateur autorisé, cible permise | Générer ou définir password | Validation policy ; propriétaire protégé | Hash changé et audit ; temporaire montré explicitement, changement forcé à première connexion non établi |
| Aucun Owner initialisé | Wizard puis commit | Owner forcé côté serveur ; second setup refusé | Owner puis paramètres en appels distincts, pas de transaction globale |

PasswordInput commun : œil, saisie successive sans remontage volontaire, focus/réessai ; switch protégé par verrou de soumission et fermeture contrôlée. Tests structurels et scénarios ciblés ne prouvent pas tous les claviers physiques/virtuels.

### Identité, présence et caisse

`utilisateur authentifié ≠ employé présent ≠ propriétaire d'une caisse`. La personne en haut à droite est l'acteur de session/responsable du contexte, non la preuve que tous les employés sont présents. Un pointage nécessite le password de la personne signataire ; l'opérateur facilitant peut être différent. Login/logout/fermeture de STORE ne pointent pas automatiquement. La caisse appartient à son ouvrant ; switch/logout sont bloqués tant qu'elle est ouverte.

## 6. Navigation, accueil et aide

| Rôle par défaut | Visible | Caché / limité |
|---|---|---|
| Owner | Accueil, Caisse, Articles, Stock, Achats, Équipe, Rapports, Messagerie, Administration, Aide | Actions restent soumises aux états métier |
| Manager | Idem sans Administration | Pas de correction présence/admin/restore/reset ; pas gestion Owner |
| Employee | Accueil, Caisse, Articles, Équipe (présence), Aide ; chat d'en-tête | Pas onglet Messagerie, Stock opérationnel, Achats, Rapports, Administration ni comptes Équipe |

Stock navigation demande STOCKS:UPDATE, bien que l'employé ait READ. Équipe accepte EMPLOYEES:READ **ou** PRESENCE:READ ; les sous-onglets sont filtrés. Le masquage Messagerie pour Employee est une règle UX, non une interdiction du chat authentifié. Aide reste disponible dans le shell.

Destination initiale courante de `App` : **POS** si autorisé, également après changement d'utilisateur ; le helper de repli `defaultDestination` choisit le premier élément autorisé, généralement Home. Ne pas prendre son test isolé pour preuve que toutes les connexions ouvrent Home. Historique de navigation en mémoire, retour filtré selon permissions ; le chat est un drawer superposé et ne remplace pas la destination métier. Nouveau message s'ouvre dans le contexte chat, non un second module central.

### Accueil

| Élément | Source / définition | Rafraîchissement / destination |
|---|---|---|
| CA aujourd'hui | Somme factures validées du jour SQLite local | Chargement page ; rapport selon droits |
| Transactions | Nombre de factures validées du jour | Chargement page ; historique/rapport autorisé |
| Panier moyen | CA / transactions, 0 si aucune | Même snapshot |
| Ruptures | Produits actifs, quantité 0 | Catalogue / stock selon droits |
| Évolution 30 jours | Agrégation des ventes validées | Graphique local, pas streaming |
| Stock à surveiller | Quantité ≤ seuil des produits actifs | Liste à défilement propre, accès articles |
| Transactions récentes | Ventes du jour | Liste à défilement propre |
| Actuellement présents | Pointages ouverts | Toutes les 30 s ; clic fiche/signature, bouton centré fiche du jour |
| Contexte du mois | CA validé mois courant et comparaison existante | Sous Présents ; lien Rapports si autorisé |

Colonnes indépendantes : tendance/transactions et stock/présents/contexte mensuel. Cartes de listes défilables sans faire dépendre toutes les listes du scroll global. Skeleton, état vide, erreur et Réessayer. Pas de garantie de rafraîchissement automatique général des KPI toutes les 30 s : cet intervalle concerne les présences. Les dates SQL/local/UTC ne constituent pas une politique temporelle entièrement unifiée.

Aide intégrée : 15 sections (démarrage, navigation, caisse, stock, utilisateurs, messagerie, rapports, paramètres, permissions, sécurité, continuité, routine, urgence, passation, dépannage), recherche titres/contenus, sommaire filtré, compteur de résultats, retour recherche et impression. Guide FR/EN, avertissement sauvegarde. Impression réutilise `printInvoice`, donc son autorisation POS:READ ; un retrait de ce droit peut empêcher d'imprimer l'aide sans empêcher de la lire.

## 7. Caisse, panier et vente

Ouverture : acteur authentifié, CASH:CREATE, montant initial non négatif à deux décimales ; session et référence `CAISSE-…` persistées. Unicité d'une caisse ouverte par utilisateur ; répétition renvoie la session existante. Théorique = ouverture + paiements capturés attachés à la caisse. Fermeture : CASH:VALIDATE, montant compté, différence et snapshots de clôture, événement audit. Ce montant n'est pas le contenu vérifié du tiroir physique.

Catalogue POS : recherche nom/catégorie/hashtag et filtre catégorie ; debounce 150 ms, réponses obsolètes ignorées, erreur réessayable. **La carte produit actuelle ajoute 1 par clic ; elle n'a pas de champ quantité intégré.** La quantité se modifie dans le panier. Une ancienne demande de champ quantité sur carte n'est pas le comportement présent.

Panier : lignes positives automatiquement retenues pour la facture ; quantité 0 retirée/exclue, pas de sélection manuelle obligatoire ; quantités entières, stock vérifié au backend. Pas de vente au poids fractionnaire. Saisie numérique clavier sans incrément involontaire par molette ; virgule/point pour les montants décimaux, pas pour transformer une quantité entière en fraction valide.

Remise montant/pourcentage si activée, calcul du dû, montant reçu et monnaie. Paiement **espèces uniquement**. Pas de carte bancaire, banque ou Mobile Money connecté.

Chemin nominal : session → caisse ouverte de l'acteur → lignes valides → stock suffisant → transaction SQLite (facture, lignes, paiement, mouvements et décréments conditionnels) → reçu → panier vidé et remise réinitialisée. Échec de validation : message sûr, pas de commit partiel ; UI conserve le contexte à réessayer. Double-clic bloqué en UI, clé de requête conservée pour réessai. Une panne après commit mais avant réponse nécessite prudence : ne pas présumer l'absence de vente.

### RF-004 : clôture et contrat de replay

**CLOSED le 28 septembre 2026.** La migration additive 18 ajoute `invoices.idempotency_request TEXT` nullable, sans backfill. Le backend exige désormais acteur serveur identique, même caisse actuellement ouverte, facture validée et commande canonique identique avant replay. Le défaut ancien renvoyait une facture étrangère au panier demandé ; le reproducer est conservé et passe.

Commande canonique versionnée : tuples de lignes productId/quantity triés (doublons conservés), remise demandée avec défaut 0, montant reçu explicite ou marqueur d'omission ; pas d'arrondi supplémentaire, prix courant ou timestamp. Persistée dans la transaction de vente. NULL historique, autre acteur/caisse ou commande différente : refus sans effet métier. Preuve : `invoiceIdempotencyRF004.test.ts`, migration historique isolée et `docs/RF004_CLOSURE_REPORT.md` ; 28 tests ciblés puis suite complète 291 PASS.

## 8. Factures, historique et documents

Référence vente `FACT-…`, date/heure, vendeur, devise et identité boutique enregistrés en snapshots ; lignes avec nom/catégorie/prix/coût observé, quantités, remise, total, reçu et monnaie. Le coût est une estimation issue des informations disponibles, pas une méthode comptable complète.

Factures validées/cancelled, historiques filtrables. Annulation POS:DELETE avec motif ≥3 caractères, transaction de compensation du stock et paiement remboursé (`REFUNDED`), statut annulé ; répétition déjà annulée sans nouveau mouvement. Une annulation n'est pas effacement de la facture. Le théorique clôturé est un snapshot : ne pas présenter le module comme comptabilité de caisse exhaustive après opérations historiques.

Actions du reçu dans l'ordre **Imprimer → Nouvelle vente (vert) → Enregistrer en PDF**, espacées. Une erreur imprimante n'annule pas la vente ; réimprimer depuis l'historique. Nouvelle vente ne doit pas rejouer le commit.

PDF : facture de vente, rapports et stock exportables ; rendu d'impression dédié clair, indépendamment du thème sombre/clair. Rapports préparés pour impression avec données détaillées ; e-mail joint le PDF généré en mémoire. Les achats ont détail daté, lignes et mouvements et rapport Achats exportable ; aucun endpoint dédié équivalent `exportPurchaseInvoicePdf` n'est établi, ne pas promettre une facture fournisseur autonome générée dans le détail achat. L'aide est imprimable.

À vérifier humainement : dimensions/marges, pages longues, tableaux/graphes, accents, lisibilité réelle des couleurs, choix d'imprimante/pilote, annulation du dialogue, fichier PDF et pièce jointe sur les deux thèmes. Les tests `printLight`/`reportDisclosure` ne sont pas une validation visuelle de toutes les sorties.

## 9. Articles et médias

Catalogue : nom, catégorie, hashtag/identifiant, description, prix, quantité entière, seuil minimal, image gérée ; création/édition, recherche/filtre, fiche et archivage. Doublons contrôlés sur nom normalisé et hashtag non vide. Catégories textuelles utilisées dans les filtres, pas un référentiel logistique hiérarchique complet.

`saveProduct` peut produire un ajustement de stock et un historique de prix ; archiver retire le produit actif et ramène son stock à zéro avec mouvement `product_deletion`, sans supprimer son image référencée. Ne pas appeler cela suppression physique des historiques.

UI opérationnelle : **import PDF STORE** portant les données structurées intégrées et export PDF stock. Ce n'est ni OCR de PDF arbitraire ni import libre de toute facture. Une API CSV `importProducts` existe, mais pas de parcours CSV établi dans `ProductList` : ne pas l'annoncer comme bouton utilisateur actuel. Un import est une mutation autorisée, pas une restauration complète ; contrôler la sélection et le résultat avant exploitation.

Médias articles : JPEG/PNG/WebP, maximum 5 Mio, contrôle de signature (pas décodage exhaustif). Copie en `.staging`, noms UUID gérés, original inchangé, preview via API contrôlée, placeholder si lecture impossible. Références et chemins validés, refus des traversées/liens aux emplacements contrôlés.

RF-001 corrigé : copie exclusive finale → flush/vérification octets → transaction DB → nettoyage best-effort. Compensation de nouvelle copie uniquement avant commit et si non référencée. Remplacement/retrait nettoie l'ancienne seulement après changement durable et vérification de toutes les références, archivées comprises. Erreur de nettoyage : orphelin récupérable, pas suppression d'image active ni faux rollback SQL. Inspection des références manquantes/orphelins en lecture seule ; pas de réparation automatique universelle. Les sauvegardes comprennent les médias référencés et échouent si requis manquants. Atomicité SQLite–filesystem reste un protocole, pas une transaction physique unique contre toute panne matérielle.

## 10. Stock, inventaires, achats et fournisseurs

### Stock

États disponibles/faibles/rupture, seuil par produit. Ajustement à quantité entière non négative, motif obligatoire, delta historisé en transaction. Filtre mouvements : période, article, catégorie, motif ; limite usuelle 250, plafond 500. Motifs : `initial`, `adjustment` (et justification), `sale`, `invoice_reversal`, `purchase`, `purchase_cancellation`, `inventory`, `product_deletion`. Labels FR/EN via mapping central, codes techniques conservés comme source.

Vente/annulation/réception/inventaire génèrent leurs mouvements et références. Alertes de stock bas avec traitement/résolution selon état ; e-mail propriétaire peut être mis en file même si SMTP absent. Une suppression autorisée d'historique de mouvements ne remet pas du stock et peut réduire la trace consultable : distincte de l'annulation achat.

### Inventaires

Création DRAFT, snapshot des articles actifs et quantités, comptage enregistré par ligne, écarts/revue puis VALIDATED. Validation applique les écarts par rapport au **stock courant**, pas simplement un delta figé au début. Transaction et refus d'une deuxième validation (`CONFLICT`). Les comptes par défaut reprennent le stock initial : ce n'est pas preuve qu'un comptage physique a eu lieu.

Draft et lignes persistent, mais pas de parcours complet de liste/reprise de draft après fermeture/restart. Pas de fonction opérationnelle d'annulation inventaire démontrée malgré états possibles du schéma. Ne pas recommencer aveuglément un inventaire sans vérifier les effets existants.

### Achats

Fournisseur facultatif actif, référence facture fournisseur, note ; DRAFT avec référence `ACH-…`, clé de création facultative. Lignes article, quantité entière positive, coût non négatif à deux décimales ; mise à jour par article et total. Draft **sans effet stock**. Nouveau produit à la fin du sélecteur ouvre la création dans le contexte du draft, conservant le formulaire ; l'UI exige le droit article approprié.

VALIDATED exige des lignes ; réception ajoute stock et mouvements une seule fois, validation répétée renvoie succès sans doubler. Annulation avec motif, draft sans compensation ; achat validé soustrait la réception si stock disponible, sinon refus atomique. Statut CANCELLED conservé et répétition sans nouveau mouvement. Idempotence de création par clé existante ne doit pas être extrapolée à une garantie d'équivalence de payload non démontrée.

Liste recherche/référence/fournisseur et statut ; détail relit fournisseur, auteur, date/heure, lignes, mouvements. **Le détail ne rouvre pas l'éditeur DRAFT** : les drafts survivent en DB mais reprise UI après fermeture/restart incomplète. Une consultation détaillée n'est pas une reprise.

### Fournisseurs

Nom, téléphone, e-mail, adresse, état actif ; nom unique, normalisation e-mail ; création/édition/recherche et sélection achat. « Nouveau fournisseur » en fin du menu conserve le contexte de création d'achat et sélectionne le résultat. Pas de gestion des dettes fournisseurs, échéanciers ou paiement bancaire établie.

## 11. Équipe, comptes et présences

Compte `users` = authentification/rôle/secrets/état/photo ; profil `employees` lié = code employé, informations RH et statut (ACTIVE, ABSENT, SUSPENDED, RESIGNED, ARCHIVED). L'UI Équipe réutilise le parcours de comptes existant, pas deux copies indépendantes d'un même formulaire. Sous-onglets Employés et Présence selon droits ; Administration accède au même éditeur de comptes.

Champs usuels : identifiant, prénom, nom, initiales, e-mail, téléphone, date d'embauche, rôle, actif, photo ; état RH et identité ne sont pas synonymes. Fiche de détail sur sélection, recherche/filtres. Le propriétaire principal ne peut être dégradé/désactivé via les parcours ordinaires. Le Manager gère les cibles employées permises, pas les privilèges administratifs.

Photo utilisateur : data URL JPEG/PNG/WebP validée, maximum décodé 512 Kio et borne de chaîne 750000 caractères, stockée en DB ; **distincte du filesystem médias articles**. Mot de passe manuel conforme ou génération explicite aléatoire `Store-…!`, minimum 8 caractères ; affichage contrôlé pour transmission. Ne pas présumer obligation de remplacement au prochain login.

Présence : fiche journalière, statut courant, entrée/sortie explicites à heure serveur avec password propre au signataire, sans choisir l'heure dans ce formulaire. Mauvais secret : pas de pointage ; input conserve sa capacité de saisie/réessai. Accès personnel/gestion conditionné aux permissions et au périmètre serveur ; la fiche du jour multi-utilisateur facilite la signature sur poste partagé, sans transformer la session opérateur en preuve de présence des autres.

Historique, filtres période/personne/statut, heures des sessions terminées ; sessions ouvertes n'entrent pas dans les totaux de durée terminée. Correction propriétaire via PRESENCE:UPDATE, motif, intervalle valide, ancien état/correcteur conservés. Répéter une correction n'a pas le même contrat d'idempotence qu'une réception d'achat : ne pas promettre déduplication générale des corrections.

Statuts VALID/CORRECTED/INTERRUPTED et ouvert/terminé en présentation. INTERRUPTED peut représenter des données historiques ; le comportement J.3 actuel ne ferme pas les présences automatiquement à la fermeture/reprise du programme. Un oubli de sortie doit être corrigé selon les droits, pas transformé arbitrairement en heure de fermeture du logiciel.

## 12. Rapports et diagrammes

Accès FINANCES:READ ; Achats requiert aussi PURCHASES:READ, Équipe PRESENCE:READ. Données préparées côté SQL et agrégations UI bornées, pas entrepôt analytique distant. Filtres brouillon puis Appliquer : période inclusive, grain, raccourcis 7/30 jours, reset, article/catégorie suivant onglet, fournisseur Achats, employé/état Équipe. Le snapshot appliqué est distinct de la saisie non encore appliquée.

| Rapport | Finalité / source / KPI | Visuels et détails | Limites et accès complémentaires |
|---|---|---|---|
| Synthèse | `reports.salesSummary`, factures : CA validé, transactions, panier moyen, unités, estimation marge | KPI, évolution/classement ; accès autres onglets | Marge estimée, pas résultat comptable |
| Ventes | Factures + agrégats, validées vs annulées | Nombre par statut, tableau références/date/vendeur/montants/statut, détail autorisé | Lecture factures POS:READ ; statut actuel sur période |
| Articles | Lignes vendues validées, allocation remise, observations prix | Classement CA, top catégories, historique article Revenue/quantité, courbes de prix par article ; tables dépliables | Top source borné ; pas histoire exhaustive de tous les articles si limite atteinte |
| Stock | Stock actuel + mouvements de période | Entrées/sorties par grain ; Période/Article/Type/Entrées/Sorties dans « Données du rapport » | État courant distinct de période ; pas valorisation au coût certifiée |
| Achats | Achats créés dans période, statut courant | Total validé, comptes drafts/annulés, classement fournisseurs, table détails | Date de création et non date de validation ; fournisseur/article, liste bornée |
| Équipe | Historique pointages | Sessions/heures terminées, sessions par statut et heures par employé, table Employé/Début/Fin/Heures/Statut | Ouvert exclu du total heures ; pas productivité/paie |
| Finances | CA validé et total achats validés séparés | KPI avec avertissement périmètre | Pas « ventes − achats = bénéfice », pas bilan/taxes/comptabilité complète |

Calculs : CA de rapport = revenu des lignes alloué par `line_total × invoice_total / invoice_subtotal` sur ventes validées ; transactions = factures distinctes correspondant aux filtres ; panier moyen = CA/transactions ; marge estimée = revenu alloué − quantité × coût snapshot (fallback prix si coût absent). Annulées exclues des ventes validées mais visibles séparément. Coût courant de vente ne vaut pas valorisation FIFO/CMUP réglementaire.

Graphiques locaux SVG/CSS : barres temporelles, classements horizontaux, courbes multi-articles à couleurs/legend distinctes ; choix Revenue/quantité pour historique articles, légende/affichage des séries selon composant. « Données du rapport » affiche/masque les tableaux associés, conserve l'alternative textuelle ; les valeurs ne sont pas uniquement accessibles par couleur. Prix unitaires : courbes superposées, pas histogramme unique.

Export PDF et envoi PDF du rapport courant : nom et période appliquée, préparation du contenu imprimable, palette claire ; envoi FINANCES:CREATE, file durable si échec SMTP. Texte d'accompagnement inclut le contexte/date de demande. Limites : rendu de tables plafonné à 250, classement source jusqu'à 200, certaines listes sources bornées/non paginées ; un PDF rendu n'est pas une extraction exhaustive illimitée de DB. Pas d'affirmation de filtres non présents (entrepôt, devise historique consolidée, validation-date achat).

## 13. Messagerie, alertes et e-mail

Chat **local** entre comptes de cette DB : destinataire individuel ou diffusion autorisée, objet ≤200 caractères, contenu ≤10000, expéditeur serveur. Lecture/non lu et suppression par utilisateur ; pas d'effacement global implicite chez tous les destinataires. Clé de requête pour déduplication, rafraîchissement de la vue ; cadence exacte de chaque composant non contractuellement attestée ici. Nouvelle rédaction dans le drawer chat. Gestion Messagerie sépare chats, alertes de stock et suivis e-mail ; pas de bouton Nouveau message dupliqué dans sa zone de gestion. Employee conserve chat d'en-tête sans onglet de gestion.

Queue e-mail persistée : pending → sending → sent ou pending/failed ; PDF Buffer enregistré avec le message. Claim avant envoi, traitement arrière-plan toutes les 2 minutes ; reprise des sending en pending au redémarrage. SMTP absent : report de 15 minutes sans consommer une tentative ; autre erreur : backoff `min(60, 2^attempts)` minutes, failed à 5 essais. Réessai manuel disponible selon droits. **Exactly-once externe non garanti** : serveur peut accepter un message avant une coupure empêchant de persister sent ; reprise peut le renvoyer. L'échec SMTP ne doit pas annuler vente, stock ou génération locale du document.

## 14. Paramètres, SMTP, administration et audit

Paramètres modifiables sur allowlist : storeName, address, phone, email, currency, discountsEnabled, smtpHost, smtpPort, smtpUser, smtpPassword, smtpFrom, smtpSecure. Strings bornées (1000, password 4096), pas d'injection de nouvelles clés ; booleans textuels true/false, e-mail/host validés, port entier 1–65535, devises EUR/XOF/XAF/CAD/GBP/CHF/NGN/GHS. Langue/thème sont préférences UI locales, pas nouveaux droits serveur. Changer la devise ne convertit pas les anciennes ventes.

SMTP : hôte, port, user/password, From, secure ; `secure=true` implique TLS dès connexion, souvent port 465 ; `false` laisse Nodemailer négocier STARTTLS si disponible. **Le code ne force pas `requireTLS`** : ne pas promettre chiffrement obligatoire pour toute configuration. Secret via safeStorage Electron et migration du stockage historique lorsque possible ; UI reçoit indicateur configuré, pas password chiffré/déchiffré. Une archive déplacée de machine peut nécessiter reconfiguration du secret lié à l'environnement OS. Test d'envoi SETTINGS:VALIDATE ; erreurs publiques SMTP_NOT_CONFIGURED/SMTP_FAILED, pas réponse fournisseur contenant credentials. Pièces jointes mémoire seulement, accès fichier/URL désactivé dans sendMail.

Administration : hub comptes, permissions effectives héritées/refusées/effectives, paramètres, sauvegardes/export/restauration, diagnostics/dossier de données, audit, reset. Navigation/action filtrées et réautorisées. Ce module est implémenté : les anciens documents qui le placent dans une phase future sont historiques.

Audit : timestamp, identifiant acteur, action exacte, cible/référence/statut, motif si capturé ; contexte responsable connecté via session et snapshots caisse (référence, montant théorique, devise). L'acteur signataire et responsable facilitant peuvent différer. Anciens champs absents restent absents, jamais complétés fictivement depuis l'utilisateur actuel. Lecture ADMINISTRATION:READ, filtres date/action/user et pagination clé `beforeId`, lots de 100 ; détails bruts non livrés à l'UI.

Événements couverts notamment : authentification/refus, switch, récupération et changement password, caisse, ajustement/inventaire/achat/fournisseur, corrections présence, permissions et suppression historique. **Ne pas annoncer audit exhaustif de chaque lecture ou chaque opération** ; la transaction de vente et ses mouvements ne sont pas équivalents à un événement explicite d'audit pour toute création.

## 15. Sauvegarde, restauration, reset et SQLite

### Sauvegarde

Manuelle et automatique après présence d'un Owner actif ; vérification au démarrage puis horaire, au plus une automatique par date UTC. Rétention : **7 dernières automatiques** ; pas « 7 dernières sauvegardes toutes catégories ». Nettoyage des temporaires âgés de plus de 24 h. Export via dialogue natif ; copy externe nécessaire contre perte du disque.

Format bundle version 2 `.store-backup`, JSON contenant DB et médias référencés en base64, manifeste tailles/SHA-256. Écriture temporaire exclusive puis renommage ; snapshot SQLite cohérent, contrôles d'intégrité et médias. Pré-sauvegardes avant migrations/opérations destructrices. Pas de chiffrement intégral garanti : hashes de comptes, données personnelles et secrets internes protégés OS restent des données sensibles dans l'archive. Une empreinte vérifie l'intégrité, pas l'identité cryptographique d'un auteur hostile.

### Restauration : séquence réelle

1. Permission RESTORE:VALIDATE, sélection utilisateur et confirmation de remplacement.
2. Valider format/tailles (plafond 512 Mio), références/chemins/médias, hashes ; DB compatible, tables attendues, intégrité/FK et Owner actif dans la candidate.
3. Pré-backup et préparation du rollback DB/médias ; staging et journal `.store-restore-pending`, marqueur écrit durablement.
4. Fermer la DB, remplacer DB/médias selon protocole, rouvrir/migrer, vérifier ; retirer le marqueur seulement après succès.
5. Erreur ou interruption détectée au démarrage : restaurer l'état préparé selon journal, plutôt que continuer silencieusement sur un mélange.

Owner actif dans l'archive ≠ réauthentification du propriétaire connecté. **Pas de seconde saisie password propriétaire établie pour Restore** ; autorisation de session reste nécessaire. Limites : espace disponible, archive compatible, état du disque, permissions fichiers et échecs de récupération simultanés. Aucun essai destructif sur données personnelles n'est une preuve acceptable.

### Réinitialisation

RESET:VALIDATE, propriétaire courant attribué serveur, vérification bcrypt de son password et confirmations UI. Pré-backup puis protocole `.store-reset-recovery` : token et staging/renommage médias, transaction de suppression métier avec marqueur, nettoyage après commit. À la reprise, restaurer les médias si pas de commit DB ; sinon achever le nettoyage différé. Ne pas transformer un nettoyage raté en suppression d'une nouvelle référence.

Conséquences : effacement volontaire des comptes/données métier/paramètres concernés et retour à l'initialisation ; backups conservés, structure/migrations/référentiel de rôles ne sont pas une désinstallation du moteur. Cette opération n'est pas un dépannage normal d'un écran de login. Sa preuve automated est isolée ; récupération physique complète à valider humainement.

### SQLite et concurrence

WAL, synchronous FULL, foreign_keys ON, busy_timeout 5000 ; migrations ordonnées transactionnelles, niveau courant **18**, sauvegarde préalable. Contraintes et transactions protègent ventes, réception/annulation, inventaire, permissions et comptes ; pas de reset automatique lors d'une mise à jour. Intégrité/FK dans tests de maintenance/backup, pas attestation de la base personnelle.

Queue des API DB côté IPC et SQLite synchrone ; timers et handlers spéciaux ne justifient pas l'affirmation « toutes les écritures du programme partagent une seule queue ». UI locks évitent clics simultanés ; invariants serveur restent nécessaires. RF-004 montre que déduplication par clé seule n'est pas équivalence de commande.

## 16. UX, accessibilité, localisation et performances

Shell tablet-first responsive : desktop, tablette paysage et portrait visés, grilles réduites, tableaux conteneur défilable, panier/catalogue avec zones distinctes, fenêtres bornées. Navigation souris/clavier/toucher ; défilement tactile sur zone, pas seulement poignée. Pas de validation générale de chaque résolution ou clavier virtuel physique. Le wizard utilise hauteur dynamique et contenu scrollable ; les formulaires critiques ne doivent pas se fermer pendant un commit.

Design system : labels, status textuels, boutons accessibles, modals/drawers avec gestion focus et fermeture Escape/hors zone selon dismissible, contrôles de mot de passe, tabs clavier, erreurs annoncées, régions scrollables focusables ; cibles tactiles nominales ≥44px. Contraste renforcé via media query, réduction des mouvements ; pas certification WCAG exhaustive ni promesse de conformité lecteur d'écran validée humainement.

Clair/sombre via tokens ; contraste entre surfaces, bordures et états ; FR/EN, libellés statuts/mouvements centralisés. Montants formatés Intl selon langue/devise ; virgule décimale normalisée pour champs monétaires, quantités métier entières. Date/heure visibles sur ventes/achats et audit ; certains calculs emploient UTC d'autres calendrier local : fuseaux et changement de jour à surveiller. PDF utilise palette claire séparée.

Optimisations établies : suppression d'un chargement initial catalogue redondant, debounce 150 ms et révision des réponses ; chargements regroupés plutôt qu'appel par KPI ; tableaux de rapport plafonnés, historiques/lecteurs bornés, pagination audit ; intervalle permissions 10 s, présences 30 s, heartbeat 60 s, queue mail 2 min, vérification backup 1 h. **Aucune mesure de latence/FPS/RAM sur appareil faible n'autorise un gain chiffré**. Pas de virtualisation générale ni pagination complète de toutes les listes ; plafonner le DOM ne plafonne pas nécessairement le transport ou la requête.

## 17. Défaillances et récupération

| Scénario | État protégé | Attendu | Récupération | Risque de perte | Preuve | Limite |
|---|---|---|---|---|---|---|
| Login invalide | Session/droits | Refus/verrou | Réessayer après délai/récupération | Pas mutation métier | AUTH | Support physique non testé exhaustivement |
| Double submit | Transaction | Replay seulement si équivalence prouvée | Vérifier résultat existant | Panier UI non durable | OPS/RF004 PASS | Ancienne clé NULL refusée |
| Stock insuffisant | Facture/stock | Refus atomique | Ajuster panier | Panier non durable | maintenanceIntegration | Stock physique externe inconnu |
| Caisse fermée | Vente/replay | Refus, même avec ancienne clé | Vérifier historique ; caisse originale requise pour replay | Acquittement perdu à vérifier | cashPolicy/RF004 PASS | Nouvelle caisse incompatible avec ancienne commande |
| Coupure vente | DB transaction | Commit entier ou rollback | Relire historique avant revente | Acquittement perdu | SQLite/OPS | Coupure électrique réelle non qualifiée |
| Échec impression | Vente déjà commitée | Erreur sans annulation | Réimprimer | Papier manquant seulement | printLight/IPC | Pilote physique à tester |
| SMTP indisponible | Queue/document | Pending/backoff/failed | Configurer, réessayer | Doublon externe possible | emailRetryPolicy | Pas exactly-once SMTP |
| Restart | DB persistée | Réouverture/migrations/recovery | Login et vérifier états | UI non sauvegardée perdue | RECOVERY | Panier non durable |
| Présence ouverte | Pointage | Reste ouverte | Sortie/correction autorisée | Durée physique imprécise | explicitAttendanceJ3 | Pas sortie auto |
| Draft achat/inventaire interrompu | Lignes DB | Pas stock avant validation | Consulter draft achat ; reprise limitée | Travail UI perdu/inaccessible | traceabilityJ5/UI | Pas reprise éditeur complète |
| Backup échoue | DB active | Erreur, pas archive valide annoncée | Espace/support/média à corriger | Pas nouvelle protection externe | backupBundleJ2 | Disque unique vulnérable |
| Restore échoue | Ancien état préparé | Rollback/journal | Redémarrer, diagnostic prudent | Si disque/rollback aussi défaillant | restoreRecovery | Essais physiques requis |
| Reset interrompu | Snapshot/journal | Commit ou récupération cohérente | Suivre marqueur/recovery | Destruction volontaire après commit | resetRecovery | Backup externe indispensable |
| Nettoyage média échoue | Référence active | Commit conservé, orphelin différé | Inspection contrôlée | Image manquante si panne physique | MEDIA RF001 | Pas GC universel |
| Recherche obsolète | Vue courante | Ignorer ancienne réponse | Nouvelle recherche | Aucun stock muté | UI/useProducts | Pas mesure réseau/CPU |
| Permissions retirées | Backend | Refus dès relecture ; UI refresh | Recharger session | Vue périmée brièvement | ipcRefreshJ6RA | UI refresh ≠ autorisation |
| Switch concurrent | Session/caisse | Verrou UI, refus si caisse | Clôturer/réessayer | Panier confirmé abandonné | sessionRemediationJ4 | Pas session durable interrestart |

Logs `logs/technical.jsonl` : timestamp, niveau/événement/contexte et erreur technique ; erreurs métier publiques normalisées. Diagnostic autorisé : version, schéma, taille DB/espace libre, santé stockage, backups, queue/heartbeat, quick_check ; certains chemins accessibles à l'administrateur/support. Ne pas publier logs/archives sans vérification des données personnelles ; tests de secrets ≠ preuve que tout futur message d'erreur sera sans donnée sensible.

Surveillance renderer : traces chargement/crash, gestion d'unresponsive et tentatives bornées de rechargement ; un rechargement ne reconstitue pas tous les formulaires non enregistrés. Aucune réparation automatique de toute corruption garantie.

## 18. Installation, packaging et baseline de qualité

Installation NSIS assistée Windows, répertoire choisi, raccourcis Bureau/menu Démarrer. `appId=com.monster.store` conservé malgré branding VIBE. `deleteAppDataOnUninstall:false` : intention de conserver les données à la désinstallation ; test réel du cycle install/update/uninstall encore requis. Mise à jour attendue par remplacement binaire et migrations, pas reset. Pas d'updater opérationnel prouvé par la seule production de metadata builder.

Pré-requis matériels minimum chiffrés et qualification exacte des versions Windows : NOT VERIFIED. Windows x64 avec runtime Electron et module natif compatible est la cible ; script ARM64/config DMG ne signifie pas support certifié. Pas service hébergé requis.

Packaging : `dist/**/*`, package.json, dépendances runtime, better-sqlite3 décompressé d'asar ; exclusions `.map`, `.db`, `.sqlite`, `.env*`, script `verify:package`. Nom `STORE Setup 2.0.1-x64.exe`, sortie builder `release`. Dernière RC historique dans `artifacts/release-candidate/` **antérieure aux corrections/branding courants**, non équivalente à l'arbre présent. SHA-256 historique : `982914D0E60C2F7CE34473FA389F653581BA899ACCF1CDA48D9CB6086B4C1579`. Non recalculé dans cette tâche. Authenticode NotSigned, aucun certificat/signing de production démontré ; distribution privée non signée n'est pas certification et peut déclencher SmartScreen.

### Preuves de tests et gates, sans fabrication de PASS

| Élément | Dernière preuve réutilisée | Portée / réserve |
|---|---|---|
| Fichiers actuels | 56 fichiers `*.test.ts` | Comptage dépôt, pas 56 suites toutes exécutées ensemble |
| Dernière suite complète | `npm test` RF004 : 56 fichiers, 291 PASS, 0 FAIL, 0 skipped | Une passe complète après correction ; rapport RF004 |
| RF-004 | Reproducer + scénario équivalence/migration PASS | Vrais IPC/SQLite, données historiques comparées |
| Baseline exécutée | 56 fichiers / 291 tests PASS | RF004 fermé, aucun test ignoré |
| S1 ciblé | `artifacts/s1-targeted.json` : 6 fichiers, 36 PASS, 0 FAIL/skip | Sessions/permissions/navigation, pas moteur vente |
| RF-001 | 29 ciblés puis inclus dans 289 | Médias/fs/SQLite, fermeture du défaut précis |
| RF-002 | 3 ciblés puis inclus dans 289 | DTO/projections et vérifications backend |
| RF-003 | Reproducer + matrice discriminateurs inclus dans 289 | Refus et absence de suppression présence |
| ESLint | PASS RF-004 | npm run lint |
| TypeScript / build | `npm run build` PASS RF-004 (tsc + Vite) | Build ≠ qualification production |
| diff whitespace | PASS RF-004 | Pas preuve d'un arbre propre |
| Electron | npm start isolé, sortie 0 RF-003 | Profil de test, pas installation finale |
| SQLite/FK | PASS tests isolés de maintenance/backup | Pas base personnelle vérifiée |
| Audit production | npm audit --omit=dev : 0 dans dernier rapport RF-003 | Daté, pas garantie perpétuelle |
| Audit tooling | 13 alertes historiques puis retour 0 sans changement lockfile rapporté | Incohérence à expliquer, pas remédiation prouvée |

Intégrations importantes : vrais SQLite et handlers IPC dans maintenance, permissions/RF, backups/restore/reset ; politiques pures et tests structurels React/CSS en complément. Harness Electron UI isolé avec API simulées dans les travaux antérieurs : ne vaut pas workflow bout-en-bout sur poste final. ABI Node/Electron différente : config `vitest.j6ra.config.ts` utilise un vrai binaire SQLite Node isolé ; ce n'est pas une DB factice. `npm test` lance un rebuild natif pouvant rencontrer un module verrouillé par Electron ; pas un échec métier à masquer.

## 19. Validations humaines restantes

À réaliser sur copie/profil dédié, sans remplacer UNKNOWN/NOT OBSERVABLE par PASS : layout multi-résolution (1920×1080, 1366×768, 1280×800, 1024×768, 800×1280 proposés, non tous observés), thèmes/contraste, focus/ordre Tab/lecteur d'écran, saisir passwords successifs et virgules, toucher/scroll/clavier virtuel, modals sans recouvrement, graphes et tableaux, parcours caisse/annulation/présence/switch, PDFs courts/longs et imprimante réelle, SMTP réel/indisponible, installation fraîche, upgrade avec données, désinstallation/réinstallation avec conservation, restauration/reset/interruption sur copie, disque plein/média manquant, coupure physique et dispositif Windows faible. Les anciens checklists non renseignés restent non renseignés.

## 20. Limites connues et fonctions différées

| ID / catégorie | Description / impact | Risque | Contournement | Production blocker? | Preuve |
|---|---|---|---|---|---|
| LIM-01 DATA | RF-004 corrigé ; clés historiques NULL non rejouables | Refus sûr plutôt que faux succès | Consulter facture/historique, ne pas recréer aveuglément une vente | NO | RF004_CLOSURE_REPORT |
| LIM-02 FUNCTIONAL | Draft achat persistant sans réouverture éditeur | Travail interrompu non reprenable normalement | Terminer dans session, consulter détail ; procédure future à autoriser | UNKNOWN | PurchasesPage |
| LIM-03 FUNCTIONAL | Draft inventaire sans reprise complète | Comptage interrompu | Planifier session, vérifier états avant recommencer | UNKNOWN | DB/UI stock |
| LIM-04 SECURITY | Restore sans seconde preuve password | Session Owner laissée ouverte sensible | Verrouiller poste, contrôle opérateur | UNKNOWN | IPC/restore |
| LIM-05 DATA | Backups sensibles non chiffrés intégralement | Exposition si fichier copié | Support externe chiffré/protégé | UNKNOWN | backupBundle |
| LIM-06 DATA | Backup local même disque | Perte poste détruit données et sauvegardes | Export régulier hors poste | NO | Architecture |
| LIM-07 DATA | Setup Owner puis settings séparés | Préférences incomplètes après interruption | Login puis Administration | NO | SetupWizard/H |
| LIM-08 FUNCTIONAL | Livraison mail peut se répéter | Doublon destinataire | Vérifier état/provider avant réessai | NO | Queue DB |
| LIM-09 SECURITY | SMTP secure=false sans requireTLS forcé | Transport dépend serveur/config | Config TLS appropriée | UNKNOWN | emailService |
| LIM-10 DATA | Temps local/UTC mixtes | Limites de période à minuit | Vérifier fuseau/période | UNKNOWN | DB/reporting |
| LIM-11 PERFORMANCE | Sources bornées et pagination incomplète | Troncature/latence grand volume | Périodes/filtres réduits | UNKNOWN | REPORT/DB |
| LIM-12 VISUAL | Pas acceptation humaine finale de tout rendu | Overflow/illisibilité possibles | Matrice visuelle à exécuter | UNKNOWN | Checklists J6/H |
| LIM-13 UX | Panier/formulaires non durables | Saisie perdue après crash | Vérifier historique puis ressaisir | NO | UI |
| LIM-14 FUNCTIONAL | Quantités entières seulement | Pas vente fractionnaire au poids | Unité de vente entière adaptée | NO | stockPolicy |
| LIM-15 RELEASE | Installer historique non aligné source actuelle | Tester/livrer le mauvais état | Identifier explicitement artefact | YES | RELEASE |
| LIM-16 RELEASE | Non signé, qualification install/upgrade absente | SmartScreen et compatibilité non prouvée | Canal privé contrôlé, validation dédiée | UNKNOWN | Builder/RELEASE |
| LIM-17 RELEASE | Audit tooling historique contradictoire ; suite RF004 maintenant verte | Audit à expliquer avant qualification | Revue dédiée des gates restants | UNKNOWN | RF004_CLOSURE_REPORT/audits |
| LIM-18 PLATFORM | ARM64/macOS non qualifiés ; mobile absent | Aucun support promis | Windows x64 cible | NO | Package |
| LIM-19 DEFERRED FEATURE | Cloud/multi-store/remote/mobile | Pas collaboration distante | Exploitation installation locale | NO | Architecture |
| LIM-20 DEFERRED FEATURE | Comptabilité/paie/banque/warehouse/barcodes | Besoins hors produit actuel | Outils distincts, sans prétendre intégration | NO | Domaines existants |
| LIM-21 DATA | Audit historique partiel, suppression mouvements permise | Traçabilité non absolue | Backups et contrôle propriétaire | UNKNOWN | DB/RF003 |
| LIM-22 FUNCTIONAL | PDF facture achat dédié non établi | Détail/rapport plutôt que document autonome | Export rapport Achats | NO | PurchasesPage/IPC |
| LIM-23 SECURITY | Pas timeout session inactivité J.4 | Poste partagé laissé accessible | Verrouillage physique/session explicite | UNKNOWN | sessionRemediationJ4 |

Fonctions intentionnellement différées : synchronisation cloud, multi-boutique/multi-poste distribué, Android/iOS, accès distant, comptabilité générale/fiscale, paie, paiements bancaires/Mobile Money, entrepôts avancés, scanning codes-barres dédié. Ce sont des limites de périmètre, pas des défauts à corriger implicitement dans une release.

## 21. Registre consolidé des invariants

CONFIRMED = appuyé par code/tests dans le périmètre décrit ; QUALIFIED = formulation limitée ; REJECTED = contredit actuellement, pas un comportement sûr à présumer.

| ID | Invariant / statut | Preuve et réserve |
|---|---|---|
| INV-001 | CONFIRMED — backend autorité des droits | IPC/RBAC, relecture session |
| INV-002 | CONFIRMED — renderer ne peut ajouter ses privilèges | Registre, attribution acteur, refus individuels subtractifs |
| INV-003 | CONFIRMED — Owner protégé contre rétrogradation/retraits | saveUser/permissions |
| INV-004 | CONFIRMED — identité, présence, caisse distinctes | J3/J4/cashPolicy |
| INV-005 | CONFIRMED — nouvelle vente et stock transactionnels, replay sans second effet | OPS/RF004 |
| INV-006 | CONFIRMED — draft achat sans effet stock | purchasePolicy/DB |
| INV-007 | CONFIRMED — validation achat répétée n'ajoute pas deux fois | maintenance/DB |
| INV-008 | CONFIRMED — inventaire validé non réappliqué | état/transaction CONFLICT |
| INV-009 | QUALIFIED — annulations métier compensées et conservées | Factures/achats ; historique mouvements effaçable séparément |
| INV-010 | CONFIRMED — restore/reset autorisés backend | Reset demande aussi password, Restore non |
| INV-011 | QUALIFIED — hashes absents des DTO publics | RF002 ; émission explicite temporaire et backups distincts |
| INV-012 | QUALIFIED — update/uninstall sans reset prévu | Migrations/config NSIS ; cycle physique non validé |
| INV-013 | CONFIRMED — type history invalide sans mutation | RF003 ; personnel jamais supprimé via deleteHistory |
| INV-014 | CONFIRMED — nettoyage média après commit ne détruit pas référence active | RF001 |
| INV-015 | CONFIRMED — PDF palette claire indépendante UI | printLight, CSS/service |
| INV-016 | CONFIRMED — présence signée avec preuve personnelle/heure serveur | explicitAttendanceJ3 |
| INV-017 | CONFIRMED — switch/logout interdits caisse ouverte | J4/backend |
| INV-018 | CONFIRMED — succès createInvoice limité à nouvelle vente valide ou replay prouvé acteur/caisse/commande | RF004 PASS ; NULL historique refusé |
| INV-019 | QUALIFIED — échec backup/restore/reset protégé par protocole | RECOVERY ; panne disque totale hors garantie |
| INV-020 | CONFIRMED — aucune élévation temporaire J.4 | sessionRemediationJ4 |
| INV-021 | QUALIFIED — quantités stock normales entières non négatives | Policies/transactions, pas inventaire physique garanti |
| INV-022 | CONFIRMED — panne SMTP ne supprime pas vente locale | Queue découplée |
| INV-023 | QUALIFIED — données sauvegardées contiennent médias référencés | bundle, backup refusé si manque |
| INV-024 | CONFIRMED — refus individuel ne crée jamais un droit hors rôle | permissions/migration16 |

## 22. Catalogue des scénarios fonctionnels

Automatisé = preuve partielle nommée ; « lecture » = implémentation constatée sans preuve automatisée autonome. H = validation humaine complémentaire requise ; tous les parcours visuels sont concernés, même avec test structurel.

| ID | Acteur / précondition | Action | Résultat attendu | Invariant | Automatisé | Humain |
|---|---|---|---|---|---|---|
| SC-AUTH-01 | Actif sans session | Login nom/casse correcte et password | Session minimale/droits | INV-001/011 | AUTH | H saisie |
| SC-AUTH-02 | Compte connu | Mauvais password répété | Refus/verrou, zéro métier | INV-001 | loginPolicy | H erreurs |
| SC-AUTH-03 | Inactif/ambigu | Login | Refus | INV-002 | AUTH | H |
| SC-AUTH-04 | Session sans caisse | Switch valide/invalide | Remplacer ou conserver identité | INV-004/020 | J4 | H focus/retry |
| SC-AUTH-05 | Caisse ouverte | Switch/logout | Refus | INV-017 | J4 | H |
| SC-AUTH-06 | Panier/payment | Switch | Confirmation/verrou UI | INV-005 | UI/J4 | H |
| SC-AUTH-07 | Compte récupérable | Réponse + nouveau password | Remplacement hash, formulaire login utilisable | INV-011 | AUTH/RF002 | H |
| SC-AUTH-08 | Installation neuve | Wizard Owner/preferences | Setup unique, reprise partielle | INV-003 | setupPhaseH | H interruption |
| SC-CASH-01 | Acteur autorisé | Ouvrir montant | Session unique persistée | INV-017 | cashPolicy | H tiroir |
| SC-CASH-02 | Caisse ouverte | Compter/fermer | Théorique/écart/snapshot | INV-004 | OPS | H |
| SC-SALE-01 | Caisse+stock | Vendre/remise/espèces | Facture et stock atomiques | INV-005 | maintenance/posModel | H |
| SC-SALE-02 | Panier qty0/stock insuffisant | Valider | Retirer zéro/refuser insuffisance | INV-021 | posModel/OPS | H |
| SC-SALE-03 | Ancienne clé autre acteur | Demander autre panier | Rejet sans effet durable | INV-018 | RF004 PASS | Gates production distincts |
| SC-INVOICE-01 | Facture commitée | Print/nouvelle/PDF | Document clair, pas nouvelle vente | INV-015 | printLight | H pilote/PDF |
| SC-INVOICE-02 | POS:DELETE | Annuler motif/répéter | Compensation une fois | INV-009 | OPS | H historique |
| SC-PRODUCT-01 | PRODUCTS:UPDATE | Créer/éditer/doublon | Validation ou refus | INV-021 | validators/OPS | H |
| SC-PRODUCT-02 | Article/image | Remplacer/retirer avec erreur cleanup | Référence durable préservée | INV-014 | RF001 | H média |
| SC-PRODUCT-03 | Droits import/export | Import PDF STORE/export stock | Catalogue selon contrat, document lisible | INV-015 | stockPdfService | H PDF |
| SC-PRODUCT-04 | PRODUCTS:DELETE | Archiver | Plus actif, mouvement, média conservé | INV-014/021 | MEDIA/DB | H |
| SC-STOCK-01 | STOCKS:UPDATE | Ajuster avec motif | Delta/mouvement transactionnels | INV-021 | stockPolicy | H |
| SC-STOCK-02 | STOCKS:READ | Filtrer mouvements/alertes | Lecture bornée, motifs lisibles | INV-009 | traceabilityJ5 | H |
| SC-INVENTORY-01 | STOCKS:C/U/V | Créer/compter/valider deux fois | Une application, seconde refusée | INV-008 | OPS | H |
| SC-INVENTORY-02 | Draft persisté | Redémarrer | DB conservée, reprise UI limitée | INV-008 | Lecture DB/UI | H reprise |
| SC-PURCHASE-01 | PURCHASES:C/U | Draft et lignes | Aucun stock | INV-006 | purchasePolicy | H |
| SC-PURCHASE-02 | Draft avec lignes | Valider/répéter | Stock une fois | INV-007 | OPS | H |
| SC-PURCHASE-03 | Achat validé | Annuler motif | Compensation ou refus stock | INV-009 | OPS | H |
| SC-PURCHASE-04 | Draft fermé | Lire détail | Lignes/mouvements, pas éditeur repris | INV-006 | traceabilityJ5/UI | H |
| SC-SUPPLIER-01 | Achat en saisie | Nouveau fournisseur inline | Création/sélection contexte gardé | INV-001 | functionalExtensionsJ2 | H |
| SC-TEAM-01 | Owner/Manager cible permise | Créer/éditer/photo/password | Compte conforme sans fuite hash | INV-003/011 | userPhoto/passwordWorkflow | H |
| SC-TEAM-02 | Owner, cible sans caisse | Retirer droits | Snapshot optimiste/effectifs | INV-024 | permissions/ipcRefresh | H |
| SC-PRESENCE-01 | Fiche jour | Entrée/sortie avec password personnel | Heure serveur/acteur correct | INV-016 | explicitAttendanceJ3 | H focus |
| SC-PRESENCE-02 | Session présence ouverte | Fermer STORE | Ne clôture pas présence | INV-004 | J3 | H reprise |
| SC-PRESENCE-03 | Owner | Corriger motif/intervalle | Originaux/correcteur tracés | INV-009 | attendancePolicy/J3 | H |
| SC-REPORT-01 | FINANCES:READ | Filtres/appliquer/onglets | Sources/statuts cohérents | INV-009 | reportingPhaseF | H |
| SC-REPORT-02 | Rapport | Graphes/séries/table disclosure | Données lisibles, détail disponible | INV-015 | reportDisclosure/articleHistory | H visuel |
| SC-REPORT-03 | Droits export/envoi | PDF/mail | Palette claire, contexte demande | INV-015/022 | printLight/emailService | H |
| SC-MESSAGE-01 | Authentifié | Chat individuel/diffusion/lire | Émetteur serveur, état personnel | INV-001 | securityHardening/DB | H |
| SC-EMAIL-01 | Queue, SMTP absent | Envoi/restart/réessai | Pending/backoff, pas perte transaction | INV-022 | emailRetryPolicy | H SMTP |
| SC-ADMIN-01 | Owner | Paramètres/diagnostic/audit | Validation/secrets masqués | INV-011 | auditSnapshots/validators | H |
| SC-ADMIN-02 | Manager direct IPC | deleteHistory invalide/personnel | Refus et présence inchangée | INV-013 | RF003 | Non nécessaire pour preuve DB |
| SC-BACKUP-01 | Owner actif | Manuel/auto/export | Bundle intègre DB+média | INV-023 | backupBundle/maintenance | H copie externe |
| SC-RESTORE-01 | Archive valide/droit | Restore puis interruption simulée | Vérifier/rollback prévu | INV-019 | restoreRecovery | H poste dédié |
| SC-RESET-01 | Owner/password | Reset/interruption | Backup/commit/recovery cohérents | INV-010/019 | resetRecovery | H poste dédié |
| SC-RECOVERY-01 | Crash/après commit | Restart + historique | Ne pas présumer vente absente | INV-005/018 | resilience/RF004 | H coupure |
| SC-INSTALL-01 | Windows x64 neuf | Installer/lancer | Setup/module natif fonctionnels | INV-012 | Smoke isolé partiel | H obligatoire |
| SC-INSTALL-02 | Profil existant | Upgrade/uninstall/reinstall | Données conservées selon config | INV-012 | migrations/config | H obligatoire |

## 23. Production Non-Regression Contract

Ces exigences protègent les comportements présents ; RF-004 est désormais couvert par les tests de clôture. Ne pas supprimer une fonctionnalité utilisateur pour satisfaire une ancienne spécification.

| ID | Comportement protégé | Preuve | Conséquence régression | Vérification avant release |
|---|---|---|---|---|
| NR-01 | Autorité IPC, discriminants fermés, acteur serveur | RBAC/RF003 | Escalade/suppression | Direct IPC, malformed, zéro mutation |
| NR-02 | DTO allowlist sans hashes | RF002 | Exposition secrets | Tous retours identité + diagnostics |
| NR-03 | No elevation J4, switch/password retry | AUTH/J4 | Mauvais acteur/blocage | Session/caisse/focus/permissions |
| NR-04 | Owner protégé, refus uniquement | permissions | Perte autorité/escalade | Rôles/cibles/droits retirés |
| NR-05 | Setup sans défaut secret ni reset implicite | H/migrations | Perte données/bootstrap illégal | Neuf/existant/interruption |
| NR-06 | Caisse propriétaire, fermeture/écart | cashPolicy | Fonds attribués à tort | Ouvrir/fermer/logout/switch |
| NR-07 | Panier zéro retiré, sélection automatique, décimales monétaires | posModel/decimalInput | Achat bloqué/montant faux | Clavier virgule/point, zéro, bornes |
| NR-08 | Vente atomique et succès correspondant à requête | OPS/RF004 | Stock/vente incohérents | Maintenir les tests RF004 et réessais inter-acteurs |
| NR-09 | Annulations tracées, compensation unique | OPS | Double stock/trace perdue | Annuler/répéter/stock insuffisant |
| NR-10 | Reçu ordre Print/Nouvelle verte/PDF, dates/snapshots | UI/printLight | Vente rejouée/document faux | Historique et imprimante |
| NR-11 | PDF toujours clair et tableaux du rapport | printLight/disclosure | Document illisible/incomplet | Thèmes, pagination, pièce jointe |
| NR-12 | Médias originaux préservés, commit-safe cleanup | RF001 | Perte image active | Injection fautes fs + backup |
| NR-13 | Catalogue/archive/doublons/import PDF | validators/stockPdf | Données dupliquées/perdues | Créer/modifier/import/archive |
| NR-14 | Stock entier justifié, filtres et labels | stockPolicy/labels | Stock négatif/trace ambiguë | Deltas/motifs/FR EN |
| NR-15 | Inventaire validation unique | OPS | Double réconciliation | Répétition/interruption/limite reprise |
| NR-16 | Achat draft sans stock, réception unique, annulation | purchasePolicy | Stock fictif | Cycle entier + détail |
| NR-17 | Création article/fournisseur depuis achat conserve contexte | J2/UI | Saisie perdue | Parcours imbriqués |
| NR-18 | Comptes/photos/passwords/permissions sans duplication éditeurs | J2/userPhoto | Fuite/compte incohérent | Cibles Owner/Manager/Employee |
| NR-19 | Présence personnelle explicite sans logout-auto | J3 | Pointages inventés | Signatures/correction/restart |
| NR-20 | Accueil cartes/listes indépendantes et raccourci présence | homePresence/UI | Information cachée | Scroll/focus/30s/position |
| NR-21 | Navigation réversible, chat drawer, Employee sans gestion messages | navigation/J1 | Mauvais retour/privilege UI | Retours/droits retirés |
| NR-22 | Rapports sept domaines, statuts et graphes/tableaux | REPORT | KPI trompeurs | Périodes, annulations, filtre, export |
| NR-23 | Chat local, alertes, queue e-mail résiliente | DB/emailRetry | Message perdu/doublon trompeur | Destinataires/panne/reprise |
| NR-24 | Paramètres validés/SMTP secret et erreurs sûres | policies/emailService | Fuite ou injection | Entrées malformées/TLS/config |
| NR-25 | Audit acteur/responsable/caisse sans inventer historique | auditSnapshots | Responsabilité attribuée à tort | Contexte absent/présent/filtres |
| NR-26 | Backups complets, restore/reset journaux | RECOVERY/MEDIA | Perte métier/médias | Tests isolés et protocole humain |
| NR-27 | FR/EN, thèmes, accessibilité/saisie continue | DS/labels/decimal | App inutilisable sur tablette | Toucher/clavier/contraste/overflow |
| NR-28 | Aide recherchable/imprimable et consignes continuité | HelpPage | Utilisation dangereuse | Lecture/recherche/print/retraits |
| NR-29 | Migrations/identifiants packaging/données conservées | DB/builder | Profil perdu à upgrade | Install/upgrade/uninstall réel |
| NR-30 | Preuves datées, pas de RC/signature imaginée | RELEASE/S1 | Livraison non représentative | Tree/diff/tests/audit/artefact/hash |

## 24. Registre des contradictions documentaires

| ID | Conflit / affirmation ancienne | Comportement actuel autoritatif | Preuve | Correction documentaire recommandée |
|---|---|---|---|---|
| DOC-01 | S1 Phase E, 90 tests, permissions à construire | Permissions/IPC déjà implémentés ; baseline RF004 291 PASS | S1/RBAC | Rebaser contrat S1 sans rétrogradation |
| DOC-02 | Élévation et expiration décrites en F/E | J4 interdit élévation | sessionRemediationJ4 | Marquer anciennes sections obsolètes |
| DOC-03 | G : lecteur audit absent, permissions seulement lecture | Audit et refus individuels présents | IPC/migrations16–17 | Actualiser statut capacités |
| DOC-04 | « Phase G/H/F non commencée » dans rapports datés | Modules existent | UI/IPC | Conserver comme historique, pas état courant |
| DOC-05 | Anciennes suites prises comme baseline actuelle | RF004 : 291 PASS dans 56 fichiers | RF004_CLOSURE_REPORT | Dater les preuves, ne pas réutiliser l'ancien FAIL comme état courant |
| DOC-06 | Sections RELEASE RF002/RF003 encore « nouveau blocker » | RF001–004 corrigés ; qualification restante | Début RELEASE/code | Lire chronologiquement, synthèse actuelle |
| DOC-07 | Première fixture RF002 prouverait dynamiquement fuite | Échouait auparavant INVALID_USER ; corrigée puis assertions atteintes | Rectification RELEASE | Ne pas réutiliser première exécution comme preuve valide |
| DOC-08 | Reprise achat assimilée au détail disponible | Détail lecture, pas réouverture draft | PurchasesPage | Séparer consultation et édition reprise |
| DOC-09 | Accueil universel comme destination depuis helper | App initialise POS, Home fallback | App/navigation | Documenter entrée réelle vs helper |
| DOC-10 | Champ quantité sur carte demandé historiquement | Carte actuelle ajoute 1 ; panier édite | ProductCard | Décrire comportement présent, pas demande passée |
| DOC-11 | Installer 2.0.1 décrit comme reflet du dernier travail | Binaire historique antérieur fixes/VIBE | RELEASE | Lier chaque artefact à sa source/date |
| DOC-12 | Audit tooling passant interprété comme correctif dépendances | Réponses contradictoires sans lockfile changé | RELEASE | Expliquer provenance avant conclure remédiation |

## 25. Feature inventory

| Domain | Capability | Status | Backend authority | Persistence | Automated evidence | Human validation | Production critical |
|---|---|---|---|---|---|---|---|
| Identity | Setup/login/recovery | IMPLEMENTED | PUBLIC guards/auth | SQLite hashes | AUTH/H | Required | YES |
| Session | Switch/logout sans elevation | IMPLEMENTED | Sender/cash guards | Mémoire | J4 | Required | YES |
| Security | DTO identité sûr | IMPLEMENTED | Allowlist RF002 | Projections | RF002 | Non nécessaire pour preuve champs | YES |
| Security | RBAC/refus individuels | IMPLEMENTED | IPC/Owner | SQLite | permissions/RF003 | Required UX | YES |
| Home | KPI/pré­sents/contexte/listes | IMPLEMENTED | DASHBOARD/PRESENCE | Sources DB | F/homePresence | Required | YES |
| Navigation | Shell/retour/chat/role visibility | IMPLEMENTED | Backend indépendant | Mémoire | navigation | Required | YES |
| Cash | Open/close/history | IMPLEMENTED | CASH | SQLite | cashPolicy/OPS | Required | YES |
| POS | Panier/remise/espèces | IMPLEMENTED | POS/stock | UI puis SQLite | posModel/OPS | Required | YES |
| POS | Idempotence acteur/caisse/commande | IMPLEMENTED | Comparaison serveur, refus NULL | Clé + commande DB | RF004 PASS | Gates production distincts | YES |
| Invoice | Snapshots/annulation/PDF/print | IMPLEMENTED | POS | SQLite/fichier export | OPS/printLight | Required | YES |
| Product | CRUD logique/import PDF | IMPLEMENTED | PRODUCTS | SQLite | validators/stockPdf | Required | YES |
| Product | Import CSV utilisateur | PARTIAL | API existe | SQLite | Backend seulement | Parcours non établi | NO |
| Media | Images articles lifecycle | IMPLEMENTED | Backend/files gérés | DB+filesystem | RF001 | Required | YES |
| Stock | Ajustements/mouvements/alertes | IMPLEMENTED | STOCKS | SQLite | stockPolicy/J5 | Required | YES |
| Inventory | Comptage/validation | IMPLEMENTED | STOCKS | SQLite | OPS | Required | YES |
| Inventory | Reprise draft UI | PARTIAL | Draft durable | SQLite/UI | Lecture | Required | YES |
| Purchase | Draft/réception/annulation/détail | IMPLEMENTED | PURCHASES | SQLite | purchasePolicy/J5 | Required | YES |
| Purchase | Reprise éditeur après fermeture | PARTIAL | Pas parcours complet | SQLite | Lecture | Required | YES |
| Purchase | Facture PDF achat autonome | UNKNOWN | Endpoint dédié non établi | — | Aucune | Required | NO |
| Supplier | CRUD/recherche/inline | IMPLEMENTED | PURCHASES | SQLite | J2/DB | Required | YES |
| Team | Comptes/photos/passwords | IMPLEMENTED | EMPLOYEES/guards | SQLite | userPhoto/AUTH | Required | YES |
| Presence | Signature/correction/historique | IMPLEMENTED | PRESENCE/password | SQLite | J3 | Required | YES |
| Reports | 7 domaines/graphes/tableaux | IMPLEMENTED | FINANCES + domaine | Lecture DB | REPORT | Required | YES |
| Messaging | Chat local/gestion alertes | IMPLEMENTED | Session/STOCKS | SQLite | IPC/DB | Required | YES |
| Email | PDF/queue/retry | IMPLEMENTED | FINANCES/SETTINGS | SQLite + SMTP | emailRetry/emailService | Required | YES |
| Settings | Boutique/devise/SMTP | IMPLEMENTED | SETTINGS | SQLite/safeStorage | policies | Required | YES |
| Audit | Lecteur/contexte responsable-caisse | IMPLEMENTED | ADMINISTRATION | SQLite | auditSnapshots | Required | YES |
| Backup | Local manuel/auto/export | IMPLEMENTED | BACKUPS | Bundle | RECOVERY | Required | YES |
| Restore | Validation/journal/rollback | IMPLEMENTED | RESTORE | DB/files | restoreRecovery | Required | YES |
| Reset | Password/backup/journal | IMPLEMENTED | RESET/Owner | DB/files | resetRecovery | Required | YES |
| Help | Recherche/sommaire/print | IMPLEMENTED | Session, print POS | Textes locaux | Lecture/J2 | Required | YES |
| UX | FR/EN/themes/tablet/accessibilité | PARTIAL | UI non autorité | Préférences locales | DS/structure | Non complète | YES |
| Install | Windows x64 RC courant | PARTIAL | NSIS/main | Profil local | Ancien package/smoke | Non complète | YES |
| Platform | Windows ARM64/macOS | PARTIAL | Scripts/config | Non qualifiée | Non établie | Required | NO |
| Platform | Android/iOS/Web autonome | NOT SUPPORTED | Aucun runtime adapté | — | — | — | NO |
| Cloud | Sync/multi-store/remote | DEFERRED | — | — | — | — | NO |
| Finance | Comptabilité/paie/banque/Mobile Money | DEFERRED | — | — | — | — | NO |
| Logistics | Warehouse avancé/barcodes dédiés | DEFERRED | — | — | — | — | NO |

## 26. Production readiness snapshot

Constat documentaire, **pas décision de livraison ni lancement de certification**.

| Champ | État factuel |
|---|---|
| Current version | 2.0.1, STORE by VIBE ; arbre modifié non gelé |
| Current test baseline | 56 fichiers/291 tests PASS, 0 FAIL, 0 skipped, npm test RF004 |
| Build | Gate RF004 PASS |
| Lint | Gate RF004 PASS |
| TypeScript | Build RF004 PASS |
| Electron | Smoke isolé RF003 PASS ; pas qualification de l'installateur courant |
| SQLite integrity | Tests isolés PASS ; base personnelle NOT VERIFIED |
| Production dependency audit | Dernière réponse RF003 : 0 ; état futur non garanti |
| Tooling audit | Contradiction historique non expliquée ; remédiation NOT VERIFIED |
| Installer | Historique 2.0.1 x64, pas reflet de l'arbre actuel ; aucun généré ici |
| Code signing | Historique NotSigned ; capacité/certificat de production NOT VERIFIED |
| Human visual validation | Incomplète / NOT VERIFIED pour matrice finale |
| Installation validation | Smoke/package historique partiels, poste propre final NOT VERIFIED |
| Upgrade validation | Conservation prévue ; validation physique finale NOT VERIFIED |
| Backup/restore validation | Tests isolés présents/PASS historique ; procédure humaine réelle incomplète |
| Reset validation | Tests isolés présents/PASS historique ; récupération physique finale NOT VERIFIED |
| Known production blockers | RF004 CLOSED ; artefact source-aligned absent et gates de qualification/revue/humains restants, sans présumer PASS |
| Known non-blocking limitations | Local-only, quantités entières, scope non comptable/mobile/cloud, panier non durable ; limites UNKNOWN au registre à décider explicitement |

Référence initialement créée sans changement applicatif. Mise à jour ciblée RF004 après correction autorisée et migration 18 sur fixtures ; aucune donnée personnelle, version, packaging, commit de release ou installateur modifié.
