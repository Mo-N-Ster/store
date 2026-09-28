# STORE 3.0 — HUMAN VALIDATION REMEDIATION AUDIT

Date : 25 septembre 2026
Source : rapport d'observation humaine fourni après Phase I.1
Portée : audit uniquement — aucune implémentation fonctionnelle, aucun changement de schéma, aucune release

## 1. Résultat exécutif

Le rapport humain est cohérent avec le code observé et maintient correctement STORE au statut **NOT READY — RELEASE BLOCKERS REMAIN**. RB-01 et RB-06 restent ouverts : il s'agit d'observations d'échec/amélioration, pas d'une validation visuelle réussie.

Les demandes ne forment pas un seul lot sûr. Elles se répartissent ainsi :

- 12 changements UI sûrs ou consolidations de composants existants ;
- 8 extensions fonctionnelles nécessitant un contrat IPC ou une persistance ;
- 6 changements d'architecture/sécurité autour de session, identité, présence et caisse ;
- 5 décisions métier bloquantes avant toute modification de ces invariants.

La suppression de l'auto-lock et de l'accès temporaire élevé ne doit surtout pas être traitée comme un masquage visuel. Le backend expire actuellement la session après 30 minutes et l'élévation remplace les permissions de la session tout en conservant l'identité primaire de l'employé. Le remplacement par un changement réel d'utilisateur affecte présence, propriétaire de caisse, acteur des ventes, panier et audit.

## 2. Périmètre inspecté

L'audit couvre notamment :

- `frontend/src/App.tsx` : session, timeout, navigation et élévation ;
- `frontend/src/components/Layout/StoreShell.tsx` : shell, menu, Help, chat, libellés et compteur élevé ;
- `frontend/src/navigation/navigation.ts` : destinations, permissions et fallback Help ;
- Products, Stock, Purchases, Reports, Presence, Mailbox et PasswordResetDialog ;
- preload strict, canaux IPC et matrice de permissions ;
- `backend/src/main/ipcHandlers.ts` : autorité de session, identité acteur, timeout et élévation ;
- `backend/src/database/storeDatabase.ts` : présence, caisse, ventes, stock, achats, messages et mots de passe ;
- schéma SQLite et migrations ;
- design system, responsive CSS et tests Phase C–I.1.

## 3. Échelle de complexité

- **S** : modification locale, sans contrat backend.
- **M** : plusieurs écrans/composants ou adaptation de tests, sans invariant métier nouveau.
- **L** : nouveau workflow, IPC ou migration compatible.
- **XL** : identité/session/caisse/présence, décision métier et campagne de sécurité obligatoires.

## 4. Matrice d'audit demandée

