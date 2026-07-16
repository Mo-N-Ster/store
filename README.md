# STORE

Application de bureau hors ligne pour la gestion d’une boutique d’épices et d’arômes : caisse, stock, employés, ventes, présences, messagerie, statistiques et sauvegardes.

## Démarrage

Prérequis : Node.js 20+ et npm.

```bash
npm install
npm run dev
```

Au premier lancement, l’assistant demande la création du compte propriétaire. Aucune identité par défaut n’est conservée en production.

## Architecture

- `backend/database` : schéma SQLite, accès aux données et règles métier.
- `backend/electron` : processus principal Electron et pont IPC sécurisé.
- `frontend/src/components` : composants UI réutilisables.
- `frontend/src/pages` : écrans fonctionnels.
- `frontend/src/hooks` : état et comportements React réutilisables.
- `frontend/src/services` : accès centralisé à l’API Electron.
- Base locale dans le dossier de données de l’application Electron.
- Sauvegardes quotidiennes avec rétention de sept jours.

## Qualité et distribution

```bash
npm test
npm run build
npm run package
```

Le packaging produit un installateur Windows NSIS ou une image macOS DMG dans `release/`.

Le guide d’utilisation se trouve dans [`docs/GUIDE_UTILISATEUR.md`](docs/GUIDE_UTILISATEUR.md).
