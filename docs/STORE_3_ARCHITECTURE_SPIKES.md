# STORE 3.0 — Architecture validation spikes

## Step 4.2 — exécution native (29 septembre 2026)

**STEP 4.2 PASS : SP-01 natif PASS ; SP-02 natif PASS ; STEP 4 CLOSED.**
Les sections Step 4.1 ci-dessous restent la trace historique des seuls essais hôte.
Elles ne constituent pas le verdict de Step 4.2.

Projet jetable : `spikes/android/native-validation/`. JDK 21.0.1 réutilisé ;
Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 ; Room 2.8.5,
BundledSQLiteDriver 2.7.1 (moteur observé SQLite 3.50.1), PDFBox-Android 2.0.27.0.
SDK/caches/AVD exclusivement dans ce projet et ignorés par Git.
Command-line tools 22.0 (ZIP 15859902, SHA-256 vérifié
`90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A`).
SDK platform 36 ; build-tools 35.0.0 sélectionné par AGP (36.0.0 aussi provisionné).

Cible unique : Android 16 / API 36 / x86_64 / pages 4096 octets,
AVD `store_spike_api36`, Emulator 37.1.11 build 15917651, WHPX ; adb 37.0.1.
Fingerprint : `Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys`.
Il ne s'agit ni d'un appareil ARM64 ni d'une certification de compatibilité.

Le premier lancement sous chemin Unicode a échoué (exit 1073741845) ;
l'essai logiciel n'a pas fourni d'Android exploitable. Alias ASCII temporaire
`V:` vers le même projet et réparation du pointeur INI synthétique : démarrage
WHPX réussi, `sys.boot_completed=1`. Aucune fonctionnalité Windows installée/modifiée.
Une erreur de banc Kotlin (`PdfDocument` non `Closeable`) a été corrigée avec
`try/finally` et fermeture explicite. Compilation ciblée : **BUILD SUCCESSFUL**,
38 tâches, puis installation adb **Success**. APK debug synthétique SHA-256 :
`C85A4E6D3106694EB4EAEAE8F26F3B77D91BCA37443F8D0E1EF65071DF05CE83`.
Ces incidents d'outillage ne sont ni un échec des invariants ni un PASS natif.

Commandes exécutées (variables SDK/JDK/caches locales omises) :

```text
gradle :app:assembleDebug --no-daemon --console=plain
emulator -avd store_spike_api36 -no-window -no-audio -no-snapshot -gpu swiftshader -cores 2 -memory 2048 -show-kernel
adb -s emulator-5554 shell getprop sys.boot_completed
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
python run_native.py
```

Le runner ne vise que `test.store.spike` : phase persistée avec PID →
`am force-stop` externe → absence de PID vérifiée → nouveau processus → trois
reprises. Deux niveaux séparés : exception déterministe, puis processus réellement
tué pendant qu'il attend à la phase demandée. La DB et les deux médias sont publiés
ensemble via le pointeur AtomicFile ; pas deux remplacements indépendants.

