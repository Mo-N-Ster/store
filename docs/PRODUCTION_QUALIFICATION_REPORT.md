# STORE — décision de qualification production

## Reprise tooling / gel — 2026-09-28 — gates logiciels PASS

Cette section remplace l'état de blocage npm ci-dessous ; historique conservé.

- Tooling audit before: 13 paquets signalés.
- Tooling remediation: npm 11.20.0 exécuté ponctuellement via npx, compatible Node 22.17 ; npm global 11.5.2 inchangé. Aucun force/legacy-peer-deps/override. Vitest 4.1.11 et concurrently 10.0.5 épinglés ; transitives compatibles mises à jour par npm, lockfile non fabriqué.
- Versions corrigées : mocker et famille Vitest 4.1.11 ; xmldom 0.8.15 ; brace-expansion 1.1.21/2.1.7/5.0.12 ; shell-quote 1.9.0 ; fast-uri 3.1.8 ; joi 18.2.9 ; js-yaml 4.3.2 ; nanoid 3.3.19 ; postcss 8.5.28 ; tar 7.5.22 ; undici 6.29.0/7.30.0. Sourcemap-codec 1.6.0 et tinyrainbow 3.1.1 actualisés dans le graphe résolu. Peers DEV optionnels windows-sign/fs-extra/cross-dirname/postject/commander ajoutés par résolution des chaînes builder existantes, pas ajout de signature configurée.
- Files/dependencies changed: package.json, package-lock.json ; .gitignore pour sorties locales ; ce rapport. Aucun code métier changé pendant cette remédiation. Versions/résolutions runtime comparées à la référence inchangées ; Electron 43.1.1, Vite 8.1.5, TypeScript 6.0.3 et builder 26.15.3 inchangés.
- Tooling audit after: npm audit, 0 alerte, sortie 0.
- Production audit: npm audit --omit=dev, 0 alerte, sortie 0.
- Tests: npm test, unique passe complète après remédiation, 56 fichiers / 291 PASS / 0 FAIL / 0 SKIP, Vitest 4.1.11. Durée 77.81 s ; ce n'est pas un benchmark comparable.
- RF-004: PASS dans la suite complète ; migration 18 conservée.
- Lint: PASS, zéro warning. Build: PASS TypeScript/Vite. git diff --check: PASS.

### Worktree review / staging policy

INCLUDE : backend/frontend/tests actuels, toutes migrations dont 18, scripts source de validation, configuration/manifestes/lockfile, documentation maintenue et historique daté, icônes build/icon.ico/build/icon.png et frontend/public/store-logo.png. Les suppressions anciennes sont incluses : preload TS remplacé par CTS, Header/Sidebar remplacés par StoreShell, ManagerAuthModal retiré selon J.4. Aucun retour arrière de fonction.

EXCLUDE : chaque rapport JSON généré au premier niveau artifacts (input-status, j6ra, presence-audit, private-rc, release-freeze-media, RF001–003, S1, stability, ui/ux), preuves locales sans politique exigeant leur versionnement ; artifacts/input-smoke (sortie harness), artifacts/j6ra-node-abi (cache/binaire natif), artifacts/release-candidate (binaires), tous artifacts/store-j6ra-* (profils synthétiques/DB/logs). Pas de suppression ; preuves réutilisables localement. Dist/release/node_modules/caches/backups/DB/logs/secrets exclus. Le harness alternatif j6ra reste un outil source optionnel nécessitant son cache ; npm test standard validé n'en dépend pas.

REVIEW/BLOCK : tout fichier inattendu ou secret au preflight, jamais ajouté automatiquement. Aucun git add global ; chemins source explicitement sélectionnés. Le gel inclut les modifications fonctionnelles précédentes déjà validées, pas seulement les dépendances de cette intervention.

Secret preflight: 253 fichiers sélectionnés ; aucun chemin DB/profil/archive/clé/secrets/generated interdit dans l'index, aucun motif de clé privée/token évident détecté. Credentials littéraux recherchés sans affichage des valeurs ; fixtures synthétiques conservées. Ce contrôle ciblé ne constitue pas une garantie universelle d'absence de secret. Les blancs de fin de ligne/EOF de fichiers auparavant non suivis ont été normalisés, sans changement sémantique ; git diff --cached --check PASS. Aucun fichier fonctionnel écarté.

