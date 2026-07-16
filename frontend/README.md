# Frontend

Application React sans accès direct à Node.js. Les pages consomment uniquement les services de `src/services`, lesquels délèguent au pont IPC exposé par le preload.

Chaque fonctionnalité est organisée en composants, hooks, services et types indépendants.
