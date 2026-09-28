# STORE — Production acceptance checklist

Date : 2026-09-28. RC `RC-63b3849-x64`, source `63b3849ee234248a3b07a643e17dd22fb8c7b23d`, version 2.0.1, migration 18.
Installateur : `artifacts/release-candidate/rc-63b3849/STORE Setup 2.0.1-x64.exe` ; 113587989 octets ; UNSIGNED.
SHA-256 : `BAF72A92F0E76ABED562B9924996AA5B8DDA3FBBAF77EFEADCD2800876A3FBFC`.
Statut final : **STORE 2.0.1 — FUNCTIONALLY QUALIFIED, UNSIGNED**. Installation/startup, migration réelle 13→18, lifecycle uninstall/reinstall et acceptation fonctionnelle globale : PASS attestés par l'utilisateur dans la mission de clôture. Signature volontairement hors périmètre, non bloquante. Ces essais n'ont pas été réexécutés pour la clôture documentaire.

Les lignes détaillées NOT TESTED ci-dessous restent non attestées individuellement : l'acceptation globale ne permet pas d'inventer un résultat pour chaque sous-scénario, matériel ou panne. Ancien binaire → nouvel installateur **in-place : NOT TESTED**, distinct de migration DB et réinstallation même RC. Windows x64 SUPPORTED + FUNCTIONALLY QUALIFIED ; Android hors 2.0.1, prévu séparément pour STORE 3.0.
Statuts permis : PASS / FAIL / NOT TESTED / NOT APPLICABLE (justification obligatoire).
Exécuter sur VM/profil Windows séparé et données synthétiques, jamais sur profil réel pour restore/reset/upgrade destructif. Le profil de test développement n'est pas un override autorisé du binaire packaged.

À renseigner : candidat/chemin/SHA-256, commit source, date, observateur, Windows/architecture, appareil/résolution, imprimante et environnement SMTP de test. Attacher preuve et résultat attendu/observé ; ne jamais joindre de credentials. Tout FAIL sécurité/intégrité bloque la promotion. Données sauvegardées avant scénarios destructifs isolés.

| ID | Contrôle | Statut | Observateur / preuve / notes |
|---|---|---|---|
| ACCEPT-01 | Installation Windows x64 et démarrage packagé | PASS | Attestation utilisateur de clôture |
| ACCEPT-02 | Migration DB historique schéma 13→18, intégrité/FK/données/pré-backup | PASS | Attestation utilisateur ; ce n'est pas un upgrade ancien binaire in-place |
| ACCEPT-03 | Uninstall/reinstall même RC : conservation des données | PASS | Attestation utilisateur ; DB bit-à-bit après uninstall et avant relaunch |
| ACCEPT-04 | Reopen/login application packagée sans corruption | PASS | Attestation utilisateur ; seul ajout audit login/session/SUCCESS, 24→25 |
| ACCEPT-05 | Login : succès, échec, récupération, saisie continue | NOT TESTED | À renseigner |
| ACCEPT-06 | User switching : caisse ouverte, panier, permissions rafraîchies | NOT TESTED | À renseigner |
| ACCEPT-07 | Permissions : Owner/Manager/Employee et retraits | NOT TESTED | À renseigner |
| ACCEPT-08 | POS sale : remise, décimales, quantité zéro, replay équivalent/incompatible | NOT TESTED | À renseigner |
| ACCEPT-09 | Cash opening/closing : montant théorique/compté/écart | NOT TESTED | À renseigner |
| ACCEPT-10 | Invoice : date, snapshots, annulation et actions | NOT TESTED | À renseigner |
| ACCEPT-11 | PDF visual rendering : clair indépendant du thème, pagination | NOT TESTED | À renseigner |
| ACCEPT-12 | Printing : imprimante réelle, échec et réimpression | NOT TESTED | À renseigner |
| ACCEPT-13 | Products : création, images, archive, import PDF | NOT TESTED | À renseigner |
| ACCEPT-14 | Stock : ajustement motivé, filtres et mouvements | NOT TESTED | À renseigner |
| ACCEPT-15 | Inventory : comptage/validation unique, limite reprise | NOT TESTED | À renseigner |
| ACCEPT-16 | Purchase : draft/réception/annulation et création imbriquée | NOT TESTED | À renseigner |
| ACCEPT-17 | Presence : signature propre password, correction, restart | NOT TESTED | À renseigner |
| ACCEPT-18 | Dashboard : KPI, présents, raccourci, scroll indépendant | NOT TESTED | À renseigner |
| ACCEPT-19 | Reports/charts : filtres, séries, tableaux dépliables | NOT TESTED | À renseigner |
| ACCEPT-20 | Messages : chat local, Employee sans gestion | NOT TESTED | À renseigner |
| ACCEPT-21 | Settings : validation, devise, permissions effectives | NOT TESTED | À renseigner |
| ACCEPT-22 | SMTP test : compte de test, panne et retry | NOT TESTED | À renseigner |
| ACCEPT-23 | Backup : création, export, intégrité et médias | NOT TESTED | À renseigner |
| ACCEPT-24 | Restore on isolated copy : validation, rollback/interruption | NOT TESTED | À renseigner |
| ACCEPT-25 | Reset on isolated copy : password, backup, recovery | NOT TESTED | À renseigner |
| ACCEPT-26 | Light theme : contrastes, fenêtres et tableaux | NOT TESTED | À renseigner |
| ACCEPT-27 | Dark theme : contrastes et PDF toujours clair | NOT TESTED | À renseigner |
| ACCEPT-28 | Tablet landscape : débordements/clavier virtuel | NOT TESTED | À renseigner |
| ACCEPT-29 | Tablet portrait : scroll et fenêtres | NOT TESTED | À renseigner |
| ACCEPT-30 | Keyboard navigation : Tab, focus, Escape, password | NOT TESTED | À renseigner |
| ACCEPT-31 | Touch : cibles, défilement zone/panier | NOT TESTED | À renseigner |

Décision finale : **acceptation fonctionnelle humaine globale PASS, attestée par l'utilisateur**. Les résultats détaillés non fournis restent NOT TESTED. Aucun statut de certification externe ; release FUNCTIONALLY QUALIFIED, UNSIGNED. READY TO TAG v2.0.1, sans tag ni push.
