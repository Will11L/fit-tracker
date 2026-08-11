# Figma — Page `4 · Organisms` (plan de création)

> Phase de **création** (≠ vérification atoms/molecules qui est terminée, cf. [FIGMA_VERIFICATION.md](FIGMA_VERIFICATION.md)).
>
> - **Fichier Figma** : `ca2qkjOKCy5N5uEbIKyqrO` — page `4 · Organisms`
> - **Organism** = section composée (faite de molecules + atoms), bloc fonctionnel récurrent d'un écran.
> - **Objectif** : définir les versions **canoniques**. L'app a beaucoup de quasi-doublons
>   (≈7 « Header », ≈12 bottom sheets, ≈10 dialogs, 5 barres de progression) → un canon par type,
>   aligné sur la politique 9 du projet (uniformité des modules).
> - **Statut** : `[ ]` à créer · `[~]` créé, à valider · `[x]` validé
> - **Méthode par item** : inspecter les composants app concernés → extraire le spec canonique →
>   créer la carte sur la page Figma `4 · Organisms` (même format de carte que les pages Atoms/Molecules).

---

## Navigation & chrome

### [~] O1 · BottomNavBar
Barre de navigation 5 onglets. Source : `bottomNavigationBar/BottomNavBar.kt`.
- Créé (carte `Card O1 · BottomNavBar`) — 5 onglets fond `bg/bottom-nav` ; item sélectionné fond
  `selected/fill` arrondi + icône agrandie `text/on-selected`, autres `text/tertiary`.

### [~] O2 · NavigationDrawer
Drawer complet : `DrawerSection` + `DrawerItem` + `DrawerMiniProgress` + `DrawerIconCountIndicator`.
Source : `drawer/DrawerContent.kt`.
- Créé (carte `Card O2 · NavigationDrawer`) — drawer 300dp `bg/screen` : 2 DrawerSection (titre
  uppercase `accentText` + items icône 22 + label 14 Medium, dividers) + footer (last sync + icônes état).

### [~] O3a · TitleBar
Barre de titre simple : Box pleine largeur 44dp, `bg/surface`, titre centré 16sp SemiBold `text/primary`.
Canonise : `RoutineHeader`, `PlannedWorkoutHeader`.
- Créé sur la page Figma `4 · Organisms` (carte `Card O3a · TitleBar`) — composant 340×44.

