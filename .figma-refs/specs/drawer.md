# SPEC D'ASSEMBLAGE FIGMA — NavigationDrawer (menu latéral)

> Cible : frame **412 × 916** (Samsung S21+, dp), drawer ouvert, **scrim à droite**.
> Source code : `appli-android/app/src/main/java/com/example/sportapp/designsystem/drawer/`.
> Réf visuelle : `.figma-refs/Screenshots/14_drawer_open_top.jpg` + `14_drawer_open_bottom.jpg`.
> Thème de réf = **dark** (les 2 screenshots sont en dark). Valeurs hex dark résolues plus bas (§5).

---

## 0. Géométrie globale & cadre

| Élément | Valeur | Source |
|---|---|---|
| Frame | 412 × 916 dp | viewport S21+ |
| **StatusBar** (haut, mock système) | 32 dp de haut, fond `bgScreen` `#101720` | rappel mémoire : pas de barre 24dp grise ; ici le drawer est poussé sous la status bar via `statusBarsPadding()` |
| **Drawer (ModalDrawerSheet)** | largeur **300 dp**, hauteur = pleine hauteur (sous status bar + au-dessus nav bar) | `DrawerContent.kt:120` `.width(300.dp)` |
| Fond du drawer | `bgScreen` = `#101720` (blueBackground) | `drawerContainerColor = appColors.bgScreen` (l.118) |
| **Scrim à droite** | bande de **112 dp** (412 − 300) à droite du drawer, voile semi-opaque sombre par-dessus l'écran sous-jacent | `ModalNavigationDrawer` (MainActivity.kt:307), largeur déduite = 412 − 300 |
| Contenu sous le scrim (visible derrière, à peine) | bouts de cards avec chevrons `>` alignés à droite (cf. screenshots, c'est l'écran Home/Week derrière) | screenshots |
| **NavBar système** (bas, mock 3 boutons Android) | ~48 dp, fond noir, 3 glyphes (║ ◯ ‹) | screenshots — c'est la barre système, optionnelle |
| Padding bas interne du drawer | `windowInsetsPadding(navigationBars)` + `Spacer(46.dp)` final | l.123 + l.377 |

> ⚠️ Le contenu du drawer est une **`LazyColumn`** scrollable (l.125). Les 2 screenshots sont 2 positions de scroll du **même** drawer (haut puis bas). En Figma, assembler **un seul** drawer complet de haut en bas ; la hauteur totale du contenu dépasse 916 dp (scroll réel sur device).

> ⚠️ **Pas de header utilisateur** dans ce drawer. Le contenu commence **directement** par la section "ACTIVITÉ" (confirmé code + screenshots). Ne PAS inventer d'avatar/nom en haut.

---

## 1. Layout drawer (haut → bas)

Ordre exact (chaque `item {}` de la LazyColumn). Le drawer = **2 sections permanentes** + **1 section admin conditionnelle** + **footer sync** + spacer.

### A. Section « ACTIVITÉ » — `DrawerSection.kt`
- **Composant** : `DrawerSection(title, items)` (`DrawerSection.kt`).
- **Fond de section** : `bgRecessed` = `#091216` (thirdBlue) — légèrement plus sombre que le fond drawer.
- **Padding section** : `top = 12dp, bottom = 8dp` (l.37).
- **Titre de section** : `"ACTIVITÉ"` (FR) / `"ACTIVITY"` (EN), **uppercase**, **centré**, couleur `accentText` = `#4FC3F7` (lightBlue), 13 sp, Bold, letterSpacing 1 sp ; padding `horizontal 16dp, vertical 8dp` (l.39-51).
- **Divider sous le titre** (avant le 1er item) : `dividerStrong` α 0.6 (`#153A6B` à 60%), épaisseur **2.5 dp**, padding horizontal 20 dp (l.54-59).
- **Items** (9), chacun = une Row, hauteur ≈ **46 dp** (icône 22dp + padding vertical 12dp ×2 ≈ 46) :