| ID | Demande | État actuel | Frontend | Backend | IPC | DB | Security | Business rule | Complexity | Proposed action |
| -- | ------- | ----------- | -------- | ------- | --- | -- | -------- | ------------- | ---------- | --------------- |
| HV-01 | Remplacer « Produit » par « Article » | Incohérent : quelques clés FR disent déjà « Article », navigation/messages disent encore « Produit » ; EN emploie `Product` | Oui, i18n/navigation/formulaires/rapports | Non | Non | Non | Faible | Glossaire canonique requis ; ne pas renommer les entités techniques | M | **SAFE UI CHANGE** — figer le glossaire FR/EN puis remplacer uniquement les libellés UX et adapter les tests de traduction |
| HV-02 | Images locales d'articles | Aucun champ image dans type, DB, IPC ou formulaire ; seul le logo est packagé | Formulaire, aperçu, fallback, miniatures | Copie/validation/lecture contrôlées | Nouveaux canaux étroits nécessaires | Migration avec identifiant relatif/métadonnées probable | Élevée : path traversal, MIME, taille, renderer sans FS | Décider cycle de vie, sauvegarde, suppression et import/export | L | **FUNCTIONAL EXTENSION + SECURITY CHANGE + NOT CURRENTLY SUPPORTED** — stocker une copie sous `userData/article-images`, nom opaque, limites MIME/taille, URL délivrée sans accès FS générique ; jamais un chemin source fragile |
| HV-03 | Présence sous forme agenda/calendrier | Vue actuelle : résumé + liste de 250 sessions ; pas de calendrier ni regroupement journal/personne | Refonte de vue et filtres | Requête agrégée/paginée souhaitable | Extension lecture filtrée | Schéma actuel contient début/fin/état | Modérée | Fuseau, jour ouvré, session traversant minuit à préciser | L | **FUNCTIONAL EXTENSION** — construire d'abord un modèle de lecture quotidien, sans modifier les écritures |
| HV-04 | Fiche quotidienne multi-utilisateurs | Le backend supporte plusieurs présences simultanées et `attendanceStatuses`; UI « Ma présence » pour non-management | Agenda/tableau interactif + popover minimal | Lecture par date/rôle/employé ; écritures à décider | Probable extension | Données de base présentes | Élevée si cellules éditables | Qui peut pointer pour qui ? Managers inclus ? | L | **ALREADY SUPPORTED PARTIALLY + FUNCTIONAL EXTENSION + BUSINESS DECISION REQUIRED** |
| HV-05 | Signature de présence par credentials propres | Présence démarrée automatiquement au login et fermée au logout/expiration ; aucune signature par cellule | Dialogue credential minimal | Vérification atomique de l'identité signataire | Nouveau canal dédié, identité jamais acceptée du renderer | Audit/signature logique à associer à la présence ; aucun secret persisté | Critique | Choisir : présence automatique, pointage explicite, ou hybride | XL | **ARCHITECTURAL CHANGE + SECURITY CHANGE + BUSINESS DECISION REQUIRED** — ne pas réutiliser `verifyAdmin` ni envoyer `employeeId` comme autorité |
| HV-06 | Interdire la saisie passée aux utilisateurs ordinaires | Les employés ne disposent pas de correction ; `correctAttendance` est permissionné et audité pour rôles autorisés | Masquer/désactiver selon droits | Validation temporelle backend à renforcer selon contrat | Canal existant | Colonnes original/corrected/reason présentes | Élevée | Définir fenêtre future/past, fuseau et permissions exactes | M/L | **ALREADY SUPPORTED PARTIALLY + SECURITY CHANGE** — ajouter une politique backend explicite ; ne jamais dépendre seulement du disabled UI |
| HV-07 | Supprimer l'auto-lock d'inactivité | Timeout 30 min dupliqué dans React et `requireSession`; l'expiration ferme aussi la présence | Retirer timer/notice seulement après décision | Modifier la politique de session et `lastActivityAt` | `touchSession` devient à supprimer/requalifier | Pas de migration | Critique sur poste partagé | Durée maximale absolue ? verrouillage manuel ? reprise après veille/coupure ? | XL | **SECURITY CHANGE + ARCHITECTURAL CHANGE + BUSINESS DECISION REQUIRED** — conserver logout et re-auth des opérations sensibles ; décider les contrôles compensatoires avant code |
| HV-08 | Supprimer l'accès temporaire élevé au profit Login/Logout | Élévation 10 min profondément intégrée : modal, session IPC, permissions, compteur, dropElevation | Retirer modal/compteur et introduire Switch user | Refaire la transition de session | Supprimer/revoir `dropElevation`, réponse `session.elevation`, logique login-élevé | Pas forcément de migration | Critique : identité acteur actuellement différente de l'autorisateur | Contrat de switch et caisse obligatoire | XL | **ARCHITECTURAL CHANGE + SECURITY CHANGE** — vraie session du nouvel utilisateur, jamais simple substitution de permissions |
| HV-09 | Changement d'utilisateur avec caisse ouverte | Une caisse ouverte appartient à `employee_id`; chaque vente force `employeeId=session.id`; un autre login ne retrouve pas la caisse du premier | Gestion panier/checkout/switch | Garanties atomiques et audit de transition | Nouveaux motifs/résultats de refus possibles | Modèle actuel est individuel, pas partagé | Critique | Décision bloquante : interdire switch, transférer, partager, ou forcer clôture ; que faire du panier ? | XL | **BUSINESS DECISION REQUIRED + ARCHITECTURAL CHANGE** — recommandation de sûreté par défaut : aucun transfert implicite ; décision humaine formelle avant implémentation |
| HV-10 | Contrôle « Développer navigation » stable en haut à gauche | Menu drawer est en haut à gauche sur petit écran ; collapse/expand desktop est sticky en bas de sidebar | Oui | Non | Non | Non | Faible | Aucun invariant | S | **SAFE UI CHANGE** — déplacer le toggle desktop dans l'en-tête/haut de sidebar, conserver clavier, label et cible tactile |
| HV-11 | Retirer l'onglet Aide principal | Help existe dans navigation, menu utilisateur, destination, fallback par défaut et tests | Oui, plusieurs points | Non | Non | Non | Faible | Garder la documentation accessible autrement ou la retirer de l'expérience ? | M | **SAFE UI CHANGE + DUPLICATE / SHOULD MERGE** — retirer de la navigation principale, choisir un point d'accès canonique ; remplacer le fallback `help` par une destination autorisée |
| HV-12 | Hiérarchie proportionnelle des espaces | Design system utilise déjà grilles asymétriques pour dashboard/rapports/POS, mais l'application reste hétérogène | Audit écran par écran | Non | Non | Non | Faible | Priorité opérationnelle par écran | M | **SAFE UI CHANGE + ALREADY SUPPORTED PARTIALLY** — utiliser les primitives existantes, pas une nouvelle grille globale |
| HV-13 | Défilement indépendant pertinent | POS possède catalogue et panier défilants séparément ; shell/main, listes et tables ont plusieurs overflows ; risque de scrolls imbriqués historiques | Oui, CSS/layout | Non | Non | Non | Faible, accessibilité focus à vérifier | Zones à figer par workflow | M | **SAFE UI CHANGE + ALREADY SUPPORTED PARTIALLY** — matrice de scroll par écran, une seule zone principale et un résumé/action stable quand utile |
| HV-14 | Cards/fiches pour entités individuelles | ProductDetailsDialog et listes `article` existent ; achats/fournisseurs utilisent listes/modales ; tables surtout pour comparaison | Oui | Éventuellement détail enrichi | Peut réutiliser les lectures | Non sauf données absentes | Faible | Distinguer comparaison vs détail | M | **SAFE UI CHANGE + ALREADY SUPPORTED PARTIALLY** — composant EntityDetails canonique ; ne pas transformer les tables comparatives |
| HV-15 | Glossaire UX canonique FR/EN | i18n centralisé mais nombreuses incohérences Product/Article et libellés historiques | Oui | Non | Non | Non | Faible | Valider les termes domaine | M | **SAFE UI CHANGE** — livrable glossaire avant HV-01 et toute retouche massive |
| HV-16 | Retirer informations banales | `Page courante` visible au-dessus du titre ; `Retour` est un contrôle accessible et parfois un libellé utile | Oui | Non | Non | Non | Attention à aria-label/navigation | Ne pas supprimer l'action back | S | **SAFE UI CHANGE** — masquer la redondance visuelle `Page courante`, conserver titre sémantique et bouton Back accessible |
| HV-17 | Éviter fonctionnalités doubles | Help a plusieurs entrées ; chat et Messages partagent MailboxPage mais variantes divergentes ; anciens Header/Sidebar coexistent dans le repo | Oui | Peu | Peut consolider canaux existants | Non | Modérée si suppression d'un chemin protégé | Définir workflow canonique | M | **DUPLICATE / SHOULD MERGE** — inventaire des points d'entrée, garder plusieurs raccourcis seulement vers le même état canonique |
| HV-18 | Hiérarchie des boutons | Design system fournit primary/secondary/ghost/danger/icon et tailles ; ancien markup emploie encore boutons CSS directs/emoji | Oui | Non | Non | Non | Destructive doit rester explicite | Aucun | M | **ALREADY SUPPORTED PARTIALLY + SAFE UI CHANGE** — migrer les écrans restants vers Button/IconButton, une seule action primaire par contexte |
| HV-19 | Normaliser espacements/géométrie | Tokens et CSS responsive existent ; CSS historique volumineux contient encore dimensions/overflows spécifiques | Oui | Non | Non | Non | Accessibilité zoom/touch | Cibles 5 résolutions obligatoires | M/L | **SAFE UI CHANGE** — supprimer overrides historiques après audit visuel, tests overflow et actions primaires à chaque résolution |
| HV-20 | Filtres propres à chaque rapport | Une barre globale applique période/granularité/article/catégorie à tous les onglets, même lorsque le stock courant ignore la période ; achats/équipe manquent état/fournisseur/employé | Oui | Les requêtes doivent accepter seulement les dimensions justifiées | Extension du payload `reports` possible | Données disponibles pour plusieurs dimensions | Permissionner filtres sensibles équipe | Sens des filtres par domaine | L | **FUNCTIONAL EXTENSION + ALREADY SUPPORTED PARTIALLY** — définir un schéma de filtres par `ReportTab`, validation backend et reset indépendant |
| HV-21 | Visualisations adaptées | Courbe prix, barres classement, mouvements, KPI et tables existent ; pas de décoration pure détectée | Ajustements par domaine | Données déjà exposées en majorité | Non/minime | Non | Faible | Chaque graphe doit répondre à une question | M | **ALREADY SUPPORTED PARTIALLY + SAFE UI CHANGE** — conserver les équivalents tabulaires accessibles, corriger seulement les associations non pertinentes |
| HV-22 | KPI justifiables, sans vérité inventée | Phase F respecte ventes validées, coûts observés et avertissements ; aucune marge/bénéfice inventé détecté | Libellés/contextes | Requêtes existantes | Non | Non | Confidentialité équipe | Définitions métriques à maintenir | S/M | **ALREADY SUPPORTED** — ajouter un dictionnaire de métriques et tests de définitions, pas de nouveaux KPI sans source |
| HV-23 | Restructurer Stock et traçabilité | Onglets état/mouvements/inventaires/alertes existent ; mouvement contient quoi/combien/quand/pourquoi/source via reason/reference, mais aucun acteur persistant | UI et filtres | Enrichir lecture ; acteur indisponible dans `stock_movements` | Extension si détail source/action | Migration `actor_user_id` probable si exigée | Autorisation/audit | Ne pas afficher un acteur inventé ; définir sources historiques | L | **FUNCTIONAL EXTENSION + NOT CURRENTLY SUPPORTED PARTIALLY** — UX d'abord sur données existantes ; persistance acteur dans une phase séparée |
| HV-24 | Restructurer Achats | Cycle fournisseur→draft→items→validation→stock existe ; validation est transactionnelle/idempotente par état ; historique/détail restent limités | Refonte détail et états | Lecture détaillée achat/items nécessaire | Extension lecture | Tables et états présents | Permissions existantes | Un achat validé modifie déjà le stock une seule fois | M/L | **ALREADY SUPPORTED PARTIALLY + FUNCTIONAL EXTENSION** — exposer détail canonique, ne pas réécrire le moteur transactionnel sans défaut prouvé |
| HV-25 | Relation Achats ↔ Stock | À validation, mouvement `reason='purchase'`, `reference_id=String(purchaseId)` ; annulation analogue ; pas de FK typée | Liens navigationnels | Requête de résolution source/cible | Canal détail possible | Relation logique persistée mais faible/texte | Vérifier permission des destinations | Décider si relation historique doit survivre à suppression | M/L | **ALREADY SUPPORTED PARTIALLY** — exploiter reason/reference avec validation stricte ; migration FK seulement si bénéfice établi |
| HV-26 | Chat rapide en drawer droit | Le bouton en-tête navigue actuellement vers une page `chat` et ajoute une entrée d'historique | Drawer, état overlay, retour au travail | Aucun besoin nouveau | Réutilise messages | Non | Focus trap/fermeture/accessibilité | Chat reste consultation/envoi rapide | M | **SAFE UI CHANGE** — drawer droit canonique, sans changer destination principale ni historique de navigation |
| HV-27 | Pas de suppression dans chat rapide | MailboxPage affiche le bouton delete dans les variantes chat et management | Condition UI | Backend reste autorité | Canal existant reste pour Messages | Non | Ne pas affaiblir permission | Suppression réservée module Messages | S | **SAFE UI CHANGE** — masquer l'action uniquement en variante chat ; conserver le canal permissionné pour Messages |
| HV-28 | Suppression dans Messages | Déjà disponible via suppression logique par utilisateur et identité forcée côté IPC | Maintenir confirmation/feedback | Supporté | `deleteMessage`, userId écrasé par session | `message_deletions` | Correctement sender-bound | Définir conservation/audit si nécessaire | S | **ALREADY SUPPORTED** — conserver dans workflow Messages canonique |
| HV-29 | Choix Nouveau mot de passe Manuel/Automatique | UI actuelle choisit implicitement selon rôle : employé automatique, manager/owner manuel avec réponse de sécurité | Nouveau sélecteur | Deux mécanismes existent mais règles diffèrent | Canaux existants séparés | Pas de migration | Critique : réinitialisation privilégiée | Qui peut choisir quel mode pour quel rôle ? automatique autorisé pour managers ? | L | **FUNCTIONAL EXTENSION + SECURITY CHANGE + BUSINESS DECISION REQUIRED** |
| HV-30 | Mot de passe manuel | Supporté seulement pour Owner/Manager avec question/réponse ; pas pour employé dans ce dialogue | Workflow à unifier | Validation min 8 + hash bcrypt existe | Canal manuel actuel privilégié | Colonnes existantes | Ne pas exposer/persister le secret renderer | Autorité requise pour reset d'un tiers | M/L | **ALREADY SUPPORTED PARTIALLY + SECURITY CHANGE** — créer un canal administratif manuel dédié si choisi, sans détourner la récupération personnelle |
| HV-31 | Mot de passe automatique sécurisé | Générateur backend `randomBytes(9).toString('base64url')`, hash bcrypt, affichage temporaire 60 s ; pas un générateur React | Sélecteur/affichage seulement | Déjà sécurisé pour employés | `resetPassword` permissionné | Pas de migration | Secret visible/copiable brièvement ; clipboard à traiter | Portée par rôle à décider | M | **ALREADY SUPPORTED** pour employés ; **BUSINESS DECISION REQUIRED** avant extension aux rôles privilégiés |
| HV-32 | Responsive aux cinq résolutions | Breakpoints et primitives existent, mais rapport humain signale encore défauts ; aucune matrice finale passée | Tous écrans modifiés | Non | Non | Non | Clavier, focus, touch, contraste | Critère de gate, pas simple polish | L | **SAFE UI CHANGE + NOT CURRENTLY VALIDATED** — tests CSS ciblés puis observation humaine 1920×1080, 1366×768, 1280×800, 1024×768, 800×1280 |

