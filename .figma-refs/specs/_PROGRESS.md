# Figma screens assembly — progress (run autonome 2026-06-04)
Fichier Figma : ca2qkjOKCy5N5uEbIKyqrO

## Conventions
- Frame écran 412×916, cornerRadius 40, clip, fond bg/screen (var 541:40), sur la page de la feature.
- StatusBarSamsung 1539:3 (top y0), BottomNavBar 1189:233 (y816), SystemNavBar3Button 1540:3 (y868).
- Home tabs : DualTabMenu 556:24 (SubRow=No) rescalé 412 ; tab du milieu = sélectionné.
- Largeur instances non-redistribuable (x/resize enfants verrouillés) → `rescale()` pour full-width.
- Flow map : cartes à droite de l'écran, ordonnées par Y du trigger (pas de croisement) ; flèches bleues pointillées #73A0E6 (0.45,0.62,0.95) dash [6,4] sw1.5.
- Specs détaillées par page dans .figma-refs/specs/<page>.md (Session, program-week, program-calendar, stats, chrono, login, drawer).

## État
- [x] **Goals** SCREEN — page Goals 1234:2, frame "📱 Home · Goals" 1618:68. Complet (chart 4 barres muscles rebuild).
- [x] Goals FLOW MAP — DONE : 5 cartes (GoalsBottomSheet, MuscleOptions, EditPriorityDialog 1449:48, TargetPickerDialog 1450:54, ChangeGoalStatus) à x=1880 + 5 flèches. Page 1234:2.
- [x] Session SCREEN (active) — page 1235:2, frame 1637:87. (Flow map = vague 2, 9 sheets)
  >> STRATÉGIE (run autonome, user absent) : assembler TOUS les écrans d'abord (coverage), flow maps en 2e vague.
  >> Registry composants : StatusBar 1539:3 · DualTabMenu 556:24(SubRow=No) · BottomNav 1189:233 · SystemNav 1540:3 · ScreenTitleBar 751:21 · TitledDivider 557:11 · LabeledProgressBar 563:23 · SummaryRow 766:3 · SummaryItem 591:15 · EntityListRow 1190:119 · EmptyListRow 1098:110 · OptionsBottomSheet 764:3 · StatusPickerDialog 1101:215 · OptionRow 605:18 · SegmentedIconToggle 1192:119 · GoalRow 1115:22 · GoalsHeader 1143:233 · GoalsAchievementChart 1131:233 · PriorityIcon 1112:11. Icons: ic_add 756:10, check 757:73, close 757:93, arrow_progress 756:22, edit 758:13, delete 757:121, eye 758:29, more_vert 758:113.
  >> Tab selection : recolorer rects[0/1/2].fills (blue #153A6B sel / dark #0F1C26) + txts (white/grey). Full-width = rescale(412/340).
- [x] Program-Week SCREEN — page Planning 1432:2, frame 1641:7. (flow map wave 2)
- [x] Program-Calendar SCREEN — page 1236:6, frame 1647:9. (flow map wave 2)
- [x] Stats SCREEN (top) — page 1236:2, frame 1648:28. (flow map wave 2 ; 3 sections charts restantes optionnelles)
- [x] Chrono SCREEN (Stopwatch) — page 1238:2, frame 1646:35. (variante Timer + flow map wave 2)
- [x] Login SCREEN — page Auth 1643:2, frame 1643:3. (Signup + flow map wave 2)
- [x] Drawer SCREEN (admin) — page Drawer, frame "📱 Drawer (admin)". (variante non-admin + flow map wave 2)

## VAGUE 2 — FLOW MAPS ✅ TOUS FAITS (cartes à droite de chaque écran, flèches bleues #73A0E6)
- [x] Goals : 5 cartes
- [x] Session : SessionOptions + ExerciseOptions + ExercisePicker + CreateWorkout + DeleteConfirm + flèches
- [x] Program-Week : PlannedWorkoutOptions + CopyPlannedWorkoutDialog (instance) + flèche
- [x] Program-Calendar : DayOptions + CreateWorkout + Legend + flèche
- [x] Stats : CustomRangePickerDialog + 2 nav drill-down (Muscle/Exercise Stats) + flèches
- [x] Chrono : TimerDurationDialog + MiniChronoOverlay + flèche
- [x] Login : SignupScreen + flèche
- [x] Drawer : note Navigation (17 items → routes) + flèche

## 🏁 RUN AUTONOME TERMINÉ 2026-06-04 : 8 écrans + 8 flow maps.
Reste optionnel (non bloquant) : variantes (Session sans-séance, Chrono Timer, Drawer non-admin/offline) ; 3 sections charts Stats restantes ; données représentatives à ajuster au 1:1 ; sélection bottom-nav par écran ; Code Connect des nouveaux composants manuels.

## CORRECTION 2026-06-04 (validée user) — structure des sheets flow map
Erreur initiale : sheets construits icône-à-gauche sans fond de row. Corrigé partout :
- OptionRow (OptionsBottomSheet) : row fond bgRecessed r8, label gauche (weight1), box couleur+icône BLANCHE à DROITE, titre = TitledDivider (instance 557:11). Builder de référence dans l'historique.
- StatusPickerDialog (ChangeGoalStatus) : FormDialog (pas de handle), row fond bgRecessed/couleur@0.1 si sélectionné, label gauche, icône TEINTÉE à droite SANS box, boutons Cancel/Update.
Appliqué : Goals (3), Session (3), Program-Week (1), Program-Calendar (DayOptions + Legend + CreateWorkout en form). Chrono/Stats/Login/Drawer non concernés (forms/notes).
