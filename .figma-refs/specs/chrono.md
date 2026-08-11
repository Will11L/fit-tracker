# SPEC D'ASSEMBLAGE FIGMA — Feature Chrono (Stopwatch + Timer)

> Cible : 1 frame Android Samsung S21+ **412 × 916 dp**, thème **dark**, par variante.
> Source : screenshots `07_chrono_stopwatch_running.jpg` + `08_chrono_timer.jpg` + code `feature/chrono/`.
> Écran plein (pas de Scaffold padding interne) : `Column` racine fond `bgScreen`, l'écran fait `weight(1f)` entre la title bar et le menu d'onglets. Le contenu central a `padding(horizontal = 18.dp)`.

## Tokens couleur (dark — valeurs exactes pour l'assembleur)

| Token (appColors) | Hex dark | Usage dans Chrono |
|---|---|---|
| `bgScreen` | `#101720` | fond global de l'écran |
| `bgSurface` | `#1E2A3C` | fond de la `ScreenTitleBar` (barre titre "Chrono") |
| `bgRecessed` | `#091216` | boîte d'affichage stopwatch, rows de laps, tuiles preset non-sélectionnées, bouton Lap désactivé |
| `bgBottomNav` | `#0F1C26` | fond du menu d'onglets `DualTabMenu` + fond de la BottomNavBar |
| `selectedFill` | `#153A6B` | fond de l'onglet actif (Stopwatch/Timer) + fond de l'item BottomNav actif |
| `primaryAction` (= `ButtonPrimaryColor`) | `#2377CA` | gros chiffres du temps, segments allumés du cadran timer, bouton Start/Resume, titres de colonne `N°` |
| `blueMedium` | `#245682` | bouton Lap actif, tuile preset sélectionnée, colonne `Δ Lap`, texte delta des laps, bordure des mini-overlays |
| `redMedium` | `#B3403E` | bouton Stop / Pause (état RUNNING) |
| `textPrimary` | `#FFFFFF` | texte principal (labels boutons, valeurs) |
| `textTertiary` | `#D3D3D3` (LightGray) | colonne `Time`, texte "Time" laps, onglet inactif, hints |
| `divider` | `#5E78A0` | lignes des `TitledDivider` ("Stopwatch", "Laps", "Timer", "Pre-set") |
| `dividerStrong` | `#153A6B` | divider 1.5dp sous le menu d'onglets (bas de l'écran) |
| `accentText` (= `lightBlue`) | `#4FC3F7` | preview "00:00:00" du dialog timer |
| `orangeMedium` | `#C4841F` | badges sync/offline BottomNav (chrome partagé) |
| `mediumGreen` | `#008444` | badge WS connecté BottomNav (chrome partagé) |

Typographie (MaterialTheme) : `displayMedium` = gros chiffres stopwatch (~45sp) ; `displaySmall` = chiffres centre timer (~36sp) ; `titleMedium` (~16sp) = labels preset + temps mini-overlay ; `bodyMedium` (~14sp) = rows laps ; `labelLarge` (~14sp bold) = en-têtes colonnes laps ; titre barre = 16sp SemiBold ; labels boutons = 14sp ; shape `small` = coins arrondis ~8dp partout (cards, tuiles, boutons).

---

## 1. Layout écran (haut→bas)

Structure commune aux 2 variantes, de haut en bas :
**StatusBar (chrome) → ScreenTitleBar → [ZONE CONTENU variable] → DualTabMenu → divider → BottomNavBar → SystemNav (chrome).**

### CHROME (identique aux 2 variantes)