Freeze commit: commit contenant cette section, message `chore(release): freeze STORE production candidate source` ; SHA et résultat post-commit consignés dans la réponse finale pour éviter une auto-référence impossible du hash dans son propre commit. Source freeze: gates pré-commit PASS, statut définitif conditionné au commit et à l'absence de modifications restantes. Aucun tag/push.

Next gate: RC fraîche et qualification install/humaine sous autorisation ultérieure. Aucun tag/push/installateur/signature/certification autorisé ici.

---

## Tooling remediation + source freeze — 2026-09-28 — BLOCKED

Tooling audit before: 13 paquets (9 high, 3 moderate, 1 low), confirmé par `npm audit --json` ; chaînes ci-dessous vérifiées par `npm ls --all`.

| Paquet | Sévérité | Direct/transitif, chaîne principale | Correctif compatible visé | Impact |
|---|---|---|---|---|
| vitest | moderate | Direct | 4.1.10 → 4.1.11 patch | Runner/tests |
| @vitest/mocker | moderate | vitest → mocker | 4.1.11 aligné | Mock/tests |
| @xmldom/xmldom | high | electron-builder → app-builder-lib → plist | 0.8.13 → 0.8.15 patch | Parsing packaging |
| brace-expansion | high | builder → asar/universal → minimatch ; eslint → minimatch | branches 1.1.18 / 2.1.4 / 5.0.9 | Globs packaging/lint |
| concurrently | high | Direct, via shell-quote | Corriger transitive shell-quote | Lancement dev |
| shell-quote | high | concurrently → shell-quote | >1.8.4 ; 1.8.5 absent du registre, version 1.10.0 disponible (minor) | Parsing commande dev |
| fast-uri | high | builder → app-builder-lib → ajv | 3.1.6 patch | Validation config build |
| joi | low | wait-on → joi | 18.2.5 patch | Attente serveur dev |
| js-yaml | high | builder/app-builder-lib/builder-util/dmg-builder | 4.3.2 patch | Configuration build |
| nanoid | high | vite → postcss → nanoid | 3.3.18 patch | Génération build CSS |
| postcss | moderate | vite → postcss | 8.5.23 patch | Pipeline CSS |
| tar | high | builder → app-builder-lib/rebuild → node-gyp | 7.5.21 patch | Archives/build natif |
| undici | high | electron → @electron/get ; builder → rebuild → node-gyp | 7.29.0 / 6.28.0 minor | Téléchargement tooling |

Tous les nœuds vulnérables constatés sont DEV/tooling dans le lockfile. Inclusion runtime non attendue selon packaging, mais exposition de l'artefact final UNKNOWN sans RC fraîche. Ces correctifs ne nécessitent pas en principe un changement majeur Electron/Vite/TypeScript ; ils doivent encore être résolus et validés ensemble. Ne pas interpréter les versions visées comme installées.

Tooling remediation:
- `npm update` ciblé avec ignore-scripts : échec interne npm 11.5.2, `Cannot read properties of null (reading 'edgesOut')`.
- Même échec en limitant à `npm update vitest`, puis en demandant explicitement `npm install --save-dev --save-exact vitest@4.1.11 --ignore-scripts --no-audit`.
- Stack dans Arborist `#loadPeerSet`/`build-ideal-tree.js`. Résolution de peers optionnels observée ; cause profonde non attribuée sans preuve. Aucun force, legacy-peer-deps, suppression node_modules, mise à jour globale npm ou contournement des peers appliqué.

Files/dependencies changed: aucun changement de dépendance appliqué ; lockfile comparé intégralement à sa référence pré-intervention et inchangé, manifeste toujours vitest ^4.1.10. Seul ce rapport actualisé.
Tooling audit after: non relancé après échec de résolution ; arbre inchangé, 13 alertes restent ouvertes.
Production audit: dernier contrôle explicite 0, dépendances runtime inchangées ; pas de nouveau gate final annoncé.
Tests: dernier résultat RF004 291 PASS ; non relancés, remédiation non terminée.
RF-004: CLOSED, code/tests non modifiés.
Lint / Build: derniers PASS RF004 ; non relancés.
git diff --check: PASS pour l'état inspecté, contrôle documentaire après mise à jour.
Worktree review: étape B non commencée, condition préalable de remédiation/gates non atteinte.
Excluded personal/data files: aucun staging ; artifacts, profils et backups restent non ajoutés.
Secret preflight: staged set non constitué, aucun verdict de conformité final.
Freeze commit: NONE.
Post-commit worktree: N/A, modifications utilisateur antérieures préservées.
Source freeze: BLOCKED.
Next gate: résoudre le défaut du gestionnaire npm via une version compatible exécutée isolément, sans remplacement global, puis reprendre les correctifs ciblés et l'unique passe complète. Demander autorisation avant ce changement d'outil hors plan initial ; aucun changement métier requis ni effectué.