## 5. Classification consolidée

### Catégorie A — UX/frontend, autorisable après contrat

HV-01, HV-10 à HV-19, HV-21, HV-22, HV-26 à HV-28 et la partie purement responsive de HV-32. Ces travaux ne doivent modifier ni identité, ni permissions, ni schéma. HV-11 dépend néanmoins du choix du point d'accès restant vers la documentation.

### Catégorie B — extensions fonctionnelles

HV-02 à HV-04, HV-20, HV-23 à HV-25 et HV-29 à HV-31. Elles nécessitent des contrats de données/API explicites et des tests de migration ou d'autorisation selon le cas.

### Catégorie C — architecture, sécurité et métier

HV-05 à HV-09. Ce lot doit être isolé des refontes visuelles. Il touche la source d'identité, la durée de session, la présence automatique, le propriétaire de caisse, l'acteur de vente et les audits.

## 6. Decision contract obligatoire

Les décisions suivantes bloquent l'implémentation correspondante :

| Décision | Question à trancher | Options sûres à évaluer | Valeur implicite interdite |
| --- | --- | --- | --- |
| DC-01 Présence | Le login vaut-il pointage, ou faut-il un acte signé distinct ? | automatique ; explicite ; hybride documenté | Ajouter un clic signé tout en conservant silencieusement le double pointage |
| DC-02 Temporalité | Jusqu'à quelle heure/date un utilisateur peut-il agir ? | temps courant strict ; fenêtre courte ; corrections uniquement privilégiées | Faire confiance à l'horloge/disabled du renderer |
| DC-03 Session | Que remplace le timeout d'inactivité ? | session jusqu'au logout + re-auth sensible ; durée absolue ; verrouillage manuel | Session infinie sans contrôle compensatoire |
| DC-04 Switch user | Peut-on changer d'utilisateur pendant une caisse ouverte ? | refus ; clôture obligatoire ; caisse partagée explicitement modélisée | Transfert implicite de caisse ou ventes attribuées au mauvais acteur |
| DC-05 Panier | Que faire du panier au logout/switch ? | bloquer ; abandon confirmé ; brouillon persisté attribué | Perte silencieuse ou reprise sous une autre identité |
| DC-06 Checkout | Que faire si switch/logout pendant validation ? | verrou atomique et fin d'opération ; refus de switch | Interruption laissant une vente ambiguë |
| DC-07 Images | Les images font-elles partie des backups/restores/imports ? | oui avec copie locale ; non avec avertissement explicite | Chemin absolu dépendant du fichier source |
| DC-08 Password | Quels rôles et opérateurs peuvent choisir manuel/automatique ? | matrice explicite et re-auth sensible | Étendre l'automatique aux comptes privilégiés sans approbation |
| DC-09 Help | Où reste le mode d'emploi après retrait de la navigation ? | menu utilisateur ; Administration ; document externe packagé | Supprimer toute voie accessible vers l'aide |

