# STORE 3.0 — Design System

## Principes

Le Design System de STORE fournit des fondations professionnelles, tactiles et accessibles
pour la migration progressive de STORE 2.0.1 vers STORE 3.0. Il ne contient aucune règle
métier et ne communique jamais directement avec Electron, SQLite ou le preload.

Ordre de priorité : intégrité, sécurité, fiabilité, accessibilité, usage tactile,
cohérence visuelle, esthétique.

Les classes du nouveau système portent le préfixe `ds-`. Les anciennes pages continuent
d'utiliser leurs styles historiques jusqu'à leur migration explicite.

## Tokens

`frontend/src/design-system/tokens.css` est la source des valeurs sémantiques :

- couleurs : `--color-background`, `surface`, `text`, `border`, `primary`, `success`,
  `warning`, `danger`, `info`, `disabled`, `focus` et `overlay` ;
- typographie : familles, tailles et hauteurs de ligne ;
- espacements : 4, 8, 12, 16, 20, 24, 32 et 40 px ;
- rayons : small, medium, large et full ;
- élévations : trois niveaux ;
- mouvements : rapide et standard ;
- contrôles : small, medium et large ;
- z-index : header, overlay et toast.

Les aliases `--bg`, `--panel`, `--surface`, `--text`, `--muted`, `--line`, `--primary`
et `--danger` constituent un pont temporaire pour les écrans STORE 2.x. Ils ne doivent
pas être utilisés dans un nouveau composant.

## Couleurs

La couleur exprime une signification stable :

| Token | Signification |
| --- | --- |
| Primary | Action principale |
| Success | Réussite ou état positif |
| Warning | Attention récupérable |
| Danger | Erreur, rupture ou destruction |
| Info | Information neutre |

Un statut comporte toujours un texte ou une icône avec nom accessible. Une couleur seule
ne suffit jamais. Le thème sombre remplace les valeurs des mêmes tokens : il ne crée pas
un second composant.

## Typographie

La pile locale est Segoe UI, Inter si déjà disponible, puis la police système. Aucun
téléchargement runtime n'est utilisé.

Hiérarchie : page title, section title, card title, KPI value, body, secondary et caption.
Les valeurs monétaires, quantités et KPI utilisent des chiffres tabulaires lorsqu'ils sont
affichés par les classes prévues.

## Espacement et densité

Trois densités sont prévues :

- `comfortable` : POS, formulaires et dialogues tactiles ;
- défaut : navigation, listes et opérations ;
- `compact` : tableaux administratifs desktop.

Définir la densité sur un conteneur avec `data-density`. Sur un pointeur tactile, la
densité compacte conserve des contrôles d'au moins 44 px.

## Interaction tactile

- cible minimale : 44×44 px ;
- actions principales : contrôle large, idéalement 48–56 px ;
- aucun comportement essentiel ne dépend de `hover` ;
- `active`, `focus-visible`, `disabled` et `loading` ont des états distincts ;
- éviter les groupes de petites icônes ; préférer une action explicite ou un drawer.

## Icônes

STORE utilise `lucide-react`, embarqué localement. Les imports doivent être nommés :

```tsx
import { Search } from 'lucide-react';
```

Ne jamais importer la bibliothèque complète. Une icône décorative utilise
`aria-hidden="true"`. Un bouton constitué uniquement d'une icône utilise `IconButton`
avec un `label` obligatoire et un tooltip natif lorsque le contexte le demande.

## Composants

### Contrôles

- `Button` : primary, secondary, ghost, danger ; sm, md, lg ; loading et disabled.
- `IconButton` : cible 44 px et nom accessible obligatoire.
- `Field` : label visible, required, aide et erreur associées.
- `TextInput` : text, search, email et password.
- `NumberInput` : clavier numérique, sans logique métier.
- `Select` : contrôle natif accessible, placeholder facultatif.
- `Checkbox` : label cliquable et état indéterminé.

```tsx
<TextInput
  id="store-name"
  label={t('storeName')}
  error={error}
  required
/>

<Button loading={saving} loadingLabel={t('saving')}>
  {t('save')}
</Button>
```

### Feedback

- `Badge` : neutral, info, success, warning, danger.
- `Alert` : message persistant avec texte et icône.
- `Toast` et `ToastRegion` : annonces polies ou assertives selon la gravité.
- `EmptyState`, `ErrorState` et `Skeleton` : chargement et états explicites.

Les erreurs critiques nécessitant une décision ne doivent pas disparaître dans un toast.

### Overlays

- `Modal` : titre accessible, focus initial, piège de focus, Escape et restitution.
- `ConfirmDialog` : conséquence et confirmation explicites.
- `Drawer` : right, left ou bottom ; fondamental pour les futures interfaces tablette.

