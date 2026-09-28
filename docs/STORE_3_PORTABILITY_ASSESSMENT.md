# STORE 3.0 — Portability Assessment

Étape 3 · 28 septembre 2026 · analyse et documentation uniquement.

## 1. Purpose

Identifier les éléments réutilisables et les dépendances à remplacer pour préparer l'étape 4, sans choisir d'architecture ni commencer Android. Une implémentation remplacée ne supprime jamais son exigence fonctionnelle.

## 2. Inputs & Scope

- Référence fonctionnelle : [STORE_3_FUNCTIONAL_BASELINE.md](STORE_3_FUNCTIONAL_BASELINE.md), commit `d99c97fe0e00cc148ebc592582f476899049de23` ; domaines A–AE et invariants XINV-01–24.
- Source STORE 2.0.1 gelée : `63b3849ee234248a3b07a643e17dd22fb8c7b23d`, FUNCTIONALLY QUALIFIED, UNSIGNED, inchangée.
- Cible : Android téléphone/tablette, autonome hors ligne, base indépendante par installation. Aucun cloud, serveur central, synchronisation Desktop/mobile ou inter-mobile. Un domaine de confiance local n'est pas un backend distant.
- Méthode : baseline puis inspection ciblée des imports, points d'entrée et contrats. Aucun audit global, test, build, installation ou packaging. Les constats ne qualifient aucun runtime Android et ne garantissent pas la compatibilité mobile des dépendances.

Références techniques abrégées utilisées ci-dessous :

| Réf. | Chemins source inspectés / portée |
|---|---|
| E1 | `package.json`, `backend/src/main/{index,windowManager,ipcHandlers}.ts`, `backend/src/preload/index.cts`, `frontend/src/services/api.ts` : runtime et frontière |
| E2 | `backend/src/database/{storeDatabase,schema,migrations}.ts` : point d'accès, schéma référencé, migrations et transactions ; inspection ciblée, pas revue exhaustive du schéma |
| E3 | `backend/src/domain/` : `sale/canonicalSale.ts`, `cash/cashPolicy.ts`, politiques auth/RBAC/stock/purchase, validateurs product/user, `user/publicIdentity.ts` |
| E4 | `domain/media/articleMedia.ts`, `domain/backup/{backupBundle,restoreRecovery,backupValidation}.ts`, `domain/system/resetRecovery.ts`, raccords E2 |
| E5 | `backend/src/services/{stockPdfService,emailService,technicalLogger}.ts`, exports/impression E1, secrets/file e-mail E2 |
| E6 | `frontend/src/App.tsx`, `hooks/`, `navigation/navigation.ts`, `design-system/components/`, CSS `pos`, `shell`, `reporting`, `i18n/i18n.ts` |
| E7 | `frontend/src/utils/domain.ts`, `pages/Dashboard/charts/reportingModel.ts`, composants de graphes et calculs de rapports E2 |
| E8 | `domain/system/auditWriter.ts`, `domain/rbac/userPermissions.ts`, `domain/attendance/explicitAttendance.ts` : domaines nommés mais couplés au driver/contexte Node |

Les noms de bibliothèques décrivent uniquement l'existant. Les conclusions Android sont des besoins à vérifier, pas des recommandations de versions ou de fournisseurs.

## 3. Classification Model

**KEEP** : réutilisable avec peu/pas de changement plateforme. **ADAPT** : réutilisation substantielle avec adaptation. **REPLACE** : mécanisme Desktop inadapté, comportement conservé. **DROP** : mécanisme inutile sur cible. **DEFER** : extension explicitement hors périmètre initial.

KEEP pour du TypeScript/DOM reste conditionné à un runtime compatible : cela ne sélectionne pas ce runtime. Une réimplémentation dans un autre langage devra préserver les mêmes résultats. Comptage final : une décision par ligne de la matrice §5 uniquement, sans addition des sous-analyses.

## 4. Executive Portability Summary

La réutilisation la plus directe concerne les validateurs purs, politiques, DTO publics, canonicalisation et modèles de présentation. React fournit un patrimoine d'UI web, pas une application Android prête. Les services frontend sont des façades de `window.store`, pas un transport indépendant déjà disponible.

Le stockage/métier est largement concentré dans `storeDatabase.ts` : imports Electron, Node, driver synchrone, e-mail et fichiers y coexistent. Les noms `domain` ne prouvent pas l'indépendance : permissions persistées, présence et audit utilisent encore le driver/contexte Node. Aucun déplacement de ces fonctions vers l'UI n'est acceptable comme raccourci de portage.

L'effort critique porte sur la frontière locale de confiance, les transactions/réessais RF004 et la récupération DB+médias. PDF, impression et SMTP sont trois problèmes distincts. Les 31 domaines restent couverts ; aucune fonction obligatoire n'est différée par cette analyse.

## 5. Implementation Portability Matrix

