# STORE 3.0 — PHASE G IMPLEMENTATION REPORT

Date: 2026-09-24
Version: 2.0.1
Périmètre: Administration uniquement; Phase H non commencée.

## 1. Executive summary

Phase G introduit un espace Administration tablet-first, permission-driven et sans nouveau contrat backend. Les comptes, permissions effectives, paramètres, sauvegardes, diagnostics, limites d’audit et réinitialisation sont séparés. Les secrets et chemins complets ne sont pas affichés. Les opérations sensibles sont verrouillées contre la double soumission et restent réautorisées par le backend.

## 2. Baseline

Baseline reçue: 24 fichiers, 113 tests. Résultat final: 25 fichiers, 121 tests. Version et dépendances inchangées.

## 3. G0 administration capability audit

| Capability | UI avant G | IPC / backend | Permission | Données modifiées | Réversible | Audité | Réauthentification | Risque |
|---|---|---|---|---|---|---|---|---|
| Users | Équipe | users/saveUser | EMPLOYEES:READ/UPDATE | users | Partiel | Partiel | Non | Élevé |
| Role | Formulaire employé | saveUser | EMPLOYEES:UPDATE + invariants backend | users.role | Partiel | Partiel | Non | Critique |
| Permissions | Absente | sessionView | ADMINISTRATION:READ pour la vue G | Aucune | Oui | N/A | Non | Moyen |
| Settings | Formulaire monolithique | settings/saveSettings | SETTINGS:READ/UPDATE | settings | Oui | Non complet | Non | Élevé |
| Backup | Zone Données | backup/backups/exportBackup | BACKUPS:READ/CREATE | fichiers locaux | Oui | Non | Non | Élevé |
| Restore | Bouton + confirm natif | restoreBackup | RESTORE:VALIDATE | base SQLite | Pré-backup | Partiel | Dette | Critique |
| Diagnostics | Zone Données | systemDiagnostics | ADMINISTRATION:READ | Aucune | Oui | N/A | Non | Élevé |
| Audit | Aucun lecteur | aucun IPC de lecture | Aucun contrat | Aucune | Oui | Source partielle | N/A | Élevé |
| Reset | Zone Données | reset | RESET:VALIDATE | données métier | Non | Partiel | Mot de passe Owner | Critique |

## 4. Administration trust boundaries

Renderer → `effectivePermissions` → preload allowlist → registre IPC default-deny → validation backend. L’interface ne constitue jamais une autorisation.

## 5. Administration IA

Hub organisé en Accès, Configuration, Protection des données, Système et Actions critiques. Chaque destination est filtrée par permission effective.

## 6. Users

La liste Comptes réutilise la liste Équipe sans la dupliquer. Recherche, rôle, état et fiche de détail sont conservés.

## 7. Accounts

Le mode Administration met en avant identifiant, email, rôle et état. Employee et Account restent conceptuellement distincts.

## 8. Role management

Le moteur `saveUser` existant est inchangé. Les options privilégiées sont rendues conservativement; le backend reste l’autorité finale.

## 9. Temporary password handling

La génération exige désormais un clic explicite, est verrouillée contre la double soumission, reste en mémoire React uniquement et disparaît de l’écran après 60 secondes.

## 10. Primary Owner invariants

Les protections backend existantes contre la désactivation ou rétrogradation du dernier Owner sont inchangées.

## 11. Permissions

La vue montre modules et actions READ/CREATE/UPDATE/DELETE/VALIDATE à partir de `effectivePermissions`.

## 12. Base/effective permission presentation

La vue identifie l’accès courant et une éventuelle élévation temporaire. La matrice de base séparée n’étant pas exposée, elle n’est pas reconstruite côté frontend.

## 13. Permission editing capability status

**CUSTOM PERMISSION EDITOR — DEFERRED.** Les permissions sont dérivées des rôles; aucune surcharge persistée ni API d’édition n’existe.

## 14. Settings classification

Personnel: thème/langue dans le shell. Global: identité, devise, remises. Sensible: SMTP. Système: sauvegardes/diagnostics séparés.

## 15. Personal settings

Le shell existant conserve langue FR/EN, thème, contraste système et réduction des animations. Aucun doublon n’a été créé.

## 16. Store-wide settings

Identité magasin, coordonnées, devise et remises utilisent exclusivement `saveSettings`.

## 17. Sensitive settings

SMTP est isolé. Le mot de passe n’est jamais relu; un champ vide conserve le secret backend existant.

## 18. Backup architecture

SQLite local, écriture temporaire puis rename, `quick_check`, sauvegarde quotidienne vérifiée chaque heure, conservation de sept automatiques.

## 19. Backup UI

Actions, état local, liste bornée à 25 entrées et métadonnées sans chemin système complet.

## 20. Automatic backups

Comportement backend inchangé: une par jour, contrôle horaire tant que l’app est ouverte, sept retenues.

## 21. Manual backups

Création et export emploient le moteur existant, avec état occupé et verrou anti-double action.

## 22. Restore

Sélection, explication, confirmation critique, appel backend puis rechargement seulement après succès.

## 23. Restore validation

