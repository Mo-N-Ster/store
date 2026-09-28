# Guide utilisateur STORE

## Administration — ajouts J.6R-A

Dans Permissions, le propriétaire choisit un Manager/Employé, décoche les droits à retirer puis confirme l'enregistrement. Annuler abandonne les modifications. Recocher restaure seulement un droit prévu par le rôle : aucun droit supplémentaire ne peut être accordé. Le propriétaire est protégé et les modifications sont refusées si la personne a une caisse ouverte. Les autorisations backend sont recalculées à chaque action ; la navigation est actualisée au focus ou sous 10 secondes.

La consultation Audit permet de filtrer les traces enregistrées par dates, action exacte et identifiant de l'acteur. Les pages sont limitées à 100 lignes. Les détails bruts ne sont pas affichés et aucun nom historique n'est inventé. Utilisateurs renvoie à Équipe, interface canonique de gestion des personnes/comptes.

Les paramètres boutique/SMTP sont validés avant enregistrement. Un export de sauvegarde annulé n'est pas annoncé comme réussi. Cette version de travail reste en validation : ne pas considérer la réinitialisation et la restauration comme nouvellement qualifiées ; consulter les bloqueurs du rapport J.6R-A avant tout essai destructif, uniquement sur profil jetable.

## Présentation

STORE est une application de bureau bilingue français/anglais destinée à la gestion
locale d’une boutique : caisse, stocks, utilisateurs, présences, historiques, rapports,
chat, alertes et sauvegardes. Les données métier restent dans la base SQLite de
l’ordinateur ou du dispositif (tablet, smarphone, ...) sur lequel l’application est installée. Une connexion Internet n’est
nécessaire que pour l’envoi d’e-mails et de notifications.

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

STORE ne déconnecte plus automatiquement l’utilisateur pour une simple période d’inactivité.
Utilisez toujours **Déconnexion** lorsque vous quittez le poste. Il n’existe plus d’accès
Manager temporaire : une opération Manager ou Owner exige de choisir **Changer d’utilisateur**
et d’authentifier réellement ce compte.

Le changement d’utilisateur conserve la session courante si le nouveau mot de passe est
incorrect. Il est bloqué si l’utilisateur courant possède une caisse ouverte ou si un paiement
est en cours. Un panier non vide doit être explicitement abandonné; il n’est jamais transmis
au nouvel utilisateur. La déconnexion est également bloquée tant que la caisse courante reste
ouverte. Ces opérations ne modifient jamais les présences.

Les comptes Manager et Owner peuvent définir une question et une réponse de sécurité
pour renouveler leur mot de passe. Un compte inactif n’apparaît pas dans la zone de
présence et ne doit plus être utilisé pour se connecter.

Après une récupération réussie, STORE revient à la connexion avec tous les champs
vides. Saisissez alors l’identifiant et le nouveau mot de passe. L’œil du champ secret
permet de contrôler la saisie sans modifier sa valeur.

## En-tête et navigation

L’en-tête reste disponible dans la Caisse et le Dashboard. Son bloc de gauche indique
toujours la page courante. Dans le Dashboard, **Retour** reprend la section visitée
précédemment ; le fil d’Ariane permet de revenir directement à l’accueil. Le bouton Chat
revient exactement à la Caisse ou à la section du Dashboard depuis laquelle il a été ouvert :

- la navigation n’affiche que les espaces permis à l’identité actuellement connectée ;
- **Changer d’utilisateur** authentifie une autre personne et recalcule immédiatement sa navigation ;
- **FR/EN** change immédiatement la langue de l’interface et des messages ;
- le thème clair/sombre s’applique à tous les écrans ;
- **Chat** ouvre la messagerie locale ; **Retour** la ferme et revient exactement à
  l’espace et à la page Dashboard précédents ;
- l’icône d’alerte ouvre les alertes système, notamment celles de stock ;

## Traçabilité du stock et des achats

Dans **Stock**, l’état actuel présente la quantité et le seuil réel de chaque Article.
L’onglet **Mouvements** affiche les écritures persistées les plus récentes : date, Article,
type, quantité d’entrée/sortie et référence source lorsqu’elle existe. Une référence de
vente, d’achat ou d’inventaire désigne uniquement l’entité réellement enregistrée; STORE
ne fabrique pas de rapprochement approximatif.