| # | Label FR (EN) | Icône (drawable) | Trailing visible | Hauteur |
|---|---|---|---|---|
| 1 | Notifications | `ic_notifications` (cloche) | `DrawerIconCountIndicator` : icône mail `ic_rounded_mail` + count non lus, teinte `primaryAction` `#2377CA` (caché si count = 0 — absent sur le screenshot) | ~46 dp |
| 2 | Conversations | `ic_chat` (bulle) | — | ~46 dp |
| 3 | Tâches (Tasks) | `ic_rounded_list_alt` (liste) | **pill `TasksTodayStatsBadge`** `"0/11"` — RoundedCornerShape 8dp, fond `color α0.15`, texte 12sp Medium ; couleur = orange `#C4841F` si en cours, vert `#008444` si done==total, gris `#7B9DD0` si total==0 (cachée si total==0). **Screenshot : `0/11` orange** | ~46 dp |
| 4 | **{nom séance du jour}** (par défaut `"Aucune séance"` / `"No session"`) | `ic_rounded_expand_circle_right` (flèche dans cercle) | **`DrawerMiniProgress`** (mini barre + %) — visible UNIQUEMENT si séance du jour existe ET totalSets>0. **Screenshot : `Core & Mobility` + barre + `0%`** | ~46 dp |
| 5 | Programme (Program) | `ic_rounded_list_alt` (liste — même que Tasks) | — | ~46 dp |
| 6 | Exercices (Exercises) | `ic_exercise` (haltère) | — | ~46 dp |
| 7 | Muscles | `ic_rounded_neurology` (cerveau) | — | ~46 dp |
| 8 | Calendrier (Calendar) | `ic_calendar_month` (calendrier) | — | ~46 dp |
| 9 | Statistiques (Statistics) | `ic_equalizer` (barres) | — | ~46 dp |

- **Divider entre items** : `dividerStrong` α 0.30 (`#153A6B` à 30%), épaisseur **2 dp**, padding horizontal 18 dp (l.96-100). Pas de divider après le dernier item.
- **Divider de fin de section** (après la section, hors `DrawerSection`) : `dividerStrong` plein `#153A6B`, épaisseur **1.5 dp** (DrawerContent.kt l.250).

### B. Section « COMPTE & PARAMÈTRES » — `DrawerSection.kt`
- **Même composant** `DrawerSection`, mêmes styles (fond `bgRecessed`, titre centré bleu clair uppercase, dividers identiques).
- **Titre** : `"COMPTE & PARAMÈTRES"` (FR) / `"ACCOUNT & SETTINGS"` (EN).
- **Items** (6) :

| # | Label FR (EN) | Icône (drawable) | Trailing |
|---|---|---|---|
| 1 | Profil (Profile) | `ic_account_circle` (silhouette dans cercle) | — |
| 2 | Paramètres (Settings) | `ic_settings` (engrenage) | — |
| 3 | Exporter les données (Export Data) | `ic_file_export` (fichier + flèche) | — |
| 4 | Citations motivantes (Motivational quotes) | `ic_rounded_book` (livre) | — |
| 5 | Déconnexion (Logout) | `ic_logout` (porte/flèche sortante) | — |
| 6 | Paramètres de sync (Sync Settings) | `ic_home` (maison) | — |

> ⚠️ **Divergence screenshot ↔ code** : les screenshots montrent l'ordre `Profil · Paramètres · Exporter les données · Déconnexion · Paramètres de sync` (5 items, **sans** "Citations motivantes"). Le code actuel insère **"Citations motivantes"** (item 4) entre Export et Déconnexion → **6 items**. Les screenshots sont **antérieurs** à l'ajout de la feature Quotes. **Pour le mockup, suivre le CODE (6 items, ordre ci-dessus)** = état courant de l'app.

### C. Section « ADMINISTRATION » — conditionnelle (`if isAdmin`)
- **Visible UNIQUEMENT si `CurrentUserManager.isAdminFlow == true`** (DrawerContent.kt l.316). Cohérent avec guard serveur `require_admin` (403 sinon).
- Précédée d'un **divider de section** `dividerStrong` plein 1.5 dp (l.318).
- **Titre** : `"ADMINISTRATION"` (FR) / `"ADMIN"` (EN), même style bleu clair centré uppercase.
- **Items** (3) :

| # | Label FR (EN) | Icône (drawable) |
|---|---|---|
| 1 | Gérer les utilisateurs (Manage users) | `ic_account_circle` (silhouette) |
| 2 | UI Showcase | `ic_exercise` (haltère) |
| 3 | Paramètres de sync (Sync Settings) | `ic_home` (maison) |

> **Screenshot bottom** : montre la section ADMINISTRATION avec **uniquement "Gérer les utilisateurs"** visible (la fin du scroll coupe avant UI Showcase / Sync Settings, mais ils existent dans le code). Pour le mockup : produire **2 variantes** (voir §4) — admin (section affichée, 3 items) et non-admin (section absente).