| # | Section | Composant (fichier) | Contenu / props | Hauteur |
|---|---|---|---|---|
| 0a | **StatusBar Android** | (système, pas Compose) | fond noir `#000000`. Gauche : `15:36` + 2-3 icônes. Droite : clé VPN, cloche barrée, wifi, signal, batterie pill `95`. | **~32 dp** |
| 0b | **ScreenTitleBar** | `ScreenTitleBar.kt` | Box pleine largeur, fond `bgSurface` `#1E2A3C`, texte **"Chrono"** centré, 16sp SemiBold, `textPrimary`. Cliquable (no-op aujourd'hui). | **44 dp** |
| Z | **ZONE CONTENU** | `StopwatchPage` ou `TimerPage` | `Box weight(1f)`, `padding(horizontal = 18.dp)`. Voir variantes ci-dessous. Le contenu interne a en plus `padding(bottom = 80.dp)` pour laisser la place à la barre de boutons ancrée en bas. | **flex (~688 dp)** |
| T | **Menu d'onglets DualTabMenu** | `DualTabMenu.kt` → `TabRowCustom.kt` | Row pleine largeur fond `bgBottomNav` `#0F1C26`, 2 onglets égaux (`weight 1f`) hauteur **42 dp** : **"Stopwatch"** et **"Timer"**. Onglet actif : fond `selectedFill` `#153A6B`, texte blanc SemiBold. Inactif : fond `bgBottomNav`, texte `textTertiary`. Pas de sous-onglets (subTabsMap vide). | **42 dp** |
| D | **Divider fort** | `HorizontalDivider` (dans ChronoScreen) | ligne pleine largeur, **1.5 dp**, couleur `dividerStrong` `#153A6B`. | 1.5 dp |
| N | **BottomNavBar** | `BottomNavBar.kt` | `NavigationBar` fond `bgBottomNav` `#0F1C26`, **52 dp** de contenu visible (au-dessus du SystemNav). 5 items, icônes seules (pas de label) : Menu (burger), Calendrier, Accueil (home), **Chrono (chronomètre) = ACTIF**, Stats. Item actif = fond pill `selectedFill` `#153A6B` + icône 38dp blanche ; inactifs = icône 28dp `textTertiary`. **Item Menu porte 4 micro-badges en coins** : haut-gauche sync (bleu cloud-done `primaryAction` ou orange cloud-off), haut-droite notifs non-lues (bleu, si >0), bas-gauche WS (vert si connecté / orange sinon), bas-droite wifi-off (orange si offline). Sur les screenshots : sync bleu (cloud) en haut-gauche + WS vert (routeur) en bas-gauche visibles. | **52 dp** |
| 0c | **SystemNav Android** | (système) | barre de navigation gestuelle/3 boutons, fond noir `#000000` : `III` (récents) · `O` (home) · `<` (back). | **~48 dp** |

> Astuce assembleur : 32 + 44 + ~688 + 42 + 1.5 + 52 + 48 ≈ 916 dp.

---

### VARIANTE A — "Stopwatch" (réf : `07_chrono_stopwatch_running.jpg`)

Zone contenu `StopwatchPage.kt` (`Column`, `Arrangement.Top`, `padding(horizontal=18.dp)` via parent + `padding(bottom=80.dp)`), de haut en bas :

| # | Section | Composant (fichier) | Contenu / props | Hauteur approx |
|---|---|---|---|---|
| A1 | Spacer | — | espace haut | 8 dp |
| A2 | **TitledDivider "Stopwatch"** | `TitledDivider.kt` | ligne `divider` `#5E78A0` — texte **"Stopwatch"** centré SemiBold `divider` — ligne. Padding vertical 6dp. | ~28 dp |
| A3 | Spacer | — | | 8 dp |
| A4 | **Boîte d'affichage du temps** | Box dans `StopwatchPage` | Box pleine largeur, fond `bgRecessed` `#091216`, shape small, `padding(24.dp)`, texte centré. Valeur **`00:00:06:81`** (format `HH:MM:SS:CC`), style `displayMedium` (~45sp), couleur `primaryAction` `#2377CA`. | ~96 dp |
| A5 | Spacer | — | | 24 dp |
| A6 | **TitledDivider "Laps"** | `TitledDivider.kt` | identique A2, texte **"Laps"**. | ~28 dp |
| A7 | Spacer | — | | 8 dp |
| A8 | **En-tête colonnes laps** | `LapsHeader.kt` | Row, `padding(horizontal=16.dp)`, 3 colonnes pondérées (weight 1 / 2 / 2) : **"N°"** (gauche, `labelLarge`, `primaryAction`) · **"Δ Lap"** (droite, `blueMedium`) · **"Time"** (droite, `textTertiary`). | ~20 dp |
| A9 | Spacer | — | | 8 dp |
| A10 | **Liste des laps** | `LazyColumn` de `LapRow.kt` | `verticalArrangement spacedBy(6.dp)`. Si vide : 1 row "No laps yet" (italique, centré, `blueMedium`, fond `bgRecessed`). Si rempli : N rows. **3 rows visibles sur le screenshot** : Row = fond `bgRecessed` `#091216`, shape small, `padding(h=16,v=8)`, 3 colonnes (weight 1/2/2) : index `1`/`2`/`3` (blanc, gauche) · delta `00:00:01:77` etc. (droite, `blueMedium`) · cumul `00:00:01:77`/`00:00:03:59`/`00:00:05:32` (droite, `textTertiary`). | ~52 dp / row |
| — | (espace flex) | — | la liste ne remplit pas → grand vide jusqu'aux boutons | flex |
| A11 | **Barre de boutons (ancrée bas)** | Row dans `StopwatchPage` (`align BottomCenter`) | `padding(20.dp)`, `spacedBy(20.dp)`, 2 boutons `weight(1f)` hauteur **46 dp** (`ActionIconWithTextButton.kt`) : **Gauche = "Lap"** (icône drapeau `ic_rounded_flag`, fond `blueMedium` `#245682` car RUNNING) · **Droite = "Stop"** (icône pause-circle `ic_rounded_pause_circle`, fond `redMedium` `#B3403E` car RUNNING). Texte blanc 14sp + icône 24dp. | 46 dp (+40dp padding) |

**Note screenshot 07** : un **petit pill** apparaît au-dessus de la barre Lap/Stop (`drapeau` bleu | `00:00:06` | `pause` rouge, bordure bleue arrondie). **C'est le `MiniChronoOverlay` flottant** (cf. §3), PAS un élément de la page — il est rendu parce que le stopwatch tourne et que l'overlay se positionne par défaut bas-centre ; il flotte au-dessus. À reproduire dans le mockup comme overlay distinct (voir §3). État RUNNING = le bouton droit est rouge "Stop", la zone temps en bleu vif, 3 laps enregistrés.

---

### VARIANTE B — "Timer" (réf : `08_chrono_timer.jpg`)

Zone contenu `TimerPage.kt` (`Column`, `Arrangement.Top`, `padding(horizontal=18.dp)` parent + `padding(bottom=80.dp)`), de haut en bas :

| # | Section | Composant (fichier) | Contenu / props | Hauteur approx |
|---|---|---|---|---|
| B1 | Spacer | — | | 8 dp |
| B2 | **TitledDivider "Timer"** | `TitledDivider.kt` | ligne `divider` — texte **"Timer"** centré — ligne. | ~28 dp |
| B3 | Spacer | — | | 8 dp |
| B4 | **Cadran circulaire** | `TimerCircularDisplay.kt` | Box centrée, **diamètre 260 dp**. Anneau de **60 barres radiales** (`segmentCount=60`) partant du bord vers l'intérieur (`barLength=18dp`, `barWidth=3dp`, bouts arrondis). État IDLE + durée>0 → **toutes les barres allumées** couleur `primaryAction` `#2377CA` (screenshot : anneau plein bleu). Barres éteintes = `primaryAction` alpha 0.15. Au centre : texte **`00:01:00:00`** (format `HH:MM:SS:CC`), style `displaySmall` (~36sp), couleur `primaryAction`. | ~260 dp |
| B5 | Spacer | — | | 24 dp |
| B6 | **TitledDivider "Pre-set"** | `TitledDivider.kt` | texte **"Pre-set"**. | ~28 dp |
| B7 | Spacer | — | | 8 dp |
| B8 | **Grille de presets** | `LazyVerticalGrid` (3 col) de `PresetTile.kt` | 3 colonnes fixes, `spacedBy(10.dp)` H et V, non-scrollable. **9 tuiles** dans cet ordre : `30s` · `45s` · **`1 min`** · `2 min` · `5 min` · `10 min` · `15 min` · `30 min` · `1h`. Chaque tuile : Box `aspectRatio 2.0` (large), shape small, texte centré `titleMedium`. **Sélectionnée** (screenshot = `1 min`) : fond `blueMedium` `#245682`, texte blanc. **Non-sélectionnée** : fond `bgRecessed` `#091216`, texte blanc alpha 0.9. (Désactivée si timer ≠ IDLE : alpha 0.4.) | ~3 lignes ≈ 220 dp |
| B9 | Spacer | — | | 12 dp |
| B10 | **Hint texte** | `Text` dans `TimerPage` | pleine largeur, centré, `bodyMedium`, `textPrimary` alpha 0.65. Texte selon état : IDLE+durée>0 → **"Ready"** (screenshot) ; IDLE+durée≤0 → "Choose a preset to start" ; running → "Timer running — reset to change preset". | ~20 dp |
| — | (espace flex) | — | | flex |
| B11 | **Barre de boutons (ancrée bas)** | Row dans `TimerPage` (`align BottomCenter`) | `padding(20.dp)`, `spacedBy(20.dp)`, 2 boutons `weight(1f)` hauteur **46 dp** : **Gauche = "Set"** (icône `ic_timer`, fond `blueMedium` `#245682`) → ouvre le dialog durée custom · **Droite = "Start"** (icône play-circle `ic_rounded_play_circle`, fond `primaryAction` `#2377CA` car IDLE). Texte blanc 14sp + icône 24dp. | 46 dp (+40dp padding) |

**État screenshot 08** : timer IDLE, preset `1 min` sélectionné, anneau plein, hint "Ready", boutons "Set" (bleu) + "Start" (bleu vif). Pas d'overlay timer visible (le timer ne tourne pas).

---

## 2. Interactions (flow map)

### Chrome / navigation
- tap onglet **"Stopwatch"** → bascule la zone contenu sur `StopwatchPage` (variante A). Persisté (`lastActiveTab`).
- tap onglet **"Timer"** → bascule sur `TimerPage` (variante B). Persisté.
- tap **BottomNav → Chrono** (déjà actif ici) → no-op.
- tap **BottomNav → autre item** (Calendrier/Accueil/Stats) → navigue ailleurs → **si stopwatch ou timer tourne**, le mini-overlay correspondant apparaît en flottant (cf. §3).
- tap **BottomNav → Menu** → ouvre le drawer.
- tap **barre titre "Chrono"** → no-op aujourd'hui (`/* later: options sheet */`).

### Onglet Stopwatch (boutons dépendent de l'état)
- tap **bouton droit "Start"** (état IDLE, fond `primaryAction`, icône play) → démarre → état RUNNING.
- tap **bouton droit "Stop"** (état RUNNING, fond `redMedium`, icône pause) → met en pause → état PAUSED.
- tap **bouton droit "Resume"** (état PAUSED, fond `primaryAction`, icône play) → reprend → RUNNING.
- tap **bouton gauche "Lap"** (état RUNNING, fond `blueMedium`, icône drapeau) → ajoute un lap dans la liste (auto-scroll en bas).
- tap **bouton gauche "Reset"** (état PAUSED, fond `blueMedium`, icône reset) → remet à zéro → IDLE, vide les laps.
- bouton gauche en état IDLE = **désactivé** (texte vide, fond `bgRecessed`, tint `textTertiary`).

### Onglet Timer (boutons dépendent de l'état)
- tap **tuile preset** (ex. `45s`) — uniquement si IDLE → définit la durée, surligne la tuile (`blueMedium`), met le cadran à plein, hint "Ready".
- tap **bouton gauche "Set"** (état IDLE, icône timer) → **ouvre `TimerDurationDialog`** (sélecteur H/M/S, cf. §3).
- tap **bouton gauche "Reset"** (état ≠ IDLE, icône reset) → remet le timer à zéro → IDLE.
- tap **bouton droit "Start"** (état IDLE, fond `primaryAction`, play) → lance le décompte → RUNNING (no-op si aucune durée choisie).
- tap **bouton droit "Pause"** (état RUNNING, fond `redMedium`, pause) → PAUSED.
- tap **bouton droit "Resume"** (état PAUSED, fond `primaryAction`, play) → RUNNING.
- tap **bouton droit "Restart"** (état FINISHED, fond `primaryAction`, play) → relance la même durée.
- à 0 → état FINISHED : cadran + chiffre **pulsent** (alpha 0↔1, ~400ms) + notification système "Timer finished".

### Mini-overlays (quand on quitte l'écran Chrono)
- **quitter l'écran Chrono pendant que le stopwatch tourne** → `MiniChronoOverlay` flotte (drapeau | temps | play/pause).
- **quitter l'écran Chrono pendant que le timer tourne** → `MiniTimerOverlay` flotte (reset | mini-cadran | play/pause).
- tap **corps d'un mini-overlay** → re-navigue vers l'écran Chrono (`onOpenChrono`).
- **drag d'un mini-overlay** → le déplace librement (clampé à l'écran).
- les boutons des overlays pilotent le même état que la page (VM partagé Activity-scope).
- les overlays sont **masqués** quand on est déjà sur l'écran Chrono (le screenshot 07 montre l'overlay car il se superpose au bas de la page — voir note §3).