Dans **Achats**, **Détails** ouvre le fournisseur, le statut, la date, l’acteur de création,
les lignes, les quantités, les coûts unitaires et l’effet stock réellement enregistré.
Un brouillon n’affecte pas le stock. Une validation déjà effectuée ne l’incrémente pas une
seconde fois.

Les rapports utilisent la date propre à chaque domaine. Les limites de période sont
inclusives. Dans le rapport Achats, le filtre fournisseur s’applique à la fois aux lignes,
au nombre d’achats et au total validé. Une absence de résultat reste distincte d’une erreur.
STORE n’affiche pas de bénéfice, marge comptable ou valorisation du stock, car aucune règle
comptable suffisante n’est configurée.
- les cercles de présence montrent uniquement les comptes actifs. Leur état vient des
  pointages explicites, jamais de la connexion à STORE.

Une mini-fenêtre ouverte depuis l’en-tête se ferme aussi en cliquant à l’extérieur.

## Caisse

Recherchez les produits par nom, catégorie ou hashtag. Cliquez dans la quantité pour
remplacer rapidement la valeur par défaut, saisissez le nombre souhaité, puis utilisez
**Ajouter**. La quantité est limitée au stock disponible et revient automatiquement à
`1` après l’ajout. Tout article ajouté est automatiquement sélectionné et inclus dans
la facture courante : aucune seconde sélection n’est nécessaire. Décochez seulement une
ligne que vous souhaitez reporter. Les boutons `−` et `+`, ainsi que la saisie
numérique, permettent d’ajuster les quantités.

Une ligne dont la quantité est mise à `0` est automatiquement retirée du panier au
clic sur **Valider l’achat**. Elle n’est ni facturée ni interprétée comme une erreur de
stock.

La remise n’est affichée que si elle a été activée dans **Paramètres**. Son montant et
les valeurs monétaires de toute l’application utilisent la devise choisie.

La validation :

1. contrôle le stock des lignes sélectionnées ;
2. crée la facture ;
3. déduit les quantités vendues ;
4. met à jour les indicateurs, historiques et alertes de stock.

L’aperçu de facture est consultable et imprimable. **Imprimer** ouvre le dialogue natif
de Windows ; **Enregistrer en PDF** produit directement un fichier partageable hors ligne.
Chaque facture conserve l’identité et la devise de la boutique telles qu’elles existaient
au moment de la vente. Une facture annulée reste consultable avec son statut et son motif.
Un clic à l’extérieur de l’aperçu le ferme.

## Mode d’emploi intégré

Le Dashboard contient une page **Mode d’emploi** accessible dans la section Analyse.
Elle regroupe les procédures essentielles, les précautions en cas de coupure, les rôles,
la caisse, les stocks, la messagerie, les rapports et les sauvegardes. Le bouton
**Imprimer le guide** permet d’en conserver une copie papier pour les périodes sans
électricité ou en cas d’absence prolongée du responsable.

### Accessibilité et appareils modestes

STORE respecte le réglage système de réduction des animations et renforce les bordures
si un contraste accru est demandé. Les lignes ouvrables fonctionnent avec **Entrée** ou
**Espace**, les onglets avec les flèches, et les fenêtres maintiennent le focus au clavier.
Sur un petit écran, les zones tactiles sont agrandies et les panneaux se réorganisent.
La caisse affiche les grands catalogues par lots de 60 produits ; utilisez **Afficher plus**
pour charger la suite sans ralentir inutilement les appareils peu puissants.

## Dashboard

### Accueil

Les sous-onglets donnent accès à :

- **Produits** : liste alphabétique avec recherche par produit, catégorie ou hashtag.
  Un clic sur une ligne ouvre tous les attributs en lecture seule ;
- **Stock faible** : articles au seuil minimal, quantité actuelle et date/heure de
  détection ;
- **Ventes aujourd’hui** : chiffre d’affaires et factures du jour en lecture seule.

Les fiches de présentation se ferment en cliquant à l’extérieur.

La carte **Chiffre d’affaires mensuel** compare le mois courant au mois précédent et
donne accès directement à l’analyse détaillée.

