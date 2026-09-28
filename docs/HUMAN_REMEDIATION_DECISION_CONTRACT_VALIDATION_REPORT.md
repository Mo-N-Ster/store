# STORE 3.0 — HUMAN REMEDIATION DECISION CONTRACT VALIDATION REPORT

Date : 25 septembre 2026
Version examinée : `2.0.1`
Statut : validation documentaire uniquement — aucune implémentation J.1

## 1. Decision Contract summary

Le contrat D-01 à D-25 est cohérent avec les objectifs STORE et majoritairement compatible avec l'architecture Electron/React/SQLite existante. L'autorité backend, le preload strict, l'idempotence des ventes/achats et le modèle de permissions peuvent être préservés.

Trois conclusions structurantes ressortent du dépôt :

1. le schéma `attendances` peut représenter un pointage explicite sans réinterpréter l'historique, à condition de conserver les anciennes lignes `source='AUTHENTICATION'` ;
2. le changement réel d'utilisateur peut remplacer l'élévation sans transférer l'identité, mais la voie minimale sûre est de le bloquer tant que la caisse du titulaire est ouverte, car aucune notion de caisse physique partagée n'existe ;
3. les backups actuels sont des copies SQLite seules. Inclure les médias exige un nouveau conteneur de sauvegarde ou un mécanisme coordonné DB+médias. La décision de format et compatibilité reste humaine avant J.2.

## 2. D-01 → D-25 validation matrix

| Décision | Compatibilité dépôt | Évidence actuelle | Conséquence d'implémentation |
| --- | --- | --- | --- |
| D-01 Attendance explicite | COMPATIBLE | `attendances` est distinct de `users`; le couplage est dans les handlers login/logout | Retirer les appels automatiques sans modifier l'historique |
| D-02 Fiche quotidienne | COMPATIBLE | début, fin, état, user/role disponibles | Nouvelle lecture filtrée et UI agenda/tableau |
| D-03 Signature attendance | COMPATIBLE AVEC NOUVEL IPC | bcrypt et login existent, mais pas de vérification dédiée self-service | Canal atomique vérifiant identité+secret puis pointant |
| D-04 Temps courant | COMPATIBLE | backend possède `now()` et correction privilégiée séparée | Ne jamais accepter de timestamp ordinaire du renderer |
| D-05 Pas d'expiration par inactivité | COMPATIBLE | timeout uniquement en mémoire dans React et `requireSession` | Retirer les deux côtés, garder logout/autorisation |
| D-06 Supprimer elevation | COMPATIBLE MAIS LARGE | élévation concentrée dans App, StoreShell, ipcHandlers, session payload et tests | Suppression complète et vraie transition d'identité |
| D-07 Switch user | COMPATIBLE AVEC NOUVEL IPC | session liée à `event.sender.id`; login actuel réinterprète employee→manager comme élévation | Créer une transition qui ne détruit A qu'après auth B réussie |
| D-08 Cash ownership | COMPATIBLE SI NON TRANSFÉRÉ | `cash_sessions.employee_id`; requêtes et ventes forcées sur session actor | Aucun transfert ; blocage minimal tant que caisse A ouverte |
| D-09 Panier/switch | COMPATIBLE | panier est état renderer, vente non créée avant checkout | Bloquer si panier non vide ou abandon confirmé sans persistance |
| D-10 Article terminology | COMPATIBLE | i18n centralisée ; noms techniques `product` séparés | Glossaire puis modification UX seulement |
| D-11 Image primaire | COMPATIBLE AVEC MIGRATION | aucun champ/média actuel | Identifiant stable, stockage géré, import contrôlé |
| D-12 Images dans backups | CONFLIT AVEC FORMAT ACTUEL | backup/restore manipule un unique `.db` | Extension de format obligatoire avant images |
| D-13 Password modes | COMPATIBLE AVEC IPC | automatique backend sécurisé ; manuel privilégié existe mais pas manuel employé | Canal manuel autorisé réutilisant policy/hash |
| D-14 Help secondaire | COMPATIBLE | Help est destination, menu user et fallback | Retirer nav, garder guide dans menu, corriger fallback |
| D-15 Quick chat drawer | COMPATIBLE | Chat réutilise MailboxPage ; Drawer existe | Overlay droit, pas d'entrée historique, delete masqué |
| D-16 Filtres contextuels | COMPATIBLE AVEC QUERIES | filtres globaux période/grain/article/catégorie | Contrats par rapport, cohérents KPI/graphe/export |
| D-17 Charts adaptés | COMPATIBLE | line/ranking/movement/KPI/table existent | Réutiliser composants et documenter question analytique |
| D-18 Stock traceability | PARTIELLEMENT COMPATIBLE | reason/reference/time/quantity persistent ; actor absent | Afficher données réelles ; migration actor seulement si décidée |
| D-19 Purchase traceability | COMPATIBLE | tables purchase/items/supplier + mouvements `purchase` et référence ID | Lectures détails et liens contrôlés, idempotence inchangée |
| D-20 Information architecture | COMPATIBLE | design system possède grilles, drawers et panneaux | Recomposition frontend sans contrat backend |
| D-21 Entity details | COMPATIBLE | ProductDetailsDialog et listes structurées existent | Composant détail canonique, tables conservées pour comparaison |
| D-22 UI redundancy | COMPATIBLE | points d'entrée Help/chat et anciens composants repérés | Un workflow canonique, raccourcis convergents |
| D-23 Buttons | COMPATIBLE | Button/IconButton et variants disponibles | Migrer les boutons historiques directs |
| D-24 Trivial labels | COMPATIBLE | `Page courante` visible ; Back nécessaire | Masquer texte redondant, conserver aria-label/action |
| D-25 Toggle upper-left | COMPATIBLE | menu mobile en haut, collapse desktop en bas | Déplacer contrôle desktop, conserver accessibilité |

