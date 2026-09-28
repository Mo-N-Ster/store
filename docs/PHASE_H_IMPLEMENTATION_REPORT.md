# STORE 3.0 — PHASE H IMPLEMENTATION REPORT

Date: 2026-09-24
Version: 2.0.1
Périmètre: Setup, first-run et recovery UX. Release/Hardening non commencé.

## 1. Executive summary

Le formulaire initial unique est remplacé par un wizard en six étapes: Bienvenue, Magasin, Propriétaire principal, Préférences essentielles, Vérification et Prêt. Le backend reste l’unique autorité. Aucun nouvel IPC, schéma, moteur d’initialisation ou stockage de reprise renderer n’a été ajouté.

## 2. Baseline

Avant H: 25 fichiers, 121 tests. Après H: 26 fichiers, 131 tests. Version, dépendances, IPC et base inchangés.

## 3. H0 current setup audit

| Capability | UI avant H | IPC | Backend | Effet DB | Réversible | Persisté pendant setup | Risque |
|---|---|---|---|---|---|---|---|
| Détection | Splash/App | needsSetup | compte les Owner | Aucun | Oui | N/A | Élevé |
| Owner | Formulaire unique | setupAdmin | validation + hash + INSERT | users | Non via UI | Au submit | Critique |
| Récupération | 2 champs | setupAdmin | hash bcrypt | users | Modifiable après auth | Au submit | Critique |
| Magasin | Absent | saveSettings | upsert settings | settings | Oui | Après Owner | Moyen |
| Devise | Absente | saveSettings | upsert settings | settings | Oui | Après Owner | Moyen |
| Completion | Implicite | needsSetup | Owner count > 0 | users | Non | Dès Owner créé | Critique |
| Session initiale | Implicite | handler setupAdmin | session sender-bound | Mémoire main | Oui/logout | Après succès Owner | Élevé |

## 4. Current initialization architecture

`App → needsSetup → Setup|Login`. `setupAdmin` vérifie l’absence d’Owner, force `role='owner'`, valide, hache les secrets et insère l’utilisateur. Le handler IPC crée ensuite une session liée au renderer.

## 5. Actual setup state model

Deux états réels uniquement:

- `INCOMPLETE`: aucun utilisateur `role='owner'`.
- `COMPLETE`: au moins un Owner existe.

`NOT_STARTED/IN_PROGRESS/FAILED` ne sont pas persistés.

## 6. Persistence timing

Les étapes visuelles restent en mémoire. Owner et secrets sont persistés lors de `setupAdmin`. Magasin/devise sont persistés ensuite par `saveSettings`. La langue est une préférence personnelle existante dans localStorage, jamais une autorité de setup.

## 7. Transaction/atomicity model

L’INSERT Owner est atomique au niveau SQLite, mais Owner + settings ne forment pas une transaction commune. L’IPC queue sérialise les soumissions, ce qui permet au second `setupAdmin` de voir le premier Owner et d’être refusé.

## 8. Pre-auth trust boundary

Seuls `needsSetup` et `setupAdmin`, ainsi que login/récupération, sont publics. Après `setupAdmin`, la session backend autorise `saveSettings`. Le renderer ne décide jamais que setup est terminé.

## 9. New wizard architecture

État React éphémère, stepper accessible, tâche unique par étape, CTA distinct pour navigation et commit final, focus du titre à chaque transition.

## 10. Welcome

Explique les données requises, le stockage local et l’usage hors ligne sans promesse cloud.

## 11. Shop

Nom, adresse, téléphone et email magasin utilisent les clés settings existantes. Aucun champ fiscal inventé. Le nom possède le défaut backend/UI `STORE`.

## 12. Primary Owner

Prénom, nom, identifiant, email, mot de passe, question et réponse de récupération. Aucun sélecteur de rôle: le backend impose Owner.

## 13. Essential Preferences

Langue FR/EN et devise existante uniquement. Thème non bloquant; SMTP et sauvegarde avancée exclus.

## 14. Verification

