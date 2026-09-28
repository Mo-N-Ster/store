# STORE 3.0 — Rapport d’implémentation Phase J.2

## 1. Executive summary

J.2 ajoute une photo principale facultative par article, un bundle autonome `.store-backup` DB+médias et un choix administratif Manuel/Automatique pour les mots de passe. L’architecture reste locale, deny-by-default et sans accès filesystem générique au renderer.

## 2. Pre-change baseline

Version `2.0.1`, branche `main`, commit de référence `d46c56c8af570fe96f9aba875726c1ca2c5abd53`. Baseline J.1 validée : 29 fichiers, 143/143 tests, lint/build/diff/audit/smoke PASS.

## 3. Audit findings

Avant J.2 : `products` n’avait aucun média; CRUD synchrone et suppression logique; imports PDF/CSV sans image; preload strict par allowlist; picker générique authentifié existant; données sous `app.getPath('userData')`; sauvegardes SQLite `.db`; restauration avec validation intégrité/tables/Owner et retour arrière DB; migrations idempotentes jusqu’à 13; générateur `randomBytes(9).toString('base64url')`; hash bcrypt; réinitialisation employé protégée; récupération Manager/Owner par réponse; permissions `EMPLOYEES:UPDATE`; Primary Owner protégé contre désactivation/rétrogradation.

## 4. Files changed

Backend : schéma, migrations, storeDatabase, canaux, registre RBAC, handlers, preload, erreurs, types Product; nouveaux modules `articleMedia.ts`, `backupBundle.ts`, `adminPasswordPolicy.ts`. Frontend : types/services Article et Employé, listes/détails/POS Article, dialogue mot de passe, Paramètres, traductions et styles; nouveau `ArticleImage.tsx`. Documentation : Architecture, Guide et ce rapport. Tests : migrations et administration mis à jour; cinq ensembles J.2 ajoutés/complétés.

## 5. Schema change

`products.image_ref TEXT NULL` est ajouté. Aucun BLOB, contrainte obligatoire ou réécriture historique.

## 6. Migration design

Migration 14 `article-primary-image`, additive et idempotente via inspection `PRAGMA table_info`. Base neuve et profil historique convergent vers la même colonne nullable.

## 7. Media architecture

Une image maximum. Le picker dédié lit et valide le fichier dans le main process, crée un token UUID, copie en staging géré, puis `saveProduct` finalise la référence. Aucun chemin externe n’est persisté.

## 8. Media storage location strategy

`app.getPath('userData')/media/articles`. Staging sous `.staging`; nettoyage des fichiers abandonnés après 24 h. Aucun chemin utilisateur n’est codé en dur.

## 9. File validation

JPEG, PNG et WebP sont reconnus par signature binaire; limite 5 MiB. Cette limite couvre les photos usuelles tout en bornant mémoire, IPC et sauvegardes. Noms finaux UUID, référence basename stricte, aucune traversée.

## 10. Article create flow

Choisir, prévisualiser, retirer avant enregistrement ou créer sans image. La sélection reste optionnelle.

## 11. Article edit flow

Conserver implicitement l’image existante, la remplacer par une nouvelle sélection ou demander explicitement son retrait.

## 12. Article image lifecycle

Sélection externe → validation → staging géré → copie finale → transaction Article → suppression staging → nettoyage de l’ancienne image uniquement si plus aucune ligne, y compris archivée, ne la référence.

## 13. Failure/rollback behavior

L’ancienne image n’est supprimée qu’après commit DB. Si la mutation DB échoue, la nouvelle copie finale est supprimée et le staging contrôlé demeure temporairement réutilisable/nettoyable. Image manquante ou corrompue retourne `null` et affiche le placeholder.

## 14. Backup bundle architecture

Un fichier JSON binaire-safe `.store-backup` contient logiquement `manifest.json`, `store.sqlite` et `media/articles/*`; les fichiers sont encodés base64 dans l’artefact unique. Aucune image n’entre dans SQLite.

## 15. Manifest format

Format, version, version application, date ISO, nature du backup, entrée DB et inventaire média avec chemin, référence, taille et SHA-256. Aucun mot de passe ou secret SMTP dans le manifeste.

## 16. Backup-format version

`backupFormatVersion: 2`, indépendant de la version applicative.

## 17. Path-traversal protection

Rejet de `..`, chemins absolus, antislashs, préfixes de lecteur, normalisation divergente, entrées inattendues et destination hors staging. Le format ne porte ni lien ni symlink.

## 18. Restore staging

Sélection → inspection → validation manifest/hashes → extraction confinée → validation SQLite/médias → backup pré-restauration → snapshot rollback DB+médias → commit → migration et revérification → nettoyage.

## 19. Legacy backup compatibility

`.db` et `.sqlite` restent acceptés et validés. Ils sont migrés vers la colonne nullable et restaurent les articles sans photo, donc avec placeholder.

## 20. Backup/restore round-trip evidence

Test sous un profil créé par `mkdtemp` dans `os.tmpdir`, explicitement distinct d’`APPDATA` : Article A avec JPEG, Article B sans image, stock métier, backup, mutation DB et suppression image, staging/restauration, comparaison octets, données, placeholder, intégrité et clés étrangères.

## 21. Password automatic flow

Le canal `generateUserPassword` réutilise exclusivement `temporaryPassword()` backend fondé sur `randomBytes`, hash bcrypt, déverrouille le compte, audite acteur/cible et retourne le secret temporaire pour affichage 60 secondes.

## 22. Password manual flow

Choix Manuel, saisie et confirmation UI, canal dédié `setUserPassword`, validation backend, bcrypt et audit sans secret.

## 23. Password policy enforcement