## 3. Current architecture compatibility

Le contrat ne requiert pas de réécriture d'Electron, React, TypeScript, SQLite ou du modèle IPC strict. Les points d'extension naturels existent : services frontend, allowlist preload, handlers sender-bound, domaine backend et migrations séquentielles. Les opérations DB sont sérialisées par `databaseQueue`, et les mutations métier critiques utilisent des transactions.

La règle d'implémentation doit rester : renderer orchestre l'UX ; preload expose une capacité nommée ; IPC remplace toute identité fournie ; backend valide permission, secret et invariant ; DB applique la transaction et l'audit.

## 4. Attendance compatibility

Le schéma actuel contient `employee_id`, `start_time`, `end_time`, `session_ref`, `source`, `status`, valeurs originales, motif et acteur de correction. Il peut donc conserver les lignes historiques `AUTHENTICATION` et créer de nouvelles lignes explicites avec une source distincte, sans migration destructive.

Les incompatibilités sont comportementales : login crée aujourd'hui une entrée, logout la ferme, expiration la ferme, `before-quit` interrompt toutes les présences ouvertes et le démarrage récupère les sessions interrompues. J.3 doit auditer chacun de ces consommateurs. Pour éviter des présences infinies après coupure, la récupération `INTERRUPTED` peut être conservée initialement, avec correction privilégiée ; cela ne recouple pas login et attendance.

## 5. Session compatibility

Une session est stockée par `webContents.sender.id` avec identité, rôle, permissions et `lastActivityAt`. La suppression du timeout ne requiert pas de DB. Elle exige le retrait du timer React, de `touchSession` et de la branche `currentTime-lastActivityAt > 30 min` côté backend.

La transition A→B doit authentifier B avant de remplacer la Map. L'échec garde A intact. La réussite remplace ensemble `id`, `role`, permissions et displayName. L'ancien modèle `elevatedUntil/authorizedById/accessRole` doit être supprimé complètement, non renommé.

## 6. Cash ownership compatibility

Chaque caisse est liée à `cash_sessions.employee_id`; les lectures/ouvertures/clôtures utilisent l'acteur backend, et `createInvoice` force le vendeur puis exige sa propre caisse ouverte. L'index unique interdit plusieurs caisses ouvertes par employé, mais autorise des caisses ouvertes pour plusieurs employés. Il n'existe ni registre physique, ni terminal/caisse partagée, ni transfert.

La politique minimale compatible sans nouveau modèle métier est donc : **bloquer le switch tant que l'utilisateur courant possède une caisse ouverte, même inactive**. Cette règle est plus stricte que l'option préférée du contrat, mais évite que deux sessions logiques partagent implicitement le même tiroir physique. Aucun changement de FK ou de propriétaire n'est requis.

Autoriser plusieurs caisses ouvertes sur un même dispositif nécessiterait une décision métier supplémentaire sur le tiroir physique ; cela ne fait pas partie du minimum J.4.

## 7. Cart/checkout compatibility

Le panier est purement frontend et n'affecte pas stock/facture/paiement avant `createInvoice`. J.4 peut donc :

- refuser le switch si le panier est non vide ;
- proposer « Annuler le panier et changer d'utilisateur » avec confirmation ;
- vider le panier sans écriture métier ;
- refuser le switch lorsque CheckoutDialog ou une soumission est active ;
- attendre un résultat déterminé, car les appels IPC passent par la file backend et la création de vente est transactionnelle/idempotente.