---

## 3. Inventaire dialogs & sheets & overlays

### 3.1 `TimerDurationDialog` (dialog) — `ui/components/TimerDurationDialog.kt`
- **But** : saisir une durée custom (heures/minutes/secondes) hors presets.
- **Déclencheur** : bouton "Set" de l'onglet Timer (état IDLE).
- **Type** : `AlertDialog` M3, `containerColor = bgScreen` `#101720`.
- **Contenu** :
  - Titre **"Set timer"** couleur `primaryAction`.
  - Corps = `HmsWheelPicker` (`HmsWheelPicker.kt`) :
    - Bandeau preview **`00:00:00`** : Box pleine largeur fond `bgRecessed` `#091216`, shape small, padding vertical 14dp, texte `titleLarge` couleur `accentText` `#4FC3F7`.
    - 3 colonnes de roues (`WheelPicker`) séparées par **`:`** : **Hours** (0–23) · **Minutes** (0–59) · **Seconds** (0–59). Chaque colonne a un label (`labelLarge`, blanc alpha 0.7) au-dessus.
    - Hint bas **"swipe"** (alpha 0.55).
  - Boutons : **"Cancel"** (`TextButton`, `textTertiary`) à gauche · **"Save"** (`TextButton`, `primaryAction` si valide / `textTertiary` si total = 0) à droite.
