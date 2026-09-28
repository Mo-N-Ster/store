# STORE 3.0 — S1 Security Hardening Report

## Changes
- Inspection ciblée uniquement ; aucune modification applicative. Version conservée : 2.0.1.
- Arrêt sur incompatibilité de baseline, avant implémentation et gate final.

## Permission contract
- Source : `permissionsForUser` / `permissionSnapshot`, relecture serveur à chaque IPC authentifié.
- Exposition : `session` authentifiée, DTO minimal et effectivePermissions ; méthode explicitement autorisée dans le preload.
- Consommation : `frontend/src/security/permissions.ts`, navigation et App ; pas de reconstruction des droits depuis le rôle. Le masquage Messages pour Employee reste une restriction UX existante, pas une autorisation.
- App attend les permissions avant montage métier ; actualisation sur focus, événement et intervalle de 10 secondes ; destination réévaluée.
- Élévation : absente volontairement depuis J.4. `sessionRemediationJ4.test.ts` exige l’absence de dropElevation, elevatedUntil et authorizedById. Réintroduire un cycle d’expiration/abandon serait une refonte interdite dans S1.

## IPC classification
- Registre existant : `backend/src/domain/rbac/ipcPermissions.ts`, exhaustif par `Record<IpcMethod, IpcPolicy>` et vérifications au démarrage du routeur.
- Gardés : PROTECTED, permission explicite.
- Généraux authentifiés : AUTHENTICATED, notamment session/switchUser/messagerie.
- Bootstrap/auth sans session : PUBLIC, catégorie distincte nécessaire.
- Internes : SYSTEM_INTERNAL refusé à la frontière ; méthodes inconnues refusées. Aucune méthode exposée non classifiée constatée dans les tests ciblés.

## Security tests / validation
- Tests ciblés : ipcPermissions, securityHardening, ipcRefreshJ6RA, sessionRemediationJ4, permissions et navigation ; rapport `artifacts/s1-targeted.json`.
- Tous les tests ciblés passent, aucun ignoré.
- git diff --check : PASS.
- npm test / lint / build / Electron : non relancés ; arrêt avant gate final, pas de nouveau résultat prétendu.

## Deviations / blockers
- Le contrat décrit Phase E / 90 tests, alors que le dépôt contient déjà les phases ultérieures et les remédiations RF. Il n’a pas été rétrogradé.
- Préserver une élévation temporaire et tester son expiration est incompatible avec le contrat J.4 courant qui l’interdit. Décision nécessaire : appliquer S1 à la session actuelle sans élévation, ou désigner la branche historique Phase E visée. Aucune réintroduction implicite.
- RF-004 reste ouvert, avec un reproducer en échec ; sa correction concerne le moteur de vente, explicitement hors S1. Une suite complète verte ne peut donc pas être annoncée.

## Protected areas unchanged
- Schéma, données personnelles, moteurs métier, idempotence, sauvegardes, SMTP, setup, rapports, version et précédentes remédiations inchangés pendant cette intervention.

## Next
NOT READY pour accepter S1 / autoriser Phase F sous ce contrat. Aucun nouveau travail Phase F engagé.
