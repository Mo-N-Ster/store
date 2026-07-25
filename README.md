# STORE

Application de bureau bilingue FR/EN pour la gestion d’une boutique d’épices et
d’arômes : caisse, stocks, utilisateurs, présences, historiques, rapports PDF, chat,
alertes, SMTP et sauvegardes.

## Démarrage

Prérequis : Node.js 20+ et npm.

```bash
npm install
npm run dev
```

Au premier lancement, l’assistant demande la création du compte Owner. Aucune identité
par défaut n’est conservée en production.

## Fonctions principales

- caisse avec recherche, sélection des lignes, contrôle du stock et facture ;
- stocks, catégories, mouvements, seuils et historique des prix ;
- utilisateurs Employee, Manager et Owner, présences et récupération sécurisée ;
- historiques ventes, achats et personnel avec sélection et export PDF ;
- rapports filtrés, graphiques, PDF et envoi par e-mail ;
- chat local, alertes de stock et historique des e-mails ;
- devise, remises, thème clair/sombre et traductions FR/EN ;
- base SQLite locale et sauvegardes quotidiennes.

## Architecture

- `backend/src/main` : démarrage Electron, fenêtres et handlers IPC ;
- `backend/src/preload` : pont sécurisé vers le frontend ;
- `backend/src/database` : schéma SQLite, migrations et transactions ;
- `backend/src/domain` : règles et validations testables ;
- `backend/src/services` : intégrations PDF et SMTP ;
- `frontend/src/components` : composants React réutilisables ;
- `frontend/src/pages` : authentification, Caisse et Dashboard ;
- `frontend/src/hooks` : comportements avec état ;
- `frontend/src/services` : appels IPC séparés par fonctionnalité ;
- `tests/unit/frontend` et `tests/unit/backend` : tests par couche.

## Qualité

```bash
npm test
npm run lint
npm run format:check
npm run build
```

## Distribution Windows

```bash
npm run package:win:x64
npm run package:win:arm64
```

Les installateurs sont générés dans `release/`. L’exécutable Windows convient aussi aux
tablettes Windows de l’architecture correspondante, mais pas à Android ou iPadOS.

## Documentation

- [Guide utilisateur et configuration SMTP](docs/GUIDE_UTILISATEUR.md)
- [Architecture technique](docs/ARCHITECTURE.md)
- [Backend](backend/README.md)
- [Frontend](frontend/README.md)

Le guide SMTP contient un exemple Gmail, un exemple Microsoft 365, la procédure de test,
les erreurs fréquentes et les précautions relatives au mot de passe d’application.
