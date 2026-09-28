# STORE 3.0 Functional Baseline

Étape 2 — extraction fonctionnelle, 28 septembre 2026. Analyse et documentation uniquement. Ce document définit les comportements à préserver ; il ne choisit ni architecture mobile ni composants d'implémentation.

## 1. Purpose

Définir ce que STORE doit continuer à faire et garantir pour Android téléphone et tablette, à partir de STORE 2.0.1 vérifié. La qualification Desktop est une preuve d'origine, **pas une qualification Android**. Aucun développement 3.0 ne découle implicitement de ce document.

Les exigences ci-dessous portent sur résultats observables, permissions et intégrité. Les mécanismes particuliers de 2.0.1 mentionnés dans les références ne sont pas des choix technologiques pour 3.0. Une limite n'est ni une autorisation de supprimer une capacité opérationnelle ni une demande implicite de l'améliorer.

## 2. Product Boundary

STORE 3.0 : Android smartphone + Android tablet, touch-first, interfaces adaptatives, standalone/local-first, base locale propre à chaque installation.

```text
STORE Desktop 2.0.1 → données locales A
STORE Android 3.0  → données locales B
                    aucune liaison de synchronisation A ↔ B
```

Comptes, droits, opérations et historiques sont indépendants d'une installation à l'autre. Aucun serveur central, compte cloud, base partagée, cohérence multi-appareil ou synchronisation multi-boutique. Les livraisons e-mail restent une fonction réseau facultative, pas une dépendance du cœur métier. Exporter un document/sauvegarde n'est pas synchroniser des installations.

## 3. Relationship to STORE 2.0.1

| Référence | Valeur / portée |
|---|---|
| Produit source | STORE by VIBE 2.0.1 — FUNCTIONALLY QUALIFIED, UNSIGNED |
| Frozen functional source | `63b3849ee234248a3b07a643e17dd22fb8c7b23d` |
| Clôture documentaire | `06c28bbd2272cab0923c905afed9498131909534` |
| Données source | Migration 18 ; ce numéro n'impose pas le schéma mobile |
| Preuves existantes | 291 PASS / 0 FAIL / 0 SKIP, RF-004 CLOSED, lint/build et audits production/tooling à zéro |
| Qualification physique source | Windows x64 installation/startup, migration réelle 13→18, uninstall/reinstall et acceptation globale attestés par l'utilisateur à la clôture |
| Limite de qualification | Ancien binaire → nouvel installateur in-place NOT TESTED ; scénarios humains non individuellement attestés non inventés |

Priorité : comportement actuel vérifié > contrats/tests actuels > baseline > documents historiques. Les passages de CURRENT_APPLICATION_BASELINE sur un arbre non gelé, RF/outillage non clos ou RC manquante sont des snapshots historiques, remplacés pour la livraison par la clôture. Les règles métier restent la référence. STORE 2.0.1, son artefact, son statut et ses sources ne sont pas modifiés ici.

## 4. Actors and Roles

Owner = ADMIN ; Manager = MANAGER ; Employee = CASHIER. « Backend/domain » signifie ici **frontière de confiance locale**, pas serveur distant. L'acteur d'une action est dérivé de l'identité authentifiée, jamais du rôle/identifiant que soumet une interface.

R=lecture, C=création, U=modification, D=suppression/annulation selon contrat, V=validation. Les codes hérités ne créent pas une fonction inexistante et ne remplacent pas les restrictions sur cible/état.

| Domaine de permission source | Owner | Manager | Employee |
|---|---|---|---|
| DASHBOARD | RCUDV | RCUDV | R |
| PRESENCE | RCUDV | R | R |
| CASH | RCUDV | RCUDV | RCUV |
| POS | RCUDV | RCUDV | RCUV |
| PRODUCTS | RCUDV | RCUDV | R |
| STOCKS | RCUDV | RCUDV | R |
| PURCHASES | RCUDV | RCUDV | — |
| EMPLOYEES | RCUDV | RCUDV | — |
| FINANCES | RCUDV | RCUDV | — |
| ADMINISTRATION | RCUDV | — | — |
| SETTINGS | RCUDV | — | — |
| BACKUPS | RCUDV | — | — |
| RESTORE | RCUDV | — | — |
| RESET | RCUDV | — | — |

Droits effectifs = hérités moins refus individuels, pour compte actif. Owner protégé ; seul Owner gère les refus et aucun droit supplémentaire hors rôle ne peut être ajouté. Conflit de modification concurrente détecté ; refus de modifier ces droits lorsque la cible possède une caisse ouverte. Manager ne gère pas Owner ni une escalade de rôle.

Employee conserve accueil, caisse, catalogue, présence et aide ainsi que chat local ; pas gestion des comptes, stock opérationnel, achats, rapports, administration ou onglet de gestion Messagerie. Lire certaines informations stock n'autorise pas les ajustements. Présence signée demande le secret personnel même lorsqu'une fiche partagée est consultable. Correction présence réservée à l'autorisation de modification, non au Manager par défaut.

Il n'existe **aucune élévation temporaire**, ni remplacement de l'acteur par une autorisation prêtée. L'absence du propriétaire ne crée pas automatiquement de délégation.

## 5. Functional Domain Map

31 domaines : A identité ; B permissions ; C sessions ; D accueil ; E caisses ; F ventes ; G factures ; H articles ; I médias ; J stock ; K mouvements ; L inventaires ; M achats ; N fournisseurs ; O équipe ; P présences ; Q rapports ; R messagerie/notifications ; S e-mails ; T paramètres ; U audit ; V backups ; W restore ; X reset ; Y diagnostic ; Z localisation ; AA accessibilité/interactions ; AB frontière de sécurité ; AC persistance ; AD récupération ; AE aide/navigation.

Décisions : **PRESERVE** = comportement maintenu ; **PRESERVE WITH PLATFORM ADAPTATION** = même capacité/garantie, interactions ou cycle de vie à adapter sans choisir de technologie ; **DEFER** = extension non exigée actuellement ; **NOT APPLICABLE** = particularité Desktop sans exigence mobile. Une adaptation n'autorise pas une réduction des droits, de l'intégrité ou des fonctionnalités.

## 6. Domain Requirements

Pour chaque domaine : finalité/acteurs, capacités/règles, invariants/sécurité/persistance, échec, limites, décision. La matrice §4 s'applique même lorsqu'un acteur est abrégé O/M/E.