### Stocks

La liste alphabétique permet de rechercher, créer et administrer les articles, catégories,
prix, quantités, seuils et hashtags. Un clic sur une ligne ouvre une fiche récapitulative
en lecture seule. Les doublons sont refusés avec un message explicite.

Chaque article peut recevoir une photo JPEG, PNG ou WebP de 5 Mo maximum. Lors de la
création ou modification, utilisez **Choisir une photo**, contrôlez l’aperçu, puis
enregistrez. Une photo existante peut être conservée, remplacée ou retirée. STORE copie
la photo dans ses propres données : ne déplacez pas manuellement les fichiers du dossier
`media`. Sans photo ou en cas de fichier manquant, l’article reste utilisable avec son
placeholder.

L’import et l’export PDF permettent de transférer ou d’archiver l’état du stock. Vérifiez
toujours l’aperçu et la devise avant de réutiliser un document importé.

### Utilisateurs

La recherche se trouve au-dessus de la liste. Les sous-onglets **Employés**,
**Managers** et **Administrateur** filtrent les comptes sans retirer les fonctions de
création, modification, activation et renouvellement du mot de passe.

Un clic sur une ligne affiche une fiche synthétique de l’utilisateur (identité,
coordonnées, rôle, statut et date d’embauche). Cliquez hors de cette fiche pour la fermer.

Lors de la création d’un Employee, un mot de passe temporaire est généré. La fenêtre
reste visible 60 secondes, avec bouton de copie, compteur et barre de progression, puis
se ferme automatiquement. Pour un Manager, l’e-mail et la question/réponse de sécurité
sont obligatoires. Les doublons de nom/prénom, username ou e-mail sont refusés.

L’action **Nouveau mot de passe** propose **Automatique**, qui génère un secret temporaire
affiché une seule fois, ou **Manuel**, qui exige la saisie et la confirmation d’au moins
huit caractères. Un Manager peut agir sur un Employee; seul l’Owner peut agir sur un
Manager. Le Primary Owner est exclu de ce flux administratif et conserve sa procédure de
récupération protégée.

### Fiche de présence

La connexion et la déconnexion à STORE ne pointent personne. Dans **Présences**, la
fiche du jour affiche simultanément la personne, son rôle, l’entrée, la sortie, la durée
et l’état. Utilisez **Entrée** ou **Sortie** sur la ligne concernée. La personne doit
saisir son propre mot de passe; le backend vérifie que le secret appartient au compte
sélectionné et enregistre l’heure actuelle.

Plusieurs personnes peuvent rester présentes en même temps, même si une seule autre
personne utilise actuellement l’interface. Fermer STORE, se déconnecter ou subir une
expiration de session ne ferme pas une présence : chaque personne doit effectuer sa
**Sortie**. L’onglet Historique conserve les anciens pointages. Les corrections de dates
passées sont distinctes, motivées et disponibles uniquement avec `PRESENCE:UPDATE`.

### Historiques

Choisissez une date de début et de fin, puis un sous-onglet :

- **Ventes** : factures et articles vendus ;
- **Achats** : entrées et ravitaillements de stock ;
- **Personnel** : présences et temps de travail.

Les cases de sélection, dont **Tout sélectionner**, déterminent le contenu exporté ou
traité. Une vente n’est jamais supprimée : elle est annulée avec un motif, son paiement
est neutralisé et son stock restitué. Pour les autres historiques supprimables, effectuez
d’abord une sauvegarde si les données doivent être conservées.

### Rapports

Les filtres journalier, hebdomadaire, mensuel ou personnalisé pilotent les rapports :

- entrées, sorties et mouvements des articles ;
- quantités, prix unitaires, valeurs totales et valeur mouvementée ;
- articles et catégories les mieux vendus ;
- évolution des prix unitaires.
- diagnostic décisionnel : tendance du chiffre d’affaires, concentration des ventes,
  proportion de stocks faibles, unités moyennes par facture et actions recommandées ;
- marge brute estimée à partir du dernier coût d’approvisionnement connu, taux de marge,
  taux d’écoulement et nombre de références en rupture.