Résumé magasin, identité Owner, rôle, langue et devise. Mot de passe et réponse de récupération absents.

## 15. Initialization

CTA « Configurer STORE », progression indéterminée, navigation bloquée, verrou synchrone contre double clic. Ordre réel: `setupAdmin`, puis `saveSettings`.

## 16. Ready

Affiché uniquement après confirmation des deux opérations. Le bouton continue vers la session déjà créée par le moteur existant; aucun nouveau mécanisme d’auto-login n’a été inventé.

## 17. Password handling

Minimum 8 caractères aligné sur `validateUser`; œil accessible avec Lucide Eye/EyeOff; mémoire React uniquement; effacé après création Owner; jamais revu.

## 18. Recovery-secret handling

Réponse saisie comme secret, jamais affichée dans le résumé ni stockée côté navigateur. Hash bcrypt réalisé par le backend.

## 19. Primary Owner security

Le rôle est forcé par `setupAdmin`; le renderer n’envoie pas `role`. `SETUP_ALREADY_COMPLETED` interdit une seconde initialisation.

## 20. Double/concurrent initialization

Verrou renderer immédiat et queue IPC backend. Deux demandes sérialisées ne peuvent pas toutes deux passer le contrôle `needsSetup`.

## 21. Interrupted setup

Avant commit: aucune donnée métier persistée. Pendant Owner: transaction SQLite de l’INSERT. Entre Owner et settings: état partiellement configuré possible, sans corruption mais sans reprise du wizard après redémarrage.

## 22. Resume capability

Reprise uniquement en mémoire si `saveSettings` échoue après Owner: le bouton réessaie les préférences sans recréer l’Owner. Aucune reprise localStorage. Reprise après redémarrage: non supportée.

## 23. Restart behavior

Avant commit, le wizard recommence. Après Owner, `needsSetup=false`, donc login/application. Après succès complet, login/application normal selon session/processus.

## 24. Rollback capability

Aucun rollback setup backend. Aucun rollback frontend simulé.

## 25. Ambiguous-state handling

Une base sans Owner reste en Setup. Une base avec Owner est considérée complète. La détection d’états plus riches ou corrompus n’existe pas; aucune auto-réparation destructive ajoutée.

## 26. Setup routing

Le rendu racine bloque toute UI opérationnelle tant que `needsSetup` vaut true.

## 27. Completed-install protection

Le composant Setup n’est plus rendu quand le backend retourne false; l’IPC `setupAdmin` refuse aussi directement.

## 28. Operational-route protection

Aucun routeur direct n’existe; les écrans opérationnels ne sont construits qu’après la branche Setup puis authentification/session.

## 29. Pre-auth IPC inventory

Voir matrice §61. Aucun settings, backup, diagnostics ou filesystem n’a été rendu public.

## 30. Input tampering

Le rôle est ignoré/écrasé côté backend. `validateUser` vérifie identité, email, mot de passe et récupération. Le flag completed n’est pas accepté depuis React.

## 31. Secret exposure review

Scan ciblé: aucun log renderer, stockage navigateur, écran de vérification ou diagnostic contenant les secrets Setup. Le logger IPC reçoit erreur/méthode/userId, pas les arguments.

## 32. Safe errors

Le wizard présente des messages publics génériques; aucune stack, SQL, exception ou chemin système.

## 33. Responsive behavior

Largeur maximale 58rem, formulaire 42rem, portrait mono-colonne sous 700px et variante paysage bas.

## 34. Virtual keyboard

`100dvh`, page scrollable, actions sticky en portrait et champs mono-colonne réduisent l’occlusion par clavier virtuel.

## 35. Touch

Boutons Design System et contrôle œil ≥44px.

## 36. Keyboard

Contrôles natifs, ordre DOM logique, boutons accessibles et aucun geste exclusif.

## 37. Focus

Le titre de chaque étape reçoit le focus programmatique. Les erreurs utilisent `role=alert`.

## 38. Accessibility

Labels explicites, required, aide password liée par `aria-describedby`, stepper avec `aria-current`, progression et textes d’état.