### A — Identité / authentification

- Finalité/acteurs : initialiser l'installation puis identifier O/M/E ; bootstrap uniquement tant qu'aucun Owner n'existe.
- Capacités/règles : création Owner, login, récupération par question/réponse pour comptes éligibles, définition/génération autorisée de password. Identifiant/e-mail/nom complet insensibles à la casse, espaces normalisés, accents conservés ; password sensible à la casse ; ambiguïté et compte inactif refusés. Protection de login actuelle : défaut 5 échecs/15 minutes, politique bornée ; récupération également limitée.
- Invariants/sécurité/persistance : secrets de vérification protégés, identité publique minimale, compte/verrous durables ; pas de password par défaut ni deuxième bootstrap. Password minimum 8 caractères ; émission temporaire explicite et autorisée, distincte du DTO public.
- Échec : aucune session accordée, message sûr, formulaire réessayable avec saisie continue/œil fonctionnel ; après récupération retour login propre.
- Limites : Owner et paramètres de setup ne sont pas un commit global ; pas de reprise complète du wizard après Owner créé ; pas de remplacement forcé prouvé au premier login.
- Décision : **PRESERVE**.

### B — Rôles / permissions

- Finalité/acteurs : O administre les droits, O/M/E consomment leurs seuls droits effectifs.
- Capacités/règles : matrice §4, refus subtractifs uniquement, protection Owner, restrictions des cibles Manager, droits relus pour chaque opération protégée.
- Invariants/sécurité/persistance : aucune permission créée par l'UI, refus persistés, état/rôle attendu vérifié avant modification ; caisse ouverte de la cible bloque l'édition des refus.
- Échec : refus sans mutation de l'opération métier ; une trace de refus de sécurité peut être enregistrée.
- Limites : visibilité et droit d'action distincts ; un code de permission ne crée pas un endpoint métier.
- Décision : **PRESERVE**. Origine S1/S2 (§15).

### C — Session behavior

- Finalité/acteurs : O/M/E travaillent sous une identité réelle, changent d'utilisateur ou se déconnectent.
- Capacités/règles : switch réauthentifie la cible et remplace entièrement les droits seulement après succès ; échec garde l'ancienne session. Caisse ouverte bloque switch/logout ; paiement en cours verrouille ; panier non vide exige confirmation de perte.
- Invariants/sécurité/persistance : identité authentifiée ≠ présence ≠ propriétaire caisse. Aucune élévation temporaire ni expiration automatique d'inactivité J.4 ; données métier restent durables même si session volatile.
- Échec : retry et focus utilisables, double soumission empêchée ; pas d'acteur intermédiaire privilégié.
- Limites : panier/session non durables après arrêt ; règles d'arrière-plan mobile à expliciter sans réintroduire un ancien timeout.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### D — Home/dashboard

- Finalité/acteurs : O/M/E lisent l'activité autorisée et ouvrent les détails permis.
- Capacités/règles : CA/transactions validées du jour, moyenne (0 si aucune), ruptures, évolution 30 jours, transactions, stock à surveiller, présents et fiche journalière, contexte mensuel. Annulées exclues du CA ; stock courant distinct de période.
- Invariants/sécurité/persistance : KPI dérivés de données locales autorisées, pas une seconde source comptable. Raccourcis respectent droits ; listes à scroll indépendant.
- Échec : chargement/vide/erreur/réessai distincts, pas de vieux résultat présenté comme nouveau snapshot.
- Limites : pas refresh universel de tous KPI ; présents actualisés à 30 s dans la source, calendrier local/UTC pas entièrement unifié.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### E — Cash sessions

- Finalité/acteurs : O/M/E autorisés attribuent ouverture, encaissement et clôture à une personne.
- Capacités/règles : montant initial non négatif, une caisse ouverte par acteur ; réouverture répétée retourne celle existante. Théorique = ouverture + paiements capturés ; clôture saisit compté, attendu/écart et auteur/date.
- Invariants/sécurité/persistance : caisse durable, propriétaire fiable, snapshots de clôture et audit ; switch/logout interdits tant qu'ouverte.
- Échec : montant invalide/refus d'état sans clôture partielle.
- Limites : montant théorique n'est pas comptage physique ; pas comptabilité complète de remboursements historiques après clôture.
- Décision : **PRESERVE**.

### F — POS / sales

- Finalité/acteurs : O/M/E disposant de validation vente encaissent des espèces.
- Capacités/règles : recherche nom/catégorie/hashtag, catégorie, ajout 1 depuis article et quantité au panier, retrait quantité zéro, lignes positives automatiquement retenues ; remises montant/pourcentage si activées, reçu/monnaie. Entiers pour quantités ; virgule/point pour montant selon saisie supportée.
- Invariants/sécurité/persistance : nouvelle vente exige acteur autorisé, sa caisse ouverte, lignes valides/stock suffisant ; facture/lignes/paiement/mouvements/stock/proof de commande cohérents dans une seule validation durable. Double submit UI ne remplace pas RF004 (§8).
- Échec : refus conserve contexte à corriger, aucune vente partielle ; succès seul vide panier/réinitialise remise. Réponse perdue : consulter/rejouer la même commande prouvée, jamais supposer absence de commit.
- Limites : espèces seulement, pas quantités fractionnaires ; panier non durable, pas terminal bancaire.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### G — Invoices / cancellation

- Finalité/acteurs : vendeurs consultent leurs capacités autorisées, O/M autorisés annulent ; E n'a pas suppression POS par défaut.
- Capacités/règles : référence unique, date/heure, vendeur/boutique/devise et lignes en snapshots, remise/reçu/monnaie ; annulation motivée (≥3 caractères), compensation une fois, facture conservée. Actions reçu : Imprimer, Nouvelle vente (action verte), PDF, séparées et non ambiguës.
- Invariants/sécurité/persistance : annulation ≠ effacement ; stock et paiement remboursé cohérents, répétition sans double compensation. PDF/impression en palette claire quel que soit thème ; historique réimprimable.
- Échec : impression/PDF raté ne rejoue/n'annule pas la vente ; refus d'annulation ne laisse pas demi-compensation.
- Limites : facture achat PDF autonome non établie ; historique financier non équivalent à comptabilité réglementaire.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**, pas retrait de la fonction documentaire/impression.

### H — Products

