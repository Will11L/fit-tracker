# Spec d'assemblage Figma — Feature **Session** (Home, onglet 0)

> Cible : frame **412 × 916** (Samsung S21+, densité ~2.625, dark theme).
> Source : `feature/session/ui/SessionTab.kt` + `feature/session/ui/NoSessionFallback.kt`, hôte `feature/home/ui/HomeScreen.kt`.
> Screenshots de réf : `02_home_session_active.jpg` (séance active), `03_home_session_no_session.jpg` (sans séance).
> Le contenu Session est rendu DANS `HomeScreen` : `Column { DualTabMenu ; Box { quand selectedTopTab==0 → SessionTab | NoSessionFallback | loader } }`. Le BottomNavBar + SystemNav sont le chrome global (hors HomeScreen, posés par le shell racine).

Palette (tokens `appColors`, thème Dark — cf. `designsystem/theme/`) :
- `bgScreen` = fond écran (bleu très sombre quasi-noir).
- `bgSurface` = surface relevée (bleu sombre, ex. title bar, name-box training).
- `bgRecessed` = surface creusée (cartes résumé, lignes, fond name-box warmup/post).
- `bgBottomNav` = fond barres nav (haut tabs + bas).
- `selectedFill` = bleu sélection (onglet actif, bouton "View program", item nav actif).
- `primaryAction` = bleu vif (sync OK, %, boutons primaires). `textTertiary` = gris (sync off, labels secondaires).
- `mediumGreen` (check DONE), `orangeMedium` (exercices / in-progress), `blueMedium` (flèche "next"/in-progress), `redMedium`/`redDark` (suppression). `divider` (lignes TitledDivider).
- `progressColor(progress)` : <0.2 rouge `redMedium`, ≥0.2 `orangeMedium`, ≥0.5 `lightGreen`, ≥0.75 `mediumGreen`, =1 `primaryAction`. (Sur le screenshot 0% → la barre est vide ; le `%` "0%" est rendu en rouge.)

---

## 1. Layout écran (haut→bas) — 2 variantes

### Chrome commun aux 2 variantes (haut et bas)

