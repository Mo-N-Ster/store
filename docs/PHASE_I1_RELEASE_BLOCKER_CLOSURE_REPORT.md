# STORE 3.0 — PHASE I.1 RELEASE BLOCKER CLOSURE REPORT

Date de clôture technique : 25 septembre 2026
Version maintenue : **2.0.1**
Branche / commit de référence : `main` / `d46c56c8af570fe96f9aba875726c1ca2c5abd53`

## 1. Executive summary

Phase I.1 ferme le bloqueur de sécurité Nodemailer et démontre la préservation des données d'une fixture représentative lors du démarrage du candidat empaqueté. La régression automatisée, la compilation, le paquet Windows et son démarrage isolé passent. La release reste néanmoins interdite : la validation visuelle réelle, le cycle installateur complet, le parcours E2E empaqueté et la signature Windows ne sont pas démontrés.

## 2. Phase I baseline

Phase I avait livré 27 fichiers de tests / 134 tests, un installateur x64, un démarrage empaqueté isolé et les audits de résilience. Elle avait laissé RB-01 à RB-06 ouverts et qualifié le Setup en deux commits de dégradé mais récupérable.

## 3. Repository/environment baseline

- Windows NT `10.0.26200`, x64 ; Node `22.17.0` ; npm `11.5.2` ; Electron `43.1.1`.
- Version package : `2.0.1`, inchangée.
- Worktree préexistant et volontairement non nettoyé : 56 entrées modifiées, 1 supprimée, 74 non suivies au relevé final.
- Aucun reset, checkout destructif ou nettoyage global n'a été exécuté.

## 4. Test isolation policy

Tous les lancements affectant les données ont employé un profil contenant explicitement `store-phase-i1` :

- fresh : `C:\Users\sterl\AppData\Local\Temp\store-phase-i1-fresh-profile` ;
- upgrade : `C:\Users\sterl\AppData\Local\Temp\store-phase-i1-upgrade-profile` ;
- build : `C:\Users\sterl\AppData\Local\Temp\store-phase-i1-933a612b47a740e1877bf7855ef484f2`.

Chaque lancement applicatif a reçu `--user-data-dir=<profil explicite>`. Le profil réel `%APPDATA%\store-desktop` n'a jamais été utilisé pour un test I.1.

## 5. Real-profile incident follow-up

Le backup réel supplémentaire de Phase I est toujours présent et n'a pas été supprimé ni modifié par Phase I.1. Le plus récent relevé avant I.1 reste `auto-store-2026-09-24T13-22-07-552Z-d1359c.db` (323 584 octets, 24/09/2026 15:22:07). Aucun fichier du répertoire réel n'a reçu une date du 25/09/2026 lors de cette phase.

## 6. Blocker inventory

État initial : RB-01 à RB-06 ouverts. Après revalidation : RB-04 est fermé ; RB-01, RB-02, RB-03, RB-05 et RB-06 restent ouverts. Aucun bloqueur n'a été reclassé silencieusement.

## 7. Nodemailer advisory verification

Version initiale réellement installée : `nodemailer@9.0.3`, dépendance directe de production. L'audit en ligne a confirmé :

- `GHSA-8m3c-c648-2xjj`, plage `<=9.1.0` ;
- `GHSA-wmmp-3585-3rmp`, plage `<9.1.0` ;
- `GHSA-2x7j-588g-ccc2`, haute, plage `<9.1.0` ;
- `GHSA-cc9r-2j5m-2m83`, plage `>=6.9.16 <9.1.0`.

Première version corrigée conservant la majeure : `9.1.1`.

## 8. Nodemailer reachability

Nodemailer est atteignable dans le runtime via `backend/src/services/emailService.ts`, appelé par la file de rapports e-mail. Il crée le transport SMTP, utilise les identifiants chiffrés/déchiffrés côté backend, construit destinataire/sujet/texte et joint les PDF en `Buffer`. La file applique sa stratégie d'échec/retry. Ce n'était donc pas une dépendance morte ou seulement de développement.

## 9. Dependency remediation

- FROM : `^9.0.3` / lock `9.0.3`.
- TO : version exacte `9.1.1` / lock `9.1.1`.
- Rupture majeure : aucune.
- API STORE affectée : aucune modification d'appel requise.
- Durcissement ajouté : `disableFileAccess: true` et `disableUrlAccess: true` ; les pièces jointes STORE restent des buffers mémoire.
- Aucun `npm audit fix --force`.
- Audit production final : 0 vulnérabilité. L'installation propre a encore signalé 13 avis de développement/transitifs (1 low, 3 moderate, 9 high), hors graphe expédié de production ; ils restent une dette d'outillage.