## 7. Contrat technique recommandé par lot

### 7.1 UX safe changes

Préconditions : glossaire approuvé, inventaire des duplications, matrice d'écran/résolution. Interdictions : modification des canaux IPC, permissions, tables ou règles de caisse. Gate : tests frontend, accessibilité clavier, absence d'overflow, puis nouvelle observation humaine.

### 7.2 Images d'articles

Contrat proposé à valider : le renderer appelle un sélecteur backend étroit ; le backend valide taille/type/signature, copie vers un dossier applicatif avec nom opaque et écriture atomique ; la DB stocke uniquement un identifiant relatif et les métadonnées utiles ; absence d'image donne un fallback ; suppression d'article ne détruit pas irréversiblement une image encore référencée ; backup/restore traite images et DB de façon cohérente. Aucun `file://` arbitraire ni API filesystem générique dans le preload.

### 7.3 Présence

Séparer lecture et écriture. Première livraison : agenda read-only à partir des sessions existantes. Deuxième livraison seulement après DC-01/DC-02 : opération signée côté backend, credential éphémère, identité dérivée de la vérification, anti-double-submit, audit succès/échec, règle temporelle et permission de correction. Les mots de passe ne doivent apparaître dans aucune table, log ou résultat IPC.

### 7.4 Session et changement d'utilisateur