Politique PDF : décision **A, validation d'entrée non fiable, pas authentification**,
conforme à la baseline Step 2 H / §Security (pas de promesse d'authenticité de tout
export). V1 n'a pas de MAC/signature/checksum propre : une modification restant
valide n'est pas détectée. Toute persistance future doit encore autoriser l'import
et valider intégralement le domaine. Le banc parser n'a aucun appel de mutation DB.
Compatibilité catalogue 2.0.1 requise, fixture synthétique conservée en asset.
PDFBox-Android sert uniquement au Subject ; PdfDocument réalise le rendu.
Licence Apache-2.0, API 19+ selon le
[mainteneur](https://github.com/TomRoush/PdfBox-Android), consulté le 29 septembre.
Pas de certification de sécurité du parser ni de ses dépendances déduite du README.

### Résultats natifs conservés

`spikes/android/native-validation/evidence/native-results.json` contient les
résultats bruts : **14 scénarios, 42 reprises PASS**, puis **13 groupes PDF PASS**.
Le runner s'est terminé avec code 0. Les phases/PID sont issus du processus Android,
pas fabriqués par le modèle hôte. Exemples du niveau B (arrêt externe alors que le
processus attend, pas exception ni simple recréation d'Activity) :

| Frontière | PID tué | PID des trois reprises | État |
|---|---|---|---|
| 1 avant staging | 4184 | 4243 / 4295 / 4346 | OLD |
| 2 média 1/2 écrit | 4403 | 4465 / 4515 / 4567 | OLD |
| 3 staging complet | 4626 | 4688 / 4744 / 4793 | OLD |
| 4 journal PREPARED | 4852 | 4913 / 4966 / 5020 | OLD |
| 5 AtomicFile temporaire écrit/sync avant finishWrite | 5075 | 5138 / 5191 / 5243 | OLD |
| 6 pointeur DB+médias publié | 5299 | 5358 / 5414 / 5463 | NEW |
| 7 vérification terminée avant cleanup | 5522 | 5580 / 5633 / 5688 | NEW |

Le niveau A couvre aussi les 7 points, 21 reprises supplémentaires. Chaque reprise
vérifie ouverture Room, `integrity_check=ok`, FK sans erreur, schema=1, parent
génération/montant=125025 attendu, deux références média et SHA-256 concordants.
La transaction négative vérifie rejet FK et rollback de l'insertion précédente.
Checkpoint TRUNCATE après fermeture Room contrôlé `(0,0,0)`. Aucun mélange ni perte
silencieuse observé dans ces cas. Les trois reprises aboutissent au même état
métier ; seul le marqueur technique `verified` est réécrit idempotemment.
Les générations inactives et le staging sont conservés, pas nettoyés en production.

SP-02 : Android PdfDocument produit un PDF blanc/noir, PDFBox-Android transporte
uniquement le Subject `STORE_DATA_V1:` ; relecture du fichier réellement persisté.
Trois produits/accents/montants conservés ; ordre des clés canonique ; rejet JSON
malformé, Subject absent, versions préfixe/payload inconnues, base64 corrompu,
prix négatif/sous-centime, quantité fractionnaire et PDF tronqué. Fixture synthétique
2.0.1 lue et équivalente. Prix modifié mais encore valide accepté **conformément à
la politique V1 non authentifiée**, pas faux test de tamper detection.

QA visuelle via compétence PDF : `python render_native.py`, moteur PDFium hôte
uniquement pour visualiser les **PDF générés sur Android**, pas comme codec candidat.
Deux pages 833×1179 inspectées : accents lisibles, colonnes alignées, aucun clipping
ni chevauchement ; rendus avant/après injection strictement identiques (même hash).

| Preuve | SHA-256 |
|---|---|
| native-results.json | `36C7F929D5FCB3F2406DB9EE6B19BFA26674628AEE76A0678E5C65C60960F84A` |
| android-visible.pdf | `840EEF1A0C70F50FA17EC64A9472BB0FA8277A36C792731257E542828EE5A87B` |
| android-structured.pdf | `0E10D374556ABB53F8B97B872456CFF3700C32CA60F87EE2E0DBA759F8EBA747` |
| Chaque PNG natif rendu | `7A10C3DB20B54E822FBC12A4F8A95E2E421FAF7AF08A22287442F22C0A8DC9B4` |

### Portée de la clôture

OQ-05/OQ-07 RESOLVED ; **10/10 questions, 16 LOCKED, 0 PROVISIONAL**, 9/9 risques
critical/high avec mitigation, 0 sans mitigation. La demande Step 4.2 autorise une
cible native unique, pas une matrice ni une certification du produit. Restent des
gates d'implémentation/qualification : RF004 réel, reset/Workers/concurrence,
corruption candidate native, faible espace/coupures physiques, ARM64/min API/16 Kio,
SAF, parsing chiffré/excessif/confiné avec budgets mémoire/temps, advisories/transitives,
release minifiée, imprimantes et UI. Aucun de ces tests n'est prétendu PASS ici.
La stack n'a pas été remplacée ; le banc ne devient pas code STORE de production.

Aucun test/build/lint/audit/packaging STORE lancé, aucune donnée personnelle ou
permission STORE modifiée. Seuls le banc isolé et les deux documents autorisés
changent, avec conservation des preuves Step 4.1 déjà présentes. Le contrôle visuel
PDF a servi à la clôture SP-02. Commit de preuves uniquement, sans push/tag.
Émulateur arrêté après les essais (`adb emu kill`, OK) et alias temporaire `V:`
retiré ; outils et données synthétiques conservés localement pour reproduction.

---

28 septembre 2026. **STEP 4.1: BLOCKED. SP-01: INCONCLUSIVE. SP-02: INCONCLUSIVE.**

Référence : [architecture](STORE_3_ANDROID_ARCHITECTURE.md), commit `34ce139b030fd322f7364d49b761508a2b265a50`. Les preuves ci-dessous sont des contrôles préparatoires **sur hôte Windows**, pas l'exécution de la stack Android sélectionnée. Aucun PASS d'architecture n'est déduit d'un modèle Python ou d'un codec Node.

## Environnement, méthode et isolation

- JDK 21.0.1 disponible ; Java par défaut pointe aussi vers un JRE 17. Pas de `adb`, `gradle`, `sdkmanager`, `emulator` dans PATH. `ANDROID_HOME`/`ANDROID_SDK_ROOT` absents ; SDK/Android Studio non trouvés dans les emplacements standards vérifiés (`AppData/Local/Android`, `C:/Android`, `Program Files/Android`, `Users/sterl/Android/Sdk`). Cela ne prouve pas leur absence sur tout disque ; aucune recherche globale effectuée.
- Aucun SDK, émulateur ou projet Android provisionné pendant cette exécution. Aucun appareil Android connecté/testé par adb. L'autorisation d'installation isolée n'a pas été utilisée pour provisionner une chaîne Android complète ; absence d'environnement cible effectivement exploitable dans ces essais.
- Python 3.13.5 / SQLite 3.49.1 pour le modèle de récupération ; Node 22.17.0 / `pdf-lib` déjà installé, utilisé en lecture seule pour le contrôle du contrat PDF existant. Pas de dépendance root installée/modifiée.
- Moteur de rendu de QA `pypdfium2==5.13.0` installé uniquement sous `spikes/android/sp02-pdf-codec/.deps/`, après échec du Poppler/MiKTeX existant. Il n'est ni candidat de remplacement du codec Android ni technologie de production choisie. Pillow existant utilisé pour PNG. Temporaires de l'installation réussie localisés sous `.pip-tmp/` du spike.
- Données entièrement synthétiques ; aucune lecture de profil/DB utilisateur. Le code source n'a été consulté que pour le contrat PDF (`stockPdfService.ts`, `importProductsPdf`, enveloppe d'export) et les sections SP-01/02 du document d'architecture.
- Le premier lancement du modèle a échoué sur une permission Windows de création de sous-dossier, avant le test. Le lancement autorisé hors sandbox a réussi, toujours limité au répertoire `out/` synthétique. Cet incident d'environnement n'est pas un résultat de récupération.

Fichiers jetables, aucun composant de production :

| Fichier | Rôle |
|---|---|
| `spikes/android/sp01-recovery/host_model.py` | Modèle générations/journal/SQLite + sous-processus brusquement terminés |
| `spikes/android/sp02-pdf-codec/host_contract.mjs` | Contrôle validation/canonicalisation/enveloppe PDF source, aucune DB |
| `spikes/android/sp02-pdf-codec/render_fixtures.py` | Rendu de deux fixtures synthétiques pour inspection visuelle |
| `spikes/android/.gitignore` | Exclut sorties, caches et dépendances locales ; aucune règle root changée |

## SP-01 — hypothèse et méthode

Hypothèse : préparer une génération DB+médias complète puis basculer une seule référence active évite une combinaison active de DB ancienne et médias nouveaux (ou inversement).

Modèle : deux générations `old`/`new`, SQLite `user_version=1`, une ligne d'état et deux références média avec SHA-256/FK ; WAL/FULL puis checkpoint TRUNCATE et fermeture avant copie. Fichiers média synthétiques, pas vraies images. Pointeur JSON publié par `os.replace` après flush/fsync ; journal `PREPARED` désigne old/new. **Ces primitives hôte ne sont pas Android AtomicFile/Room.**

Interruption : sous-processus Python `os._exit(87)`, sans déroulement de finally ; récupération dans un **nouveau processus**, répétée trois fois par point. C'est une terminaison abrupte réelle de processus **hôte**, utilisée pour simuler les frontières du protocole. Ce n'est ni un kill Android, ni une coupure électrique, ni une exception interceptée dans le même processus.

### Preuves obtenues

| Point | Frontière injectée | État après chacune des 3 reprises |
|---|---|---|
| 1 | Avant staging | OLD complet |
| 2 | Pendant staging médias, 1 fichier sur 2 | OLD complet ; staging incomplet non activé |
| 3 | Après staging, avant marqueur/remplacement | OLD complet |
| 4 | Après journal PREPARED écrit | OLD complet |
| 5 | Pointeur temporaire écrit, avant bascule | OLD complet |
| 6 | Pointeur basculé, avant vérification médias nouveaux | NEW complet |
| 7 | Nouveau jeu vérifié, avant cleanup | NEW complet |

Les points 5/6 représentent les deux côtés de la bascule **commune** DB+médias, pas deux remplacements indépendants. Aucun test d'interruption à l'intérieur d'un appel système de rename n'est prétendu.

**7/7 frontières, 21/21 reprises : PASS du modèle hôte.** À chaque reprise : DB ouvrable, `integrity_check=ok`, FK valides, version 1, état génération attendu, deux médias correspondant à leurs hashes. La DB old reste identique octet pour octet. Trois contrôles négatifs supplémentaires : DB new corrompue, média new manquant, média new altéré → génération old valide, aucune acceptation de new invalide. Aucun état mixte observé dans ces cas.

Machine d'état effectivement exercée :

```text
OLD actif
  → staging partiel/inactif
  → NEW complet/inactif
  → journal PREPARED
  → pointeur temporaire
  → NEW actif
  → validation reprise

redémarrage :
  actif valide              → garder actif
  NEW invalide + OLD valide → repointer OLD
  aucun jeu validable       → erreur (branche non exercée ici)
```

Temporaires de pointeur nettoyés ; ancienne génération et staging partiel retenus comme preuves inactives, non supprimés récursivement. Aucun ramasse-miettes complet ni rétention production n'est validé.

### Résultat et limite résiduelle

**SP-01 INCONCLUSIVE pour Android.** Le modèle est cohérent aux frontières testées, sans signe de perte silencieuse dans ce périmètre. Mais aucune preuve Room/BundledSQLiteDriver, pool/connexion concurrente, checkpoint Android, AtomicFile/fsync répertoire, kill Android, faible espace réel, ABI/pages 16 Kio, RF004 intégré, reset ou Workers. Aucun protocole Android n'est donc déclaré techniquement qualifié.

Conséquence : ADR06 reste PROVISIONAL, OQ-05 bloquante. Ne pas transférer ce code Python en production ; le prochain essai doit exécuter le protocole sélectionné sur une cible Android isolée. Aucun redesign automatique.

## SP-02 — contrat, hypothèse et méthode

Contrat source confirmé : Subject PDF `STORE_DATA_V1:` + base64 UTF-8 JSON ; payload `kind="stocks"`, `version=1`, tableau `products`. Limites source : écriture payload 2 000 000 octets, PDF import 25 000 000 octets, 10 000 produits. Champs produit : name, hashtag, category, description, price, stockQuantity, minStockThreshold. Pas OCR ni extraction des tableaux visuels comme protocole.

Contrôle hôte : sérialisation avec ordre explicite des clés, ordre des produits préservé ; validation forme/types, nom/catégorie non vides, montants finis non négatifs à précision centime, quantités entières sûres non négatives ; UTF-8 et base64 stricts. Borne de chaîne additionnelle 64 000 caractères **propre au banc**, pas changement autorisé de règle métier. Le JSON source n'était pas intrinsèquement canonique : le lecteur contrôle aussi une enveloppe source avec ordre de clés différent.

Human-visible : catalogue synthétique blanc/noir, trois lignes avec accents et valeurs 1250.25, 0.50, 999.99. Machine-readable : Subject versionné séparé. Fixture de compatibilité construite avec l'algorithme exact de l'enveloppe 2.0.1 et données fictives ; **ce n'est pas une facture exportée depuis le runtime Electron**. Aucun module DB/application importé.

### Preuves obtenues

Commande hôte : `node spikes/android/sp02-pdf-codec/host_contract.mjs`.

| Cas obligatoire / complément | Observation hôte |
|---|---|
| Aller-retour valide après écriture/relecture disque | PASS, trois produits identiques |
| Plusieurs enregistrements | PASS, ordre conservé |
| Unicode/accents | PASS, Café/Épices/Thé préservés |
| Montants décimaux | PASS, valeurs identiques |
| Payload JSON malformé | Rejet |
| Payload absent | Rejet |
| Version préfixe ou payload non supportée | Rejet |
| Base64 corrompu / produit altéré devenu invalide | Rejet |
| Canonicalisation : ordre clés différent | Mêmes octets JSON ; ordre produits différent reste différent |
| PDF tronqué | Rejet ; warnings du parser attendus dans ce cas négatif |
| Quantité fractionnaire / sous-centime / dépassement borne | Rejet |
| Enveloppe source 2.0.1 synthétique | Lecture validée |
| Modification restant valide (prix 100) | Acceptée : limite explicite V1, pas authentification |
| PDF normalement lisible | Rendu puis inspection PNG : PASS des deux fixtures |

**12 groupes d'assertions hôte PASS**, couvrant plusieurs sous-cas ; cela n'est pas 12 tests Android. Rendu séparé avec PDFium hôte, deux pages à 833×1179, accents lisibles, colonnes alignées, pas de chevauchement constaté ; les deux PNG ont le même SHA-256. La compétence PDF a servi à cette QA visuelle seulement.

**Limite d'intégrité essentielle :** le format V1 ne contient ni signature/MAC ni checksum autonome de payload. Une corruption invalide est rejetée ; une modification sémantiquement valide n'est pas détectable comme falsification. Aucun faux test n'exige son rejet. Si « altered payload » signifie rejeter toute modification valide, ce besoin est **non satisfait** par V1 et exige une décision de format/confiance, pas une assertion affaiblie. Même un checksum recalculable ne prouverait pas l'authenticité.

### Résultat et limite résiduelle

**SP-02 INCONCLUSIVE pour le choix Android.** Aller-retour/validation du contrat existant prouvés sans navigateur ni Electron, mais avec Node/pdf-lib et **non** avec PdfDocument/PDFBox-Android. Aucun résultat sur maintenance/advisories du candidat, confinement natif, limites mémoire/temps du parser Android, release minifiée, SAF, PDF chiffré natif ou génération PdfDocument. PDFium de QA n'est pas une preuve du codec retenu.

Compatibilité : convention catalogue 2.0.1 requise par ADR10 et contrôlée par fixture synthétique ; aucune compatibilité DB Desktop ni compatibilité de PDF arbitraires requise. Le modèle valide des données seulement ; aucun import ne persiste dans STORE.

Conséquence : ADR10 reste PROVISIONAL et OQ-07 bloquante. Pas de remplacement de bibliothèque ni ajout de protocole V2 décidé pendant ce contrôle.

## Preuves conservées et reproduction

Sorties locales ignorées par Git, volontairement conservées :

- `spikes/android/sp01-recovery/out/results.json`, plus générations synthétiques de chaque scénario.
- `spikes/android/sp02-pdf-codec/out/results.json`, `synthetic-catalog.pdf`, `synthetic-2.0.1-envelope.pdf` et leurs PNG.
- Le JSON SP-02 mentionne une QA visuelle séparée requise : celle-ci a ensuite été exécutée et consignée ici, sans modifier le résultat brut du runner.

Commandes exécutées :

```text
python spikes/android/sp01-recovery/host_model.py
node spikes/android/sp02-pdf-codec/host_contract.mjs
python spikes/android/sp02-pdf-codec/render_fixtures.py
```

Le renderer nécessite `pypdfium2==5.13.0` dans son `.deps` isolé et Pillow disponible ; pas dans les dépendances de STORE. Les commandes créent des fixtures, pas du code applicatif. SHA-256 des preuves de cette exécution :

| Preuve | SHA-256 |
|---|---|
| host_model.py | `36A18D65325261DE6ECFDA6C56BD93C6F097FAEA61B2C5411213B08F1B02D475` |
| host_contract.mjs | `7112319D4CF96C14FC9799A26F74DD5C16549F0B0558E79A942B1F8892DAD009` |
| SP-01 results.json | `3427DD4F58EBC6120AD60C85D778E69702C468D7AD1D96E81D207E6B1026D893` |
| SP-02 results.json | `F9EA1A16518971A29C6D611EF0A96561A52798C6E547404C88020CF1784F0F3E` |
| Chaque PNG contrôlé | `1B5CF837674D6465C1AA9E080089407FB01DBB897DCC071249B0E3D043BD823E` |

## Architecture consequence / STOP

Architecture inchangée : 8/10 questions résolues, 14 décisions LOCKED, 2 PROVISIONAL. Les 9 risques critical/high ont des mitigations documentées, **pas des validations Android acquises** ; les preuves manquantes de recovery/durabilité ne sont pas closes.

**Blocage exact : la stack sélectionnée n'a pas été exécutée sur Android.** Il faut un environnement SDK/Gradle isolé et une cible émulateur/appareil, puis les deux bancs Kotlin/Room/BundledSQLiteDriver/AtomicFile et PdfDocument/codec candidat. Les modèles hôte ne justifient ni passage à Step 5 ni commit de clôture.

Aucun test/build/audit/package STORE exécuté, aucun profil réel touché, aucune modification source/runtime/config/DB/migrations/package.json de STORE 2.0.1. Seuls fichiers isolés et ce rapport ajoutés. Pas de mise à jour d'architecture car aucune décision PROVISIONAL résolue. **Pas de commit, tag ou push.**
