# STORE — Tests de résilience

## Contrôles automatisés

La suite `npm test` vérifie notamment :

- migrations successives et réexécution sans doublon ;
- mise à niveau d’une ancienne base minimale sans perte de mouvements de stock ;
- refus d’une sauvegarde SQLite étrangère ou sans Propriétaire actif ;
- unicité des requêtes de facture afin de neutraliser les doubles clics ;
- rollback complet d’une transaction de stock interrompue ;
- lecture des données validées dans une base WAL et contrôle `quick_check` ;
- verrouillage de connexion et de récupération ;
- permissions Propriétaire, Manager et Caissier ;
- calculs monétaires et valeurs de stock ;
- délais progressifs de la file SMTP, y compris l’absence de configuration ;
- niveaux d’alerte quand l’espace disque devient faible ou critique.

## Scénarios physiques à exécuter sur le poste de livraison

Ces scénarios ne doivent pas être simulés en supprimant brutalement des fichiers. Ils sont
à réaliser sur une copie de recette et jamais sur les données de production.

1. Valider une vente, couper le processus Electron depuis le Gestionnaire des tâches,
   relancer STORE et contrôler facture, stock, paiement et `Intégrité : Correcte`.
2. Couper Internet, envoyer un rapport, redémarrer STORE, rétablir Internet et vérifier
   le passage `En attente` vers `Envoyé` sans doublon.
3. Copier une sauvegarde sur une clé USB, restaurer cette copie sur le poste de recette,
   puis contrôler comptes, produits, ventes et rapports.
4. Tenter une restauration avec un fichier texte renommé en `.db` et vérifier que la base
   active reste intacte.
5. Remplir un volume de recette jusqu’aux seuils d’avertissement, sans saturer le disque
   système, et vérifier l’alerte du diagnostic.
6. Tester l’imprimante Windows réelle, Microsoft Print to PDF et l’export PDF direct.
7. Laisser une session inactive 30 minutes et un accès Manager temporaire 10 minutes.
8. Tester l’interface aux résolutions 1366×768, 1024×768 et 720×500, avec clavier seul.

Consigner pour chaque scénario la date, la version de STORE, le poste, le résultat et la
preuve obtenue. Les essais physiques restants font partie de la validation de l’installeur.
