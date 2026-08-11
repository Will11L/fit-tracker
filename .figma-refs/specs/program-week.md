# SPEC D'ASSEMBLAGE FIGMA — Program / Week (Home onglet 2 sub 0)

> Écran : `WeekViewScreen` — onglet **Program → Week** du Home. Frame cible **412×916 dp** (Samsung S21+).
> Source code : `appli-android/app/src/main/java/com/example/sportapp/feature/planning/ui/WeekViewScreen.kt`
> Référence visuelle : `.figma-refs/Screenshots/05_home_program_week.jpg`
> Thème de référence du screenshot = **DARK** (`appColorsDark`). Tokens couleur ci-dessous en dark.

## Palette (tokens → hex, dark)

| Token sémantique | Primitive | Hex | Usage dans cet écran |
|---|---|---|---|
| `bgScreen` | blueBackground | `#101720` | fond global écran |
| `bgSurface` | boxBlue | `#1E2A3C` | fond box "jour" (pill), fond bouton 3-points header |
| `bgRecessed` | thirdBlue | `#091216` | fond des Card jour |
| `bgBottomNav` | secondBlue | `#0F1C26` | fond DualTabMenu + BottomNavBar |
| `bgButton` | boxBlue | `#1E2A3C` | fond carré ActionIconButton (3-points) |
| `selectedFill` | firstBlue | `#153A6B` | onglet actif (Program / Week), item Home actif |
| `primaryAction` | ButtonPrimaryColor | `#2377CA` | titres dialogs, % à 100%, label session |
| `textPrimary` | White | `#FFFFFF` | jour, onglet actif |
| `textTertiary` | LightGray | `#D3D3D3` | onglets inactifs, "Rest Day", cloud-off |
| `divider` | GrayBlue | `#5E78A0` | lignes TitledDivider |
| `dividerStrong` | firstBlue | `#153A6B` | séparateur entre top-tabs et sub-tabs |
| blueMedium | — | `#245682` | bordure "today", flèche →, icône cloud-done, icône arrow-progress, lune Rest |
| redDark | — | `#7A2E2C` | icône autoplay (séance prévue non commencée) |
| darkOrange | — | `#9D5300` | icône construction (séance "building"), label en building |
| mediumGreen | — | `#008444` | icône check (séance complétée) |
| redMedium | — | `#B3403E` | label "%" rouge (progress < 20%, cf. `progressColor`) |
| orangeMedium | — | `#C4841F` | badge offline (chrome) |

`progressColor(value)` (seuils, utilisé pour la barre ET le %) : `>=1` primaryAction `#2377CA` · `>=0.75` mediumGreen · `>=0.5` lightGreen `#00D572` · `>=0.2` orangeMedium · `else` redMedium `#B3403E`. **Sur le screenshot tout est à 0% → couleur = redMedium `#B3403E`** (texte "0%" rouge + barre remplie 0).

---

## 1. Layout écran (haut → bas)

Largeur contenu = frame 412 − padding horizontal 18dp × 2 = **376dp utiles** (sauf chrome qui est pleine largeur). Fond global `bgScreen #101720`.

### A. StatusBar (chrome système) — ~32dp
- Pleine largeur, fond noir `#000000`.
- Gauche : heure `15:35` (blanc, gras) + 2 petites icônes (screen-record / check).
- Droite : icônes système (clé VPN, son coupé, wifi, signal, batterie `96`).
- *Note : mock système, pas un composant app.*

### B. DualTabMenu — composant `DualTabMenu.kt` — ~83dp (42 + 1.5 divider + 40)
Fond `bgBottomNav #0F1C26`, pleine largeur. Deux rangées via `TabRowCustom`.
- **Top row (42dp)** : 3 onglets équirépartis `Session` · `Goals` · `Program`.
  - `Program` = **actif** → fond `selectedFill #153A6B`, texte blanc **gras**.
  - `Session`, `Goals` = inactifs → fond transparent (sur bgBottomNav), texte `textTertiary #D3D3D3`, normal.
- **Divider** : `HorizontalDivider` couleur `dividerStrong #153A6B`, épaisseur 1.5dp.
- **Sub row (40dp)** : 2 onglets `Week` · `Calendar`.
  - `Week` = **actif** → fond `selectedFill #153A6B`, texte blanc gras.
  - `Calendar` = inactif → texte `textTertiary`.
- *Note : l'onglet actif occupe ~50% (top: ~33% chacun ; sub: 50% chacun). Coins légèrement arrondis sur la pastille active (shapes.small).* 

### C. Contenu scrollable (zone WeekViewScreen) — entre B et G
Padding horizontal 18dp. Spacer 8dp en haut.