### D. Footer sync — `DrawerFooter.kt`
- Précédé d'un **divider de section** `dividerStrong` plein 1.5 dp (l.358).
- **Composant** : `DrawerFooter(...)`. Row pleine largeur, `padding horizontal 16dp / vertical 8dp`, `SpaceBetween`, centré verticalement. Hauteur ≈ **40 dp**.
- Contenu gauche → droite :
  1. **Texte** `"Dernière sync : 2 min ago"` (FR) / `"Last sync: …"` (EN) — couleur `textTertiary` `#D3D3D3` (LightGray), style bodySmall. Le `%1$s` = texte relatif calculé (`"2 min ago"`, `"Never"`, `"5 s"`, `"Yesterday"`, etc.).
  2. **Icône signal réseau** : `ic_baseline_signal_cellular_alt` (barres montantes) si connecté → teinte **vert** `#008444` ; sinon `ic_rounded_signal_cellular_off` → **rouge** `#B3403E`. Pas de fond, non cliquable. **Screenshot : barres vertes** (connecté).
  3. **Icône sync (cloud) + badge** : `BadgedBox` — `ic_cloud_done` (nuage ✓) si tout synced → teinte `primaryAction` `#2377CA` (bleu) ; `ic_rounded_cloud_upload` (nuage ↑) si unsynced → **orange** `#C4841F`. Badge rouge M3 avec `totalPending` **uniquement si > 0**. Cliquable (déclenche sync). **Screenshot : nuage bleu ✓ (synced), pas de badge**.
  4. **Icône WebSocket (routeur)** : `ic_rounded_router` si WS connecté → **vert** `#008444` ; `ic_rounded_router_off` sinon → **orange** `#C4841F`. Cliquable seulement si déconnecté. **Screenshot : routeur vert** (connecté).
- Les 3 icônes = `ActionIconButton` `hasBackground=false` (pas de fond carré), taille glyphe ~40 dp de zone tap, icône intérieure ~22-24 dp.

### E. Spacer final
- `Spacer(height = 46.dp)` (l.377) — marge basse pour ne pas coller à la nav bar.

---

## 2. Interactions (flow map)

Chaque tap **ferme le drawer** (`drawerState.snapTo(Closed)`) puis navigue. Format `tap <item> → <Écran>` :

**Section ACTIVITÉ :**
- tap **Notifications** → écran Notifications (`Routes.NOTIFICATIONS`)
- tap **Conversations** → écran Conversations (`Routes.CONVERSATIONS`)
- tap **Tâches** → écran Tasks (Daily + Agenda unifié) (`Routes.TASKS`)
- tap **{séance du jour}** → écran Séance du jour `Routes.session(uuid)` si une séance existe, **sinon** → Accueil (`Routes.HOME`)
- tap **Programme** → écran Program (`Routes.PROGRAM`)
- tap **Exercices** → écran Exercises (`Routes.EXERCISES`)
- tap **Muscles** → écran Muscles (`Routes.MUSCLES`)
- tap **Calendrier** → écran Calendar (`Routes.CALENDAR`)
- tap **Statistiques** → écran Stats (`Routes.STATS`)

**Section COMPTE & PARAMÈTRES :**
- tap **Profil** → écran Profile (`Routes.PROFILE`)
- tap **Paramètres** → écran Settings (`Routes.SETTINGS`)
- tap **Exporter les données** → écran Export Data (`Routes.EXPORT_DATAS`)
- tap **Citations motivantes** → écran Quotes (`Routes.QUOTES`)
- tap **Déconnexion** → écran/route Logout (`Routes.LOGOUT`)
- tap **Paramètres de sync** → écran Sync Settings (`Routes.SYNC_SETTINGS`)

**Section ADMINISTRATION (si admin) :**
- tap **Gérer les utilisateurs** → écran Admin Users (`Routes.ADMIN_USERS`)
- tap **UI Showcase** → écran UI Showcase (`Routes.ADMIN_UI_SHOWCASE`)
- tap **Paramètres de sync** → écran Sync Settings (`Routes.SYNC_SETTINGS`)

**Footer (ne ferment PAS le drawer) :**
- tap **icône sync (cloud)** → déclenche `syncAllAndRefresh()` (sync manuelle, reste dans le drawer)
- tap **icône routeur (WS)** → si WS déconnecté : `restartWebSocket(token)` ; sinon no-op
- tap **icône signal réseau** → non cliquable (indicateur seul)

**Hors items :**
- tap **scrim (zone droite 112dp)** → ferme le drawer
- swipe gauche / geste → ferme le drawer (`gesturesEnabled`)