Créer une transition atomique explicite, pas une évolution de l'élévation. Le backend doit fermer ou conserver correctement la présence de l'ancien acteur, vérifier le statut de sa caisse et du checkout, traiter le panier selon DC-05, terminer la session sender-bound, authentifier le nouvel acteur, créer sa présence selon DC-01, puis retourner ses seules permissions. Chaque vente continue de dériver l'acteur de la session backend.

### 7.5 Rapports, Stock et Achats

Étendre les lectures avant la persistance. Les filtres doivent être validés par domaine. Les liens achat↔mouvement peuvent exploiter `reason/reference_id` après validation numérique et permission, sans prétendre qu'une FK existe. L'acteur d'un mouvement ne peut être affiché avant persistance réelle. Le moteur transactionnel d'achat validé une seule fois doit rester inchangé et couvert par régression.

## 8. Ordre d'exécution approuvé par l'audit

```text
R-0  Décisions DC-01 → DC-09
  ↓
R-1  Glossaire + inventaire duplications + matrice responsive
  ↓
R-2  UX safe changes (navigation, Help, labels, buttons, cards, scroll)
  ↓
R-3  Chat drawer + filtres/visualisations avec données existantes
  ↓
R-4  Images + password choice + lectures Stock/Achats/Présence
  ↓
R-5  Architecture Session/Presence/Switch/Cash isolée
  ↓
R-6  Régression sécurité, données, caisse, offline et coupure
  ↓
R-7  Validation humaine aux cinq résolutions et trois thèmes
  ↓
R-8  Retour au release hardening I.1
```

