# SPEC D'ASSEMBLAGE FIGMA — Feature Login / Auth (sport-app, Android Compose)

> Cible Figma : **frame 412 × 916** (Samsung S21+, densité xxhdpi). Écran **plein, pré-authentification → AUCUNE bottom nav bar.**
> Sources lues : `feature/auth/ui/LoginScreen.kt`, `SignupScreen.kt`, `SplashScreen.kt` + ViewModels + `designsystem/common_components/{CustomTextField,ActionIconWithTextButton}.kt` + `designsystem/theme/{AppColors,Color}.kt` + `app/MainActivity.kt` (NavHost) + `app/navigation/Routes.kt` + `res/values/strings.xml`.
> Référence visuelle : `.figma-refs/Screenshots/01_login_filled.jpg` (état « champs remplis », thème **dark**, `will` / mot de passe masqué).

---

## 0. Tokens couleur (thème DARK — celui du screenshot)

Tous les écrans auth sont rendus en **dark** (`appColorsDark`). Valeurs HEX résolues à utiliser dans Figma :

| Token sémantique (code) | Primitive | HEX | Usage dans cet écran |
|---|---|---|---|
| `bgScreen` / `MaterialTheme.background` | `blueBackground` | **#101720** | Fond plein de l'écran (quasi-noir bleuté) |
| `bgBottomNav` | `secondBlue` | **#16202C**¹ | Fond de la **Card** qui entoure les champs (légèrement plus clair que le fond) |
| `bgRecessed` | `thirdBlue` | **#091216** | Fond **intérieur** des champs de saisie (TextField, plus sombre) |
| `primaryAction` | `ButtonPrimaryColor` | **#2377CA** | Titre « Fit Tracker », labels de champ actifs, liens, soulignement champ focus |
| `blueMedium` | — | **#245682** | **Fond du bouton Login** (bleu moyen, plus terne que primaryAction) + sous-titre |
| `textPrimary` | `Color.White` | **#FFFFFF** | Texte saisi (« will »), texte du bouton, points du mot de passe |
| `textTertiary` | `Color.LightGray` | **#D3D3D3** | Placeholders / hints |

