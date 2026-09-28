# STORE 3.0 — Réconciliation des 41 recommandations

Date : 25 septembre 2026
Version : `2.0.1`

## Méthode et statut

Cette matrice confronte le cahier consolidé au code actif, et non aux seuls rapports de phase. Les statuts `CONFORME CODE` signifient que le flux, les contrôles backend et les tests existent. Ils ne remplacent pas la validation visuelle humaine aux cinq résolutions cibles.

|   # | Exigence                                    | Statut                           | Preuve principale                                                              |
| --: | ------------------------------------------- | -------------------------------- | ------------------------------------------------------------------------------ |
|   1 | Article/Item et glossaire stable            | CONFORME CODE                    | `i18n.ts`, `UX_GLOSSARY_FR_EN.md`; noms DB/API conservés                       |
|   2 | Hiérarchie de l’information                 | CONFORME CODE / VISUEL À VALIDER | primitives `ops-*`, `report-*`, `pos-*`                                        |
|   3 | Navigation stable et compréhensible         | CONFORME CODE                    | `StoreShell.tsx`, historique réversible dans `App.tsx`                         |
|   4 | Aide secondaire                             | CONFORME CODE                    | absente de `navigationItems`, accessible dans le menu utilisateur              |
|   5 | Hiérarchie des boutons                      | CONFORME CODE                    | variantes primary/secondary/ghost/danger et `IconButton` labellisé             |
|   6 | Desktop et tablette                         | CONFORME CODE / VISUEL À VALIDER | breakpoints 68rem, 56.25rem, 50rem, 35rem/34rem                                |
|   7 | Liste pour plusieurs, fiche pour un         | CONFORME CODE                    | listes/tableaux et dialogues de détail dédiés                                  |
|   8 | Image principale d’Article gérée localement | CONFORME CODE                    | `ArticleImage`, `articleMedia.ts`, `image_ref`, copie opaque JPEG/PNG/WebP     |
|   9 | Authentification distincte de la présence   | CONFORME CODE                    | pointage explicite; aucun couplage login/logout                                |
|  10 | Fiche de présence                           | CONFORME CODE                    | `PresencePage.tsx`, vue du jour, historique et filtres                         |
|  11 | Signature du pointage par la personne       | CONFORME CODE                    | secret de la cible vérifié dans `explicitAttendance.ts`                        |
|  12 | Correction administrative séparée           | CONFORME CODE                    | IPC permissionné, heure backend, raison et avant/après audités                 |
|  13 | Aucun logout général par inactivité         | CONFORME CODE                    | aucun timer de session ni `lastActivityAt` actif                               |
|  14 | Aucune élévation Manager temporaire         | CONFORME CODE                    | surfaces/canaux supprimés; permissions issues de la session réelle             |
|  15 | Véritable changement d’utilisateur          | CONFORME CODE                    | authentification atomique de B; session A conservée en cas d’échec             |
|  16 | Recalcul immédiat après changement          | CONFORME CODE                    | permissions mises à `null`, vues/dialogues remontés par clé utilisateur        |
|  17 | Switch/logout bloqués par caisse ouverte    | CONFORME CODE                    | garde frontend et contrôle backend autoritatif                                 |
|  18 | Panier jamais transféré                     | CONFORME CODE                    | abandon confirmé, remise à zéro locale, aucune mutation métier                 |
|  19 | Identité figée pendant checkout             | CONFORME CODE                    | `checkoutCritical` bloque switch/logout; acteur injecté par backend            |
|  20 | Quick Chat en drawer droit                  | CONFORME CODE / VISUEL À VALIDER | drawer droit, hauteur/scroll propres, workspace préservé                       |
|  21 | Suppression absente du Quick Chat           | CONFORME CODE                    | suppression rendue uniquement en variante management                           |
|  22 | Mot de passe automatique ou manuel          | CONFORME CODE                    | génération backend, politique manuelle, secret éphémère                        |
|  23 | Filtres contextuels par rapport             | CORRIGÉ                          | isolation par onglet; achat fournisseur+Article; présence employé+état         |
|  24 | KPI/graphe/table sur même population        | CORRIGÉ                          | filtres normalisés; ventes et achats filtrent aussi leurs listes détaillées    |
|  25 | Graphiques adaptés à la question            | CONFORME CODE                    | courbe temporelle, barres de classement/mouvement, tables équivalentes         |
|  26 | Aucun indicateur inventé                    | CONFORME CODE                    | marge/valorisation/ROI absents; limites finance visibles                       |
|  27 | Traçabilité honnête du stock                | CONFORME CODE                    | type, quantité, date, source/référence persistés; aucun acteur fictif          |
|  28 | Origines de mouvement réelles               | CONFORME CODE                    | vente, annulation, achat, inventaire, ajustement persistés                     |
|  29 | Navigation métier sur relations réelles     | CONFORME CODE                    | détails achat ↔ mouvements par `reference_id`; aucune heuristique temporelle   |
|  30 | Cycle achat lisible                         | CONFORME CODE                    | fournisseur, brouillon, lignes, total, validation, effet stock                 |
|  31 | Achat validé = stock modifié une fois       | CONFORME CODE                    | transaction et garde idempotente `VALIDATED`                                   |
|  32 | Sauvegarde unique DB+médias                 | CONFORME CODE                    | bundle `.store-backup` v2 avec manifeste, SQLite et médias                     |
|  33 | Restauration prudente                       | CONFORME CODE                    | inspection, checksums, staging, validation DB puis commit                      |
|  34 | Sécurité des sauvegardes                    | CONFORME CODE                    | rejet traversal, absolus, incohérences et corruption                           |
|  35 | Une implémentation canonique par workflow   | CORRIGÉ                          | anciens `Header`/`Sidebar` supprimés; `StoreShell` est le shell unique         |
|  36 | Scroll et géométrie contrôlés               | CONFORME CODE / VISUEL À VALIDER | zones indépendantes POS/chat/présence, repli tablette                          |
|  37 | Accessibilité clavier/tactile               | CONFORME CODE / HUMAIN À VALIDER | focus trap/restauration, Échap, labels, focus visible, cibles tactiles         |
|  38 | Clair, sombre, contraste élevé              | CONFORME CODE / VISUEL À VALIDER | tokens light/dark et `prefers-contrast: more`                                  |
|  39 | Architecture locale conservée               | CONFORME                         | Electron → preload strict → backend → SQLite, aucun cloud requis               |
|  40 | Autorité backend transversale               | CONFORME CODE                    | RBAC IPC default-deny; identité et acteurs remplacés depuis la session         |
|  41 | Redesign centré identité/opérations         | CONFORME CODE / VISUEL À VALIDER | shell role-aware et domaines POS/Articles/Stock/Achats/Rapports/Administration |

## Corrections issues de cette réconciliation

- suppression des deux anciens composants de navigation concurrents ;
- centralisation du type de section dans le module de navigation canonique ;
- filtres de rapports réellement contextuels et purgés lors du changement d’onglet ;
- rapport Présences branché sur la lecture de présence réelle ;
- filtre Article ajouté aux achats via relation persistée `purchase_items` ;
- filtres Article/catégorie appliqués à la liste de ventes via `invoice_lines` ;
- affichage du vrai nom d’employé dans le rapport Présences ;
- test déterministe de non-contamination des filtres.

## Délimitation honnête

Le code et les invariants peuvent être certifiés automatiquement. L’absence de superposition, de coupure ou de défaut de contraste à `1920×1080`, `1366×768`, `1280×800`, `1024×768` et `800×1280` doit encore être constatée visuellement dans l’application réelle. Ce gate ne doit pas être déclaré réussi sur la seule base de recherches de chaînes ou de règles CSS.