Le backend ne peut pas prouver l'état du panier puisqu'il n'est pas persisté. Cette garde est nécessairement UI, tandis que l'identité de la vente et la mutation restent garanties côté backend.

## 8. Article-media persistence compatibility

Une migration additive peut ajouter à `products` un identifiant relatif nullable, par exemple `image_ref`, sans renommer les entités. Le fichier importé doit recevoir un nom généré, rester dans `app.getPath('userData')/media/articles`, être validé par contenu/taille/décodage et être écrit atomiquement.

Le preload ne doit exposer ni chemin générique ni lecture arbitraire. Une capacité `selectArticleImage` peut ouvrir un picker côté main, importer et retourner une référence sûre ; une capacité de résolution contrôlée doit fournir l'image sans exposer tout le filesystem. Le placeholder reste frontend.

## 9. Backup/media compatibility

**STOP CONDITION D-12 rencontrée.** `createBackup`, export et restore produisent/valident/remplacent un unique fichier SQLite. Ils n'ont aucun manifeste, archive ou staging de répertoire média. Ajouter `image_ref` sans changer ce moteur créerait des backups incomplets, expressément interdits.

Alternatives minimales :

- **A — bundle `.store-backup` recommandé** : archive avec `manifest.json`, `store.db` et `media/articles/**`; validation/extraction dans staging ; swap coordonné ; anciennes sauvegardes `.db` restent acceptées comme legacy sans média.
- **B — paire `.db` + dossier sidecar** : plus simple, mais facile à séparer lors de copie USB/e-mail ; non recommandée.
- **C — images BLOB dans SQLite** : backup cohérent automatiquement, mais augmente DB/WAL, coûts de lecture et mémoire ; non recommandé pour dispositifs faibles.

Le contrat décide que les images sont sauvegardées, mais ne choisit pas le format. Une décision humaine A/B/C reste nécessaire avant J.2.

## 10. Password-reset compatibility

Le générateur automatique est backend (`randomBytes`, base64url, hash bcrypt) et déjà permissionné. Le mode manuel actuel pour Manager/Owner est une récupération fondée sur la réponse de sécurité du compte cible, pas un reset administratif générique. Les employés n'ont pas de canal manuel.

J.2 doit ajouter un canal administratif manuel protégé par `EMPLOYEES:UPDATE`, appliquer la même validation de mot de passe et bcrypt, réinitialiser les compteurs, auditer l'acteur sender-bound et préserver les protections Owner. Il ne faut pas détourner `resetManagerPassword`, qui possède une sémantique de récupération différente.

## 11. Help/chat/navigation compatibility

Help peut sortir de `navigationItems` tout en restant dans le menu utilisateur. `defaultDestination` ne doit plus tomber sur `help`; il doit choisir la première destination permise ou un écran forbidden/support explicite.

Le chat peut devenir un Drawer droit fourni par le design system, ouvert sans `navigate('chat')`, ce qui préserve la page courante et corrige naturellement le Back. MailboxPage doit distinguer strictement `variant='chat'` : conversation/compose sans delete, contre Messages : gestion et suppression. Le canal backend reste inchangé et sender-bound.

## 12. Stock/purchase traceability compatibility

Les achats validés créent exactement une fois des mouvements `reason='purchase'` avec `reference_id=purchaseId`; la validation répétée retourne sans doubler le stock. L'annulation crée `purchase_cancellation` dans la même transaction. Cette base permet des liens achat↔mouvement↔article après validation de la référence et des permissions.

Stock ne persiste pas l'acteur dans `stock_movements`. J.5 doit d'abord afficher article, quantité, date, reason, référence et prix réellement disponibles. Si l'acteur devient obligatoire, une migration additive `actor_user_id` ne pourra renseigner honnêtement que les futurs mouvements ; les historiques devront afficher « Non enregistré ».

## 13. Reporting compatibility

La couche reports accepte actuellement from/to/grain/productId/category et applique ces dimensions aux séries backend. L'UI les montre partout, même lorsque certaines sont non pertinentes. Les données achats ont supplier/status ; présence a employee/role/status ; mouvements ont reason.

J.5 doit créer des validateurs de filtres par domaine et recalculer ensemble KPI, charts, tables et exports depuis le même payload. Les composants existants couvrent déjà courbe temporelle, classement horizontal, mouvements, KPI et détails. Aucun modèle ne justifie bénéfice comptable, valorisation ou productivité individuelle.