- Finalité/acteurs : O/M gèrent, E consulte le catalogue.
- Capacités/règles : nom, catégorie, hashtag, description, prix, quantité, seuil, image ; doublons nom normalisé/hashtag non vide ; fiche/recherche/filtres/création/édition/archive. Import/export PDF STORE structuré, pas PDF arbitraire/OCR.
- Invariants/sécurité/persistance : données validées, prix/historique et stock cohérents ; archive sort le produit actif et produit un mouvement pour retrait stock sans effacer les références historiques.
- Échec : doublon ou import non conforme refusé ; pas promesse de restauration complète par import catalogue.
- Limites : catégories textuelles, CSV backend d'origine sans parcours utilisateur établi ; n'en faire pas une nouvelle exigence mobile.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION** ; extension UI CSV **DEFER**.

### I — Product media

- Finalité/acteurs : O/M attachent/remplacent/retirent images, lecteurs autorisés les consultent.
- Capacités/règles : JPEG/PNG/WebP, plafond source 5 Mio, contrôle du contenu attendu, prévisualisation et fallback ; original sélectionné jamais détruit.
- Invariants/sécurité/persistance : contenu copié de façon contrôlée avant référence durable ; référence active/archivée protégée contre nettoyage. Après commit, échec de cleanup n'annule pas la réussite et ne supprime pas l'image active ; backup inclut les médias requis.
- Échec : avant commit compenser seulement la copie non référencée ; après commit conserver l'orphelin récupérable plutôt que détruire une référence valide.
- Limites : validation de signature, pas décodeur exhaustif ; pas réparation/collecte automatique universelle ni garantie physique multi-support absolue.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION** (RF001).

### J — Stock

- Finalité/acteurs : O/M ajustent, lecteurs autorisés surveillent disponible/faible/rupture.
- Capacités/règles : quantités entières non négatives, seuil, ajustement justifié à une quantité cible ; effets de ventes/réceptions/compensations/inventaires ; alertes.
- Invariants/sécurité/persistance : autorisation et validation centrales, mise à jour stock et mouvement cohérents ; aucune UI ne force un stock négatif.
- Échec : stock insuffisant/entrée invalide refusé atomiquement.
- Limites : stock applicatif n'est pas preuve du stock physique, pas unités pondérales fractionnaires.
- Décision : **PRESERVE**.

### K — Stock movements

- Finalité/acteurs : lecteurs autorisés expliquent entrées/sorties, O/M disposent de suppression historique limitée selon droits.
- Capacités/règles : types initial, ajustement/motif, vente, annulation vente, achat, annulation achat, inventaire, archive produit ; référence, date, article ; filtres temps/article/catégorie/type.
- Invariants/sécurité/persistance : types connus seulement ; code source stable et libellé traduit. Suppression historique achats ne sélectionne jamais présence par défaut ni ressource arbitraire ; présences interdites à cette suppression même pour Owner.
- Échec : discriminateur inconnu/manquant/malformé → refus sans mutation métier.
- Limites : historique mouvements effaçable par fonction autorisée ; pas journal global append-only ; supprimer un mouvement ne compense pas un achat.
- Décision : **PRESERVE** (RF003).

### L — Inventories

- Finalité/acteurs : O/M autorisés comparent comptage au stock enregistré.
- Capacités/règles : draft, lignes comptées persistées, écarts/revue, validation unique ; réconciliation avec stock courant lors de validation.
- Invariants/sécurité/persistance : draft sans effet de réconciliation ; seconde validation refusée ; stock/mouvements cohérents.
- Échec : transaction refusée sans application partielle ; draft durable n'autorise pas une double validation.
- Limites : compte initial prérempli n'est pas comptage physique ; reprise éditeur après restart et annulation opérationnelle non complètes.
- Décision : **PRESERVE** ; amélioration de reprise/annulation **DEFER** sans effacer les drafts durables.

### M — Purchases

- Finalité/acteurs : O/M autorisés enregistrent réception de marchandises.
- Capacités/règles : fournisseur actif facultatif, facture fournisseur/note, draft, lignes entières/coût non négatif/total, création article contextualisée, validation, détail et annulation motivée.
- Invariants/sécurité/persistance : draft ne touche pas stock ; validation répétée ne reçoit pas deux fois ; annulation validée soustrait si stock suffisant ou refuse tout ; draft annulé sans compensation ; statut conservé.
- Échec : aucune réception/compensation partielle ; champs de création imbriquée conservés.
- Limites : draft consultable mais pas réouverture éditeur complète ; déduplication création achat n'a pas une preuve d'équivalence identique à RF004 vente ; ne pas l'affirmer.
- Décision : **PRESERVE** ; meilleure reprise draft **DEFER**.

### N — Suppliers

- Finalité/acteurs : O/M gèrent partenaires d'achat.
- Capacités/règles : nom unique, téléphone/e-mail/adresse/actif, recherche/édition ; créer depuis sélection achat et sélectionner le résultat sans perdre contexte.
- Invariants/sécurité/persistance : fournisseur sélectionné valide, écritures autorisées et tracées.
- Échec : validation refuse sans transformer draft en réception.
- Limites : pas échéancier de dettes/paiements bancaires fournisseurs.
- Décision : **PRESERVE**.

### O — Team / employee profiles

- Finalité/acteurs : O/M gèrent cibles permises ; E n'administre pas comptes.
- Capacités/règles : compte authentifiant distinct du profil employé lié ; identité/contact/embauche/photo/rôle/actif, code/statut RH ; fiche et filtres ; password manuel ou temporaire explicitement délivré. Owner protégé.
- Invariants/sécurité/persistance : pas duplication d'éditeurs contradictoires ; états ACTIVE/ABSENT/SUSPENDED/RESIGNED/ARCHIVED ne sont pas les permissions ; photo validée (source JPEG/PNG/WebP ≤512 Kio), pas secret dans fiche publique.
- Échec : cible interdite/password/photo invalides refusés sans privilège accordé.
- Limites : pas paie, obligations RH ou changement forcé de password à première connexion établi.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### P — Presence / attendance

- Finalité/acteurs : personne signe, opérateur facilite, Owner corrige selon droits.
- Capacités/règles : fiche du jour/personnelle/gestion, entrée/sortie à heure applicative courante avec password propre au signataire, historique/filtres/durée ; correction justifiée conserve originaux et correcteur.
- Invariants/sécurité/persistance : login/logout/fermeture app ne créent ni ne terminent automatiquement présence ; acteur connecté distinct du signataire. Sessions ouvertes exclues du total heures terminées ; statuts VALID/CORRECTED/INTERRUPTED et ouverts lisibles.
- Échec : mauvais password/intervalle interdit → aucun pointage fictif ; saisie et retry restent possibles.
- Limites : oublis de sortie nécessitent action/correction, pas idempotence générale démontrée des corrections ni score de productivité.
- Décision : **PRESERVE**.