| Component | 2.0.1 implementation | 3.0 requirement | Classification | Reason | Android capability needed |
|---|---|---|---|---|---|
| P01 React UI | React/DOM, design-system E6 | Tous écrans autorisés | ADAPT | Composants web réutilisables, disposition mobile non qualifiée | UI adaptative/tactile |
| P02 TypeScript métier | Politiques pures + E2 monolithique | Règles Step 2 | ADAPT | Noyau pur conservable, orchestration à découpler | Exécution domaine locale |
| P03 State/context/hooks | Hooks, timers, événements window | État cohérent/retry | ADAPT | État volatile et signaux Desktop | Lifecycle/préférences |
| P04 Navigation | Destinations/droits + pile App | Retour logique protégé | ADAPT | Politique réutilisable, retour système absent | Navigation/lifecycle |
| P05 Validation pure | Validateurs product/user, cash/stock E3 | Contrôles métier | KEEP | Fonctions TS sans ressource plateforme dans ce sous-ensemble | none |
| P06 i18n | Dictionnaires FR/EN, i18next, localStorage | Libellés/formats | ADAPT | Textes réutilisables, persistance/init web | Préférences/locale |
| P07 Accessibilité/responsive | CSS, focus DOM, clavier E6 | Toucher/lecture/clavier | ADAPT | Responsive existant ≠ qualification téléphone | Accessibilité/viewport |
| P08 Electron main | app, BrowserWindow, timers E1 | Démarrage/coordination | REPLACE | Runtime et fenêtres Desktop | Shell/lifecycle |
| P09 Preload | contextBridge/ipcRenderer E1 | API locale contrôlée | REPLACE | Mécanisme Electron | Frontière locale |
| P10 Contrats IPC | Méthodes explicites, DTO, erreurs | Opérations validées | ADAPT | Sémantique conservée, transport remplacé | Interface domaine sûre |
| P11 Auth/session | bcrypt sync, DB, Map sender.id | Identité/récupération/J.4 | REPLACE | Session attachée à webContents | Crypto/session locale |
| P12 Autorisation | Matrice/politiques + requireSession + SQL | RBAC actuel | ADAPT | Politiques portables, enforcement à réhéberger | Autorité locale |
| P13 Modèle SQLite | Tables/relations/snapshots E2 | Modèle métier durable | ADAPT | Potentiel SQL, compatibilité à démontrer | DB relationnelle |
| P14 Accès DB | better-sqlite3 prepare/get/run | Accès autorisé/intègre | REPLACE | Addon Node synchrone | Driver DB local |
| P15 Migrations | SQL + callbacks driver + registre | Ordre/atomicité/restart | ADAPT | Contenu sémantique réutilisable, exécuteur couplé | Migration transactionnelle |
| P16 Transactions | db.transaction et queue IPC | Atomicité/exclusion | ADAPT | Unités métier réutilisables, timing non transférable | Transactions/concurrence |
| P17 Commande canonique RF004 | canonicalSale, tuples versionnés | Équivalence exacte | KEEP | TS pur, aucun hash/Node requis | none ; intégration transactionnelle séparée |
| P18 Filesystem | fs/path, userData, chemins absolus | Fichiers privés/exports | REPLACE | API Node et chemins Desktop | Stockage privé/import-export |
| P19 Médias | Buffer, fsync, copies, références E4 | RF001/images/photos | REPLACE | Validation partiellement portable, I/O couplées | Médias/fichiers |
| P20 Secrets | safeStorage, randomBytes E2 | Protection secrets | REPLACE | Fournisseur Electron et crypto Node | Stockage sûr/crypto |
| P21 Backup | db.backup + bundle JSON/base64 E4 | Snapshot+médias/rétention | REPLACE | Driver/I/O et mémoire Desktop | Snapshot/export sûr |
| P22 Restore | staging, close/copy/reopen, marqueur | Validation/recovery | REPLACE | Protocole conservé, primitives remplacées | Fichiers/DB/lifecycle |
| P23 Reset/recovery | transaction + journal/fsync/cleanup | Reset Owner sûr | REPLACE | Opérations DB+fichiers spécifiques | Recovery durable |
| P24 PDF | HTML/CSS, printToPDF, pdf-lib E5 | Sorties claires/import structuré | ADAPT | Templates/données réutilisables, moteur remplacé | Rendu PDF/export |
| P25 Impression | webContents.print/dialogues | Imprimer sans rejouer vente | REPLACE | Spool/dialogue Desktop | Impression locale |
| P26 SMTP | Nodemailer, Buffer, queue/timers | Envoi/retry local | REPLACE | Transport Node non transférable tel quel | Réseau/secret/tâches |
| P27 Rapports/graphes | SQL, reportingModel, React/SVG/CSS | KPI/tableaux/graphes | ADAPT | Modèles réutilisables, requêtes/interaction à adapter | DB/UI/PDF |
| P28 Audit | SQL + AsyncLocalStorage E8 | Acteur/responsable/caisse | ADAPT | Sémantique portable, contexte Node à remplacer | Contexte fiable/DB |
| P29 Diagnostics | fs.statfs, pragma, logs fichiers | Santé/erreurs sûres | REPLACE | Sources de métriques Desktop | Intégrité/stockage/logs |
| P30 Lifecycle | ready/quit/activate, timers E1 | Reprise/interruption | REPLACE | Fin propre non garantie comme modèle cible | Lifecycle/tâches |
| P31 Packaging/install/update | electron-builder/scripts natifs | Installer/mettre à jour sans perte | REPLACE | Livraison Desktop, aucune chaîne Android existante | Distribution/update |
| P32 Ouvrir dossier Desktop | shell/openDataFolder | Export/diagnostic maintenus ailleurs | DROP | Interaction Desktop non requise Step 2 Y | none |
| P33 Extensions déjà différées | UI CSV/reprise drafts étendue | Pas capacité initiale obligatoire | DEFER | Step 2 H/L/M/§13, aucune suppression des fonctions actuelles | À décider ultérieurement |

