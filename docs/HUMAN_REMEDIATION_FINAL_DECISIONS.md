# STORE 3.0 — HUMAN REMEDIATION FINAL DECISIONS

Date d'enregistrement : 25 septembre 2026
Version maintenue : `2.0.1`

## Statut

Les deux arbitrages humains restant après la validation du Decision Contract sont résolus. Ils sont autoritatifs pour les phases J correspondantes.

## HD-01 — Format de backup des médias d'articles

Décision : **bundle autonome `.store-backup`**.

Invariant : un seul artefact portable contient tout l'état métier supporté à restaurer, notamment :

```text
backup.store-backup
├── manifest.json
├── store.sqlite
└── media/articles/**
```

Règles obligatoires :

- aucun sidecar externe requis ;
- aucune image BLOB dans SQLite uniquement pour la portabilité ;
- manifeste versionné sans secret, avec version application/format, date, entrées et intégrité ;
- validation de structure, DB, médias, compatibilité et checksums avant acceptation ;
- rejet des chemins absolus, traversal et destinations hors stockage géré ;
- restauration dans un staging avant commit ;
- cohérence des références DB et fichiers médias ;
- backups DB-only historiques explicitement reconnus et acceptés ;
- article sans image restauré avec placeholder ;
- tests backup/restore, média absent/corrompu, legacy et archive hostile obligatoires.

Cette décision appartient à J.2 et n'autorise aucune modification du moteur de backup pendant J.1.

## HD-02 — Changement d'utilisateur avec caisse ouverte

Décision : **le changement d'utilisateur est bloqué tant que l'utilisateur courant possède une caisse ouverte**.

Règles obligatoires :

- aucun transfert de `cash_sessions.employee_id` ;
- message opérationnel expliquant que la caisse doit être clôturée ;
- route claire vers la clôture lorsque sûre ;
- logout soumis à la même protection ;
- panier non vide jamais transféré silencieusement ;
- abandon du panier explicitement confirmé et sans mutation métier ;
- switch interdit durant checkout/commit/paiement/facture/confirmation critique ;
- identité de la vente égale à l'acteur backend authentifié ;
- aucune caisse partagée, remise de caisse, multi-terminal ou abstraction spéculative en STORE 3.0.

Cette décision appartient à J.4 et n'autorise aucune modification de session ou caisse pendant J.1.

## Decision Contract final

```text
Media backup
→ self-contained .store-backup bundle

Open cash + user switching
→ switching/logout blocked until cash closure
```

**DECISION CONTRACT IMPLEMENTABLE**

La prochaine phase autorisée reste J.1, mais son implémentation exige une autorisation séparée après validation de son plan exact.