Le chiffre d’affaires est calculé après remises et exclut les ventes annulées. La tendance
compare la période filtrée à la période immédiatement précédente de même durée. La marge
reste une estimation : avant le premier approvisionnement renseigné, STORE utilise la
meilleure valeur locale disponible et ne doit pas être considéré comme un outil comptable.

Les rapports sélectionnés peuvent être visualisés sous forme de graphiques, exportés en
PDF ou envoyés par e-mail. **Tout sélectionner** inclut tous les rapports visibles dans
le filtre courant.

### Chat

- **Boîte de réception** : messages locaux entre utilisateurs ;
- **Alertes** : historique des alertes système et de stock ;
- **E-mails de rapports** : historique des envois de rapports réussis.

Quand Internet ou le serveur SMTP est indisponible, les rapports PDF et alertes externes
sont conservés dans une file locale. STORE réessaie automatiquement toutes les deux
minutes avec un délai progressif. Les statuts **En attente**, **Envoi en cours**,
**Envoyé** et **Échec** sont visibles dans la gestion des e-mails. Après cinq échecs,
utilisez **Réessayer les e-mails** lorsque la connexion ou la configuration SMTP a été
corrigée. Fermer STORE n’efface pas la file d’attente.

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

N’utilisez pas le mot de passe principal d’une boîte personnelle. Lorsque Windows le
permet, STORE chiffre le mot de passe SMTP avec le coffre sécurisé du système. Un repli
local existe pour les environnements incompatibles : protégez donc le compte Windows,
l’appareil et les sauvegardes dans tous les cas.

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
| TLS implicite     | À activer si le fournisseur l’exige   | activé avec le port `465`        |
| Utilisateur SMTP  | Adresse complète du compte expéditeur | `boutique.epices.demo@gmail.com` |
| Mot de passe SMTP | Mot de passe d’application            | `xxxx xxxx xxxx xxxx` (fictif)   |
| Expéditeur        | Adresse affichée comme émetteur       | `boutique.epices.demo@gmail.com` |
| E-mail boutique   | Destination du test                   | `direction@epices-demo.fr`       |

Utilisez généralement STARTTLS sur le port `587`. Si le fournisseur impose le port
`465`, activez **TLS implicite**. Ne mélangez pas le port et le mode de chiffrement :
une mauvaise combinaison entraîne le plus souvent un refus ou un délai dépassé.

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

Les mots de passe des utilisateurs sont hashés avec bcrypt. Une sauvegarde automatique
est créée chaque jour, y compris lorsque STORE reste ouvert plusieurs jours. Les sept
dernières sauvegardes automatiques sont conservées ; les sauvegardes manuelles ne sont pas
effacées par cette rétention. Une nouvelle sauvegarde est un seul fichier portable
`.store-backup` contenant la base, un manifeste de contrôle et les photos d’articles
référencées. Les anciennes sauvegardes SQLite `.db` ou `.sqlite` restent acceptées;
les articles restaurés depuis ces anciens formats utilisent le placeholder. Les
sauvegardes contiennent les données et paramètres locaux : conservez-les dans un
emplacement chiffré et à accès limité.

Avant une mise à jour importante, une restauration ou une réinitialisation, créez une
sauvegarde et vérifiez que le fichier peut être copié sur un support sûr.

Dans **Paramètres > Données**, le diagnostic affiche l’intégrité SQLite, les versions,
la taille de la base, l’espace disque, les e-mails en attente et les sauvegardes récentes.
**Ouvrir le dossier** montre l’emplacement local exact. **Copier vers un support externe**
permet de choisir une clé USB ou un autre disque : cette copie est indispensable, car une
sauvegarde conservée uniquement sur le même ordinateur ne protège pas contre sa perte ou
la panne complète du disque.

Le diagnostic avertit sous 512 Mo d’espace disponible et devient critique sous 128 Mo.
Dans ce cas, arrêtez les imports, copiez une sauvegarde sur un autre support et libérez de
l’espace avant de poursuivre les ventes.

## Routine quotidienne recommandée

### Ouverture

1. démarrez STORE et vérifiez que la date et l’heure de Windows sont correctes ;
2. ouvrez **Paramètres > Données et diagnostic** et contrôlez l’intégrité, l’espace
   disponible et la date de la dernière sauvegarde ;
