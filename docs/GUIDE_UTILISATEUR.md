# Guide utilisateur STORE

## Présentation

STORE est une application de bureau bilingue français/anglais destinée à la gestion
locale d’une boutique : caisse, stocks, utilisateurs, présences, historiques, rapports,
chat, alertes et sauvegardes. Les données métier restent dans la base SQLite de
l’ordinateur sur lequel l’application est installée. Une connexion Internet n’est
nécessaire que pour l’envoi d’e-mails.

## Compatibilité tablette

STORE est installable sur Windows 10/11 et sur les tablettes Windows. L’interface
s’adapte aux écrans tactiles à partir de 720 px et réorganise automatiquement la caisse
et le Dashboard.

- `npm run package:win:x64` produit l’installateur pour les appareils Intel/AMD ;
- `npm run package:win:arm64` produit l’installateur pour les appareils Windows ARM.

Le fichier `.exe` ne fonctionne pas sur Android ou iPadOS. Une version pour ces
plateformes demanderait un backend réseau sécurisé et une synchronisation serveur.

## Premier lancement et rôles

Au premier lancement, créez le compte **Owner** avec un mot de passe d’au moins huit
caractères. Ce compte unique administre l’application et ses paramètres sensibles.

Les autres rôles disponibles sont :

- **Employee** : accès courant à la caisse et aux fonctions autorisées ;
- **Manager** : gestion opérationnelle et accès au Dashboard après authentification ;
- **Owner** : administration complète, sauvegarde et réinitialisation.

Les comptes Manager et Owner peuvent définir une question et une réponse de sécurité
pour renouveler leur mot de passe. Un compte inactif n’apparaît pas dans la zone de
présence et ne doit plus être utilisé pour se connecter.

## En-tête et navigation

L’en-tête reste disponible dans la Caisse et le Dashboard :

- le bouton **Caisse ↔ Dashboard** change d’espace ; un Employee doit faire valider
  l’accès au Dashboard par un Manager ou l’Owner ;
- **FR/EN** change immédiatement la langue de l’interface et des messages ;
- le thème clair/sombre s’applique à tous les écrans ;
- **Chat** ouvre la messagerie locale ; un second clic la ferme et revient à l’écran
  précédent ;
- l’icône d’alerte ouvre les alertes système, notamment celles de stock ;
- les cercles de présence montrent uniquement les comptes actifs : vert pour présent,
  bleu pour absent. Une connexion active automatiquement la présence.

Une mini-fenêtre ouverte depuis l’en-tête se ferme aussi en cliquant à l’extérieur.

## Caisse

Recherchez les produits par nom, catégorie ou hashtag, puis cliquez sur une carte pour
l’ajouter au panier. Chaque ligne possède sa propre case de sélection : seules les lignes
cochées sont incluses lors de la validation de la vente. Les boutons `−` et `+`, ainsi
que la saisie numérique, permettent d’ajuster les quantités.

La remise n’est affichée que si elle a été activée dans **Paramètres**. Son montant et
les valeurs monétaires de toute l’application utilisent la devise choisie.

La validation :

1. contrôle le stock des lignes sélectionnées ;
2. crée la facture ;
3. déduit les quantités vendues ;
4. met à jour les indicateurs, historiques et alertes de stock.

L’aperçu de facture est consultable et imprimable. Un clic à l’extérieur le ferme.

## Dashboard

### Accueil

Les sous-onglets donnent accès à :

- **Produits** : liste alphabétique avec recherche par produit, catégorie ou hashtag.
  Un clic sur une ligne ouvre tous les attributs en lecture seule ;
- **Stock faible** : articles au seuil minimal, quantité actuelle et date/heure de
  détection ;
- **Ventes aujourd’hui** : chiffre d’affaires et factures du jour en lecture seule.

Les fiches de présentation se ferment en cliquant à l’extérieur.

### Stocks

La liste alphabétique permet de rechercher, créer et administrer les articles, catégories,
prix, quantités, seuils et hashtags. Un clic sur une ligne ouvre une fiche récapitulative
en lecture seule. Les doublons sont refusés avec un message explicite.

L’import et l’export PDF permettent de transférer ou d’archiver l’état du stock. Vérifiez
toujours l’aperçu et la devise avant de réutiliser un document importé.

### Utilisateurs

La recherche se trouve au-dessus de la liste. Les sous-onglets **Employés**,
**Managers** et **Administrateur** filtrent les comptes sans retirer les fonctions de
création, modification, activation et renouvellement du mot de passe.