---

## 3. Inventaire composants

| Composant | Fichier | But | Contenu clé |
|---|---|---|---|
| **DrawerContent** | `DrawerContent.kt` | Conteneur racine du drawer : `ModalDrawerSheet` (300dp) + `LazyColumn` ; assemble les 3 sections + footer + spacer ; calcule la progression de la séance du jour et les compteurs | header ABSENT ; 9 items Activité, 6 items Compte, 3 items Admin (cond.), footer |
| **DrawerSection** | `DrawerSection.kt` | Bloc de section réutilisable : titre centré bleu uppercase + liste de rows séparées par dividers | fond `bgRecessed` ; titre 13sp Bold `accentText` ; rows : icône 22dp + label 14sp Medium `textPrimary` + slot trailing à droite |
| **DrawerItem** | `DrawerItem.kt` | data class (PAS un composable) : `title`, `iconRes`, `trailingContent?`, `onClick` | modèle d'une row |
| **DrawerMiniProgress** | `DrawerMiniProgress.kt` | Mini barre de progression + % à droite de la row "séance du jour" | barre 60dp × 6dp, RoundedCornerShape 2dp, trough `bgSurface` `#1E2A3C` ; couleur barre/texte selon % (rouge<20% / orange<50% / vert clair<75% / vert<100% / bleu=100%) ; texte `"X%"` 12sp SemiBold |
| **DrawerIconCountIndicator** | `DrawerIconCountIndicator.kt` | Petit groupe icône + nombre (trailing Notifications = mail + non-lus) | icône 16dp + nombre 12sp SemiBold ; **caché si count ≤ 0** (showWhenZero=false) ; teinte par défaut `primaryAction` |
| **DrawerFooter** | `DrawerFooter.kt` | Barre de statut sync en bas : texte "Last sync" + 3 icônes d'état (réseau / cloud-sync+badge / WS) | `ActionIconButton` sans fond ; couleurs conditionnelles vert/orange/rouge/bleu ; badge M3 si pending>0 |
| **TasksTodayStatsBadge** | `DrawerContent.kt` (private, l.398) | Pill `"done/total"` à droite de la row Tâches | RoundedCornerShape 8dp, fond couleur α0.15, texte 12sp Medium ; vert si tout fait / orange en cours / gris si 0 ; padding 12dp×2dp ; **cachée si total==0** |
| **DrawerViewModel** | `DrawerViewModel.kt` | Source des données dynamiques | `unreadNotificationsCount`, `tasksTodayStats` (done/total), `todaySession` (nom + progression), `totalPendingCount`, `hasUnsyncedData`, `isWsConnected`, `lastSyncText` (relatif, tick 1s) |

> **Section Admin conditionnelle** : rendue par un `DrawerSection` standard imbriqué dans `if (isAdmin) { item { … } }` (DrawerContent.kt l.316-355). Ce n'est pas un composant séparé — c'est `DrawerSection` réutilisé avec titre "ADMINISTRATION" + 3 items, précédé d'un divider 1.5dp.

---

## 4. Notes (états / variantes)

### Variantes à produire (recommandé : 2 frames)
1. **Drawer ADMIN** (= screenshots) : section ADMINISTRATION présente (3 items). C'est le cas des screenshots (l'utilisateur `will` est admin sur device).
2. **Drawer NON-ADMIN** : section ADMINISTRATION + son divider **complètement absents** ; le footer remonte juste après la section "COMPTE & PARAMÈTRES".

### États dynamiques des trailings / footer (figer les valeurs des screenshots)
- **Tâches** : pill `0/11` **orange** (en cours, done<total). Caché si aucune tâche aujourd'hui.
- **Séance du jour** : label `Core & Mobility` + mini-barre + `0%` **rouge** (0% → rouge, <20%). Si pas de séance : label `Aucune séance` / `No session`, **aucun** trailing.
- **Notifications** : screenshot = **pas** de trailing (0 non-lu). Variante possible : icône mail bleue + nombre si non-lus > 0.
- **Footer (screenshot = tout vert/connecté)** :
  - Texte : `Dernière sync : 2 min ago`.
  - Réseau : barres **vertes** (`ic_baseline_signal_cellular_alt`, connecté).
  - Cloud-sync : nuage **bleu ✓** (`ic_cloud_done`, tout synced), **pas de badge**.
  - WS : routeur **vert** (`ic_rounded_router`, connecté).
