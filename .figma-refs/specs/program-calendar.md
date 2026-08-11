# SPEC D'ASSEMBLAGE FIGMA — Program / Calendar (Home tab 2, sub 1)

> Écran : **CalendarViewScreen** (onglet Home `Program` → sous-onglet `Calendar`).
> Frame cible : **412 × 916 dp** (Samsung S21+, dark theme par défaut).
> Source code : `appli-android/app/src/main/java/com/example/sportapp/feature/calendar/`
> Réf visuelle : `.figma-refs/Screenshots/06_home_program_calendar.jpg`
> Le contenu (à partir du divider "Month Completion") est hôté DANS `HomeScreen` :
> `Column { DualTabMenu(...) ; Box { ... CalendarViewScreen(...) } }` (cf. `feature/home/ui/HomeScreen.kt`). Le contenu du calendrier n'a PAS de StatusBar / BottomNav propres : ceux-ci appartiennent au shell `MainActivity` (Scaffold). Ils sont inclus ci-dessous pour reconstituer l'écran complet.

---

## Palette (tokens dark — `designsystem/theme/Color.kt` + `AppColors.kt`)

| Token sémantique | Valeur hex (dark) | Usage dans cet écran |
|---|---|---|
| `bgScreen` (blueBackground) | `#101720` | fond global de l'écran + fond des bottom-sheets |
| `bgSurface` (boxBlue) | `#1E2A3C` | (cartes posées sur bgScreen, non utilisé directement ici) |
| `bgRecessed` (thirdBlue) | `#091216` | fond du conteneur calendrier, fond des cartes résumé, fond legend cells, trough barre |
| `bgBottomNav` (secondBlue) | `#0F1C26` | fond DualTabMenu + sous-barre + BottomNavBar |
| `bgButton` (boxBlue) | `#1E2A3C` | fond des ActionIconButton (flèches mois, bouton info) |
| `selectedFill` (firstBlue) | `#153A6B` | onglet actif (Program / Calendar), item BottomNav actif (Home) |
| `primaryAction` (ButtonPrimaryColor) | `#2377CA` | bordure du jour "aujourd'hui", icône cloud-done synced, % barre si ≥100% |
| `textPrimary` | `#FFFFFF` | titres onglets actifs, valeurs résumé |
| `textTertiary` | `#D3D3D3` (LightGray) | numéros de jour, nom du mois, labels résumé, onglets inactifs |
| `textOnSelected` | `#FFFFFF` | texte onglet/nav actif |
| `accentText` (lightBlue) | `#4FC3F7` | titres TitledDivider ("Month Completion", "Calendar"), tint icône "Up next" |
| `divider` (GrayBlue) | `#5E78A0` | lignes des TitledDivider |
| `dividerStrong` (firstBlue) | `#153A6B` | séparateur entre la barre d'onglets top et la sous-barre |
| `lightGrayBlue` | `#7B9DD0` | lettres d'en-tête des jours (M T W T F S S) |
| **Couleurs d'état (primitives, glyphes du calendrier)** | | |
| `mediumGreen` | `#008444` | check "Completed", icône résumé "Done" |
| `redMedium` | `#B3403E` | croix "Skipped/Uncompleted", "-" Missed, portion rouge de la barre (<20%) |
| `orangeMedium` | `#C4841F` | flèche "In progress / Planned", icône résumé "Streak" (fire) |
| `darkOrange` | `#9D5300` | tint du "-" Missed (`ic_check_indeterminate_small`) + fallback |
| `blueMedium` | `#245682` | lune "Rest day" |

> ⚠️ Nuance icônes du screenshot : la croix rouge "missed planned" (le petit "-") est rendue en `darkOrange` (`#9D5300`) dans le code (`CalendarDay.kt` ligne `isMissedPlanned -> StatusIcon(..., darkOrange)`), tandis que la légende le liste en `redMedium`. Sur le screenshot le "-" au-dessus des jours 1, 3, 10, 11, 13 apparaît orange foncé → suivre le code (`darkOrange`).

---

## 1. Layout écran (haut → bas)

Largeur de contenu utile : la `Column` racine de `CalendarViewScreen` applique `padding(horizontal = 18.dp)` → contenu sur **376 dp** de large, centré. Le chrome (StatusBar, onglets, BottomNav) est pleine largeur **412 dp**.

