# Refactor UI Components — Plan de consolidation & nommage

> Audit du 2026-05-22 (4 agents parallèles sur ~110 composables Compose de `appli-android/`).
>
> **Objectif** : éliminer les composants quasi-dupliqués (politique projet 9 « uniformité des
> modules ») et corriger le nommage incohérent — pour que le code rattrape le design system
> Figma (page `4 · Organisms`). Une fois consolidés, plusieurs organisms deviennent des vrais
> composants 1:1 → les cartes Figma peuvent porter le nom réel de l'app.
>
> **Point de retour** : ce refactor démarre juste après le commit qui ajoute ce fichier.
> Pour tout annuler : `git revert` des commits de refactor, ou `git reset --hard <commit de ce doc>`.
> La baseline est notée dans l'historique de session `CLAUDE.md`.
>
> **Légende** : `[ ]` à faire · `[~]` en cours · `[x]` fait
>
> **Méthode** : 1 refactor = 1 commit. `compileDebugKotlin` OK à chaque étape ; build release +
> smoke S21+ pour les refactors à impact visuel. On avance par vagues : Tier 1 → Nommage 🔴 →
> Tier 2 (un canonique à la fois) → Tier 3 + Nommage 🟡 (à arbitrer).

---

## Tier 1 — Doublons stricts (copier-coller, fusion sans risque)

Ces composants sont des copies quasi intégrales. Les fusionner ne change rien au rendu.

- [x] **R1 — Fusionner `SessionSummaryItem` ≡ `PlannedWorkoutSummaryItem`**
  Copie intégrale (mêmes weights 0.4f/0.5f, icône 36dp, value 14sp SemiBold, label 12sp).
  → canonique `common_components/SummaryItem.kt`. 2 callsites recâblés, 2 fichiers supprimés.
- [x] **R2 — Supprimer `GenericDetailRow` (≡ `DetailRow`)**
  Doublon strict, 0 callsite réel. Fichier supprimé + section `Section_GenericDetailRow` (M2)
  retirée de l'UI Showcase. ⚠️ Trou « M2 » dans la numérotation molecules — non renuméroté
  (toucherait Figma). Carte Figma « M2 · GenericDetailRow » à retirer (suivi design-system).
- [x] **R3 — Fusionner `AddExerciseToSessionBottomSheet` ≡ `AddExerciseToPlannedWorkoutBottomSheet`**
  → canonique `common_components/ExercisePickerBottomSheet.kt` (paramètre `title`). 2 callsites
  recâblés (SessionTab, PlannedWorkoutScreen), 2 fichiers supprimés. Titres externalisés en
  strings i18n (`sheet_add_exercise_title`, `sheet_add_exercise_to_plan_title`, EN + FR).
- [x] **R4 — Extraire `PhasePickerDialog`**
  → `common_components/PhasePickerDialog.kt` (`onPhaseSelected: (String) -> Unit`). 2 dialogs
  inline copier-collés (SessionTab, PlannedWorkoutScreen) remplacés par 1 composable.
- [x] **R5 — Extraire `SegmentedIconButton` commun**
  → `common_components/SegmentedIconButton.kt` paramétré (`width`/`iconSize`/`unselectedBorderColor`,
  défauts = variante stats). Les 2 helpers internes `SegmentIconButton`/`GoalsSegmentIconButton`
  supprimés ; 5 toggles recâblés (ChartType, Metric, Sort, GoalsViewMode, GoalsSort).
- [x] **R6 — Extraire le helper `progressColor()`**
  Helper public `@Composable progressColor(value)` ajouté dans `LabeledProgressBar.kt`. Les 3
  copies (`LabeledProgressBar` local, `RoutineTasksProgressBar` private, `PlannedDayProgressBar`
  local) supprimées et recâblées sur le helper partagé.