R-2 et R-3 ne doivent pas commencer avant validation de R-0 pour les points qui en dépendent. R-5 doit disposer de sa propre autorisation d'implémentation.

## 9. Impacts de migration anticipés

- Terminologie : aucune migration DB.
- Images : migration additive probable sur `products` et stratégie fichiers/backups ; rollback compatible requis.
- Présence read-only : aucune migration. Signature explicite : métadonnées d'opération/audit potentiellement additives après contrat.
- Suppression elevation/switch : aucune migration nécessaire par défaut, mais refonte IPC/session majeure.
- Stock actor : migration additive seulement si la décision exige l'acteur historique/futur dans chaque mouvement.
- Password choice : aucune migration si les politiques existantes suffisent ; nouveau canal possible.

## 10. Tests minimaux par phase

- Glossaire : clés FR/EN complètes, aucune chaîne UX `Produit` résiduelle dans les surfaces convenues.
- Images : MIME falsifié, fichier trop gros, disparition source, collision, backup/restore, fichier orphelin, path traversal, offline.
- Présence : deux utilisateurs simultanés, minuit/fuseau, correction autorisée/refusée, secret absent logs/DB, double clic, coupure.
- Session/switch : caisse ouverte, panier vide/non vide, checkout en cours, présence, attribution vente, permissions après switch, renderer spoofing, logout répétitif.
- Rapports : chaque filtre change uniquement les domaines applicables, aucune métrique inventée, tables accessibles équivalentes.
- Stock/Achats : validation répétée sans double stock, annulation transactionnelle, lien source, données historiques sans acteur affichées honnêtement.
- Responsive : cinq résolutions, light/dark/high contrast, clavier/tactile, modales/drawers, aucun overflow/superposition/action coupée.