### [~] O3b · ListSearchHeader
En-tête d'écran liste : `StyledSearchField` (weight 1f) + sync + tri (dropdown) + more, puis ligne
« N résultats · tri » 12sp `text/tertiary`. Canonise : `ExerciseListHeader`, `MuscleListHeader`.
- Créé sur la page Figma `4 · Organisms` (carte `Card O3b · ListSearchHeader`) — composant 340 large.
- Icône sync = instance de `ic_cloud_done` (set d'icônes). Icônes `sort` / `more_vert` dessinées.

> Note O3 : les 6 « headers » de l'app = 4 types. Seuls les 2 vrais doublons sont catalogués
> (O3a, O3b). `ExerciseScreenHeader` (rangée d'icônes d'action) et `GoalsHeader` (nav semaine
> flèches + pill) sont des headers **one-off spécifiques** — non catalogués comme organisms.

---

## Sections de contenu

### [~] O4 · SummaryStatsRow
Rangée de N × M4 `CalendarSummaryItem`.
Canonise : `CalendarSummary`, `sessionTab/SessionSummaryRow`, `PlannedWorkoutSummaryRow`,
`routineTasksScreen/RoutineSummaryInline`.
- Créé (carte `Card O4 · SummaryStatsRow`) — 3 cellules `weight 1f` gap 8 : icône tintée (instance
  set d'icônes recolorée) + value 13sp SemiBold + label 12sp tertiary.

### [~] O5 · SectionProgressHeader
`TitledDivider` + barre de progression de section.
Canonise : `MonthViewProgressBar`, `PlannedWorkoutProgressBar`, `RoutineTasksProgressBar`,
`GoalsProgressBar`.
- Créé (carte `Card O5 · SectionProgressHeader`) — TitledDivider + LabeledProgressBar (track 7dp
  `bg/recessed` + remplissage `primary/action` + % + bouton info).

### [~] O6 · CalendarMonthGrid
En-tête jours de semaine + grille de `CalendarDay` + navigation mois.
- Créé (carte `Card O6 · CalendarMonthGrid`) — en-tête L M M J V S D + 5×7 `CalendarDay` (icône de
  statut + numéro 16sp) ; jour courant bordé `primary/action`.

### [~] O7 · ExerciseBlock
**Représentation du layout `SessionExerciseScreen.kt` — pas un composable réutilisable.**
Le pattern "header exercice + N SetRow groupés" est inliné dans le screen, pas extrait.
- `ScreenTitleBar` (nom exo + bouton `+`) + `SetTableHeader` (labels colonnes) +
  `LazyColumn` de M5 [`SetRow`](../appli-android/app/src/main/java/com/example/sportapp/ui/components/common_components/SetRow.kt) (35dp : N° · reps · poids · tendance · statut · supprimer · notes).
- Cf. [SessionExerciseScreen.kt:144-167](../appli-android/app/src/main/java/com/example/sportapp/ui/screens/SessionExerciseScreen.kt#L144-L167).
- 🔄 Description Figma actualisée 2026-05-23 — à extraire en composable `ExerciseBlock(exercise, sets, onAddSet)` seulement si on veut le réutiliser ailleurs (ex. preview historique séance, page récap programme).
- 🔄 **Rows = vraies instances M5 SetRow** (2026-05-23) : les 3 SetRow hand-built dans le viewport ont été remplacés par des instances réelles de `M5 · SetRow` (`613:54`) — 2× `Status=Done` (sets 1+2 avec 10/60 et 9/60) + 1× `IsDropset=Yes` (set 3 avec arrow ↳ + 8/50). Tout changement futur du canonique M5 se propage automatiquement à O7.

### [~] O8 · StatsChartCard
Chart Vico (`LineChartScreen`) + `StatsRangePicker` + `MetricToggle` / `SortToggle`.
- Créé (carte `Card O8 · StatsChartCard`) — chips de période + graphe (barres `primary/action`) +
  chips de métrique.

### [~] O9 · EntityListCard
Carte d'entité affichée en liste : `ExerciseCard`, `MuscleCard`, `GoalRow`.
- Arbitrage : catalogué comme organism (carte composée déplaible = en-tête + DetailRows + actions).
- Créé (carte `Card O9 · EntityListCard`) — état déplié : en-tête (icône + nom centré + favori +
  chevron) + 4 DetailRows + rangée de 5 ActionIconButton.

---

## Overlays

### [~] O10 · OptionsBottomSheet
Sheet : titre + liste de M3 `OptionRow`. Canonise les ~12 `*BottomSheet` de l'app.
- Créé sur la page Figma `4 · Organisms` (carte `Card O10 · OptionsBottomSheet`) — poignée +
  TitledDivider + 3 OptionRow (Default / Primary / Danger), container `bg/screen`, coins haut 24.
  Icônes = instances du set d'icônes recolorées `text/primary`.

### [~] O11 · ConfirmationDialog
Dialog : titre + message + 2 boutons. Source : `common_components/ConfirmationDialog.kt`.
- Créé (carte `Card O11 · ConfirmationDialog`) — AlertDialog M3 : titre `primary/action` + message
  `text/primary` + 2 TextButton à droite (Cancel `text/tertiary`, action `redMedium`/`snackbar/error`),
  container `bg/screen`, coins 28.

### [~] O12 · FormDialog
Dialog avec champs de saisie : `Edit*Dialog` / `Add*Dialog`.
- Arbitrage : catalogué — même squelette que O11 mais corps = formulaire (champs) au lieu du message.
- Créé (carte `Card O12 · FormDialog`) — titre + champs (SingleSelectDropdown + CustomTextField) +
  TextButton Cancel / Add, container `bg/screen` coins 28.

### [~] O13 · EmptyState
Icône + message d'état vide : `EmptyGoalsWeekCard`, `NoSessionFallbackScreen`, « Pas d'échauffement ».
- Créé (carte `Card O13 · EmptyState`) — carte `bg/screen` cr12 : titre 16sp SemiBold + texte 13sp
  tertiary + `ActionIconWithTextButton` pleine largeur.

### [~] O14 · SnackbarHost
Snackbars custom : success / warning / error.
- Créé (carte `Card O14 · SnackbarHost`) — barre `bg/recessed` cr12 + bordure 1.5dp accent (couleur
  par type) + icône + message + action Close. 4 types : Success / Warning / Error / Info.

---

## Ordre de création recommandé

Priorité aux organisms à plus fort taux de duplication dans l'app (un canon → le plus de callsites alignables) :

1. **O3a · TitleBar** + **O3b · ListSearchHeader** — 2 canons headers (2 doublons chacun)
2. **O10 · OptionsBottomSheet** — ≈12 bottom sheets
3. **O11 · ConfirmationDialog** — ≈10 dialogs
4. **O4 · SummaryStatsRow** — ≈4 rangées de résumé

Puis le reste selon décision.

## Avancement

- **15 / 15 créés** 🎉 (`[~]` à valider) — O1, O2, O3a, O3b, O4, O5, O6, O7, O8, O9, O10, O11,
  O12, O13, O14. Page Figma `4 · Organisms` complète.

---

## Sync post-refactor UI components — 2026-05-23

> Le refactor `docs/REFACTOR_UI_COMPONENTS.md` (43/43 livré, 2026-05-22 → 2026-05-23)
> a renommé des composants côté code. Sync Figma exécutée via MCP (`use_figma`) — toutes
> les modifs ci-dessous sont appliquées directement sur le fichier `ca2qkjOKCy5N5uEbIKyqrO`.

### Renommages (alignement nom carte ↔ nom canonique code)

- 🔄 **O3a · TitleBar → O3a · ScreenTitleBar** : R9 a posé le canonique
  `ScreenTitleBar.kt` qui absorbe 10 ex-doublons (6 headers : `ChronoScreenHeader`,
  `SessionHeader`, `RoutineHeader`, `NotificationsHeader`, `ExerciseHeader`,
  `PlannedWorkoutHeader` + 4 *Title : `ExerciseListTitle`, `MuscleListTitle`,
  `ExerciseTitle`, `MuscleTitle`). Carte + ComponentSet + "all states" frame renommés
  + description annotée R9.
- 🔄 **O4 · SummaryStatsRow → O4 · SummaryRow** : R10 a posé le canonique
  `SummaryRow(items: List<SummaryItemData>, compact)`. Carte + ComponentSet renommés
  + description actualisée (`CalendarSummary` → `CalendarSummaryRow` N16,
  `RoutineSummaryInline` supprimé en B2 = code mort).

### Descriptions actualisées (renommages référencés)

- 🔄 **O8 · StatsChartCard** : description référence maintenant `MultiLineChart.kt`
  (N1 renommé depuis `LineChartScreen.kt`) + `StatsRangeComponents.kt` (N3 file
  rename) + `SegmentedIconToggle<T>` (R11 canonique générique).
- 🔄 **O9 · EntityListCard** : description référence R16 (slot-based, ExerciseCard +
  MuscleCard wrappers, look inchangé).
- 🔄 **O13 · EmptyState** : description référence `EmptyGoalsWeekState` (N8 renommé
  depuis `EmptyGoalsWeekCard`) + `NoSessionFallback` (N7 renommé depuis
  `NoSessionFallbackScreen`).

### Notes variantes (canoniques voisins, sans cartes distinctes)

Plutôt que créer des cartes pour chaque variante mineure, des notes inline sont
ajoutées dans la description de la carte parente :

- 🟡 **O10 · OptionsBottomSheet** : note variante R3
  `ExercisePickerBottomSheet(title)` (canonique add-exercise avec param title
  externalisé, 2 callsites).
- 🟡 **O11 · ConfirmationDialog** : notes R14 (4 confirmations inline absorbées) +
  R4 `PhasePickerDialog(onPhaseSelected)` (variante liste de phases, 2 callsites).
- 🟡 **O12 · FormDialog** : notes R12 (canonique créé, 8 dialogs migrés) +
  variantes R13 `StatusPickerDialog(items)` + R15 `RoutinePeriodFormDialog(period?)`
  + `RoutineTaskFormDialog(task?)` (pattern Add/Edit fusionné).

### Bilan post-sync

- **15 / 15 cartes** organisms en place + descriptions à jour post-refactor (43/43).
- **Tous les renommages côté code** sont reflétés en Figma (noms + descriptions).
- Reste : remplacer le statut `[~]` (créé à valider) par `[x]` (validé) carte par
  carte après revue visuelle des proportions sur device. Aucune création organism
  supplémentaire prévue post-refactor (le canonique R17 `EntityListRow` est
  catalogué en molecule M9 dans [`FIGMA_VERIFICATION.md`](FIGMA_VERIFICATION.md)).

### Fix O3b ListSearchHeader (revue visuelle 2026-05-23)

- 🔄 **DemoBox bg** : changé de `bg/recessed` (thirdBlue) → `bg/screen` (blueBackground)
  pour le frame `754:39 "O3b · ListSearchHeader — viewport"`. Avant : le search
  field thirdBlue se confondait avec son fond (invisible). Après : contraste correct,
  match exactement le rendu app (`ListSearchHeader` est une `Column` sans bg, posée
  sur l'écran qui a `appColors.bgScreen`).
- 🔄 **Placeholder "Search…"** : opacity du fill text passée de `1.0` → `0.6` (node
  `754:6`) pour matcher `StyledSearchField.kt:30` qui utilise
  `appColors.textPrimary.copy(alpha = 0.6f)` (placeholder gris/blanc dilué, pas
  blanc plein). Variable binding `text/primary` conservée, seule l'opacity change.
- 🔄 **Padding viewport** (`754:39`) : passé de `16h/0v` → `24h/24v` pour donner
  des marges visibles tout autour du composant et le faire respirer.

### Fix O5 SectionProgressHeader (revue visuelle 2026-05-23)

- 🔄 **Padding viewport** (`768:33`) : passé de `0/0/0/0` → `18/18/18/18` pour
  match le padding canonique des écrans app (`padding(horizontal = 18.dp)` —
  pattern utilisé dans 14 fichiers `ui/screens/*.kt` : CalendarViewScreen,
  GoalsTabContent, PlannedWorkoutScreen, RoutineTasksScreen, SessionTab, etc.).
  Le composant SectionProgressHeader se présente maintenant dans un viewport
  qui reflète l'environnement de page réelle.

### Fix O2 NavigationDrawer (revue visuelle 2026-05-23)

- 🔄 **Dividers : 3 styles (Figma plus fin que code, hiérarchie préservée par opacity)** :
  - **Top section** (titre → 1er item, 2×) : `dividerStrong α0.60` thickness **1.5dp** padding h=20dp width 260 (code = 2.5dp).
  - **Inter-item** (entre items d'une section, 7×) : `dividerStrong α0.30` thickness **1dp** padding h=18dp width 264 (code = 2dp).
  - **Inter-section** (entre 2 DrawerSection, 2×) : `dividerStrong` full opacity thickness **1dp** **full width 300** (code = 1.5dp).
  Couleurs et opacities matchent exactement le code ([DrawerSection.kt:55-60](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerSection.kt#L55-L60), [96-100](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerSection.kt#L96-L100), [DrawerContent.kt:252](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerContent.kt#L252)) ; les thicknesses sont divisées par ~1.5 pour matcher le rendu device perçu user (à 3x density Android, 2.5dp = 7.5px qui visuellement = ~1.5dp Figma 1:1).
- 🔄 **Items câblés sur M11 + variants spécifiques** : tous les `DrawerItem` sont des instances réelles de [M11 · DrawerItem](FIGMA_VERIFICATION.md). Override per-instance : `swapComponent` sur l'icône inner + `characters` sur le label. **Variants utilisés** : Notifications=`WithIconCount` (mail+3) / Tasks=`WithStatsBadge` (2/5 orange pill) / Bench Day=`WithProgress` (mini bar 60%) / tous les autres = `Default`.
- 🔄 **Drawer complet rebuilt from M12 spec 2026-05-23** : O2 a été reconstruit from scratch à partir de la spec [M12 · DrawerSection](FIGMA_VERIFICATION.md) (auto-layout VERTICAL, padding t=12 b=8, bg `bg/recessed`, title 13sp Bold accentText, topDiv 1.5dp α0.60 padding 20, inter-div 0.5dp α0.30 padding 18). Plus aucune divergence vs M12. Sections :
  - **ACTIVITY** (9 items) : Notifications · Conversations · Tasks · Bench Day · Program · Calendar · Exercises · Muscles · Statistics
  - **ACCOUNT & SETTINGS** (5 items) : Profile · Settings · Export data · Log out · Sync settings
  - **ADMIN** (2 items, conditionnelle `isAdmin`) : Manage users · UI Showcase
  - **Footer** : last sync text + 3 status icons (network / cloud sync / WebSocket)
  Inter-section dividers : `dividerStrong` 1.5dp full width 300, raw color blueMedium #245682 (plus navy que firstBlue). Viewport O2 = 914dp.

### Fix O8 StatsChartCard (revue visuelle 2026-05-23)

- 🔄 **Chips row** (`881:84`) : labels passés de "1w/30d/3m/6m/1y/All/Custom"
  → "1 week/30 days/3 months/6 months/1 year/All/Custom" pour matcher exactement
  `strings.xml` (`stats_range_1_week`, etc., cf. [StatsRangeComponents.kt:62-68](../appli-android/app/src/main/java/com/example/sportapp/ui/components/stats/StatsRangeComponents.kt#L62-L68)).
  Style FilterChip M3 24dp pill cr12 : selected = bg `primary/action` + label
  `text/primary` ; unselected = border 1px `text/secondary` α0.6 + label
  `text/secondary`. Auto-layout `clipsContent=true` → la chip "Custom" sort
  partiellement du viewport pour suggérer le `horizontalScroll` du code.
- 🔄 **Toggles row** (`881:99`) : remplacement texte "Bar/Line" + "Sets/Exo/Vol"
  par les vraies instances **icônes** de M10 SegmentedIconToggle (clone des
  frames `841:13` 2-seg + `841:22` 3-seg). ChartTypeToggle = `ic_equalizer`
  (Bar, selected) + `ic_query_stats` (Line) — matche
  [ChartTypeToggle.kt:27-30](../appli-android/app/src/main/java/com/example/sportapp/ui/components/stats/ChartTypeToggle.kt#L27-L30).
  MetricToggle = `ic_rounded_repeat` (Sets, selected) +
  `ic_rounded_format_list_numbered` (Exercises) + `ic_exercise` (Volume) —
  matche [MetricToggle.kt:27-31](../appli-android/app/src/main/java/com/example/sportapp/ui/components/stats/MetricToggle.kt#L27-L31).
  Layout `SPACE_BETWEEN` (toggles aux 2 extrémités, cf. user feedback
  2026-05-07 "ChartTypeToggle à gauche, MetricToggle à droite").
- 🔄 **Chart** (`881:112`) : remplacement de la version line chart placeholder par un **bar chart** inspiré
  de la legacy `E9 GoalsAchievementChart` (`422:2` dans page `Design System (legacy)`).
  Bg `bg/recessed` cr8, padding (4l/12r/28t/12b). Axe Y 24dp à gauche : labels
  "0% / 25% / 50% / 75% / 100%" right-aligned en `text/secondary` 8sp + ligne
  verticale solide α0.55. 5 horizontal **dashed guidelines** (pattern 4-3, α0.45)
  alignées sur chaque tick %. **6 bars** (zones Chest/Back/Shoulders/Arms/Legs/Core),
  largeur 24px (ratio bar/slot = 24/44 ≈ 0.555 du code `BarChartBox.kt` constante
  `BAR_RATIO`), espacement 7px (ratio 0.167 = `SPACING_RATIO`), coins `topRadius=5`
  uniquement. Couleurs reliées aux variables : Chest=`primary/action`,
  Back=`snackbar/warning`, Shoulders=`text/accent`, Arms=`priority/high`,
  Legs=`priority/low`, Core=`priority/medium` — palette `StatsScreen.groupColors`
  ([StatsScreen.kt:105-115](../appli-android/app/src/main/java/com/example/sportapp/ui/screens/StatsScreen.kt#L105-L115))
  reproduite via variables. X labels 9sp Medium **colorés à la même variable que
  leur bar** (pattern `BarChartBox.kt:341-353`).

> ✅ **Set d'icônes créé** (2026-05-21) : page Figma dédiée **`Icons`** — 119 icônes Material Symbols
> importées depuis les vector drawables de l'app (`res/drawable/ic_*.xml`), converties en composants
> Figma (vector-drawable → SVG → `createNodeFromSvg`), chaque icône = composant nommé (nom exact du
> drawable, mapping 1:1 avec `R.drawable.*`). Regroupées en **6 catégories** sur cartes `bg/recessed`
> (thirdBlue), icônes en `primary/action` : Navigation & flèches (21), États & feedback (19),
> Sync & connectivité (21), Actions & édition (19), Média & temps (9), Contenu & domaine (30).
> Les organisms réutilisent ces icônes. Reste possible : recolorer O3b (sync) avec le vrai `ic_cloud_done`.