## 6. Functional Domain Coverage

Chaque ligne renvoie au domaine homonyme Step 2 §6. Plusieurs besoins peuvent coexister ; « replacement » concerne le mécanisme, non le domaine. DEFERRED CAPABILITY ne désigne que les extensions déjà différées.

| Domaine Step 2 | Besoin de portage | Matrice |
|---|---|---|
| A Identité/authentification | PORTABLE LOGIC + PLATFORM REPLACEMENT | P05/P11/P20 |
| B Rôles/permissions | PORTABLE LOGIC + PLATFORM ADAPTATION | P12 |
| C Session | PLATFORM REPLACEMENT | P11/P30 |
| D Home/dashboard | PORTABLE LOGIC + PLATFORM ADAPTATION | P01/P27 |
| E Cash sessions | PORTABLE LOGIC + PLATFORM ADAPTATION | P02/P14/P16 |
| F POS/sales | PORTABLE LOGIC + PLATFORM ADAPTATION | P01/P16/P17 |
| G Invoices/cancellation | PORTABLE LOGIC + PLATFORM REPLACEMENT | P02/P24/P25 |
| H Products | PORTABLE LOGIC + PLATFORM ADAPTATION ; DEFERRED CAPABILITY : UI CSV | P05/P19/P33 |
| I Product media | PLATFORM REPLACEMENT | P19 |
| J Stock | PORTABLE LOGIC + PLATFORM ADAPTATION | P05/P14/P16 |
| K Stock movements | PORTABLE LOGIC + PLATFORM ADAPTATION | P12/P14 |
| L Inventories | PORTABLE LOGIC + PLATFORM ADAPTATION ; DEFERRED CAPABILITY : reprise étendue | P02/P16/P33 |
| M Purchases | PORTABLE LOGIC + PLATFORM ADAPTATION ; DEFERRED CAPABILITY : reprise étendue | P02/P16/P33 |
| N Suppliers | PORTABLE LOGIC + PLATFORM ADAPTATION | P02/P14 |
| O Team/profiles | PLATFORM ADAPTATION + PLATFORM REPLACEMENT | P01/P11/P19 |
| P Presence | PORTABLE LOGIC + PLATFORM ADAPTATION | P02/P11/P28 |
| Q Reports/KPIs | PORTABLE LOGIC + PLATFORM ADAPTATION | P24/P27 |
| R Local messaging/notifications | PLATFORM ADAPTATION | P01/P14/P30 |
| S Email/report delivery | PLATFORM REPLACEMENT | P24/P26 |
| T Settings | PORTABLE LOGIC + PLATFORM REPLACEMENT | P06/P14/P20 |
| U Audit | PLATFORM ADAPTATION | P28 |
| V Backup | PLATFORM REPLACEMENT | P21 |
| W Restore | PLATFORM REPLACEMENT | P22 |
| X Reset | PLATFORM REPLACEMENT | P23 |
| Y Diagnostics/integrity | PLATFORM REPLACEMENT | P29 ; P32 ne retire pas diagnostic |
| Z Localization | PORTABLE LOGIC + PLATFORM ADAPTATION | P06 |
| AA Accessibility/responsive | PLATFORM ADAPTATION | P07 |
| AB Security boundaries | PORTABLE LOGIC + PLATFORM REPLACEMENT | P09/P10/P12/P20 |
| AC Persistence/transactions | PLATFORM REPLACEMENT | P13–P17 |
| AD Failure/recovery | PLATFORM REPLACEMENT | P21–P23/P30 |
| AE Help/navigation | PLATFORM ADAPTATION | P01/P04/P25 |

## 7. Business Logic Reuse

« As-is » signifie code pur identifié, pas transfert de l'autorité au frontend. Aucun fichier n'est extrait maintenant.

