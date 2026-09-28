# STORE 3.0 — PHASE C IMPLEMENTATION REPORT

## 1. Executive summary

Phase C installe le shell tablet-first STORE 3.0 autour des pages 2.0.1 sans
modifier leur logique métier. L'écran intermédiaire Caisse/Dashboard disparaît :
Owner et Manager arrivent sur Accueil, Employee sur Caisse. La navigation est
centralisée, réversible, responsive, accessible et recalculée lors de la fin
d'une élévation temporaire. Phase D n'a pas commencé.

## 2. Repository state before Phase C

Baseline validée : version `2.0.1`, 18 fichiers/53 tests, lint et build passants.
Le worktree était déjà fortement modifié par les phases antérieures, notamment
dans backend, preload, build et pages métier. Ces changements préexistants ont
été conservés.

## 3. Navigation/session data discovered

Le résultat de login exposé au renderer est un `User` (identité et rôle). Le
backend maintient séparément `role`, `accessRole`, `permissions`,
`authorizedById` et `elevatedUntil` dans une session privée au main process.

## 4. effectivePermissions availability

`effectivePermissions` n'est pas exposé au renderer. Phase C utilise donc un
adapter frontend conservateur limité aux parcours déjà disponibles en 2.0.1.
Cet adapter n'est pas une autorité : tous les contrôles backend restent actifs.

## 5. Files added

- `frontend/src/components/Layout/StoreShell.tsx`
- `frontend/src/navigation/navigation.ts`
- `frontend/src/design-system/shell.css`
- `tests/unit/frontend/navigation.test.ts`
- `docs/PHASE_C_IMPLEMENTATION_REPORT.md`

## 6. Files modified

- `frontend/src/App.tsx`
- `frontend/src/pages/Dashboard/DashboardPage.tsx`
- `frontend/src/index.tsx`
- `frontend/src/i18n/i18n.ts`
- `docs/DESIGN_SYSTEM.md`

## 7. Dependencies

Aucune dépendance ajoutée. Lucide et les primitives Phase B sont réutilisés.

## 8. Shell architecture

`AppShell` contient un `ShellHeader`, un `ShellSidebar` et un unique contenu
principal. Les pages legacy restent montées dans cette frontière sans réécriture
métier.

## 9. Navigation architecture

Un état `AppDestination` et une pile d'historique remplacent les états de vues
dispersés. La navigation principale et les vues transversales Chat/Aide utilisent
la même source, sans React Router.

## 10. Owner navigation

Accueil, Caisse, Produits, Équipe, Rapports, Messages, Administration. Les
opérations dangereuses restent dans les pages secondaires de Paramètres.

## 11. Manager navigation

Accueil, Caisse, Produits, Équipe, Rapports, Messages. Administration est absente.

## 12. Employee navigation

Caisse, Messages et Aide. Produits/Stock ne sont pas exposés car la page legacy
contient encore des contrôles d'édition et le renderer ne possède pas les
permissions effectives permettant une présentation READ-only sûre.

## 13. Default-home behavior

Owner/Manager : Accueil. Employee : Caisse. En cas de perte d'accès, la première
destination sûre correspondante est restaurée sans boucle.

## 14. Sidebar expanded

Icône, label, groupes conceptuels, état actif multi-indices et cibles tactiles.

## 15. Sidebar compact

Rail à icônes avec `aria-label`, `title`, état actif non fondé uniquement sur la
couleur et commande explicite développer/réduire.

## 16. Portrait navigation

Sous 800 px (`50rem`), la sidebar fixe disparaît et un Drawer Phase B prend le
relais. Sélection, Escape, clic extérieur et restauration du focus sont hérités
de la primitive testée.

## 17. Header

Titre courant prioritaire, retour contextuel, état local utile, élévation,
accès Chat et menu utilisateur. Le header se compacte aux petits formats.

## 18. User/session menu

Identité, rôle, Aide, langue, thème, demande/quittement d'élévation et déconnexion
sont regroupés. La déconnexion est séparée visuellement des actions métier.

## 19. Global statuses

L'absence d'Internet est présentée comme « opérations locales disponibles », pas
comme une panne globale. Aucun faux statut de caisse n'a été ajouté.