---

## Qualification actuelle — 2026-09-28 — BLOCKED

Cette section remplace les conclusions d'état courant du rapport historique ci-dessous, conservé comme preuve datée.

Source baseline:
- HEAD `d46c56c8af570fe96f9aba875726c1ca2c5abd53`, Windows x64.
- Avant documentation : 64 entrées suivies modifiées, 4 supprimées, 144 entrées non suivies (répertoires parfois regroupés). Migrations, corrections RF et nombreux modules fonctionnels non commitées. Aucun commit/tag/snapshot de livraison couvrant cet ensemble n'est établi. Aucun nettoyage, stage ou commit effectué.
- Freeze BLOCKED : ne pas associer le futur binaire au seul HEAD historique. Revue et gel explicites des changements nécessaires avant fabrication.

Version: 2.0.1 — STORE by VIBE.
Migration: 18, colonne nullable et comparaison RF004 présentes dans le code courant.
Environment: Node 22.17.0 ; npm 11.5.2 ; Electron installé 43.1.1 ; better-sqlite3 12.11.1 ; win32/x64.

Tests:
- Preuve RF004 réutilisée : 291 PASS / 56 fichiers / 0 FAIL / 0 SKIP, reproducer corrigé et historique NULL refusé.
- Pas de nouveau gate complet : préparation non stable/source non gelée. Ne pas appeler cette preuve un gate final RC.
Lint: PASS RF004 historique, non relancé.
Build: PASS RF004 historique (TypeScript/Vite), non relancé.
git diff --check: PASS dans cette inspection.

Dependencies:
- Audit actuel `npm audit --omit=dev --json` : 0 alerte, sortie 0.
- Audit actuel explicite `npm audit --include=dev --json` : 13 paquets signalés, 9 high / 3 moderate / 1 low, sortie 1. Une seconde lecture complète confirme ces résultats.
- Tous les nœuds signalés sont DEV dans package-lock. Cela ne prouve pas l'absence du risque dans un artefact non encore inspecté, et l'audit npm ne certifie pas Electron/Chromium.
- Le précédent résultat tooling zéro n'est pas reproductible avec ces options explicites ; cause historique non démontrée. Aucune mise à jour ni remédiation supposée.

Tooling vulnerabilities:

| Package | Severity | Dependency class | Runtime exposure | Affected production artifact? | Fix available (npm) | Upgrade risk | Release blocker? |
|---|---|---|---|---|---|---|---|
| @vitest/mocker | moderate | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| @xmldom/xmldom | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| brace-expansion | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| concurrently | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| fast-uri | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| joi | low | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| js-yaml | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| nanoid | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| postcss | moderate | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| shell-quote | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| tar | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| undici | high | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |
| vitest | moderate | DEV/tooling | Aucun chemin runtime établi ; entrée de build/test potentiellement exposée | UNKNOWN (RC non construit ; exclusion DEV prévue) | YES | Régression build/test à vérifier | UNKNOWN |

Les 13 entrées sont des paquets affectés, pas seulement 13 advisories. Les risques incluent parsing XML/YAML/archives/URI, déni de service, lecture de fichiers via test/CSS et clients HTTP de tooling. Pas d'exploitation runtime STORE démontrée dans ce contrôle restreint. Ensemble non clos pour qualification : revue de correction contrôlée ou justification explicite nécessaire. Aucun `audit fix`, upgrade Electron/Vite ou changement de lockfile effectué.

