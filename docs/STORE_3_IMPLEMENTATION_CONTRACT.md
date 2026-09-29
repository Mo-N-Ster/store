# STORE 3.0 — Implementation Contract

Étape 5 · 29 septembre 2026 · **PASS documentaire / aucune implémentation produit**.

## 1. Purpose

Contrat des phases Android suivantes, pas autorisation de les exécuter toutes. Chaque phase exige une mission explicite, un commit de départ et l'acceptation de ses prérequis. Une phase acceptée déverrouille la suivante sans la démarrer. Tous les domaines obligatoires restent dus ; priorité n'est pas optionalité.

Cible : STORE by VIBE, Android natif téléphone/tablette, Kotlin/Compose/Material 3, local-first, Room/BundledSQLiteDriver, base indépendante. Aucun cloud DB, serveur central, SaaS, synchronisation PC/mobile ou inter-mobile. Aucun code produit créé à cette étape.

## 2. Authority & Baselines

Références abrégées utilisées dans chaque phase ; lire les sections indiquées, pas l'histoire entière :

| Réf. | Document / autorité |
|---|---|
| B | [Functional baseline](STORE_3_FUNCTIONAL_BASELINE.md) : domaines A–AE, FLOW-01–11, XINV-01–24 ; commit `d99c97fe0e00cc148ebc592582f476899049de23` |
| P | [Portability assessment](STORE_3_PORTABILITY_ASSESSMENT.md) : C01–19 et risques R01–13 ; commit `4b5cbdc0437f96bc8fa9e99b3032b1570f99cdbf` |
| A | [Android architecture](STORE_3_ANDROID_ARCHITECTURE.md) : 16 ADR LOCKED, 0 PROVISIONAL |
| S | [Architecture spikes](STORE_3_ARCHITECTURE_SPIKES.md), section Step 4.2 native : SP-01/SP-02 PASS |
| F | Le présent contrat : clauses globales + seule section de phase courante |

A/S validés au commit `fe945cd540bcdacfbc79317a4e5b801b0c1778af`. Les mentions BLOCKED des anciennes sections Step 4.1 sont historiques, pas le statut courant. SP-01 : API 36 x86_64, 14 scénarios/42 reprises, arrêt externe réel, intégrité/FK/médias/idempotence PASS. SP-02 : PDF natif/round-trip/rejets/fixture 2.0.1 PASS. Ces preuves sélectionnent les mécanismes ; elles ne qualifient ni ARM64, ni produit complet, ni sécurité générale du parser.

STORE 2.0.1 gelé : `63b3849ee234248a3b07a643e17dd22fb8c7b23d`. Référence fonctionnelle seulement, jamais espace de travail Android. Ne pas réauditer les étapes closes ni relancer la suite Desktop.

## 3. Architecture Freeze

Les 16 décisions sont conservées ; aucun remplacement implicite de technologie. Correspondance de responsabilité :

| ADR | Décision conservée | Phase propriétaire / vérification |
|---|---|---|
| 01 | Kotlin Android natif | I01 / I14 |
| 02 | Compose, Material 3 adaptive, navigation typée, ViewModel/StateFlow | I01, chaque UI / I13 |
| 03 | Domaine pur, use cases/ports, composition manuelle | I01 / toutes |
| 04 | bcrypt favre, session mémoire | I03 / I13 |
| 05 | Room/BundledSQLite, migrations explicites | I02 / I12/I14 |
| 06 | Snapshot quiescent, générations/journal, ZIP streaming | I12 / I14 |
| 07 | UnitOfWork, canonicalisation versionnée et parité | I02/I06 / I07/I13 |
| 08 | Session volatile, journal de commande soumise | I03/I06 / I12/I13 |
| 09 | Keystore AES-GCM, clés non exportées | I03 / I11/I12/I14 |
| 10 | PdfDocument + PDFBox-Android pour Subject STORE | I10 / I13/I14 |
| 11 | PrintManager, FileProvider, partage par intent | I11 / I14 |
| 12 | Angus SMTP/Activation, TLS, queue durable | I11 / I14 |
| 13 | WorkManager unique et rattrapage | I11/I12 / I13 |
| 14 | Stockage privé + ContentResolver/SAF + AtomicFile | I04/I12 / I10/I11 |
| 15 | JVM/JUnit, Room réel, Compose, AndroidX/UI Automator, appareils | I01 / toutes, I14 |
| 16 | Gradle Wrapper/AGP/KSP, APK privé signé | I01 / I14 |

Direction imposée : presentation → application-api ; application → domain/ports ; infrastructure implémente les ports ; app compose. Aucun DAO/SQL/hash/clé dans Compose, pas logique métier dans composition root, pas de grand service monolithique, état global caché ou erreurs de sécurité/persistance avalées. Résultats critiques typés et erreurs sûres explicites. Pas abstraction sans besoin démontré.

Versions : I01 épingle et vérifie les versions stables compatibles de Wrapper/JDK/AGP/Kotlin/KSP/Compose/Room/driver/test dans un catalogue et une preuve de dépendances. Les versions testées S sont un point de preuve, pas licence pour adopter celles d'un exemple alpha ; l'annotationProcessor Java du spike ne remplace pas KSP cible. MinSdk 23, compile/targetSdk 36 de qualification initiale ; toute évolution ultérieure doit être motivée, compatible et testée. Dépendances spécialisées ajoutées seulement à leur phase (bcrypt I03, PDF I10, Angus/WorkManager I11). Besoin, maintenance, licence/notices, privilèges, transitives et compatibilité documentés avant ajout ; ni version dynamique ni bibliothèque redondante. Dépendance changeant l'architecture → STOP/reopen.

## 4. Global Non-Regression Rules

Ordre d'autorité : **1 B → 2 invariants transverses → 3 A validée → 4 phases acceptées → 5 phase courante**. Le comportement vérifié d'une phase acceptée devient référence sauf conflit avec niveau supérieur. Les adaptations mobiles explicitement décidées dans A (notamment TLS, session après mort et journal soumis) sont conservées, pas des changements tacites de B. Contradiction réelle → STOP, pas choix opportuniste.

G-NR s'applique à toutes phases : préserver les XINV-01–24, RF001–004 et J.4 ; ne pas modifier calculs/arrondis, quantités entières, restrictions de cible/historique, portée d'audit ou limites des drafts sans décision de périmètre. Aucun résultat d'audit absent n'est inventé. Les 31 domaines ont un propriétaire ci-dessous ; les phases ultérieures ne réimplémentent pas leur logique, elles appellent les use cases acceptés.

UI ajoutée seulement pour les use cases opérationnels de la phase. Les fonctions futures ne sont ni présentées comme réussies ni simulées en production ; seules fixtures de test sont permises. Aucun écran privilégié avant I03. La version intermédiaire reste développement synthétique, pas livrable client.

## 5. Development Safety & Global Phase Gate

**G-SCOPE** : après autorisation de phase, seuls `store-android/**` (future racine distincte) et son dossier `store-android/docs/evidence/Ixx/` sont modifiables, limités aux modules/packages cités dans la phase. Un changement de ce contrat nécessite une décision documentaire explicite. Les sources Electron, migrations Desktop, root package/lock/config, versions/dépendances Desktop, profils/backups réels et `spikes/**` sont hors périmètre. Étudier/porter délibérément un contrat source ciblé en lecture seule n'autorise pas sa mutation.

Profils synthétiques, DB fixtures et stockage d'émulateur/test exclusivement. Identifiant debug suffixé distinct ; aucun secret réel dans repo/logs/fixtures/artefacts. Aucun accès destructif à une donnée personnelle. Aucun dépôt de clé release, téléchargement arbitraire, composant privilégié exporté, SQL/table/chemin générique pilotable par UI. Pas d'autorité déduite de navigation.

**G-STOP**, obligatoire dans chaque phase : contradiction B/A ; régression acceptée ; violation sécurité/intégrité ; migration ou dépendance inexpliquée ; modification hors scope/2.0.1 ; test requis non fiable ou indisponible ; action destructrice touchant données réelles. Conserver preuves, arrêter la phase, aucun commit d'acceptation ni phase suivante ; jamais affaiblir assertion/invariant. Un vrai défaut se corrige dans le scope, puis revalidation ; une décision bloquante ou changement d'autorité doit être demandé.

**G-PASS** : toutes assertions requises et régressions pertinentes PASS, 0 FAIL, 0 test requis SKIPPED ; défauts bloquants clos avec preuve, build/lint/type compilation Android pertinents PASS, `git diff --check` PASS, seuls chemins permis. NOT TESTED n'est pas PASS. Un contrôle physique indisponible bloque la phase qui l'exige, pas fausse dispense.

**G-EVIDENCE** : dossier de phase avec base/commit/branche, chemins changés, versions/cible, commandes exactes/code retour, compte PASS/FAIL/SKIP, IDs d'exigences→tests, sorties concises et limites. Screenshots UI COMPACT/MEDIUM/EXPANDED si UI ; PID/phase durable/restart/intégrité si mort processus ; hashes d'artefacts si sortie. Pas logs massifs ou secrets. Rapport final court : statut, scope, tests, invariants, limites, commit, arbre, prochaine phase autorisable.