- [x] **R7 — Fusionner `ExerciseListHeader` ≡ `MuscleListHeader`**
  → canonique `common_components/ListSearchHeader.kt` (params `searchPlaceholder` +
  `resultsCountText`). 2 callsites recâblés (ExerciseListScreen, MuscleListScreen), 2 fichiers
  supprimés. ↔ organism **O3b ListSearchHeader**.
- [x] **R8 — Fusionner `RoutineTaskEmptyRow` ≈ `SessionEmptyPhaseRow`**
  → canonique `common_components/EmptyListRow.kt` (icône optionnelle ; `fontSize`/`verticalPadding`
  paramétrés pour préserver les 2 rendus exacts — 13sp/5dp routine vs 14sp/4dp session, à
  uniformiser plus tard si voulu). 4 callsites recâblés, 2 fichiers supprimés.

---

## Tier 2 — Canoniques à créer (vraie valeur design system)

- [x] **R9 — `ScreenTitleBar` canonique** ↔ organism **O3a TitleBar**
  → `common_components/ScreenTitleBar.kt` (`title`, `modifier`, `onClick: (() -> Unit)? = null`).
  10 ex-doublons absorbés et supprimés : 6 headers (`ChronoScreenHeader`, `SessionHeader`,
  `RoutineHeader`, `NotificationsHeader`, `ExerciseHeader`, `PlannedWorkoutHeader`) + 4 `*Title`
  (`ExerciseListTitle`, `MuscleListTitle`, `ExerciseTitle`, `MuscleTitle`). 9 callsites recâblés
  (`RoutineHeader` était un import mort). Strings i18n existantes réutilisées (`exercise_list_title`,
  `muscle_list_title`). compileDebugKotlin + assembleRelease OK. **Résout aussi N11, N19, B1**
  (composants supprimés) et la part `*Title` de B3. `SettingsSubScreenHeader` gardée à part (variante
  fond + back, non absorbée). ⏳ smoke visuel à faire sur device (build installé sur S21+).
- [x] **R10 — `SummaryItem(compact)` + `SummaryRow(items)`** ↔ organism **O4 SummaryStatsRow**
  `SummaryItem` (canonique de R1) reçoit un mode `compact` qui reproduit exactement
  `CalendarSummaryItem` (24dp/13sp/ellipsis) → `CalendarSummaryItem` supprimé (absorbé), section
  Showcase M4 mise à jour. `SummaryRow(items: List<SummaryItemData>, compact)` générique remplace
  la structure `Row` dupliquée ; `SessionSummaryRow` / `PlannedWorkoutSummaryRow` / `CalendarSummary`
  réécrits en adaptateurs fins (signatures inchangées → écrans non touchés). **Zéro changement visuel**
  (choix user). Famille **inline** (`SummaryInlineItem` + `RoutineSummaryInline` +
  `NotificationsSummaryInline`) laissée telle quelle — déjà DRY (`SummaryInlineItem` partagé).
  compileDebugKotlin + assembleRelease OK.
- [x] **R11 — `SegmentedIconToggle<T>` générique**
  → `common_components/SegmentedIconToggle.kt` (générique `<T>` + `data class SegmentItem<T>`).
  Les 5 toggles (`ChartTypeToggle`, `MetricToggle`, `SortToggle`, `GoalsViewModeToggle`,
  `GoalsSortToggle`) réécrits en wrappers (build `List<SegmentItem<T>>` + délègue). Signatures
  inchangées → callsites non touchés. Zéro changement visuel. compileDebugKotlin + assembleRelease OK.