## 39. Themes/high contrast

Tokens STORE 3.0 et media query contraste renforcé.

## 40. Reduced motion

Media query désactivant transitions/scroll animé.

## 41. i18n

Tous les textes H disponibles en français et anglais.

## 42. Files added

- `frontend/src/pages/auth/SetupWizard.tsx`
- `frontend/src/design-system/setup.css`
- `tests/unit/frontend/setupPhaseH.test.ts`
- `docs/PHASE_H_IMPLEMENTATION_REPORT.md`

## 43. Files modified

AuthPages, PasswordInput, index CSS et i18n.

## 44. Dependencies

Aucune.

## 45. IPC changes

Aucun.

## 46. Database/schema changes

Aucun schéma, migration ou SQL ajouté.

## 47. Tests added

10 tests Phase H.

## 48. Fresh-install tests

PASS en SQLite `:memory:`: zéro Owner représente une installation neuve.

## 49. Existing-install tests

PASS en SQLite `:memory:` et inspection du guard backend: un Owner fait basculer l’état, `setupAdmin` refuse ensuite.

## 50. Security tests

Surface publique, rôle backend, secrets, double submit et post-completion vérifiés.

## 51. Full regression result

PASS — 26 fichiers, 131/131 tests, 0 skipped.

## 52. E.5 security regression

PASS — 9 tests.

## 53. Phase F regression

PASS — 14 tests.

## 54. Phase G regression

PASS — 8 tests.

## 55. ESLint

PASS — zéro warning.

## 56. Build

PASS — TypeScript/Vite production.

## 57. git diff --check

PASS.

## 58. Electron startup

PASS avec environnement nettoyé de `ELECTRON_RUN_AS_NODE`. Aucun contournement applicatif ajouté.

## 59. Visual validation

**NOT OBSERVABLE** — Windows retourne `apps: []`, `browsers: []`. Dette marquée **RELEASE BLOCKER**.

## 60. Setup contract matrix

| Step | Data | Backend capability | Persisted when | Reversible | Secret |
|---|---|---|---|---|---|
| Welcome | Aucune | Aucune | Jamais | Oui | Non |
| Shop | storeName/address/phone/email | saveSettings | Après Owner | Oui | Non |
| Owner | identité/credentials/récupération | setupAdmin | Commit final, premier appel | Non via Setup | Oui |
| Preferences | langue/devise | i18n + saveSettings | Commit final | Oui | Non |
| Verification | résumé non secret | Aucune | Jamais | Oui | Non |
| Commit | Owner puis settings | setupAdmin + saveSettings | Séquentiel | Partiel | Secrets transmis ciblés |

## 61. Pre-auth security matrix

| IPC | Pourquoi public | Setup-only | Autorisé après setup | Validation | Sortie sensible |
|---|---|---|---|---|---|
| needsSetup | Routage avant session | Non | Oui, booléen | Aucune entrée | Non |
| setupAdmin | Bootstrap racine | Oui par guard métier | Appel possible mais DENY `SETUP_ALREADY_COMPLETED` | validateUser + rôle forcé | User public, pas hash |
| login | Entrée auth | Non | Oui | credentials backend | User public |
| forgotPasswordQuestion | Découverte récupération limitée | Non | Oui | identifiant | Question + id |
| recoverPassword | Récupération | Non | Oui | preuve + rate limit | Résultat sûr |

## 62. Secret matrix

| Secret | React memory | Browser storage | Preload | Logs | Verification | Backend |
|---|---|---|---|---|---|---|
| Owner password | Temporaire, effacé après Owner | Non | Argument setupAdmin | Non | Non | bcrypt hash |
| Recovery answer | Temporaire, effacée après Owner | Non | Argument setupAdmin | Non | Non | bcrypt hash normalisé |
| SMTP password | Hors setup | Non | Non utilisé | Non | Non | Inchangé |

## 63. Recovery matrix