## 20. Temporary elevation UI

Badge de sécurité temporaire avec compte à rebours, auteur connu depuis le login
d'élévation et détail dans le menu utilisateur.

## 21. Elevation expiry behavior

À expiration ou abandon, le profil initial est restauré, la navigation est
recalculée et une destination devenue interdite est remplacée par le Home sûr.
L'expiration affichée est synchronisée au délai frontend existant ; le backend
reste l'autorité effective.

## 22. Back/context behavior

Une pile centrale restaure l'origine de Chat, Aide et de l'historique des ventes.
Les entrées devenues indisponibles sont ignorées lors du retour.

## 23. Responsive behavior

Desktop : sidebar étendue. 1024–1088 px : rail compact automatique. 800 px et
moins : drawer. Le contenu principal conserve la priorité et son propre scroll.

## 24. Touch behavior

Les contrôles du shell utilisent au minimum `--touch-target-min` (44 px).

## 25. Keyboard behavior

Boutons natifs, navigation Tab/Shift+Tab/Enter/Space, Escape dans Drawer/menu,
focus visible et focus déplacé vers le contenu après changement de destination.

## 26. Accessibility

Skip link, `header`, `nav`, `main`, nom de navigation, `aria-current="page"`,
labels du mode compact, menu et drawer accessibles.

## 27. Light/dark/high-contrast

Uniquement des tokens Phase B. Les états actifs ont un indicateur structurel ;
`prefers-contrast: more` et `prefers-reduced-motion` sont couverts.

## 28. i18n

Tous les nouveaux libellés existent en français et en anglais.

## 29. Legacy-page integration

Caisse, Overview, produits, employés, rapports, messagerie, paramètres, aide et
historique sont hébergés sans modification de leur workflow métier.

## 30. Tests added

9 tests de navigation/shell : homes, profils, Administration Owner-only,
destinations secondaires, historique, i18n, breakpoints, contraste/mouvement,
skip link et landmarks.

## 31. Full test result

PASS — 19 fichiers, 62 tests.

## 32. Lint result

PASS — zéro avertissement.

## 33. Build result

PASS — Vite 8.1.5, JS 336.18 kB (gzip 101.52 kB), CSS 64.30 kB
(gzip 13.38 kB).

## 34. Electron startup result

PASS technique — `npm start` a lancé Electron et le processus est resté actif
sans erreur console. Le contrôleur visuel n'a retourné aucune fenêtre exploitable.

## 35. Manual smoke test

PARTIAL — lancement réel vérifié. Login/navigation/logout par rôle non exécutés :
aucune fenêtre n'était observable par l'outil UI et aucun identifiant n'a été
inventé ou réinitialisé.

## 36. Responsive manual checks

- 1920×1080 : NOT TESTED visuellement ; règles structurelles présentes.
- 1366×768 : NOT TESTED visuellement ; règles structurelles présentes.
- 1280×800 : NOT TESTED visuellement ; règles structurelles présentes.
- 1024×768 : NOT TESTED visuellement ; rail compact couvert statiquement.
- 800×1280 : NOT TESTED visuellement ; drawer couvert statiquement.

## 37. Acceptance criteria matrix