**G-COMMIT** : un commit revue/acceptation par phase, message `feat(android): Ixx ...` (I13 `fix(android): I13 ...`, I14 `test(android): I14 qualify release candidate`). Pas commit d'acceptation si gate en échec ; aucune réécriture de l'histoire Desktop, amend sans lien, push ou tag automatique. Séquence de petits commits uniquement si autorisée explicitement avant phase ; préserver un point final d'acceptation identifiable. Voir §12.

## 6. Dependency Graph

Ordre séquentiel d'acceptation obligatoire ; chaque flèche exige un commit précédent propre et accepté. Chemin critique de livraison = I01 → I14 ci-dessous (pas estimation calendaire). Les dépendances métier supplémentaires sont indiquées dans la roadmap ; aucune fonction opérationnelle n'attend un mécanisme prévu plus tard.

```mermaid
flowchart LR
  I01["I01 Fondation"] --> I02["I02 Données"] --> I03["I03 Sécurité/setup"]
  I03 --> I04["I04 Catalogue/stock/médias"] --> I05["I05 Équipe/présence"]
  I05 --> I06["I06 Caisse/vente/facture"] --> I07["I07 Réceptions/inventaires"]
  I07 --> I08["I08 Administration/chat/aide"] --> I09["I09 Accueil/rapports"]
  I09 --> I10["I10 PDF/import"] --> I11["I11 Print/share/SMTP"]
  I11 --> I12["I12 Backup/restore/reset"] --> I13["I13 Durcissement"] --> I14["I14 Qualification"]
```

Anti-cycles : audit writer/contexte et paramètres nécessaires au métier livrés en I03, pas reportés à l'UI d'audit I08 ; contrôle caisse pour switch/droits branché dès I03 sur repository I02, testé avec fixtures avant UI caisse I06. I02 prévoit hook recovery et snapshot pré-migration minimal privé ; aucun update d'une DB existante sans sauvegarde vérifiée, même avant le service utilisateur complet I12. I04 fournit médias partagés à I05 ; I07 fournit achats/inventaires à I09 ; I10 précède toute impression/envoi. Les sauvegardes complètes et leur scheduling attendent I12, après tous les domaines persistants, sans activer auparavant un Worker vide prétendant sauvegarder.

## 7. Implementation Roadmap

14 phases au total, dont I14 qualification séparée : P0=3, P1=5, P2=4, P3=2. Ce sont des priorités de construction, pas des fonctionnalités facultatives.

| Phase | Priorité | REQUIRES (outre contrat) | PRODUCES | UNLOCKS |
|---|---|---|---|---|
| I01 Fondation native | P0 | Step 5 accepté | Projet/modules/build/tests sans métier | I02 |
| I02 Données transactionnelles | P0 | I01 | Schéma logique/ports/Room/UoW/migrations | I03 |
| I03 Sécurité, bootstrap, configuration socle | P0 | I02 | Autorité/auth/session/secrets/setup/audit writer | I04 |
| I04 Catalogue, médias, stock/mouvements | P1 | I03 ; UoW I02 | Articles et stock autorisés, adaptateur médias RF001 | I05 |
| I05 Équipe et présence | P1 | I04 ; identité I03 | Profils/comptes/signature/corrections | I06 |
| I06 Caisse, POS, factures/paiements | P1 | I05 ; stock I04 | Vente RF004 et annulation, reçu local | I07 |
| I07 Fournisseurs, achats, inventaires | P1 | I06 ; stock/audit existants | Réceptions/comptages/compensations | I08 |
| I08 Administration, chat, diagnostics et aide | P2 | I07 ; auth/audit I03 | Surfaces admin, messages/alertes, guide | I09 |
| I09 Accueil et rapports | P2 | I08 ; données I04–07 | KPI/7 rapports/graphes/tableaux/snapshots | I10 |
| I10 PDF et import catalogue | P2 | I09 ; médias/catalogue I04 | Documents natifs/codec/import autorisé | I11 |
| I11 Impression, partage et e-mail | P2 | I10 ; secrets I03 | Adaptateurs/queue/WorkManager SMTP | I12 |
| I12 Sauvegarde, restauration et reset | P1 | I11 ; DB/fichiers/tous domaines | Recovery SP-01 produit/backup scheduling | I13 |
| I13 Durcissement intégré | P3 | I12 | Régressions/lifecycle/stress/sécurité clos | I14 |
| I14 Qualification finale | P3 | I13 | Preuves release, APK signé qualifié | Décision de distribution distincte |

## 8. Phase Contracts

Chaque section est exécutable avec F §§2–5 et §§9–13 + ses références ciblées. Les règles G-* sont incluses par référence sans recopier leur texte. « Hors scope » inclut toujours tout ce qui n'est pas explicitement permis et tous les non-goals B §14/A §28. REQUIRES/PRODUCES/UNLOCKS figurent aussi §7. Les chemins cités sont futurs, aucun créé ici.

### I01 — Fondation native [P0]

- OBJECTIVE : squelette compilable Android isolé, pas UI métier.
- AUTHORITATIVE INPUTS : F I01 ; A §§4–7,18,21–23, ADR01/02/03/15/16 ; P C15/C18.
- PREREQUISITES : Step 5 accepté, branche/base propre confirmée ; aucune implémentation existante supposée.
- IN SCOPE : `store-android/` Wrapper/catalogue, modules app/presentation/application-api/application/domain/infrastructure/testing, wiring Room/driver sans DB métier, écran smoke, thèmes/FR-EN/navigation typée, infrastructure de tests.
- OUT OF SCOPE : auth opérationnelle, tables métier, POS, copie du spike en produit, clé/artefact release distribué.
- ARCHITECTURE CONSTRAINTS : direction §3 testée ; JDK/AGP/KSP/Compose stables compatibles épinglés ; min23/cible36 ; configurations debug/release séparées, backup système/transfert implicite désactivés par règles appropriées.
- FUNCTIONAL CONTRACTS : socle Z/AA/AE seulement ; aucune fonction simulée présentée comme opérationnelle. Réserver applicationId proposé `com.vibe.store`, debug `.debug` : vérifier collision/maîtrise avant premier build distribué, demander arbitrage si indisponible ; pas nom définitif inventé après diffusion.
- SECURITY INVARIANTS : pas composant privilégié exporté, pas secret/DAO UI, debug distinct.
- DATA INVARIANTS : aucune DB Desktop ni donnée réelle ; aucune migration automatique.
- IMPLEMENTATION DELIVERABLES : squelette, catalogue verrouillé, tests de dépendances, README build et état des versions.
- TESTS REQUIRED : JVM smoke, frontière des modules (dépendance interdite détectée), compilation debug et configuration release sans clé prod, lancement smoke natif, UI trois classes.
- ACCEPTANCE CRITERIA : G-PASS ; skeleton construit/testé, privilèges absents et wiring vérifié sans métier.
- NON-REGRESSION CHECK : G-NR, diff strictement racine Android ; baseline Desktop inchangée.
- STOP CONDITIONS : G-STOP ; stack incompatible, nécessité de modifier root/Desktop, identité package incertaine au gate concerné.
- EVIDENCE REQUIRED : G-EVIDENCE + graphe modules/versions/cible et capture smoke.
- COMMIT POLICY : G-COMMIT, `feat(android): I01 establish native foundation`.
- NEXT PHASE UNLOCKED : I02 uniquement.

### I02 — Fondations de persistance [P0]

- OBJECTIVE : autorité de données Android et mécanisme transactionnel avant le métier.
- AUTHORITATIVE INPUTS : F I02 ; B AC/§9/XINV-09–20 ; A §§9–11,14,16 ; S SP-01 (mécanisme seulement).
- PREREQUISITES : I01 accepté.
- IN SCOPE : domain valeurs/ports, application UoW/DatabaseOwner/CommandCoordinator/barrière, infrastructure Room/DAO privés/schémas/migrations/testing ; modèle logique A–U, génération/journaux techniques.
- OUT OF SCOPE : use cases métier publics, import SQLite Desktop, UI backup complète, Workers opérationnels.
- ARCHITECTURE CONSTRAINTS : version mobile initiale 1, Room/BundledSQLite, schémas exportés/KSP, un propriétaire par génération, aucune ouverture parallèle cachée ; recovery hook avant ouverture ; snapshot privé quiescent pré-migration vérifié.
- FUNCTIONAL CONTRACTS : contrats repository typés, diagnostics intégrité internes ; valeurs monétaires/calendrier injectés pour parité, pas nouvelle sémantique.
- SECURITY INVARIANTS : pas SQL/table arbitraire public ; ports internes, DTO séparés.
- DATA INVARIANTS : FK/unicité/snapshots, WAL/FULL/FK sur connexions effectives, busy borné, atomicité/coroutines structurées ; aucune migration destructive/downgrade-reset.
- IMPLEMENTATION DELIVERABLES : schéma initial logique complet sans données, ports/DAO/UoW, fixture v1, mécanisme migration/snapshot préalable ; évolutions justifiées §11.
- TESTS REQUIRED : Room réel base neuve/relations/FK/contraintes, rollback après chaque écriture, contention, checkpoint/fermeture/reopen, intégrité ; migration synthétique test-only et restart ; version future refusée sans modification.
- ACCEPTANCE CRITERIA : G-PASS ; toutes relations prévues testées, erreur transactionnelle sans demi-état, snapshot préalable obligatoire pour DB existante.
- NON-REGRESSION CHECK : G-NR + modules/build I01 ; aucune compatibilité binaire Desktop requise.
- STOP CONDITIONS : G-STOP ; pragmas non effectifs, copie DB ouverte, migration non sûre.
- EVIDENCE REQUIRED : G-EVIDENCE + graphe relations/version, pragmas, intégrité/FK et snapshots synthétiques.
- COMMIT POLICY : G-COMMIT, `feat(android): I02 establish transactional persistence`.
- NEXT PHASE UNLOCKED : I03.

