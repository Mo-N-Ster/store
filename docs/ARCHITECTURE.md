# Architecture technique

STORE suit une architecture Electron en trois zones de confiance.

1. Le processus principal, dans `src/main`, possède les accès système et enregistre les handlers IPC.
2. Le preload, dans `src/preload`, expose uniquement les méthodes listées dans `src/ipc/channels.ts`.
3. Le renderer React, dans `src/renderer`, ne possède aucun accès direct à Node.js ou SQLite.

## Renderer

- `components/Layout` et `components/UI` regroupent les briques réutilisables.
- `pages/Cashier` contient les cartes produits, le panier et les factures.
- `pages/Dashboard` sépare chaque domaine : produits, employés, ventes, graphiques, messagerie et paramètres.
- `hooks` contient les comportements avec état, notamment le panier et le chargement des produits.
- `services/api.ts` constitue l’unique point d’accès frontend au pont Electron.

## Base de données

`src/database/storeDatabase.ts` conserve les transactions métier atomiques. Son découpage futur en repositories devra préserver les transactions de vente, de stock et de réinitialisation ; créer un modèle par table sans besoin concret ajouterait une abstraction sans bénéfice.

## Builds

- Renderer : `dist/renderer`.
- Main/preload/database : `dist/main`.
- Installateur : `release/STORE Setup 1.0.0.exe`.