Minimum backend existant de 8 caractères, complété par `minLength` informatif frontend. La confirmation n’est jamais transmise ni persistée.

## 24. Primary Owner protection

Le flux administratif refuse toujours une cible Owner. Un Manager ne peut modifier qu’un Employee; un Owner peut modifier Manager ou Employee. Le flux de récupération Owner existant reste distinct.

## 25. IPC changes

Ajouts : `selectArticleImage`, `articleImage`, `generateUserPassword`, `setUserPassword`. Chaque opération est sémantique et étroite.

## 26. Preload changes

Quatre noms ajoutés à l’allowlist explicite. Aucun `fs`, `path`, Node brut, `ipcRenderer` brut ou `invoke(channel)` générique exposé.

## 27. Authorization-registry changes

Médias : `PRODUCTS:READ/UPDATE`. Mots de passe : `EMPLOYEES:UPDATE`. L’acteur et le niveau effectif proviennent de la session backend; en élévation, l’auteur autorisant est attribué.

## 28. Security tests

Registre complet, inconnu refusé, preload strict, permissions, acteur backend, chemins hostiles, hash/intégrité et absence de stockage navigateur couverts.

## 29. Migration tests

Base neuve, application répétée, profil 2.0.1 représentatif, conservation Article/données et `image_ref=NULL` validés.

## 30. Media tests

JPEG/PNG/WebP, type invalide, taille excessive, noms sûrs, traversée, staging/commit, fichier manquant et compensation structurelle couverts.

## 31. Backup tests

Bundle DB-only, bundle DB+média, manifest versionné, inventaire, tailles/hashes, moteur commun manuel/auto/pré-restore/pré-reset couverts.

## 32. Restore tests

Staging nouveau format, DB et image, legacy via couverture historique SQLite, manifest altéré, média manquant, chemins hostiles, DB corrompue et conservation sûre pré-commit couverts.

## 33. Password tests

Autorisation Owner/Manager/Employee, cible Owner, canaux protégés, confirmation, générateur backend, absence de stockage navigateur et attribution acteur couverts.

## 34. Historical regression

**33 fichiers, 159/159 tests PASS, 0 skipped**, contre 29/143 en J.1.

## 35. SQLite integrity

`PRAGMA integrity_check = ok` sur migration et round-trip.

## 36. Foreign-key integrity

`PRAGMA foreign_key_check = []` sur migration, staging et round-trip.

## 37. Upgrade validation

Le fixture 2.0.1 conserve utilisateurs, articles, stock, factures, messages, présences et ajoute seulement `image_ref=NULL`; migration appliquée une fois.

## 38. Test-profile isolation evidence

Tous les tests de mutation/restauration emploient un chemin `os.tmpdir()/store-j2-*`; assertion explicite qu’il n’est pas `APPDATA`; suppression après test. Aucun restore n’a ciblé le profil STORE réel.

## 39. Dependency changes

Aucune. Le runtime Node/Electron, `crypto`, `fs`, SQLite et JSON suffisent; une bibliothèque d’archive ou d’image aurait augmenté surface et poids sans nécessité.

## 40. Production dependency audit

`npm audit --omit=dev` : **0 vulnérabilité**.

## 41. ESLint

PASS, zéro avertissement.

## 42. TypeScript/Vite

PASS; 1984 modules transformés.

## 43. git diff --check

PASS.

## 44. Electron smoke test

PASS après neutralisation locale de la variable d’infrastructure `ELECTRON_RUN_AS_NODE=1`; processus stable durant l’observation, arrêt propre.

## 45. Responsive status

CSS structurel couvert pour 1280×800, 1024×768, 800×1280, 1366×768 et 1920×1080 : miniatures bornées, formulaire replié sous 56.25rem, modes mot de passe empilés sous 35rem. Validation visuelle humaine requise.

## 46. Accessibility status

Alt significatif pour vraie photo; placeholder décoratif masqué; boutons nommés; radios groupés par fieldset/legend; labels secrets; erreurs `role=alert`; focus et modal du design system; cibles tactiles; contraste/mouvement existants préservés.

## 47. Visual validation status

**AUTOMATED VISUAL VALIDATION: NOT OBSERVABLE** (`apps: []`, `browsers: []`).

## 48. Human visual checklist

- Créer sans photo puis avec JPEG/PNG/WebP; annuler une sélection.
- Modifier : conserver, remplacer et retirer; vérifier placeholder si fichier absent.
- Contrôler miniatures Catalogue/POS et fiche Article aux cinq tailles cibles.
- Tester clair, sombre, contraste renforcé, clavier, focus et tactile.
- Ouvrir Nouveau mot de passe : radios Manuel/Automatique, confirmation divergente, affichage 60 s, copie uniquement sur clic.
- Vérifier refus Manager→Manager et refus de l’Owner comme cible.
- Créer/exporter un `.store-backup`, le copier hors poste et restaurer sur un profil de test.

## 49. Known limitations

Une image seulement, aucune galerie/caméra/cloud/redimension automatique. Validation de contenu par signatures robustes mais sans décodage pixel complet. Bundle chargé en mémoire et borné à 512 MiB; adapté au périmètre local actuel, pas à une médiathèque massive.

## 50. Deferred requirements

J.3 présence explicite; J.4 sessions/utilisateurs/élévation/caisse; J.5 stock/achats/rapports. Aucun de ces moteurs n’a été modifié.

## 51. Version confirmation

Version maintenue à **2.0.1**. Aucun installateur, publication ou release créé.

## 52. Readiness for J.3

**J.2 COMPLETE — AUTOMATED VALIDATION PASSED, VISUAL REVIEW REQUIRED**

J.2 est prêt pour revue humaine. Arrêt avant J.3.
