# STORE 3.0 — Rapport d’implémentation de la phase J.3

## 1. Executive summary

La présence est désormais un domaine explicite et indépendant : se connecter ne pointe pas une entrée, se déconnecter ne pointe pas une sortie. La fiche de présence multi-utilisateur permet de pointer maintenant avec le mot de passe de la personne concernée. L’heure et l’identité sont vérifiées côté backend. Les données historiques sont conservées.

Invariants documentés :

- Login does not create attendance.
- Logout does not close attendance.
- Attendance is explicit.
- Attendance uses credential confirmation.
- Backend time is authoritative.
- Historical records are preserved.

Statut : **J.3 COMPLETE — AUTOMATED VALIDATION PASSED, VISUAL REVIEW REQUIRED**.

STORE demeure **NOT READY — RELEASE BLOCKERS REMAIN**.

## 2. Pre-J.3 baseline

Référence J.2 : 33 fichiers, 159/159 tests, aucun test ignoré, ESLint/build/diff/audit/smoke et intégrité SQLite/FK valides, version 2.0.1.

## 3. Attendance audit findings

L’audit a trouvé des mutations de présence lors de la connexion, déconnexion, expiration d’inactivité, récupération au démarrage et fermeture de l’application. Les statuts et historiques reposaient sur `attendances`; la correction existait déjà avec valeurs originales, raison et permission dédiée.

## 4. Legacy login/attendance coupling

Les appels implicites ont été retirés de `ipcHandlers.ts`, `storeDatabase.ts` et `main/index.ts`. Les anciennes lignes restent inchangées et lisibles, y compris celles dont la source est `AUTHENTICATION`.

## 5. Files changed

Périmètre propre à J.3 :

- `backend/src/domain/attendance/attendancePolicy.ts`
- `backend/src/domain/attendance/explicitAttendance.ts`
- `backend/src/database/storeDatabase.ts`
- `backend/src/main/ipcHandlers.ts`
- `backend/src/main/index.ts`
- `backend/src/ipc/channels.ts`
- `backend/src/domain/rbac/ipcPermissions.ts`
- `backend/src/preload/index.cts`
- `frontend/src/services/attendanceService.ts`
- `frontend/src/pages/Dashboard/employees/PresencePage.tsx`
- `frontend/src/pages/Dashboard/employees/TeamPage.tsx`
- `frontend/src/App.tsx`
- `frontend/src/design-system/operations.css`
- `frontend/src/i18n/i18n.ts`
- tests J.3, architecture, guide utilisateur et présent rapport.

Les autres changements visibles dans le worktree proviennent des phases antérieures et ont été conservés.

## 6. Schema status

**NO SCHEMA CHANGE.** Le schéma actuel représente déjà les entrées ouvertes/fermées, la source, les corrections et les valeurs originales. Aucune migration destructive ou additive J.3 n’a été créée.

## 7. Explicit attendance architecture

Le renderer demande une action sémantique `CLOCK_IN` ou `CLOCK_OUT`. Le backend vérifie le compte, l’employé, le secret et la transition, puis écrit dans une transaction SQLite avec `source = EXPLICIT` et sans rattachement artificiel à une session de présence.

## 8. Credential verification architecture

La vérification réutilise bcrypt et la politique existante de verrouillage/tentatives. Le compte qui possède le mot de passe doit être exactement le sujet sélectionné. Un compte inconnu, désactivé ou un secret appartenant à une autre personne est refusé.

## 9. Backend authoritative time

Le backend injecte `now()` dans l’opération. Les champs renderer `timestamp`, `startTime` et `endTime` sont interdits pour le pointage normal. Le libre-service passé/futur est donc impossible.

## 10. Attendance state machine

Transitions valides : `ABSENT → CLOCK_IN → PRESENT` puis `PRESENT → CLOCK_OUT → COMPLETED/ABSENT`. Une seconde entrée ouverte et une sortie sans entrée ouverte sont refusées côté backend.

## 11. Double-submit/concurrency handling

Le dialogue verrouille l’envoi pendant la requête. Le backend sérialise les mutations via la file DB et revalide l’état dans une transaction, ce qui conserve une seule présence ouverte par personne.

## 12. Multi-user attendance