3. ouvrez la session de caisse avec le fonds réellement compté ;
4. vérifiez physiquement quelques produits sensibles ou à faible stock ;
5. si Internet est disponible, relancez les e-mails en attente.

### Fermeture

1. terminez les factures en cours et vérifiez la dernière vente ;
2. comptez les espèces, fermez la session de caisse et documentez tout écart ;
3. consultez les alertes de stock et la file d’e-mails ;
4. créez une sauvegarde après une journée importante ;
5. copiez régulièrement cette sauvegarde sur une clé USB ou un autre disque, puis
   éjectez proprement le support.

## Procédure d’urgence

### Après une coupure électrique ou un arrêt brutal

1. rétablissez une alimentation stable avant de redémarrer ;
2. ouvrez STORE une seule fois et laissez l’initialisation se terminer ;
3. ne validez pas à nouveau une vente sans contrôler **Historiques > Ventes**, la
   dernière facture et le stock correspondant ;
4. contrôlez **Paramètres > Données et diagnostic** ;
5. si l’intégrité est correcte, reprenez les opérations et traitez les e-mails restés
   en attente ;
6. si l’intégrité échoue, arrêtez toute nouvelle saisie, conservez la base actuelle et
   restaurez uniquement une sauvegarde connue et vérifiée.

### Pendant une coupure Internet

La caisse, les stocks, les historiques et le chat local restent utilisables. Les e-mails
sont conservés dans une file locale et pourront être relancés. Ne répétez pas plusieurs
fois le même envoi : consultez d’abord son état dans la gestion des e-mails.

### En cas d’appareil très lent ou de disque presque plein

Fermez les logiciels non indispensables. Si le diagnostic est critique, arrêtez les
imports et les nouvelles ventes, copiez une sauvegarde sur un support externe, puis
libérez de l’espace. Ne supprimez jamais manuellement les fichiers de données STORE.

## Relais pendant une absence prolongée du propriétaire

Avant l’absence :

1. désignez au moins un Manager avec un compte nominatif et testez sa connexion ;
2. remettez-lui ce guide, les procédures d’ouverture/fermeture et les contacts
   d’assistance ;
3. montrez l’emplacement des sauvegardes externes et réalisez un exercice de copie ;
4. conservez la réponse de récupération Owner dans un lieu physique ou numérique
   sécurisé, accessible uniquement à la personne autorisée ;
5. documentez le fournisseur SMTP et la procédure de révocation du mot de passe
   d’application, sans inscrire le secret dans le guide ;
6. ne partagez pas le mot de passe Owner. Créez ou désactivez des comptes nominatifs.

Le Manager peut assurer les opérations courantes. Les actions sensibles réservées à
l’Owner, telles que la réinitialisation complète, doivent attendre son retour ou suivre
une procédure d’urgence formellement autorisée par l’organisation.

## Dépannage rapide

| Problème | Vérification et conduite à tenir |
| --- | --- |
| STORE ne démarre pas | Consultez `technical.jsonl` dans le dossier des données STORE et conservez une copie avant toute intervention. |
| Connexion refusée | Vérifiez Verr. Maj et la disposition du clavier, utilisez l’œil, puis **Mot de passe oublié** pour l’Owner. |
| Impression absente | Vérifiez l’imprimante par défaut dans Windows ou choisissez **Microsoft Print to PDF** dans le dialogue d’impression. |
| Vente incertaine après incident | Recherchez d’abord la dernière vente dans l’historique ; ne validez pas une deuxième fois par réflexe. |
| E-mail non reçu | Consultez la file, testez SMTP, vérifiez spam/quarantaine puis utilisez **Réessayer les e-mails**. |
| Restauration refusée | Le fichier est invalide ou incompatible ; la base active reste protégée. Utilisez une autre sauvegarde vérifiée. |
| Diagnostic d’intégrité en erreur | Arrêtez les saisies, copiez les fichiers existants et restaurez une sauvegarde saine. |

Pour toute assistance, ne transmettez jamais de mot de passe, de réponse de récupération
ou de secret SMTP. Fournissez la version de STORE, l’heure de l’incident, l’action en
cours et uniquement les lignes pertinentes du journal technique.