## 14. Required schema migrations

Obligatoire si J.2 est autorisée :

- migration additive `products.image_ref TEXT NULL` ou table média équivalente ;
- index uniquement si usage démontré ;
- migration idempotente, historique intact, backups legacy acceptés.

Optionnelle, à différer vers J.5 :

- `stock_movements.actor_user_id REFERENCES users(id)` nullable pour mouvements futurs.

Aucune migration nécessaire par défaut pour attendance explicite, session, switch, chat, Help, filtres ou mots de passe.

## 15. Required IPC changes

- J.2 : sélectionner/importer/remplacer/supprimer image de façon étroite ; backup bundle/restore bundle si A retenue ; reset manuel administratif.
- J.3 : `attendanceDay`, `clockAttendance` signé et lectures filtrées ; conserver `correctAttendance` séparé.
- J.4 : `switchUser` atomique, état caisse vérifié côté backend, retrait `dropElevation`/`touchSession` et payload elevation.
- J.5 : détails achat/mouvement et filtres contextuels validés.

Tous les nouveaux canaux doivent entrer dans `ipcPermissions`, la liste canonique, le preload et les tests unknown-IPC deny.

## 16. Required preload changes

Le preload reste une allowlist de méthodes nommées. Il ne doit jamais exposer `ipcRenderer`, `fs`, `path`, `app.getPath`, des chemins arbitraires ou une lecture URL/file générique. Les nouvelles capacités doivent accepter des DTO limités et retourner des objets sérialisables sans secrets.

## 17. Required backend changes

- découpler login/logout/expiration de `api.attendance` ;
- ajouter authentification attendance atomique sans journaliser le secret ;
- supprimer timeout général et modèle d'élévation ;
- authentifier puis remplacer l'identité pour switch, avec caisse bloquante ;
- gérer médias et backup staging selon option décidée ;
- ajouter reset manuel administratif utilisant validation/hash existants ;
- enrichir lectures traçabilité/report sans inventer de données.

## 18. Required frontend changes

- glossaire Article/Item et remplacement i18n UX ;
- toggle upper-left, Help secondaire, labels redondants retirés visuellement ;
- hiérarchie boutons/cards/scroll/espacements ;
- chat Drawer droit et style conversation multiuser sans delete ;
- image picker/preview/placeholder ;
- choix password Manuel/Automatique ;
- fiche présence quotidienne et signature éphémère ;
- switch user avec guards caisse/panier/checkout ;
- Stock/Achats/Reports contextuels et traçables.

## 19. Security impact

Risque maximal : J.4, puis J.3/J.2. Les invariants obligatoires sont identité=permissions, aucun switch sans credential, aucune mutation avec actor renderer, aucun secret persisté, aucune API FS générique, aucune baisse de protection Owner, aucune session B partiellement installée après échec.

La suppression du timeout augmente l'exposition sur appareil laissé sans surveillance. Les compensations contractuelles restent logout manuel, switch authentifié, autorisation backend et confirmations sensibles. Cette acceptation vient du Decision Contract ; elle doit être documentée dans le guide opérationnel.

## 20. Data-integrity impact

J.1 est sans impact DB. J.2 affecte cohérence DB+médias+backup. J.3 doit préserver chaque ligne historique et les corrections originales. J.4 ne change pas les propriétaires de caisse. J.5 conserve les transactions et idempotence ventes/achats. Toute migration doit passer integrity_check, foreign_key_check et fixture 2.0.1.

## 21. Upgrade impact

Le candidat précédent ne suffit plus après J.2–J.5. Une fixture 2.0.1 doit couvrir article sans image, anciens backups `.db`, attendance `AUTHENTICATION`, caisses ouvertes/fermées, achats validés et messages. L'upgrade doit conserver les données, attribuer `image_ref=NULL` et ne pas réinterpréter les présences historiques.

## 22. Backup/restore impact

Si le bundle A est choisi, la restauration doit valider manifeste, DB, checksums médias, limites d'extraction et absence de traversal dans un staging ; créer un pre-restore complet ; fermer SQLite ; basculer DB+médias de façon récupérable ; redémarrer ; vérifier intégrité. Les `.db` legacy doivent restaurer DB et établir un répertoire média vide sans erreur.

## 23. STOP conditions encountered

### SC-01 — Backup média

Rencontrée et bloquante pour J.2 : le moteur mono-fichier ne satisfait pas D-12. Minimum proposé : bundle `.store-backup` versionné. Décision humaine requise sur A/B/C.

### SC-02 — Cash

