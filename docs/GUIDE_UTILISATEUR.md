# Guide utilisateur STORE

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

