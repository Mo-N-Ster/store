# STORE 3.0 — Checklist humaine de validation visuelle J.6

## Mode d’emploi

Exécuter cette checklist sur l’application de développement construite depuis la version 2.0.1. Utiliser uniquement un profil de test. Ne jamais cocher PASS sans observation réelle. Pour chaque test, renseigner **Résultat réel**, **PASS/FAIL** et **Notes**; joindre éventuellement une référence de capture sans modifier l’application.

## Matrice d’exécution

### J6-VIS-001 — Connexion

- **Écran/workflow :** Login et authentification Employee A
- **Précondition :** application redémarrée, aucun compte connecté
- **Viewport :** 1920×1080 puis 800×1280
- **Thème :** clair et sombre
- **Étapes :** parcourir au clavier; afficher/masquer le mot de passe; échouer puis réussir la connexion
- **Résultat attendu :** aucun chevauchement; erreur associée; focus visible; champs utilisables; destination Employee correcte
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-002 — POS, panier et caisse

- **Écran/workflow :** Caisse, ouverture de caisse, catalogue, panier
- **Précondition :** Employee A connecté, caisse fermée
- **Viewport :** 1280×800, 1024×768, 800×1280
- **Thème :** clair
- **Étapes :** ouvrir la caisse; rechercher; ajouter/modifier/supprimer plusieurs Articles; ouvrir le panier portrait
- **Résultat attendu :** actions primaires visibles; quantité lisible; aucun débordement de page; drawer utilisable; propriétaire cohérent
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-003 — Blocages identité

- **Écran/workflow :** Changer d’utilisateur et Déconnexion avec panier/caisse
- **Précondition :** panier non vide puis caisse ouverte
- **Viewport :** 1024×768
- **Thème :** sombre
- **Étapes :** demander switch; refuser puis confirmer l’abandon; réessayer avec caisse ouverte; essayer logout
- **Résultat attendu :** confirmation panier explicite; aucun transfert; messages caisse actionnables; identité inchangée
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-004 — Checkout

- **Écran/workflow :** paiement et intégrité acteur
- **Précondition :** caisse ouverte, panier valide
- **Viewport :** 1280×800 et 800×1280
- **Thème :** clair
- **Étapes :** ouvrir checkout; essayer switch/logout; annuler; recommencer et valider une vente
- **Résultat attendu :** identité non modifiable pendant checkout; boutons non coupés; résultat déterministe; facture lisible
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-005 — Changement d’utilisateur

- **Écran/workflow :** Employee→Manager→Employee et échec
- **Précondition :** panier vide, caisse fermée
- **Viewport :** 1366×768 et 800×1280
- **Thème :** clair/sombre
- **Étapes :** mauvais mot de passe; bon mot de passe Manager; ouvrir page privilégiée; revenir Employee
- **Résultat attendu :** ancienne session conservée après échec; navigation recalculée; dialogues privilégiés fermés; aucune élévation/minuterie
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-006 — Présences

- **Écran/workflow :** fiche du jour, pointage A/B, historique et correction
- **Précondition :** comptes de test actifs
- **Viewport :** 1280×800, 1024×768, 800×1280
- **Thème :** clair et contraste élevé
- **Étapes :** filtrer; pointer deux personnes; sortir l’une; tester mauvais secret; ouvrir correction autorisée
- **Résultat attendu :** plusieurs personnes lisibles; états textuels; dialogue compact; aucune superposition ou scroll horizontal de page
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-007 — Liste et détail Article

- **Écran/workflow :** liste, détail et édition média
- **Précondition :** Articles JPEG, PNG/WebP, sans image et référence média manquante
- **Viewport :** 1920×1080, 1024×768, 800×1280
- **Thème :** clair/sombre
- **Étapes :** rechercher/filtrer; ouvrir détail; choisir/remplacer/retirer image; observer les placeholders
- **Résultat attendu :** miniatures non déformées; fallback stable; actions accessibles; aucun chemin local exposé
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-008 — Stock actuel et alertes