## 10. SMTP regression

Deux tests dédiés vérifient le transport configuré, la pièce jointe en mémoire, les deux interdictions d'accès et l'échec fermé `SMTP_NOT_CONFIGURED`. Aucun serveur, identifiant ou destinataire réel n'a été utilisé. La politique de retry existante reste couverte.

## 11. Representative 2.0.1 fixture

Le script `scripts/phase-i1-upgrade-fixture.mjs` crée sans écrasement une base héritée déterministe : Owner, Manager, Employee, produit, stock, mouvement, facture, ligne, présence, réglages et message. Les modules fournisseurs/achats/inventaires, absents de ce schéma hérité, sont créés vides par les migrations. La fixture est synthétique et ne contient aucune donnée utilisateur réelle.

## 12. Pre-upgrade snapshot

Valeurs clés : produit `Poivre I1`, stock `37`, prix `1 250`, facture `INV-I1-0001`, total `2 500`, présence 08:00–17:00, devise `XOF`, magasin `STORE I1 Fixture`. Le snapshot JSON et `representative-2.0.1-pre-upgrade.db` ont été créés avant le lancement.

## 13. Upgrade execution

Le véritable `STORE.exe` du candidat `win-unpacked` a été lancé avec le profil fixture. Le chemin normal `initDatabase → backup pré-migration → migrations → schéma courant` a appliqué les migrations 1 à 13. Il s'agit du mécanisme applicatif réel, mais pas d'une installation NSIS par-dessus l'installation réelle.

## 14. Post-upgrade integrity

`PRAGMA integrity_check` = `ok` ; `foreign_key_check` = liste vide ; 13 migrations enregistrées. L'application est restée active et répondante jusqu'à l'arrêt contrôlé de ses quatre processus issus du seul exécutable temporaire.

## 15. Historical-data preservation

Comparaison sérialisée avant/après : `preserved=true`. Les trois comptes, le produit/stock, le mouvement, la facture/ligne, la présence, le message et les réglages sont identiques. Les rôles effectifs sont `ADMIN`, `MANAGER`, `CASHIER`.

## 16. Post-upgrade business smoke

La lecture métier et les droits migrés sont vérifiés. En revanche, login réel, nouvelle vente, facture, effet de stock, rapport et backup manuel via l'interface n'ont pas été observés. RB-02 reste donc **OPEN**, malgré une preuve forte de migration des données.

## 17. Installer installation

Non exécutée : le registre confirme une installation réelle `STORE 2.0.1` et un désinstalleur sous `C:\Users\sterl\AppData\Local\Programs\STORE`. Lancer le candidat NSIS sur le même compte aurait pu remplacer l'application en service et violer l'isolation.

## 18. First packaged launch

PASS pour l'exécutable décompressé : démarrage sans Node, npm, Vite, dépôt ou VS Code ; profil fresh créé ; base SQLite, backups et caches présents ; processus répondant.

## 19. Uninstall behavior

NOT TESTED. Aucun désinstallateur réel n'a été lancé. La conservation/suppression exacte des données ne peut pas être déduite du script NSIS seul.

## 20. User-data retention

NOT OBSERVED pour un vrai uninstall. Le test décompressé prouve seulement que les données résident hors du dossier de programme, dans le `user-data-dir` choisi.

## 21. Reinstall

NOT TESTED sur ce compte, pour la même contrainte de protection de l'installation réelle.

## 22. Reinstall data validation

NOT OBSERVED. Requiert un utilisateur Windows jetable ou une VM/sandbox Windows avec aucun STORE réel.

## 23. Packaged Setup

Le profil fresh prouve la création backend et l'état `needs setup` (aucun Owner). L'assistant graphique n'a pas été observé ni complété.

## 24. Packaged Login

NOT OBSERVED dans l'interface. Les hashes et rôles migrés sont valides structurellement, sans preuve de saisie/login empaqueté.

## 25. Packaged POS

NOT OBSERVED.

## 26. Packaged sale

NOT OBSERVED ; aucun stock de la fixture n'a été altéré par une écriture directe destinée à simuler une vente.

## 27. Offline packaged operation

Le démarrage fresh et l'upgrade ont réussi sans service distant ni SMTP. Cela confirme le bootstrap local offline, pas le parcours POS offline complet.

## 28. Visual-validation methodology