### I03 — Sécurité, bootstrap et configuration socle [P0]

- OBJECTIVE : rendre possibles uniquement des opérations explicitement autorisées.
- AUTHORITATIVE INPUTS : F I03 ; B A/B/C/T/U/AB, §4, FLOW-01/11, XINV-01–08/19 ; A §§7–8,12–15.
- PREREQUISITES : I02 accepté, repository caisse et audit disponibles même sans UI métier.
- IN SCOPE : auth/RBAC/session/use cases/DTO, bcrypt/JCA/Keystore, lockout/récupération, bootstrap Owner, paramètres boutique/devise/remises/langue/thème, audit writer/contexte, formulaires auth et navigation protégée.
- OUT OF SCOPE : écran administration complète, équipe opérationnelle, envoi SMTP, POS ; pas extension de reprise wizard.
- ARCHITECTURE CONSTRAINTS : coût bcrypt source 10/parité UTF-8/72 octets, secrets hors UI thread ; session mémoire génération-aware, droits relus dans mutation ; aucun bearer persistant.
- FUNCTIONAL CONTRACTS : bootstrap unique, pas password par défaut ; identité NFC/casse/espaces/ambiguïté B ; récupération limitée et retour formulaire propre ; switch échoué conserve ancienne session ; caisse ouverte/opération critique bloque switch/logout, panier confirmé ; pas timeout J.4/élévation/pointage automatique.
- SECURITY INVARIANTS : G-SEC §9 ; Owner protégé ; refus subtractifs uniquement et conflit/caisse cible ouverte ; allowlists à tous retours/logs/export UI, hash/réponse/ciphertext exclus ; secret SMTP exposé comme disponibilité seulement.
- DATA INVARIANTS : compte/lockout/refus/paramètres/audit prévus persistés ; Owner et setup restent opérations distinctes ; snapshots responsabilité/caisse capturés, pas backfill.
- IMPLEMENTATION DELIVERABLES : autorité utilisable, adaptateur secrets, audit writer réutilisable, parcours bootstrap/login/recovery/switch ; fixtures rôles.
- TESTS REQUIRED : appels directs refusés, Manager→Owner interdit, compte inactif/ambigu, clé invalidée sans plaintext, brute-force/retry ; caisse fixture ouverte bloque switch/refus ; rotation/background/mort réelle→login sans clôture caisse/présence ; saisie continue/œil.
- ACCEPTANCE CRITERIA : G-PASS + G-SEC ; zéro mutation métier après refus (trace sécurité autorisée distincte), zéro secret public.
- NON-REGRESSION CHECK : G-NR + persistance I02 et frontières I01.
- STOP CONDITIONS : G-STOP ; différence bcrypt/identité non expliquée, autorité UI, sessions restaurées sans preuve.
- EVIDENCE REQUIRED : G-EVIDENCE + matrice rôles/appels directs, scan des projections et preuves lifecycle.
- COMMIT POLICY : G-COMMIT, `feat(android): I03 establish identity and authorization`.
- NEXT PHASE UNLOCKED : I04.

### I04 — Catalogue, médias et stock [P1]

- OBJECTIVE : catalogue/stock réels sûrs, prérequis des ventes et photos équipe.
- AUTHORITATIVE INPUTS : F I04 ; B H/I/J/K, FLOW-04, XINV-10/18/22 ; A §§7,9–11,14,18.
- PREREQUISITES : I03 accepté.
- IN SCOPE : packages catalog/stock/media, use cases/DAO/UI, SAF/ContentResolver privé, mouvement initial/ajustement/archive et historique prix ; adaptateur média commun avec politiques article/photo distinctes.
- OUT OF SCOPE : import PDF I10, inventaire/réception I07, UI CSV, caméra/scanner nouveaux.
- ARCHITECTURE CONSTRAINTS : préparation durable→référence DB→cleanup post-commit RF001 ; fichiers gérés relatifs allowlist, pas accès global stockage.
- FUNCTIONAL CONTRACTS : CRUD/archive/recherche/filtres/doublons ; quantité entière, prix parité, stock cible justifié ; archive garde références historiques. Formats JPEG/PNG/WebP, article ≤5 Mio, photo future ≤512 Kio.
- SECURITY INVARIANTS : Employee lecture seulement ; discriminateurs historiques stricts ; présence jamais supprimable par cette voie, même Owner ; type inconnu/malformé refuse sans mutation.
- DATA INVARIANTS : stock+mouvement atomiques ; original externe intact ; média actif/archivé jamais supprimé par cleanup échoué.
- IMPLEMENTATION DELIVERABLES : use cases/catalogue adaptatif, service médias, contrôles stock et filtres mouvements.
- TESTS REQUIRED : G-STOCK/G-MEDIA/G-HISTORY §9 ; URI révoquée/copie partielle/format invalide/espace insuffisant ; kill avant/après référence ; annulation picker et listes volumineuses/clavier/FR-EN.
- ACCEPTANCE CRITERIA : G-PASS ; aucune référence cassée ni stock négatif, refus sans effet, tous attributs consultables aux trois largeurs.
- NON-REGRESSION CHECK : G-NR + auth/permissions/UoW ; pas secret dans fiche média.
- STOP CONDITIONS : G-STOP ; suppression originale/active, table choisie par entrée.
- EVIDENCE REQUIRED : G-EVIDENCE + snapshots stock/références et phases d'interruption RF001.
- COMMIT POLICY : G-COMMIT, `feat(android): I04 implement catalog media and stock`.
- NEXT PHASE UNLOCKED : I05.

### I05 — Équipe et présence [P1]

- OBJECTIVE : comptes/profils liés et pointage personnel sans confusion d'identités.
- AUTHORITATIVE INPUTS : F I05 ; B O/P, FLOW-07, XINV-03–08/19 ; A §§8,11–14,18.
- PREREQUISITES : I04 accepté ; réutiliser auth/audit I03 et médias I04.
- IN SCOPE : packages team/attendance, UI fiches/filtres, création compte permise/photo, password manuel/temporaire spécialisé, présence jour/personnelle/gestion et corrections.
- OUT OF SCOPE : paie, délégation Owner, productivité, sortie automatique ou changement forcé de password inventé.
- ARCHITECTURE CONSTRAINTS : signataire vérifié par secret propre, acteur facilitateur distinct ; service média partagé, jamais second gestionnaire de comptes.
- FUNCTIONAL CONTRACTS : états RH ≠ droits ; Owner protégé, restrictions Manager ; heure courante injectée, originaux/correcteur/motif conservés, sessions ouvertes hors total heures terminées.
- SECURITY INVARIANTS : Manager sans droit de correction ne corrige pas ; Employee n'administre pas comptes ; password temporaire résultat spécialisé non journalisé.
- DATA INVARIANTS : transitions/intervalle valides, correction/audit cohérents ; login/logout/crash ne signent rien.
- IMPLEMENTATION DELIVERABLES : parcours équipe/fiche du jour, signatures et corrections autorisées, projections sans secrets.
- TESTS REQUIRED : G-AUTH/G-OWNER/G-PRESENCE ; mauvais secret, cible interdite, signature par autre acteur, intervalle invalide, mutation interrompue, focus/retry/multicaractères/password/photo.
- ACCEPTANCE CRITERIA : G-PASS ; attribution correcte, pas pointage fictif ni permissions issues du statut RH.
- NON-REGRESSION CHECK : G-NR + I03/I04 ; aucune duplication RBAC/médias.
- STOP CONDITIONS : G-STOP ; confusion acteur/signataire/caisse ou correction non autorisée.
- EVIDENCE REQUIRED : G-EVIDENCE + fixtures signatures/corrections et captures trois largeurs.
- COMMIT POLICY : G-COMMIT, `feat(android): I05 implement team and attendance`.
- NEXT PHASE UNLOCKED : I06.

### I06 — Caisse, POS, facture et paiements [P1]