- [x] **R12 — `FormDialog` canonique (slot-based)** ↔ organism **O12 FormDialog**
  `common_components/FormDialog.kt` : `FormDialog(title, confirmText, onConfirm, onDismiss,
  confirmEnabled, dismissText, scrollable, content: @Composable ColumnScope.() -> Unit)`.
  **8 dialogs standalone migrés** — lot 1 (mono-champ) : `CreateActualWorkoutDialog`,
  `RenameActualWorkoutDialog`, `CreatePlannedWorkoutDialog` ; lot 2 (sous-formulaires exercice) :
  `EditDescriptionEquipmentDialog`, `EditInstructionsDialog`, `EditSetsRepsRestDialog` ; lot 3 (gros
  formulaires) : `EditExerciseDialog`, `TaskCreateEditDialog`. Signatures inchangées → callsites non
  touchés. compileDebugKotlin + assembleRelease OK.
  ⚠️ Normalisations mineures : titres `RenameActualWorkoutDialog` / `EditExerciseDialog` (centré)
  → `primary/action` standard ; gaps internes 8/10→12dp. `TaskCreateEditDialog` : bloc `content`
  sur-indenté de 8 espaces (cosmétique, swap AlertDialog→FormDialog — reformat IDE 1 raccourci).
  **Non migrés** (hors scope R12) : form-dialogs inline-écran (add/edit muscle, add exercise, add
  goal, profile delete — nécessitent refacto des écrans) ; paires Add/Edit (`RoutinePeriod`,
  `RoutineTask`, `Muscle`) → **R15**. Strings hardcodées résiduelles = dette B3.
- [x] **R13 — `StatusPickerDialog` canonique**
  → `common_components/StatusPickerDialog.kt` (+ `data class StatusOption`), bâti sur `FormDialog`.
  `ChangeGoalStatusDialog` + `ChangeSetStatusDialog` réécrits en wrappers (build `List<StatusOption>`
  + délèguent). Signatures inchangées, zéro changement visuel. compileDebugKotlin + assembleRelease OK.
  **Non migrés** : le dialog statut inline de `PlannedWorkoutScreen` (inline-écran, statuts en
  convention « espaces » divergente — différé avec les autres inline) ; `TargetPickerDialog` (pattern
  différent : presets de reps + saisie custom, pas une simple liste de statuts).
- [x] **R14 — Migrer 4 confirmations inline → `ConfirmationDialog`** ↔ organism **O11**
  4 `AlertDialog` bruts migrés vers le canonique `ConfirmationDialog` : suppression d'exercice
  (SessionTab + PlannedWorkoutScreen), confirmation de sync + de retour semaine (GoalsTabContent).
  Textes de boutons passés explicitement en `stringResource` (le canonique a des défauts hardcodés
  EN — dette B3). compileDebugKotlin + assembleRelease OK. ⚠️ Normalisation mineure : le message des
  2 dialogs Goals prend la couleur `text/primary` du canonique (était couleur M3 par défaut).
