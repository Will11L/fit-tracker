# Figma — Vérification du Design System (Atoms + Molecules)

> Checklist de vérification **carte par carte** : design system Figma contre le rendu réel de l'appli Android.
>
> - **Fichier Figma** : `ca2qkjOKCy5N5uEbIKyqrO` — pages `2 · Atoms` et `3 · Molecules`
> - **Référence appli** : écran **UI Showcase** (admin → UI Showcase), onglets Atoms / Molecules
> - **Méthode par item** : screenshot tel (S21+) ↔ screenshot Figma → comparer les points listés
> - **Statut** : `[ ]` à vérifier · `[x]` validé · `[~]` corrigé, à re-valider

## Points transverses (à checker sur chaque composant)

1. **Largeur** ≈ 340dp (cible Samsung S21+). Exception : M8 = 364dp (cadre `_DemoBox` inclus).
2. **Couleurs** : liées aux variables `AppColors` Figma (pas de littéraux figés).
3. **Cadre `_DemoBox`** : 11 sections de l'appli enveloppent leur démo dans un cadre thirdBlue
   (`bg/recessed`, cornerRadius **6**, padding **12**). Noté « cadre ✓ » ci-dessous. Spec de référence :
   [`UiShowcaseScreen.kt` `_DemoBox`](../appli-android/app/src/main/java/com/example/sportapp/admin/ui/UiShowcaseScreen.kt).
4. **Variants / états** : tous présents, couleurs correctes par état.

> ✅ **Écart cadre résolu** : tous les cadres `_DemoBox` Figma sont désormais en `cornerRadius 6 /
> padding 12` (vrai spec `_DemoBox`). Corrigé carte par carte lors de la vérification.

---

## Atoms (16)

### [x] D5 · CustomSwitch — ✅ vérifié + corrigé
- Source : `CustomSwitch.kt` · **cadre `_DemoBox` ✓**
- 4 variants : On-enabled / On-disabled / Off-enabled / Off-disabled
- Corrigé : variants disabled atténués (track α0.4, thumb α0.6 checked / α0.4 unchecked + stroke α0.4) ;
  thumbs Off 24→16dp (M3 unchecked plus petit) ; cadre cr8/pad32 → cr6/pad12

### [x] D5b · CustomRadioButton — ✅ vérifié + corrigé
- Source : `CustomRadioButton.kt` · **cadre `_DemoBox` ✓**
- 4 variants : Selected-enabled / Selected-disabled / Unselected-enabled / Unselected-disabled
- Corrigé : variant Selected-disabled atténué (ring + dot α0.4) ; cadre cr8/pad32 → cr6/pad12
- (Unselected-disabled était déjà correct)

### [x] E1 · CustomTextField — ✅ vérifié + corrigé
- Source : `CustomTextField.kt` · pas de cadre
- 4 variants State×Focus — container `bg/recessed`, label `text/tertiary` au repos / `primary/action` flottant,
  underline focus `primary/action`, valeur `text/primary` (unfocused) / `text/secondary` (focused) — conformes
- Corrigé : suppression d'un `Rectangle 340×18` parasite (barre blanche) dans le variant Filled-Focus
- (l'appli ne montre que 2 états en statique — Empty/Filled — le focus étant interactif ; Figma documente les 4)

### [x] E2 · StyledSearchField — ✅ vérifié + corrigé
- Source : `StyledSearchField.kt` · pas de cadre
- 2 variants Empty / Filled — container `bg/recessed`, texte `primary/action`, placeholder `text/primary` α0.6
- Corrigé : suppression de l'icône loupe parasite (le code n'a PAS de `leadingIcon`) ; suppression de
  l'underline du variant Filled (état non focus) ; texte recentré x44→16 ; placeholder atténué α0.6 ; container 48→56dp

### [x] E3 · CustomSelect — ✅ vérifié + corrigé
- Source : `CustomSelect.kt` · pas de cadre
- 2 variants Closed / Open — container `bg/recessed` 48dp, texte `primary/action`, menu options A/B/C,
  option sélectionnée bordée `primary/action` + texte bleu Bold