### Q — Reports / KPIs

- Finalité/acteurs : O/M autorisés analysent sans confondre gestion et comptabilité.
- Capacités/règles : sept domaines Synthèse/Ventes/Articles/Stock/Achats/Équipe/Finances ; dates/grain/appliquer/raccourcis/reset, article/catégorie selon onglet, fournisseur et employé/statut selon données. Graphes, séries distinctes, Revenue/quantité, courbes prix superposées, tableaux « Données du rapport » dépliables ; PDF et e-mail.
- Invariants/sécurité/persistance : CA validé, annulations distinctes, panier moyen=CA/nombre, zéro si aucun ; allocation remise aux lignes ; marge explicitement estimée depuis coût snapshot/fallback. État stock courant ≠ mouvements de période ; achats par création/statut courant, heures terminées seulement. Rapport dérivé, pas ledger autonome ; filtres appliqués identifiables sur sortie claire.
- Échec : vide/erreur distingués, résultat précédent non réattribué à filtres échoués ; export raté ne change pas opérations.
- Limites : tables/rankings bornés, pas export exhaustif infini ; pas bénéfice « ventes − achats », bilan, taxes ou valorisation comptable validée.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### R — Local messaging / notifications

- Finalité/acteurs : comptes locaux échangent ; gestion alerts selon droits.
- Capacités/règles : individuel/diffusion, objet/contenu bornés (200/10000 source), non lu/lu/suppression personnelle ; gestion chats/alertes/e-mails distincte du chat ; nouvelle rédaction seulement contexte chat. Employee garde chat sans onglet gestion.
- Invariants/sécurité/persistance : expéditeur authentifié, visibilité/état propres au destinataire, messages/alertes durables ; aucune diffusion à d'autres installations.
- Échec : refus sûr, réessai/déduplication selon contrat existant, pas attribution usurpée.
- Limites : chat local, pas messagerie Internet ou notification push distante.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### S — Email/report delivery

- Finalité/acteurs : O configure/teste, O/M avec droits envoient des rapports/relancent les files autorisées.
- Capacités/règles : rapport PDF et contexte/date de demande, queue durable pending/sending/sent/failed, retries ; absence configuration n'épuise pas les essais, erreurs d'envoi bornées (5 dans source), backoff, reprise pending après envoi interrompu.
- Invariants/sécurité/persistance : secret d'envoi isolé ; erreurs ne publient pas réponses sensibles ; pièces jointes contrôlées sans lecture arbitraire de ressources externes ; perte réseau n'annule pas vente/document local.
- Échec : conserver travail en file ou failed explicite ; réessai autorisé, pas faux « envoyé ».
- Limites : réception externe exactement une fois non garantie ; activité app suspendue n'a pas de garantie actuelle de minuterie continue ; configuration source ne force pas chiffrement transport dans tout mode.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**, pas obligation de service central.

### T — Settings

- Finalité/acteurs : O configure boutique et envoi ; utilisateurs choisissent préférences d'affichage prévues.
- Capacités/règles : identité/adresse/contact, devise, activation remise ; paramètres envoi hôte/port/identifiant/secret/expéditeur/mode sécurisé, test autorisé ; thème/langue. Allowlist et validations bornées, pas ajout arbitraire de paramètre.
- Invariants/sécurité/persistance : paramètres métier durables, préférences sans pouvoir d'autorisation, indicateur secret configuré sans retour de sa valeur ; devise changée ne convertit pas histoire.
- Échec : paramètres invalides refusés, erreur test sûre.
- Limites : devises source EUR/XOF/XAF/CAD/GBP/CHF/NGN/GHS ; pas conversion multi-devise historique ni fiscalité intégrée.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### U — Audit

- Finalité/acteurs : O autorisé inspecte responsabilité et incidents, acteurs métier alimentent événements prévus.
- Capacités/règles : date/heure, acteur, action exacte, cible/référence/résultat/motif capturé, responsable connecté et snapshot caisse (référence/théorique/devise), filtres et pagination.
- Invariants/sécurité/persistance : pas secret/détails bruts dans lecture publique ; aucune invention rétroactive de responsable/caisse manquants. Auth/refus/switch/password/caisse/stock/inventaire/achat/fournisseur/correction/permissions couverts selon événements existants.
- Échec : conserver cohérence des actions transactionnelles explicitement auditées ; ne pas présumer journal universel atomique pour toute opération.
- Limites : pas audit exhaustif de chaque lecture/création vente, ni preuve d'inaltérabilité absolue.
- Décision : **PRESERVE**.

### V — Backup

- Finalité/acteurs : O protège/exporte les données locales ; automatisme après Owner actif.
- Capacités/règles : manuel/automatique, snapshot cohérent avec médias requis et manifeste d'intégrité, export ; une automatique par jour de référence, 7 dernières automatiques conservées, pas suppression arbitraire des manuelles/pré-opération.
- Invariants/sécurité/persistance : publication archive seulement après création vérifiable ; ne pas annoncer backup valide si média requis absent ; préparation avant migration/destruction selon contrats.
- Échec : original métier préservé, erreur explicite, archive incomplète non proposée comme complète.
- Limites : même appareil ne protège pas perte physique ; archives sensibles, pas chiffrement intégral garanti ; périodicité source liée à app active, adaptation mobile requise.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION** ; compatibilité Desktop↔Android des archives non requise ici.

### W — Restore

- Finalité/acteurs : O autorisé récupère depuis une archive valide.
- Capacités/règles : sélectionner/confirmer, valider format/compatibilité/tailles/médias/intégrité/relations et Owner actif candidat ; pré-backup, préparation reprise, remplacement, vérification et récupération sur interruption.
- Invariants/sécurité/persistance : pas remplacement actif par données non validées ; état précédent récupérable selon protocole ; cohérence données+médias ; opération locale privilégiée.
- Échec : rollback/récupération contrôlée, pas mélange silencieux ancien/nouveau.
- Limites : pas seconde saisie password imposée en 2.0.1 pour restore ; Owner actif candidat ≠ réauthentification courante ; défaillance physique totale hors garantie.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### X — Reset