- **Écran/workflow :** État actuel et Alertes
- **Précondition :** Articles disponible/bas/épuisé
- **Viewport :** 1280×800 et 800×1280
- **Thème :** sombre et contraste élevé
- **Étapes :** filtrer par état; rechercher; ouvrir ajustement
- **Résultat attendu :** quantité/seuil/état lisibles sans couleur seule; champs et actions non coupés
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-009 — Mouvements stock

- **Écran/workflow :** historique et filtres de mouvements
- **Précondition :** mouvements vente, achat, inventaire, ajustement
- **Viewport :** 1366×768, 1024×768, 800×1280
- **Thème :** clair
- **Étapes :** filtrer chaque type; rechercher Article/référence; contrôler ordre et sens
- **Résultat attendu :** type, entrée/sortie, quantité, date et référence clairs; aucun acteur/avant-après inventé
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-010 — Inventaire

- **Écran/workflow :** brouillon, comptage et validation
- **Précondition :** Manager connecté, inventaire disponible
- **Viewport :** 1280×800 et 800×1280
- **Thème :** clair
- **Étapes :** créer; saisir plusieurs comptages; parcourir au clavier; confirmer validation
- **Résultat attendu :** attendu/compté/différence textuels; focus logique; aucun double submit
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-011 — Achats et fournisseurs

- **Écran/workflow :** liste, brouillon, détail et fournisseur
- **Précondition :** deux fournisseurs et achats de statuts mixtes
- **Viewport :** 1280×800, 1024×768, 800×1280
- **Thème :** clair/sombre
- **Étapes :** filtrer; ouvrir détail; créer brouillon; ajouter lignes; valider; revisiter
- **Résultat attendu :** cycle/statut/acteur/lignes/total/effet stock lisibles; aucune seconde incrémentation
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-012 — Rapports

- **Écran/workflow :** ventes, Articles, stock, achats, équipe
- **Précondition :** données mixtes multi-dates/fournisseurs
- **Viewport :** 1920×1080, 1280×800, 1024×768, 800×1280
- **Thème :** clair/sombre/contraste élevé
- **Étapes :** appliquer date, Article, catégorie et fournisseur; tester résultat vide puis erreur simulable
- **Résultat attendu :** KPI/chart/table cohérents; filtres contextuels; information textuelle; aucune marge/valorisation/score fictif
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-013 — Quick Chat

- **Écran/workflow :** drawer droit et changement d’expéditeur
- **Précondition :** deux comptes, historique de messages
- **Viewport :** 1024×768 et 800×1280
- **Thème :** clair
- **Étapes :** ouvrir; faire défiler; envoyer comme A; fermer; switch B; envoyer comme B
- **Résultat attendu :** espace préservé; scroll indépendant; auteur/heure clairs; aucune suppression dans Quick Chat
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-014 — Gestion des messages

- **Écran/workflow :** interface Messages autorisée
- **Précondition :** Manager avec messages
- **Viewport :** 1280×800 et 800×1280
- **Thème :** sombre
- **Étapes :** parcourir chats/alertes/emails; vérifier actions; comparer Quick Chat
- **Résultat attendu :** suppression seulement dans le contexte autorisé; onglets sans doublons; aucune superposition
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-015 — Mots de passe

- **Écran/workflow :** modes automatique et manuel
- **Précondition :** compte test et acteur autorisé
- **Viewport :** 1024×768 et 800×1280
- **Thème :** clair
- **Étapes :** tester politique/confirmation; générer temporaire; fermer; tester l’œil et erreurs
- **Résultat attendu :** secret lisible seulement temporairement; labels/erreurs/focus corrects; actions accessibles
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-016 — Sauvegarde et restauration

- **Écran/workflow :** Administration, backups, restauration
- **Précondition :** Owner, profil test avec médias
- **Viewport :** 1366×768 et 800×1280
- **Thème :** clair/sombre
- **Étapes :** créer bundle; choisir restauration valide puis invalide; observer confirmations/erreurs
- **Résultat attendu :** étapes compréhensibles; action destructive clairement confirmée; aucune action coupée
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-017 — Navigation, aide et menu utilisateur