- OBJECTIVE : première chaîne d'encaissement entièrement atomique et replay sûr.
- AUTHORITATIVE INPUTS : F I06/§9 gates vente ; B E/F/G, FLOW-02/03/11, XINV-09–14/17/19 ; A §§9–13,18.
- PREREQUISITES : I05 accepté, acteurs/catalogue/stock/audit réels et UoW.
- IN SCOPE : cash/sale/invoice/payment, modèle reçu local, POS adaptatif, journal privé de commande soumise, consultation historique/annulation ; intégration guards switch/caisse.
- OUT OF SCOPE : PDF/impression avant I10/I11, Mobile Money/banque, quantités fractionnaires, panier durable ou nouvelle règle de remise.
- ARCHITECTURE CONSTRAINTS : commande figée/clé stable avant soumission, UoW unique ; aucun PDF/SMTP/Worker dans transaction de vente, mutation au scope application.
- FUNCTIONAL CONTRACTS : caisse unique/acteur, ouverture idempotente, attendu/compté/écart ; zéro au panier retiré, lignes positives retenues ; espèces/remise/reçu/monnaie selon source ; référence/date/heure/snapshots ; annulation motivée ≥3 caractères, histoire conservée.
- SECURITY INVARIANTS : acteur et prix autoritaires hors UI ; droit/caisse courants revérifiés transactionnellement ; replay n'accorde jamais une ancienne autorisation.
- DATA INVARIANTS : G-SALE/G-RF004/G-CANCEL §9 ; preuve et facture même transaction ; fermeture caisse reste snapshot, pas comptabilité de remboursement rétroactive inventée.
- IMPLEMENTATION DELIVERABLES : encaissement/reçu/annulation, résolution explicite résultat ambigu après re-login ; actions documentaires non simulées en attendant leur phase.
- TESTS REQUIRED : tous cas RF004 §9, concurrence/double toucher, injection entre écritures, kill avant commit/après commit avant réponse, restart sans auto-vente ; caisse ferme/switch interdit, orientation/IME/trois largeurs.
- ACCEPTANCE CRITERIA : G-PASS + trois gates vente/replay/annulation ; aucun second effet durable ni rejet du replay exact dû au stock déjà consommé.
- NON-REGRESSION CHECK : G-NR + I02–05, audit à couverture exacte et guards J.4.
- STOP CONDITIONS : G-STOP ; replay clé seule, commande nouvelle après timeout, demi-paiement/stock/facture.
- EVIDENCE REQUIRED : G-EVIDENCE + comptages DB/stock avant-après, preuve canonique/PID et matrice refus.
- COMMIT POLICY : G-COMMIT, `feat(android): I06 implement atomic sales and cash`.
- NEXT PHASE UNLOCKED : I07.

### I07 — Fournisseurs, achats et inventaires [P1]

- OBJECTIVE : réceptions et rapprochements stock avec drafts sans effet métier prématuré.
- AUTHORITATIVE INPUTS : F I07 ; B L/M/N, FLOW-05/06, XINV-15–17 ; A §§10–13,18.
- PREREQUISITES : I06 accepté ; réutiliser catalogue/stock/UoW/audit.
- IN SCOPE : supplier/purchase/inventory, drafts/lignes/détails, création fournisseur/article contextualisée, validation et annulation achat.
- OUT OF SCOPE : reprise éditeur étendue, annulation inventaire nouvelle, paiements fournisseurs bancaires/dettes, facture achat PDF autonome non établie.
- ARCHITECTURE CONSTRAINTS : mêmes coordinateurs/transactions ; création imbriquée ne valide jamais implicitement la réception.
- FUNCTIONAL CONTRACTS : fournisseur actif facultatif, comptage/écarts contre stock courant ; validation inventaire une fois ; achat validé ne reçoit pas deux fois ; compensation achat seulement si stock suffisant.
- SECURITY INVARIANTS : Employee refusé par appels directs ; cibles/types bornés, droits courants.
- DATA INVARIANTS : G-INVENTORY/G-PURCHASE §9 ; drafts persistés sans effet stock ; pas prétention d'équivalence RF004 pour création achat.
- IMPLEMENTATION DELIVERABLES : parcours réception/comptage et filtres/détail ; statut historique conservé.
- TESTS REQUIRED : validation répétée, stock modifié depuis comptage, annulation draft/validé, compensation impossible rollback total ; kill pendant validation, restart draft/détail intact, formulaires imbriqués et saisie numérique.
- ACCEPTANCE CRITERIA : G-PASS + gates inventaire/achat ; stock/mouvements/statut cohérents et jamais appliqués deux fois.
- NON-REGRESSION CHECK : G-NR + vente/caisse/stock/audit ; une réception n'altère pas les snapshots de facture.
- STOP CONDITIONS : G-STOP ; draft change stock, validation concurrente double, perte contexte de création entraînant opération erronée.
- EVIDENCE REQUIRED : G-EVIDENCE + scénarios drafts/compensations et assertions DB après interruptions.
- COMMIT POLICY : G-COMMIT, `feat(android): I07 implement purchases and inventories`.
- NEXT PHASE UNLOCKED : I08.

### I08 — Administration, messagerie locale, diagnostics et aide [P2]

- OBJECTIVE : surfaces de gestion/support au-dessus des services acceptés.
- AUTHORITATIVE INPUTS : F I08 ; B R/T/U/Y/AE, §4 ; A §§7,15,18–20.
- PREREQUISITES : I07 accepté ; services auth/refus/config/audit I03 disponibles.
- IN SCOPE : UI admin/permissions effectives/configuration, audit filtres/pagination, diagnostics/logs sûrs ; chat individuel/diffusion/alertes, états personnels ; aide FR/EN recherchable et navigation réversible.
- OUT OF SCOPE : Internet chat/push, écran e-mails simulant des envois (I11), intégration backup (I12), réécriture des services RBAC.
- ARCHITECTURE CONSTRAINTS : lectures aussi autorisées, diagnostics ponctuels non réparateurs, aucun log brut identité/secret ; rafraîchissement lifecycle-aware, pas service permanent.
- FUNCTIONAL CONTRACTS : Employee chat mais pas gestion Messagerie ; nouveau message seulement dans chat, limites 200/10000 ; audit acteur/responsable/caisse exacts ; aide procédures/perte appareil/urgence/passation, sections futures clairement non opérationnelles jusqu'à intégration.
- SECURITY INVARIANTS : G-DENY/G-DTO/G-AUDIT ; visibilité messages destinataire, suppression personnelle seulement ; aucune délégation Owner implicite.
- DATA INVARIANTS : paramètres allowlist, devise ne convertit pas l'histoire ; notifications/messages durables, historiques inconnus non reconstruits.
- IMPLEMENTATION DELIVERABLES : surfaces administration/support/chat/aide, événements d'alertes raccordés aux domaines existants.
- TESTS REQUIRED : droits révoqués/navigation-retour, destinataire forgé/lecture inter-compte, offline/restart/non-lus, filtres audit exacts, erreurs/logs expurgés ; paramètres invalides sans mutation, aide/tables accessibles.
- ACCEPTANCE CRITERIA : G-PASS ; lecture/gestion exactement selon rôle ; diagnostic ne réinitialise rien.
- NON-REGRESSION CHECK : G-NR + I03–07 ; pas nouvel endpoint générique ni élargissement d'audit.
- STOP CONDITIONS : G-STOP ; secret exposé, destinataire usurpé, réparation destructive automatique.
- EVIDENCE REQUIRED : G-EVIDENCE + matrice visibilité/administration et échantillons audit sans secrets.
- COMMIT POLICY : G-COMMIT, `feat(android): I08 integrate administration and local support`.
- NEXT PHASE UNLOCKED : I09.

### I09 — Accueil, KPI et rapports [P2]

- OBJECTIVE : interprétation autorisée de tous les domaines métier acceptés.
- AUTHORITATIVE INPUTS : F I09 ; B D/Q/Z, §12–13 ; A §§10,17–19.
- PREREQUISITES : I08 accepté, sources ventes/achats/stock/présence complètes.
- IN SCOPE : query models/reporting/dashboard, sept rapports, filtres, Canvas/graphes/tableaux dépliables, snapshot DocumentModel sans génération PDF.
- OUT OF SCOPE : comptabilité/bénéfice certifié, changement universel de calendrier, exports infinis ou envoi.
- ARCHITECTURE CONSTRAINTS : projections bornées/DAO encapsulés, pas nouvel entrepôt ; stale results ignorés, calculs testables indépendants Compose.
- FUNCTIONAL CONTRACTS : CA validé/annulations distinctes, moyenne zéro sans ventes, remises allouées, marge estimée explicitement ; stock actuel distinct période, achats création/statut, heures terminées ; présents/fiche du jour/listes indépendantes/contexte mois.
- SECURITY INVARIANTS : rapports/filtrage selon droits ; raccourcis Home n'accordent aucun accès supplémentaire.
- DATA INVARIANTS : lectures sans mutation, dates/fuseaux/arrondis conformes fixtures ; snapshot sortie conserve filtres/période/devise.
- IMPLEMENTATION DELIVERABLES : Home et sept rapports avec modèles pour I10, états vide/erreur/réessai.
- TESTS REQUIRED : fixtures calculs/seuils/date/fuseau/DST, résultat obsolète/échec filtre, données volumineuses ; toucher/légendes/alternatives tabulaires, trois largeurs/langues/thèmes.
- ACCEPTANCE CRITERIA : G-PASS ; concordance graphe/tableau/snapshot et aucune donnée non autorisée.
- NON-REGRESSION CHECK : G-NR + domaines sources/permissions, aucune réécriture des données pour arranger KPI.
- STOP CONDITIONS : G-STOP ; écarts de calcul inexpliqués, période trompeuse ou fuite par agrégat.
- EVIDENCE REQUIRED : G-EVIDENCE + jeux de calcul attendus/obtenus et captures adaptatives.
- COMMIT POLICY : G-COMMIT, `feat(android): I09 implement dashboard and reporting`.
- NEXT PHASE UNLOCKED : I10.