¹ `secondBlue` non listé dans le grep ; déduire une teinte entre `blueBackground` (#101720) et `boxBlue`. Dans le screenshot la Card est **à peine** plus claire que le fond : viser **#16202C** (±). Le fond des champs (`thirdBlue` #091216) est lui plus **sombre** que la Card — bien respecter ce contraste « champ enfoncé dans une carte légèrement surélevée ».

Gradient de fond : `Brush.verticalGradient(background → background·alpha0.92)` = quasi imperceptible. **En Figma : fond uni #101720 suffit.**

---

## 1. Layout écran (haut → bas) — LOGIN (écran de référence)

Structure racine : `Box(fillMaxSize, padding 20dp)` + `Column(align Center, centré horizontalement)`. **Le bloc est centré verticalement** dans la frame (pas ancré en haut). Reproduire ce centrage : le groupe logo→lien doit être centré dans la zone utile (entre StatusBar et SystemNav).

| # | Section | Composant Compose (fichier) | Contenu / props visibles | Hauteur approx (dp) |
|---|---|---|---|---|
| 0a | **Status bar** (chrome OS) | — (système) | Barre 412dp de large, fond #101720. Gauche : heure « 15:33 » blanche. Droite : icônes clé/vibreur/wifi/signal + badge batterie « 96 ». | **32** |
| 1 | **Logo app** | `Image(R.drawable.ic_loading_screen)` `.size(96.dp)` | Icône carrée arrondie : tuile bleu nuit foncé, **haltère blanc** en haut + **« FT » bleu (#2377CA)** dessous. Centrée. | **96** (carré 96×96) |
| — | *spacer* | `Spacer(36.dp)` | | 36 |
| 2 | **Nom de marque** | `Text` `headlineMedium`, color `primaryAction` | « **Fit Tracker** » — `@string/auth_brand_name`. Bleu #2377CA, ~28sp, centré. | ~36 |
| — | *spacer* | `Spacer(12.dp)` | | 12 |
| 3 | **Sous-titre** | `Text` `bodyMedium`, color `blueMedium` | « Sign in to synchronize your data » — `@string/auth_login_subtitle`. Bleu terne #245682, ~16sp, centré. | ~22 |
| — | *spacer* | `Spacer(56.dp)` | (gros espace avant la carte) | 56 |
| 4 | **Card formulaire** | `Surface(shape RoundedCornerShape 16dp, color bgBottomNav, tonalElevation 2dp)` → `Column(padding 16dp, spacedBy 12dp)` | Carte arrondie 16dp, fond #16202C, **largeur = fillMaxWidth** (donc 412 − 2×20 = **372dp** de large). Contient les 3 sous-éléments 4a/4b/4c. | **~300** (somme ci-dessous) |
| 4a | → Champ **Username** | `CustomTextField` | Label « Username » (`auth_username`) en bleu #2377CA au-dessus (label flottant Material, remonté car champ rempli). Valeur saisie « **will** » en blanc. Fond interne #091216. Placeholder (si vide) « Ex: will ». **Pas** de soulignement coloré (non focus). | ~70 |
| 4b | → Champ **Password** | `CustomTextField` (`PasswordVisualTransformation`) | Label « Password » (`auth_password`) bleu #2377CA. Valeur masquée = **suite de points** « •••••••• » blancs + **curseur** vertical clignotant. Fond #091216. **Soulignement bleu vif #2377CA** en bas (= champ **focus** dans le screenshot). | ~70 |
| — | *spacer interne* | `Spacer(6.dp)` | | 6 |
| 4c | → Bouton **Login** | `ActionIconWithTextButton(iconRes ic_account_circle, backgroundColor blueMedium, fillMaxWidth)` | Bouton pleine largeur, **fond #245682**, coins arrondis (shapes.small ≈ 8–12dp). Centré : **icône « compte » ronde blanche** (cercle + buste) + 8dp + texte « **Login** » blanc 14sp. (`auth_login_button`. Pendant chargement → « Signing in… » `auth_loading_login`.) Hauteur du bouton ≈ 44–48dp. | ~48 |
| — | *spacer* | `Spacer(20.dp)` | | 20 |
| 5 | **Lien créer un compte** | `Text` `bodySmall`, color `primaryAction`, `clickable` | « **No account yet? Create one** » — `@string/auth_no_account_action`. Bleu #2377CA, ~14sp, centré, cliquable. | ~20 |
| 0b | **System nav bar** (chrome OS) | — (système) | Barre du bas 412dp, fond #101720. 3 boutons gestes blancs : ▮▮▮ (récents) · ◯ (home) · ‹ (retour). **Pas de BottomNavBar applicative.** | **48** |

**Carte 4 — détail dimensionnel** : 16dp padding haut + champ(70) + 12 + champ(70) + 6 + bouton(48) + 16dp padding bas ≈ **~290–300dp**. Largeur 372dp. Le screenshot montre exactement cette structure : carte sombre arrondie englobant les 2 champs + bouton, avec le **2ᵉ champ souligné en bleu** (focus).

> **Anatomie d'un `CustomTextField`** (à reproduire fidèlement) : conteneur arrondi fond `bgRecessed` #091216 ; **label flottant** en haut (couleur `primaryAction` #2377CA quand rempli/focus, sinon `textTertiary` gris) ; texte de valeur blanc ; **indicateur (ligne)** sous le champ : `primaryAction` bleu si focus, **transparent** sinon. C'est un `TextField` Material3 (style « filled », pas outlined) → pas de bordure complète, juste fond + ligne basse.

---

## 2. Interactions (flow map)

Navigation réelle confirmée dans `MainActivity.kt` (NavHost, lignes 512–545) + `SplashScreenViewModel`.

| Geste | Élément | Action / destination |
|---|---|---|
| tap | **Champ Username** | focus → clavier texte (ImeAction **Next** → passe au champ Password). Label remonte, fond inchangé. |
| tap | **Champ Password** | focus → clavier mot de passe masqué (ImeAction **Done**). Soulignement bleu apparaît. « Done » au clavier → si username+password non vides → déclenche `login`. |
| tap | **Bouton Login** | `vm.login(onLoginSuccess)`. **Actif uniquement si** username **et** password non vides (`canSubmit`). Appel `RetrofitInstance.login`. **Succès** → snackbar SUCCESS « Successful login! » puis `onLoginSuccess` → navigate **SPLASH** (popUpTo LOGIN inclusive). **Échec** → snackbar ERROR « Login failed. », reste sur Login. |
| (auto) | après **SPLASH** | `SplashScreenViewModel` : sync puis route finale = **Onboarding** (si 1er run, user pas « done ») **sinon** l'écran de démarrage préféré (`startScreen` : **Home** par défaut, ou Tasks/Calendar/Stats/Chrono/Program/Notifications/Conversations). |
| tap | **« No account yet? Create one »** | `onCreateAccount` → navigate **SIGNUP** (`Routes.SIGNUP`). Désactivé pendant `isLoading`. |

**Résumé flux haut-niveau** :
`SplashScreen` (au lancement) → si pas de token → **LoginScreen**.
`LoginScreen` —Login OK→ **SplashScreen** —(sync)→ **Onboarding** ou **Home/startScreen**.
`LoginScreen` —lien→ **SignupScreen** —Create account OK→ (auto-login) → **SplashScreen** → Home/Onboarding.
`SignupScreen` —lien→ retour **LoginScreen**.

---

## 3. Inventaire écrans liés

### 3.1 SignupScreen — `feature/auth/ui/SignupScreen.kt`
But : créer un compte (puis auto-login). Même squelette visuel que Login (Box+gradient → Column centré → logo → titres → Card → lien), mais **logo plus petit (72dp)**, titre `headlineSmall`, et **5 champs** au lieu de 2 (`Column spacedBy 10dp`).

Layout haut→bas (mêmes tokens couleur / même chrome StatusBar 32 / SystemNav 48) :
1. **Logo** `ic_loading_screen` `.size(72.dp)`. → spacer 20dp
2. **Titre** « Create your account » (`auth_signup_title_create`, `headlineSmall`, `primaryAction`). → spacer 8dp
3. **Sous-titre** « Sign up to start tracking » (`auth_signup_subtitle`, `bodySmall`, `blueMedium`). → spacer 28dp
4. **Card** (`Surface` 16dp, `bgBottomNav`, padding 16dp, `spacedBy 10dp`) contenant 5 `CustomTextField` puis le bouton :
   - **Username** — label « Username (3+ chars) » (`auth_signup_username_label`), placeholder « Ex: alice » (`auth_signup_username_placeholder`). ImeAction Next.
   - **Password** — label « Password (8+ chars) » (`auth_signup_password_label`), placeholder « •••••••• », masqué.
   - **Confirm password** — label « Confirm password » (`auth_confirm_password`), masqué.
   - **First name** — label « First name » (`auth_first_name`), placeholder « Optional » (`auth_signup_optional_placeholder`).
   - **Last name** — label « Last name » (`auth_last_name`), placeholder « Optional », ImeAction Done.
   - *spacer 6dp* puis **Bouton** `ActionIconWithTextButton` (icône `ic_account_circle`, fond `blueMedium`, fillMaxWidth) texte « **Create account** » (`auth_signup_button_create`) / pendant chargement « Creating… » (`auth_loading_signup`).
5. *spacer 20dp* → **Lien** « Already have an account? Sign in » (`auth_have_account_action`, `bodySmall`, `primaryAction`, cliquable).

Interactions Signup :
- **Create account** → `vm.signup`. **Actif si** username+password+confirm non vides. Validation client : username ≥ 3, password ≥ 8, password == confirm (sinon snackbar ERROR de validation). Succès → POST signup + auto-login → snackbar SUCCESS « Welcome … » → navigate **SPLASH** (popUpTo LOGIN inclusive). Erreurs HTTP mappées en snackbars : **409** username pris, **422** entrée invalide, **429** trop de tentatives, autre = « failed (code) ».
- **Lien « Already have an account? »** → `onBackToLogin` → retour **LoginScreen**.

### 3.2 Dialogs
**Aucun `Dialog` / `AlertDialog`** dans le flux Login ni Signup. Les retours utilisateur passent **uniquement par des snackbars** (overlay flottant géré globalement, hors de ces écrans). Pas de pop-up modale à dessiner.

### 3.3 SplashScreen (contexte amont/aval, hors périmètre principal)
`feature/auth/ui/SplashScreen.kt` — écran de chargement entre Login et Home. Fond #101720, centré : « Fit Tracker » (28sp, primaryAction) → logo 120dp → « Loading… » → (citation motivante optionnelle, italique-like, primaryAction) → `CircularProgressIndicator` 64dp → texte d'étape (« Authenticating… » / « Synchronizing data… » / « Finalizing… ») → `LinearProgressIndicator` (70% largeur, 6dp). **Non requis pour le mockup Login** sauf si la flow map veut le nœud intermédiaire.

---

## 4. Notes — états, variantes, fidélité

### États du LoginScreen (à prévoir comme variantes Figma si besoin)
- **Champs remplis (= screenshot 01_login_filled, état CANONIQUE à reproduire)** : username « will » visible, password masqué « •••••••• » + curseur, **champ Password focus** (soulignement bleu #2377CA), labels remontés en bleu, bouton Login **plein** (#245682) cliquable.
- **Vide / initial** : champs vides → **placeholders** gris clair affichés (« Ex: will » / « •••••••• »), labels en gris `textTertiary` (pas remontés en bleu tant que vide ET non focus), **pas** de soulignement, bouton Login **présent mais inactif** (`clickable=false` ; le composant ne grise pas la couleur de fond — il reste #245682 mais ne réagit pas au tap). 
- **Loading** (`isLoading=true`) : texte du bouton → « Signing in… », lien « Create one » non cliquable. (Pas de spinner dans le bouton — seul le label change.)
- **Erreur de login** : aucun changement inline sur l'écran → **snackbar ERROR** rouge (`snackbarError` = #C… rouge medium) « Login failed. » apparaît (overlay bas).

### Focus / label flottant
Le label d'un `CustomTextField` est **en haut, à l'intérieur** de la zone du champ (Material filled, label flottant) — couleur `primaryAction` #2377CA si focus **ou** champ non vide, sinon `textTertiary`. Bien reproduire les deux labels « Username » / « Password » en **bleu** dans l'état rempli.

### Variantes build (debug PC vs release Pi)
**Aucune différence d'UI.** Les 2 build variants ne changent que `BuildConfig.API_BASE_URL` / `WS_BASE_URL` (debug → `<pc-lan-ip>:8000`, release → Pi Tailscale HTTPS) injectés dans la couche réseau. Le rendu Login/Signup est strictement identique. Rien à dessiner de spécifique.

### Thème clair (info, non utilisé par le screenshot)
L'app supporte un thème light (`appColorsLight`) : fond blanc, carte #F2F5F9, champs #E1E7EF, `primaryAction` inchangé #2377CA. Le mockup de référence est **dark** — ne produire le light qu'en variante secondaire si explicitement demandé.

### Assets
- **Logo** : `R.drawable.ic_loading_screen` = tuile bleu-nuit arrondie, haltère blanc + « FT » bleu. Réutilisé en 96dp (Login), 72dp (Signup), 120dp (Splash).
- **Icône bouton** : `R.drawable.ic_account_circle` = pictogramme « compte » (cercle + silhouette buste), tinté blanc, 24dp, à gauche du label dans le bouton.

### Dimensions clés à respecter (frame 412×916)
- Padding écran : **20dp** sur tous les bords.
- Largeur carte / bouton : **372dp** (= 412 − 40).
- Rayon carte : **16dp** ; rayon bouton : `shapes.small` (~8–12dp) ; rayon champ : Material filled (haut arrondi léger).
- Chrome : StatusBar **32dp** haut, SystemNav **48dp** bas, **aucune** BottomNavBar (écran pré-auth).