Lors de la création d’un Employee, un mot de passe temporaire est généré. La fenêtre
reste visible 60 secondes, avec bouton de copie, compteur et barre de progression, puis
se ferme automatiquement. Pour un Manager, l’e-mail et la question/réponse de sécurité
sont obligatoires. Les doublons de nom/prénom, username ou e-mail sont refusés.

### Historiques

Choisissez une date de début et de fin, puis un sous-onglet :

- **Ventes** : factures et articles vendus ;
- **Achats** : entrées et ravitaillements de stock ;
- **Personnel** : présences et temps de travail.

Les cases de sélection, dont **Tout sélectionner**, déterminent le contenu exporté ou
supprimé. L’export est généré au format PDF. Une suppression est définitive pour les
éléments choisis ; effectuez d’abord une sauvegarde si ces données doivent être conservées.

### Rapports

Les filtres journalier, hebdomadaire, mensuel ou personnalisé pilotent les rapports :

- entrées, sorties et mouvements des articles ;
- quantités, prix unitaires, valeurs totales et valeur mouvementée ;
- articles et catégories les mieux vendus ;
- évolution des prix unitaires.

Les rapports sélectionnés peuvent être visualisés sous forme de graphiques, exportés en
PDF ou envoyés par e-mail. **Tout sélectionner** inclut tous les rapports visibles dans
le filtre courant.

### Chat

- **Boîte de réception** : messages locaux entre utilisateurs ;
- **Alertes** : historique des alertes système et de stock ;
- **E-mails de rapports** : historique des envois de rapports réussis.

Les messages locaux ne remplacent pas les alertes : les alertes système vont dans l’icône
d’alerte et, lorsque SMTP est configuré, peuvent aussi être envoyées à l’Owner.

### Paramètres

Les paramètres sont regroupés par fonction :

- identité et coordonnées de la boutique ;
- devise parmi EUR, XOF, XAF, CAD, GBP, CHF, NGN, GHS ;
- activation ou désactivation des remises ;
- e-mail et SMTP ;
- sauvegarde, restauration et réinitialisation.

La réinitialisation est réservée à l’Owner, demande son mot de passe et une confirmation
explicite, et crée une sauvegarde préalable.

## Configuration de l’e-mail et de SMTP

### À quoi sert SMTP ?

SMTP permet à STORE d’envoyer :

- un e-mail de test ;
- un rapport PDF au destinataire choisi ;
- certaines alertes système à l’adresse de l’Owner.

Le chat interne reste local et ne dépend pas de SMTP.

### Prérequis

Avant de commencer :

1. utilisez de préférence une boîte dédiée à la boutique ;
2. activez l’authentification à deux facteurs chez le fournisseur ;
3. créez un **mot de passe d’application** lorsque le fournisseur le permet ;
4. vérifiez que le réseau autorise les connexions sortantes sur le port `587` ;
5. renseignez un e-mail valide pour la boutique et pour l’Owner.

N’utilisez pas le mot de passe principal d’une boîte personnelle. Le mot de passe SMTP
est actuellement conservé dans la base locale de STORE : l’accès au compte Windows et
aux sauvegardes doit donc être protégé.

### Champs à renseigner dans STORE

Ouvrez **Dashboard > Paramètres** :

1. dans la partie **Boutique**, renseignez le champ **E-mail** ; cette adresse reçoit
   notamment le test SMTP ;
2. ouvrez **E-mail et SMTP** ;
3. complétez les champs ci-dessous ;
4. enregistrez les paramètres ;
5. cliquez sur **Tester SMTP**.

| Champ STORE       | Rôle                                  | Exemple                          |
| ----------------- | ------------------------------------- | -------------------------------- |
| Serveur SMTP      | Serveur sortant du fournisseur        | `smtp.gmail.com`                 |
| Port SMTP         | Port STARTTLS                         | `587`                            |
| Utilisateur SMTP  | Adresse complète du compte expéditeur | `boutique.epices.demo@gmail.com` |
| Mot de passe SMTP | Mot de passe d’application            | `xxxx xxxx xxxx xxxx` (fictif)   |
| Expéditeur        | Adresse affichée comme émetteur       | `boutique.epices.demo@gmail.com` |
| E-mail boutique   | Destination du test                   | `direction@epices-demo.fr`       |

La version actuelle utilise STARTTLS sur le port `587`. N’utilisez pas le port `465`,
car l’interface ne permet pas encore d’activer le mode TLS implicite requis par ce port.

### Exemple concret avec Gmail

