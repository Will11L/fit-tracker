# Plan i18n EN / FR — sport-app

> ✅ **PLAN LIVRÉ LE 2026-05-12 — DOC FIGÉ.** i18n EN/FR opérationnelle en prod (politique 18 dans [CLAUDE.md](../CLAUDE.md)). `values/strings.xml` + `values-fr/strings.xml` en place ; tout nouveau texte UI passe par `stringResource(R.string.xxx)`. **Approche finale différente du plan §2.2** : `CompositionLocalProvider(LocalContext, LocalConfiguration)` dans `MainActivity` (live switching propre) au lieu de `AppCompatDelegate.setApplicationLocales` (qui crashait sur `ComponentActivity`). Doc conservé comme record de la phase de préparation.

> **Préparé 2026-05-11**. À implémenter en 3 sessions consécutives (A → B → C).
> Référence pour Claude reprise de session : *"Lis ce fichier complet avant
> d'écrire du code, puis confirme le scope de la session courante."*

---

## 1. Contexte

`sport-app` est un monorepo (FastAPI serveur + Android client Compose). L'app
est aujourd'hui **EN hardcodé partout** (~100-200 strings dispersées). Pas de
`res/values-XX/`. Aucun `stringResource()` dans le code Compose (sauf
quelques rares cas type `R.string.app_name`).

L'utilisateur veut un **Language picker EN / FR** dans l'onboarding step
PREFERENCES + locale switching runtime. Architecture confirmée :

- **Côté client** uniquement (serveur reste agnostique langue).
- Pattern Android natif `res/values/` (EN default) + `res/values-fr/` (FR).
- Runtime via `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))`.
- Locale persistée dans `OnboardingPreferences` DataStore (clé `locale`).
- Toggle UI dans `OnboardingPreferencesScreen` step PREFERENCES (déjà step 2
  depuis le reorder 2026-05-11).

---

## 2. Architecture cible

### 2.1 Fichiers ressources

```
appli-android/app/src/main/res/
├── values/
│   └── strings.xml          # EN (default)
└── values-fr/
    └── strings.xml          # FR
```

### 2.2 DataStore + LocaleHelper

`OnboardingPreferences` étendu avec :

```kotlin
enum class AppLocale(val tag: String) { EN("en"), FR("fr") }

data class OnboardingPreferences(
    // ...existing fields...
    val appLocale: AppLocale = AppLocale.EN,
)
```

`OnboardingDataStore` étend ses Keys avec `LOCALE = stringPreferencesKey("locale")`.

Nouveau `LocaleHelper.kt` dans `data/` ou `utils/` :

```kotlin
object LocaleHelper {
    fun apply(locale: AppLocale) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(locale.tag)
        )
    }
}
```

Appelé :
- Au boot de l'app (lecture DataStore → apply).
- Au changement dans PREFERENCES step (apply immédiat + persistance DataStore).

### 2.3 UI

Card "Language" dans `OnboardingPreferencesScreen` avec
`RadioOptions(options = listOf(EN to "English", FR to "Français"))`.

Ajoutable AUSSI dans `SettingsScreen` après l'onboarding (pour switch
post-onboarding).

---

## 3. Plan en 3 sessions

### Session A — Infra + onboarding (~1.5h)

**Livrables :**

1. `AppLocale` enum dans `onboarding/data/OnboardingPreferences.kt`.
2. Persistance `locale` dans `OnboardingDataStore`.
3. `LocaleHelper` Singleton.
4. Appel `LocaleHelper.apply()` au boot dans `Application.onCreate` (ou
   `MainActivity.onCreate`) après lecture DataStore.
