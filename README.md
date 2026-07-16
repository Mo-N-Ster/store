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

- `backend/src/main` : démarrage Electron, fenêtres et handlers IPC.
- `backend/src/preload` : pont sécurisé vers le frontend.
- `backend/src/database` : schéma SQLite et transactions métier.
- `backend/src/domain` : types et validations testables par fonctionnalité.
- `frontend/src/components` : composants React réutilisables.
- `frontend/src/pages` : modules Caisse, Dashboard et authentification.
- `frontend/src/hooks` : panier, produits, thème et notifications.
- `frontend/src/services` : services IPC séparés par fonctionnalité.
- `tests/unit/frontend` et `tests/unit/backend` : tests par couche.
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