### I10 — PDF natif et import catalogue [P2]

- OBJECTIVE : documents lisibles et données structurées validées sans autorité du fichier.
- AUTHORITATIVE INPUTS : F I10 ; B G/H/Q/AE/XINV-24 ; A §17/ADR10 ; S SP-02 natif/politique V1.
- PREREQUISITES : I09 accepté ; modèle facture I06, rapports I09 et catalogue I04.
- IN SCOPE : infrastructure documents/codec/parser borné, sorties facture/rapport/catalogue/aide, export SAF et import catalogue via use case autorisé ; tests des métadonnées et pagination.
- OUT OF SCOPE : OCR/PDF arbitraire, import SQLite/archives Desktop, authenticité V1 inventée, facture achat autonome nouvelle, impression/SMTP.
- ARCHITECTURE CONSTRAINTS : PdfDocument rend, PDFBox-Android transporte Subject ; aucune DB/session/réseau dans parser ; vérifier maintenance/advisories/notices et tester confinement isolé/budgets temps/mémoire avant activation de l'import externe.
- FUNCTIONAL CONTRACTS : fond clair indépendant thème, dates/filtres/colonnes/pagination, génération offline ; STORE_DATA_V1 canonique/UTF-8/base64/versions, compatibilité fixture 2.0.1 ; validation métier complète après parsing avant persistance.
- SECURITY INVARIANTS : G-PDF §9 ; V1 non authentifié, validité structurelle ≠ confiance ; droits courants/doublons/articles/prix/quantités contrôlés par autorité, zéro import partiel.
- DATA INVARIANTS : export sans mutation, import atomique après validation, aucun média/chemin implicite depuis payload ; limites format documentées, bornes du spike non promues silencieusement en règles métier.
- IMPLEMENTATION DELIVERABLES : codec/document renderer/import, corpus synthétique et prévisualisation/export sûr.
- TESTS REQUIRED : SP-02 transposé, malformed/missing/versions/corrompu/tronqué/chiffré/excessif, Unicode/montants/canonicalisation, entrée valide altérée traitée non fiable ; import refusé zéro effet ; QA rendu réel, annulation/révocation SAF/espace plein.
- ACCEPTANCE CRITERIA : G-PASS + G-PDF ; bornes/confinement démontrés, aucun secret/DB du parser, toutes pages contrôlées et sorties lisibles.
- NON-REGRESSION CHECK : G-NR + catalogue/stock/rapports/facture ; pas second effet vente après erreur export.
- STOP CONDITIONS : G-STOP ; codec incapable/maintenance insuffisante ou confinement impossible → ADR10 reopen, jamais substitution automatique.
- EVIDENCE REQUIRED : G-EVIDENCE + corpus/rejets/limites, SHA-256 PDF, pages rendues et inspection visuelle.
- COMMIT POLICY : G-COMMIT, `feat(android): I10 implement native documents and catalog import`.
- NEXT PHASE UNLOCKED : I11.

### I11 — Impression, partage, SMTP et queue [P2]

- OBJECTIVE : effets externes indépendants de la réussite métier locale.
- AUTHORITATIVE INPUTS : F I11 ; B S/T/G/Q/AE/XINV-23–24 ; A §§15,17,20/ADR11–13.
- PREREQUISITES : I10 accepté et PDF stables ; secrets/config I03.
- IN SCOPE : PrintManager/PrintDocumentAdapter, FileProvider read-only/chooser, Angus/Activation, configuration/test SMTP Owner, queue/relance autorisée/WorkManager unique, UI gestion e-mails et actions reçu/aide.
- OUT OF SCOPE : serveur STORE, OAuth/SASL nouveaux, email exactement une fois, background POS, daemon toujours actif ; backup Worker avant I12.
- ARCHITECTURE CONSTRAINTS : TLS implicite ou STARTTLS obligatoire avant secrets, certificat/hostname vérifiés, timeouts ; Worker autorité interne bornée, droits/compte courants et génération/barrière recontrôlés.
- FUNCTIONAL CONTRACTS : reçu Imprimer / Nouvelle vente verte / PDF séparés ; partage ne marque pas SMTP sent ; queue pending/sending/sent/failed, 5 erreurs bornées/backoff, configuration absente n'épuise pas essais, sending interrompu revient pending ; contexte/date dans e-mail.
- SECURITY INVARIANTS : pièces jointes internes autorisées, grants temporaires seuls, pas chemin arbitraire/clé/log secret ; perte Keystore requiert reconfiguration, jamais plaintext.
- DATA INVARIANTS : erreur réseau/print/share n'annule ni ne rejoue vente ; crash après remise SMTP peut dupliquer livraison, limite affichée.
- IMPLEMENTATION DELIVERABLES : sorties intégrées et queue durable, scheduler de retries avec rattrapage, aide mise à jour.
- TESTS REQUIRED : offline/retour réseau, timeout/TLS invalide/config absente, droits retirés, double Worker/contraintes/restart/process death ; imprimante indisponible/annulation/service système, URI refusée ; serveur SMTP de test contrôlé, aucune adresse réelle non autorisée.
- ACCEPTANCE CRITERIA : G-PASS + G-DELIVERY ; aucune réussite externe fictive ni effet métier secondaire ; impression/partage intégration Android prouvés (matériel réel final I14).
- NON-REGRESSION CHECK : G-NR + documents/auth/secrets/vente ; stock/facture inchangés après échec d'envoi.
- STOP CONDITIONS : G-STOP ; contournement TLS, pièce jointe arbitraire, Worker se faisant passer pour Owner.
- EVIDENCE REQUIRED : G-EVIDENCE + transitions queue/capture erreurs expurgées et sorties print/share.
- COMMIT POLICY : G-COMMIT, `feat(android): I11 integrate print share and delivery`.
- NEXT PHASE UNLOCKED : I12.

### I12 — Backup, restore, reset et recovery [P1]

- OBJECTIVE : protocole SP-01 complet sur le modèle produit, avant métier au redémarrage.
- AUTHORITATIVE INPUTS : F I12 ; B V/W/X/AD, FLOW-08–10, XINV-20–23 ; A §§9,12–16,20 ; S SP-01 natif.
- PREREQUISITES : I11 accepté ; tous domaines durables/DB/médias/queue présents, snapshot pré-migration I02.
- IN SCOPE : maintenance/recovery/générations/archives, app startup gate, UI backup/restore/reset, diagnostics/aide associés ; WorkManager backup automatique et cleanup non référencé.
- OUT OF SCOPE : sync/cloud/compatibilité backup Desktop, garantie physique absolue, clé Keystore exportée, reset de secours implicite.
- ARCHITECTURE CONSTRAINTS : barrière draine lecteurs/écrivains/Workers ; checkpoint vérifié/fermeture→snapshot DB+médias→contrôles→ZIP stream ; petits journaux/pointeur AtomicFile verrouillés ; reprise avant ouverture migrante/UI/Workers.
- FUNCTIONAL CONTRACTS : manuel Owner, automatique après Owner actif une/jour référence/rétention 7 auto ; manuelles/pré-opération conservées ; restore Owner+confirmation sans seconde réauth inventée ; reset Owner+password+confirmations+pré-backup ; sessions invalidées après bascule.
- SECURITY INVARIANTS : archive non fiable bornée, pas zip-slip/symlink/doublon, Owner candidat actif ; recovery sans session seulement pour opération déjà autorisée journalisée.
- DATA INVARIANTS : G-RECOVERY §9 ; old ou new complet, aucun mix ; migrer/valider copie candidate, backup préalable intact ; reset avant commit récupère old, après commit termine new sans ressusciter comptes.
- IMPLEMENTATION DELIVERABLES : backup/export/restauration/reset complets et scheduling unique, état progrès/erreur/reprise ; reconfiguration SMTP si clé indisponible.
- TESTS REQUIRED : 7 frontières SP-01 aux deux niveaux dont kills externes réels, reprises ×3, intégrité/FK/médias ; vraie fixture multi-domaines, low-space/corruption candidate/archives excessives/URI révoquée, migration candidate, concurrence POS/Worker, export partiel ; duplications/rattrapage scheduling et retention.
- ACCEPTANCE CRITERIA : G-PASS + G-RECOVERY ; pas accès métier avant état validé, rollback ou blocage sûr si aucun état validable ; aucune archive incomplète annoncée valide.
- NON-REGRESSION CHECK : G-NR + intégralité données/relations/snapshots et replay vente ; secrets non exportés en clair.
- STOP CONDITIONS : G-STOP ; état mixte irrécupérable → ADR06 reopen, aucune suppression WAL/données pour passer.
- EVIDENCE REQUIRED : G-EVIDENCE + matrice phases/PID/générations/hashes, DB/FK/références avant-après, échecs d'espace et backup.
- COMMIT POLICY : G-COMMIT, `feat(android): I12 implement durable maintenance recovery`.
- NEXT PHASE UNLOCKED : I13.

