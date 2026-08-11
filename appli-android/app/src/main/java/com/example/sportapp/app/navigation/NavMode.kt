package com.example.sportapp.app.navigation

/**
 * Mode de navigation par domaine (UPPER_CASE, cf. politique 11) — pilote la barre
 * de navigation basse + l'accent couleur (Sport bleu / Nutrition dark orange
 * #9D5300 / Santé vert #008444).
 * Port 1:1 de `appli-web/src/app/shell/nav-mode.ts` (mode persisté, suit la page).
 */
enum class NavMode { SPORT, NUTRITION, HEALTH }

/**
 * Valeur sentinelle (≠ route Compose) du slot « bascule de mode » de la barre basse.
 * Sur Android le toggle remplace le burger (drawer ouvrable au swipe), d'où une
 * valeur réservée comme l'était `"menu"` auparavant.
 */
const val MODE_TOGGLE_ROUTE = "mode_toggle"

/**
 * Le mode suit la page : toute route nutrition (préfixe "nutrition") → NUTRITION,
 * toute route santé → HEALTH, sinon SPORT. Les 5 routes nutrition (`nutrition`,
 * `nutrition_goals`, `nutrition_stats`, `nutrition_catalogue`, `nutrition_recipes`)
 * commencent toutes par "nutrition" ; aucune route sport ne commence ainsi
 * (`stats`, `muscle_stats`, `exercise_stats` restent du sport). Santé = le hub
 * (`health_dashboard`) + l'écran Données santé (`settings_health`, rangé dans la
 * section drawer Santé — cohérence avec [sectionKeyForRoute]).
 */
fun modeForRoute(route: String?): NavMode = when {
    route == null -> NavMode.SPORT
    route.startsWith("nutrition") -> NavMode.NUTRITION
    route.startsWith("health") || route.startsWith(Routes.SETTINGS_HEALTH) -> NavMode.HEALTH
    else -> NavMode.SPORT
}

/** Mode suivant du cycle (clic sur la bascule) : SPORT → NUTRITION → HEALTH → SPORT (miroir web `nextMode`). */
fun nextMode(mode: NavMode): NavMode = when (mode) {
    NavMode.SPORT -> NavMode.NUTRITION
    NavMode.NUTRITION -> NavMode.HEALTH
    NavMode.HEALTH -> NavMode.SPORT
}

/** Page d'accueil d'un mode : cible de la bascule manuelle + reprise au cold start. */
fun homeRouteForMode(mode: NavMode): String = when (mode) {
    NavMode.SPORT -> Routes.HOME
    NavMode.NUTRITION -> Routes.NUTRITION
    NavMode.HEALTH -> Routes.HEALTH_DASHBOARD
}