- Finalité/acteurs : Owner seul efface volontairement l'exploitation locale.
- Capacités/règles : autorisation, password propre Owner, confirmations, pré-backup, reset/reprise contrôlés, retour initialisation ; sauvegardes conservées.
- Invariants/sécurité/persistance : preuve d'identité et effets DB/médias cohérents ; pas reset implicite pour réparer login ; interruptions avant/après commit distinguées.
- Échec : récupérer ancien état avant commit ou terminer nettoyage après commit, pas perte silencieuse des médias actifs.
- Limites : destruction voulue des comptes/métier/paramètres, pas annulation universelle sans backup disponible.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### Y — Diagnostics / integrity

- Finalité/acteurs : O/support autorisé inspecte version, état données/stockage et incidents.
- Capacités/règles : niveau données, taille/espace, santé, backups, files, logs techniques et erreurs publiques sûres ; vérifications intégrité/relation.
- Invariants/sécurité/persistance : diagnostic ne réinitialise pas données, n'expose pas credentials ; logs protégés, pas objet identité brut.
- Échec : indisponibilité indiquée, pas résultat intégrité PASS inventé.
- Limites : contrôle ponctuel n'est ni réparation de toute corruption ni protection contre panne matérielle.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION** ; ouvrir un dossier système Desktop **NOT APPLICABLE** comme interaction obligatoire.

### Z — Localization

- Finalité/acteurs : tous interprètent correctement montants/dates/statuts.
- Capacités/règles : FR/EN, labels métier cohérents, devise paramétrée, date/heure, virgule/point monétaires, thèmes ; codes persistés non remplacés par traductions.
- Invariants/sécurité/persistance : traduction ne change pas autorisations, calculs ni entier de quantité ; PDF clair indépendant thème.
- Échec : valeur monétaire invalide refusée, pas parsing silencieux en un autre nombre.
- Limites : calendrier UTC/local mélangé dans source, à expliciter sans inventer correction déjà réalisée.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### AA — Accessibility / responsive interaction

- Finalité/acteurs : tous utilisent téléphone/tablette sans rendre des actions critiques inaccessibles.
- Capacités/règles : toucher prioritaire, portrait/paysage, clavier utilisable, champs labellisés, œil password, focus/retry, erreurs/statuts textuels, contrastes/thèmes/réduction mouvement, tableaux alignés et défilement des zones indépendantes.
- Invariants/sécurité/persistance : pas autorisation par apparence ; pas geste/couleur seul pour comprendre ; pas double commit par double toucher ; fermeture de dialogue contrôlée pendant mutation.
- Échec : action et retour d'erreur restent visibles, saisie corrigeable sans resélection répétée.
- Limites : pas certification accessibilité complète ni qualification mobile héritée de Windows ; taille tactile source nominale 44px, unité mobile non choisie.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### AB — Security boundaries

- Finalité/acteurs : frontière de confiance protège tous les acteurs et données contre entrées non fiables.
- Capacités/règles : classification explicite des opérations bootstrap/auth/protégées ; autorisation courante, validation runtime des discriminateurs, ressources sémantiques fixes et refus par défaut.
- Invariants/sécurité/persistance : UI ne choisit pas permissions, acteur, table ou chemin arbitraire ; retours publics allowlist sans hash/secret ; fichiers/imports validés ; secrets persistés protégés de façon adaptée à la plateforme.
- Échec : refus avant mutation métier ; logs sûrs, pas contournement par appel direct.
- Limites : émission explicite password temporaire autorisé et backup sensible sont contrats séparés, pas fuite tolérée des DTO.
- Décision : **PRESERVE**.

### AC — Persistence / transactions

- Finalité/acteurs : toutes opérations métier autorisées conservent cohérence locale.
- Capacités/règles : validation durable atomique des ensembles métier, relations/invariants, migrations ordonnées sans reset silencieux, snapshots historiques, commandes idempotentes persistées.
- Invariants/sécurité/persistance : même transaction pour preuve d'idempotence et vente ; pas demi-stock/demi-paiement ; contention ne doit pas autoriser écrasement non contrôlé ; références médias protégées selon protocole distinct.
- Échec : rollback ou récupération explicite, jamais simulation de réussite ; preuves d'intégrité lisibles.
- Limites : pas atomicité distribuée ni garantie absolue sur disque cassé ; sérialisation universelle de toutes tâches non établie.
- Décision : **PRESERVE** ; mécanisme de stockage non choisi.

### AD — Failure / recovery

- Finalité/acteurs : O/M/E retrouvent l'état durable sans aggraver un incident.
- Capacités/règles : erreur/retry, protection soumission, réponses de recherche obsolètes ignorées ; redémarrage ouvre données existantes, récupère opérations destructrices et files prévues ; historique permet vérifier résultat ambigu.
- Invariants/sécurité/persistance : coupure Internet n'empêche pas cœur local ; impression/envoi raté ne rejoue pas vente ; interruption ne fabrique ni sortie présence ni délégation.
- Échec : message sûr, contexte conservé dans les limites source, pas nettoyage destructeur opportuniste.
- Limites : saisie/panier volatils, drafts difficilement reprenables, exactly-once e-mail absent ; cycle de vie mobile ne peut être présumé équivalent au Desktop.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

### AE — Help / navigation

- Finalité/acteurs : tous comprennent les procédures, trouvent et quittent les fonctions permises.
- Capacités/règles : aide recherchable, sommaire/sections FR/EN, impression, consignes permissions/sécurité/continuité/sauvegarde/urgence/passation ; navigation réversible et structurée, titre destination, pas doubles commandes ; chat ne détruit pas contexte métier.
- Invariants/sécurité/persistance : navigation filtrée par droits actuels, aucune page interdite affichée avant validation des droits ; aide ne contourne pas autorisation d'impression.
- Échec : retour/retry disponibles sans succès métier fictif ; écran interdit ne se réactive pas par retour.
- Limites : entrée source POS si permis, Home comme repli ; pile de navigation non durable. Ne pas réintroduire une ancienne matrice de navigation ou figer des dimensions Desktop.
- Décision : **PRESERVE WITH PLATFORM ADAPTATION**.

## 7. Core Business Flows