### I13 — Durcissement et régression intégrés [P3]

- OBJECTIVE : fermer les lacunes techniques avant qualification d'un artefact gelé.
- AUTHORITATIVE INPUTS : F I13/§9–11 ; A §§18–22,26–27 ; S limites résiduelles ; preuves I01–12 seulement selon risques.
- PREREQUISITES : I12 accepté, périmètre fonctionnel complet.
- IN SCOPE : corrections ciblées Android, tests intégrés, perf/budgets, accessibilité/lifecycle, revue sécurité/dépendances/manifest ; stabiliser release minifiée sans distribuer.
- OUT OF SCOPE : nouvelles fonctions, architecture substituée, réaudit Desktop, qualification humaine finale prétendue par automatisation.
- ARCHITECTURE CONSTRAINTS : stack/modules gelés, appareil min API et cible, ARM64/pages 16 Kio natif à vérifier, dépendances/notices/advisories vérifiées sans mise à jour aveugle.
- FUNCTIONAL CONTRACTS : 31 domaines, 24 invariants et 11 flows couverts ; aide complète conforme au comportement actuel, pas commandes futures inactives restantes.
- SECURITY INVARIANTS : revue appels directs/exports/intents/DTO/logs/Workers, no network core, clés/non-debug release, import confiné et borné.
- DATA INVARIANTS : G-NR global, stress contention/peu d'espace/low-memory/kill, upgrades fixtures sans perte ; pas purge opportuniste.
- IMPLEMENTATION DELIVERABLES : clôture des défauts, matrice couverture, seuils mesurés sur fixture et appareils déclarés, dossier candidat pour I14.
- TESTS REQUIRED : suite Android élargie, cycles background/rotation/switch/restart, JVM+Room+Compose+adaptateurs ; COMPACT/MEDIUM/EXPANDED portrait/paysage/IME/FR-EN/thèmes/TalkBack, listes volumineuses et appareil faible ; ABI16K/release minifiée et parser hostile.
- ACCEPTANCE CRITERIA : G-PASS ; aucun blocker sécurité/intégrité/fonction requise, budgets et limites mesurés reproductibles, trace tous IDs.
- NON-REGRESSION CHECK : G-NR + suites acceptées I01–12 selon matrice d'impact, élargies au jalon.
- STOP CONDITIONS : G-STOP ; appareil requis inaccessible, test flaky, vulnérabilité sans mitigation validée, dépassement non résolu des budgets.
- EVIDENCE REQUIRED : G-EVIDENCE + tableau défaut→fix→tests, profil mémoire/latence/données, targets et gaps zéro requis.
- COMMIT POLICY : G-COMMIT, `fix(android): I13 harden integrated application`.
- NEXT PHASE UNLOCKED : I14.

### I14 — Qualification finale et candidate release [P3]

- OBJECTIVE : qualifier séparément un commit/artefact figé, sans développement fonctionnel.
- AUTHORITATIVE INPUTS : F I14/§14 ; A §§21–22 ; preuve I13 et matrice complète.
- PREREQUISITES : I13 accepté/propre, appareils téléphone/tablette et canaux de test disponibles ; applicationId stable confirmé, clé release détenue hors repo et sauvegardée avec accord propriétaire.
- IN SCOPE : tests/évidence de qualification, builds release/signature/artefacts dans sorties dédiées, install/update/desinstallation sur profils synthétiques.
- OUT OF SCOPE : nouvelles fonctionnalités, corrections discrètes dans le candidat, distribution/push/tag automatique, AAB sans exigence de distribution autorisée.
- ARCHITECTURE CONSTRAINTS : APK privé signé, versionName 3.0.0 premier release, versionCode croissant ; debug suffixé, min23/cible36 revalidés avant diffusion ; signature/identité stables pour update.
- FUNCTIONAL CONTRACTS : couverture finale §14, aucun transfert de statut Windows ni du spike ; téléphones/tablettes réellement acceptés, offline complet.
- SECURITY INVARIANTS : clé privée jamais repo/log, APK sans fixtures/secrets/debug composants ; SBOM/licences/advisories et autorisations finales vérifiés.
- DATA INVARIANTS : fresh/update/restart/restore préserver données, uninstall efface privé/clé mais pas exports choisis ; backup externe préalable démontré.
- IMPLEMENTATION DELIVERABLES : rapport final, APK signé SHA-256 et certificat public fingerprint, matrice environnement/résultats ; AAB seulement si requis/autorisé.
- TESTS REQUIRED : suite complète Android + §14, signature/apk/install physiques, upgrade fixture 3.x applicable, kill-process/backup/restore/reset, PDF/print/share/SMTP contrôlés.
- ACCEPTANCE CRITERIA : G-PASS + G-RELEASE ; tous contrôles humains requis attestés, 0 requis NOT TESTED ; artefact exactement lié au commit testé.
- NON-REGRESSION CHECK : G-NR global et bilan 31/31,19/19,16/16 ; données Desktop intactes.
- STOP CONDITIONS : G-STOP ; échec qualification retourne au propriétaire de phase pour remédiation acceptée puis nouveau gel ; absence de clé/appareil/preuve bloque la release.
- EVIDENCE REQUIRED : G-EVIDENCE + rapport de qualification signé/attesté, hash/versions/commits/cibles et résultats humains explicites.
- COMMIT POLICY : G-COMMIT, `test(android): I14 qualify release candidate` ; commit d'évidence référence le commit produit et l'APK exact, aucun binaire/secret ajouté à l'histoire sans accord.
- NEXT PHASE UNLOCKED : décision utilisateur de distribution, pas nouvelle implémentation automatique.

## 9. Cross-Phase Test Strategy & Critical Gates

Pyramide : règles pures JVM/JUnit d'abord → use cases → Room/driver réel → Compose UI → instrumentation/lifecycle là où Android importe. Pas émulateur pour une simple fonction pure. Pendant édition : tests ciblés ; fin de phase : suite de phase + régressions impactées + build/lint Android pertinents ; jalons I03/I06/I12/I13 : suite plus large des domaines existants ; I14 : suite complète et matériel. Aucun full suite après chaque petite modification. Pas de test Desktop relancé pour valider Android.

Traçabilité légère : ID de test stable vers XINV/FLOW/gate, phase propriétaire et résultat dans preuve ; chaque nouveau test critique porte cet ID. Pas cérémonial pour détail cosmétique. Le gate suivant est une obligation, pas un résultat acquis à Step 5 :