| Zone | Réutilisation | Preuve et limite |
|---|---|---|
| Validateurs article/user ; quantités/stock ; cash/money | REUSE AS-IS | E3 : imports types ou fonctions pures ; montants arrondis ≠ canonicalisation |
| Identité/lockout ; politique password administrative ; statut employé | REUSE AS-IS | Politiques E3 ; orchestration bcrypt/DB séparée |
| Matrice rôles, droits par opération, discriminateurs historiques, DTO public | REUSE AS-IS | E3 ; renommage transport possible, enforcement toujours obligatoire |
| Commande canonique | REUSE AS-IS | `canonicalSale.ts` : tuples versionnés/tri/doublons/omission explicite, pas crypto Node |
| Calculs d'affichage facture et rapports | REUSE AS-IS | `utils/domain.ts`, `reportingModel.ts` ; jamais autorité du commit vente |
| Vente/remise effective, stock, annulation, inventaire, achats | REUSE AFTER EXTRACTION | E2 mélange SQL/politiques/transactions ; conserver ordre et ensembles atomiques, pas copie du seul calcul UI |
| KPI agrégés et filtres SQL | REUSE AFTER EXTRACTION | E2 requêtes avec dates/ROUND ; contrôler types et calendrier du futur moteur |
| Refus individuels/Owner persistés | PLATFORM-COUPLED | `userPermissions.ts` prépare SQL et transaction ; règles réutilisables après séparation |
| Signature présence | PLATFORM-COUPLED | `explicitAttendance.ts` driver+bcrypt sync ; politique de transition pure distincte |
| Audit contextuel | PLATFORM-COUPLED | `auditWriter.ts` AsyncLocalStorage+driver ; éviter perte d'acteur dans appels asynchrones |
| Backup/media/recovery, génération aléatoire, secret SMTP | PLATFORM-COUPLED | E2/E4 Node/Electron ; ne pas qualifier modules entiers de portables |

## 8. UI Portability

| Zone | Classe | Adaptation à prévoir, sans redesign |
|---|---|---|
| Textes, modèles de séries et helpers purs | KEEP | Locale/formatage et mêmes données sources |
| Controls/cards/badges/forms React | ADAPT | Réutilisation conditionnelle DOM ; clavier virtuel, inputMode, focus, masquage password, retry |
| Shell/sidebar/navigation | ADAPT | Retour système et empilement ; aucune page interdite restaurée ; pas choix de framework |
| POS | ADAPT | Deux colonnes 20–24rem, toolbar minimums, cart mobile ; vérifier téléphone étroit/clavier/rotation sans masquer validation |
| Dialogues/drawers | ADAPT | Focus trap/Escape/body overflow actuels ; fermeture tactile/retour, mutation en cours, viewport utile |
| Tables et filtres | ADAPT | Densité/scroll, alignement et attributs consultables sur téléphone ; ne pas supprimer colonnes métier |
| Graphes | ADAPT | SVG/CSS réutilisables ; barres à info hover/focus dans reporting.css nécessitent accès tactile explicite et tableau équivalent |
| Responsive/accessibilité | ADAPT | Breakpoints présents 68/50/40rem etc., cibles source nominales 44px ; ne pas assimiler px à taille physique qualifiée |
| Confirm/reload et fenêtres système | REPLACE | `window.confirm`, reload fatal et dialogues Electron : comportement mobile à définir |

Fenêtre Desktop initiale 1440×900, minimum 720×500 (E1) : preuve que le lancement qualifié ne couvre pas les petits téléphones. Les styles tablette sont un point de départ, pas une validation de toutes les interfaces. Les événements mouse/keyboard/hover restent utiles mais aucun geste indispensable ne peut dépendre du seul survol. Préférences localStorage, refresh au focus et timers doivent être raccordés au lifecycle retenu ; pas de persistance implicite du panier/session.

## 9. Database Portability

| Dimension | Constat source | Conséquence Step 4 |
|---|---|---|
| Sémantique/modèle | SQL SQLite relationnel, contraintes, snapshots, registre migrations | Fort potentiel de réemploi conceptuel ; pas promesse de réutilisation binaire ni d'import Desktop |
| Driver | better-sqlite3 synchrone, singleton DB, prepare/get/all/run | Remplacer ; types résultats/identifiants/erreurs à vérifier |
| Migrations | Callback `up` + insertion version dans même transaction, dernière 18 | Préserver ordre/restart/rollback ; exécuteur et éventuel schéma initial mobile à décider |
| FK/intégrité | foreign_keys ON, quick_check et foreign_key_check | Capacités obligatoires, activation/contrôle sur connexions effectives |
| Durabilité | WAL, synchronous FULL, busy_timeout 5000 ; checkpoint fermeture | Ne pas présumer mêmes options disponibles/effectives ; prouver durabilité et snapshot incluant WAL |
| Transactions | Callbacks sync ; unités vente/stock/paiement/preuve | Une API async ne doit ni committer trop tôt ni entrelacer des étapes |
| Concurrence | Queue pour handlers API E1 ; handlers spéciaux et timers existent aussi | Ce n'est pas preuve de sérialisation universelle ; définir exclusion maintenance/vente/backup, busy/retry |
| Idempotence | Comparaison commande persistée avec acteur/caisse/état | Canonicalisation portable ; décision et écriture exigent contexte transactionnel fiable ; NULL historique refusé |
| Backup | Online backup driver, vérification copie puis bundle | Ne pas remplacer par copie naïve du seul fichier ouvert |
| Types/calendrier | JS number, arrondis, fonctions SQL dates/local/UTC | Préserver sémantique et limites Step 2 ; tester parité, pas changement monétaire implicite |