Release configuration:
- appId com.monster.store, productName STORE, copyright VIBE ; Windows NSIS x64 prévu ; version inchangée.
- Installation assistée, choix de répertoire, raccourcis ; deleteAppDataOnUninstall=false. Conservation prévue, non test install réel.
- Icônes présentes/non vides : icon.ico 54437 octets, icon.png 132989 octets.
- Dist/package.json et dépendances runtime ; exclusions maps/DB/sqlite/.env ; SQLite natif décompressé asar. Vérificateur package existant ; pas exécuté sur une RC fraîche inexistante.
- userData Electron inchangé ; override profil test refusé en packaged. Aucun test d'installateur sur le profil personnel autorisé/exécuté. Une VM ou session Windows séparée est nécessaire pour qualifier le binaire packagé sans contourner ce guard.
- Pas d'updater opérationnel attesté.

Secrets:
- Scan ciblé backend/frontend/build/scripts/dist pour signatures de clés privées/tokens évidents : aucun résultat ; aucune valeur secrète affichée.
- Vérification exhaustive credentials SMTP/passwords et contenu final de l'archive : NOT VERIFIED, pas de RC créée. Ne pas conclure « release sans secret » depuis ces seules signatures.

Fresh DB:
- Preuves RF004/migrations de base neuve réutilisées ; application installée + setup réel NOT TESTED.
Upgrade DB:
- Test RF004 pré-18 isolé avec données de vente, ajout NULL sans backfill, comparaison facture/lignes/paiements/mouvements/produits et redémarrage : PASS précédent.
- Authentification post-upgrade packagé et conservation exhaustive des utilisateurs à travers installateur : HUMAN VALIDATION REQUIRED.
SQLite integrity:
- integrity_check/FK PASS dans fixture RF004 ; pas de DB personnelle consultée.
Backup/restore/reset evidence:
- maintenanceIntegration, backupBundleJ2, backupValidation, restoreRecovery, resetRecovery et articleMedia inclus dans la suite 291 PASS. Tests isolés ; scénarios physiques/installés et coupures réelles restent à exécuter sur copie.

RC artifact: NONE — nouvelle génération bloquée par freeze non établi ; ancien installateur non réutilisé.
SHA-256: N/A, aucune nouvelle RC.
Fresh install: HUMAN VALIDATION REQUIRED.
Upgrade install: HUMAN VALIDATION REQUIRED.
Uninstall/reinstall: HUMAN VALIDATION REQUIRED.
Restart packaged: HUMAN VALIDATION REQUIRED.
Human checklist: docs/PRODUCTION_ACCEPTANCE_CHECKLIST.md ; tous NOT TESTED pour nouvelle RC.

Code signing:
- CODE SIGNING: BLOCKED / NOT CONFIGURED.
- Pas de configuration de signature dans builder ni noms de variables CSC_/WIN_CSC_/AZURE_TRUSTED_SIGNING détectés dans cette session. Cela ne prouve pas absence de certificat dans tout Windows ; aucun service valide établi ici.
- Ancien binaire NotSigned selon rapport historique. RC non signée autorisée pour qualification, pas obstacle absolu à sa fabrication ; ne pas déclarer signée/certifiée.

Known limitations:
- Contrat et limites CURRENT_APPLICATION_BASELINE conservés ; aucune correction métier/UX engagée.

Production blockers:
1. État source fonctionnel non gelé/non commité, aucune association reproductible acceptée pour fabrication.
2. Qualification tooling non close (13 paquets DEV signalés, risque build à traiter/justifier).
3. Gate logiciel final, nouveau package, vérification secrets/package et qualification install/humaine non réalisés.

Qualification: BLOCKED

Suite nécessaire : autorisation d'une revue/remédiation ciblée des dépendances de build/test, puis revue et commit de gel des seuls fichiers nécessaires (sans embarquer artifacts/profils), avant gate final unique et RC. Aucun fichier applicatif ni dépendance changé ; aucune installation, publication, signature ou distribution.

---

Date : 2026-09-27. **Statut : NO-GO — qualification non terminée.**

La demande de production autorise la préparation d'une livraison, mais ne constitue pas une preuve de validation humaine ni une certification indépendante. Aucun installateur n'a été renommé ou déclaré « certifié » dans cette étape.

## Candidat examiné