| ID gate | Contrat binaire / tests minimum | Propriétaire |
|---|---|---|
| G-AUTH | XINV-01/07 : identité normalisée/casse/ambiguïté, password sensible, lockout/récupération, compte inactif, bcrypt UTF-8/72 octets parité | I03 |
| G-SEC | G-AUTH + G-DENY/G-OWNER/G-DTO ; bootstrap unique et J.4 switch/échec/caisse sans élévation | I03 |
| G-DENY | XINV-02/04/05 : appels directs, action/type inconnu/null/manquant/malformé, acteur forgé, rôle/droits modifiés concurremment, refus subtractifs ; zéro mutation métier après refus | I03 puis chaque endpoint |
| G-OWNER | XINV-03/04 : aucune rétrogradation/désactivation/refus Owner ; Manager cible/role interdits, caisse cible ouverte/conflict interdit | I03/I05 |
| G-DTO | XINV-08 : identité/session/listes/erreurs/logs/diagnostics/état UI sans password/hash/security_answer_hash/ciphertext ; résultat temporaire spécialisé contrôlé | I03, I08, I13 |
| G-SALE | XINV-09–11 : autorisation, caisse propre ouverte, validation lignes/stock/entiers/monnaie/remise, facture+lignes+paiement+stock+mouvements+preuve même commit ; rollback à chaque failpoint, réponse perdue ; audit selon G-AUDIT | I06 |
| G-RF004 | XINV-12–14 : matrice exacte détaillée ci-dessous, même moteur Android ; zéro second effet après replay/refus | I06 |
| G-STOCK | XINV-10/11/15–17 : stock non négatif/entier et deltas cohérents sous concurrence/échec | I04/I06/I07 |
| G-CANCEL | XINV-17 : droit/motif/état, compensation stock/paiement une fois, facture et audit prévu conservés ; deuxième appel sans nouvelle compensation, rollback | I06 |
| G-INVENTORY | XINV-16 : draft/comptage durable, aucun effet prévalidation, stock courant à validation unique, deltas+mouvements+statut atomiques, reprise après kill | I07 |
| G-PURCHASE | XINV-15/17 : draft sans réception, validation répétée sans double effet ; draft annulé sans compensation, achat validé compense une fois si stock suffisant sinon aucun effet ; crash rollback/commit | I07 |
| G-HISTORY | XINV-18 : enum allowlist, ressource backend fixe, inconnu refuse ; suppression historique jamais présence même Owner, pas compensation achat implicite | I04 |
| G-AUDIT | XINV-19 : acteur/responsable/caisse capturés, données anciennes manquantes non inventées, secrets absents ; audit atomique où contrat l'exige ; création vente trace facture/lignes/mouvements, pas nouvel événement universel supposé | I03 puis producteurs/I08 |
| G-MEDIA | XINV-22/RF001 : original intact, préparation avant référence, échec précommit compense seulement non référencé, cleanup échoué postcommit conserve succès/référence active ou archivée | I04/I05 |
| G-PRESENCE | XINV-06 : password signataire propre, facilitateur distinct, heure courante, correction autorisée/originaux, jamais signature login/logout/crash | I05 |
| G-RECOVERY | XINV-20–22 : backup DB+médias vérifié, restore candidat sûr et pré-backup, reset Owner/password, old/new complet, phase durable/kill/restart/reprise ×3, FK/intégrité/médias et invalidation session | I12 |
| G-PDF | XINV-24/SP-02 : natif lisible/canonique/versionné, fixture 2.0.1, rejets et bornes, V1 non authentifié, autorisation+validation métier avant transaction import, aucun import partiel | I10 |
| G-DELIVERY | XINV-23/24 : TLS/grants/queue/retries/droits/duplicate Worker/restart, jamais seconde vente, partage ≠ SMTP sent, duplication externe possible déclarée | I11 |
| G-LIFE | Session ≠ brouillon ≠ commit ; kill réel et reprise depuis disque, pas auto-vente/pointage, résultats anciens session/génération rejetés | I03/I06/I07/I11/I12/I13 |
| G-RELEASE | Tous contrôles §14 attestés sur artefact gelé, aucune preuve manquante requise | I14 |

### Matrice RF004 obligatoire (G-RF004)

Identité logique mobile versionnée = multiensemble trié `(articleId, quantité)` avec doublons conservés, remise **demandée** défaut 0, reçu `OMITTED` ou `EXPLICIT` avec valeur exacte validée. Ordre JSON/lignes sans effet ; -0 normalisé 0, non-fini refusé ; ni fusion de doublons ni arrondi additionnel pour rendre deux intentions équivalentes. Prix/stock actuels ne réécrivent pas l'intention historique. Comparaison côté use case/UoW, jamais renderer.

- Nouvelle commande valide : SUCCESS unique avec preuve dans même transaction.
- Même clé/acteur autorisé/même caisse originale actuellement ouverte/facture validée/commande équivalente : même facture SUCCESS, aucun second effet. Vérifier après consommation du stock : pas de revalidation du stock courant pour replay.
- Même clé mais acteur différent/inactif/droit retiré, caisse absente/fermée/autre, facture annulée, panier/quantité/multiplicité/remise demandée/reçu différent : REJECT.
- Ordre propriétés/lignes différent : équivalent ; omission reçu versus reçu explicite même total : non équivalent ; doublons versus lignes fusionnées : non équivalents.
- Preuve historique NULL/absente/version non interprétable : REJECT sans reconstruction ni réécriture historique. Fixture Android synthétique, pas import Desktop.
- Chaque rejet/replay : comptes factures/paiements/mouvements et stock inchangés ; trace de refus sécurité éventuellement prévue distinguée du métier.
- Clé concurrente/double soumission/timeout/kill avant commit et après commit avant réponse : un seul résultat durable ; journal soumis conservé, re-login puis résolution explicite, aucune nouvelle clé ni vente automatique.

## 10. Lifecycle & Adaptive Validation Strategy

Toute phase UI/durable documente les quatre classes ; n'en invente pas la durabilité :

| Classe | Règle | Preuve prévue |
|---|---|---|
| EPHEMERAL UI STATE | filtres/position/navigation bornés, panier édité en ViewModel ; password jamais SavedState, destruction formulaire efface secret | I01 puis UI de chaque phase, rotation/recomposition/IME/retour ; pas kill requis pour détail purement visuel |
| SESSION STATE | mémoire application, persiste Activity recreation pas mort processus ; background seul ne déconnecte pas ; foreground revalide droits | I03, guards I06, I13 ; nouveau PID→login, pas changement présence/caisse |
| DRAFT BUSINESS STATE | achat/inventaire enregistrés DB survivent ; reprise éditeur complète reste DEFER ; commande vente déjà soumise journalisée distincte du panier | I06/I07 : restart/kill, données intactes, résultat résolu explicitement sans auto-commit |
| COMMITTED BUSINESS STATE | DB/médias/queue/journal durables selon protocole, jamais callback de fermeture requis | I02/I04–07/I11–12 : kill aux frontières pertinentes, intégrité et absence double effet |

Preuve mort processus = phase persistée + PID avant + terminaison externe + disparition + nouveau PID + assertions depuis stockage, pas seulement recréation Activity. Faible mémoire/espace, background/foreground, force-stop et reboot/relancement sont distingués ; aucun maintien continu garanti. I11 valide retries réseau/contraintes/droits/doublons WorkManager ; I12 backup quotidien/rattrapage/rétention et cleanup sûr ; I13 interactions/concurrence/barrière. Aucun POS dans WorkManager.

Chaque phase présentant UI valide COMPACT <600dp, MEDIUM 600–839dp, EXPANDED ≥840dp, téléphone/tablette, portrait/paysage lorsque redimensionnement/IME change le parcours. Cibles ≥48dp, toucher sans hover, focus/password/retry, libellé destination/retour système protégé, fermeture dialog pendant mutation gardée, listes paginées et zones scroll indépendantes, colonnes/attributs disponibles en fiche compacte, graphe avec alternative tableau. FR/EN/thèmes et erreurs visibles ; aucune identité pixel Desktop imposée. I13 consolide TalkBack/contraste/réduction mouvement et faible appareil ; I14 fait accepter matériellement téléphone et tablette.

## 11. Data / Migration Strategy

Deux migrations distinctes : **A)** schéma Android futur 3.x, **B)** compatibilité d'import de formats sélectionnés. Aucun transfert SQLite Desktop ni équivalence migration Desktop 18→mobile. I02 établit version mobile 1 et toutes relations logiques ; domaines suivants ajoutent les use cases, pas des modèles parallèles. Tout delta de schéma ultérieur nécessite motif, migration ordonnée/transactionnelle, schéma exporté, fixtures ancienne→nouvelle/base neuve/restart et preuve de préservation ; pas réédition silencieuse du schéma déjà accepté.

Backup avant ouverture migrante de DB existante, aucune purge/reset de convenance, downgrade/version trop récente refusés explicitement. I02 fournit le mécanisme minimal sécurisé ; I12 le raccorde aux archives/recovery complets. Avant première diffusion, la version réellement figée avec son historique de migrations devient base de référence des futurs 3.x ; pas obligation de rester à version 1 si des évolutions justifiées ont eu lieu.

Compatibilité B limitée au catalogue PDF STORE_DATA_V1 synthétique 2.0.1 en I10 ; archives Desktop, sync et copier une vraie DB restent hors scope. Valeurs binary64/arrondis source et calendriers par rapport conservés via fixtures explicites ; pas nouvelle arithmétique bancaire ou conversion de devises historiques implicite.

## 12. Commit & Worktree Policy / Efficient Execution

Avant phase : vérifier branche/worktree attendu, SHA précédent accepté, statut propre (ou état compris explicitement approuvé), lire F clauses globales et **seule section de phase courante**, puis sections B/A/P/S citées nécessaires. Inspecter uniquement modules/fichiers liés ; recherches ciblées, pas reconstruction de l'histoire ni réaudit global. Source 2.0.1 ciblée en lecture seule seulement si parité à préciser ; contradiction → demander décision, pas fouiller sans fin.

Pendant phase : tests ciblés et petites modifications, pas full suite répétée. Fin : `git diff`, `git status`, contrôle de scope, tests/gates requis, G-EVIDENCE puis G-COMMIT et arbre propre avant prochaine phase. Modification inattendue → STOP jusqu'à explication, ne pas écraser travail utilisateur. Un rollback est revue/revert autorisé, jamais reset dur ni reset de données personnelles.

Prompt compact futur :