| # | Section | Composant Compose (fichier) | Données / props visibles | Hauteur ≈ (dp) |
|---|---|---|---|---|
| 0 | **StatusBar Android** (shell système, hors Compose app) | mock système | `15:35` à gauche ; icônes droite : clé VPN, son coupé, wifi, signal, batterie `96`. Fond noir `#000000`, texte/icônes blancs. | **32** (rendu ~24-32, le screenshot montre une barre noire pleine largeur) |
| 1 | **Barre onglets TOP** (chrome Home) | `TabRowCustom` via `DualTabMenu` (`designsystem/common_components/DualTabMenu.kt`) | 3 onglets pleine largeur, poids égal : `Session` / `Goals` / **`Program`** (actif). Actif = fond `selectedFill` `#153A6B`, texte blanc SemiBold ; inactifs = fond `bgBottomNav` `#0F1C26`, texte `textTertiary` Normal. Fond global de la barre = `bgBottomNav`. | **42** |
| 1b | Séparateur | `HorizontalDivider` (dans `DualTabMenu`) | couleur `dividerStrong` `#153A6B`, épaisseur **1.5dp**. | 1.5 |
| 2 | **Sous-barre onglets** (chrome Program) | `TabRowCustom` (`isSubRow = true`) via `DualTabMenu` | 2 onglets poids égal : `Week` / **`Calendar`** (actif). Actif = fond `selectedFill` à **alpha 0.75** (`#153A6B` @75%), texte blanc SemiBold. Inactif = fond `bgBottomNav` @alpha 0.5, texte `textTertiary` @alpha 0.8 Normal. | **40** |
| — | *(début du contenu `CalendarViewScreen`, padding horizontal 18dp)* | | | |
| 3 | Spacer | `Spacer(height=8.dp)` | — | 8 |
| 4 | **TitledDivider "Month Completion"** | `TitledDivider` (`designsystem/common_components/TitledDivider.kt`) | Texte centré `Month Completion` couleur `accentText` `#4FC3F7` SemiBold, encadré de 2 `HorizontalDivider` (couleur `divider` `#5E78A0`) à poids égal de part et d'autre. Padding vertical 6dp. | ~28 (texte ~16 + pad 12) |
| 5 | **Barre de progression mensuelle** | `MonthViewProgressBar` (`feature/calendar/.../MonthViewProgressBar.kt`) → `LabeledProgressBar` + `ActionIconButton` info | Row alignée centre, padding vertical 8dp : (a) `ProgressBarPrimitive` weight 1f, hauteur **7dp**, coins 2dp, trough `bgRecessed`, remplissage = `progressColor(progress)`. À 13% (screenshot) la portion remplie est **rouge `redMedium`** (`<0.20`), reste = trough. (b) Spacer 12dp. (c) Label `13%` largeur min 48dp, hauteur 40dp, texte **14sp SemiBold** couleur = `progressColor` (rouge `#B3403E` ici). (d) Spacer 12dp. (e) `ActionIconButton` (icône `ic_rounded_info`, 24dp dans box 40dp, fond `bgButton` `#1E2A3C`, coins small) → ouvre la légende. | **40** (hauteur de la box % / bouton) |
| 6 | **Rangée résumé (3 cartes)** | `CalendarSummaryRow` (`feature/calendar/.../CalendarSummaryRow.kt`) → `SummaryRow` + 3× `SummaryItem` (compact) | Row pleine largeur, padding vertical 12dp, gap 8dp, 3 cartes à poids égal. Chaque carte = `SummaryItem(compact=true)` : fond `bgRecessed` `#091216`, coins small, padding h12/v10 ; icône **24dp** tintée + Spacer 8dp + Column (value 13sp SemiBold blanc / label 12sp `textTertiary`). **Contenu (code actuel)** : ① `ic_rounded_local_fire` tint `orangeMedium` — value `"0 weeks"`, label `Streak`. ② `ic_rounded_check_circle` tint `mediumGreen` — value `"3 days"`, label `Done`. ③ `ic_calendar_month` tint `accentText` — value = prochaine séance `"d MMM"` (ou `—`), label `Up next`. | ~72 (icône 24 + pad 20 + vpad 24) |
| 7 | **TitledDivider "Calendar"** | `TitledDivider` | identique à #4, texte `Calendar`. | ~28 |
| 8 | Spacer | `Spacer(height=4.dp)` | — | 4 |
| 9 | **Conteneur calendrier (carte)** | `Box` (dans `CalendarViewScreen`) | `Box.fillMaxWidth().background(bgRecessed, shape=shapes.small).padding(vertical=12, horizontal=8)`. Coins arrondis "small" (≈8dp). Contient le sous-bloc 9a→9d. | variable (≈420-460 selon nb de rangées) |
| 9a | **Header mois** (dans le conteneur) | `Row` (dans `CalendarViewScreen`, ligne ~101) | `SpaceBetween`, vertical centré, padding bottom 8dp. Gauche : `ActionIconButton(ic_arrow_left_alt)` (box 40dp fond `bgButton`, icône blanche) → mois précédent. Centre : `Text` `"mai 2026"` = `month.getDisplayName(FULL, locale) + " " + year`, **16sp Medium** couleur `textTertiary`. Droite : `ActionIconButton(ic_arrow_right_alt)` → mois suivant. | ~48 (boutons 40 + pad) |
| 9b | Spacer | `Spacer(height=12.dp)` | — | 12 |
| 9c | **En-tête des jours (lettres)** | `Row` (ligne ~128) | `SpaceBetween`, 7 `Text` à poids égal, centrés : `M T W T F S S` (Monday-first, depuis strings `weekday_short_*`). **16sp** couleur `lightGrayBlue` `#7B9DD0`. | ~22 |
| 9d | Spacer | `Spacer(height=20.dp)` | — | 20 |
| 9e | **Grille du mois** | `CalendarMonthGrid` (`designsystem/common_components/CalendarMonthGrid.kt`) + cellules `CalendarDay` | `LazyVerticalGrid` 7 colonnes fixes, espacement **6dp** h ET v. `firstDayOffset` = `(jour1.dayOfWeek + 6) % 7` (Monday-first) → cellules vides (Spacer) en tête. `cellSize = largeurDispo / 7` (≈ (376 − 16 padding − 6×6 gaps)/7 ≈ **46-48dp** carré). Chaque jour = `CalendarDay` (voir §détail cellule). Mois affiché : mai 2026, 1er mai = vendredi → 4 cellules vides (Mon-Thu) puis 1..31. | ~380-420 (5-6 rangées × ~64 + gaps) |
| — | *(fin contenu, fin Column racine)* | | | |
| 10 | **BottomNavBar** (chrome shell) | `BottomNavBar` (`designsystem/bottomNavigationBar/BottomNavBar.kt`) | `NavigationBar` fond `bgBottomNav` `#0F1C26`, shadow elev 24dp. 5 items poids égal : ① **Menu** (`ic_menu` hamburger) — porte des badges overlay (voir Notes), ② **Calendrier** (`ic_calendar_month`), ③ **Accueil** (`ic_home`) = **actif** (fond `selectedFill` `#153A6B`, icône agrandie 38dp couleur `textOnSelected` blanc, coins small), ④ **Chrono** (`ic_timer`), ⑤ **Stats** (`ic_rounded_monitoring`). Icônes inactives 28dp tint `textTertiary`. | **52** (zone visuelle ; le composant déclare height 100 + windowInsets, mais la barre visible ≈52) |
| 11 | **SystemNav Android** (shell système) | mock système | Barre de navigation gestuelle/3-boutons : `⋮⋮⋮` (récents) / `○` (home) / `‹` (back). Fond noir, glyphes gris clair. | **48** |