Les flèches décrivent des dépendances logiques, pas plusieurs commits à réaliser séparément. « Trace » inclut facture/lignes/mouvements ; un événement d'audit explicite est requis seulement où il existe, pas inventé pour toute vente.

| ID | Flux et refus critiques |
|---|---|
| FLOW-01 | LOGIN → normaliser identifiant → compte actif/non verrouillé/non ambigu → vérifier password → session/droits courants ; sinon refus/compteur, aucune session nouvelle |
| FLOW-02 | OPEN CASH → panier/remise/espèces → valider acteur/caisse/stock → commit unique facture+lignes+paiement+stock+mouvements+commande → reçu ; contexte/audit caisse selon contrats. Réessai clé : preuve RF004 avant résultat existant |
| FLOW-03 | CANCEL SALE → autoriser/motif → état facture → compensation stock+paiement+statut en transaction → histoire conservée ; répétition sans nouvelle compensation |
| FLOW-04 | PRODUCT create/update → valider doublons/champs → préparer média → commit références/données/effets stock-prix → cleanup non destructeur ; archive → sortir catalogue actif/mouvement, garder histoire/média référencé |
| FLOW-05 | INVENTORY → draft/lignes → compter/revoir écarts → autoriser validation → réconcilier stock courant+mouvements → VALIDATED ; deuxième validation refusée |
| FLOW-06 | PURCHASE → draft/fournisseur/lignes (zéro stock) → valider réception une fois → mouvements ; annuler motif → compenser si stock disponible, sinon tout refuser |
| FLOW-07 | PRESENCE → choisir signataire → vérifier son password → heure courante entrée/sortie → historique ; correction privilégiée/motif/intervalle → originaux/correcteur/audit conservés |
| FLOW-08 | BACKUP → Owner actif/droit si manuel → snapshot cohérent+médias requis → contrôles/manifeste → publier archive → rétention 7 automatiques ; échec ne remplace pas archive valide |
| FLOW-09 | RESTORE → autoriser/sélection/confirmer → valider archive/Owner/intégrité/médias → safety backup + état récupérable → remplacer/vérifier → finaliser ; interruption → récupération contrôlée |
| FLOW-10 | RESET → Owner/droit/password/confirmer → safety backup → préparer reprise → commit reset → nettoyage sûr ; avant commit restaurer, après commit achever conformément au marqueur durable |
| FLOW-11 | SWITCH/LOGOUT → contrôler caisse/transaction critique/panier → switch vérifie cible avant remplacement, logout termine session → navigation permise ; ni présence automatique ni élévation |

## 8. Cross-Domain Invariants

| ID | Garantie à préserver et borne |
|---|---|
| XINV-01 | Auth valide requise hors bootstrap/récupération explicitement ouverts ; comptes invalides/inactifs refusés |
| XINV-02 | Toute opération protégée autorisée à la frontière de confiance selon droits courants, pas UI |
| XINV-03 | Owner principal protégé contre rétrogradation/désactivation et refus de ses droits |
| XINV-04 | Refus individuels subtractifs, jamais ajout hors rôle ; cible caisse ouverte protégée |
| XINV-05 | Aucune élévation temporaire ; switch change la véritable identité après preuve |
| XINV-06 | Identité session, signature présence et propriétaire caisse distincts et attribués correctement |
| XINV-07 | Identifiant/nom insensible à casse, password sensible, ambiguïté refusée |
| XINV-08 | Secrets et hashes absents des projections publiques ; preuve personnelle vérifiée dans domaine |
| XINV-09 | Monnaie calculée selon règles d'arrondi source à deux décimales ; pas d'arrondi supplémentaire pour rendre des commandes équivalentes |
| XINV-10 | Quantités métier entières positives pour lignes, stock non négatif ; panier zéro retiré/exclu |
| XINV-11 | Vente validée atomique : facture/lignes/paiement/stock/mouvements/commande durable cohérents |
| XINV-12 | Replay vente : même acteur autorisé, même caisse originale actuellement ouverte, facture validée et commande équivalente prouvée, sinon refus |
| XINV-13 | Équivalence RF004 : multiensemble trié de couples article/quantité avec doublons conservés, remise demandée défaut 0, montant reçu explicite ou omission ; ordre des propriétés/lignes sans effet, prix/stock courants non utilisés pour réécrire l'intention |
| XINV-14 | Clé seule insuffisante ; preuve historique absente → refus, pas backfill supposé ; replay/rejet sans second effet métier |
| XINV-15 | Draft achat ne touche pas stock ; validation répétée ne reçoit pas deux fois |
| XINV-16 | Inventaire validé non réappliqué ; différences validées contre stock courant |
| XINV-17 | Annulation conserve facture/achat et compense une fois selon état/stock ; pas effacement déguisé |
| XINV-18 | Histoire non universellement immuable : préserver restrictions exactes ; suppression présence interdite via historique, inconnu jamais branche permissive |
| XINV-19 | Audit attribue acteur/responsable/caisse capturés sans inventer données anciennes ; couverture limitée aux événements existants |
| XINV-20 | Relations/intégrité/migrations préservent données ; pas reset implicite en update ou erreur de démarrage |
| XINV-21 | Backup cohérent inclut médias requis ; restore seulement après validation/préparation sûre ; reset Owner/password/backup/recovery |
| XINV-22 | Échec post-commit cleanup média ne détruit pas référence active ; coupure physique totale reste hors garantie |
| XINV-23 | Cœur local autonome hors Internet ; e-mail échoué n'annule pas métier, exactement-une-fois externe non garanti |
| XINV-24 | Export/impression lisibles en palette claire ; erreur périphérique ne modifie pas vente durable ni autorisations |

## 9. Data Ownership

Chaque installation possède ces catégories locales ; aucune autorité commune avec un autre poste.

