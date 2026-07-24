# Guide utilisateur STORE

## Compatibilité tablette

STORE est installable sur les tablettes Windows 10/11. L’interface s’adapte aux écrans
tactiles à partir de 720 px, agrandit les zones interactives et réorganise la caisse et
le Dashboard. Deux distributions Windows peuvent être produites :

- `npm run package:win:x64` pour les tablettes Intel/AMD ;
- `npm run package:win:arm64` pour les tablettes Windows ARM.

Le fichier `.exe` Windows ne peut pas être installé sur Android ou iPadOS. Une version
Android/iPad nécessite une API réseau sécurisée et une synchronisation serveur à la place
du pont Electron/SQLite local. Il ne faut pas distribuer le frontend seul sur ces appareils,
car les ventes et les données ne seraient pas opérationnelles.

## Premier lancement

Créez le compte propriétaire avec un mot de passe d’au moins huit caractères. Ce compte est le seul à accéder au Dashboard. Les données sont conservées localement dans le dossier applicatif Electron.

## Caisse

Recherchez ou filtrez les produits, cliquez sur une carte pour l’ajouter, ajustez les quantités et appliquez éventuellement une remise en euros. **Valider** enregistre la facture et met le stock à jour. L’aperçu peut être imprimé. Pour les présences, un clic sur les initiales démarre le service ; `Maj + clic` le termine.

## Dashboard

- **Accueil** : indicateurs de produits, alertes et chiffre d’affaires.
- **Produits** : ajout, modification, ajustement de stock et suppression logique.
- **Employés** : création et modification. Le mot de passe provisoire est affiché une seule fois.
- **Ventes** : filtres par dates et recherche, détail et export CSV.
- **Graphiques** : ventes des trente derniers jours et heures complètes travaillées.
- **Messagerie** : échanges employés/propriétaire et gestion des messages.
- **Paramètres** : identité de la boutique, sauvegarde et réinitialisation protégée.

## Sécurité et sauvegardes

Les mots de passe sont hashés avec bcrypt. Une sauvegarde quotidienne est créée avec une rétention de sept fichiers. La réinitialisation demande le mot de passe propriétaire, une confirmation explicite et déclenche une sauvegarde préalable.
