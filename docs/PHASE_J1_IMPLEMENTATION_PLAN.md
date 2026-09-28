# STORE 3.0 — PHASE J.1 SAFE UX & INFORMATION ARCHITECTURE REMEDIATION

## 1. Statut du plan

Plan exact prêt pour validation. **Aucune implémentation J.1 n'est réalisée dans cette étape.** Version `2.0.1`, aucune release.

## 2. Objectif

Corriger les observations humaines strictement frontend et informationnelles sans modifier authentification, session backend, présence, caisse, base, migrations, médias, backup ou contrats métier.

## 3. Frontières impératives

J.1 peut modifier : composants React, navigation frontend, i18n, styles/design system et tests frontend/documents associés.

J.1 ne doit pas modifier :

- `backend/src/**` ;
- `backend/src/database/schema.ts` ou migrations ;
- preload, canaux IPC ou permissions ;
- modèle session/elevation/timeout ;
- attendance ou caisse ;
- backup/restore ;
- package version/dépendances ;
- transactions ventes, achats ou stock.

L'élévation et le timeout restent visibles/fonctionnels jusqu'à J.4 ; J.1 ne doit pas les masquer partiellement.

## 4. Lot J1-0 — Baseline et garde-fous

1. Capturer branche, commit, status, version et baseline 137 tests.
2. Ajouter/adapter les tests frontend avant suppression des destinations ou changements structurants.
3. Vérifier que toute modification reste hors fichiers interdits.
4. Conserver les données et artefacts release intacts.

Gate : baseline verte et diff de portée documenté.

## 5. Lot J1-1 — Glossaire UX canonique

Produire puis appliquer le tableau minimal :

| Concept technique | FR canonique | EN canonique |
| --- | --- | --- |
| product | Article | Item |
| stock | Stock | Stock |
| stock movement | Mouvement de stock | Stock movement |
| purchase | Achat | Purchase |
| supplier | Fournisseur | Supplier |
| inventory | Inventaire | Inventory |
| employee | Employé | Employee |
| user | Utilisateur | User |
| account | Compte | Account |
| attendance | Présence | Attendance |
| clock-in | Entrée | Clock-in |
| clock-out | Sortie | Clock-out |
| cash | Caisse | Cash register |
| cash session | Session de caisse | Cash session |
| sale | Vente | Sale |
| invoice | Facture | Invoice |
| report | Rapport | Report |
| message | Message | Message |
| backup | Sauvegarde | Backup |
| restore | Restauration | Restore |

Actions : normaliser toutes les clés user-facing liées à Product/Article ; conserver noms internes `product`, DTO, services et tests techniques ; contrôler navigation, formulaires, stock, achats, rapports, factures, états vides, confirmations et guides.

Gate : aucune occurrence française UX de « Produit(s) » pour le concept Article, aucune coexistence anglaise Item/Product sur une même surface ; interpolation métier toujours correcte.

## 6. Lot J1-2 — Navigation et Help

1. Retirer `help` des items de navigation principale et du groupe système.
2. Conserver « Guide d'utilisation » dans le menu utilisateur comme point canonique.
3. Garder Help comme destination secondaire interne si cela simplifie la compatibilité, mais pas comme entrée principale.
4. Remplacer le fallback `defaultDestination(...)=help` par la première destination autorisée et un fallback neutre explicitement testé.
5. Déplacer le toggle expand/collapse desktop en haut à gauche, près de la marque/titre ; conserver le bouton drawer mobile existant.
6. Préserver labels accessibles, focus visible, clavier, taille tactile, compact et portrait.

Fichiers probables : `navigation/navigation.ts`, `StoreShell.tsx`, `App.tsx`, `design-system/shell.css`, tests navigation/permissions.

Gate : Help absent de la navigation principale pour tous les rôles, guide accessible depuis menu user, aucun dead-end de navigation.

## 7. Lot J1-3 — Titre, Back et informations triviales

1. Retirer visuellement `Page courante` au-dessus du titre sans perdre le `<h1>` ni les landmarks.
2. Conserver Back comme contrôle réversible ; utiliser icon-only avec label accessible si le texte est redondant.
3. Vérifier qu'aucun pictogramme ne masque le titre et que les libellés longs FR/EN restent ellipsés ou wrap correctement.

Gate : navigation toujours réversible, titre courant annoncé, aucun texte redondant visible.

## 8. Lot J1-4 — Quick Chat Drawer

1. Remplacer `navigate('chat')` depuis l'en-tête par un état overlay local du shell/app.
2. Ouvrir un Drawer droit conservant l'espace de travail courant et son historique.
3. Réutiliser MailboxPage `variant='chat'` en mode conversation compacte.
4. Afficher sender, heure et corps en bulles multiuser, avec distinction textuelle/alignement en plus de la couleur.
5. Garder compose et consultation ; retirer delete uniquement du quick chat.
6. Conserver delete dans Messages et son autorisation backend actuelle.
7. Gérer focus trap, Escape, backdrop, fermeture, scroll conversation et retour du focus au bouton déclencheur.

Fichiers probables : `App.tsx`, `StoreShell.tsx`, `MailboxPage.tsx`, design-system overlays/styles et tests navigation/messaging.

Gate : ouvrir/fermer le chat ne change pas `destination`, Back ne quitte pas la page courante, aucune suppression visible dans le drawer.

## 9. Lot J1-5 — Workflows canoniques et duplications

