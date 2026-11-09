# Espaces STORE conservés

La source Android livrée reste référencée dans [`../CURRENT_ANDROID.md`](../CURRENT_ANDROID.md).
Les dossiers de ce répertoire regroupent les espaces de travail et les archives conservés
après la livraison.

## Classement

- `git-worktrees/` : worktrees Git secondaires, déplacés avec `git worktree move`.
- `archives/` : anciens espaces, points de contrôle et données de tests.
- `tooling/` : espaces de construction et d’activation PDFium liés au projet.
- `transfers/` : dossier de transfert conservé tel quel.
- `external/` : éléments STORE déplacés depuis le dossier personnel et Téléchargements,
  rangés par emplacement d’origine.

Dans `external/OneDrive/`, les sources et livrables historiques de `STORE` et `STORE_Test`,
ainsi que les deux rapports PDF, ont été regroupés localement. Le transfert sélectif a exclu
les dépendances, les caches Gradle, les répertoires `build`/`dist` et les fichiers
`tsbuildinfo`, car ils sont régénérables. La première tentative avait déjà rapatrié quelques
fichiers de ces catégories ; ils ont été conservés. Des caches et dossiers régénérables
peuvent donc rester dans OneDrive.

Les worktrees secondaires contiennent encore des modifications non validées. Elles ont été
préservées pendant le déplacement. Pour vérifier leurs branches et leurs chemins, utiliser
`git worktree list` depuis le dépôt principal.