- **Validation** : Save désactivé si h+m+s = 0.

### 3.2 `MiniChronoOverlay` (overlay flottant) — `ui/overlay/MiniChronoOverlay.kt`
- **But** : garder le stopwatch contrôlable quand on a quitté l'écran Chrono.
- **Visibilité** : visible si `stopwatchState != IDLE`. Position par défaut : bas-centre, à 130dp du bas. Draggable.
- **Forme** : Row arrondie (`RoundedCornerShape 16dp`), bordure 1.5dp `blueMedium` `#245682`, fond `bgRecessed` `#091216`, `padding(12.dp)`, `spacedBy(10.dp)`.
- **Contenu** (3 éléments) :
  - `ActionIconButton` gauche : icône drapeau (RUNNING) ou reset (PAUSED), fond `blueMedium`.
  - Texte temps **`HH:MM:SS`** (`formatTimeFull`, ex. `00:00:06`), `titleMedium`, blanc.
  - `ActionIconButton` droit : pause-circle (RUNNING, fond `redMedium`) ou play-circle (sinon, fond `primaryAction`).
- **Note** : c'est le pill visible sur le screenshot 07 (`drapeau bleu | 00:00:06 | pause rouge`). Le reproduire comme **calque overlay au-dessus** de la page stopwatch, centré horizontalement, ~à 130dp au-dessus de la BottomNavBar (au-dessus de la barre Lap/Stop).