- **Variante footer OFFLINE / unsynced** (utile pour flow map états) : réseau **rouge** (`ic_rounded_signal_cellular_off`), cloud **orange ↑** (`ic_rounded_cloud_upload`) + **badge rouge** `N`, WS **orange** (`ic_rounded_router_off`).

### Thème
- Mockup principal en **dark** (screenshots). Tokens dark résolus en §5. (Un thème light existe — `appColorsLight` — mais hors périmètre des screenshots.)

### Pièges fidélité
- **Pas de header utilisateur** (avatar/nom) — ne pas en ajouter.
- Titres de section **centrés** (pas alignés à gauche) et en **bleu clair** `#4FC3F7`, uppercase.
- Fond des sections (`bgRecessed` `#091216`) **plus sombre** que le fond drawer (`bgScreen` `#101720`) — léger contraste visible entre blocs et marges.
- Dividers : 3 épaisseurs/opacités différentes (2.5dp α0.6 sous titre ; 2dp α0.30 entre items ; 1.5dp plein entre sections). Tous en teinte `firstBlue` `#153A6B`.
- Icônes des items en **blanc** (`textPrimary`), 22 dp. Labels blancs 14 sp Medium.
- Le **scrim** occupe les 112 dp de droite : laisser deviner l'écran derrière (cards + chevrons `>`) sous un voile sombre.

---

## 5. Tokens couleur (dark) — valeurs hex résolues

| Token / usage | Primitive | Hex |
|---|---|---|
| `bgScreen` (fond drawer) | blueBackground | `#101720` |
| `bgRecessed` (fond sections) | thirdBlue | `#091216` |
| `bgSurface` (trough mini-progress) | boxBlue | `#1E2A3C` |
| `accentText` (titres sections) | lightBlue | `#4FC3F7` |
| `textPrimary` (labels + icônes items) | White | `#FFFFFF` |
| `textTertiary` (texte footer "Last sync") | LightGray | `#D3D3D3` |
| `dividerStrong` (tous les dividers) | firstBlue | `#153A6B` |
| `primaryAction` (sync cloud OK, count badge bleu, mini-progress 100%) | ButtonPrimaryColor | `#2377CA` |
| vert (réseau/WS connecté, badge tasks done, mini-progress<100%) | mediumGreen | `#008444` |
| vert clair (mini-progress <75%) | lightGreen | `#00D572` |
| orange (tasks en cours, cloud unsynced, WS off, mini-progress<50%) | orangeMedium | `#C4841F` |
| rouge (réseau off, mini-progress<20%) | redMedium | `#B3403E` |
| gris (pill tasks total==0) | lightGrayBlue | `#7B9DD0` |

---

## 6. Récap rapide de l'arbre (pour assemblage)

```
Frame 412×916 (dark)
├─ [StatusBar mock] 32dp  (fond #101720)
├─ Drawer 300dp (fond #101720)  ── LazyColumn scrollable
│   ├─ Section "ACTIVITÉ" (fond #091216)
│   │   ├─ Titre centré bleu #4FC3F7 uppercase
│   │   ├─ divider 2.5dp α0.6
│   │   ├─ Notifications [+ mail count si >0]
│   │   ├─ Conversations
│   │   ├─ Tâches  [pill 0/11 orange]
│   │   ├─ Core & Mobility (séance jour) [mini-bar + 0% rouge]
│   │   ├─ Programme
│   │   ├─ Exercices
│   │   ├─ Muscles
│   │   ├─ Calendrier
│   │   └─ Statistiques        (dividers 2dp α0.30 entre items)
│   ├─ divider 1.5dp #153A6B
│   ├─ Section "COMPTE & PARAMÈTRES" (fond #091216)
│   │   ├─ Profil
│   │   ├─ Paramètres
│   │   ├─ Exporter les données
│   │   ├─ Citations motivantes   (← absent des screenshots, présent dans le code)
│   │   ├─ Déconnexion
│   │   └─ Paramètres de sync
│   ├─ [if admin] divider 1.5dp + Section "ADMINISTRATION"
│   │   ├─ Gérer les utilisateurs
│   │   ├─ UI Showcase
│   │   └─ Paramètres de sync
│   ├─ divider 1.5dp #153A6B
│   ├─ Footer : "Dernière sync : 2 min ago"  [signal vert][cloud bleu✓ +badge?][routeur vert]
│   └─ Spacer 46dp
├─ [Scrim] 112dp à droite (voile sombre par-dessus écran Home, chevrons > visibles)
└─ [NavBar système mock] ~48dp (optionnel)
```
