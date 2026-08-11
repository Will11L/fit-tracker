# SPEC D'ASSEMBLAGE FIGMA — Feature **Stats** (sport-app, Android Compose)

> Frame cible : **412 × 916** (Samsung S21+, dp). Écran PLEIN (pas un onglet du Home).
> Fond global : `appColors.bgScreen` (bleu très foncé, ~`#0B1622`). Padding horizontal écran : **18dp** (sauf chrome StatusBar/BottomNav qui sont edge-to-edge).
> Source code principale : `appli-android/app/src/main/java/com/example/sportapp/feature/stats/ui/StatsScreen.kt`.
> Le screenshot de réf (`09_stats_top.jpg`) montre le **haut de l'écran scrollé en mode BAR / range "3 months"** : FrequencyCard, ligne Sort+Range, section "Sets / Zone" complète (bar chart 6 zones + chips), et le début de "Sets / Group".

---

## 0. Repère visuel rapide (d'après le screenshot)

- StatusBar Android (heure 15:37 à gauche ; clé / mute / wifi / signal / batterie "95" à droite) sur fond noir.
- Titre divisé **"Training frequency"** (texte bleu-gris centré, traits horizontaux de part et d'autre).
- **FrequencyCard** : 1 ligne, 4 stats — `Sessions 41` (bleu), `Per week 3,2` (bleu), `Top group Legs` (VERT, couleur de la zone Legs), `Total sets 2035` (bleu). Labels gris au-dessus, valeurs en gras en dessous.
- Ligne **Sort + Range chips** : à gauche 2 pilules carrées (icône A↓Z + icône palette **sélectionnée/bleue**), puis chips horizontalement scrollables `k`(=tronqué "…1 week"), `30 days`, `3 months`(**sélectionné/bleu plein**), `6 m…`(tronqué).
- Titre divisé **"Sets / Zone"**.
- Ligne **toggles** : à gauche 2 pilules (BarChart **sélectionné/bleu** + LineChart) ; à droite 3 pilules (Repeat=Sets **sélectionné/bleu** + ListNumbered=Exercises + Dumbbell=Volume).
- **Bar chart** (carte fond enfoncé, bordure arrondie) : axe Y gauche `0 / 275 / 549 / 824`, guidelines horizontales pointillées, 6 barres colorées : Chest (bleu), Back (orange), Shoulde[rs] (bleu clair), Arms (rouge), Legs (vert, la + haute ≈824), Core (jaune, très petite). Labels X colorés sous chaque barre.
- **Chips de filtre Zone** (1 ligne scrollable) : `Chest`(bleu plein), `Back`(orange plein), `Shoulders`(bleu clair plein), `Arms`(rouge plein), `L…`(vert, tronqué) — fond = couleur de la zone quand coché.
- Titre divisé **"Sets / Group"** + sa ligne de toggles + début de chart (on voit "478" sur l'axe Y et une barre vert pâle à droite).
- **BottomNavBar** : 5 icônes — burger (avec badges sync/WS/offline en coin), calendrier, maison, chrono, **Stats (sélectionné : fond bleu arrondi + icône chart blanche)**.
- SystemNav Android (3 boutons : recents ||| , home ◯ , back ‹) sur fond noir.

---

## 1. Layout écran (haut → bas)

Empilement vertical. Le **HEADER est sticky** (Training frequency + FrequencyCard + ligne Sort/Range) ; en dessous une **zone scrollable** (`weight(1f)` + `verticalScroll`) contient les 4 sections de charts.

| # | Section | Composant Compose (fichier) | Données / props visibles | Hauteur approx (dp) |
|---|---|---|---|---|
| A | **StatusBar** (chrome, edge-to-edge) | système Android | Heure + icônes statut, fond noir `#000` | **32** |
| B | Spacer | — | — | 12 |
| C | **TitledDivider "Training frequency"** | `designsystem/common_components/TitledDivider.kt` | Texte `stats_training_frequency` = "Training frequency", couleur `appColors.divider`, SemiBold, 2 `HorizontalDivider` (weight 1f) de part et d'autre, padding H 8dp autour du texte ; padding vertical 6dp | ~28 |
| D | **FrequencyCard** | `FrequencyCard` (privé, dans `StatsScreen.kt`) | Card `containerColor = appColors.bgRecessed`, padding H14/V10, marge V8dp. 1 `Row` SpaceBetween de 4 `FrequencyStat` : `Sessions`=count, `Per week`=avg 1 décimale (ex "3,2"), `Top group`=zone localisée colorée (ex "Legs" en vert), 4ᵉ stat dépend de la métrique active : `Total sets` / `Total exos` / `Total volume` + suffixe unité. Chaque `FrequencyStat` : label 10sp `lightGrayBlue` en haut, valeur 15sp Bold (couleur `appColors.primaryAction` sauf top group qui prend la couleur de zone) en bas. | ~64 (card incl. marges) |
| E | Spacer | — | — | 8 |
| F | **Ligne Sort + Range** (`Row`, spacedBy 8dp, centré V) | inline `StatsScreen.kt` | Voir détail §1.1 et §1.2 | ~32 |
| G | Spacer | — | — | 16 |
| — | **↓↓ DÉBUT ZONE SCROLLABLE (weight 1f, verticalScroll) ↓↓** | — | Les sections H→W scrollent sous le header sticky | reste de l'écran |
| H | **Section 1 — "Sets / Zone"** (hero) | voir §1.3 (titre dynamique + toggles + chart + chips) | 6 zones (Chest…Core), `colorMap = groupColors` | titre ~28 + toggles ~38 + chart **300** + chips ~32 + spacers |
| I | Spacer | — | — | 16 |
| J | **Section 2 — "Sets / Group"** | voir §1.3 | 17 muscle_groups (Pecs, Lats, Delts, Triceps…), `colorMap = muscleGroupColors` (nuances par zone) | idem section (chart 300) |
| K | Spacer | — | — | 16 |
| L | **Section 3 — "Sets / Muscle"** | voir §1.3 | 35 muscles précis (Mid Chest, Triceps Long head…), `colorMap = muscleColors` | idem section (chart 300) |
| M | Spacer | — | — | 16 |
| N | **Section 4 — "Sets / Exercise"** | voir §1.3 | exercices individuels, `colorMap = exerciseColors` ; métrique EXERCISES → label "Sessions" | idem section (chart 300) |
| O | Spacer fin de scroll | — | — | 24 |
| — | **↑↑ FIN ZONE SCROLLABLE ↑↑** | — | — | — |
| P | **BottomNavBar** (chrome) | `designsystem/bottomNavigationBar/BottomNavBar.kt` | 5 items : Menu(burger)+badges / Calendar / Home / Chrono / **Stats (sélectionné)**. Conteneur `appColors.bgBottomNav`, shadow elev 24dp. Item sélectionné = icône 38dp + fond `appColors.selectedFill` (rounded small) + tint `appColors.textOnSelected` ; non-sélectionné = 28dp `appColors.textTertiary`. | **52** (zone barre) |
| Q | **SystemNav** (chrome, edge-to-edge) | système Android | 3 boutons gestes, fond noir | **48** |

> **Note budget vertical** : 412×916 ne montre PAS toutes les sections d'un coup. Le mockup "haut d'écran" reproduit le screenshot (chrome + header sticky + Section 1 entière + début Section 2). Les sections 2/3/4 complètes sont identiques structurellement à la Section 1 (même gabarit), à dupliquer pour une vue longue/scroll si besoin.

### 1.1 SortToggle (gauche de la ligne F)

- Fichier : `feature/stats/ui/components/stats/SortToggle.kt` → rend un `SegmentedIconToggle` (`designsystem/common_components/SegmentedIconToggle.kt`).
- **2 pilules** côte à côte (spacedBy 6dp). Chaque pilule = `SegmentedIconButton` : **40dp large × 30dp haut**, coins `RoundedCornerShape(6dp)`, bordure 1dp, icône 18dp centrée.
  - Pilule 1 : icône **`SortByAlpha`** (A↓Z) → valeur `ALPHA`.
  - Pilule 2 : icône **`Palette`** → valeur `ZONE`. **Sélectionnée par défaut** (default `StatsSortMode.ZONE`).
- Sélectionné : fond `appColors.primaryAction` (bleu), icône `appColors.textPrimary`. Non-sélectionné : fond transparent, bordure `appColors.textSecondary @60%`, icône `lightGrayBlue`.

### 1.2 RangeChipsRow (droite de la ligne F, `weight(1f)`)

- Fichier : `feature/stats/ui/components/stats/RangeChipsRow.kt`.
- `Row` `height(32dp)` **horizontalement scrollable**, chips spacedBy 8dp. **7 `FilterChip` M3** dans l'ordre :
  `1 week` · `30 days` · `3 months` · `6 months` · `1 year` · `All` · `Custom`.
  (libellés : `stats_range_1_week`/`_30_days`/`_3_months`/`_6_months`/`_1_year`/`_all`/`_custom`).
- Chip non-sélectionné : fond transparent, label `lightGrayBlue`, bordure 1dp `appColors.textSecondary @60%`.
- Chip sélectionné : fond `appColors.primaryAction`, label `appColors.textPrimary`, bordure `appColors.primaryAction`.
- État par défaut du screenshot : **`3 months` sélectionné** (bleu plein). Les chips débordent → scroll horizontal (on voit `1 week` tronqué à gauche et `6 m…` tronqué à droite).

### 1.3 Gabarit d'UNE section de chart (identique ×4)

Chaque section = (titre dynamique) + (ligne 2 toggles) + (chart) + (spacer 8) + (chips de légende/filtre).

1. **TitledDivider** avec titre **dynamique selon la métrique active** de la section :
   - Zone : `Volume (kg) / Zone` | `Sets / Zone` | `Exercises / Zone`.
   - Group : `Volume (kg) / Group` | `Sets / Group` | `Exercises / Group`.
   - Muscle : `Volume (kg) / Muscle` | `Sets / Muscle` | `Exercises / Muscle`.
   - Exercise : `Volume (kg) / Exercise` | `Sets / Exercise` | `Sessions / Exercise` (métrique EXERCISES → "Sessions").
   - `(kg)` remplacé par `(lbs)` si unité = LBS.
2. **Ligne toggles** (`Row`, `SpaceBetween`, padding V4) :
   - **Gauche = `ChartTypeToggle`** (`ChartTypeToggle.kt`) : 2 pilules `SegmentedIconButton` — `BarChart` (valeur BAR, **défaut sélectionné**) + `ShowChart` (valeur LINE).
   - **Droite = `MetricToggle`** (`MetricToggle.kt`) : 3 pilules — `Repeat` (SETS, **défaut sélectionné**) + `FormatListNumbered` (EXERCISES) + `FitnessCenter` (TOTAL_WEIGHT).
   - Toggles **indépendants par section** (Zone peut être BAR/Sets et Muscle LINE/Volume simultanément).
3. **Chart** : `MuscleGroupVolumeChart` (`MuscleGroupVolumeChart.kt`), **hauteur 300dp**, carte fond `appColors.bgRecessed`, coins `RoundedCornerShape(8dp)`.
   - **Mode BAR** (= screenshot) : Compose pur. Axe Y gauche (colonne 24dp) avec **4 ticks** `0 / max⅓ / max⅔ / max` (9sp `lightGrayBlue`), 4 **guidelines horizontales pointillées** (`lightGrayBlue @45%`, dash 8/5), 1 **ligne d'axe Y verticale** pleine à gauche. Une **barre par clé** : largeur ~55% du slot, coins hauts arrondis 5dp, couleur = `colorMap[clé]`. Sous chaque barre un **label X coloré** (10sp Medium, même couleur que la barre). Padding interne : start 4 / end 12 / top 28 / bottom 12. Si >15 barres → **scroll horizontal** (slot 18dp, barre 10dp, spacing 3dp).
   - **Mode LINE** : Vico `CartesianChartHost`, multi-courbes (1 par clé, stroke 2.5dp, cubic), couleur = `colorMap`. Axe X formaté `W18` (semaine ISO) ou `5/5` (jour) selon granularité, axe Y `k`/`M` si volume. Guidelines verticales discrètes aux transitions de mois + label mois court. Scroll horizontal si période longue (56dp/bucket weekly, 80dp/bucket daily).
   - **Granularité auto** : ≤14 jours → DAILY, sinon WEEKLY.
   - **Empty states** (voir §4).
4. **GroupFilterChips** (`GroupFilterChips.kt`) : `Row` `height(32dp)` scroll horizontal, chips spacedBy 8dp. 1 `FilterChip` par clé, **label = nom de la clé**, **fond = `colorMap[clé]` quand sélectionné** (sinon transparent + bordure de la couleur), label `appColors.textPrimary` si coché sinon la couleur. Sert de **légende + filtre** (pas de légende séparée sous le chart).

> **Palette de référence (`groupColors`, niveau Zone)** : Chest = `appColors.primaryAction` (bleu) · Back = `orangeMedium` · Shoulders = `appColors.accentText` (bleu clair) · Arms = `redMedium` · Legs = `mediumGreen` · Core = `yellowMedium` · Other = `mediumPurple`. Les sections Group/Muscle/Exercise dérivent des **nuances** de la couleur de la zone parente (`paletteForZone`).

---

## 2. Interactions (flow map)

Sur l'écran Stats principal :

- tap **pilule SortToggle (A↓Z / Palette)** → bascule le tri **des 4 sections** (ALPHA = alphabétique, ZONE = groupé par couleur de zone). Reste sur l'écran (réordonne barres + chips).
- tap **range chip `1 week`/`30 days`/`3 months`/`6 months`/`1 year`/`All`** → recalcule les 4 charts pour la période. Reste sur l'écran.
- tap **range chip `Custom`** → ouvre **`CustomRangePickerDialog`** (choisir une plage de dates).
- tap **pilule ChartTypeToggle (Bar / Line)** d'une section → bascule **cette section** entre bar chart et line chart.
- tap **pilule MetricToggle (Sets / Exercises / Volume)** d'une section → change la **métrique de cette section** (et son titre dynamique). La métrique de la **Section Zone** pilote aussi la 4ᵉ stat de la FrequencyCard (Total sets/exos/volume).
- tap **GroupFilterChip** (Zone/Group/Muscle/Exercise) → toggle la visibilité de cette série dans le chart de sa section (filtre). Reste sur l'écran.
- tap **icône BottomNav** : Menu → ouvre le Drawer ; Calendar/Home/Chrono → navigue vers l'écran correspondant ; Stats → déjà dessus (no-op).
- **back système** : si le Drawer est ouvert → le ferme ; sinon comportement nav standard.

> ⚠️ **Pas de drill-down depuis StatsScreen** : l'écran principal **ne navigue PAS** vers MuscleStatsScreen / ExerciseStatsScreen (aucun `navController.navigate` dans `StatsScreen.kt`). Ces deux sous-écrans existent et sont atteints par d'AUTRES écrans (liste muscles, liste exercices, carte muscle). Inclus ici car demandés dans la spec.

Dans `CustomRangePickerDialog` :
- tap **flèche gauche / droite** (`ActionIconButton` `ic_arrow_left_alt` / `ic_arrow_right_alt`) → mois précédent / suivant.
- tap **jour** → sélectionne start (1ᵉʳ tap) puis end (2ᵉ tap) ; tap avant le start → swap ; tap quand range complet → reset au nouveau start.
- tap **Cancel** → ferme sans appliquer. tap **OK** (activé seulement si start ET end choisis) → applique `StatsRange.Custom(start, end)` et ferme.

Dans `MuscleStatsScreen` (sous-écran) :
- tap **flèche back** (header) ou **back système** → `popBackStack`.
- tap chips de période / `Custom` → idem (range partagé via `StatsRangeState`).
- tap **RelatedExerciseRow** (carte exercice "›") → **`ExerciseStatsScreen`** (`Routes.exerciseStats(uuid)`).

Dans `ExerciseStatsScreen` (sous-écran) :
- tap **flèche back** / **back système** → `popBackStack`.
- tap chips de période / `Custom` → idem.

---

## 3. Inventaire dialogs & bottom-sheets + sous-écrans

### 3.1 `CustomRangePickerDialog` (dialog plein largeur)
- Fichier : `feature/stats/ui/components/stats/CustomRangePickerDialog.kt`.
- **But** : sélectionner une plage de dates personnalisée (range `Custom`).
- **Contenu clé** (Compose pur, style CalendarView, fond `appColors.bgScreen`, coins 20dp, padding) :
  - Titre `Select date range` (13sp `lightGrayBlue`) + ligne **range affiché** "MMM d, yyyy → MMM d, yyyy" (18sp Medium, placeholders `Start`/`End`).
  - `HorizontalDivider` (`accentText @40%`).
  - **Header navigation mois** : flèche gauche (`ActionIconButton`) + label mois centré entre 2 traits (`accentText`, 16sp Medium, ex "June 2026") + flèche droite.
  - **Ligne 7 jours** Monday-first (Mon…Sun, 13sp `lightGrayBlue`, centrés).
  - **Grille des jours** : `LazyVerticalGrid` 7 colonnes, cellules carrées (`aspectRatio 1f`), coins 6dp. Jour start/end = fond `appColors.primaryAction` + texte gras. Jours dans le range = fond `appColors.selectedFill` + texte `textOnSelected`. Aujourd'hui (si non sélectionné) = bordure `accentText`.
  - **Boutons** alignés à droite : `Cancel` (`lightGrayBlue`) + `OK` (`primaryAction`, désactivé tant que start/end incomplets).

### 3.2 Sous-écran `MuscleStatsScreen` (écran plein, NON atteint depuis Stats)
- Fichier : `feature/muscles/ui/MuscleStatsScreen.kt`.
- **But** : stats d'un muscle précis (objectif hebdo + volume hebdo + exercices liés).
- **Contenu clé** (Column scrollable, fond `appColors.bgScreen`, padding H18) :
  - **Header** : `ActionIconButton` back + nom du muscle (18sp SemiBold `primaryAction`).
  - TitledDivider "Weekly goal" + **WeekGoalCard** (Card `bgRecessed`) : "done/target" + "priority/status" (codes UPPER_CASE bruts), ou "no goal".
  - TitledDivider "Period" + **`RangeChipsRow`** (mêmes 7 chips + Custom).
  - **`StatsChartCard`** "Weekly volume" → **`MultiLineChart`** (1 courbe "Volume" en `orangeMedium`) ; empty text si pas de data.
  - TitledDivider "Exercises" + liste de **`RelatedExerciseRow`** (Card `bgRecessed`, nom + chevron "›", cliquable → ExerciseStats), ou "no exercises".

### 3.3 Sous-écran `ExerciseStatsScreen` (écran plein, NON atteint depuis Stats)
- Fichier : `feature/exercises/ui/ExerciseStatsScreen.kt`.
- **But** : stats d'un exercice (records all-time + progression sur la période).
- **Contenu clé** (Column scrollable, fond `appColors.bgScreen`, padding H18) :
  - **Header** : back + nom exercice (18sp SemiBold).
  - TitledDivider "All time" + **AllTimeStatsCard** (Card `bgRecessed`, Row 3 colonnes) : `Top set` (max weight + unité), `Total sets`, `Total volume` (+ unité).
  - TitledDivider "Period" + **`RangeChipsRow`**.
  - **`StatsChartCard`** "Progression" avec **légende** (carrés colorés) : `Max weight (kg)` = `mediumGreen`, `Volume (kg)` = `orangeMedium` → **`MultiLineChart`** 2 courbes (Weight + Volume) ; empty text sinon.

> `StatsChartCard` (`designsystem/common_components/StatsChartCard.kt`) et `MultiLineChart` (`designsystem/common_components/MultiLineChart.kt`) sont les composants chart **legacy** utilisés par les 2 sous-écrans (différents du `MuscleGroupVolumeChart` Compose/Vico de l'écran principal).

---

## 4. Notes (états / variantes / toggles / empty states)

- **Header sticky** : `Training frequency` + `FrequencyCard` + ligne `Sort/Range` restent figés en haut ; seules les 4 sections de charts défilent (zone `weight(1f)` + `verticalScroll`).
- **Toggles par défaut** : tri global = **ZONE (palette)** ; chaque section démarre en **BAR + Sets**. Le screenshot reflète cet état (Palette + Bar + Sets actifs, range `3 months`).
- **Métrique → titre dynamique** : le titre de section ET le 4ᵉ stat de la FrequencyCard changent avec la métrique (Sets→"Total sets", Exercises→"Total exos", Volume→"Total volume kg/lbs"). Section Exercise + métrique EXERCISES affiche **"Sessions"** au lieu de "Exercises".
- **Unité de poids** : KG par défaut ; si l'user est en LBS, tous les volumes sont convertis et `(kg)` devient `(lbs)`.
- **Granularité auto** : période ≤14 jours → buckets **DAILY** (X = `5/5`) ; sinon **WEEKLY** (X = `W18`).
- **Couleurs des séries** : palette par zone. Section Zone = 6 couleurs franches (`groupColors`). Sections Group/Muscle/Exercise = **nuances** dérivées de la zone parente via `paletteForZone` (ex. tous les groupes "Chest" en tons bleus). Les chips de filtre reprennent exactement la couleur de leur série (rôle de légende).
- **Scroll horizontal des charts** : BAR bascule en scroll si >15 séries (utile section Muscle 35 / Exercise N) ; LINE scrolle si la période est longue.
- **Empty states du `MuscleGroupVolumeChart`** (carte `bgRecessed`, bordure `primaryAction` 1.5dp, texte centré `primaryAction` 12sp) :
  - Aucune donnée sur la période → **"No data for this period"**.
  - Données présentes mais **1 seul bucket** (impossible de tracer une ligne) → **"Not enough data points to draw a trend.\nTry a wider range."**.
- **Empty states sous-écrans** : `StatsChartCard` affiche `emptyText` ("No data") si la série est vide ; listes muscles/exercices affichent un texte de remplacement si vides.
- **Top group "—"** : si aucune zone n'a de volume, la stat "Top group" affiche `—` en `lightGrayBlue`.
- **Chrome** : StatusBar 32dp (fond noir) en haut ; en bas BottomNavBar (zone barre ~52dp, conteneur `bgBottomNav`, item Stats sélectionné = fond `selectedFill` arrondi + icône agrandie 38dp) puis SystemNav 48dp (fond noir). L'item **Menu (burger)** porte des badges en coin (sync done/pending, WS on/off, offline) — visibles dans le screenshot.
- **Localisation** : libellés EN canoniques (FR via `values-fr`). Les codes wire (zones, status, priority) ne sont pas traduits côté stockage ; "Top group" affiche la zone localisée via `localizedZone`.
