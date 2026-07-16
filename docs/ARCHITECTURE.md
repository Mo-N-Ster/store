# Architecture technique

STORE suit une architecture Electron en trois zones de confiance.

1. Le backend Electron, dans `backend/src`, possède les accès système, SQLite et les handlers IPC.
2. Le preload, dans `backend/src/preload`, expose uniquement les méthodes listées dans `backend/src/ipc/channels.ts`.
3. Le frontend React, dans `frontend/src`, ne possède aucun accès direct à Node.js ou SQLite.

## Renderer

- `components/Layout` et `components/UI` regroupent les briques réutilisables.
- `pages/Cashier` contient les cartes produits, le panier et les factures.
- `pages/Dashboard` sépare chaque domaine : produits, employés, ventes, graphiques, messagerie et paramètres.
- `hooks` contient les comportements avec état, notamment le panier et le chargement des produits.
- `services/api.ts` constitue l’unique point d’accès frontend au pont Electron.

## Base de données

`backend/src/database/storeDatabase.ts` conserve les transactions métier atomiques. Les validations et types purs sont rangés dans `backend/src/domain` afin d’être testés sans Electron ni SQLite.

## Builds

- Frontend : `dist/frontend`.
- Backend/preload/database : `dist/backend`.
- Installateur : `release/STORE Setup 1.0.0.exe`.