La compatibilité exacte des requêtes et migrations avec le futur moteur est UNKNOWN tant que moteur/version et adaptateur ne sont pas choisis. Aucune migration 2.0.1 n'est modifiée ; aucune donnée utilisateur utilisée.

## 10. Security Portability

| Garantie | Nature | Portabilité / besoin |
|---|---|---|
| Identité, lockout, password sensible à casse | PORTABLE LOGIC | Politiques E3 ; orchestrer avec compte courant et temps fiable de l'application |
| Vérification bcrypt, randomness | PLATFORM SECURITY CAPABILITY REQUIRED | Source bcryptjs hash/compare sync coût 10, randomBytes/UUID Node ; charge et primitives à qualifier, pas secrets en UI |
| RBAC/Owner/refus subtractifs | PORTABLE LOGIC | Matrice et restrictions conservées ; contrôles d'état/DB restent côté autorité |
| Frontière de confiance, session, identité de l'appelant | PLATFORM SECURITY CAPABILITY REQUIRED | Remplacer contexte isolé/sandbox/Map webContents ; pas SQL générique ni acteur fourni par UI |
| Allowlist DTO/erreurs/discriminateurs | PORTABLE LOGIC | RF002/RF003 conservés à tous les retours/appels ; TypeScript seul n'est pas validation runtime |
| Secret SMTP persistant | PLATFORM SECURITY CAPABILITY REQUIRED | Remplacer safeStorage ; secret indisponible après restauration doit être explicite, jamais exporté en clair comme contournement |
| Média/import et chemins | PLATFORM SECURITY CAPABILITY REQUIRED | Règles taille/signature/allowlist réutilisables ; accès sélectionné limité, octets non fiables, pas chemin libre |
| Audit/historique | PORTABLE LOGIC | Attribution et limitations source maintenues ; remplacement AsyncLocalStorage/driver sans perdre contexte |
| Restore/reset | PLATFORM SECURITY CAPABILITY REQUIRED | Owner/contrôles préalables, backup/recovery durable ; ne pas inventer seconde réauth restore ni enlever password reset |

La séparation UI/domaine est une exigence d'autorité applicative ; ni le stockage privé ni le hash ne suffisent à eux seuls. La résistance à un OS compromis n'est pas promise. J.4 reste sans élévation temporaire ni timeout d'inactivité restauré par défaut.

## 11. Files / Media / Backup

E4 dépend de chemins `userData`, Node fs/path/Buffer, copies exclusives, fsync, rename, fichiers temporaires et suppression explicite. Une sélection mobile ne doit pas être supposée fournir un chemin absolu local durable : résoudre en Step 4 accès sélectionné, durée d'accès et copie privée, sans choisir d'API.

- Médias : réutiliser références logiques, validations et ordre RF001 ; remplacer I/O et lecture d'octets. Pas de suppression d'un original sélectionné ou d'une référence active après échec post-commit. Photos employé aussi concernées (Buffer/base64).
- Bundle actuel : JSON avec fichiers base64, manifeste version 2, tailles et SHA-256 ; inspection limitée à 512 Mio. Tout charger/encoder simultanément crée un risque mémoire mobile réel ; format/streaming à décider sans promesse de compatibilité Desktop.
- Publication : rename sur stockage privé et export externe ne sont pas présumés atomiques de la même manière. Distinguer archive interne complète, export annulé et copie externe incomplète.
- Restore : staging validé, snapshot rollback, marqueur durable, close/replace/reopen et médias ; adapter primitives et ordre de reprise avant exposition du métier.
- Reset : préserver distinction avant/après commit, journal et nettoyage récupérable. Le protocole couvre DB et fichiers, pas une seule transaction magique.
- Temporaires : erreurs/espace faible/interruption ne doivent pas déclencher nettoyage d'un média actif ni effacer le staging nécessaire à une reprise. Rétention des 7 automatiques conservée, pas des exports manuels supprimés arbitrairement.
- Copie hors appareil : reste capacité utilisateur de sauvegarde/export, pas nouveau cloud/sync. Emplacement, permissions et comportement de désinstallation à documenter pour la cible choisie.

## 12. PDF / Print / Email

| Fonction | Réutilisable | Remplacement/adaptation |
|---|---|---|
| PDF facture/rapport | Données, filtres, templates et CSS clair ; metadata STORE structurée | `event.sender.printToPDF` dépend du moteur Electron ; rendition/pagination/export à qualifier indépendamment du thème |
| PDF catalogue structuré | Convention `STORE_DATA_V1`, logique pdf-lib | `stockPdfService.ts` utilise Buffer/base64 ; adapter bytes, bornes et parsing. Pas OCR/PDF arbitraire ajouté |
| Impression | Contenu et statut vente indépendante du résultat | `webContents.print`, dialogue et imprimante Desktop remplacés ; disponibilité/annulation/échec explicites |
| E-mail | Texte/contexte, queue durable, états/retry/backoff | Nodemailer/Buffer/timers remplacés ; transport, secrets et execution locale mobile à décider |