| # | Section | Composant (fichier) | Données / props visibles | Hauteur ≈ |
|---|---|---|---|---|
| C1 | **StatusBar Android** | (système, pas Compose app) | 15:34, icônes statut, batterie 96 % | 32 dp |
| C2 | **DualTabMenu — top row** | `DualTabMenu.kt` → `TabRowCustom` | 3 onglets : **Session** (actif), **Goals**, **Program**. Fond `bgBottomNav`. Onglet actif = fond `selectedFill` + texte gras blanc ; inactifs = texte `textTertiary`. Pas de sub-row (sub-tabs n'existent que sous "Program"). | 42 dp |
| C3 | **BottomNavBar** | `bottomNavigationBar/BottomNavBar.kt` | 5 items : Menu (☰), Calendrier (📅), **Accueil/Home (🏠, actif)**, Chrono (⏱), Stats (📈). Fond `bgBottomNav`. Item actif = icône 38 dp sur pastille `selectedFill` ; inactifs = icône 28 dp `textTertiary`. Sur l'item Menu, 4 micro-badges en overlay (coins) : sync (haut-gauche, `cloud_done` bleu = OK), WS (bas-gauche, router vert/orange), notifs (haut-droite, bleu si unread>0), offline (bas-droite, wifi-off orange si déconnecté). Sur les screenshots : badge sync bleu (haut-gauche) + badge WS vert (bas-gauche) visibles. | 52 dp (barre) |
| C4 | **SystemNav (gestes/3-boutons)** | (système) | barres : récents `III`, home `○`, retour `‹` | 48 dp |

> Le bloc de contenu (variante A ou B) occupe l'espace entre C2 (DualTabMenu, y≈74) et C3 (BottomNav, y≈816) → hauteur utile ≈ **742 dp**.

---

### VARIANTE A — « Séance active » (screenshot `02_home_session_active.jpg`)

Rendu par `SessionTab.kt`. Structure : `Column(bg=bgScreen) { ScreenTitleBar ; Column(padding horizontal 18dp) { divider + progress + summary + LazyColumn(phases) } }`.

| # | Section | Composant (fichier) | Données / props visibles sur le screenshot | Hauteur ≈ |
|---|---|---|---|---|
| A1 | **Title bar (nom séance)** | `ScreenTitleBar` (`designsystem/common_components/ScreenTitleBar.kt`) appelée dans `SessionTab.kt:134` | Titre centré **"Core & Mobility"**, 16 sp SemiBold, blanc. Fond pleine largeur `bgSurface`. **Toute la barre est cliquable** (`onClick` → SessionOptionsBottomSheet). | 44 dp |
| A2 | **Divider titré "Session Completion"** | `TitledDivider` (`SessionTab.kt:142`) | Texte centré **"Session Completion"** (`session_completion`) entre 2 lignes `divider`, SemiBold. Précédé d'un Spacer 8 dp. | ~32 dp (avec padding) |
| A3 | **Barre de progression + actions** | `SessionTabProgressBar` (`feature/session/.../sessionTab/SessionTabProgressBar.kt`) → `LabeledProgressBar` | À gauche : barre fine (trough `bgRecessed`, remplie selon progress ; ici **0 %** → vide). Puis label **"0%"** (14 sp SemiBold, couleur = `progressColor` ; rouge à 0%). Puis `rightContent` = Row gap 8 dp de 3 `ActionIconButton` : ① **sync** (`ic_cloud_done` si synced sinon `ic_cloud_off`, tint `primaryAction`/`textTertiary`, sans fond) — screenshot = cloud bleu ; ② **done/progress** (`ic_rounded_check` vert si done sinon `ic_arrow_progress`, tint `mediumGreen`/`blueMedium`, sans fond) — screenshot = cercle pointillé bleu/flèche ; ③ **add** (`ic_add`, AVEC fond `selectedFill`, pastille carrée arrondie). | ~56 dp (Row padding vertical 8) |
| A4 | **Résumé 2 cellules** | `SessionSummaryRow` (`.../sessionTab/SessionSummaryRow.kt`) → `SummaryRow` + 2 × `SummaryItem` | 2 cartes côte à côte (poids égal, gap 8 dp), fond `bgRecessed`. Cellule 1 : icône `ic_rounded_check` (36 dp, tint `mediumGreen`) + valeur **"0/6"** (14 sp SemiBold) + label **"Sets Done"** (12 sp `textTertiary`). Cellule 2 : icône `ic_arrow_progress` (36 dp, tint `orangeMedium`) + valeur **"0/2"** + label **"Exercises"**. | ~60 dp |
| A5 | **LazyColumn — phases** | `SessionTab.kt:167` (LazyColumn, fillMaxSize, padding bottom 10 dp) | Conteneur scrollable des 3 phases (A6→A11). Spacer 6 dp au-dessus. | reste (flex) |
| A6 | **Divider "Warm-Up"** | `TitledDivider(session_phase_warmup)` | Texte **"Warm-Up"** entre 2 lignes. | ~32 dp |
| A7 | **Empty warmup** | `EmptyListRow(session_phase_empty_warmup)` (`SessionTab.kt:180`) | Affiché car aucun exo WARMUP : barre `bgRecessed` 44 dp, texte italique gauche **"No warm-up"** (`blueMedium`, 14 sp italic). (Sans icône → texte aligné gauche padding 12 dp.) | 44 dp + pad |
| A8 | **Divider "Training"** | `TitledDivider(session_phase_training)` | Texte **"Training"**. | ~32 dp |
| A9 | **Lignes exercices (Training)** | `SessionExerciseRow` × N (`.../sessionTab/SessionExerciseRow.kt`) → `EntityListRow` | 2 lignes sur le screenshot : **"Plank"** et **"Push-Up"**. Chaque ligne = Row 44 dp `bgRecessed`. Name-box cliquable (training : `nameBoxColor = bgSurface` → encadré bleu légèrement relevé), texte 14 sp Medium, `nameWeight = 2.5f`. `trailingContent` (3 zones) : ① icône sync centrée (weight 1f) — `ic_cloud_done` bleu (`primaryAction`) si synced sinon `ic_cloud_off` gris, 20 dp, non cliquable (screenshot = cloud bleu) ; ② texte **"0/3"** centré (setsDone/setsToDo, weight 1f, 14 sp) ; ③ pastille statut 44 dp carrée arrondie, fond selon statut (DONE vert / IN_PROGRESS orange / NEXT+NOT_STARTED `blueMedium` / SKIPPED rouge / pendingDeletion transparent) avec icône 30 dp blanche (`ic_keyboard_arrow_right` "›" pour NOT_STARTED/NEXT — screenshot = pastille bleue avec chevron `>`). | 44 dp/ligne + pad 5 dp |
| A10 | **Divider "Post-Training"** | `TitledDivider(session_phase_posttraining)` | Texte **"Post-Training"**. | ~32 dp |
| A11 | **Empty post-training** | `EmptyListRow(session_phase_empty_posttraining)` | Barre `bgRecessed` 44 dp, texte italique gauche **"No post-training"** (`blueMedium`). | 44 dp + pad |

> Sur le screenshot la liste se termine après "Post-Training / No post-training" — le reste de l'écran est du `bgScreen` vide jusqu'au BottomNav (la LazyColumn ne remplit pas l'espace). Padding horizontal du bloc A2→A11 = **18 dp** (gauche/droite). A1 (title bar) est pleine largeur.

---

### VARIANTE B — « Sans séance » (screenshot `03_home_session_no_session.jpg`)

Rendu par `NoSessionFallback.kt`. Structure : `Column(fillMaxSize, padding 32dp, center vertical + horizontal)`. **Pas de title bar, pas de barre de progression** — juste un état vide centré.

| # | Section | Composant (fichier) | Données / props visibles | Hauteur ≈ |
|---|---|---|---|---|
| B0 | (chrome C1+C2 en haut, C3+C4 en bas — identiques ; onglet **Session** toujours actif) | — | — | — |
| B1 | **Titre "Currently sleeping 💤"** | `Text` dans `NoSessionFallback.kt:32` (style `headlineSmall`, `textPrimary`) | Texte centré **"Currently sleeping 💤"** (`home_currently_sleeping`), grand (headlineSmall ~24 sp), blanc, l'emoji 💤 en bleu. Centré verticalement dans l'écran. | ~32 dp |
| B2 | **Bouton "View program"** | `ActionIconWithTextButton` (`NoSessionFallback.kt:41`) | Pastille arrondie : icône `ic_calendar_month` (📅) + texte **"View program"** (`home_view_program`). Fond par défaut (variante neutre du bouton, plus sombre/discret que B3). Spacer 32 dp au-dessus, 20 dp en dessous. | ~48 dp |
| B3 | **Bouton primaire "Start …"** | `ActionIconWithTextButton` (`NoSessionFallback.kt:51` ou `:59`) | **Deux cas exclusifs** (cf. §4) : (a) si une séance planifiée existe aujourd'hui → icône `ic_rounded_double_arrow` (»») + texte **"Start <nom planifié>"** (`home_start_planned` → screenshot = **"Start Core & Mobility"**), fond `selectedFill` (bleu). (b) sinon → icône `ic_rounded_add_box` (+) + texte **"Start a new session"** (`home_start_new_session`), fond `selectedFill`. Screenshot = cas (a). | ~48 dp |

> Tout le bloc B1→B3 est centré (vertical + horizontal) dans l'espace entre tabs et BottomNav. Fond `bgScreen` (quasi noir).

---

## 2. Interactions (flow map)

### Variante A (séance active)
- tap **title bar "Core & Mobility"** (A1) → **SessionOptionsBottomSheet** (options de la séance).
- tap **icône sync** (A3 ①, `cloud_done`/`cloud_off`) → **ConfirmationDialog "Sync Session"** (confirmer la synchro manuelle).
- tap **icône done/progress** (A3 ②) → **action directe** `toggleActualWorkoutDone()` (bascule statut DONE↔en cours, pas de dialog).
- tap **icône add "+"** (A3 ③) → **ExercisePickerBottomSheet** (ajouter un exercice à la séance).
- tap **name-box d'une ligne exercice** (A9, ex. "Plank") → **ExerciseOptionsBottomSheet** (options de cet exercice).
- tap **pastille statut "›"** à droite d'une ligne (A9) → navigue vers **SessionExerciseScreen** (`Routes.sessionExercise(uuid)`, détail/saisie des sets de l'exercice). N.B. cliquable seulement si statut ∈ {DONE, NEXT, IN_PROGRESS, NOT_STARTED}.
- (les zones sync-icône et "0/3" d'une ligne ne sont **pas** cliquables.)

### Variante B (sans séance)
- tap **"View program"** (B2) → bascule l'onglet Home vers **Program / Week** (`selectedTopTab=2, selectedSubTab=0` ; pas une nav, un switch d'onglet interne).
- tap **"Start <nom planifié>"** (B3-a) → action `startActualWorkoutFromPlanned(pw)` → crée la séance du jour depuis le planning → l'écran rebascule en Variante A.
- tap **"Start a new session"** (B3-b) → ouvre **CreateActualWorkoutDialog** (saisir un nom puis créer).

### Chrome (commun)
- tap onglet **Goals** → contenu Home = GoalsTabContent. tap **Program** → WeekView/Calendar (avec sub-tabs).
- BottomNav : **Menu** → ouvre le drawer ; **Calendrier/Chrono/Stats** → nav vers ces écrans ; **Home** → déjà ici.

---

## 3. Inventaire dialogs & bottom-sheets

> Tous montés conditionnellement à la fin de `SessionTab.kt` (sauf Create… qui est dans `NoSessionFallback.kt`). Sheets = `ModalBottomSheet` fond `bgScreen`, en-tête `TitledDivider`. Dialogs = `AlertDialog`/FormDialog fond `bgScreen`.

| Nom | Fichier | But | Contenu clé |
|---|---|---|---|
| **SessionOptionsBottomSheet** | `.../sessionTab/SessionOptionsBottomSheet.kt` (via `OptionsBottomSheet`) | Actions sur la séance courante | En-tête = nom séance. 4 actions (`SheetAction` = icône+label coloré) : **Mark as Done / Mark as Undone** (toggle, `ic_rounded_check` vert ou `ic_arrow_progress` bleu) ; **Rename session** (`ic_rounded_edit`, `selectedFill`) ; **See today's planned workout** (`ic_calendar_month`, `selectedFill`) ; **Delete session** (`ic_rounded_delete_forever`, `redDark`). |
| **ExerciseOptionsBottomSheet** | `.../sessionTab/ExerciseOptionsBottomSheet.kt` (via `OptionsBottomSheet`) | Actions sur un exercice de la séance | En-tête = nom exo. 2 actions : **See exercise details** (`ic_rounded_eye_tracking`, `blueMedium`) → nav ExerciseScreen ; **Remove from session** (`ic_rounded_delete_forever`, `redMedium`) → ouvre confirmation de suppression. |
| **ExercisePickerBottomSheet** | `designsystem/common_components/ExercisePickerBottomSheet.kt` | Ajouter un exercice à la séance | En-tête **"Add Exercise"** + dragHandle. Barre filtres (FilterDropdown "Equipment" + champ recherche centré) + TitledDivider "Exercises" + LazyColumn de lignes `bgRecessed` (nom + 2 boutons : œil `ic_rounded_eye_tracking` fond `selectedFill` → voir ; "+" `ic_add` fond `primaryAction` → sélectionner, puis enchaîne PhasePickerDialog). N'affiche que les exos pas déjà dans la séance. |
| **PhasePickerDialog** | `designsystem/common_components/PhasePickerDialog.kt` (AlertDialog) | Choisir la phase de l'exo qu'on vient d'ajouter | Titre **"Add to which phase?"**. 3 boutons pleine largeur 44 dp (`bgRecessed`) : **Warm-Up** / **Training** / **Post-Training** (renvoie code WARMUP/TRAINING/POST_TRAINING). |
| **CreateActualWorkoutDialog** | `.../sessionTab/CreateActualWorkoutDialog.kt` (via `FormDialog`) | Créer une nouvelle séance (depuis l'état "sans séance") | Titre **"Start a session"**. `CustomTextField` placeholder **"Session name"**. Bouton confirm **"Start"** (désactivé si vide ou == "Rest Day"). Messages d'erreur en `redMedium` si vide / "Rest Day". |
| **RenameActualWorkoutDialog** | `.../sessionTab/RenameActualWorkoutDialog.kt` (via `FormDialog`) | Renommer la séance | Titre **"Rename workout"**. `CustomTextField` label "Workout name", placeholder "New name", singleLine. Bouton **"Save"** (`common_save`). |
| **ConfirmationDialog — Sync Session** | `designsystem/common_components/ConfirmationDialog` (appelé `SessionTab.kt:384`) | Confirmer la synchro manuelle | Titre **"Sync Session"**, message **"Do you want to sync your session now?"**, bouton confirm **"Sync"** (`primaryAction`). |
| **ConfirmationDialog — Remove exercise** | idem (`SessionTab.kt:453`) | Confirmer le retrait d'un exo de la séance | Titre **"Confirm deletion"**, message **"Remove <nom exo> …"** (`session_remove_exercise_message`), confirm **"Delete"** / dismiss **"Cancel"**. Sur confirm → nav HOME. |
| **ConfirmationDialog — Delete session** | idem (`SessionTab.kt:474`) | Confirmer la suppression de toute la séance | Titre **"Delete session"** (`session_delete_title`), message `session_delete_message`, confirm **"Delete"** en `redMedium` / dismiss **"Cancel"**. Sur confirm → popBackStack. |

---

## 4. Notes (états / variantes / empty states / UI conditionnelle)

- **3 états du contenu onglet Session** (gérés dans `HomeScreen.kt:99-133`, pas dans SessionTab) :
  1. **Loading** : `loading || !initialSessionLoaded` → `CircularProgressIndicator` centré (anti-flash : évite d'afficher "Currently sleeping" puis basculer sur la séance). À mocker éventuellement comme 3ᵉ frame mineure.
  2. **Séance active** (`sessionUUID != null`) → **Variante A**.
  3. **Sans séance** (`sessionUUID == null`) → **Variante B**.
- **Variante B — bouton primaire conditionnel** : `plannedWorkout != null` → "Start <nom>" (cas screenshot, "Start Core & Mobility") ; sinon → "Start a new session" (ouvre le dialog de création). Un seul des deux est rendu.
- **Empty states des phases (Variante A)** : chaque phase (Warm-Up / Training / Post-Training) affiche un `EmptyListRow` italique si elle n'a aucun exercice. Sur le screenshot : Warm-Up et Post-Training sont vides ("No warm-up" / "No post-training"), Training a 2 exos. Mocker au moins 1 phase pleine + 2 phases vides pour fidélité.
- **Icône sync (barre A3 ① et ligne A9 ①)** : 2 états — synced (`ic_cloud_done`, `primaryAction` bleu) vs non synced (`ic_cloud_off`, `textTertiary` gris). Screenshots = tout en bleu (synced).
- **Icône done/progress (A3 ②)** : 2 états — done (`ic_rounded_check` vert) vs en cours (`ic_arrow_progress`, cercle pointillé bleu). Screenshot = en cours.
- **Pastille statut de ligne (A9 ③)** : 5+ variantes de couleur/icône selon `status` (DONE vert+check / IN_PROGRESS orange+arrow_progress / NEXT bleu+chevron / NOT_STARTED bleu+chevron / SKIPPED rouge+cancel / pendingDeletion transparent+close). Screenshot = NOT_STARTED (pastille `blueMedium` + chevron `›`).
- **Couleurs name-box selon phase** : Training utilise `nameBoxColor = bgSurface` (encadré relevé visible) ; Warm-Up et Post-Training utilisent `nameBoxColor = bgRecessed` (se fond davantage avec la ligne). Fond de ligne toujours `bgRecessed`.
- **Couleur du % de progression** : suit `progressColor()` par seuils ; à 0% c'est rouge (cohérent avec le "0%" rouge du screenshot). À mocker en fonction de la valeur choisie.
- **pendingDeletion** (ligne en cours de suppression) : fond ligne `darkGray`, name-box masqué, textes en `textTertiary`, pastille statut transparente + icône close — non visible sur les screenshots mais possible.
- **Sub-tabs** : la sub-row du `DualTabMenu` n'apparaît **que** sous l'onglet "Program". Sous "Session" → pas de 2ᵉ rangée d'onglets.
- **i18n** : tous les labels via `stringResource` (valeurs EN ci-dessus depuis `values/strings.xml`). Les noms de séance/exercice sont user-typed (non traduits ; "Core & Mobility", "Plank", "Push-Up").