#### C1. TitledDivider "Week Completion" — `TitledDivider.kt` — ~28dp (padding vertical 6dp + texte)
Ligne — **Week Completion** — ligne. Lignes couleur `divider #5E78A0`, texte centré couleur `divider`, gras (SemiBold), padding horizontal 8dp autour du texte.

#### C2. WeekViewProgressBar — `WeekViewScreen.kt::WeekViewProgressBar` (wrap `LabeledProgressBar.kt`) — ~56dp
Row pleine largeur, padding vertical 8dp, centré verticalement :
- **Barre** (`ProgressBarPrimitive`) : `weight(1f)`, hauteur **7dp**, coins 2dp. Trough `bgRecessed #091216`, remplissage = `progressColor(progress)`. **Screenshot : 0% → trough visible (barre vide), couleur de remplissage redMedium mais largeur 0.** *(NB visuel screenshot : la trough apparaît bleu clair/gris assez claire et large — rendre la trough bien visible.)*
- Spacer 12dp.
- **% label** : box `widthIn(min 48dp)`, hauteur 40dp, texte `"0%"` 14sp SemiBold, couleur `progressColor` = **redMedium `#B3403E`** (rouge) à 0%.
- Spacer 12dp.
- **rightContent** : carré 40×40dp, `clip(shapes.small)`, fond `bgSurface #1E2A3C`, icône 3-points verticaux (`ic_rounded_more_vert`) centrée, tint `textPrimary` blanc. → ouvre WeekCompletionBottomSheet.

#### C3. TitledDivider "Days" — `TitledDivider.kt` — ~28dp
Identique à C1, texte **Days**. (Spacers 4dp avant/après autour de ce divider.)

#### C4. LazyColumn des jours — `WeekViewScreen.kt` (itemsIndexed sur `completePlannedList`)
Liste de **7 cartes** (Monday → Sunday), une par jour de la semaine. Chaque jour sans PlannedWorkout en DB devient un filler "Rest Day". Padding vertical 4dp entre cartes. Padding bottom 10dp en fin de liste.

**Card jour (`Card`, RoundedCornerShape 6dp, fond `bgRecessed #091216`)** — hauteur ~108–120dp selon contenu :
- Si **aujourd'hui** (`isToday(dayOfWeek)`) → **bordure 1.5dp `blueMedium #245682`** autour de la carte (cf. screenshot : **Friday** est encadré en bleu).
- Padding interne 12dp. `Row` (vertical center) :
  - **Colonne gauche (`weight(1f)`)** :
    - **Row haut** (vertical center) :
      - **Pill jour** : box fond `bgSurface #1E2A3C`, coins 6dp, padding (h 12dp, v 6dp). Texte = nom du jour localisé (`localizedDayOfWeek`), `textPrimary` blanc, 14sp. Ex : `Monday`, `Tuesday`…
      - Spacer 8dp.
      - **Si NON filler** : 2 icônes inline (`ActionIconButton`, sans fond, taille box 40dp / icône 24dp) :
        1. **Icône statut** (selon `calcDayProgressResult`) — une seule des 4 :
           - `isBuilding` (séance planifiée mais 0 sets prévus) → `ic_rounded_construction`, tint **darkOrange `#9D5300`**.
           - `completed` → `ic_rounded_check`, tint **mediumGreen `#008444`**.
           - `hasActual` (commencée, en cours) → `ic_arrow_progress`, tint **blueMedium `#245682`**.
           - sinon (prévue, pas encore commencée) → `ic_rounded_autoplay`, tint **redDark `#7A2E2C`**.
        2. **Icône sync cloud** : `ic_cloud_done` si `synced` (tint blueMedium `#245682`, non cliquable) sinon `ic_cloud_off` (tint `textTertiary`, cliquable → relance sync).
      - **Si filler ("Rest Day")** : à la place des 2 icônes → `ActionIconWithTextButton` : icône `ic_rounded_bedtime` (lune) + texte `"Zzz..."`, les deux en **blueMedium `#245682`**, sans fond.
    - **PlannedDayProgressBar** (`PlannedDayProgressBar.kt`) — sous la row, padding (start 6, top 6, end 18) :
      - Row align bottom. **Label** = nom de la séance (`localizedDayOfWeek(name)`, mais nom user-typed reste tel quel), 14sp, couleur :
        - filler → `textTertiary` (ex. **"Rest Day"** gris).
        - building → darkOrange `#9D5300`.
        - sinon → `primaryAction #2377CA` (bleu) — ex. **"Push Day"**, **"Pull Day"**, **"Leg Day"**, **"Core & Mobility"** en bleu.
      - Gap 24dp.
      - **Barre + %** affichés UNIQUEMENT si `!isFiller && !isBuilding` (`showProgressBar`) :
        - `ProgressBarPrimitive` `weight(1f)`, hauteur 7dp, trough `bgSurface #1E2A3C` (override car carte = bgRecessed), remplissage `progressColor`.
        - Spacer 8dp + texte `"<percent>%"` 13sp SemiBold, couleur `progressColor`. **Screenshot : "0%" rouge redMedium.**
      - *Donc pour Rest Day / Thursday : pas de barre ni %, juste le label "Rest Day" gris.*
  - **Colonne droite (`Row`, spacedBy 4dp, vertical center)** :
    - **ActionIconButton 3-points** : `ic_rounded_more_vert`, AVEC fond (`bgButton #1E2A3C`), box 40dp, icône blanche. → ouvre WeekSessionOptionsBottomSheet pour ce jour.
    - **Si NON filler** : **ActionIconButton flèche →** : `ic_arrow_right_alt`, fond personnalisé **blueMedium `#245682`**, icône blanche, box 40dp. → navigue vers PlannedWorkoutScreen du jour.
    - *Si filler (Rest Day) : pas de flèche → (seul le 3-points est présent, cf. Thursday du screenshot).*