Le skill Windows Computer Use a été initialisé selon son protocole. L'inventaire a retourné `apps: []`, `browsers: []`. Aucun screenshot, clic ou résultat visuel n'a été inventé. La checklist humaine de Phase I reste la procédure de référence.

## 29. Observation source

- CODEX-OBSERVED : aucun écran, faute de surface exposée.
- USER-OBSERVED : aucune nouvelle preuve fournie durant I.1.
- PROCESS/FILESYSTEM-OBSERVED : démarrage, processus, bases, package et signatures.

## 30. Visual results

Tous les écrans demandés restent **NOT OBSERVED** : Setup, Login, Shell, POS, Products, Stock, Inventories, Purchases, Team, Presence, Dashboard, Reports, Administration, Users, Settings, Backups, Diagnostics, Danger Zone, ainsi que loading/empty/error/forbidden/modal/drawer/confirmation/success.

## 31. Resolution matrix

| Résolution | Résultat |
| --- | --- |
| 1920×1080 | NOT OBSERVED |
| 1366×768 | NOT OBSERVED |
| 1280×800 | NOT OBSERVED — critique |
| 1024×768 | NOT OBSERVED — critique |
| 800×1280 | NOT OBSERVED — critique |

## 32. Theme/accessibility observations

Light, dark, high contrast et reduced motion : **NOT OBSERVED** sur le paquet réel. Les tests source ne remplacent pas cette validation.

## 33. Code-signing audit

`electron-builder.yml` ne contient aucune configuration `win.sign` et aucun secret de signature n'a été fourni. `Get-AuthenticodeSignature` retourne `NotSigned` pour l'installateur et `STORE.exe`.

## 34. Signing requirements

