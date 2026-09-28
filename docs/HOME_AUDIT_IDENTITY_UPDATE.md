# Accueil, audit et identité — 2026-09-27

## Accueil

La carte des personnes présentes comporte un bouton centré « Fiche de présence du jour ». Il ouvre la fiche générale, sans filtre employé, y compris lorsque la liste est vide ou en chargement. Le clic sur une personne conserve son raccourci individuel. L'accès reste conditionné à PRESENCE:READ. Le mot de passe personnel reste nécessaire pour signer une entrée/sortie.

« Contexte du mois » est groupé directement sous cette carte avec un espacement commun. Les listes gardent leur défilement indépendant. Le jour utilisé par la fiche et son historique est le jour local du poste, avec horodatages stockés en UTC.

## Audit

Migration additive 17 `audit-session-cash-snapshot` : cinq colonnes nullables, sans réécriture des anciens événements.

- Responsable : identifiant et libellé du compte réellement connecté, transmis par le contexte backend de la requête (AsyncLocalStorage), jamais par un champ libre du renderer.
- Acteur : l'identifiant historique existant reste distinct et inchangé.
- Caisse : référence de la session du responsable, montant théorique après l'action et devise figée. À la fermeture, le montant théorique conservé est utilisé, pas le montant compté physiquement.
- Sans caisse : « Aucune caisse associée ». Anciennes traces / opération sans session authentifiée : « Non enregistré », jamais zéro inventé.
- Les informations restent consultables uniquement avec ADMINISTRATION:READ. Les détails libres et secrets ne sont pas exposés.

Les instantanés sont écrits avec chaque événement existant par le writer commun, y compris les modifications de permissions. Cette évolution ne prétend pas ajouter une trace à chaque opération de l'application qui n'en produisait pas auparavant.

## Authentification

Connexion, changement d'utilisateur et récupération reconnaissent l'identifiant, l'e-mail et le nom complet (prénom nom ou nom prénom), sans distinction de majuscules/minuscules. Unicode NFC et espaces normalisés ; accents conservés. Les mots de passe restent strictement sensibles à la casse.

Si plusieurs anciens comptes correspondent à une identité normalisée, la demande est refusée plutôt que d'en choisir un arbitrairement. Les contrôles de rôle, statut et verrouillage restent actifs. La prévention des doublons de noms/identifiants utilise la même normalisation.

## Validation

Tests d'intégration sur profil jetable : connexion/récupération/changement d'utilisateur, accents, mot de passe sensible à la casse, contexte responsable fourni par IPC, caisse ouverte/fermée, devise historique figée, audit ancien sans valeurs reconstruites. Test de rendu du raccourci pendant le chargement. Vérifications SQL integrity_check/FK et migration idempotente.

Aucune base personnelle modifiée pendant les essais. Vérification visuelle humaine encore nécessaire pour l'accueil responsive et les nouvelles colonnes d'audit.

Résultat automatisé final : **51 fichiers, 257 tests réussis, 0 échec, 0 ignoré** (`artifacts/presence-audit-tests.json`). Lint, compilation TypeScript/Vite et `git diff --check` réussis. Version technique conservée à 2.0.1 ; l'installateur reste un candidat de test non signé et n'est pas installé automatiquement.
