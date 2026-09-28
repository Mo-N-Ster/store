# STORE 3.0 — Glossaire UX français / anglais

Ce glossaire fixe le vocabulaire visible de STORE. Les noms techniques internes, les clés i18n, les champs de base de données et les contrats IPC ne sont pas renommés.

| Concept | Français canonique | Anglais canonique | À éviter dans l’interface |
| --- | --- | --- | --- |
| Article vendu ou stocké | Article | Item | Produit / Product |
| Ensemble des articles | Articles | Items | Produits / Products |
| Stock disponible | Stock | Stock | Inventaire lorsqu’il ne s’agit pas d’un comptage |
| Variation tracée du stock | Mouvement | Movement | Vente ou achat si la cause n’est pas confirmée |
| Approvisionnement enregistré | Achat | Purchase | Mouvement |
| Partenaire d’approvisionnement | Fournisseur | Supplier | Client |
| Comptage physique | Inventaire | Inventory count | Stock |
| Personne travaillant dans la boutique | Employé | Employee | Utilisateur lorsque la personne est le sujet |
| Vente en cours | Panier | Cart | Facture avant validation |
| Vente validée | Vente | Sale | Panier |
| Document commercial de vente | Facture | Invoice | Panier |
| Document remis après encaissement | Reçu | Receipt | Facture si le document n’est pas fiscal |
| Utilisateur habilité | Utilisateur | User | Employé comme terme générique |
| Identité permettant l’accès | Compte | Account | Utilisateur lorsque la personne est le sujet |
| Suivi du temps de travail | Présence | Attendance | Session utilisateur |
| Action de déclarer un horaire | Pointage | Time clock entry | Connexion / déconnexion |
| Début du temps de travail | Entrée | Clock-in | Connexion |
| Fin du temps de travail | Sortie | Clock-out | Déconnexion |
| Poste d’encaissement | Caisse | Point of sale / Till | Vente |
| Période comptable ouverte à la caisse | Session de caisse | Cash session | Session utilisateur |
| Vue analytique ou document exporté | Rapport | Report | Tableau de bord |
| Communication interne | Message | Message | Alerte système |
| Conversation rapide | Chat | Chat | Messagerie de gestion |
| Centre administratif des communications | Gestion de la messagerie | Messaging management | Chat |
| Copie locale de protection | Sauvegarde | Backup | Export |
| Réintégration d’une sauvegarde | Restauration | Restore | Import ordinaire |

## Règles d’usage

- Les intitulés, aides, états vides, confirmations et rapports emploient **Article / Item**.
- Les variables historiques telles que `product`, `productId` et les clés `navProducts` restent inchangées pour préserver les contrats techniques.
- Un libellé d’action commence par un verbe et décrit l’effet réel.
- Les actions destructrices nomment explicitement l’objet concerné.
- Le français est la langue de référence fonctionnelle ; l’anglais doit conserver le même sens, sans traduction littérale ambiguë.