| Failure point | État persisté | Restart result | Resume | Rollback | Data risk |
|---|---|---|---|---|---|
| Avant initialisation | Aucun | Setup recommence | Oui, recommencer | N/A | Faible |
| Pendant INSERT Owner | Transaction SQLite | Setup ou Owner créé | Selon commit | SQLite atomique | Faible |
| Après Owner | Owner actif | Login, pas Setup | Pas après restart | Non | Préférences par défaut |
| Pendant settings | Owner + settings partiels possibles par upserts transactionnels saveSettings | Login | En mémoire avant restart | Transaction saveSettings | Moyen UX, faible intégrité |
| Avant completion flag | N/A, aucun flag | Selon Owner count | N/A | N/A | Modèle binaire |
| Après completion | Owner + settings | Login/application | N/A | Non | Faible |

## 64. Responsive matrix

| Screen | 1920×1080 | 1366×768 | 1280×800 | 1024×768 | 800×1280 |
|---|---|---|---|---|---|
| Welcome | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Shop | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Owner | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Preferences | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Verification | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Ready | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Recovery | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |

Structure CSS validée; ce n’est pas un PASS visuel.

## 65. Acceptance matrix

| Critères | Résultat | Preuve / notes |
|---|---|---|
| H-AC-001–007 | PASS | Audit, modèle binaire, routage backend, Design System |
| H-AC-008–016 | PASS | Six étapes, champs backend existants, formulaire unique supprimé |
| H-AC-017–029 | PASS | Policy inchangée, secrets protégés, Owner backend, locks, succès confirmé |
| H-AC-030 | PASS | Matrice interruption §63 |
| H-AC-031 | PASS | Reprise seulement en mémoire supportée; aucune fausse reprise persistée |
| H-AC-032 | PASS | Aucun état setup en localStorage |
| H-AC-033 | PARTIAL | Modèle backend ne détecte que présence Owner; états riches absents |
| H-AC-034–047 | PASS | Pas de reset auto; IPC minimal; aucun schéma/migration/IPC/dépendance |
| H-AC-048–050 | PARTIAL | Support CSS structurel; visuel non observable |
| H-AC-051–057 | PASS | Overflow, clavier virtuel, touch, clavier, focus, stepper, erreurs |
| H-AC-058–060 | PARTIAL | Architecture thème/contraste validée, rendu non observable |
| H-AC-061–065 | PASS | Reduced motion, FR/EN, tests install isolés |
| H-AC-066–071 | PASS | 131/131, E.5/F/G, nouveaux tests, 0 skip |
| H-AC-072–076 | PASS | lint, build, diff, Electron propre, 2.0.1 |
| H-AC-077 | PASS | Résultat visuel honnête |
| H-AC-078 | PASS | Dette visuelle marquée release blocker |
| H-AC-079–080 | PASS | Dettes conservées; phase suivante non commencée |

## 66. Deviations

Le moteur ne possède pas un commit unique Shop+Owner+Preferences ni les quatre états envisagés. Le wizard orchestre donc deux opérations existantes et expose clairement la récupération possible.

## 67. Security debt

État setup binaire basé sur Owner count; pas d’état FAILED contrôlé; dettes E.5/G inchangées.

## 68. Functional debt

Pas de reprise persistée après Owner créé/settings échoués. Une future évolution exige un contrat backend transactionnel dédié, pas localStorage.

## 69. Reliability debt

Tests réels de coupure processus entre les deux commits et installation packagée restent nécessaires dans la future phase.

## 70. UX debt

Validation visuelle réelle multi-résolution obligatoire avant release.

## 71. Tooling limitations

Computer Use ne voit aucune fenêtre. Vite/Vitest nécessitent hors sandbox sous Windows à cause de `spawn EPERM`.

## 72. Files intentionally untouched

Moteur `setupAdmin`, schéma, migrations, preload, registre IPC, RBAC, backup/restore et dettes hors H.

## 73. Recommendation for next phase

Release & Reliability Hardening, sous autorisation distincte: visual QA réel, installateur, upgrade, crash tests, interruption entre commits, backup/restore catastrophe, performance et artefacts. Cette phase n’a pas été commencée.