Le sujet de présence est distinct de l’opérateur de la session STORE. Plusieurs employés et managers peuvent donc avoir simultanément une ligne ouverte, sans transfert d’identité UI.

## 13. Daily attendance sheet

La vue principale est une fiche du jour : personne, rôle, entrée, sortie, durée, état et action autorisée. Une présence ouverte est indiquée comme en cours sans durée finale fabriquée.

## 14. Filters

La fiche offre date, personne, rôle et état. L’historique offre période, personne, rôle et état. Tous correspondent à des données persistées ou à un état dérivé des lignes réelles.

## 15. Historical attendance

L’historique lit les enregistrements existants sans réécriture, suppression ni réinterprétation. Le calcul de durée existant est conservé.

## 16. Historical corrections

La correction est séparée du pointage normal, montre les valeurs originales, exige une raison et refuse les dates futures. Elle reste une opération privilégiée.

## 17. Permission model

La consultation et le pointage passent par `PRESENCE:READ`; la correction exige `PRESENCE:UPDATE`. Les décisions sont prises dans le registre IPC/backend, pas par un nom de rôle dans React.

## 18. Login decoupling

Une authentification réussie crée uniquement une session applicative. Aucun enregistrement de présence n’est ajouté.

## 19. Logout decoupling

La déconnexion détruit uniquement la session applicative. Elle ne ferme aucune présence.

## 20. Restart/recovery behavior

La récupération automatique qui clôturait les présences au démarrage a été supprimée. Une présence explicitement ouverte survit à une réouverture de la base/application et reste dérivée des données persistées.

## 21. Session/elevation intermediate behavior

Les sessions, l’élévation temporaire et leurs délais demeurent en place pour J.4. La signature de présence n’accorde aucune permission élevée.

## 22. Inactivity intermediate behavior

Le timeout d’inactivité reste opérationnel, mais sa fermeture de session n’engendre plus de sortie de présence.

## 23. Cash separation

Aucun comportement de caisse, propriétaire de session de caisse ou partage de caisse n’a été modifié. Pointer n’ouvre, ne ferme et ne transfère pas la caisse.

## 24. POS separation

L’attribution des ventes demeure inchangée. Sujet de présence, opérateur STORE et acteur de vente restent des concepts distincts.

## 25. IPC changes

L’ancien canal générique a été remplacé par `clockAttendance`, `attendanceSheet` et `attendanceHistory`. Le handler lie `facilitatedBy` à la session backend et ne remplace jamais le sujet demandé par l’opérateur courant.

## 26. Preload changes

La liste blanche stricte expose uniquement les trois capacités sémantiques nécessaires. Aucun accès DB générique ni `ipcRenderer` brut n’est exposé.

## 27. Security analysis

L’autorité reste backend : permission de session, état du compte, propriété du secret, transition et horodatage. Les IPC inconnus restent refusés par défaut et les erreurs publiques restent contrôlées.

## 28. Credential-secret handling

Le mot de passe vit uniquement dans l’état mémoire court du dialogue et est effacé à la fermeture, l’annulation, l’échec, le succès ou le changement de sujet. Il n’entre ni dans une ligne de présence, ni dans l’audit, ni dans le stockage navigateur, ni dans l’URL.

## 29. Tests added

Ajout de `explicitAttendanceJ3.test.ts` et `attendanceDecouplingJ3.test.ts`, extension des tests de politique et adaptation du scénario d’opérations quotidiennes.

## 30. Login/logout decoupling tests

Les tests de source prouvent l’absence d’appel de présence dans login/logout/inactivité et l’absence de fermeture/récupération lifecycle. Les tests d’intégration prouvent que la présence explicite persiste après réouverture.

## 31. Multi-user tests

Scénario couvert : A, B et Manager C entrent; B sort; A et C restent ouverts et B est correctement fermé.

## 32. Historical fixture tests

Une ligne historique `AUTHENTICATION` est conservée, relue et non modifiée pendant les opérations explicites et la réouverture isolée.

## 33. Correction tests

Les tests existants d’autorisation sont conservés; la validation refuse désormais aussi une entrée ou une sortie future. La correction reste attachée à l’acteur de session autorisé.

## 34. Security tests

