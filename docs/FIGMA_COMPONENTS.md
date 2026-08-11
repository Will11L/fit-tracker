# Figma Components — sport-app design system

> ⚠️ **DOC FIGÉ AU 2026-05-17 — APPROCHE SUPPLANTÉE.** L'approche pixel-perfect screen-by-screen (A1 StatusBar, etc.) décrite ci-dessous a été remplacée le 2026-05-17 par la refonte **Design System v2** organisée en pages Figma `1 · Foundations` / `2 · Atoms` / `3 · Molecules` / `4 · Organisms`. État courant : [FIGMA_VERIFICATION.md](FIGMA_VERIFICATION.md) (atoms+molecules, 16 atoms validés) et [FIGMA_ORGANISMS.md](FIGMA_ORGANISMS.md) (page 4, organisms en création). L'écran `UiShowcaseScreen.kt` (Drawer → Admin → UI Showcase, admin gated) sert de référence runtime. Les helpers `.figma-refs/` (grid/pipette/diff) restent utilisables pour debug ponctuel.

> Liste exhaustive des composants UI de l'app Android Compose à reproduire dans Figma de manière pixel-perfect (<2% diff par composant).
>
> Approche : **par composant** (atomique, réutilisable) plutôt que par screen (accumulation d'erreurs). Chaque composant est construit une fois en Figma puis instancié dans les screens.

## Fichier Figma cible

- **fileKey** : `ca2qkjOKCy5N5uEbIKyqrO`
- **URL** : https://www.figma.com/design/ca2qkjOKCy5N5uEbIKyqrO
- **Projet** : "SportApp" (drive perso Will99)

## Tooling (dans `.figma-refs/`)

| Helper | Usage | Description |
|---|---|---|
| `grid.py` | `python grid.py <img> <out> [step]` | Overlay grille de coords sur screenshot pour mesurer |
| `pipette.py` | `python pipette.py <img> x1 y1 [x2 y2 ...]` | Sample RGB hex au pixel exact |
| `crop.py` | `python crop.py <img> <out> <x> <y> <w> <h>` | Isole une zone du screenshot pour tester un composant |
| `diff.py` | `python diff.py <orig> <fig> <out> [thresh] [W H]` | Diff 4-panel pixel par pixel |
| `compare.py` | `python compare.py <fig> <orig> <out>` | Side-by-side simple |

## Légende statut

- `[ ]` à faire
- `[~]` en cours
- `[x]` pixel-perfect (<2% diff)
- `[!]` bloqué (limitation tech)

---

## A. Chrome universel

> Présent sur quasi tous les screens. Perfectionner = gain visible partout.

- `[ ]` **A1. StatusBar** — Samsung Android status bar (top 95px en 1080×2400)
  - Éléments : time text, screen_rec, check_circle, vpn_key, volume_off, wifi, signal_cellular_alt, battery pill (texte 96)
  - Variants : `time_text` (15:33, 15:34, 15:35, 15:36, 15:37, 15:45), `battery_level` (95, 96, 100)
  - Source : tous les screenshots top

- `[ ]` **A2. SystemNav** — Samsung 3-button nav (bottom ~170px en 1080×2400)
  - Éléments : recents (3 lignes verticales rounded), home (square rounded outline), back (chevron <)
  - Variants : aucun (statique)
  - Source : tous les screenshots bottom

- `[ ]` **A3. BottomNavBar** — Barre d'app 5 icones + sync badges
  - Éléments : icones [menu, calendar_today, home, timer, bar_chart], left sync badges (cloud_done + router)
  - Variants : `selected_menu|calendar|home|chrono|stats`, `cloud_state_synced|pending`, `router_state_online|offline`
  - Style sélectionné (per code) : pill bg `selectedFill` #153A6B + icone `textOnSelected` #FFFFFF, taille icone 38.dp
  - Style non-sélectionné (per code) : pill bg **transparent** (parent `bgBottomNav` #0F1C26 visible à travers) + icone `textTertiary` #D3D3D3, taille icone 28.dp
  - Source : `02_home_session_active.jpg`, `07_chrono_stopwatch_running.jpg`, `09_stats_top.jpg`

---

## B. Navigation

- `[ ]` **B1. DualTabMenu** — 3 tabs top (Session/Goals/Program)
  - Variants : `selected_0|1|2`
  - Style sélectionné : `selectedTab` blue bg, **coins inférieurs CARRÉS** (pas arrondis — corrigé)
  - Source : `02_home_session_active.jpg`, `04_home_goals.jpg`, `05_home_program_week.jpg`

- `[ ]` **B2. SubTabMenu** — 2 sub-tabs (Week/Calendar)
  - Variants : `selected_0|1`
  - Style sélectionné : `selectedTab` blue, coins carrés
  - Source : `05_home_program_week.jpg`, `06_home_program_calendar.jpg`

- `[ ]` **B3. AppBar** — Titre simple centré (h~50px), e.g. "Chrono", "Core & Mobility"
  - Variants : `with_back_button|simple`
  - Source : `07_chrono_stopwatch_running.jpg` ("Chrono"), `02_home_session_active.jpg` ("Core & Mobility")

---

## C. Sections & dividers

- `[ ]` **C1. TitledDivider** — line — title — line
  - Lignes courtes gauche/droite (80dp en frame 380), titre centré 16pt textMuted color
  - Source : tous les screens avec sections

- `[ ]` **C2. ThickDivider** — Séparateur drawer entre sections
  - Line plus visible que C1, color `drawerHeading` opacity 0.5
  - Source : `14_drawer_open_top.jpg`

- `[ ]` **C3. SectionHeader** — Drawer section title (ACTIVITÉ, COMPTE & PARAMÈTRES, ADMINISTRATION)
  - Text Semi Bold 14pt en uppercase, color `drawerHeading` (blue clair)
  - Sous-trait blue
  - Source : `14_drawer_open_top.jpg`, `14_drawer_open_bottom.jpg`

---

## D. Form components

- `[ ]` **D1. TextField** — Material outlined
  - Variants : `state_empty|filled` × `focus_unfocused|focused` × `kind_text|password|number`
  - Élements : floating label top (color primaryAction), value text white, optional underline (focused = primaryAction, unfocused = none)
  - Source : `01_login_filled.jpg`

- `[~]` **D2. PrimaryButton** — Plusieurs variants (NON universel)
  - **D2a. IconText Wide** (Login style) : bg `blueMedium` (#245682), rounded `shapes.small` (8dp), icon `account_circle` 24dp + spacer 8dp + text 14sp blanc, hauteur ~40dp, fillMaxWidth. Source : `01_login_filled.jpg` Login button. ✅ build node 100:2 (3.85% diff).
  - **D2b. Text-only square rounded** (forme majoritaire dans l'app) : bg `bgButton` (boxBlue #1E2A3C) ou `primaryAction`, rounded `shapes.small` (8dp), text 14sp blanc, padding compacte, hauteur ~40dp. À construire quand rencontré.
  - **D2c. IconText Compact** : variant pour CTA secondaires (Start Core & Mobility, View program) — à analyser.
  - Source : `01_login_filled.jpg` (D2a), `03_home_session_no_session.jpg` (D2c probablement), reste de l'app (D2b).
  - **Note critique** : `D2` n'est PAS une forme universelle — ne pas conflater. Chaque variant = composant Figma séparé.

- `[ ]` **D3. SecondaryButton** — Outlined
  - Bg transparent ou bgCard, stroke divider/primary
  - Source : `03_home_session_no_session.jpg` (View program)

- `[ ]` **D4. IconButton** — Bouton rond avec icone seule
  - Variants : `bg_filled|outlined|transparent`, `size_24|32|40`
  - Source : multiple

- `[ ]` **D5. Switch** — Material switch
  - Variants : `state_on|off`
  - Source : `14_drawer_open_bottom.jpg` (Admin section quand exposed)

- `[ ]` **D6. Chip** — Filter chip
  - Variants : `selected|unselected`, `with_icon|text_only`, `color_default|primary|warning`
  - Source : `09_stats_top.jpg` (range chips), `04_home_goals.jpg` (legend chips)

- `[ ]` **D7. RadioGroup item** — Onboarding preferences
  - Pas de screenshot direct (onboarding pas captured), à approximer depuis le code

- `[ ]` **D8. Slider** — Continuous slider
  - À identifier (peut-être pas utilisé)

---

## E. Cards & containers

- `[ ]` **E1. Card** — Container rounded générique
  - Bg `bgCard` (#091217), cornerRadius 14, padding variable
  - Source : Tous les screens

- `[ ]` **E2. StatCard** — Icon + count + label
  - Layout : icon left (~26-32dp), count Bold 18-22pt, label Regular 12pt
  - Variants : `icon_check|arrow_upward|flame|moon|info`
  - Source : `02_home_session_active.jpg` (Sets Done, Exercises), `06_home_program_calendar.jpg` (Streak, Done, Rest)

- `[ ]` **E3. WorkoutCard** — Workout title bar
  - Source : `02_home_session_active.jpg` ("Core & Mobility")

---

## F. Lists & rows

- `[ ]` **F1. ExerciseRow** — Liste exercice dans session
  - Layout : name pill + cloud_done icon + count "0/3" + chevron blue button
  - Variants : `synced|pending`, `done|in_progress|not_started`
  - Source : `02_home_session_active.jpg` (Plank, Push-Up)

- `[ ]` **F2. DrawerItem** — Item du drawer latéral
  - Variants : `simple|with_badge|with_progress`
  - Layout : icon left (22dp) + label 16pt + optional right element (badge orange "0/11", progress + 0%)
  - Source : `14_drawer_open_top.jpg`

- `[ ]` **F3. DrawerSection** — Section header + items wrapper
  - SectionHeader (C3) + N items (F2)
  - Source : `14_drawer_open_top.jpg`

- `[ ]` **F4. DayCard** — Carte d'un jour dans Week view
  - Layout : name pill + status icons (replay red dashed / up-arrow blue dashed / moon) + cloud_done + workout label + mini progress + more + arrow_forward
  - Variants : `status_active|planned|rest|today_highlighted`
  - Source : `05_home_program_week.jpg`

- `[ ]` **F5. MuscleGoalRow** — Ligne de la table Goals
  - Layout : name pill + prio square (border colored + arrow) + done value + todo pill + status icon (ring dashed / check)
  - Variants : `status_in_progress|done`, `priority_up_red|down_green|diag_orange`
  - Source : `04_home_goals.jpg`

- `[ ]` **F6. LapRow** — Ligne du tableau Laps Chrono
  - Layout : N° (blue) + Δ Lap (blue muted) + Time (white) sur bgCard row
  - Source : `07_chrono_stopwatch_running.jpg`

- `[ ]` **F7. WorkoutSetRow** — Set inside session exercise
  - Layout : reps × weight + status
  - Source : pas captured directement (sous-écran ExerciseScreen)

- `[ ]` **F8. AdminUserRow** — Liste users admin
  - Layout : avatar + name + Switch is_admin (avec self-protect)
  - Source : pas captured (sous-écran Admin)

- `[ ]` **F9. TaskRow / RoutineRow** — Pour TasksScreen + RoutineScreen
  - À explorer (Routine Periods, Routine Tasks)

- `[ ]` **F10. ConversationRow** — Liste conversations
  - À explorer (placeholder Conversations dans drawer)

---

## G. Calendar

- `[ ]` **G1. CalendarCell** — Une cellule du calendrier
  - Variants : `status_dash|moon|check|failx|up|today|empty`
  - Layout : icon top (16-20dp), day number bottom (Medium 16pt)
  - Today : border bleu rounded autour de la cell
  - Source : `06_home_program_calendar.jpg`

- `[ ]` **G2. CalendarMonthHeader** — `← month →`
  - Source : `06_home_program_calendar.jpg`

- `[ ]` **G3. DayHeaderRow** — Header row "M T W T F S S"
  - Source : `06_home_program_calendar.jpg`

- `[ ]` **G4. CalendarGrid** — Composite des 3 ci-dessus + 5×7 cells
  - Source : `06_home_program_calendar.jpg`

---

## H. Chrono

- `[ ]` **H1. DialClock** — Cadran 60 tick marks
  - SVG path composé de 60 lignes radiales blue
  - Source : `08_chrono_timer.jpg`

- `[ ]` **H2. TimeDisplay** — Digital text time
  - Variants : `format_HHMMSS|HHMMSScs` (stopwatch a centisecondes, timer pas)
  - Source : `07_chrono_stopwatch_running.jpg` ("00:00:06:81"), `08_chrono_timer.jpg` ("00:01:00:00")

- `[ ]` **H3. PresetGrid** — 3×3 grid presets timer
  - Cellules **sans border** (corrigé)
  - Variants : selected cell par index 0-8
  - Source : `08_chrono_timer.jpg`

- `[ ]` **H4. CurrentRunPill** — Pill avec flag + time + pause
  - Source : `07_chrono_stopwatch_running.jpg`

- `[ ]` **H5. ChronoActionButton** — Lap / Stop (grand bouton)
  - Variants : `color_blue_lap|red_stop`, `with_icon`
  - Source : `07_chrono_stopwatch_running.jpg`

---

## I. Stats

- `[ ]` **I1. BarChart** — Chart bar avec dashed Y gridlines
  - Variants : Y axis labels (kg / sets / exercises units), nb bars (2-7)
  - Source : `09_stats_top.jpg`

- `[ ]` **I2. LineChart** — Chart ligne (Vico style)
  - Pas dans les captures actuelles

- `[ ]` **I3. LegendChipColored** — Chip avec couleur pleine
  - Variants : color per zone (Chest blue, Back orange, etc.)
  - Source : `09_stats_top.jpg`

- `[ ]` **I4. LegendChipDot** — Chip avec dot + text (autre style legend)
  - Source : `09_stats_top.jpg`

- `[ ]` **I5. StatsToggle** — Toggle button sort/palette/etc.
  - Variants : `icon_sort_az|palette|trending_up|trending_down|warning`, `selected|unselected`
  - Source : `09_stats_top.jpg`, `04_home_goals.jpg`

- `[ ]` **I6. RangeChip** — Time range chip (30 days, 3 months, etc.)
  - Variants : `selected|unselected`, `label_text`
  - Source : `09_stats_top.jpg`

- `[ ]` **I7. FrequencyCard** — 4-column stats card
  - Source : `09_stats_top.jpg`

- `[ ]` **I8. ChartTypeToggle** — Bar vs Line toggle
  - Source : `09_stats_top.jpg`

---

## J. Goals

- `[ ]` **J1. ProgressBar** — Horizontal progress + %
  - Variants : `color_green|red|primary`, `with_percent_label|without`
  - Source : `04_home_goals.jpg`, `06_home_program_calendar.jpg`

- `[ ]` **J2. StatusIndicator** — Ring dashed avec icon central
  - Variants : `state_in_progress_up|done|todo|paused`
  - Source : `04_home_goals.jpg`, `05_home_program_week.jpg`

- `[ ]` **J3. PrioritySquare** — Square avec colored stroke + arrow
  - Variants : `priority_up_red|down_green|diag_orange`
  - Source : `04_home_goals.jpg`

- `[ ]` **J4. MoreMenuButton** — Vertical 3-dot menu
  - Source : multiple

---

## K. Feedback & overlays

- `[ ]` **K1. Snackbar** — Avec icone + text
  - Variants : `success_green|error_red|info_blue|warning_orange`
  - Source : `authenticated_screen.jpg` (Connexion réussie!)

- `[ ]` **K2. LoadingSpinner** — Ring loading
  - Variants : `size_sm|md|lg`
  - Source : `authenticated_screen.jpg`

- `[ ]` **K3. LinearProgress** — Loading bar horizontale
  - Variants : `determinate|indeterminate`
  - Source : `authenticated_screen.jpg`

- `[ ]` **K4. Dialog** — Confirmation dialog
  - Variants : `with_two_actions|with_one_action`
  - Source : pas captured directement (ConfirmationDialog dans le code)

- `[ ]` **K5. BottomSheet** — Sheet montant du bas
  - À explorer (GoalsBottomSheet, etc.)

- `[ ]` **K6. Drawer** — Container nav latéral 304dp width
  - Bg `bgRecessed` (thirdBlue), wrapper de DrawerSection
  - Source : `14_drawer_open_top.jpg`

- `[ ]` **K7. MiniChronoOverlay** — Pill flottant (chrono running quand on quitte ChronoScreen)
  - Source : pas captured (overlay)

---

## L. Iconography

- `[ ]` **L1. MaterialIcon** — 30+ icones Material utilisés
  - Variants par icone : `filled|outlined`, `color_white|primary|accent`
  - Liste : menu, home, calendar_today, timer, bar_chart, cloud_done, router, notifications, chat, format_list_bulleted, fitness_center, psychology, account_circle, settings, file_upload, logout, signal_cellular_alt, wifi, volume_off, vpn_key, screen_rec, back_arrow, arrow_back, arrow_forward, arrow_upward, arrow_downward, chevron_right, check, check_circle, close, remove, info, more_vert, play_arrow, pause_circle, refresh, bedtime, flag, double_arrow, palette, sort_az, trending_up, trending_down, warning, add, local_fire, list, stack, apps, format_list_numbered, show_chart, screen_rec
  - Source : multiple

- `[ ]` **L2. FTLogo** — Logo composite Fit Tracker
  - Square rounded bgCard + dumbbell horizontal + "FT" bold blue
  - Variants : `size_sm|md|lg`
  - Source : `01_login_filled.jpg`, `authenticated_screen.jpg`

- `[ ]` **L3. DumbbellIcon** — Dumbbell standalone propre (SVG custom)
  - À utiliser dans FTLogo + ailleurs
  - Source : custom SVG (déjà créé `dumbbell_clean`)

---

## M. Onboarding

- `[ ]` **M1. OnboardingHeader** — Progress bar + step indicator
  - Variants : `step_1|2|3|4_of_4`
  - Source : pas captured (auto-backup blocage tester)

- `[ ]` **M2. OnboardingFooter** — Back / Skip / Next buttons
  - Variants : `step_first|middle|last` (changement de Next → Finish)
  - Source : pas captured

- `[ ]` **M3. OnboardingStepContent** — 4 variants pour Welcome/Bio/Preferences/Permissions
  - Source : pas captured

---

## N. Misc charting

- `[ ]` **N1. ChartAxisLabel** — Y-axis text label aligné droite
  - Source : `09_stats_top.jpg`, `04_home_goals.jpg`

- `[ ]` **N2. DashedBlueGridLine** — Horizontal dashed line accentText opacity 0.4
  - Source : `09_stats_top.jpg`, `04_home_goals.jpg`

---

## Priorité de construction (recommandée)

**Sprint 1 — Chrome universel (impact maximal)**
1. A3 BottomNavBar
2. A1 StatusBar
3. A2 SystemNav
4. D1 TextField
5. D2 PrimaryButton

**Sprint 2 — Navigation + primitives**
6. B1 DualTabMenu
7. B2 SubTabMenu
8. C1 TitledDivider
9. E1 Card
10. L1 MaterialIcon (lib)
11. L2 FTLogo

**Sprint 3 — Lists & specialized**
12. F1 ExerciseRow
13. F4 DayCard
14. F5 MuscleGoalRow
15. G1 CalendarCell
16. F2 DrawerItem

**Sprint 4 — Charts & feedback**
17. I1 BarChart
18. I6 RangeChip
19. K1 Snackbar
20. K2 LoadingSpinner

**Sprint 5 — Composites**
21. G4 CalendarGrid
22. H1 DialClock
23. H3 PresetGrid

**Sprint 6 — Reconstruct screens from components**
- LoginScreen ← {StatusBar, FTLogo, brand, subtitle, TextField×2, PrimaryButton, link, SystemNav}
- HomeSessionActive ← {StatusBar, DualTabMenu, AppBar, TitledDivider×4, ProgressBar, StatCard×2, ExerciseRow×2, BottomNavBar, SystemNav}
- etc.

## Notes

- **Polices** : tous les composants utilisent Roboto (font Android). Inter est un fallback acceptable mais Roboto est requis pour pixel-perfect.
- **Résolution** : tous les composants construits à résolution native device (1080×N en référence). Variant ×0.352 pour usage dans des frames low-res si besoin.
- **Color tokens** : à dériver d'un fichier `colors.json` (à créer) basé sur pipettages depuis screenshots device. Voir aussi `appli-android/app/src/main/java/com/example/sportapp/ui/theme/Color.kt` pour les noms canoniques côté code.