Supposons que la boutique utilise `boutique.epices.demo@gmail.com` :

1. connectez-vous à ce compte Google ;
2. activez la validation en deux étapes ;
3. ouvrez les paramètres de sécurité Google et créez un mot de passe d’application
   pour STORE, si cette option est autorisée pour le compte ;
4. dans STORE, saisissez :

```text
Serveur SMTP     : smtp.gmail.com
Port SMTP        : 587
Utilisateur SMTP : boutique.epices.demo@gmail.com
Mot de passe     : [mot de passe d’application généré par Google]
Expéditeur       : boutique.epices.demo@gmail.com
E-mail boutique  : direction@epices-demo.fr
```

5. enregistrez, puis cliquez sur **Tester SMTP** ;
6. vérifiez la boîte `direction@epices-demo.fr`, y compris le dossier spam ;
7. ouvrez ensuite **Rapports**, générez un PDF et envoyez-le à une adresse de test.

Les valeurs ci-dessus illustrent la configuration : les adresses et le mot de passe sont
à remplacer par les vôtres. Dans un environnement Google Workspace, l’administrateur
peut interdire les mots de passe d’application ou imposer une autre méthode.

### Exemple Microsoft 365

Pour une boîte `store@epices-demo.fr` hébergée par Microsoft 365 :

```text
Serveur SMTP     : smtp.office365.com
Port SMTP        : 587
Utilisateur SMTP : store@epices-demo.fr
Mot de passe     : [identifiant SMTP autorisé par l’administrateur]
Expéditeur       : store@epices-demo.fr
E-mail boutique  : direction@epices-demo.fr
```

SMTP AUTH doit être autorisé pour cette boîte par l’administrateur Microsoft 365.
L’expéditeur doit normalement être identique au compte de connexion ; une adresse
différente nécessite l’autorisation **Send As**.

### Vérifier que l’envoi fonctionne

Un test réussi confirme que STORE a remis le message au serveur SMTP. Vérifiez ensuite
la réception réelle. Lorsqu’un rapport est envoyé avec succès, l’opération apparaît dans
**Chat > E-mails de rapports**. Un échec ne doit pas créer une entrée de succès.

### Dépannage

| Symptôme                                  | Cause probable                                | Correction                                                                                     |
| ----------------------------------------- | --------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| « SMTP non configuré »                    | Serveur ou destinataire absent                | Renseigner le serveur SMTP et l’e-mail boutique                                                |
| Erreur d’authentification, `535`          | Identifiant, mot de passe ou SMTP AUTH refusé | Vérifier l’adresse complète, créer un mot de passe d’application ou contacter l’administrateur |
| Délai dépassé / connexion refusée         | Port bloqué par le pare-feu ou le réseau      | Autoriser la sortie TCP `587` et réessayer sur un autre réseau                                 |
| Expéditeur refusé                         | Adresse différente du compte connecté         | Utiliser la même adresse ou configurer l’autorisation Send As                                  |
| Test réussi mais message absent           | Filtrage antispam ou adresse erronée          | Vérifier spam, quarantaine et adresse destinataire                                             |
| La configuration fonctionnait puis échoue | Mot de passe révoqué ou politique modifiée    | Générer un nouveau secret et mettre à jour STORE                                               |

Ne copiez jamais le mot de passe SMTP dans une capture d’écran, un rapport de bug ou un
message du chat. En cas de perte ou de vol de l’appareil, révoquez immédiatement le mot
de passe d’application auprès du fournisseur.

### Références officielles des fournisseurs

- [Configuration SMTP Gmail et Google Workspace](https://support.google.com/a/answer/176600?hl=fr)
- [Paramètres des clients de messagerie Gmail](https://support.google.com/mail/answer/7104828?hl=fr)
- [Envoi SMTP depuis une application avec Microsoft 365](https://learn.microsoft.com/fr-fr/exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365)
- [Dépannage SMTP Microsoft 365](https://learn.microsoft.com/fr-fr/troubleshoot/exchange/email-delivery/fix-issues-with-printers-scanners-and-lob-applications-that-send-email-using-office-365)

## Sécurité et sauvegardes

Les mots de passe des utilisateurs sont hashés avec bcrypt. Une sauvegarde quotidienne
est créée avec une rétention de sept fichiers. Les sauvegardes contiennent les données et
paramètres locaux : conservez-les dans un emplacement chiffré et à accès limité.

Avant une mise à jour importante, une restauration ou une réinitialisation, créez une
sauvegarde et vérifiez que le fichier peut être copié sur un support sûr.
