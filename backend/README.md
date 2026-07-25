# Backend

Processus principal Electron, preload, IPC, persistance SQLite et intégrations système.
Les règles pures et validations résident dans `src/domain` ; les transactions restent
dans la couche `database` afin de préserver leur atomicité.

- `src/main` : cycle de vie Electron et handlers ;
- `src/preload` : API minimale exposée au renderer ;
- `src/ipc` : noms et contrats des canaux ;
- `src/database` : schéma, migrations, sauvegardes et transactions ;
- `src/domain` : règles testables sans Electron ;
- `src/services` : génération PDF et envoi SMTP.

Le renderer ne doit jamais importer ce dossier directement. Toute nouvelle opération
doit passer par un canal IPC explicite et validé.

La configuration SMTP détaillée et les limites de sécurité sont documentées dans
[`../docs/GUIDE_UTILISATEUR.md`](../docs/GUIDE_UTILISATEUR.md) et
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).