Cas couverts : mauvais secret, secret d’un autre utilisateur, compte désactivé, timestamps fournis par le renderer, transition invalide, permissions IPC, acteur backend et absence du secret dans les lignes persistées.

## 35. J.2 regression

Les suites médias article, sauvegarde `.store-backup` v2/legacy, réinitialisations manuelle/automatique et protections Primary Owner passent inchangées.

## 36. Full regression

Résultat final : **35 fichiers, 168/168 tests PASS, 0 skipped**. Le seuil historique de 159 est dépassé sans suppression de couverture.

## 37. SQLite integrity

PASS sur le profil temporaire d’intégration J.3 après pointages, historique et réouverture.

## 38. Foreign-key integrity

PASS sur le même profil isolé; aucun lien orphelin détecté.

## 39. ESLint

PASS avec `eslint . --max-warnings=0`.

## 40. TypeScript/Vite

PASS : `tsc -b` puis build Vite, 1984 modules transformés.

## 41. git diff --check

PASS, aucune erreur d’espace ou marqueur conflictuel.

## 42. Production dependency audit

PASS : `npm audit --omit=dev` retourne **0 vulnerabilities**. Aucune dépendance J.3 ajoutée.

## 43. Electron smoke test

PASS : Electron démarre, reste actif sans exception fatale, puis est arrêté proprement après le contrôle.

## 44. Responsive status

La structure CSS prévoit une grille lisible desktop/tablette paysage et une représentation liste/carte sous 56.25rem, avec en-tête stable, corps défilant et sans page large forcée. Les tailles 1920×1080, 1366×768, 1280×800, 1024×768 et 800×1280 restent à confirmer visuellement.

## 45. Accessibility status

Libellés, boutons réels, dialogue, états textuels, erreurs, verrouillage d’envoi, styles de focus, contraste élevé et réduction des animations utilisent les fondations existantes. La navigation clavier et la restauration effective du focus exigent encore la revue humaine.

## 46. Visual validation status

**AUTOMATED VISUAL VALIDATION: NOT OBSERVABLE.** Le canal de contrôle Windows a retourné zéro application et zéro fenêtre contrôlable. Aucune conformité visuelle n’est revendiquée sur cette base.

## 47. Human visual checklist

- Vérifier plusieurs personnes visibles simultanément.
- Vérifier la clarté des actions Entrée/Sortie et de la présence ouverte.
- Vérifier lisibilité des durées, rôles et états sans dépendre de la couleur.
- Ouvrir/fermer le dialogue de signature; tester mauvais secret, succès et double clic.
- Tester date, période, personne, rôle et état, puis l’historique.
- Vérifier que la correction est visuellement distincte et réservée aux permissions adéquates.
- Tester 1920×1080, 1366×768, 1280×800, 1024×768 et surtout 800×1280.
- Vérifier absence de débordement horizontal et adaptation portrait.
- Parcourir tout le flux au clavier, focus visible, retour du focus après dialogue.
- Examiner clair, sombre, contraste élevé et réduction des animations.

## 48. Known limitations

La table `employees` autorise techniquement un `user_id` nul, mais l’interface de présence J.3 n’expose que les sujets actifs disposant d’un compte et d’un secret. Aucun PIN ou mécanisme d’identité alternatif n’a été inventé. Les éventuels employés historiques sans compte ne peuvent donc pas utiliser le pointage normal et nécessitent une décision métier ultérieure avant exposition.

## 49. Deferred J.4 work

Sont explicitement différés : changement d’utilisateur, refonte de session/authentification, suppression de l’élévation et de son compte à rebours, refonte du timeout, règles de logout avec caisse ouverte, propriété/partage de caisse et attribution POS.

## 50. Version confirmation

Version conservée : **2.0.1** dans `package.json` et `package-lock.json`. Aucun installer, aucune release et aucun passage à 3.0.0.

## 51. Readiness for J.4

Le domaine présence est découplé et toutes les validations automatisées J.3 passent. La revue visuelle humaine demeure obligatoire avant d’accepter totalement l’UX. J.4 n’a pas été commencé.

Classification finale : **J.3 COMPLETE — AUTOMATED VALIDATION PASSED, VISUAL REVIEW REQUIRED**.

État produit : **STORE NOT READY — RELEASE BLOCKERS REMAIN**.