- **Écran/workflow :** shell, Retour, Aide, identité, thème/langue
- **Précondition :** session active
- **Viewport :** cinq tailles cibles
- **Thème :** clair/sombre
- **Étapes :** parcourir toutes destinations; retour; ouvrir menu; aide; changer langue/thème
- **Résultat attendu :** page courante claire; identité visible; aucune duplication; menu hors-clic; aucune coupure
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

### J6-VIS-018 — Accessibilité transversale

- **Écran/workflow :** navigation, modales et drawers critiques
- **Précondition :** préférence reduced-motion activée puis contraste élevé
- **Viewport :** 1280×800 et 800×1280
- **Thème :** clair/sombre/contraste élevé
- **Étapes :** utiliser seulement Tab/Shift+Tab/Entrée/Espace/Échap; ouvrir/fermer chaque dialogue et drawer
- **Résultat attendu :** focus visible, ordre logique, piège/restauration corrects, noms accessibles, cibles tactiles, animations non nécessaires
- **Résultat réel :** À renseigner
- **PASS/FAIL :** À renseigner
- **Notes :** À renseigner

## Synthèse humaine

- **Application/build observé :** À renseigner
- **Date et observateur :** À renseigner
- **Nombre PASS :** À renseigner
- **Nombre FAIL :** À renseigner
- **Défauts BLOCKER/CRITICAL :** À renseigner
- **Conclusion humaine :** À renseigner

## Complément J.6R-A — validation humaine requise (NON EXÉCUTÉE)

### Ajouts du 27 septembre — À RENSEIGNER

- Accueil : bouton centré vers la fiche générale du jour, y compris sans personne présente ; entrée/sortie authentifiée ; retour vers l'accueil ; clic individuel toujours filtré.
- Carte « Contexte du mois » immédiatement sous les présents, marges stables en portrait/paysage, défilements indépendants.
- Audit : responsable identique au compte connecté lors de l'action ; ancienne trace « Non enregistré » ; session de caisse, montant théorique et devise figés ; distinguer montant théorique et comptage physique.
- Connexion, changement d'utilisateur et récupération : identifiant/nom complet en casse différente ; accents ; mot de passe incorrect toujours refusé ; identité ambiguë refusée.


Tous les résultats restent À RENSEIGNER. Utiliser un profil jetable pour les changements destructifs.

- Propriétaire : ouvrir Permissions, identifier utilisateur/rôle, comprendre droits hérités/retraits/résultat ; Save/Cancel, changement de cible non sauvegardée, erreur/conflit.
- Retirer un droit à un utilisateur sans caisse ouverte ; contrôler actualisation de navigation au focus et dans les 10 secondes, refus backend, fermeture des interfaces interdites et persistance après redémarrage.
- Manager/Employé : édition refusée ; propriétaire protégé ; modification du titulaire d'une caisse ouverte refusée.
- Administration > Utilisateurs mène à Équipe ; création/édition, mots de passe manuel/automatique sur comptes jetables.
- Paramètres boutique/contact/devise : sauvegarde/relecture et consommation sur facture ; remises désactivées, historique inchangé.
- SMTP : aucun mot de passe relu en clair, champ vide conserve le secret ; saisies invalides et test réussi/échoué avec serveur autorisé, erreurs sans secret.
- Sauvegardes : créer/lister/exporter ; annulation sans faux succès ; échec de copie sans perte de source.
- Restauration/réinitialisation : validation de sûreté suspendue jusqu'à fermeture des bloqueurs J.6R-A ; ensuite profil jetable uniquement, confirmation/authentification/sauvegarde préalable/erreurs injectées.
- Diagnostics en lecture seule ; audit dates/action/acteur, chargement/vide/erreur/pagination, sans attribution historique inventée.
- Tablette portrait/paysage, débordements, focus clavier et cases Save/Cancel ; hiérarchie claire des actions destructives.

Observateur/date/build, preuves et résultats PASS/FAIL : À renseigner.