**Total vertical** : 32 (status) + 42 + 1.5 + 40 (chrome onglets) + contenu (~600-640) + 52 (nav) + 48 (sysnav) ≈ **916 dp** (cohérent avec la frame S21+). Le contenu est non-scrollable dans ce design (tout tient à l'écran) — la grille `LazyVerticalGrid` peut techniquement scroller si débordement.

### Détail de la cellule `CalendarDay` (`feature/calendar/.../CalendarDay.kt`)

Carré de `cellSize` (≈46-48dp), `clip(shapes.small)`, **bordure 1dp** `primaryAction` `#2377CA` SI `isToday` sinon transparente, clickable. Column centrée verticalement, 2 zones empilées :

1. **Bande d'icônes** (`Box` height **18dp**, centré) — `Row` SpaceEvenly contenant, dans l'ordre :
   - **Cloud (optionnel, à gauche)** : si `isSynced` (actual existe + synced) → `ic_cloud_done` tint `primaryAction` `#2377CA` ; sinon si `showCloudOff` (actual existe + non synced) → `ic_cloud_off` tint `textTertiary`.
   - **Glyphe d'état (1 seul, priorité `when`)** :
     | État | Drawable | Tint | Condition |
     |---|---|---|---|
     | Rest day | `ic_rounded_bedtime` (lune) | `blueMedium` `#245682` | pas d'actual + planned rest/vide |
     | Missed planned | `ic_check_indeterminate_small` ("-") | `darkOrange` `#9D5300` | pas d'actual + passé + planned a une séance |
     | Completed | `ic_rounded_check` | `mediumGreen` `#008444` | actual done |
     | Skipped | `ic_rounded_close` (croix) | `redMedium` `#B3403E` | actual non-done passé, OU actual non-done aujourd'hui+rest |
     | In progress | `ic_arrow_progress` (flèche cible) | `orangeMedium` `#C4841F` | futur/aujourd'hui avec planned non fait |
     | (fallback) | `ic_arrow_progress` | `darkOrange` | aucun cas |
   - `StatusIcon` = icône 16dp dans box 16dp (`designsystem/common_components/StatusIcon.kt`).
2. **Numéro du jour** : `Text(dayOfMonth)` **16sp Medium** couleur `textTertiary` `#D3D3D3`.

> Sur le screenshot, **le jour 15 est "aujourd'hui"** : bordure bleue `primaryAction`, et porte cloud-off (gris) + flèche in-progress orange. Voir §4 pour la carte complète des états visibles.

---

## 2. Interactions (flow map)

- **tap onglet `Session`** (top) → bascule `selectedTopTab=0` → affiche SessionTab/NoSessionFallback (autre écran, hors scope).
- **tap onglet `Goals`** (top) → `selectedTopTab=1` → GoalsTabContent (hors scope).
- **tap onglet `Program`** (top, actif) → `selectedTopTab=2` (reste sur Program).
- **tap sous-onglet `Week`** → `selectedSubTab=0` → WeekViewScreen (hors scope).
- **tap sous-onglet `Calendar`** (actif) → `selectedSubTab=1` → CalendarViewScreen (cet écran).
- **tap bouton info `ⓘ`** (à droite de la barre de progression) → ouvre **CalendarLegendBottomSheet** (légende des icônes/couleurs).
- **tap flèche `←`** (header mois) → `viewModel.setMonth(mois précédent)` (recompose la grille, pas de nav).
- **tap flèche `→`** (header mois) → `viewModel.setMonth(mois suivant)`.
- **tap jour AVEC séance (`aw != null`)** → navigation `Routes.session(aw.uuid)` → **écran Session** (détail/édition de la séance de ce jour). *(NB : un jour rouge "skipped" ou "completed" porte un actual → ce tap NAVIGUE vers la séance ; un jour "missed planned" ou "rest day" n'a PAS d'actual → ouvre le sheet ci-dessous.)*
- **tap jour SANS séance (`aw == null`)** → ouvre **DayOptionsBottomSheet** (choix de créer une séance pour cette date).
  - dans le sheet, **tap "Add new actual workout"** → ferme le sheet → ouvre **CreateActualWorkoutDialog** (saisie du nom) → à la validation, crée la séance et navigue `Routes.session(uuid)`.
  - dans le sheet, **tap "Add from planned"** → ferme le sheet → `startActualWorkoutFromPlannedOnDate(date)` (copie le planned du jour en actual + exercices + sets) → navigue `Routes.session(createdUuid)` (ou snackbar info si rien de planné / Rest Day).
- **tap item BottomNav `Menu`** → ouvre le Drawer (`onMenuClicked`).
- **tap item BottomNav `Calendrier`/`Chrono`/`Stats`** → `navController.navigate(route)` (écrans dédiés).
- **tap item BottomNav `Accueil`** (actif) → no-op (déjà sur Home).
- **BackHandler** : si Drawer ouvert → ferme le drawer ; sinon (au niveau Home) → dialog de sortie de l'app (`ConfirmationDialog`, géré par HomeScreen).

---

## 3. Inventaire dialogs & bottom-sheets

### 3.1 CalendarLegendBottomSheet
- **Fichier** : `feature/calendar/ui/components/calendarScreen/CalendarLegendBottomSheet.kt`
- **But** : expliquer la signification des icônes/couleurs des jours.
- **Shell** : `AppBottomSheet` (ModalBottomSheet M3, fond `bgScreen` `#101720`, drag handle M3 par défaut en haut), system bars sombres.
- **Contenu** : `TitledDivider("Legend")` (texte `accentText`) + Spacer 12dp + grille de cellules (`BoxWithConstraints` → **3 colonnes** si largeur ≥ 360dp, sinon 2). Espacement 12dp h et v. 6 entrées (`LegendEntry`), chaque cellule = Row sur fond `bgRecessed` `#091216` coins small, padding h10/v8, gap 8dp : icône **18dp** tintée + label 13sp `textTertiary` 1 ligne.
  | Icône | Tint | Label |
  |---|---|---|
  | `ic_rounded_check` | `mediumGreen` | Completed |
  | `ic_rounded_bedtime` | `blueMedium` | Rest Day |
  | `ic_rounded_close` | `redMedium` | Uncompleted |
  | `ic_arrow_progress` | `orangeMedium` | Planned |
  | `ic_cloud_done` | `primaryAction` | Synced |
  | `ic_check_indeterminate_small` | `redMedium` | Missed |
- Bottom padding 12dp + Spacer final 6dp.

### 3.2 DayOptionsBottomSheet
- **Fichier** : `feature/calendar/ui/components/calendarScreen/DayOptionsBottomSheet.kt`
- **But** : sur tap d'un jour sans séance, proposer 2 actions de création.
- **Shell** : `OptionsBottomSheet` (`designsystem/common_components/OptionsBottomSheet.kt`) — ModalBottomSheet M3, fond `bgScreen`.
- **Contenu** : `TitledDivider("Day Options")` + Spacer 10dp + liste de `OptionRow` (espacement 10dp). Chaque `OptionRow` = Row pleine largeur fond `bgRecessed` coins 8dp, padding h12/v10, SpaceBetween : label 14sp `textPrimary` (weight 1f) à gauche + `ActionIconButton` à droite (icône dans box 40dp coloré).
  | Label | Icône | Couleur fond bouton |
  |---|---|---|
  | `Add new actual workout` | `ic_add` | `primaryAction` `#2377CA` |
  | `Add from planned` | `ic_rounded_add_link` | `selectedFill` `#153A6B` |

### 3.3 CreateActualWorkoutDialog
- **Fichier** : `feature/session/ui/components/sessionTab/CreateActualWorkoutDialog.kt` (réutilisé par le calendrier).
- **But** : saisir le nom d'une nouvelle séance (après "Add new actual workout").
- **Shell** : `FormDialog` (`designsystem/common_components/FormDialog.kt`) — `AlertDialog` M3, fond `bgScreen`.
- **Contenu** : titre `Start a session` (couleur `primaryAction`) ; champ `CustomTextField` placeholder `Session name` ; message d'erreur rouge `redMedium` conditionnel (`Name cannot be empty.` si vide / `"Rest Day" is not allowed.` si == Rest Day). Boutons : `Cancel` (`textTertiary`) + `Start` (`primaryAction`, désactivé/gris si nom invalide).

---

## 4. Notes (états / variantes / conditions)

### 4.1 ⚠️ Discordance screenshot ↔ code (3e carte résumé)
Le **screenshot** montre la 3e carte résumé = `ic_rounded_bedtime` (lune bleue) + `"3 days"` / `Rest`. Le **code actuel** (`CalendarSummaryRow.kt`) a remplacé cette carte par `ic_calendar_month` + prochaine séance (`d MMM` ou `—`) / **`Up next`**. De même la 1re carte du screenshot dit `0 weeks` / `Streak` (cohérent code) et la 2e `3 days` / `Done` (cohérent code).
→ **Décision pour le mockup** : suivre le **CODE** (Streak / Done / **Up next**), car la spec doit refléter l'état réel de l'app. Mentionner la variante "Rest" comme historique si une 2e frame est souhaitée. Les chaînes de strings `calendar_summary_perfect_weeks` / `calendar_summary_rest_days` existent encore mais ne sont plus câblées dans la rangée actuelle.

### 4.2 États visuels d'un jour (combinaisons d'icônes) — visibles sur le screenshot
Un jour combine **0-1 cloud** (en haut-gauche de la bande) + **1 glyphe d'état**. États observés mai 2026 :
- **Aujourd'hui (15)** : bordure bleue `primaryAction` autour de la cellule + `ic_cloud_off` (gris) + `ic_arrow_progress` (orange). → planned non terminé aujourd'hui.
- **Complété & synced (4, 5, 6)** : `ic_cloud_done` (bleu) + `ic_rounded_check` (vert).
- **Rest day (2, 7, 9, 16, 21, 23, 28, 30)** : `ic_rounded_bedtime` (lune `blueMedium`), aucun cloud.
- **Skipped/uncompleted passé avec actual (8, 12, 14)** : `ic_cloud_off` ou `ic_cloud_done` (selon synced) + `ic_rounded_close` (croix rouge).
- **Missed planned passé (1, 3, 10, 11, 13)** : `ic_check_indeterminate_small` ("-") tint `darkOrange`, aucun cloud (pas d'actual).
- **Planned futur (17, 18, 19, 20, 22, 24-27, 29, 31)** : `ic_arrow_progress` (flèche cible orange `orangeMedium`), aucun cloud.

### 4.3 Cellules vides en tête de mois
mai 2026 commence un **vendredi** → `firstDayOffset = 4` → **4 cellules vides** (colonnes Mon, Tue, Wed, Thu de la 1re rangée) rendues comme `Spacer` de `cellSize`. Le jour `1` se place sous la colonne `F` (vendredi). Cohérent avec le screenshot.

### 4.4 Barre de progression — couleur par seuil (`progressColor`)
`>=1.0` → `primaryAction` (bleu) · `>=0.75` → `mediumGreen` · `>=0.5` → `lightGreen` · `>=0.2` → `orangeMedium` · `<0.2` → `redMedium` (rouge). À **13%** (screenshot) = rouge, petit segment rempli à gauche + label `13%` rouge. Le `%` affiché = `(progress*100).toInt()`.

### 4.5 Convention Monday-first
Toute la grille et l'en-tête des jours sont en semaine **Lundi → Dimanche** (`M T W T F S S`). Les deux derniers `S` = Saturday et Sunday (mêmes lettres). C'est codé en dur via les strings `weekday_short_*` et l'offset `(dayOfWeek + 6) % 7`.

### 4.6 BottomNav — badges overlay sur l'item "Menu" (gauche)
L'item Menu porte jusqu'à 4 badges en overlay (positions coin) : sync-state (haut-gauche, bleu `cloud_done` si tout synced / orange `cloud_off` si pending), WS-state (bas-gauche, vert si connecté / orange sinon), unread notifications (haut-droite, bleu, si >0), offline (bas-droite, orange wifi-off, si hors-ligne). **Sur le screenshot** : on distingue en bas-gauche de la zone Menu deux petites icônes (cloud bleu = synced + router/wifi orange ou vert). Pour le mockup : représenter au moins le badge sync (bleu, haut-gauche) + WS (bas-gauche). Ces badges sont indépendants de la taille de l'icône (rendus unbounded).

### 4.7 Empty states
- **Aucune séance planifiée du mois** → barre de progression à `0%` (rouge, segment quasi nul) ; tous les jours ouvrables sans planned n'affichent pas de flèche (fallback rare). Pas de message vide dédié : la grille reste affichée.
- **Pas de prochaine séance dans 7 jours** → 3e carte résumé value = `—` (`calendar_summary_next_workout_none`).

### 4.8 Light theme (variante optionnelle)
Si une frame light est voulue : `bgScreen` `#FFFFFF`, `bgRecessed` `#E1E7EF`, `bgBottomNav` `#F2F5F9`, `selectedFill` `#D3E4F7`, `textPrimary` `#1A2330`, `textTertiary` `#9AA3B0`, `accentText` `#1F86C4`. Les couleurs d'état (vert/rouge/orange/lune) + `primaryAction` `#2377CA` restent identiques. (cf. `appColorsLight`).

### 4.9 demoHighlight
Le header mois (`"calendar.header"`) et la grille (`"calendar.grid"`) portent un `demoHighlight` (overlay du tour démo). **Invisible** hors mode tour → à ignorer pour le mockup.