- Corrigé **code appli** : chevron `ArrowDropDown` (triangle plein) → chevron fin flippable
  (`KeyboardArrowDown`/`KeyboardArrowUp` selon ouvert/fermé) + tint bleu `primary/action` quand ouvert
- Corrigé **Figma** : chevron ouvert → bleu ; cadre bleu de l'option sélectionnée recentré (y 96→99.5)

### [x] E4 · SingleSelectDropdown — ✅ vérifié + corrigé
- Source : `SingleSelectDropdown.kt` · pas de cadre
- 2 variants Closed / Open — coche ✓ bleue sur l'item sélectionné, item disabled suffixe « (current) » en `text/tertiary`
- Corrigé **code + Figma** : texte item sélectionné → `primary/action` ; chevron `TrailingIcon` → chevron fin flippable + bleu quand ouvert

### [x] E5 · MultiSelectDropdown — ✅ vérifié + corrigé
- Source : `MultiSelectDropDown.kt` · pas de cadre
- 2 variants Closed / Open — coche ✓ bleue sur chaque item sélectionné
- Corrigé **code + Figma** : texte items sélectionnés → `primary/action` ; chevron `TrailingIcon` → chevron fin flippable + bleu quand ouvert

### [x] E6 · FilterDropdown — ✅ vérifié + corrigé
- Source : `FilterDropDown.kt` · pas de cadre
- 2 variants Closed / Open — items All / Recent / Favorites
- Corrigé **code appli** : chevron fin flippable + bleu quand ouvert ; **ajout de la sélection visuelle** (le composant
  n'avait NI check NI logique `isSelected`) → item sélectionné texte `primary/action` + check bleu
- Corrigé **Figma** : chevron ouvert bleu ; item "All" sélectionné en bleu + check bleu ajouté

### [x] F1 · DualTabMenu — ✅ vérifié + corrigé
- Source : `DualTabMenu.kt` · **cadre `_DemoBox` ✓**
- 2 variants SubRow=No / SubRow=Yes — top tab 42dp, sub tab 40dp, sélectionné `selected/fill`, coins carrés, bg `bg/bottom-nav`
- Corrigé **Figma** : sub-row sélectionné `selected/fill` → α0.75 ; textes sous-tabs non-sél. → `text/tertiary` α0.8 ;
  divider 2→1.5dp ; cadre cr8/pad32 → cr6/pad12
- (code `DualTabMenu.kt` conforme — corrections Figma uniquement)

### [x] C1 · TitledDivider — ✅ vérifié + corrigé
- Source : `TitledDivider.kt` · **cadre `_DemoBox` ✓**
- 2 variants Default / Strong — ligne — titre SemiBold — ligne, lignes + titre même couleur (`divider` / `divider/strong`)
- Corrigé **Figma** : titre wrappait sur 2 lignes (text node trop étroit 128) → hug 1 ligne ; gap titre↔ligne 6→8dp ;
  cadre cr8/pad32 → cr6/pad12
- (code `TitledDivider.kt` conforme — corrections Figma uniquement)

### [x] C2 · CustomSpacer — ✅ vérifié (conforme)
- Source : `CustomSpacer.kt` · API `CustomSpacer(width: Dp = 6.dp)` — Spacer transparent `width().fillMaxHeight()`
- Carte Figma C2 **déjà à jour** : titre "C2 · CustomSpacer" + description exacte (transparent, 6dp, renommé depuis
  CustomVerticalDivider). Pas de représentation visuelle (invisible par nature) — choix assumé.
- Note : l'appli (Section_CustomSpacer) montre une mini-démo (2 cells + gap) absente du Figma — divergence mineure à arbitrer.

### [x] D4 · ActionIconButton family — ✅ vérifié + corrigé
- Source : `ActionIcon.kt` · **cadre `_DemoBox` ✓**
- 3 types : ActionIconButton (40×40) / ActionTextButton / ActionIconWithTextButton — bg `bg/button`, icône 24dp `text/primary`
- Corrigé **Figma** : cornerRadius des 3 boutons 4→8 (= `shapes.small`) ; ActionIconWithTextButton trop large
  180→148 + gap icône↔texte 12→8 ; cadre cr8/pad32 → cr6/pad12
- (code conforme — corrections Figma uniquement)

### [x] I1 · StatusIcon — ✅ vérifié + corrigé
- Source : `StatusIcon.kt` · **cadre `_DemoBox` ✓** · API `StatusIcon(iconRes, tint, size = 16.dp)` — param `size` ajouté
- 4 variants : Success / Warning / Error / Info
- Corrigé **code appli** : param `size` ajouté à `StatusIcon` (défaut 16dp, usages calendrier intacts) ; Section_StatusIcon —
  4 icônes distinctes (check_circle / warning / cancel / info, étaient toutes check_circle), `size = 24.dp`, espacement `spacedBy(6.dp)`
- Corrigé **Figma** : cadre cr6/pad12 ; labels Success/Warning/Error/Info sous chaque icône (variant autolayout vertical) ; icônes 16→24dp (parité appli)

### [x] E7 · WheelPicker — ✅ vérifié + corrigé
- Source : `WheelPicker.kt` · **cadre `_DemoBox` ✓**
- Roue 80×200 — fond `text/primary` α0.06, barre sélection α0.08 (40dp), chiffres sélectionné plein / autres α0.45
- Conforme au code. La "known issue" (bg invisible) est **résolue** : sur le fond navy actuel l'α0.06 ressort.
- Corrigé **Figma** : cornerRadius roue 4→8 (= `shapes.small`) ; cadre cr8/pad32 → cr6/pad12

### [x] E8 · HorizontalNumberPicker — ✅ vérifié + corrigé
- Source : `HorizontalNumberPicker.kt` · pas de cadre
- 2 variants WithTarget=No / Yes — cellules 40×40 cr8, spacing 6dp, sélectionné `primary/action`,
  hors-range `redMedium` α0.5, in-range `bg/recessed`
- Conforme. Corrigé **Figma** : cellules rouges hors-range liées à la variable `snackbar/error` α0.5 (étaient en littéral `rgb`)

### [x] I2 · LabeledProgressBar — ✅ vérifié + corrigé
- Source : `LabeledProgressBar.kt` · pas de cadre
- 5 variants 10/30/60/80/100% — track 7dp cr2 `bg/recessed`, couleur par seuil, label % couleur = barre
- Conforme. Corrigé **Figma** : barres 10/30/80% liées aux variables `priority/high|medium|low` (étaient littérales) ;
  100% déjà lié `primary/action` ; 60% (lightGreen) reste littéral — aucune variable AppColors équivalente

---

## Molecules (8)

### [x] M1 · DetailRow — ✅ vérifié + corrigé
- Source : `DetailRow.kt` · **cadre `_DemoBox` ✓**
- 2 variants : Inline (icône 16dp + "label:" Regular + value Medium, tous `text/tertiary`) +
  Indented (AnnotatedString : label Regular + value Medium, top-aligné) — conforme au code
- Corrigé **Figma** : cadre cr8/pad24 → cr6/pad12 ; cadre converti de ComponentSet → FRAME pour
  intercaler les 2 captions « Inline (DetailRow) » / « Indented (DetailRowWithIndentation) — value
  wraps multiline » (alignées à gauche, comme l'appli) ; itemSpacing 12 → 20
- Corrigé **appli** : `Section_DetailRow` `_DemoBox` Column `spacedBy` 12 → 20

### [x] M2 · GenericDetailRow — ✅ vérifié + corrigé
- Source : `GenericDetailRow.kt` · **cadre `_DemoBox` ✓**
- 1 variant — icône 24dp (M3 default), "label:" Regular + value Medium, 13sp `text/tertiary`, gaps 8dp — conforme au code
- Corrigé **Figma** : cadre cr8/pad24 → cr6/pad12 (corrections Figma uniquement)

### [x] M3 · OptionRow — ✅ vérifié + corrigé
- Source : `OptionRow.kt` · pas de cadre
- 3 variants Default / Primary / Danger — Row `bg/recessed` rounded 8dp, padding 12h/10v, label 14sp `text/primary` weight 1f, ActionIconButton trailing
- Conforme. Corrigé **Figma** : ActionIconButton cornerRadius 4→8 (= `shapes.small`) ; bouton Danger lié à `snackbar/error` (était littéral)

### [x] M4 · CalendarSummaryItem — ✅ vérifié + corrigé
- Source : `CalendarSummaryItem.kt` · pas de cadre
- 2 variants Success / Accent — Row `bg/recessed`, padding 12h/10v, icône 24dp tintée + spacer 8dp +
  Column (value 13sp SemiBold `text/primary` + label 12sp Regular `text/tertiary`) — conforme
- Corrigé **appli** : `includeFontPadding = false` + `lineHeight 20.sp` sur value/label (l'écart
  parasite value↔label venait du `lineHeight 24sp` hérité de `bodyLarge`) ; padding 12h/8v → 12h/10v
- Corrigé **Figma** : cornerRadius du Row 4→8 (= `shapes.small`) ; resynchro appli — padding v8→10,
  lineHeight des textes → 20px, itemSpacing colonne 2→0

### [x] M5 · SetRow — ✅ vérifié + corrigé
- Source : `SetRow.kt` · pas de cadre
- Corrigé cette session : largeur 340, "60" (pas "60 kg"), fond dropset `bg/recessed` α0.5,
  flèche dropset centrée, checks verts espacés, boutons delete/notes cr8, hauteur 35dp
- Re-validé sur la page Molecules ✅

### [x] M6 · GenericEntityCard — ✅ vérifié + corrigé
- Source : `GenericEntityCard.kt` · pas de cadre
- Audité cette session : card rounded 16dp, header (icône + titre + chevron), body DetailRows,
  actions delete/sync (padding 87 = SpaceEvenly), couleurs header dynamiques
- Re-validé sur la page Molecules ✅ (collapsed + expanded)

### [x] M7 · TimeRangePickerBar — ✅ vérifié + corrigé
- Source : `TimeRangePickerBar.kt` · pas de cadre
- Corrigé cette session : bande centrale `#153A6B` (= ticks natifs M3) alignée sur le rendu réel —
  bandes inactives inset ~6dp côté extérieur, bande active pleine largeur
- Re-validé sur la page Molecules ✅

### [x] M8 · HmsWheelPicker — ✅ vérifié + corrigé
- Source : `HmsWheelPicker.kt` · **cadre `_DemoBox` ✓**
- Corrigé cette session : 3 wheels largeur égale 93dp (plus de débordement SS), colons hug,
  cadre `_DemoBox` ajouté (cr6/pad12, thirdBlue) → largeur composant 364dp
- Re-validé sur la page Molecules ✅ (preview + wheels + hint dans le cadre)

---

## Avancement

- **Atoms : 16/16 ✅ vérifiés + validés (appli + Figma) — 2026-05-20.**
- **Molecules : 8/8 ✅ vérifiés + corrigés — 2026-05-20.** (M1–M4 vérifiés ; M5–M8 polis puis re-validés)
- **→ Design system Atoms + Molecules : 24/24 complet.**
- Reste : page **Organisms** (encore placeholder) — phase de *création*, pas de vérification.

---

## Sync post-refactor UI components — 2026-05-23

> Le refactor `docs/REFACTOR_UI_COMPONENTS.md` (43/43 livré, 2026-05-22 → 2026-05-23)
> a introduit renommages et nouveaux canoniques. Sync Figma exécutée via MCP
> (`use_figma`) — toutes les modifs ci-dessous sont appliquées directement sur le
> fichier `ca2qkjOKCy5N5uEbIKyqrO`.

### Atoms (16 → 16, inchangé en compte)

- **I2 · LabeledProgressBar** : description actualisée — mention du sous-primitif
  `ProgressBarPrimitive(progress, color, modifier, height, troughColor)` extrait par
  R18 (partagé avec `RoutineTasksProgressBar` + `PlannedDayProgressBar`). Bonus :
  param `troughColor` ajouté pour résoudre le bug "trough invisible sur container
  thirdBlue" (passe à `boxBlue` quand callsite sur `bgRecessed`).
- **E7 · WheelPicker** : fix offset vertical +2px sur les 5 TEXT (10, 11, 12, 13, 14)
  — les chiffres étaient décalés de 2px vers le bas dans leurs cellules de 40dp. Le
  plus visible sur le "12" sélectionné (la barre sombre derrière sert de repère).
  Y shifté de -2px sur chaque (`561:6` à `561:10`). Maintenant alignement parfait
  avec le code app (`WheelPicker.kt`:169-185 utilise `Box(height=40dp,
  contentAlignment=Center)`).
- **M8 · HmsWheelPicker** : même bug que E7 sur les **3 wheels HH/MM/SS** (15 TEXT
  au total `597:11..597:15`, `597:21..597:25`, `597:31..597:35`). Y shifté de
  -2px sur chacun. Les valeurs sélectionnées (08, 30, 45) sont maintenant centrées
  dans leur barre sombre.
- **Cleanup** : TEXT node orphelin `573:6` "C2 · CustomSpacer" hors gallery
  supprimé (la vraie carte C2 reste dans la gallery, `576:57`).

### Molecules (8 → 9, +1 net)

- ❌ **M2 · GenericDetailRow SUPPRIMÉ** : R2 a éliminé `GenericDetailRow.kt` (doublon
  strict de `DetailRow`, 0 callsite réel). La carte Figma + ComponentSet retirés.
  ⚠️ Trou « M2 » assumé dans la numérotation (non renuméroté, toucherait organisms).
- 🔄 **M4 · CalendarSummaryItem → M4 · SummaryItem (compact)** : R10 a absorbé
  `CalendarSummaryItem` dans `SummaryItem(mode = compact)` — même rendu, canonique
  unique. Carte renommée + ComponentSet renommé + description annotée R10.
- 🔄 **M6 · GenericEntityCard** : description actualisée — R16 (2026-05-23) a
  réécrit en slot-based (`headerTrailing`, `detailsContent`, `actions`), réflexion
  Java abandonnée du canonique. `ExerciseCard` + `MuscleCard` = thin wrappers.
- ➕ **M9 · EntityListRow** : nouvelle carte créée — R17 (2026-05-23) a posé le
  canonique row 44dp slot-based (`name`, `nameWeight`, `nameMaxLines`,
  `onNameClick`, `leadingContent`, `trailingContent`, `verticalPadding`,
  `contentEndPadding`, `isPendingDeletion`). Canonise `PlannedExerciseRow` +
  `RoutineTaskRow` + `SessionExerciseRow`. ✅ Visuel posé (2026-05-23) — 3 variants
  340dp : Default (PlannedExerciseRow style : name weight 2.6f + sync cloud_done
  primaryAction + status check snackbar/success + sets×reps) / WithLeading
  (RoutineTaskRow style : dragHandle 40dp `ic_menu` + name weight 1f maxLines 1 +
  sync + checkbox 44dp `ic_rounded_check_box_outline_blank`, contentEndPadding 8) /
  PendingDeletion (bg darkGray #13151A + texte text/tertiary + icônes atténuées).
- ➕ **M10 · SegmentedIconToggle** : nouvelle carte créée — R11 (2026-05-22) a posé
  le canonique générique `<T>` + `data class SegmentItem<T>` (atom partagé R5
  `SegmentedIconButton`). Canonise 5 toggles : `ChartTypeToggle`, `MetricToggle`,
  `SortToggle`, `GoalsViewModeToggle`, `GoalsSortToggle`. ✅ Visuel posé
  (2026-05-23) — 3 variants segment box 30dp cr6 : 2 segments ChartTypeToggle
  (`ic_equalizer` + `ic_query_stats`, width 40, icon 18, border text/secondary
  α0.6) / 3 segments MetricToggle (`ic_rounded_repeat` +
  `ic_rounded_format_list_numbered` + `ic_exercise`, mêmes dimensions) / 5 segments
  GoalsSortToggle (`ic_rounded_sort_by_alpha` + `ic_pie_chart` proxy palette +
  `ic_arrow_downward_alt` + `ic_arrow_upward_alt` + `ic_rounded_flag`, width 36,
  icon 16, border divider α0.6). Premier segment sélectionné dans chaque variant
  (bg primary/action + icône text/primary) ; autres unselected (bg transparent,
  icône `lightGrayBlue` #7B9DD0).
- 🟡 **M3 · OptionRow** : note R8 ajoutée — canonique frère `EmptyListRow` (4
  callsites `RoutineTaskEmptyRow` + `SessionEmptyPhaseRow` absorbés). Pas de carte
  distincte (squelette voisin sans bouton trailing).

- ➕ **M11 · DrawerItem** : nouvelle carte créée 2026-05-23 — atome ligne du drawer (icône 22dp + label 14sp Medium + slot trailing), padding h=18 v=12 → hauteur 46dp, bg `bg/recessed`. ComponentSet **4 variants** matchant les patterns réels [DrawerContent.kt](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerContent.kt) :
  - `State=Default` — icône + label seul (8 callsites code : Conversations, Program, Exercises, Muscles, Calendar, Statistics, Profile, Settings, Export, Log out, Sync settings, Admin items)
  - `State=WithIconCount` — icône + label + (mail icon 16dp + count number SemiBold 12sp, tous deux `primaryAction`) → match [DrawerIconCountIndicator.kt](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerIconCountIndicator.kt) utilisé pour Notifications (unreadCount)
  - `State=WithStatsBadge` — icône + label + pill RoundedCornerShape(8dp) avec text "done/total" 12sp Medium, bg `color.copy(alpha=0.15)` (orangeMedium tint visible) + text color (orangeMedium en cours / mediumGreen fini) → match exact `TasksTodayStatsBadge` ([DrawerContent.kt:428-447](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerContent.kt#L428-L447)) utilisé pour Tasks (done/total). Padding pill h=12 v=4.
  - `State=WithProgress` — icône + label + mini bar 60×6 (RoundedCornerShape 2dp), track bg **`bg/surface`** (boxBlue, plus clair que `bg/recessed`) + fill `primaryAction` + text "60%" SemiBold 12sp `primaryAction` → diverge volontairement de [DrawerMiniProgress.kt:58](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerMiniProgress.kt#L58) qui utilise `bgRecessed` (track invisible sur parent `bgRecessed`). Validé user 2026-05-23 : Figma pose la version visible, code à corriger côté `DrawerMiniProgress.kt` (cf. TODO_FIXES §9).
  - Variants showcase utilisent les labels code-représentatifs : Default=Calendar / WithIconCount=Notifications / WithStatsBadge=Tasks / WithProgress=Bench Day (chacun avec son icône appropriée).
- ➕ **M12 · DrawerSection** : nouvelle carte créée 2026-05-23 — composition d'une section drawer : titre uppercase 13sp Bold `text/accent` (padding h=16 v=8 centré) + topDivider (`dividerStrong α0.60`, 2.5dp, padding 20dp) + N instances M11 séparées par inter-dividers (`dividerStrong α0.30`, 2dp, padding 18dp). Container `bg/recessed` padding top=12 bottom=8. Canonise [DrawerSection.kt](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerSection.kt). Le **showcase M12 demo** dans la carte affiche 2 instances stackées (ACTIVITY + ACCOUNT & SETTINGS) séparées par un inter-section divider (`dividerStrong` 1.5dp full-opacity full-width match [DrawerContent.kt:252](../appli-android/app/src/main/java/com/example/sportapp/ui/components/drawer/DrawerContent.kt#L252)) — section 1 = 5 items showcasing les 4 variants M11 (Notifications WithIconCount / Tasks WithStatsBadge / Bench Day WithProgress / Calendar + Statistics Default) ; section 2 = 5 items Default (Profile/Settings/Export data/Log out/Sync settings). 2 callsites code dans O2 + 1 prévu Admin (cf. [CLAUDE.md historique 2026-05-11](../CLAUDE.md)). O2 utilise des instances M11 directement (sections inline) pour permettre les overrides icône/label per-item.

### Bilan

- **Atoms : 16/16 ✅** (descriptions à jour post-refactor)
- **Molecules : 11/11 ✅** — 7 validées (M1, M3, M4, M5, M6, M7, M8) + M9 et M10
  visuels posés (2026-05-23) + M11 (DrawerItem) et M12 (DrawerSection) posés (2026-05-23).

Pour la sync des **Organisms**, voir [`FIGMA_ORGANISMS.md`](FIGMA_ORGANISMS.md) §Sync post-refactor.