Options valides : certificat Authenticode OV/EV en `.pfx/.p12` ou magasin Windows via `signtool`, HSM, ou Azure Artifact/Trusted Signing. Electron-builder accepte `WIN_CSC_LINK`/`WIN_CSC_KEY_PASSWORD` (fallback `CSC_LINK`/`CSC_KEY_PASSWORD`) et le timestamp RFC 3161. Certificat, clé, mot de passe ou token doivent rester dans un coffre CI/variables secrètes, jamais dans Git. Références : [electron-builder Windows signing](https://www.electron.build/docs/features/code-signing/code-signing-win/) et [Microsoft SmartScreen reputation](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation).

## 35. Signing classification

**RELEASE BLOCKER** par défaut pour une distribution Windows publique : l'absence de signature expose à “Windows protected your PC” et peut être bloquée par des politiques d'entreprise/Smart App Control. Seul le responsable humain peut accepter explicitement une distribution privée non signée ; aucune acceptation n'a été donnée.

## 36. Two-commit Setup reproduction

Le test isolé crée l'Owner puis simule l'absence du commit de préférences. Au redémarrage : un Owner actif, aucun `storeName`, base intègre.

## 37. Restart result

Le backend considère le Setup terminé dès qu'un Owner actif existe. Le wizard ne reprend pas ; le login devient la route attendue. Les préférences utilisent alors leurs valeurs par défaut.

## 38. Manual recovery

Après login Owner, Administration/Settings permet de compléter les préférences. Il n'y a ni duplication d'Owner ni perte de capacité d'administration. Le chemin est documentable, mais pas visuellement observé ici.

## 39. Release-impact classification

**NON-BLOCKING DOCUMENTED LIMITATION** sur le plan de l'intégrité et de l'autorité backend. Aucun faux mécanisme de reprise n'a été ajouté. Une reprise exacte du wizard nécessiterait une phase corrective distincte si le produit l'exige.

## 40. Code/config/dependency changes

- Nodemailer verrouillé à `9.1.1`.
- Accès fichiers/URLs désactivés dans la construction des e-mails.
- Fixture et assertions d'upgrade représentatif ajoutées.
- Aucune feature métier, migration de schéma ou version produit ajoutée.

## 41. Files modified

- `package.json`
- `package-lock.json`
- `backend/src/services/emailService.ts`
- `tests/unit/backend/emailService.test.ts`
- `tests/unit/backend/migrations.test.ts`
- `scripts/phase-i1-upgrade-fixture.mjs`
- `docs/PHASE_I1_RELEASE_BLOCKER_CLOSURE_REPORT.md`

## 42. Tests added

Trois tests : deux régressions SMTP et une migration représentative complète. Total passé de 134 à 137.

## 43. Full regression

PASS : 28 fichiers / 137 tests, 137 réussis, 0 skipped/todo.

## 44. Security regression

PASS automatisé : suites IPC fail-closed, preload strict, permissions effectives, identité acteur, expiration élévation, secrets et backup. Audit production : 0 vulnérabilité.

## 45. ESLint

PASS, zéro avertissement (`--max-warnings=0`).

## 46. TypeScript/Vite

PASS : `tsc -b && vite build`, 1 983 modules transformés.

## 47. git diff --check

PASS.

## 48. Production package

PASS dans la copie propre temporaire. `verify:package` confirme `STORE.exe`, `app.asar`, frontend/logo, module natif `better-sqlite3` et installateur NSIS. Taille installateur : 114 875 565 octets. Les artefacts `release` du dépôt n'ont pas été remplacés.

## 49. Packaged startup

PASS : fresh et upgrade, exécutable `win-unpacked`, processus répondants, base exploitable, `better-sqlite3` opérationnel. Les processus test ont été arrêtés exclusivement par chemin/PID du candidat temporaire.

## 50. Installer SHA-256

Nouveau candidat I.1 : `536D364A2B7B66092D372C83F4FBB6219D8BDA4908E7DACC9CE5B7B79CEEACCA`. L'ancien hash Phase I `C1B597…` est invalidé pour ce binaire. Ce candidat n'est ni publié ni distribué.

## 51. Final blocker table

| ID | Blocker | Initial state | Final state | Evidence |
| --- | --- | --- | --- | --- |
| RB-01 | Visual validation | OPEN | OPEN | `apps: []`; écrans NOT OBSERVED |
| RB-02 | 2.0.1 upgrade | OPEN | OPEN | Données préservées et DB intègre, mais installer/login/POS smoke non observés |
| RB-03 | Installer lifecycle | OPEN | OPEN | Installation réelle détectée ; uninstall/reinstall non exécutés pour sécurité |
| RB-04 | Nodemailer vulnerability | OPEN | CLOSED | 9.1.1, audit prod 0, tests SMTP et package PASS |
| RB-05 | Code signing | OPEN | OPEN | Installateur et exe `NotSigned`, aucune décision humaine d'acceptation |
| RB-06 | Packaged E2E visual journey | OPEN | OPEN | Démarrage backend PASS, Setup→Login→POS→sale NOT OBSERVED |

## 52. Release gate matrix

| Gate | Result | Evidence | Blocking? |
| --- | --- | --- | --- |
| Reproducibility | PASS | clean `npm ci`, tests, build, package temporaire | Non |
| Full regression | PASS | 137/137, 0 skipped | Non |
| Security | PASS | suites sécurité + audit prod 0 | Non |
| Data integrity | PASS | integrity/foreign keys/migrations | Non |
| Backup/restore | PASS | suites Phase I + backups pré-migration | Non |
| Fresh install | PARTIAL | paquet fresh démarre, NSIS non installé | Oui |
| Upgrade | PARTIAL | migration data PASS, E2E métier absent | Oui |
| Installer lifecycle | NOT TESTED | profil réel protégé | Oui |
| Packaged execution | PASS | fresh + upgrade répondants | Non |
| Packaged E2E | NOT OBSERVED | aucune surface UI | Oui |
| Visual validation | NOT OBSERVED | `apps: []` | Oui |
| Dependencies | PASS PROD | Nodemailer fermé ; dette dev restante | Non |
| Code signing | FAIL | NotSigned | Oui |

## 53. Remaining non-blocking debt

- 13 avis npm dans le graphe de développement/transitif lors du `npm ci` propre ; pas de `force fix`.
- Fichiers SQLite `*.tmp-wal`/`*.tmp-shm` laissés à côté de certaines sauvegardes validées ; encombrement, pas perte de données démontrée.
- Setup en deux commits : préférences à compléter manuellement après incident.
- Tests de performance UI et SMTP réel contrôlé encore absents.

## 54. Remaining release blockers

1. Validation visuelle humaine aux cinq résolutions, thèmes et états requis.
2. Cycle NSIS complet dans une VM/utilisateur Windows jetable.
3. Upgrade installé avec login des trois rôles et smoke POS complet.
4. Parcours empaqueté Setup→Login→POS→facture→stock, offline inclus.
5. Signature Authenticode réelle ou décision humaine explicite acceptant le canal non signé.

## 55. Acceptance matrix I1-AC-001 → I1-AC-063

| Critère | État | Preuve / motif |
| --- | --- | --- |
| I1-AC-001 | PASS | Baseline vérifiée |
| I1-AC-002 | PASS | Profil réel lecture seule |
| I1-AC-003 | PASS | user-data-dir explicite |
| I1-AC-004 | PASS | backup réel intact |
| I1-AC-005 | PASS | audit en ligne revalidé |
| I1-AC-006 | PASS | chemin runtime tracé |
| I1-AC-007 | PASS | 9.1.1 identifiée |
| I1-AC-008 | PASS | aucun force fix |
| I1-AC-009 | PASS | tests SMTP |
| I1-AC-010 | PASS | fixture créée |
| I1-AC-011 | PASS | JSON + DB pré-upgrade |
| I1-AC-012 | PARTIAL | startup/migrations réels, pas installateur NSIS |
| I1-AC-013 | PASS | integrity/foreign keys |
| I1-AC-014 | PASS | preserved=true |
| I1-AC-015 | NOT OBSERVED | login UI absent |
| I1-AC-016 | NOT OBSERVED | smoke POS absent |
| I1-AC-017 | NOT MET | installateur non installé |
| I1-AC-018 | PASS | exe unpacked lancé |
| I1-AC-019 | NOT MET | uninstall non exécuté |
| I1-AC-020 | PARTIAL | localisation data connue, uninstall non observé |
| I1-AC-021 | NOT MET | reinstall non exécuté |
| I1-AC-022 | NOT MET | comportement reinstall non observé |
| I1-AC-023 | NOT OBSERVED | Setup UI |
| I1-AC-024 | NOT OBSERVED | Login UI |
| I1-AC-025 | NOT OBSERVED | POS UI |
| I1-AC-026 | NOT OBSERVED | vente empaquetée |
| I1-AC-027 | NOT OBSERVED | effet stock E2E |
| I1-AC-028 | PARTIAL | bootstrap local offline seulement |
| I1-AC-029 | NOT MET | visuel critique absent |
| I1-AC-030 | NOT OBSERVED | 1280×800 |
| I1-AC-031 | NOT OBSERVED | 1024×768 |
| I1-AC-032 | NOT OBSERVED | 800×1280 |
| I1-AC-033 | NOT OBSERVED | Setup |
| I1-AC-034 | NOT OBSERVED | POS |
| I1-AC-035 | NOT OBSERVED | Dashboard/Reports |
| I1-AC-036 | NOT OBSERVED | Administration |
| I1-AC-037 | PASS | source d'observation distinguée |
| I1-AC-038 | PASS | configuration auditée |
| I1-AC-039 | PASS | aucun faux certificat |
| I1-AC-040 | PASS | blocker par défaut, décision humaine requise |
| I1-AC-041 | PASS | test reproduit |
| I1-AC-042 | PASS | restart caractérisé |
| I1-AC-043 | PASS | limitation non bloquante documentée |
| I1-AC-044 | PASS | aucune fausse reprise |
| I1-AC-045 | PASS | 137/137 |
| I1-AC-046 | PASS | 0 skipped |
| I1-AC-047 | PASS | suites sécurité |
| I1-AC-048 | PASS | ESLint zéro warning |
| I1-AC-049 | PASS | build |
| I1-AC-050 | PASS | diff check |
| I1-AC-051 | PASS | package produit |
| I1-AC-052 | PASS | startup fresh/upgrade |
| I1-AC-053 | PASS | nouveau SHA-256 |
| I1-AC-054 | PASS | RB-01 OPEN documenté |
| I1-AC-055 | PASS | RB-02 OPEN documenté |
| I1-AC-056 | PASS | RB-03 OPEN documenté |
| I1-AC-057 | PASS | RB-04 CLOSED documenté |
| I1-AC-058 | PASS | RB-05 OPEN documenté |
| I1-AC-059 | PASS | RB-06 OPEN documenté |
| I1-AC-060 | PASS | classification fondée sur preuves |
| I1-AC-061 | PASS | version 2.0.1 |
| I1-AC-062 | PASS | aucune publication |
| I1-AC-063 | PASS | aucune phase fonctionnelle suivante |

## 56. FINAL RELEASE CLASSIFICATION

**NOT READY — RELEASE BLOCKERS REMAIN**

## 57. Required human decision

Le responsable doit fournir un environnement Windows jetable pour le cycle NSIS et les observations UI, puis choisir entre :

1. fournir une identité de signature Authenticode/Azure gérée par secrets CI ; ou
2. accepter explicitement, pour un canal privé défini, la limitation d'une distribution non signée.

Même après ces décisions : ne pas passer à `3.0.0`, ne pas publier et ne pas distribuer sans une nouvelle approbation humaine.