- [x] **R15 — Fusionner les paires Add/Edit en `*FormDialog(entity? = null)`**
  `AddRoutinePeriodDialog` + `EditRoutinePeriodDialog` → `RoutinePeriodFormDialog(period? = null)` ;
  `AddRoutineTaskDialog` + `EditRoutineTaskDialog` → `RoutineTaskFormDialog(task? = null)`. Bâtis sur
  `FormDialog`. 4 callsites RoutineTasksScreen recâblés, 4 fichiers supprimés. Bonus : la version
  Add de RoutineTask était en strings hardcodées EN → la fusion utilise l'i18n (politique 18).
  compileDebugKotlin + assembleRelease OK.
  **Non fait** : la paire add/edit muscle de `MuscleListScreen` est **inline-écran** (2 blocs
  `AlertDialog` dans le fichier écran) → différée avec les autres dialogs inline-écran (extraction
  = refacto d'écran).

---

## Tier 3 — À arbitrer (gain réel, mais design d'API à trancher / risque visuel)

- [x] **R16 — `EntityCard` à slots** ✅ 2026-05-23
  `GenericEntityCard` réécrit slot-based : `title`, `iconRes`, `cardBackground`, `headerTrailing` (slot
  optionnel pour étoile favori), `detailsContent` (slot `ColumnScope`), `actions` (slot `RowScope`).
  Réflexion Java abandonnée du canonique. `ExerciseCard` (9 DetailRows + 5 actions + fond
  `WeekViewProgramCardBackground`) et `MuscleCard` (3 DetailRows + 5 actions) deviennent des thin
  wrappers. Le callsite admin-only `SyncSettingsScreen` conserve la réflexion **inline** (entité
  unknown au compile) — debug view acceptable. Showcase M6 mise à jour avec DetailRows explicites.
- [x] **R17 — `EntityListRow` à slots** ✅ 2026-05-23 (R17a + R17b)
  Split en 2 commits :
  - **R17a ✅** : canonique `EntityListRow` créé (`name`, `nameWeight`, `nameMaxLines`,
    `onNameClick`, `leadingContent`, `trailingContent`, `verticalPadding`, `contentEndPadding`).
    `PlannedExerciseRow` (nameWeight 2.6f, sync+status group + sets×reps) et `RoutineTaskRow`
    (nameWeight 1f, maxLines 1, leadingContent dragHandle, trailingContent sync+checkbox) deviennent
    thin wrappers. Zero changement visuel attendu.
  - **R17b ✅** : `SessionExerciseRow` migré + B5 fix (`Image+ColorFilter` → `ActionIconButton`
    pour l'icône statut tap-cycle). Tap-cycle logic (DONE/IN_PROGRESS/NEXT/NOT_STARTED → onClickDetails)
    préservée dans le slot trailing. Bg coloré 44dp + clickable wrapper conservé, ActionIconButton
    interne `hasBackground=false` pour aligner ripple + tint sur le reste de l'app.
- [x] **R18 — Primitif `ProgressBarPrimitive` extrait** ✅ 2026-05-23 (contre-proposition validée)
  Plutôt qu'étendre `LabeledProgressBar` avec 8 params pour absorber les 2 court-circuit (ce qui
  aurait alourdi le canonique et risqué de régresser les 8+ autres callsites), un **micro-primitif
  10 lignes** `ProgressBarPrimitive(progress, color, modifier, height=7.dp)` rend uniquement la
  barre 7dp (trough `bgRecessed` + remplissage couleur, coins `RoundedCornerShape(2)`).
  `LabeledProgressBar` + `RoutineTasksProgressBar` + `PlannedDayProgressBar` deviennent tous 3
  clients du primitif (~18 lignes dédupliquées). Chaque composant reste structurellement
  lui-même (toolbar 6-zones / week-view label / labeled bar) → zero risque visuel sur les
  callsites existants.
  > Les wrappers d'écran `MonthViewProgressBar`/`GoalsProgressBar`/`PlannedWorkoutProgressBar`/
  > `SessionTabProgressBar` passent déjà par `LabeledProgressBar` — bénéficient indirectement du
  > primitif via la délégation.

---

## Nommage 🔴 — Vrais bugs à corriger

- [x] **N1 — `LineChartScreen.kt` → `MultiLineChart.kt`**
  `git mv` — le fichier contenait `MultiLineChart` (pas un écran). Aucune référence code (Kotlin
  importe par package, pas par nom de fichier).
- [x] **N2 — `weekCompletionBottomSheet.kt` → `WeekCompletionBottomSheet.kt`**
  `git mv` — PascalCase, cohérent avec tous les autres fichiers.
- [x] **N3 — `StatsRangePicker.kt` → `StatsRangeComponents.kt`**
  `git mv` — le fichier contient `RangeChipsRow` + `CustomRangePickerDialog`, aucun composable
  `StatsRangePicker`.
- [x] **N4 — `RoutineDateNavRow` → `DateNavBar`** (composable + fichier renommés, callsite
  RoutineTasksScreen + commentaires AppColors mis à jour ; gardé dans le package `routineTasksScreen`)
  Ce n'est pas une row de liste, c'est une barre de navigation de date (◀ date ▶). Le suffixe
  `Row` le range à tort dans la famille rows.
- [x] **N5 — `EditExerciseDialog.kt` : package divergent** (corrigé `…exercise` → `…exerciseScreen`,
  import de ExerciseListScreen mis à jour)
  Fichier dans `ui/components/exerciseScreen/` mais `package …ui.components.exercise` (les 3
  autres `Edit*Exercise` sont en `…exerciseScreen`). Aligner le package.
- [x] **N6 — Collision `SessionOptionsBottomSheet` ×2** (celui de `weekViewScreen/` renommé
  `WeekSessionOptionsBottomSheet`, composable + fichier + callsite WeekViewScreen)
  Deux composables homonymes dans `sessionTab/` et `weekViewScreen/`, rôles distincts. Renommer
  celui de `weekViewScreen/` → `WeekSessionOptionsBottomSheet`.

---

## Nommage 🟡 — Suffixes trompeurs / incohérences (cosmétique)

> Plusieurs de ces items deviennent **caducs si la consolidation est faite** (les headers/titles
> sont absorbés par R9, etc.). À traiter en dernier, ce qui reste.

- [x] **N7 — `NoSessionFallbackScreen` → `NoSessionFallback`** (pas un écran, c'est un état vide)
- [x] **N8 — `EmptyGoalsWeekCard` → `EmptyGoalsWeekState`** (pas un `Card`, c'est une `Column`)
- [x] **N9 — `TasksHeader` → `TasksTabMenu`** (c'est un sélecteur d'onglets, pas un header)
- [x] **N10 — `TableHeader` (goalsTabContent) → `TableHeaderCell`** (c'est une cellule, pas une rangée)
- [x] **N11 — `ExerciseHeader` (sessionExerciseScreen)** — caduc : `ExerciseHeader` supprimé par R9
  (absorbé dans `ScreenTitleBar`). Plus de collision.
- [x] **N12 — `ExerciseScreenHeader` → `ExerciseActionBar`** (c'est une toolbar d'actions, pas une
  barre de titre)
- [x] **N13 — `PriorityEditDialog` → `EditPriorityDialog`** (suffixe incohérent avec `Edit*Dialog`)
- [x] **N14 — `MorningTimePickerDialog` → `RoutineTimePickerDialog`** (« Morning » trompeur, c'est
  un time picker générique)
- [x] **N15 — `TaskCreateEditDialog` → `TaskFormDialog`** (suffixe atypique)
- [x] **N16 — Uniformiser les suffixes de la famille Summary**
  `CalendarSummary` → `CalendarSummaryRow` ; `NotificationsSummaryInline` → `NotificationsSummaryRow`.
  ⚠️ `RoutineSummaryInline` s'est révélé être du **code mort** (importé dans RoutineTasksScreen +
  TasksCalendarScreen mais jamais appelé) — non renommé, **à supprimer** (cf. note fin de doc).
- [x] **N17 — `SwipeableNotificationItem` / `NotificationCard`** — **SKIP (décision)**
  Vérifié : le fichier `SwipeableNotificationItem.kt` correspond bien au composable **public**
  `SwipeableNotificationItem` ; `NotificationCard` est un composable **privé** interne dont le nom
  est sémantiquement correct (le wrapper swipe = « Item », la carte visuelle interne = « Card »).
  Pas une vraie incohérence → pas de churn sur un `private`. (Note : ce fichier n'a pas de
  déclaration `package` — même problème que B4, à traiter là-bas.)
- [x] **N18 — Trancher `Add*` vs `Create*`** — **SKIP (décision)**
  Ce n'est pas un renommage net mais une décision de vocabulaire qui imposerait un rename de masse
  (plusieurs dialogs/sheets) pour un gain de cohérence marginal. Non fait, assumé.
- [x] **N19 — `ChronoScreenHeader`** — caduc : supprimé par R9 (absorbé dans `ScreenTitleBar`).

---

## Bonus — Nettoyages repérés pendant l'audit

- [x] **B1 — Param mort `navController` dans `PlannedWorkoutHeader`** — caduc : `PlannedWorkoutHeader`
  supprimé par R9, l'appel `ScreenTitleBar(title = workout.name)` n'a plus de `navController`.
- [x] **B2 — Param mort `currentSet` dans `SetOptionsBottomSheet`** — retiré (+ import `ActualWorkoutSet`
  devenu inutile + callsite SessionExerciseScreen). Bonus : `RoutineSummaryInline` (code mort) supprimé.
- [x] **B3 — Strings hardcodées résiduelles i18n** — `LastSessionTableHeader` traité : 4 nouveaux
  string resources EN + FR (`exercise_last_session_col_date/_sets/_volume/_del`) ; code passé en
  `stringResource`. Les autres items B3 (`ExerciseListTitle`/`MuscleListTitle`, `RoutineHeader`
  default, `AddRoutineTaskDialog`, `AddExerciseTo*BottomSheet`) étaient déjà résolus par R9/R3/R15.
- [x] **B4 — Fichiers sans déclaration `package`** — `package` ajouté à 4 fichiers du package
  par défaut : `OptionsBottomSheet.kt` (+ `SheetAction`) et `ForceSheetSystemBars.kt`
  (→ `common_components`), `SwipeableNotificationItem.kt` (→ `notifications.ui.components`). Cascade
  gérée : 15 wrappers `*BottomSheet` recâblés + `DayTasksBottomSheet` + `NotificationScreen`.
  compileDebugKotlin OK.
- [x] **B5 — `SessionExerciseRow` icône de statut** ✅ **absorbé par R17b (2026-05-23)**
  Migration `Image+ColorFilter` → `ActionIconButton` (`hasBackground=false`, `iconSize=30.dp`,
  `clickable=false`, tint pendingDeletion-aware) faite dans le wrapper R17b avec préservation du
  bg coloré 44dp + clickable parent (tap-cycle). Tailles visuelles conservées (44dp box, icône 30dp).
- [x] **B6 — Extraire le `ModalBottomSheet` inline de `MonthViewProgressBar`** → nouveau composable
  `calendarScreen/CalendarLegendBottomSheet.kt` (data class `LegendEntry` + sheet + private grid/cell).
  `MonthViewProgressBar.kt` passe de ~165 lignes à 30 (juste la progress bar + bouton info → sheet).

---

## Avancement

- **Tier 1** : 8 / 8 ✅
- **Tier 2** : 7 / 7 ✅
- **Tier 3** : 3 / 3 ✅ (R16 ✅, R17 ✅, R18 ✅ contre-proposition)
- **Nommage 🔴** : 6 / 6 ✅
- **Nommage 🟡** : 13 / 13 ✅ (N16 fait ; N17, N18 skippés par décision motivée)
- **Bonus** : 6 / 6 ✅ (B1 caduc via R9 ; B2, B3, B4, B6 faits ; B5 absorbé R17b)

**Total : 43 / 43.** 🎉 Refactor UI components terminé.

> **Code mort `RoutineSummaryInline`** — ✅ supprimé (fichier + 2 imports morts) avec B2.

---

## Ce qui est déjà conforme (ne PAS toucher)

L'audit confirme que la canonisation est **déjà très avancée** sur certaines familles :

- **Bottom sheets** : 16/21 sont déjà des wrappers fins autour de `OptionsBottomSheet` canonique.
  Les wrappers nommés par écran (`DayOptionsBottomSheet`, `TaskOptionsBottomSheet`, etc.) sont
  **légitimes** — ils mappent les callbacks métier. Ne pas les fusionner.
- **Progress bars** : 4/6 callsites passent déjà par `LabeledProgressBar` via des wrappers d'écran
  légitimes (slot `rightContent` différent par écran).
- **Pickers** : `WheelPicker` ↔ `HmsWheelPicker` = composition saine, pas de doublon.
- La **majorité des noms** est propre et cohérente (`BottomNavBar`, `MuscleCard`, `GoalRow`,
  `SetRow`, `ConfirmationDialog`, les `*OptionsBottomSheet`…). Pas de refactor de nommage massif.
