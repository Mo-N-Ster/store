# Frontend

Application React sans accès direct à Node.js. Les pages consomment uniquement les
services de `src/services`, lesquels délèguent au pont IPC exposé par le preload.

Chaque fonctionnalité est organisée en composants, hooks, services et types indépendants :

- `src/pages/Auth` : démarrage, connexion et récupération du mot de passe ;
- `src/pages/Cashier` : catalogue, panier, vente et facture ;
- `src/pages/Dashboard` : Accueil, Stocks, Utilisateurs, Historiques, Rapports, Chat
  et Paramètres ;
- `src/components` : navigation, fenêtres et contrôles réutilisables ;
- `src/hooks` : état et comportements partagés ;
- `src/services` : façade métier vers l’IPC ;
- `src/types` : contrats TypeScript.

Tout nouveau libellé visible doit être ajouté aux traductions FR et EN. Les composants
doivent conserver le thème clair/sombre, les tailles tactiles et les règles responsives
existantes.