| Catégorie | Autorité persistée | Dérivé / transitoire | Histoire et sensibilité |
|---|---|---|---|
| Utilisateurs/rôles/refus/profils | Comptes, état, rôles, refus, profil lié | Permissions effectives calculées, session volatile | Secrets de vérification, PII/photos ; changements audités selon contrat |
| Produits/médias | Champs/seuils/quantité/références/contenu géré | Recherche/preview/état faible | Prix/mouvements, références archivées ; originaux externes non propriété destructive STORE |
| Caisses | Propriétaire, ouverture/clôture, snapshots | Théorique courant | Responsabilité et audit sensibles |
| Factures/lignes/paiements | Vente/status/snapshots/commande de replay | Panier, reçu affiché, monnaie calculée | Historique financier, compensations, données vendeur |
| Stock/mouvements/inventaires | Quantité, deltas, drafts/comptages/statuts | Écarts et alertes affichés | Justifications/references ; suppression limitée explicitement permise |
| Achats/fournisseurs | Draft/lignes/réception/annulation/contact | Total/listes filtrées | Historique réception et PII partenaires |
| Présence | Début/fin/statut/corrections | Présents et durées agrégées | Données personnelles et signatures/correcteur |
| Rapports | Pas d'entrepôt analytique séparé requis | KPI/graphes/tableaux depuis métier | PDF exporté = snapshot sensible, pas nouvelle autorité |
| Messages/notifications/e-mails | Messages/états locaux, alertes et queue/attachments | Compteurs non lus, activité de livraison | Correspondance personnelle, contenu des rapports |
| Paramètres/audit/diagnostic | Configuration, événements/metadata | Préférences d'affichage et santé calculée | Secrets envoi protégés ; logs ne doivent pas collecter secrets |
| Backups | Snapshot données+médias et intégrité | Liste/date/taille | Copie sensible de l'autorité à un instant, pas sync ni chiffrement universel garanti |

## 10. Security Baseline

Exigences transverses AB/B/A : aucune interface générale donnant accès arbitraire aux ressources ; opérations sémantiques connues et validation des types à l'exécution ; identité et contexte établis dans frontière de confiance ; permissions recontrôlées même si bouton absent. Une ressource inconnue ne retombe pas sur une ressource plus privilégiée.

Secrets persistés : protection adaptée à la plateforme sans sélectionner sa technologie. Interfaces publiques allowlist, pas ligne d'utilisateur brute. Les passwords sont protégés et sensibles à la casse ; réponses de récupération jamais publiques. L'émission contrôlée d'un password temporaire autorisé n'autorise pas sa journalisation. Les backups restent sensibles et leur possession nécessite des précautions ; ne pas promettre chiffrement/authenticité cryptographique de tout export existant.

Entrées fichiers/médias/imports non fiables : vérifier contenu/type/borne/référence, pas accès arbitraire aux fichiers ; domaine valide avant mutation. Restore/reset restent privilégiés avec garanties propres, sans ajouter silencieusement une seconde réauthentification restore absente de 2.0.1. Protection fonctionnelle locale ne promet pas résistance absolue à un système d'exploitation compromis.

## 11. Local-First / Offline Baseline

Sans Internet : login local, droits, caisse, ventes, stock, achats, inventaires, présence, rapports, chat local, génération documentaire et sauvegarde locale restent possibles dans leurs préconditions. Envoi e-mail demande une connectivité/configuration ; échec est mis en file/état explicite. Aucune validation de vente par serveur central.

Panne de courant/arrêt forcé : distinguer commande non commitée, commitée avec réponse perdue, et effet externe incertain. Ne pas rejouer aveuglément. Les sauvegardes hors appareil protègent mieux contre perte de celui-ci, sans créer obligation de cloud. Appareil faible : chargements bornés, états de progression/erreur, stale results ignorés ; aucune performance chiffrée ni disponibilité background perpétuelle n'est héritée.

## 12. Phone & Tablet Functional Expectations

Téléphone et tablette Android, toucher prioritaire, portrait/paysage, navigation réversible et libellés cohérents ; lecture et actions critiques accessibles avec clavier affiché. Catalogue/panier, cartes de listes et tableaux doivent rester distincts/lisibles sans perte de fonction. Les graphes gardent équivalents tabulaires et légendes ; confirmation/annulation des opérations sensibles explicites.

Adapter placement/dimension/interactions sans copier obligatoirement la structure visuelle Desktop. Conserver absence de doublons de commandes, saisie password stable, champs numériques au clavier, contrastes et palette documentaire claire. Les droits et résultats ne dépendent pas du format d'écran. Aucun framework de navigation, API mobile, bibliothèque de données, outil de build ou pipeline installable choisi ici.

## 13. Preserved 2.0.1 Limitations

Ces limites bornent la preuve de référence, pas une obligation de recréer des défauts. Toute amélioration substantielle nécessite décision ultérieure, pas implémentation implicite.

| Limite valable | Conséquence pour l'extraction |
|---|---|
| Draft achats/inventaires : reprise éditeur incomplète | Préserver données et effets ; extension reprise DEFER |
| Panier et saisie non durables, session sans timeout J.4 | Ne pas prétendre reprise complète ni réintroduire timeout historique |
| Cash-only, quantités entières | Fractionnement/paiement banque/Mobile Money DEFER |
| Rapports bornés, coût/marge estimés, dates UTC/local mixtes | Ne pas promettre comptabilité/exhaustivité/calendrier déjà unifié |
| Audit partiel et suppression historique mouvements autorisée | Ne pas promettre histoire universellement append-only |
| Secret restore sans seconde réauth ; archives sensibles | Décrire les garanties réelles, protection platform à évaluer |
| E-mail duplicable après interruption ; TLS source selon configuration | Pas exactly-once ni sécurité transport automatique universelle |
| Setup en opérations séparées ; média signature non décodage exhaustif | Limites explicitement conservées dans analyse de risques |
| Facture achat PDF autonome UNKNOWN, CSV non opérationnel en UI | Ne pas inventer un parcours existant ; exigences nouvelles DEFER |
| Windows qualifié n'implique aucun appareil Android qualifié | Qualification mobile future distincte ; ancien upgrade binaire in-place toujours NOT TESTED |

RF001–004, migrations/source freeze et remédiation tooling sont clos : **ne pas les remettre dans les défauts ouverts**. Les mécanismes concrets de backup, impression, suspension et secret mobile restent questions d'adaptation, pas capacités supprimées.

## 14. Explicit Non-Goals

- **DEFER** : PC↔Android, Android↔Android et multi-store sync, cloud DB, backend SaaS, authentification centrale ; import inter-plateformes/compatibilité archives Desktop non requis ici.
- **DEFER** : iOS, web déployé, comptabilité générale, paie, banque/Mobile Money, entrepôts avancés, scan codes-barres dédié, nouvelles fonctionnalités de reprise.
- **NOT APPLICABLE** à l'exigence fonctionnelle mobile : installateur Windows, chemins de profil Desktop, signature Authenticode, IPC Electron ou bibliothèque SQLite d'origine. Ces noms identifient l'origine seulement.
- **NOT APPLICABLE** à cette étape : réécriture 2.0.1, implémentation, installation de dépendances, création projet Android, sélection de stack/plugins/API level/configuration/pipeline APK-AAB. L'étape 3 n'est pas exécutée et aucune classification de composants n'est produite.