### 3.3 `MiniTimerOverlay` (overlay flottant) — `ui/overlay/MiniTimerOverlay.kt`
- **But** : garder le timer contrôlable hors écran Chrono.
- **Visibilité** : visible si `timerState != IDLE`. Position par défaut : bas-centre, à 205dp du bas. Draggable. (Non visible sur le screenshot 08 car timer IDLE.)
- **Forme** : identique à 3.2 (Row arrondie 16dp, bordure 1.5dp `blueMedium`, fond `bgRecessed`, padding 12dp).
- **Contenu** (3 éléments) :
  - `ActionIconButton` gauche : icône reset, fond `blueMedium`.
  - **Mini cadran** `TimerCircularDisplay` réduit : diamètre **40dp**, `segmentCount=30`, `barLength=8dp`, `barWidth=1.5dp`, texte central compact (`compactRemainingText` : minutes restantes si ≥1min, sinon secondes — 1-2 chiffres), `labelMedium`, `primaryAction`.
  - `ActionIconButton` droit : pause-circle (RUNNING, `redMedium`) ou play-circle (sinon, `primaryAction`).

### Pas de bottom sheet dans cette feature
La barre titre "Chrono" a un slot `onClick` réservé (`/* later: options sheet */`) mais **aucune sheet n'existe aujourd'hui** — ne pas en dessiner.