Un partage de fichier via une autre application ne prouve ni livraison SMTP ni succès d'une entrée en queue. Il ne peut silencieusement remplacer le contrat Step 2 S. PDF local ne doit pas dépendre du réseau ; erreur d'impression/envoi ne rejoue jamais la vente. Pas de garantie exactly-once e-mail héritée. Facture achat PDF autonome reste UNKNOWN comme en Step 2, pas nouvelle fonction inventée.

## 13. Android Lifecycle Gap Analysis

| Événement/gap | Hypothèse Desktop identifiée | Exigence / question pour Step 4 |
|---|---|---|
| Foreground/background | Focus window et intervalles | Définir refresh/revalidation au retour ; ne pas promettre timers continus |
| Suspension/termination/low memory | before-quit ferme/checkpoint ; session Map et panier mémoire | Cohérence durable sans callback de fin ; distinguer perte de saisie et vente commitée |
| Recréation/rotation/changement format | React en fenêtre stable, CSS responsive | Décider conservation d'état transitoire et réattachement UI, sans double soumission |
| DB interrompue/réponse perdue | Transaction sync puis réponse IPC | Résoudre résultat ambigu selon RF004, sans seconde vente ni replay non prouvé |
| Backup/restore/reset interrompu | Timers, fichiers et marqueurs | Reprise avant accès métier, exclusion opérations concurrentes, staging encore disponible |
| Temporaires/espace faible | Buffers/copies sur disque Desktop | Budget espace/mémoire et nettoyage sûr vérifiables |
| POS/périphérique interrompu | Panier volatile ; export séparé | Politique explicite de perte/reprise ; facture durable consultable, pas panier restauré supposé |
| Session recréée | Session liée à sender.id | Rétablissement autorisé à définir ; aucune élévation/pointage automatique/ancien timeout |
| Mise à jour | Distribution Electron et migrations | Identité installation/données préservées, migration récupérable ; ne pas confondre uninstall et update |
| Queue et backup périodique | Backup horaire, e-mail 2 min, heartbeat 1 min | Cadence réaliste et rattrapage à préciser ; garanties de service nouvelles non acquises |

## 14. Android Capability Inventory

CORE = sécurité/durabilité/cœur indispensables ; IMPORTANT = fonction obligatoire mais indisponibilité ponctuelle tolérée avec erreur ; OPTIONAL = extension non nécessaire. IMPORTANT ne signifie pas facultatif.

| ID / Capability | Required by | Criticality | Reason |
|---|---|---|---|
| C01 DB relationnelle locale | E–U/AC | CORE | Autorité indépendante/hors ligne, FK |
| C02 Transactions/concurrence/durabilité | E–M/AC | CORE | Atomicité et RF004 |
| C03 Migrations/intégrité | Y/AC | CORE | Update/restart non destructifs |
| C04 Frontière d'exécution autorisée | A–C/AB | CORE | Identité fiable et refus fermé |
| C05 Crypto/hash/randomness | A/I/AB | CORE | Vérification secrets/identifiants/intégrité |
| C06 Protection secrets locale | S/T/AB | CORE | Pas valeur sensible publique |
| C07 Fichiers privés/publication/recovery | I/V–X | CORE | Références et journaux durables |
| C08 Accès médias sélectionnés | I/O | IMPORTANT | Images contrôlées et copie sûre |
| C09 Import/export et copie externe | H/V/W | IMPORTANT | Catalogue/sauvegardes utilisables |
| C10 Snapshot/backup/restore/reset | V–X | CORE | Protection et récupération |
| C11 Génération PDF hors ligne | G/Q/H | IMPORTANT | Documents clairs/structurés |
| C12 Impression | G/AE | IMPORTANT | Fonction conservée, erreurs explicites |
| C13 Livraison e-mail et queue | S | IMPORTANT | Envoi/retry sans dépendance métier réseau |
| C14 Lifecycle/tâches/reprise | C/AD/V/S | CORE | Interruption sûre et scheduling borné |
| C15 UI adaptative/accessibilité/navigation | D–AE | CORE | Téléphone/tablette, clavier/retour |
| C16 Permissions appareil/accès limité | I/V/W/S | CORE | Refus/révocation sans accès arbitraire |
| C17 Diagnostics/logs sûrs | U/Y | IMPORTANT | Santé et incident sans secrets |
| C18 Packaging/update/données locales | AC/AD | CORE | Installation et évolution non destructives |
| C19 Partage utilisateur de fichiers | G/Q/V | IMPORTANT | Remise de documents/export ; distinct de SMTP |

## 15. Dependency Assessment

Classification de l'usage source, non certification mobile ; pas inventaire transitif ni choix de version.