## 15. Traceability

Les chemins techniques ci-dessous désignent exclusivement l'origine 2.0.1, pas des composants imposés à 3.0.

| Réf. | Origine | Exigences extraites |
|---|---|---|
| S1 | `docs/CURRENT_APPLICATION_BASELINE.md` §§4–5, `backend/src/domain/rbac/permissionMatrix.ts`, `ipcPermissions.ts` | A/B/AB, matrice et droits courants |
| S2 | `tests/unit/backend/sessionRemediationJ4.test.ts`, baseline §§5–6 | C/AE, pas élévation ni expiration d'inactivité, switch/caisse |
| S3 | `docs/RF004_CLOSURE_REPORT.md`, `backend/src/domain/sale/canonicalSale.ts`, `tests/unit/backend/invoiceIdempotencyRF004.test.ts` | F/AC, XINV-11–14 et historiques NULL |
| S4 | Baseline §§7–10, `backend/src/database/storeDatabase.ts`, tests `maintenanceIntegration`, `cashPolicy`, `purchasePolicy`, `traceabilityJ5` | E–N, ventes/compensations/stock |
| S5 | `backend/src/domain/rbac/historyOperation.ts`, tests `historyAuthorizationRF003`, `publicIdentitySecrets` | K/AB, refus fermé et projections |
| S6 | Baseline §9, tests `articleMedia`, `backupBundleJ2`, `maintenanceIntegration` | I, RF001 et sauvegarde médias |
| S7 | Baseline §§11–14, tests `explicitAttendanceJ3`, `auditSnapshots`, `reportingPhaseF`, `printLight`, `reportDisclosure` | O–U, signatures, audit, KPI/documentation |
| S8 | Baseline §15, tests `backupValidation`, `restoreRecovery`, `resetRecovery`, `migrations` | V–Y/AC/AD, récupération et intégrité |
| S9 | Baseline §§16–17, §§20–23 dont NR-01–30 | Z/AA/AE, limites et contrat de non-régression |
| S10 | `docs/PRODUCTION_QUALIFICATION_REPORT.md` clôture définitive, `PRODUCTION_ACCEPTANCE_CHECKLIST.md` | Statut qualifié source et portée exacte des preuves |

### Contradictions résolues / inconnues

1. `SECURITY_AUTHORIZATION.md` décrit encore référence présence, expiration 30 minutes et élévation temporaire : document historique contredit par J.4 actuel/tests, **non repris**.
2. Ancien BLOCKED/tooling 13/RC absent dans baseline : clôture documentaire finale prime pour statut release, sans changer règles métier.
3. « Vente → audit » ne signifie pas événement audit explicite garanti pour toute création : facture/lignes/mouvements constituent la trace durable ; couverture audit limitée enregistrée.
4. Détail achat ≠ reprise éditeur ; import backend CSV ≠ parcours utilisateur ; distinction préservée.
5. Sortie facture achat autonome reste **UNKNOWN** ; aucun parcours nouveau déduit. Méthodes mobiles d'impression, backup et lifecycle restent **UNKNOWN** jusqu'à évaluation, sans modifier les obligations fonctionnelles correspondantes.

## 16. Open Questions for Portability Assessment

Ces 10 questions ne sélectionnent aucune technologie et ne remettent pas en cause le périmètre local indépendant.

| ID | Question à résoudre à l'étape 3 |
|---|---|
| OQ-01 | Comment matérialiser la frontière de confiance locale et réautoriser chaque opération sans déléguer l'autorité à l'UI ? |
| OQ-02 | Comment conserver les garanties transactionnelles, relations, migrations et preuve canonique RF004 dans le stockage mobile ? |
| OQ-03 | Comment traiter suspension/arrêt forcé et reprise de session sans élévation, timeout historique ou pointage automatique ? |
| OQ-04 | Comment préserver les médias référencés et leurs limites lors de sélection, copie, remplacement et interruption ? |
| OQ-05 | Comment exporter/conserver/valider des sauvegardes locales et préserver le protocole restore/reset sous contraintes de stockage mobile ? |
| OQ-06 | Quelles limites d'exécution background explicites appliquer au rythme backup/retry e-mail sans prétendre une disponibilité permanente ? |
| OQ-07 | Comment rendre PDF, impression et pièces jointes utilisables sur téléphone/tablette en gardant palette claire et absence de second effet métier ? |
| OQ-08 | Comment protéger les secrets locaux et gérer leur éventuelle non-portabilité à la restauration sans les exposer à l'UI ? |
| OQ-09 | Comment adapter navigation, panier/listes/tableaux/focus/clavier et accessibilité aux deux formats sans retirer les fonctions ? |
| OQ-10 | Quels scénarios de qualification mobile vérifier pour arrêt brutal, espace faible, données volumineuses, dates/fuseaux, offline et lifecycle des données à désinstallation ? |

Toute compatibilité d'archives avec Desktop, amélioration de reprise draft ou autre extension découverte exige une décision de périmètre ultérieure, pas une réponse technique implicite à ces questions.

## 17. Baseline Acceptance Criteria

- 31 domaines documentés, 24 invariants et 11 flux avec traçabilité des garanties critiques et limites réelles.
- Android phone/tablet et base locale indépendante explicitement confirmés ; pas cloud/sync/serveur imposé.
- Matrice rôles, restrictions Employee, Owner, refus subtractifs, J.4 et RF001–004 préservés ; ambiguïtés et statut historique distingués.
- Fonction ≠ mécanisme : aucun choix de stack, schéma Android, plugin ou framework ; pas d'évaluation de composants de l'étape 3.
- Rapports/documents/aide et fonctionnalités utilisateur existantes inclus ; pas réduction au seul moteur de vente.
- Sécurité/atomicité/recovery décrites sans garantie plus forte que la preuve 2.0.1 ; qualification Android non prétendue.
- STORE 2.0.1 et sa release inchangés ; un seul nouveau document autorisé, pas tests/build/audit/package réexécutés.

**READY FOR STEP 3 — PORTABILITY ASSESSMENT**. Ce statut valide l'extraction documentaire, pas une implémentation ni une qualification Android.
