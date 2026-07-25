# Architecture technique

STORE est une application Electron, React et TypeScript reposant sur SQLite. Elle sépare
le code en trois zones de confiance.

1. Le processus principal Electron dans `backend/src` possède les accès système, SQLite,
   PDF et SMTP.
2. Le preload dans `backend/src/preload` expose uniquement les opérations autorisées par
   les canaux déclarés dans `backend/src/ipc/channels.ts`.
3. Le renderer React dans `frontend/src` n’accède directement ni à Node.js ni à SQLite.

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

## Backend

- `main` démarre Electron, crée les fenêtres et enregistre les handlers ;
- `ipc` définit la surface d’échange autorisée ;
- `database` contient le schéma, les migrations et les transactions atomiques ;
- `domain` contient les types, règles et validations testables sans Electron ;
- `services` regroupe les intégrations système, notamment PDF et SMTP.

Les ventes, mouvements de stock et suppressions associées doivent rester transactionnels
afin d’éviter une facture enregistrée sans mise à jour du stock.

## Persistance

La base SQLite est placée dans le dossier de données Electron de l’utilisateur. Elle
conserve notamment :

- utilisateurs, rôles, sécurité et présences ;
- produits, catégories, stocks et historique des prix ;
- ventes, lignes de vente et mouvements d’entrée/sortie ;
- messages, suppressions logiques et alertes ;
- paramètres de boutique, devise, remises et SMTP ;
- historique des e-mails de rapports.

Les migrations du schéma sont appliquées par le backend. Une sauvegarde quotidienne est
créée avec une rétention de sept fichiers. Toute réinitialisation produit d’abord une
sauvegarde et exige une confirmation de l’Owner.

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
écrit le succès dans l’historique `email_report_logs`. Le test SMTP utilise l’adresse
e-mail de la boutique comme destinataire. Les alertes utilisent l’e-mail du compte Owner
lorsqu’il est disponible.

Les clés de configuration sont :

- `smtpHost` ;
- `smtpPort` (valeur par défaut `587`) ;
- `smtpUser` ;
- `smtpPassword` ;
- `smtpFrom` ;
- `smtpSecure` (actuellement non exposé dans l’interface et donc désactivé).

La configuration actuelle prend en charge STARTTLS sur le port 587. Le port 465 exige
`smtpSecure=true` et ne doit pas être proposé tant que ce choix n’est pas exposé et testé
dans l’interface.

### Limite de sécurité connue

Le mot de passe SMTP est stocké dans les paramètres de la base locale. Il faut employer
une boîte dédiée et un mot de passe d’application révocable, protéger le compte Windows
et chiffrer l’emplacement des sauvegardes. Une évolution recommandée consiste à stocker
ce secret dans le gestionnaire d’identifiants du système d’exploitation.

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
- installateur Windows : `release/STORE Setup 1.0.0-x64.exe` ou variante ARM64.

Commandes :

```bash
npm run package:win:x64
npm run package:win:arm64
```

Le frontend seul n’est pas une distribution fonctionnelle : l’application dépend du
backend Electron, de SQLite et du preload.