Le dépôt confirme un modèle par utilisateur, sans caisse physique partagée. Aucun changement fondamental n'est nécessaire si J.4 applique le blocage tant que la caisse courante est ouverte. Autoriser un switch « caisse ouverte mais idle » sortirait du minimum sûr et requiert une décision/modélisation future.

### SC-03 — Attendance

Non rencontrée : le schéma peut représenter l'explicite sans réécriture historique. Il faut conserver les sources historiques et les interruptions de recovery.

### SC-04 — Password

Non rencontrée : policy et hash peuvent être réutilisés. Un nouveau canal administratif est nécessaire ; aucun bypass n'est requis.

## 24. Remaining human decisions, if any

**Aucune décision métier humaine non résolue ne bloque J.1.** Les deux arbitrages ont été fournis et sont autoritatifs :

1. **HD-01** : backup média sous forme d'un bundle autonome `.store-backup`, avec manifeste, SQLite et médias ; pas de sidecar requis, pas de BLOB image ajouté pour la portabilité.
2. **HD-02** : changement d'utilisateur et logout bloqués tant que l'utilisateur courant possède une caisse ouverte ; aucune caisse physique partagée et aucun transfert de propriété.

HD-01 débloque la conception J.2, sous réserve des validations de structure, intégrité, path traversal, staging, cohérence et compatibilité des backups DB-only historiques. HD-02 débloque la conception J.4 sans migration du modèle de caisse.

## 25. Proposed exact J.1 scope

Inclus : glossaire FR/EN ; Produit→Article/Item UX ; toggle upper-left ; Help retiré de navigation mais conservé menu utilisateur ; `Page courante` visuellement retiré ; consolidation boutons ; détails entité ; suppression duplications UI ; espaces/proportions/scroll ; chat Drawer droit, bulles multiuser, delete absent ; responsive cinq cibles ; tests frontend/a11y.

Exclus : DB, migration, IPC, auth, session, elevation backend, attendance, caisse, backup, password mutation, rapports backend.

## 26. Proposed exact J.2 scope

Après décision backup : migration image nullable ; stockage média géré ; validation JPEG/PNG/WebP/taille/décodage ; import/replace/cleanup sûr ; placeholder ; backup/restore versionné avec legacy `.db` ; choix Manuel/Automatique ; reset manuel backend ; protections Owner ; tests sécurité/migration/backup.

## 27. Proposed exact J.3 scope

Découpler login/logout ; source explicite ; lecture journalière multiuser ; filtres date/période/user/rôle/état ; signature credential atomique ; temps backend ; double pointage fermé ; correction historique distincte/auditée ; conservation `AUTHENTICATION`; recovery interruption ; rapports présence et tests coupure.

## 28. Proposed exact J.4 scope

Retirer timeout React/backend/touchSession ; retirer elevation intégralement ; ajouter switchUser atomique ; auth B échouée conserve A ; permissions recalculées ; bloquer switch si caisse A ouverte, panier non vide ou checkout actif ; abandon panier confirmé ; aucune mutation stock ; audit attribution B ; documentation sécurité et tests IPC/session/caisse/POS.

## 29. Proposed exact J.5 scope

Stock : source/reference/reason/date/quantité et liens réels. Achats : détail fournisseur/items/états/stock effect, navigation par référence, idempotence intacte. Rapports : filtres par domaine, query backend cohérente, chart/question mapping, exports identiques, métriques documentées sans vérité inventée.

## 30. Proposed J.6 validation scope

Clean install dependencies ; full tests/0 skipped ; lint/build/diff ; security/unknown IPC/preload ; DB integrity ; upgrade 2.0.1 ; backup bundle et legacy ; attendance simultanée/signature/coupure ; switch échec/succès ; caisse/panier/checkout ; POS/idempotence ; médias ; chat ; reports ; offline ; cinq résolutions, thèmes, clavier/tactile et observation humaine enregistrée.

## 31. Proposed J.7 requalification scope

Reconstruire en environnement propre ; audit prod ; install/launch/uninstall/reinstall en VM ; upgrade installé ; packaged Setup→Login→attendance→cash→POS→invoice→stock ; backup/restore DB+médias ; visual gate ; Authenticode/décision distribution ; nouveau SHA-256 ; matrice RB-01 à RB-06 ; aucune version 3.0.0/publication sans autorisation.

## 32. Final statement

**DECISION CONTRACT IMPLEMENTABLE**

HD-01 et HD-02 ferment les deux arbitrages restants et correspondent à l'architecture observée. Aucun conflit métier non résolu ne bloque J.1. Conformément à l'autorisation actuelle : **STOP BEFORE J.1 IMPLEMENTATION**.