| Dépendance matérielle | Classe | Constat |
|---|---|---|
| React/react-dom/lucide-react | WEB-COMPATIBLE | Composants DOM/SVG, maintien possible seulement avec rendu compatible |
| i18next/react-i18next | WEB-COMPATIBLE | Dictionnaires réutilisables ; bootstrap localStorage à adapter |
| TypeScript/politiques sans I/O | PORTABLE | Code réutilisable dans environnement compatible ; compilation seule ne valide pas entrées |
| better-sqlite3 | NODE-SPECIFIC | Addon natif/ABI Electron ou Node ; remplacement requis |
| Electron/contextBridge/safeStorage | ELECTRON-SPECIFIC | Aucun mécanisme mobile fourni par le dépôt |
| Nodemailer | NODE-SPECIFIC | Transport réseau serveur Node, pas simple composant UI |
| pdf-lib | WEB-COMPATIBLE | Traitement bytes distinct du rendu HTML ; wrapper actuel Buffer à adapter |
| bcryptjs | PORTABLE | Implémentation JS ; charge sync, randomness et environnement de confiance à qualifier |
| node:fs/path/crypto/async_hooks, Buffer | NODE-SPECIFIC | I/O, hash, bytes, contexte d'audit à remplacer/adapter |
| electron-builder et scripts native/launch/verify | ELECTRON-SPECIFIC | Chaîne Desktop, pas chaîne Android |
| Installateur Windows/contrôles RC x64 | WINDOWS-SPECIFIC | Artefact Windows ne qualifie pas mobile |
| Vite/ESLint/Vitest/outillage Node | NODE-SPECIFIC | Outillage hôte potentiellement réutilisable ; ne requiert pas Node sur appareil |
| Futur driver/shell/print/secure storage | REQUIRES MOBILE ALTERNATIVE | Capacité manquante ; alternative précise UNKNOWN, aucun paquet ajouté |

## 16. Migration Risk Register

Risques de portage, pas réouverture de RF001–004 ni nouvelle faille confirmée dans STORE 2.0.1. 9 critical/high, 4 other.

| ID / Risk | Affected capability | Impact | Why it exists | Must resolve in Step 4? |
|---|---|---|---|---|
| R01 Transaction fragmentée | Vente/stock/RF004 | CRITICAL : effets partiels/doubles | Passage callbacks sync → autre accès DB | YES |
| R02 Autorité déplacée UI | Auth/RBAC | CRITICAL : contournement | IPC/sender remplacés, façade window.store | YES |
| R03 Migration/durabilité divergente | DB/update | CRITICAL : perte/histoire incohérente | Driver/pragmas/WAL et exécuteur changent | YES |
| R04 Recovery DB+médias incomplet | Restore/reset | CRITICAL : perte références/données | Deux ressources, staging/journal/close-reopen | YES |
| R05 Publication/accès fichiers | Médias/exports | HIGH : copie incomplète/nettoyage dangereux | Chemins/fsync/rename Desktop | YES |
| R06 Secrets non disponibles/exposés | Auth/SMTP | HIGH : fuite ou envoi impossible | safeStorage et contexte d'autorité remplacés | YES |
| R07 Arrêt à résultat ambigu | POS/session | HIGH : double vente/perte saisie | État mémoire et réponse IPC perdue | YES |
| R08 Budget mémoire/espace | Backup/PDF/médias | HIGH : arrêt pendant maintenance | Bundle entier base64 et copies en mémoire | YES |
| R09 Contrôles cachés téléphone | POS/formulaires | HIGH : opération inaccessible/erronée | Fenêtre minimum Desktop et layouts denses | YES |
| R10 Rendu/imprimante divergents | PDF/print | MEDIUM : sortie inutilisable | printToPDF/print remplacés | YES |
| R11 Timer/transport différents | SMTP/backup auto | MEDIUM : retard/duplication externe | setInterval et Nodemailer | YES |
| R12 Calendrier/nombres différents | KPI/montants | MEDIUM : parité rompue | JS/SQL, locale, UTC/local mixtes | YES |
| R13 Gestes/lecteur/graphes | Accessibilité | MEDIUM : lecture difficile | Hover/focus/tableaux denses | YES |

Résoudre en Step 4 signifie décider capacités/contraintes et preuves futures, non prétendre supprimer ces risques sans implémentation ni essais.

## 17. Step-2 Open Questions

Toutes les questions demandaient un mécanisme ou une qualification mobile : le dépôt établit les obligations et couplages, pas leurs solutions Android. Ne pas compter « problème identifié » comme « solution résolue » : 0 resolved, 10 remaining.

