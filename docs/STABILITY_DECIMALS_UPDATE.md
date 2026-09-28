# Accueil, changement d'utilisateur et décimales — 2026-09-27

- Accueil organisé en deux colonnes indépendantes : courbe/ventes à gauche ; stock à surveiller, présents et contexte du mois à droite. Le stock et les présents ne sont plus séparés par la hauteur de la courbe.
- Changement d'utilisateur : verrou synchrone anti-double soumission, saisies/annulation désactivées pendant la requête, focus sur le mot de passe après refus, gestion d'erreur à l'ouverture et retour à une destination sûre après changement réel d'identité. Les contrôles de caisse ouverte et de panier sont conservés.
- Messages retiré de la navigation lorsque le rôle courant est employee, sans retirer le chat d'en-tête ni modifier les permissions backend. Managers/propriétaire conservent l'onglet.
- Champs monétaires décimaux partagés : virgule ou point acceptés et transmis sous forme canonique avec point. Bornes et précision restent contrôlées ; les séquences ambiguës sont refusées. Prix, achat, montant reçu et ouverture/clôture de caisse utilisent ces contrôles. Remises acceptent également les décimales. Quantités de stock et d'articles restent entières : aucune règle métier de fractionnement n'a été introduite.
- Catalogue : suppression du second chargement initial complet ; recherche temporisée 150 ms ; réponses périmées ignorées ; erreurs visibles avec réessai. Maxima des courbes calculés une fois par rendu. Aucun contrôle backend n'est supprimé pour gagner du temps ; aucun gain chiffré global revendiqué.

Validation : 260 tests réussis, aucun échec/ignoré ; lint, TypeScript, build et git diff --check. Banc Electron sur profil isolé/API simulée pour saisie continue, virgule, précision, visibilité mot de passe et double soumission/réessai du changement d'utilisateur. Pas d'essais sur les comptes personnels, pas de validation humaine auto-déclarée.

Protection complémentaire : le contenu métier attend les permissions du nouveau compte avant son montage. En cas d'échec de récupération de session, un réessai explicite est proposé ; aucune promesse rejetée de cette étape n'est laissée sans traitement.