1. Cartographier les anciens `Header`/`Sidebar` et confirmer leurs consommateurs avant suppression ou conservation.
2. Faire converger les points d'entrée vers les mêmes composants Help, Messages et Chat.
3. Supprimer uniquement les implémentations mortes prouvées ; ne pas supprimer une capacité backend.
4. Préserver les raccourcis utiles s'ils ouvrent le workflow canonique.

Gate : aucune double implémentation incohérente, aucun import mort, tests navigation verts.

## 10. Lot J1-6 — Hiérarchie de boutons

1. Inventorier les `<button>` directs dans les écrans J.1.
2. Utiliser Button/IconButton et variants Primary, Secondary, Ghost/Tertiary, Danger, Icon-only.
3. Une action primaire dominante par contexte/modal.
4. Destructive toujours distincte et jamais réduite à la couleur seule.
5. Supprimer emoji fonctionnels résiduels au profit de Lucide + label.

Gate : variants cohérents, labels accessibles, aucun bouton destructive ambigu.

## 11. Lot J1-7 — Entity details et densité

1. Utiliser les fiches structurées existantes pour un article, utilisateur, fournisseur, achat ou inventaire individuel.
2. Conserver listes/tables pour comparer plusieurs entités.
3. Prioriser action principale, données décisionnelles, informations secondaires puis métadonnées.
4. Ne pas ajouter de données que les services existants ne fournissent pas.

Gate : aucune fausse information, détail individuel lisible en portrait, comparaison multi-entités préservée.

## 12. Lot J1-8 — Scroll, proportions et géométrie

1. Définir une matrice par écran : page, zone scrollable, zone stable, action primaire.
2. Préserver catalogue défilant + panier stable sur paysage ; portrait suit le drawer/panneau existant.
3. Garder filtres/actions de listes visibles seulement si cela ne crée pas un scroll imbriqué.
4. Normaliser padding/gap/radius avec les tokens existants.
5. Supprimer les largeurs rigides/overflow historiques responsables de superposition.

Gates obligatoires : NO unintended overflow, NO superposition, NO clipped primary action, NO accidental horizontal page scroll.

## 13. Lot J1-9 — Responsive et accessibilité

Valider les surfaces J.1 à : 1920×1080, 1366×768, 1280×800, 1024×768 et 800×1280, priorité aux trois dernières.

Vérifier : navigation expanded/compact/drawer ; Help user menu ; chat drawer ; messages ; détails entités ; modales ; tableaux/listes ; light/dark/high contrast ; reduced motion ; Tab/Shift+Tab/Escape/Enter ; cibles tactiles ; zoom ; libellés FR/EN.

La validation automatisée ne ferme pas le gate visuel : résultats finaux doivent être USER-OBSERVED ou CODEX-OBSERVED réels.

## 14. Tests à créer ou adapter

- glossaire : clés FR/EN canoniques et absence de termes interdits dans les surfaces ciblées ;
- navigation : Help absent de `navigationFor`, guide secondaire présent, fallback valide ;
- shell : toggle upper-left, aria-label, Back préservé, pas de `currentPage` visible ;
- chat : Drawer droit, destination inchangée, delete absent en variant chat et présent en management ;
- boutons : variants/labels destructifs ;
- responsive : assertions de structure/classes, overflow contract et breakpoints ;
- tests existants Phase C/F/G/I adaptés sans réduire leur portée.

## 15. Régression J.1

Après modifications : clean dependency state si nécessaire, full tests, 0 skipped, ESLint zéro warning, TypeScript/Vite, `git diff --check`. Aucun package/release requis avant que l'UI source soit validée, sauf instruction ultérieure.

Tests sécurité ciblés obligatoires malgré portée frontend : unknown IPC deny, preload strict, permissions effectives et identité acteur doivent rester verts.

## 16. Critères d'acceptation J.1

1. Glossaire approuvé appliqué sans renommage technique.
2. Help absent de navigation primaire et guide accessible via menu user.
3. Toggle stable en haut à gauche, clavier/tactile/portrait.
4. Libellés triviaux visuellement retirés, accessibilité conservée.
5. Quick chat Drawer droit, travail courant conservé, aucune suppression.
6. Messages conserve la suppression autorisée.
7. Aucun workflow dupliqué incohérent.
8. Hiérarchie boutons cohérente.
9. Entity details structurés, tables réservées à la comparaison.
10. Scroll/proportions/espacements conformes.
11. Aucun overflow, chevauchement ou action principale coupée aux cinq résolutions.
12. Auth/session/elevation/attendance/cash/DB/backup strictement inchangés.
13. Version toujours `2.0.1`, aucun artefact publié.
14. Suite complète verte et nouvelle validation humaine demandée.

## 17. STOP conditions J.1

STOP si une correction requiert : nouveau canal IPC, mutation backend, migration DB, contournement de permission, modification elevation/timeout, modification attendance/cash, changement backup/media, ou règle métier non couverte.

Tout besoin de ce type est reporté vers J.2–J.5 avec preuve du fichier et de l'invariant concerné.

## 18. Ordre d'exécution exact

```text
J1-0 Baseline/tests
  → J1-1 Glossaire
  → J1-2 Navigation/Help
  → J1-3 Titre/Back
  → J1-4 Quick Chat Drawer
  → J1-5 Duplications
  → J1-6 Boutons
  → J1-7 Entity details
  → J1-8 Scroll/géométrie
  → J1-9 Responsive/accessibilité
  → Régression complète
  → Rapport J.1
  → Validation humaine
```

## 19. Autorisation

Ce document est un plan, pas une autorisation implicite d'implémentation. Attendre l'instruction explicite de démarrer J.1.