Validation existante conservée: intégrité SQLite, tables essentielles, Owner actif et compatibilité gérée par le moteur.

## 24. Restore limitations

Owner reauthentication et chiffrement des sauvegardes restent différés. Le file picker générique reste une dette E.5.

## 25. Diagnostics

Version, schéma, intégrité, taille, espace, nombre de sauvegardes, emails en attente et heartbeat sont présentés avec statuts textuels.

## 26. Secret sanitization

Ni secrets SMTP, mots de passe, hashes, réponses de récupération, tokens, ni chemins complets ne sont rendus.

## 27. Diagnostic limitations

Pas de télémétrie distante ni dump brut. Le dossier peut être ouvert via l’IPC autorisé sans afficher son chemin.

## 28. Audit architecture

La table `audit_logs` existe et trace certaines actions; aucun IPC de lecture n’existe.

## 29. Audit presentation

L’interface affiche explicitement le statut partiel/différé et ne fabrique aucune ligne.

## 30. Actor/authorizer

La table courante possède `user_id` mais pas un champ authorizer complet. La distinction E.5 ne peut donc pas être présentée fidèlement: différée.

## 31. Audit limitations

**DETAILED OWNER AUDIT — PARTIAL / DEFERRED.** Nécessite un contrat backend distinct.

## 32. Danger Zone

Physiquement séparée des paramètres et sauvegardes; seule l’opération Reset existante y figure.

## 33. Reset

Mot de passe Owner, conséquence, irréversibilité, confirmation et verrou anti-double soumission.

## 34. Pre-reset backup

Créé exclusivement par le backend existant; aucun doublon renderer.

## 35. Destructive-operation protections

Permission effective pour visibilité, confirmation pour intention, IPC autorisé et revalidation backend pour sécurité.

## 36. Permission integration

Les actions Users, Settings, SMTP, Backup, Restore, Diagnostics et Reset utilisent `can()` sur les permissions effectives.

## 37. Direct-access protection

Administration reste inaccessible sans SETTINGS:READ; les sections sont recalculées et une section perdue revient au hub.

## 38. Elevation expiry

Le comportement Phase F est conservé: permissions vidées immédiatement puis session rafraîchie; la page privilégiée est démontée.

## 39. Privileged-data cleanup

Effets React annulent les mises à jour tardives; backups et diagnostics sont vidés au démontage.

## 40. Error handling

Messages publics génériques; aucune exception interne ni chemin de fichier dans les notifications.

## 41. Responsive behavior

Deux colonnes desktop/tablette paysage, navigation horizontale et contenu mono-colonne en portrait.

## 42. Touch

Navigation ≥44 px et boutons Design System.

## 43. Keyboard

Boutons natifs, formulaires labellisés et ConfirmDialog/Modal avec piège de focus et Escape.

## 44. Focus

Les primitives Modal restaurent le focus précédent. Les états occupés neutralisent les soumissions répétées.

## 45. Accessibility

Landmarks, `aria-current`, tables avec rôles/en-têtes, statuts textuels et labels explicites.

## 46. Themes/high contrast

Couleurs via tokens; les états ne reposent pas uniquement sur la couleur.

## 47. Reduced motion

Media query dédiée; aucune animation décorative ajoutée.

## 48. i18n

Tous les nouveaux libellés sont fournis en français et anglais.

## 49. Files added

`frontend/src/design-system/administration.css`, `tests/unit/frontend/administrationPhaseG.test.ts`, ce rapport.

## 50. Files modified

SettingsPage, EmployeeList, PasswordResetDialog, TeamPage, DashboardPage, App, navigation, index CSS et i18n.

## 51. Dependencies

Aucune dépendance ajoutée en G.

## 52. Database/IPC changes

Aucun SQL, migration, schéma, canal IPC ou modification backend en G.

## 53. Tests added

8 tests Phase G: permissions, lecture seule, comptes, mot de passe temporaire, verrous, confidentialité, dettes, responsive.

## 54. Full test result

PASS — 25 fichiers, 121/121 tests, 0 ignoré.

## 55. Security regression result

PASS — les 9 tests E.5 restent verts.

## 56. Phase F regression result

PASS — les 14 tests Phase F restent verts.

## 57. ESLint

PASS — 0 warning.

## 58. Build

PASS — TypeScript + Vite production.

## 59. git diff --check

PASS.

## 60. Electron startup

PASS après suppression, pour le processus de test, de la variable externe `ELECTRON_RUN_AS_NODE=1`. Sans cela Electron est intentionnellement forcé en mode Node et ne peut importer `app`.

## 61. Admin capability matrix