---

## 4. Notes — états & variantes

### Stopwatch — 3 états (`StopwatchStateMachine.State`)
| État | Bouton gauche | Bouton droit | Zone temps |
|---|---|---|---|
| **IDLE** | "" désactivé (fond `bgRecessed`, reset icon, tint tertiaire) | **"Start"** play, fond `primaryAction` | `00:00:00:00`, pas de laps ("No laps yet") |
| **RUNNING** (screenshot 07) | **"Lap"** drapeau, fond `blueMedium` | **"Stop"** pause-circle, fond `redMedium` | temps qui défile, laps possibles |
| **PAUSED** | **"Reset"** reset, fond `blueMedium` | **"Resume"** play, fond `primaryAction` | temps figé |

### Timer — 4 états (`TimerStateMachine.State`)
| État | Bouton gauche | Bouton droit | Cadran |
|---|---|---|---|
| **IDLE** (screenshot 08) | **"Set"** timer-icon, fond `blueMedium` → ouvre dialog | **"Start"** play, fond `primaryAction` | plein si durée choisie (toutes barres `primaryAction`), vide si durée ≤ 0 ; hint "Ready" / "Choose a preset to start" |
| **RUNNING** | **"Reset"** reset, fond `blueMedium` | **"Pause"** pause-circle, fond `redMedium` | barres allumées ∝ remaining/duration, s'éteignent sens horaire depuis 12h ; hint "Timer running…" ; presets désactivés (alpha 0.4) |
| **PAUSED** | **"Reset"** | **"Resume"** play, fond `primaryAction` | figé |
| **FINISHED** | **"Reset"** | **"Restart"** play, fond `primaryAction` | tout l'anneau + le chiffre **pulsent** (alpha 0↔1, 400ms reverse), effet "ding" + notif système |

### Détails de rendu importants pour la fidélité
- **Cadran timer** : barres radiales (lignes), PAS un arc/anneau plein. i=0 à 12h, décompte horaire (les barres du haut s'éteignent en premier). 60 barres en grand (page), 30 en mini (overlay).
- **Gros chiffres** stopwatch : format à **4 groupes** `HH:MM:SS:CC` (centisecondes), couleur `primaryAction`. Centre timer = même format.
- **Tuiles preset** : ratio large (2:1), arrondies, la sélectionnée passe en `blueMedium` plein.
- **Boutons d'action** : tous hauteur 46dp, `weight(1f)` chacun (50/50), gap 20dp, padding extérieur 20dp ; le rouge (`redMedium`) = action "stopper/pauser", le bleu vif (`primaryAction`) = action "lancer/reprendre", le bleu moyen (`blueMedium`) = action secondaire (Lap/Reset/Set).
- **Mini-overlays** : même style (pill arrondi 16dp + bordure `blueMedium`), 3 slots ; le chrono affiche un texte, le timer affiche un mini-cadran.
- **Chrome partagé** : StatusBar 32 + BottomNav 52 + SystemNav 48 ; la BottomNavBar a l'item Chrono en pill `selectedFill` + 4 micro-badges d'état système sur l'item Menu (sync/notifs/WS/offline).
- **Thème** : tout en dark. Les valeurs hex ci-dessus sont les valeurs dark réelles ; un mode light existe dans le code mais les screenshots sont en dark → assembler en dark.
