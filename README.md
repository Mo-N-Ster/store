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

- `src/main` : démarrage Electron, création des fenêtres et handlers IPC.
- `src/preload` : pont sécurisé entre Electron et le renderer.
- `src/database` : schéma SQLite, accès aux données et règles métier.
- `src/ipc` et `src/shared` : canaux, types et constantes partagés.
- `src/renderer/components` : composants UI réutilisables.
- `src/renderer/pages` : modules Caisse, Dashboard et authentification.
- `src/renderer/hooks` : panier, produits, thème et notifications.
- `src/renderer/services` : accès centralisé à l’API Electron.
- `tests` : tests unitaires et, à terme, tests d’intégration.
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