| Capability | Permission | Backend source | Reversible | Confirmation | Audit | Status |
|---|---|---|---|---|---|---|
| User management | EMPLOYEES:READ/UPDATE | users/saveUser | Partiel | Backend/contextuelle | Partiel | Implémenté |
| Role management | EMPLOYEES:UPDATE | saveUser + invariants | Partiel | Backend | Partiel | Existant |
| Permissions | ADMINISTRATION:READ | sessionView | Oui | N/A | N/A | Lecture seule |
| Settings | SETTINGS:READ/UPDATE | settings/saveSettings | Oui | Non | Partiel | Implémenté |
| Manual backup | BACKUPS:CREATE | backup | Oui | Non | Non | Implémenté |
| Restore | RESTORE:VALIDATE | restoreBackup | Pré-backup | Oui | Partiel | Implémenté |
| Diagnostics | ADMINISTRATION:READ | systemDiagnostics | Oui | N/A | N/A | Implémenté |
| Audit | Aucun lecteur | audit_logs interne | Oui | N/A | Source partielle | Différé |
| Reset | RESET:VALIDATE | reset | Non | Oui + password | Partiel | Implémenté |

## 62. Danger matrix

| Action | Permission | Qui | Réversible | Backup avant | Confirmation | Réauth | Audit | Récupération |
|---|---|---|---|---|---|---|---|---|
| Restore | RESTORE:VALIDATE | Permission effective | Pré-backup | Oui | Oui | Différée | Partiel | Backup pré-restore |
| Reset | RESET:VALIDATE | Permission effective + Owner password | Non | Oui | Oui | Oui | Partiel | Backup pré-reset |

## 63. Secret exposure matrix

| Valeur | Backend | Preload | Renderer | Logs | Diagnostics |
|---|---|---|---|---|---|
| Password | Hash/validation | Argument ciblé | Champ transitoire | Non attendu | Non |
| Password hash | DB seulement | Non | Non | Non attendu | Non |
| Temporary password | Généré à la demande | Résultat ciblé | Mémoire, 60 s | Non | Non |
| Recovery answer/hash | Validation backend | Argument ciblé | Saisie transitoire | Non attendu | Non |
| SMTP secret | Stockage chiffré si disponible | Écriture ciblée | Jamais relu | Non attendu | Non |
| Session secret | Process principal | Non exposé | Non | Non attendu | Non |

Scan ciblé renderer Admin: aucun identifiant sensible interdit trouvé.

## 64. Responsive matrix

| Area | 1920×1080 | 1366×768 | 1280×800 | 1024×768 | 800×1280 |
|---|---|---|---|---|---|
| Admin Home | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Users | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Permissions | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Settings | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Backups | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Diagnostics | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Audit | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |
| Danger Zone | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |

Structure CSS validée aux seuils 1024 et 760 px; cela ne remplace pas une inspection visuelle.

## 65. Acceptance matrix

| Critères | Résultat | Preuve / note |
|---|---|---|
| G-AC-001–010 | PASS | IA, filtrage, comptes, mot de passe temporaire et nettoyage |
| G-AC-011–016 | PASS | Permissions réelles, aucun éditeur fictif, settings classés, devise dynamique |
| G-AC-017–022 | PASS | UI backup/restore; moteur inchangé |
| G-AC-023–024 | PASS | Dettes reauth et chiffrement documentées |
| G-AC-025–027 | PASS | Diagnostics minimisés; audit sans fausses données |
| G-AC-028 | PARTIAL | Actor présent; authorizer non stocké/exposé |
| G-AC-029–043 | PASS | Read-only audit, Danger Zone, E.5 préservé, dettes explicites |
| G-AC-044–047 | NOT APPLICABLE | Structure responsive validée; rendu non observable |
| G-AC-048–050 | PASS | Cibles, primitives clavier/focus |
| G-AC-051–054 | PARTIAL | Tokens/CSS validés; rendu non observable |
| G-AC-055–058 | PASS | FR/EN, aucun SQL/IPC/dépendance |
| G-AC-059–063 | PASS | 121/121, sécurité et F inclus, 0 skip |
| G-AC-064–068 | PASS | lint, build, diff, startup, version 2.0.1 |
| G-AC-069 | PASS | Limitation visuelle rapportée honnêtement |
| G-AC-070 | PASS | Phase H non commencée |

## 66. Deviations

Le rendu manuel demandé est impossible car l’outil Windows retourne `apps: []`. Le portail Administration reste volontairement sous SETTINGS:READ pour préserver le contrat de navigation historique testé.

## 67. Security debt

Élévation temporaire Manager-like, file picker authentifié générique, seconde authentification avant restore, audit authorizer et chiffrement backup.

## 68. Functional debt

Éditeur de permissions personnalisées et lecteur d’audit détaillé nécessitent de nouveaux contrats backend. Aucun n’a été simulé.

## 69. UX debt

Inspection visuelle et tests manuels multi-résolutions restent à réaliser dès qu’une fenêtre Windows est observable.

## 70. Tooling limitations

Computer Use: `apps: []`, `browsers: []`. Vite/Vitest nécessitent une exécution hors sandbox sur cette machine à cause de `spawn EPERM`.

## 71. Files intentionally untouched

Moteur backup/restore/reset, schéma SQLite, registre IPC, preload allowlist et règles RBAC.

## 72. Recommendation for Phase H

Priorité recommandée: Release readiness et reliability hardening, incluant test visuel réel, parcours restore isolé, documentation opérateur hors ligne et décision explicite sur l’audit Owner. Ne pas démarrer sans autorisation humaine.