- Version technique : 2.0.1 ; Windows x64.
- Fichier : `artifacts/release-candidate/STORE Setup 2.0.1-x64.exe`.
- SHA-256 : `982914D0E60C2F7CE34473FA389F653581BA899ACCF1CDA48D9CB6086B4C1579`.
- Authenticode : **NotSigned**, vérifié sur le fichier actuel.
- Le dépôt contient de nombreuses modifications antérieures non commitées. Aucun tag de livraison reproductible n'est établi dans cette étape.

## Preuves acquises

| Contrôle | Preuve | Portée |
|---|---|---|
| Régression automatisée | 52 fichiers, 260 tests réussis, 0 échec/ignoré | Rapport existant `artifacts/stability-decimal-tests.json`, relu dans cette étape |
| Lint / TypeScript / Vite | Réussis lors du dernier assemblage | Ne remplace pas l'essai sur machine cible |
| Saisie / changement d'utilisateur | Banc Electron passé | Vrais composants, API simulée, profil jetable |
| SQLite embarqué | ABI Electron 148, chargement/intégrité mémoire vérifiés | Ne certifie pas tous les scénarios disque |
| Dépendances npm de production | `npm audit --omit=dev --json` : 0 alerte | Audit exécuté à nouveau ; n'est pas un audit exhaustif du runtime Electron/Chromium |
| Sauvegarde/reset/restore | Journaux de reprise et tests d'intégration ajoutés | Voir `INPUT_STATUS_RELEASE_CANDIDATE.md` ; remplace les constats techniques antérieurs sur les chemins corrigés |

## Points non clos

1. **Validation humaine** : `PHASE_J6_HUMAN_VISUAL_VALIDATION_CHECKLIST.md` contient encore les résultats/observateur/build à renseigner. Aucun résultat humain PASS ne peut être déduit des tests automatisés.
2. **Chaîne de fabrication** : nouvel `npm audit --json`, 13 alertes (1 faible, 3 modérées, 9 élevées). Paquets signalés : @vitest/mocker, @xmldom/xmldom, brace-expansion, concurrently, fast-uri, joi, js-yaml, nanoid, postcss, shell-quote, tar, undici, vitest. Corrections annoncées disponibles ; elles ne sont pas appliquées dans ce contrôle. Mise à jour contrôlée et régression sur environnement propre nécessaires, sans remplacer un module natif utilisé par STORE ouvert.
3. **Signature / identité éditeur** : fichier non signé ; aucune chaîne de signature de production vérifiée. Obtenir la décision de l'éditeur et son service/certificat de signature. Ne jamais transmettre clé privée ou mot de passe dans le chat. Une signature atteste l'éditeur/intégrité, pas la sûreté fonctionnelle.
4. **Qualification de distribution** : installation, mise à niveau depuis la version installée, désinstallation conservant les données et retour arrière à vérifier sur environnement Windows isolé. Les essais de démarrage de développement ne prouvent pas ces parcours.
5. **Conditions terrain** : essais sur appareils cibles, imprimante, SMTP autorisé, absence de réseau, reprise après interruption et disque plein restent à documenter. N'effectuer aucune coupure ni opération destructive sur les données personnelles.
6. **Traçabilité de livraison** : version définitive, périmètre fonctionnel accepté, état source figé, dépendances verrouillées et empreintes finales à enregistrer après les corrections. Ne pas attribuer une certification externe sans organisme/référentiel et preuve.

## Ordre de clôture

1. Corriger les dépendances vulnérables dans un environnement de build isolé, rejouer tests/lint/build/native et documenter chaque éventuelle dérogation.
2. Vérifier les parcours d'installation/migration/retour arrière et résilience sur profil jetable.
3. Faire exécuter et signer la recette humaine sur le candidat identifié par empreinte.
4. Confirmer version, éditeur et moyen de signature ; signer le candidat accepté et contrôler la signature/horodatage.
5. Produire manifeste de livraison, empreintes, notes de version et consignes de sauvegarde/mise à jour. Décision GO explicite seulement après clôture des critères convenus.

## Décision de cette étape

**Conserver le statut candidat de test. Aucun déploiement production, signature, publication ou remplacement de l'application installée effectué.**

Informations nécessaires : résultats de recette humaine et disponibilité d'un certificat/service de signature appartenant à l'éditeur ; si « certifiée » désigne une certification externe particulière, préciser le référentiel et l'organisme.
