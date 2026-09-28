# Architecture technique

## Ajout J.6R-A — permissions individuelles et audit

Migration 16 : `user_permission_denials` soustrait des droits hérités, sans modifier les matrices de rôles. Seul le propriétaire actif peut enregistrer des retraits sur un non-propriétaire ; cible avec caisse ouverte et modifications concurrentes refusées. Transaction commune avec la trace d'audit. Les propriétaires restent protégés.

Chaque requête protégée recalcule l'identité active et les permissions côté backend. Le renderer actualise la session au focus, à l'événement de modification et toutes les 10 secondes ; il n'est pas l'autorité de sécurité. Les canaux explicites `userPermissions`, `saveUserPermissions`, `auditLogs` restent dans l'allowlist. L'audit expose uniquement des métadonnées persistées, 100 lignes maximum, jamais les détails bruts.

La complétude de phase n'est pas acquise : voir `PHASE_J6RA_COMPLETION_REPORT.md`, notamment atomicité de réinitialisation DB/médias, restauration legacy et couverture de tests restante.

STORE est une application Electron, React et TypeScript reposant sur SQLite. Elle sépare
le code en trois zones de confiance.

1. Le processus principal Electron dans `backend/src` possède les accès système, SQLite,
   PDF et SMTP.
2. Le preload dans `backend/src/preload` expose uniquement les opérations autorisées par
   les canaux déclarés dans `backend/src/ipc/channels.ts`.
3. Le renderer React dans `frontend/src` n’accède directement ni à Node.js ni à SQLite.

Le processus principal conserve une session par fenêtre. Les identifiants d’auteur envoyés
par React ne sont jamais considérés comme une preuve d’identité : le backend utilise
l’utilisateur de la session et contrôle le rôle requis pour chaque canal. Un Manager peut
assurer les opérations quotidiennes et les restaurations pendant l’absence de l’Owner ; la
réinitialisation totale et la modification du compte Owner restent réservées à l’Owner.

## Session et changement d’utilisateur

Une fenêtre possède exactement une identité authentifiée. Cette identité est à la fois
l’identité de permission et l’acteur des opérations métier. Il n’existe plus d’élévation
temporaire, d’union de permissions ni d’expiration générale pour inactivité. Un redémarrage
complet ne restaure pas silencieusement la session en mémoire.

`switchUser` est une capacité IPC étroite : le backend lie l’utilisateur courant à la
requête, vérifie d’abord qu’il ne possède aucune caisse ouverte, authentifie la cible avec
la politique normale de verrouillage puis remplace atomiquement la session. Un échec laisse
la session courante intacte. Les permissions sont recalculées exclusivement pour la nouvelle
identité. `logout` applique le même blocage de caisse. Le panier et le checkout, qui sont des
états renderer non persistés, sont contrôlés avant l’ouverture du dialogue; le commit de vente
reste sérialisé et transactionnel côté backend.

## Frontend

- `components` contient les contrôles, fenêtres, navigation et composants réutilisables ;
- `pages/Auth` gère le démarrage, la connexion et le renouvellement de mot de passe ;
- `pages/Cashier` gère le catalogue, le panier, la vente et les factures ;
- `pages/Dashboard` contient Accueil, Stocks, Utilisateurs, Historiques, Rapports, Chat
  et Paramètres ;
- `hooks` porte les états et comportements réutilisables ;
- `services` sépare les appels IPC par domaine métier ;
- `types` centralise les contrats TypeScript ;
- les traductions FR/EN et le thème clair/sombre sont appliqués au niveau de l’application.

Les services frontend délèguent au pont exposé par le preload. Ils ne contiennent pas de
requête SQLite et ne doivent pas importer de module Node.js.

## Présence explicite

Authentification, présence et caisse sont trois états indépendants. Une connexion ou
déconnexion STORE ne crée ni ne ferme de présence; le démarrage, un changement de session
et la fermeture de l’application non plus. Une présence ouverte persiste jusqu’à une
sortie explicite.

Le pointage normal reçoit une personne, son mot de passe et l’action Entrée/Sortie. Le
backend vérifie que le mot de passe appartient exactement à cette personne, contrôle le
compte et le statut, applique la politique de verrouillage, fournit l’heure autoritative et
exécute la transition dans une transaction SQLite. L’acteur de la session qui facilite le
pointage est lié par le handler IPC et distingué du sujet dans l’audit. Aucun secret n’est
persisté. Les corrections historiques utilisent un canal séparé `PRESENCE:UPDATE`, une
raison et les champs de traçabilité existants.

## Backend

- `main` démarre Electron, crée les fenêtres et enregistre les handlers ;
- `ipc` définit la surface d’échange autorisée ;
- `database` contient le schéma, les migrations et les transactions atomiques ;
- `domain` contient les types, règles et validations testables sans Electron ;
- `services` regroupe les intégrations système, notamment PDF et SMTP.

Les ventes, mouvements de stock et suppressions associées doivent rester transactionnels
afin d’éviter une facture enregistrée sans mise à jour du stock.

## Traçabilité stock, achats et rapports

Les mouvements de stock conservent l’Article, la quantité signée, le type, une référence
source éventuelle, la valeur unitaire au moment de l’écriture et l’horodatage. Les types
`sale`, `purchase`, `purchase_cancellation` et `inventory` permettent de résoudre la
référence persistée vers la vente, l’achat ou l’inventaire correspondant. Cette navigation
ne repose sur aucune proximité d’heure ou de quantité.

