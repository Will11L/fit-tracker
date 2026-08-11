package com.example.sportapp.app.navigation

/**
 * Constantes des routes Compose Navigation.
 * Centralise les paths pour eviter les magic strings dans
 * MainActivity.NavHost + tous les navigate(...) callsites.
 */
object Routes {
    // Auth
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val SPLASH = "splash"
    const val LOGOUT = "logout"

    // Main
    const val HOME = "home"
    const val CHRONO = "chrono"
    const val STATS = "stats"
    const val CALENDAR = "calendar"

    // Drawer / activities
    const val NOTIFICATIONS = "notifications"
    const val TASKS = "tasks"   // ecran unifie Daily + Agenda (toggle interne, Phase 1.5 2026-05-12)
    const val CONVERSATIONS = "conversations"
    const val PROGRAM = "program"
    const val EXERCISES = "exercises"
    const val MUSCLES = "muscles"
    const val MATERIAL = "material"   // ecran Materiel dedie (catalogue + mon materiel)
    const val QUOTES = "quotes"   // gestion des citations motivantes (user-scoped)
    const val HEALTH_DASHBOARD = "health_dashboard"   // hub Santé (pas/objectif/cardio/sommeil)
    const val NUTRITION = "nutrition"   // Journal nutrition du jour (A2)
    const val NUTRITION_CATALOGUE = "nutrition_catalogue"   // Catalogue d'aliments (A3)
    const val NUTRITION_RECIPES = "nutrition_recipes"   // Recettes & repas enregistrés (A4)
    const val NUTRITION_GOALS = "nutrition_goals"   // Objectifs nutrition (A5)
    const val NUTRITION_STATS = "nutrition_stats"   // Stats nutrition (A6)

    // Account & settings
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val LANGUAGE_DISPLAY = "language_display"   // legacy stub, peut etre supprime (drawer ne pointe plus dessus)
    const val EXPORT_DATAS = "export_datas"
    const val SYNC_SETTINGS = "sync_settings"

    // Sous-ecrans Settings (drill-down depuis SETTINGS)
    const val SETTINGS_APPEARANCE = "settings_appearance"
    const val SETTINGS_LANGUAGE_FORMAT = "settings_language_format"
    const val SETTINGS_NOTIFICATIONS = "settings_notifications"
    const val SETTINGS_STARTUP = "settings_startup"
    const val SETTINGS_SERVER_URL = "settings_server_url"  // admin users uniquement
    const val SETTINGS_HEALTH = "settings_health"  // Connexion Health Connect (lecture santé)

    // Admin (caché si !currentUser.isAdmin -- cf. DrawerContent)
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_UI_SHOWCASE = "admin_ui_showcase"  // gallery atoms/molecules pour comparatif Figma <-> app

    // Sync Settings : écran dédié par table (data grid + filtres + tri + paging)
    const val SYNC_TABLE_DETAIL_PATTERN = "sync_table/{entityName}"
    fun syncTableDetail(entityName: String) = "sync_table/$entityName"
    const val ARG_ENTITY_NAME = "entityName"

    // B1 onboarding (5 écrans guidés post-signup, déclenché 1 fois par user au 1er run)
    const val ONBOARDING = "onboarding"

    // Others
    const val DELAVIER_METHOD = "delavier_method"

    // Param routes : pattern (pour composable) + builder (pour navigate)
    const val EXERCISE_PATTERN = "exercise/{exerciseUUID}"
    fun exercise(uuid: String) = "exercise/$uuid"

    const val MUSCLE_PATTERN = "muscle/{muscleUUID}"
    fun muscle(uuid: String) = "muscle/$uuid"

    // Detail Materiel : cle = nom du materiel (possession insensible a la casse),
    // encode pour supporter espaces / caracteres speciaux dans le path.
    const val MATERIAL_DETAIL_PATTERN = "material_detail/{equipmentName}"
    fun materialDetail(name: String) = "material_detail/${android.net.Uri.encode(name)}"
    const val ARG_EQUIPMENT_NAME = "equipmentName"

    // SESSION_PATTERN = legacy UI ; data renomee actual_workout cote serveur (V7.4-E).
    const val SESSION_PATTERN = "session/{sessionUUID}"
    fun session(uuid: String) = "session/$uuid"

    const val SESSION_EXERCISE_PATTERN = "session_exercise/{actualWorkoutExerciseUUID}"
    fun sessionExercise(uuid: String) = "session_exercise/$uuid"

    const val PLANNED_WORKOUT_PATTERN = "planned_workout/{plannedWorkoutUUID}"
    fun plannedWorkout(uuid: String) = "planned_workout/$uuid"

    // B3-2 Stats sous-ecrans (2026-05-07)
    const val MUSCLE_STATS_PATTERN = "muscle_stats/{muscleUUID}"
    fun muscleStats(uuid: String) = "muscle_stats/$uuid"

    const val EXERCISE_STATS_PATTERN = "exercise_stats/{exerciseUUID}"
    fun exerciseStats(uuid: String) = "exercise_stats/$uuid"

    const val NUTRITION_FOOD_DETAIL_PATTERN = "nutrition_food_detail/{foodUUID}"   // Détail d'un aliment du catalogue
    fun nutritionFoodDetail(uuid: String) = "nutrition_food_detail/$uuid"

    // Cles d'arguments pour backStackEntry.arguments?.getString(...)
    const val ARG_EXERCISE_UUID = "exerciseUUID"
    const val ARG_MUSCLE_UUID = "muscleUUID"
    const val ARG_SESSION_UUID = "sessionUUID"
    const val ARG_SESSION_EXERCISE_UUID = "actualWorkoutExerciseUUID"
    const val ARG_PLANNED_WORKOUT_UUID = "plannedWorkoutUUID"
    const val ARG_FOOD_UUID = "foodUUID"
}
