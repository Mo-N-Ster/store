# Correctifs de saisie, statuts et candidat Windows — 2026-09-26

## Périmètre et résultat

Numéro technique conservé à 2.0.1. L'installateur produit est un **candidat de test**, pas une certification de production. Aucune installation sur les données personnelles ni publication automatique.

### Saisie et présence

- Le focus initial des dialogues respecte le champ déjà actif et le champ explicitement demandé ; le bouton Fermer ne vole plus le focus au mot de passe.
- Un changement d'état occupé ne réexécute plus l'initialisation du focus. Les callbacks de fermeture restent actualisés et les animations différées sont annulées au démontage.
- Le bouton œil respecte l'état désactivé et ne retire pas le focus lors d'un clic souris.
- Le formulaire de signature désactive le mot de passe et Annuler pendant la requête, redonne le focus après refus et distingue erreur d'authentification et autre échec.
- Contrôle des composants partagés TextInput, NumberInput, Select, Checkbox, textarea et PasswordInput. Aucune affirmation de validation manuelle exhaustive de chaque champ de chaque écran.

### Statuts et mouvements

`frontend/src/utils/entityLabels.ts` centralise les traductions de présentation : normalisation de casse, vocabulaire borné des mouvements, statut inconnu explicite. Les valeurs persistées restent inchangées.

Les achats, stocks, rapports et historique des ventes utilisent ces libellés. Les présences terminées, en cours, corrigées et interrompues restent distinctes. Un statut inconnu n'est plus assimilé à un brouillon ou à une annulation.

### Fermeture technique des risques de maintenance identifiés

- Reset : sauvegarde préalable existante conservée ; déplacement réversible des images avant suppression SQL transactionnelle ; marqueur de commit dans settings ; journal filesystem persistant ; reprise au démarrage. Une erreur de nettoyage après commit ne transforme pas un reset effectif en faux échec.
- Restore : migrations avant création des index du schéma courant ; instantané de rollback complet avant remplacement ; journal persistant avant mutation ; reprise DB et médias avant ouverture SQLite au démarrage. En cas de rollback incomplet, instantané et journal sont conservés au lieu d'être supprimés en finally.
- Les opérations destructives simultanées sont refusées. Les erreurs filesystem persistantes peuvent toujours empêcher le démarrage jusqu'à réparation des permissions/espace disque ; le journal n'est pas supprimé dans cet état.
- Ces corrections et leurs tests remplacent les constats techniques antérieurs sur ces chemins, mais ne qualifient pas à elles seules tous les scénarios physiques de panne disque/coupure.

## Preuves automatisées

- Suite complète : 251 tests, aucun échec/ignoré ; `artifacts/input-status-tests.json`.
- Lint, TypeScript et build de production Vite exécutés.
- Banc Electron : vrai composant PresencePage et vrais composants React, API de présence simulée, profil temporaire. Saisie continue, œil, mauvais mot de passe puis réussite, textes/nombres/textarea/select/checkbox/champ désactivé, blocage des flèches numériques.
- Tests réels storeDatabase sur SQLite/profil jetable : persistance paramètres/relecture, absence de secret SMTP dans paramètres/diagnostics, bundle restore, v15 DB-only restore, corruption rejetée, panne de remplacement et rollback, reset refusé puis réussi avec sauvegarde.
- Tests de journaux : rollback SQL/media, échec de déplacement, interruption pré-commit, nettoyage différé et reprise, échec de copie rollback et nouvelle tentative, rejet de traversée de chemins.
- `npm start` sur profil isolé avec ELECTRON_RUN_AS_NODE initialement défini : lancement et sortie smoke réussis ; données personnelles non utilisées.

## Limites et validation humaine

Les alertes d'outillage de l'audit précédent et les autres points J.6R-A non concernés restent à traiter. Le prétest standard npm test reste distinct de la suite exécutée avec binding SQLite Node isolé, pour ne pas remplacer le module verrouillé par STORE ouvert.

À tester manuellement sur le candidat : entrée/sortie du vrai compte de test, clavier tactile, collage/accents, œil, nouvelle tentative, navigation, français/anglais et thèmes ; restauration et reset exclusivement sur profil jetable sauvegardé. Aucun PASS visuel ni compatibilité de tous les dispositifs affirmé.

## Reproduction

```text
npx vite build --config scripts/input-smoke/vite.config.ts
electron scripts/input-smoke/run.mjs   (ELECTRON_RUN_AS_NODE absent)
npx vitest run --config vitest.j6ra.config.ts
npm run lint
npm run build
```

L'assemblage utilise le module SQLite Electron préalablement vérifié, sans reconstruire celui que l'instance personnelle tient ouvert. La vérification de paquet accepte un dossier explicite : `node scripts/verify-package.mjs artifacts/release-candidate`.

## Livraison du candidat

- Installateur Windows x64 : `artifacts/release-candidate/STORE Setup 2.0.1-x64.exe`.
- Vérification du paquet réussie ; storeDatabase, journaux de reset/restore et index HTML embarqués comparés octet par octet avec le build courant.
- `node scripts/check-packaged-native.mjs` : SQLite réellement extrait du paquet charge sous Electron ABI 148 ; integrity_check en mémoire = ok.
- Le paquet n'a pas été installé ni démarré sur les données personnelles. Sauvegarder/exporter les données avant tout remplacement manuel d'une installation existante.