## 11. Release gates actualisés

| Gate | État après rapport humain | Justification |
| --- | --- | --- |
| RB-01 — validation visuelle | OPEN / OBSERVED FAIL-PARTIAL | Les observations découvrent des corrections ; aucune matrice complète n'est passée |
| RB-02 — upgrade | OPEN | Non affecté par ce rapport |
| RB-03 — installer lifecycle | OPEN | Non affecté |
| RB-04 — Nodemailer | CLOSED | Non régressé ; aucune modification code dans cet audit |
| RB-05 — signature | OPEN | Non affecté |
| RB-06 — packaged E2E | OPEN | Le rapport ne démontre pas Setup→Login→POS→sale→stock complet |

## 12. Fichiers à ne pas modifier dans les premiers lots UX

Pour R-1/R-2, garder hors portée sauf nécessité prouvée :

- `backend/src/database/schema.ts` et migrations ;
- `backend/src/main/ipcHandlers.ts` ;
- permission matrix et preload ;
- transactions ventes/achats/stock ;
- mécanismes backup/restore.

Cette frontière empêche qu'une correction visuelle modifie accidentellement l'autorité backend ou l'intégrité métier.

## 13. Conclusion d'audit

L'audit est terminé. Les demandes sont réalisables, mais HV-05 à HV-09 et HV-29 ne sont pas implémentables correctement sans les décisions DC correspondantes. Les changements UI sûrs peuvent former une phase distincte après validation du glossaire et du point d'accès Help. Aucun changement fonctionnel ou architectural n'a été appliqué pendant cet audit.

**Classification inchangée : NOT READY — RELEASE BLOCKERS REMAIN.**

**STOP : attendre le DECISION CONTRACT avant toute implémentation.**