```text
MISSION: STORE 3.0 Ixx uniquement
BASELINE COMMIT: <SHA d'acceptation précédent>
PHASE SECTION: docs/STORE_3_IMPLEMENTATION_CONTRACT.md Ixx
GLOBAL CONTRACT: G-SCOPE/G-NR/G-STOP/G-PASS/G-EVIDENCE/G-COMMIT
ALLOWED FILES: <modules/packages Ixx + evidence/Ixx>
TESTS: <IDs/tests de Ixx et régressions impactées>
STOP/ACCEPTANCE: F Ixx ; ne pas lancer la phase suivante
FINAL REPORT: statut, scope, tests/preuves, limites, SHA, arbre, next
```

## 13. Architecture Reopen Procedure

Une ADR LOCKED ne se réexamine que sur preuve concrète d'incapacité à satisfaire une exigence. Arrêter la phase affectée et sa chaîne dépendante ; aucun remplacement automatique ni réduction fonctionnelle. Produire :

```text
ARCHITECTURE REOPEN REQUEST
- decision: ADRxx
- evidence: reproducer, cible/version, résultat attendu/observé
- affected requirement: B/XINV/FLOW/gate
- affected phase: Ixx (+ dépendants)
- minimum decision requiring reconsideration: <strict minimum>
```

Attendre décision explicite, modifier le contrat/ADR seulement avec autorisation, répéter les preuves affectées. Un défaut d'implémentation réparable sans changement de décision n'est pas une excuse pour changer de stack. Échec sécurité/intégrité ne se contourne jamais par suppression du test.

## 14. Final Qualification Contract (I14)

Qualification distincte de construction : figer commit produit, versions, jeu synthétique et environnement ; aucun test exécuté maintenant. Tous résultats automatiques/humains requis doivent être liés à l'APK signé testé, pas à un debug voisin.

| Groupe | Preuves nécessaires avant diffusion |
|---|---|
| Automatisé | Suite complète JVM/use cases/Room/migrations/Compose/adaptateurs/lifecycle ; lint/build release minifié ; 0 FAIL/0 requis SKIP, source propre |
| Installation | Fresh install→startup→création DB→Owner/setup ; offline ; version/schema/ABI ; applicationId/signature exacts |
| Update | Fixture Android version précédente supportée→cible, conservation métier/médias/clé/queue ; interruption/restart ; si aucun ancien 3.x publié, fixture technique Android contrôlée + update même package/clé, pas ancien APK inventé |
| Désinstallation | Backup externe préalable, uninstall efface privé/Keystore, reinstall frais ; restore autorisé de données synthétiques et reconfiguration SMTP ; exports choisis non effacés |
| Métier | FLOW-01–11, RF001–004/J.4, droits Owner/Manager/Employee/refus, caisse/vente/annulation/réception/inventaire/présence, rapports/limites exactes |
| Recovery | Backup/restore/reset DB+médias, intégrité/FK, kills externes phases durables et réponse vente perdue, reprise idempotente ; low-space/corruption/block sûr ; interruption appareil contrôlée distincte du kill quand test matériel réalisable, sinon aucune promesse de durabilité physique |
| Matériel/UI | Au moins téléphone et tablette Android réels, cible/minimum pris en charge et ABI/pages 16 Kio qualifiés, appareil faible ; trois largeurs/rotation/clavier/toucher/TalkBack/FR-EN/thèmes ; validations humaines explicites |
| Documents/sorties | PDF clair multi-pages lisible/metadata/import, impression via service/périphérique réel disponible, partage/grants, SMTP de test TLS/offline/retry ; absence périphérique testée ne remplace pas succès du parcours pris en charge |
| Sécurité | Appels directs/DTO/logs/manifest/components/permissions ; secrets/Keystore/cloud backup/transfert OEM ; parser confiné/budgets/corpus hostile ; SBOM/licences/advisories, aucun secret/fixture/debug en release |
| Artefact | APK privé signé, SHA-256/certificat public/versions/commit produit, clé hors repo sauvegardée ; AAB seulement si canal l'exige et autorisation séparée ; pas push/tag/upload automatique |

La qualification n'est PASS que si tous gates requis sont prouvés ; une cible/peripheral requis indisponible reste BLOCKED. Les limites physiques explicitement hors garantie restent déclarées, pas déguisées en test PASS. Défaut découvert : arrêt, remédiation dans phase propriétaire avec régression, nouveau commit candidat, requalification de l'artefact exact. Aucune diffusion avant décision utilisateur ; « architecture LOCKED » ne signifie jamais « production-ready ».

## 15. Coverage Matrix & Implementation Readiness Gate

Un propriétaire par domaine ; intégrations ultérieures explicites ne dupliquent pas la responsabilité métier. Les sous-capacités UI/output peuvent être terminées dans leur phase dépendante indiquée.

| Domaine B | Phase propriétaire | Intégration/complément prévu |
|---|---|---|
| A Identité | I03 | équipe I05 |
| B Permissions | I03 | éditeur admin I08 |
| C Sessions | I03 | guards caisse/résultat ambigu I06 |
| D Accueil | I09 | présence/caisse déjà disponibles |
| E Caisses | I06 | rapports I09 |
| F Ventes/POS | I06 | sorties I10/I11 |
| G Factures/annulation | I06 | PDF I10, impression I11 |
| H Articles | I04 | import/export PDF I10 |
| I Médias | I04 | photo employé I05, backup I12 |
| J Stock | I04 | effets vente I06 et réceptions/comptage I07 |
| K Mouvements | I04 | producteurs I06/I07 |
| L Inventaires | I07 | rapports I09 |
| M Achats | I07 | rapports I09 |
| N Fournisseurs | I07 | sélection contextuelle même phase |
| O Équipe | I05 | administration I08 |
| P Présence | I05 | Home/rapports I09 |
| Q Rapports | I09 | PDF I10, e-mail I11 |
| R Messagerie/alertes | I08 | gestion e-mails I11 sans chat distant |
| S E-mails | I11 | recovery queue I12 |
| T Paramètres | I03 | gestion I08, configuration/test SMTP I11 |
| U Audit | I03 | writer initial, producteurs par phase, consultation I08 |
| V Backups | I12 | snapshot technique pré-migration I02 distinct |
| W Restore | I12 | qualification I14 |
| X Reset | I12 | qualification I14 |
| Y Diagnostic | I08 | primitives intégrité I02, maintenance I12 |
| Z Localisation | I01 | chaque UI, formulaires monétaires I04/I06, PDF I10 |
| AA Accessibilité | I01 | chaque UI, consolidation I13 et humain I14 |
| AB Frontière sécurité | I03 | barrières compilation I01, toutes opérations |
| AC Persistance | I02 | transactions de chaque domaine |
| AD Récupération | I12 | hook I02, lifecycle/session I03, commande soumise I06, queue I11 |
| AE Aide/navigation | I08 | navigation socle I01/I03, guide incrémental, impression I11 |

| Capacité P | Destination et preuve |
|---|---|
| C01 DB locale | I02 |
| C02 Transactions/concurrence/durabilité | I02 ; métier I06/I07, maintenance I12 |
| C03 Migrations/intégrité | I02 ; upgrade I14 |
| C04 Frontière autorisée | I03 |
| C05 Crypto/hash/randomness | I03 ; hashes médias I04 et backup I12 |
| C06 Secrets | I03 ; SMTP/restore I11/I12 |
| C07 Fichiers/publication/recovery | I04 ; générations I12 |
| C08 Accès médias | I04/I05 |
| C09 Import/export | I10 ; archives I12 |
| C10 Snapshot/backup/restore/reset | I12 ; préparation pré-migration I02 |
| C11 PDF offline | I10 |
| C12 Impression | I11 ; réel I14 |
| C13 E-mail/queue | I11 |
| C14 Lifecycle/tâches/reprise | I03/I06, Workers I11/I12, consolidation I13 |
| C15 UI/accessibilité/navigation | I01, chaque UI, I13/I14 |
| C16 Permissions appareil/accès limité | I01 manifest, I04 SAF, I10/I11/I12 grants/imports |
| C17 Diagnostic/logs sûrs | I03 audit, I08 diagnostic, I12 santé maintenance |
| C18 Packaging/update/données | I01 configuration, I02 migrations, I14 qualification |
| C19 Partage fichiers | I11 ; archives I12 par adaptateur accepté |

Contrôle documentaire Step 5 : **31/31 domaines assignés ; 19/19 capacités assignées ; 16/16 ADR préservées**. Fondation→persistance→sécurité précèdent métier ; aucun mécanisme nécessaire repoussé derrière son consommateur. Gates vente/RF004/annulation/inventaire/achat/sécurité/lifecycle/SP-01/SP-02 définis ; chaque phase a tests/STOP/acceptation/preuves/commit, qualification finale séparée. Les obligations résiduelles des spikes sont placées I10–14, pas supprimées. STORE 2.0.1 immuable ; aucune implémentation, dépendance, test ou build produit lancé à Step 5.

**READY FOR IMPLEMENTATION PHASE I01 — FONDATION NATIVE**, sous mission explicite limitée à I01 et commit documentaire propre. Les phases suivantes ne démarrent pas automatiquement.