La ligne de mouvement ne possède pas de clé d’acteur ni de quantités avant/après. Ces
informations ne sont donc pas reconstruites. L’acteur est affiché seulement dans les
entités qui le persistent directement, notamment création/validation/annulation d’achat
et inventaire. Les achats validés restent idempotents et produisent leurs mouvements dans
la transaction de validation existante.

Le reporting utilise des périodes inclusives et des requêtes paramétrées. Les données
potentiellement volumineuses sont bornées et triées de façon déterministe. STORE ne publie
ni marge/profit comptable ni valorisation monétaire du stock : aucune règle de coût
comptable (FIFO, LIFO ou coût moyen) n’est définie par le domaine.

## Persistance

La base SQLite est placée dans le dossier de données Electron de l’utilisateur. Elle
conserve notamment :

- utilisateurs, rôles, sécurité et présences ;
- produits, catégories, stocks et historique des prix ;
- ventes, lignes de vente et mouvements d’entrée/sortie ;
- messages, suppressions logiques et alertes ;
- paramètres de boutique, devise, remises et SMTP ;
- historique des e-mails de rapports.

Une photo principale facultative par article est référencée dans SQLite par un nom
géré par STORE. Les octets restent dans `userData/media/articles`; le renderer ne reçoit
aucun accès générique au système de fichiers. La sélection, la validation JPEG/PNG/WebP,
la limite de 5 Mo, la copie gérée et la lecture passent par des capacités IPC dédiées.

Les migrations du schéma sont appliquées par le backend. Une sauvegarde quotidienne est
créée avec une rétention de sept fichiers. Les nouvelles sauvegardes sont des bundles
autonomes `.store-backup` version 2 contenant manifeste, instantané SQLite et médias
référencés. Les anciennes sauvegardes `.db`/`.sqlite` restent restaurables. Toute
réinitialisation produit d’abord une
sauvegarde et exige une confirmation de l’Owner.

La restauration inspecte et valide le bundle dans un dossier temporaire confiné, contrôle
les empreintes SHA-256, l’intégrité SQLite, les clés étrangères, l’Owner actif et les
références média avant remplacement. Une copie de retour arrière DB+médias protège le
profil actif pendant le commit.

## Définition administrative des mots de passe

La fenêtre Utilisateurs propose un mode manuel et un mode automatique. Le mode automatique
réutilise le générateur cryptographique backend existant; le mode manuel transmet le secret
uniquement au canal protégé qui valide la politique puis calcule le hash. L’acteur provient
de la session backend, les comptes privilégiés ne peuvent pas être réinitialisés par un
niveau inférieur et le Primary Owner reste exclu de ce flux administratif.

## Flux SMTP

Le flux d’envoi est :

```text
Paramètres/Rapports React
        ↓ service frontend
Pont preload et canal IPC autorisé
        ↓
storeDatabase.ts (lecture de la configuration)
        ↓
emailService.ts / Nodemailer
        ↓ STARTTLS, TCP 587
Serveur SMTP du fournisseur
```

Pour un rapport, le backend génère le PDF, l’attache au message, effectue l’envoi, puis
écrit d’abord le message et sa pièce jointe dans `email_report_logs`. Un worker local
tente l’envoi, applique un délai progressif et conserve l’état entre les redémarrages.
Le test SMTP utilise l’adresse
e-mail de la boutique comme destinataire. Les alertes utilisent l’e-mail du compte Owner
lorsqu’il est disponible.

Les clés de configuration sont :

- `smtpHost` ;
- `smtpPort` (valeur par défaut `587`) ;
- `smtpUser` ;
- `smtpPassword` ;
- `smtpFrom` ;
- `smtpSecure` (exposé comme option TLS directe, habituellement pour le port 465).

La configuration prend en charge STARTTLS, habituellement sur le port 587, et TLS direct,
habituellement sur le port 465. Le port est validé entre 1 et 65535.

### Limite de sécurité connue

STORE chiffre le mot de passe SMTP avec le coffre-fort du système d’exploitation lorsqu’il
est disponible et ne le renvoie jamais au renderer. Sur un système ancien dépourvu de
coffre-fort, le fonctionnement hors ligne est conservé avec un stockage local de secours.
Il faut toujours employer une boîte dédiée et un mot de passe d’application révocable,
protéger le compte Windows et chiffrer l’emplacement des sauvegardes.

## Résilience locale

SQLite utilise WAL et `synchronous=FULL` afin de privilégier l’intégrité lors des coupures
d’électricité. Les sauvegardes sont écrites dans un fichier temporaire, contrôlées, puis
renommées atomiquement. Une restauration est vérifiée avant de remplacer la base active et
doit contenir un Owner actif. Le contrôle de sauvegarde est relancé chaque heure afin de
couvrir les postes qui restent ouverts plusieurs jours. Sept sauvegardes automatiques sont
conservées localement sans supprimer les sauvegardes manuelles. Avant remplacement ou
retour arrière, les fichiers secondaires WAL/SHM sont retirés pour ne pas réappliquer un
journal provenant d’une autre base.

## Tests et qualité

```bash
npm test
npm run lint
npm run format:check
npm run build
```

Les tests unitaires se trouvent dans `tests/unit/frontend` et `tests/unit/backend`. Les
évolutions de règles métier doivent être testées dans `domain` ou dans le service concerné,
sans dépendre d’une fenêtre Electron.

## Builds

- frontend : `dist/frontend` ;
- backend, preload et base : `dist/backend` ;
- installateur Windows : `release/STORE Setup 2.0.1-x64.exe` ou variante ARM64.

Commandes :

```bash
npm run package:win:x64
npm run package:win:arm64
```

Le frontend seul n’est pas une distribution fonctionnelle : l’application dépend du
backend Electron, de SQLite et du preload.