| Critère | Statut | Preuve |
|---|---|---|
| C-AC-001 | PASS | Shell utilisé par toutes les zones authentifiées |
| C-AC-002 | PASS | Primitives/tokens Phase B |
| C-AC-003 | PASS | Sidebar étendue implémentée/test structurel |
| C-AC-004 | PASS | Compact manuel + automatique |
| C-AC-005 | PARTIAL | Drawer implémenté, contrôle visuel indisponible |
| C-AC-006 | PARTIAL | Profil Owner testé, permissions effectives absentes |
| C-AC-007 | PASS | Administration absente du profil Manager, test |
| C-AC-008 | PASS | Home Employee = POS, test |
| C-AC-009 | PARTIAL | Navigation sûre ; Produits/Stock différés |
| C-AC-010 | PASS | Adapter UI non autoritaire, backend inchangé |
| C-AC-011 | PASS | Contrôles backend inchangés par Phase C |
| C-AC-012 | PASS | Badge, détail, auteur et compte à rebours |
| C-AC-013 | PASS | Recalcul et redirection à expiration/drop |
| C-AC-014 | PARTIAL | Rail/header CSS validés, pas de preuve visuelle |
| C-AC-015 | PARTIAL | Drawer CSS validé, pas de preuve visuelle |
| C-AC-016 | PASS | Aucun IPC ajouté |
| C-AC-017 | PASS | Aucune migration ajoutée |
| C-AC-018 | PASS | Aucun workflow métier modifié |
| C-AC-019 | PASS | Tokens light |
| C-AC-020 | PASS | Tokens dark |
| C-AC-021 | PASS | Règle high contrast + test |
| C-AC-022 | PASS | Reduced motion + test |
| C-AC-023 | PASS | Cibles 44 px + test |
| C-AC-024 | PARTIAL | Sémantique clavier présente, smoke indisponible |
| C-AC-025 | PASS | Skip link + test |
| C-AC-026 | PASS | Landmarks + test |
| C-AC-027 | PARTIAL | Historique central, pas de smoke UI |
| C-AC-028 | PASS | Navigation créée seulement après login résolu |
| C-AC-029 | PASS | FR/EN |
| C-AC-030 | PASS | Pages legacy montées dans le shell |
| C-AC-031 | PASS | 62/62 tests |
| C-AC-032 | PASS | Lint |
| C-AC-033 | PASS | Build |
| C-AC-034 | PASS | Processus Electron lancé sans erreur |
| C-AC-035 | PASS | Version 2.0.1 inchangée |
| C-AC-036 | PASS | Aucun fichier backend touché par Phase C |
| C-AC-037 | PASS | Aucune dépendance ajoutée |
| C-AC-038 | PASS | Smoke documenté honnêtement |
| C-AC-039 | PASS | Cinq formats documentés NOT TESTED |
| C-AC-040 | PASS | Phase D non commencée |

## 38. Deviations

Stock, Achats et Permissions n'apparaissent pas comme destinations autonomes :
aucun écran frontend sûr correspondant n'existe. Employee ne reçoit pas encore
Produits/Stock, car la page actuelle mélange lecture et édition.

## 39. Known limitations

La navigation reflète les profils legacy, pas des permissions personnalisées.
Le statut de caisse n'est pas global car son état n'est pas exposé au shell.
Les badges messages/alertes séparés nécessitent une donnée globale sûre.

## 40. Security blockers discovered

**SECURITY BLOCKER**

- Current architecture: permissions effectives privées dans `ipcHandlers`.
- Missing capability: snapshot renderer de session/effective permissions,
  auteur et expiration d'élévation.
- Why frontend-only workaround is unsafe: dupliquer la matrice divergerait du
  backend et pourrait afficher des fonctions interdites.
- Minimal future backend/API change: canal read-only authentifié `sessionInfo`
  retournant identité, permissions effectives, élévation et expiration.
- Required permission model: codes module/action déjà existants, sérialisés sans
  secrets, recalculés côté backend.
- Tests required: absence de fuite avant login, profils personnalisés, drop et
  expiration atomiques, navigation devenue interdite.
- Risk if deferred: navigation conservatrice et fonctionnalités sûres masquées ;
  le backend reste protégé.

## 41. Technical debt deferred

Vue Products READ-only Employee, écrans Stock/Achats dédiés, session snapshot,
badges globaux Messages/Alertes, statut caisse et smoke responsive automatisé.

## 42. Files intentionally not modified

Backend, SQLite, schéma, migrations, services métier, IPC, preload,
electron-builder et version. Ils présentent des changements préexistants dans le
worktree, mais aucun n'a été effectué pendant Phase C.

## 43. Recommendation for Phase D

Le shell est structurellement prêt pour Tablet POS. Avant diffusion, effectuer
un smoke visuel humain aux cinq résolutions et par trois rôles. Phase D peut se
concentrer sur le catalogue, la recherche, le panier et le checkout sans
reconstruire le shell, sous réserve d'acceptation des limitations ci-dessus.