**Mapping screenshot ↔ états (5 jours visibles) :**
| Jour | Pill | Label session | Icône statut | Cloud | Barre/% | Flèche → | Encadré bleu |
|---|---|---|---|---|---|---|---|
| Monday | Monday | Push Day (bleu) | autoplay rouge (redDark) | cloud-done bleu | 0% rouge | oui | non |
| Tuesday | Tuesday | Pull Day (bleu) | arrow-progress/dashed-up (blueMedium) | cloud-done bleu | 0% rouge | oui | non |
| Wednesday | Wednesday | Leg Day (bleu) | autoplay rouge (redDark) | cloud-done bleu | 0% rouge | oui | non |
| Thursday | Thursday | Rest Day (gris) | — (lune + "Zzz...") | — | — (aucune) | non | non |
| Friday | Friday | Core & Mobility (bleu) | arrow-progress/dashed-up (blueMedium) | cloud-done bleu | 0% rouge | oui | **OUI (today)** |

> *(Saturday/Sunday existent dans la liste mais hors-écran sous le fold — probablement 2 cartes "Rest Day" supplémentaires.)*

### G. BottomNavBar (chrome) — `BottomNavBar.kt` — ~52dp (+ SystemNav 48dp)
`NavigationBar` fond `bgBottomNav #0F1C26`, ombre élévation 24dp, pleine largeur. 5 items équirépartis (icône seule, pas de label) :
1. **Menu** (`ic_menu`, burger) — inactif, gris. Porte les **badges overlay** (cf. screenshot, coin bas-gauche de l'écran au-dessus de la nav) : sync-state (haut-gauche : cloud bleu `primaryAction` si OK), WS-state (bas-gauche : router **vert `#008444`** si connecté sinon orange), offline (bas-droite : wifi-off orange si déconnecté). Sur le screenshot on voit en bas-gauche un petit **cloud bleu** + un **router vert**.
2. **Calendrier** (`ic_calendar_month`) — gris inactif.
3. **Accueil/Home** (`ic_home`) — **ACTIF** → fond `selectedFill #153A6B` (pastille), icône blanche agrandie (38dp vs 28dp).
4. **Chrono** (`ic_timer`) — gris inactif.
5. **Stats** (`ic_rounded_monitoring`) — gris inactif.
- Icône active 38dp / inactives 28dp. Couleur active `textOnSelected` blanc / inactives `textTertiary` gris.

### H. SystemNav (chrome système) — ~48dp
Barre de navigation Android (gestures Samsung) : fond noir, 3 boutons (récents `|||`, home `○`, retour `‹`). Mock système.

---

## 2. Interactions (flow map)

Chrome / onglets :
- tap **onglet "Session"** (top) → switch vers Home/Session (autre écran, hors scope).
- tap **onglet "Goals"** (top) → switch vers Home/Goals (hors scope).
- tap **onglet "Program"** (top, déjà actif) → reste sur Program.
- tap **sub-onglet "Week"** (déjà actif) → reste WeekViewScreen.
- tap **sub-onglet "Calendar"** → switch vers vue Calendar du Program (hors scope).

Header Week Completion :
- tap **bouton 3-points (carré 40dp à droite de la barre globale)** → ouvre **WeekCompletionBottomSheet** (actions globales semaine).

Par carte jour :
- tap **icône cloud-off** (uniquement si non synced) → `syncAllPlannedWorkouts()` (relance la sync, pas de sheet/dialog ; redevient cloud-done). *cloud-done = non cliquable.*
- tap **3-points (colonne droite, avec fond)** → ouvre **WeekSessionOptionsBottomSheet** pour ce jour (rename/done/duplicate/delete OU "plan a workout" si filler).
- tap **flèche → (bleue, colonne droite)** → navigue vers **PlannedWorkoutScreen** du jour (`Routes.plannedWorkout(uuid)`) — édition des exercices planifiés.
- *(icône statut + pill jour = non cliquables, purement informatives.)*

Depuis WeekSessionOptionsBottomSheet (séance existante) :
- tap **"Rename planned workout"** → ouvre **dialog Rename** (AlertDialog).
- tap **"Mark as Done" / "Mark as Undone"** → `toggleDoneForPlannedWorkout()` (toggle l'actual du jour ; ferme la sheet).
- tap **"Duplicate planned workout"** → ouvre **CopyPlannedWorkoutDialog** (choix jour cible).
- tap **"Delete planned workout"** → `deletePlannedWorkout()` (suppression directe ; ferme la sheet).

Depuis WeekSessionOptionsBottomSheet (filler "Rest Day") :
- tap **"Plan a workout for this day"** → ouvre **CreatePlannedWorkoutDialog** (créer une séance pour ce jour).

Depuis WeekCompletionBottomSheet :
- tap **"Sync sessions"** → `syncAllPlannedWorkouts()`.
- tap **"Mark sessions as Done"** → `markAllActualWorkoutsAsDone()`.
- tap **"Mark sessions as Undone"** → `markAllActualWorkoutsAsUndone()`.

Dialogs :
- **Rename dialog** : tap "Rename" → renomme (refus si nom = "Rest Day" → snackbar info) ; tap "Cancel" → ferme.
- **CreatePlannedWorkoutDialog** : tap "Create" → crée la séance (si le jour a déjà une séance → ouvre dialog de remplacement `showReplaceDialog`) ; refus si nom = "Rest Day".
- **CopyPlannedWorkoutDialog** : tap "Copy" → copie vers le jour cible sélectionné ; tap "Cancel" → ferme.

BottomNav :
- tap **Menu** → ouvre le Drawer.
- tap **Calendrier / Chrono / Stats** → navigue vers l'écran correspondant.
- tap **Home** (actif) → reste / re-navigue Home.

---

## 3. Inventaire dialogs & bottom-sheets

### Bottom-sheets (toutes via `OptionsBottomSheet.kt` = `ModalBottomSheet`, fond `bgScreen #101720`, titre = `TitledDivider`, lignes d'action = `OptionRow` avec fond, icône + label coloré)

1. **WeekCompletionBottomSheet** — `weekViewScreen/WeekCompletionBottomSheet.kt`
   - But : actions globales sur la semaine. Titre : **"Week Completion Options"**.
   - 3 actions (icône / label / couleur) :
     - `ic_rounded_cloud_upload` — **"Sync sessions"** — primaryAction `#2377CA`.
     - `ic_rounded_check` — **"Mark sessions as Done"** — mediumGreen `#008444`.
     - `ic_rounded_close` — **"Mark sessions as Undone"** — orangeMedium `#C4841F`.

2. **WeekSessionOptionsBottomSheet** — `weekViewScreen/WeekSessionOptionsBottomSheet.kt`
   - But : options d'une séance d'un jour. Titre = **nom de la séance** (ex. "Push Day", ou "Rest Day").
   - **Variante filler (Rest Day)** : 1 seule action :
     - `ic_add` — **"Plan a workout for this day"** — `selectedFill #153A6B`.
   - **Variante séance existante** : 4 actions :
     - `ic_rounded_edit` — **"Rename planned workout"** — blueMedium `#245682`.
     - toggle done : si déjà done → `ic_check_indeterminate_small` **"Mark as Undone"** orangeMedium ; sinon `ic_rounded_check` **"Mark as Done"** mediumGreen.
     - `ic_rounded_content_copy` — **"Duplicate planned workout"** — `selectedFill #153A6B`.
     - `ic_rounded_delete_forever` — **"Delete planned workout"** — redMedium `#B3403E`.

### Dialogs (AlertDialog / FormDialog, fond `bgScreen #101720`, titre couleur `primaryAction`)

3. **Rename dialog** (inline dans `WeekViewScreen.kt`, `AlertDialog`)
   - But : renommer la séance. Titre **"Rename Session"** (primaryAction).
   - Corps : `CustomTextField` placeholder **"New session name"**.
   - Boutons : **"Rename"** (primaryAction) / **"Cancel"** (textTertiary).
   - Garde-fou : nom "Rest Day" interdit → snackbar info, ferme.

4. **CreatePlannedWorkoutDialog** — `weekViewScreen/CreatePlannedWorkoutDialog.kt` (`FormDialog`)
   - But : créer une séance pour un jour. Titre **"Plan a New Workout for <Day>"** (ex. "Plan a New Workout for Thursday").
   - Corps : `CustomTextField` placeholder **"Workout name"**.
   - Bouton confirm : **"Create"** (+ Cancel/Dismiss via FormDialog).

5. **CopyPlannedWorkoutDialog** — `weekViewScreen/CopyPlannedWorkoutDialog.kt` (`AlertDialog`)
   - But : dupliquer la séance vers un autre jour. Titre **"Copy Session"** (primaryAction, centré).
   - Corps : texte **"Choose the target day:"** + `SingleSelectDropdown` label **"Day"** (options = jours sauf le jour courant) + caption **"Current day: <Day>"** (textTertiary).
   - Boutons : **"Copy"** (primaryAction) / **"Cancel"** (textTertiary).

> *Replace dialog (`showReplaceDialog`) : déclaré dans le state mais **non rendu** dans le code actuel (pas d'UI associée) — à ignorer pour le mock.*

### Autres composants design-system référencés (pour Code Connect / cohérence)
- `LabeledProgressBar.kt`, `ProgressBarPrimitive.kt`, `PlannedDayProgressBar.kt`, `TitledDivider.kt`, `ActionIconButton.kt`, `ActionIconWithTextButton.kt`, `OptionsBottomSheet.kt` (+ `OptionRow`), `CustomTextField.kt`, `FormDialog.kt`, `SingleSelectDropdown.kt`, `DualTabMenu.kt` (+ `TabRowCustom`), `BottomNavBar.kt`.

---

## 4. Notes (états / variantes / empty states / UI conditionnelle)

- **Toujours 7 cartes** : la liste `completePlannedList` mappe les 7 jours ; tout jour sans PlannedWorkout en DB devient un **filler "Rest Day"** (pas d'empty state global — l'écran a toujours 7 lignes). Pas de "liste vide" possible.
- **Carte "today"** : seul le jour courant a la **bordure bleue 1.5dp `blueMedium`**. Le screenshot montre Friday encadré (donc capturé un vendredi). Pour le mock par défaut, encadrer la carte du jour courant.
- **4 états d'icône statut** mutuellement exclusifs (cf. tableau §1) : building (construction orange), completed (check vert), in-progress (arrow-progress bleu), planned-not-started (autoplay rouge). Filler → pas d'icône statut, remplacée par lune + "Zzz...".
- **Barre de progression conditionnelle** : masquée pour filler ET pour building (`showProgressBar = !isFiller && !isBuilding`). Donc Rest Day n'affiche ni barre ni %. Une séance "building" affiche le label en orange sans barre.
- **Couleur barre + %** suit `progressColor` par seuils (rouge <20%, orange 20-50%, vert clair 50-75%, vert 75-99%, bleu 100%). Le screenshot est intégralement à 0% → rouge `#B3403E`.
- **Trough override** : dans la carte (bgRecessed), la trough de la barre jour est forcée à `bgSurface #1E2A3C` pour rester visible ; la barre globale Week Completion (sur bgScreen) garde trough `bgRecessed #091216`.
- **Cloud sync** : cloud-done (bleu, non cliquable) vs cloud-off (gris, cliquable). Le screenshot montre toutes les séances en cloud-done bleu (= synced).
- **Flèche →** absente sur les filler (Rest Day) — seul le bouton 3-points subsiste à droite (cf. Thursday).
- **Thème clair** : tous les tokens basculent (cf. `appColorsLight`) — bgScreen/bgSurface blanc, textPrimary `#1A2330`, etc. Le mock de référence est en dark.
- **Badges BottomNav** (sur item Menu) : sync-state (toujours visible, cloud bleu si OK / orange+count si pending), WS-state (router vert/orange), unread notifs (mail bleu si >0), offline (wifi-off orange si déconnecté). Screenshot : cloud bleu (synced OK) + router vert (WS connecté) visibles en bas-gauche.
- **Spacers/dividers** : Spacer 8dp top, TitledDivider "Week Completion", barre, Spacer 4dp, TitledDivider "Days", Spacer 4dp, liste. Respecter ces espacements pour la fidélité verticale.
- **demoHighlight** : `program.header` (sur la barre globale) et `program.list` (sur la LazyColumn) — ancrages du tour démo, sans impact visuel hors mode tour.