5. Setter `setAppLocale(locale)` dans `OnboardingViewModel` qui :
   - update `_preferencesDraft` (comme weightUnit, etc.).
   - appelle `LocaleHelper.apply()` immédiat (live preview, l'écran flippe
     EN ↔ FR sous les yeux de l'user).
6. Card "Language" dans `OnboardingPreferencesScreen` (RadioGroup
   EN/Français).
7. **Extraction strings** des 5 fichiers onboarding/ :
   - `OnboardingWelcomeScreen.kt`
   - `OnboardingPreferencesScreen.kt`
   - `OnboardingBioScreen.kt`
   - `OnboardingPermissionsScreen.kt`
   - `OnboardingScreen.kt` + `OnboardingHeader.kt` + `OnboardingFooter.kt`
   - `BirthDateField.kt` + `BirthDatePickerDialog.kt` + `MorningTimePickerDialog.kt`
8. Traduction FR pour ces clés dans `values-fr/strings.xml`.
9. Build release + install S21+ + test runtime FR ↔ EN sur onboarding.

**Estimation** : ~30 strings à extraire, ~1.5h total.

**Critère de succès** : toggle Language dans l'onboarding flippe TOUS les
textes de l'onboarding entre EN et FR live. Le reste de l'app peut rester
en EN pour cette session.

---

### Session B — Écrans main (~2h)

**Livrables :**

Extraction strings + traduction FR pour :

1. `HomeScreen.kt` + `NoSessionFallbackScreen.kt` (Currently sleeping, View
   program, Start a new session, etc.).
2. `StatsScreen.kt` + sous-écrans (Training frequency, Volume / Zone,
   Sets / Group, range chips 30j/3m/6m/1an/Custom, etc.). Attention aux
   formats numériques (Locale.FRENCH pour les `String.format` avec `%f`).
3. `CalendarViewScreen.kt` (weekday labels M T W T F S S, month names —
   déjà gérés via `LocalDate.month.getDisplayName(TextStyle.FULL,
   Locale.ENGLISH)` → passer en `Locale.getDefault()`).
4. `SessionTab.kt` + `SessionExerciseScreen.kt` (Warm-Up / Training /
   Post-Training, Session Completion, etc.).
5. `WeekViewScreen.kt` (Week Completion, Days, Rest Day, etc.).
6. `ChronoScreen.kt` + `StopwatchPage.kt` + `TimerPage.kt` (Stopwatch /
   Timer / Start / Pause / Lap / Reset / etc.).

**Estimation** : ~60-80 strings.

**Critère de succès** : navigate tous les écrans main en FR sans aucune
string EN qui traîne.

---

### Session C — Reste de l'app + traduction finale (~1.5h)

**Livrables :**

1. Extraction strings + traduction FR pour :
   - `SettingsScreen.kt` + `SyncSettingsScreen.kt` + `LanguageDisplayScreen.kt`.
   - `ProfileScreen.kt`.
   - `AdminUsersScreen.kt` (admin/`).
   - `Drawer/DrawerContent.kt` + tous les items.
   - `LoginScreen.kt` + `SignupScreen.kt` + `LogoutScreen.kt`.
   - `NotificationsScreen.kt`.
   - `RoutineTasksScreen.kt`.
   - `ExerciseListScreen.kt` + `ExerciseScreen.kt`.
   - `MuscleListScreen.kt` + `MuscleScreen.kt`.
   - `PlannedWorkoutScreen.kt`.
   - `DelavierMethodScreen.kt`.
   - `GoalsTabContent.kt`.
   - `ExportDatasScreen.kt`.
   - `ConversationsScreen.kt`.
2. **Snackbar messages** : tous les `showSnackbar(message = "...")` dispersés
   dans les ViewModels.
3. **NotificationCenter** : titres + corps des notifications push système.
4. **Demo tour** : `DemoTourStep.kt` (10 captions title/body).
5. **TODO list final** : extraire à la grep `Text("` et `text = "..."` pour
   ne rien oublier.
6. Ajout option Language dans `SettingsScreen` aussi (post-onboarding).

**Estimation** : ~50-70 strings.

**Critère de succès** : grep `text = "[A-Z][a-z]+ "` dans tout le code
Compose retourne 0 hit. Tout est dans `R.string.xxx`.

---

## 4. Convention de nommage des clés `strings.xml`

Pour faciliter les recherches et la maintenance :

```
<screen>_<section>_<purpose>
```

Exemples :

- `onboarding_welcome_title` = "Welcome to %1$s"
- `onboarding_welcome_subtitle` = "Let's set up your tracking experience in 4 quick steps."
- `onboarding_welcome_prompt_name` = "How should we call you?"
- `onboarding_welcome_placeholder_name` = "Your first name"
- `onboarding_welcome_label_name` = "First name (optional)"
- `onboarding_bio_title` = "About you"
- `onboarding_bio_subtitle` = "Optional — helps us personalize..."
- `onboarding_bio_card_birthdate` = "Birth date"
- `onboarding_bio_card_sex` = "Sex"
- `onboarding_bio_radio_male` = "Male"
- `onboarding_bio_radio_female` = "Female"
- `onboarding_bio_radio_other` = "Other"
- `onboarding_bio_card_height` = "Height"
- `onboarding_bio_card_weight` = "Weight"
- `onboarding_bio_placeholder_height_cm` = "e.g. 175 cm"
- `onboarding_bio_placeholder_height_in` = "e.g. 69 inches"
- `onboarding_bio_placeholder_weight_kg` = "e.g. 72 kg"
- `onboarding_bio_placeholder_weight_lb` = "e.g. 160 lbs"
- `common_finish` = "Finish"
- `common_next` = "Next"
- `common_skip` = "Skip"
- `common_save` = "Save"
- `common_cancel` = "Cancel"
- `common_back` = "Back"
- `home_currently_sleeping` = "Currently sleeping 💤"
- `home_view_program` = "View program"
- `home_start_new_session` = "Start a new session"
- `stats_training_frequency` = "Training frequency"
- `stats_volume_per_zone` = "Volume (%1$s) / Zone"
- ...

Préfixe `common_` pour les strings réutilisées (boutons standards).

---

## 5. Pattern d'extraction (étape par étape, par fichier)

Pour chaque fichier :

1. Grep `Text\("`, `text = "`, `placeholder = "`, `label = "`, `text = "` dans
   le fichier — note toutes les strings à extraire.
2. Pour chaque string :
   - Inventer une clé (cf. §4).
   - Ajouter à `res/values/strings.xml` : `<string name="..."><![CDATA[...]]></string>` (CDATA pour échapper apostrophes/etc.).
   - Ajouter à `res/values-fr/strings.xml` : traduction FR.
   - Remplacer dans le `.kt` par `stringResource(R.string.xxx)` (et import
     `androidx.compose.ui.res.stringResource`).
3. Pour les strings avec **paramètres dynamiques** (ex. nom user, count) :
   `<string name="...">Welcome to %1$s</string>` + appel
   `stringResource(R.string.xxx, value)`.
4. Pour les **pluriels** : utiliser `<plurals>` natif + `pluralStringResource(...)`.
5. Build + vérifier qu'on n'a rien cassé.

---

## 6. Pièges spécifiques sport-app

### 6.1 Strings serveur (UPPER_CASE codes)

Le serveur envoie des codes `"MALE"`, `"FEMALE"`, `"DONE"`, `"NOT_STARTED"`,
`"TRAINING"`, etc. (politique 11 UPPER_CASE cross-stack). **Ne JAMAIS
traduire ces codes en base** — ils restent UPPER_CASE EN canoniques.

Pattern client : créer des fonctions display-only locales par enum-like :

```kotlin
@Composable
fun displaySex(code: String?): String = when (code) {
    "MALE" -> stringResource(R.string.onboarding_bio_radio_male)
    "FEMALE" -> stringResource(R.string.onboarding_bio_radio_female)
    "OTHER" -> stringResource(R.string.onboarding_bio_radio_other)
    else -> "-"
}
```

### 6.2 Names utilisateur (workout / muscle / exercise names)

**JAMAIS traduits.** Si l'user a tapé "Push Day" en EN ou "Jour pectoraux"
en FR, on garde tel quel. Seul le starter pack pré-seed est en EN canonique
(Bench Press, Squat, etc.) — l'user peut renommer s'il veut.

### 6.3 Formats numériques

`String.format("%.1f", 72.5)` donne `"72.5"` en EN-US et `"72,5"` en FR.
**Important** pour la cohérence : utiliser `String.format(Locale.getDefault(), ...)` ou bien
forcer `Locale.US` partout pour les valeurs stockées (cm, kg canoniques).
Pour l'affichage user-facing : `Locale.getDefault()` OK.

### 6.4 Dates

`LocalDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)` est utilisé
plusieurs fois (cf. `CalendarViewScreen.kt`, `BirthDatePickerDialog.kt`).
Passer en `Locale.getDefault()` pour suivre la locale courante.

### 6.5 Demo tour captions

`DemoTourStep.kt` enum porte `title` + `body` en dur dans l'enum. À
extraire vers `strings.xml` ET adapter pour que le `@Composable
captionFor(step)` lise les strings dynamiquement (un enum ne peut pas
contenir `stringResource(R.string.xxx)` directement — il faut une fonction
de mapping `@Composable`).

### 6.6 Snackbar messages

Tous dispersés dans les ViewModels via `showSnackbar(message = "...")`.
**Problème** : les ViewModels n'ont pas accès à `stringResource(...)` (pas
de Composable context). Solutions :
- Injecter `@ApplicationContext context: Context` dans le ViewModel et
  appeler `context.getString(R.string.xxx)`. Pattern Hilt standard.
- OU passer la clé `R.string.xxx: Int` au `showSnackbar` qui résout côté
  Composable.

Reco : option 1 (Context injecté). Plus simple.

### 6.7 NotificationCenter

`NotificationCenter.kt` crée des notifs système avec titre + body. Pareil
que snackbar — accès au Context Android via Hilt. Passer en
`context.getString(R.string.xxx)`.

---

## 7. Validation finale

Après Session C :

1. **Build release** OK.
2. **Install S21+** + clear app data.
3. **Test EN** (default) : tous les écrans rendent en EN.
4. **Switch FR** dans onboarding step PREFERENCES → tous les écrans flippent en FR.
5. **Switch EN** via Settings → tous flippent en EN.
6. **Kill app + relaunch** : la locale persiste.
7. **Grep final** :
   - `grep -rn 'text = "[A-Z]' appli-android/app/src/main/java/com/example/sportapp/ui/` → 0 hit hors `R.string`.
   - `grep -rn 'showSnackbar.*message = "' .../viewmodel/` → 0 hit (tous via `context.getString`).
8. **Vérifier le strings.xml FR** : pas de clé manquante (Android Studio
   souligne en jaune les clés EN sans équivalent FR).

---

## 8. Note sur strings.xml existant

Au démarrage Session A :
- `res/values/strings.xml` n'existe probablement pas (vérifier).
- Si présent, garder `<string name="app_name">Sport App</string>` et autres clés
  déjà extraites.

Aussi : la string "Sport App" est référencée via `R.string.app_name` dans
`OnboardingWelcomeScreen.kt:32` et le `AndroidManifest.xml` (label
application). Donc le `strings.xml` existe DÉJÀ a minima.

---

## 9. Hors scope i18n session A/B/C

Pas dans ce plan, **différé** :

- **Plus de langues** (ES, DE, JP, etc.) — ajouter plus tard juste avec un
  nouveau `values-XX/strings.xml`.
- **i18n des catalogues exercises/muscles** (Bench Press → Développé
  couché) — chantier serveur séparé, pas critique pour MVP.
- **RTL** (arabe, hébreu) — pas de demande, layouts pas testés RTL.
- **Pluriels avancés** russe/polonais — pas demandé.
- **i18n des notifs serveur** (push) — pas nécessaire, le serveur n'envoie
  pas de push avec texte traduit aujourd'hui.

---

## 10. Commits suggérés

Pour traçabilité :

- Session A : `feat(i18n): infra + extraction onboarding strings (EN + FR)`
- Session B : `feat(i18n): extraction main screens (Home/Stats/Calendar/Session/Chrono)`
- Session C : `feat(i18n): extraction settings/admin/drawer/auth + traduction FR finale`
- Bonus : `polish(i18n): grep audit final, missing keys, locale-aware date formatters`

---

**Fin du plan.** Prochaine conversation : démarrer par lire ce fichier puis
proposer Session A en step-by-step + questions avant chaque changement
substantiel (cf. mémoire `feedback_step_by_step.md`).