| Question | Statut | Constat acquis / décision restante |
|---|---|---|
| OQ-01 Frontière | REMAINS OPEN FOR STEP 4 | E1 session/IPC/enforcement identifiés ; frontière cible à choisir |
| OQ-02 Stockage/RF004 | REMAINS OPEN FOR STEP 4 | Sémantique/commande pure connues ; driver/transactions à qualifier |
| OQ-03 Lifecycle/session | REMAINS OPEN FOR STEP 4 | Map/état volatile/J.4 connus ; reprise cible ouverte |
| OQ-04 Médias | REMAINS OPEN FOR STEP 4 | RF001 et I/O identifiés ; accès/publication cible ouverts |
| OQ-05 Backup/restore | REMAINS OPEN FOR STEP 4 | Format/protocole connus ; représentation/snapshot/externe ouverts |
| OQ-06 Background | REMAINS OPEN FOR STEP 4 | Intervalles Desktop connus ; cadence/rattrapage cible ouverts |
| OQ-07 Documents | REMAINS OPEN FOR STEP 4 | Templates vs moteur séparés ; rendu/print/partage ouverts |
| OQ-08 Secrets | REMAINS OPEN FOR STEP 4 | safeStorage identifié ; protection et restauration cible ouvertes |
| OQ-09 UI | REMAINS OPEN FOR STEP 4 | Réutilisation web et gaps téléphone identifiés ; stratégie adaptative ouverte |
| OQ-10 Qualification | REMAINS OPEN FOR STEP 4 | Matrice de scénarios requise §13/§16 ; dispositifs/runtime/install lifecycle pas choisis |

Pas de nouvelle question de périmètre : les décisions ci-dessous décomposent ces questions. Compatibilité archives Desktop et amélioration des drafts restent différées, pas conditions nouvelles de l'architecture initiale.

## 18. Step-4 Readiness Gate

PASS documentaire : 33 composants classés, 31/31 domaines couverts, 19 capacités, 13 risques et 10 questions transmises. Les 24 invariants Step 2 restent obligatoires, notamment RF001–004 et J.4 ; aucun domaine obligatoire supprimé/différé. Pas de gagnant technologique, architecture finale, source modifiée ou Android commencé. Aucun gate de qualification 2.0.1 réexécuté.

**READY FOR STEP 4 — ANDROID ARCHITECTURE** signifie que les décisions peuvent être instruites, pas que les mécanismes mobiles sont déjà prouvés.

# Architecture Decision Inputs

Dernière section : décisions uniquement, sans gagnant. Catégories candidates = familles à comparer, pas recommandation ni autorisation d'installation.

| Decision | Required capabilities | Constraints inherited from Step 2 | Portability findings | Candidate categories |
|---|---|---|---|---|
| Shell/runtime local | C04/C14/C15/C18 | Standalone téléphone/tablette | UI React web, Electron remplacé | Shell web embarqué avec capacités locales ; runtime natif/multiplateforme |
| Frontière autorité et API | C04/C05 | RBAC/DTO/RF003/J.4 | Enforcement IPC non réutilisable tel quel | Domaine local isolé derrière interface validée ; services locaux autorisés du runtime |
| DB/transactions/migrations | C01–C03 | XINV-09–20/RF004, aucun reset implicite | Driver sync et queue à remplacer | Accès relationnel natif ; adaptateur relationnel local via frontière contrôlée |
| Secrets/hash/contexte session | C04–C06/C14 | Pas secrets UI, Owner protégé | bcrypt sync/safeStorage/sender.id | Service protégé plateforme ; primitives locales encapsulées et qualifiées |
| Fichiers/médias/import | C07–C09/C16 | RF001, originaux préservés | Chemins/Buffer/fsync Desktop | Stockage privé géré + sélection/export utilisateur contrôlés |
| Backup/restore/reset | C02/C03/C07/C10 | XINV-21–22, rétention/recovery | Bundle mémoire/journal identifiés | Snapshot validé et conteneur local ; format borné ou traitement progressif |
| PDF | C09/C11 | Hors ligne/thème clair/filtres | Rendu Electron distinct de metadata | Rendu document depuis présentation ; composition documentaire dédiée |
| Impression/partage | C12/C19/C16 | Vente indépendante du périphérique | print et dialogues Desktop | Impression système ; export/partage document avec résultat explicite |
| E-mail local | C06/C13/C14 | Queue/retry, pas nouveau serveur central | Nodemailer remplacé | Transport local encapsulé ; partage utilisateur seulement complémentaire, non équivalent SMTP |
| UI/navigation adaptative | C15 | Tous domaines/retour/droits/clavier | Patrimoine DOM réutilisable sous condition | Adaptation UI web ; recomposition mobile conservant modèles et comportements |
| Lifecycle/planification | C14/C02/C10 | Aucune présence implicite ni double commit | Timers/quit non suffisants | Exécution premier plan avec rattrapage ; tâches plateforme contraintes |
| Distribution/update | C18/C03 | Base locale préservée, aucune sync | Builder Desktop remplacé | Distribution privée ; distribution via catalogue, à décider |
| Stratégie de preuve mobile | Toutes, surtout C02/C04/C10 | Non-régression Step 2 sans surpromesse | Tests purs réutilisables, tests Electron non suffisants | Tests contrats/politiques + intégration adaptateurs + essais appareils et interruptions/install/update |