Un overlay bloque le scroll du document pendant son ouverture. `dismissible=false` est
réservé aux opérations qui ne peuvent pas être interrompues sans risque.

### Données

- `Tabs` : tablist/tab/tabpanel et navigation Arrow/Home/End.
- `DataList` : alternative tactile aux tableaux.
- `ResponsiveTable` : région nommée à défilement contrôlé ; l'écran reste responsable
  du choix entre table, colonnes réduites et DataList.
- `Card` : surface simple statique ou interactive.
- `KpiCard` : reçoit une valeur déjà calculée ; aucun calcul métier interne.

### Structure

- `AppShell`, `ShellHeader` et `ShellSidebar` fournissent uniquement les zones
  structurelles. La navigation métier et le RBAC appartiennent à la phase C.

## Accessibilité

Les composants suivent les règles suivantes :

- labels visibles et relations `aria-describedby` ;
- focus visible ;
- ordre clavier naturel ;
- focus trap et restauration pour les overlays ;
- `aria-live` pour les notifications ;
- statut jamais exprimé uniquement par couleur ;
- zones tactiles minimales ;
- contraste renforcé préservé ;
- réduction des animations respectée.

## Motion

Les transitions sont fonctionnelles, de 120 à 180 ms. Avec
`prefers-reduced-motion: reduce`, les transitions deviennent quasi instantanées et le
skeleton cesse de s'animer.

## Responsive

Les primitives doivent être vérifiées à 1920×1080, 1366×768, 1280×800, 1024×768 et
800×1280. Le breakpoint structurel initial de l'AppShell correspond au passage d'une
sidebar latérale à une zone supérieure. Les phases suivantes pourront définir des ruptures
propres au POS ou aux pages, fondées sur leur contenu réel.

## Migration legacy

1. ne supprimer aucune règle encore utilisée ;
2. utiliser les aliases temporaires pour préserver l'ancien rendu ;
3. migrer les composants partagés ;
4. migrer un écran à la fois ;
5. supprimer les règles dépréciées seulement après le dernier consommateur ;
6. retirer le pont legacy lors du nettoyage final.

Ordre de chargement actuel : styles legacy spécialisés, puis tokens et fondations. Les
classes `ds-` ont ainsi un contrat stable sans exiger la réécriture immédiate des pages.

## Anti-patterns

Sont interdits :

- couleurs métier brutes dans les nouveaux composants ;
- emojis utilisés comme contrôles fonctionnels ;
- groupes d'icônes minuscules ;
- cartes décoratives imbriquées ;
- formulaires basés uniquement sur des placeholders ;
- actions disponibles uniquement au survol ;
- logique métier dans les primitives ;
- accès direct à Electron, Node ou SQLite ;
- texte UI métier non internationalisé ;
- override CSS global massif ;
- calcul de permission dans un composant visuel.

## Limite de phase B

Ce Design System ne remplace encore ni le shell métier, ni le POS, ni le Dashboard. Les
écrans historiques restent actifs jusqu'à leur migration dans les phases C à H.
# Phase C — Application shell

Le shell STORE 3.0 est construit avec `AppShell`, `ShellHeader`, `ShellSidebar`,
`Drawer`, `IconButton` et `Badge`. Ses styles restent isolés dans
`frontend/src/design-system/shell.css` sous les préfixes `store-shell-*` et
`store-nav-*`. À 1088 px la sidebar devient automatiquement compacte ; à 800 px
elle est remplacée par un drawer avec piégeage/restauration du focus.

La navigation utilise un adaptateur frontend conservateur. Le renderer ne reçoit
pas encore les permissions effectives de la session backend : aucune permission
n'est donc simulée et seules les destinations déjà sûres dans les parcours 2.0.1
sont affichées. Cette navigation reste une aide d'interface, jamais une autorité
d'autorisation.

## Phase D — POS tablet-first

Le POS utilise les primitives `Button`, `IconButton`, `TextInput`, `NumberInput`,
`Select`, `Alert`, `Modal` et `Drawer`. Ses styles sont isolés dans
`design-system/pos.css`, sans couleur métier arbitraire. En paysage, le panier
reste visible ; sous 900 px, un résumé sticky ouvre un drawer inférieur.
`ModalBackdrop` accepte désormais génériquement `data-autofocus` pour placer le
focus sur le contrôle ou titre pertinent.

## Phase E — Daily Operations

Les écrans Produits, Stock, Inventaires, Achats, Fournisseurs, Équipe et Présence
partagent la frontière `operations.css` (`ops-*`). Elle définit listes compactes,
filtres, formulaires groupés, statuts textuels, détails et variantes portrait sans
introduire de logique métier. Les actions continuent d'appeler exclusivement les
services frontend branchés sur les IPC existants.
