package com.example.sportapp.feature.onboarding.data

/**
 * Settings client persistés par l'onboarding (étape Preferences) via DataStore.
 * Pas synchronisés serveur -- usage UI local uniquement.
 *
 * Note : `weekStart` impacte le calcul weekIso côté Stats (et idéalement
 * la 1ère colonne du Calendar). `morningRoutineHourMinute` = heure par défaut
 * pré-remplie quand l'user crée une routine_task. `autoSyncOnWifi` = toggle
 * pour autoriser les sync background quand wifi dispo.
 */
data class OnboardingPreferences(
    val weekStart: WeekStart = WeekStart.MONDAY,
    val morningRoutineHour: Int = 6,
    val morningRoutineMinute: Int = 0,
    val autoSyncOnWifi: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLocale: AppLocale = AppLocale.SYSTEM,
    /** Si true au Finish onboarding : insère 5 fake workouts + lance un tour
     *  visuel temporaire (sessions 2+). Données cleanup auto au prochain
     *  ColdStart. Default ON pour qu'un nouveau user voie du contenu. */
    val runDemoTour: Boolean = true,
    /** Ecran ouvert au lancement de l'app (post-splash, post-login, post-onboarding).
     *  Default HOME = Session du jour (comportement historique). */
    val startScreen: StartScreen = StartScreen.HOME,
)

enum class WeekStart { MONDAY, SUNDAY }

/** Unité de poids pour l'affichage (sets, stats, body weight). UPPER_CASE politique 11. */
enum class WeightUnit { KG, LBS }

/** Unité de longueur pour l'affichage (height, mensurations futur). UPPER_CASE politique 11. */
enum class LengthUnit { CM, INCHES }

/** Theme mode user. SYSTEM suit le mode dark/light Android, LIGHT/DARK forcent. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Locale de l'app (i18n Session A 2026-05-11). UPPER_CASE politique 11.
 *  SYSTEM = suit la locale Android système (LocaleListCompat.getEmptyLocaleList()).
 *  EN / FR = force la langue. `tag` null pour SYSTEM, sinon BCP-47. */
enum class AppLocale(val tag: String?) {
    SYSTEM(null),
    EN("en"),
    FR("fr"),
}

/** Ecran de demarrage (Settings : choix de la page d'accueil au launch).
 *  UPPER_CASE politique 11. Le mapping vers Routes.* est dans le SplashScreenViewModel. */
enum class StartScreen { HOME, TASKS, CALENDAR, STATS, CHRONO, PROGRAM, NOTIFICATIONS, CONVERSATIONS }
