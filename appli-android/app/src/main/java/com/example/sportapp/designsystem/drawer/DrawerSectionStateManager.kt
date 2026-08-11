package com.example.sportapp.designsystem.drawer

import com.example.sportapp.app.navigation.NavMode
import com.example.sportapp.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * État déplié/replié des sections thématiques du drawer (accordéon).
 *
 * Transposition fidèle du drawer web Angular (`appli-web/src/app/shell/app-shell.ts`,
 * signal `openSections`) : à l'ouverture, TOUTES les sections sont repliées SAUF
 * celle de la route courante ([sectionKeyForRoute]) ; à chaque navigation, la
 * section de la nouvelle route est dépliée en plus (add-only, [ensureOpen]) sans
 * refermer celles que l'utilisateur a ouvertes manuellement ; le clic sur l'en-tête
 * bascule une section indépendamment des autres ([toggle]). Seule exception à
 * l'add-only : la bascule de mode de la barre basse ([resetTo]) recale l'accordéon
 * sur LA section du mode choisi et referme toutes les autres — et la navigation
 * induite par cette bascule n'en ré-ouvre aucune autre ([suppressNextRouteOpen]),
 * pour que SEULE la section du mode reste ouverte.
 *
 * Session-only (comme le signal web qui repart de zéro à chaque rechargement de
 * page) : l'ensemble vit dans cet `object` singleton et se réinitialise à la mort
 * du process (cold start). Aucune persistance disque — un plié/déplié manuel ne
 * survit qu'à la session en cours, comme sur le web.
 *
 * L'ensemble contient les clés STABLES des sections dépliées (≠ titre i18n
 * localisé), pour ne pas casser au changement de langue.
 */
object DrawerSectionStateManager {

    // Clés stables des sections (indépendantes du libellé i18n localisé).
    const val KEY_GENERAL = "general"
    const val KEY_SPORT = "sport"
    const val KEY_NUTRITION = "nutrition"
    const val KEY_HEALTH = "health"
    const val KEY_ACCOUNT_SETTINGS = "account_settings"
    const val KEY_ADMIN = "admin"

    // Sections actuellement dépliées. Présence = dépliée, absence = repliée.
    private val _openSections = MutableStateFlow<Set<String>>(emptySet())
    val openSections: StateFlow<Set<String>> = _openSections

    // Drapeau one-shot : la navigation induite par une bascule de mode ([resetTo] +
    // navigate vers la page d'accueil du mode) ne doit PAS ré-ouvrir add-only la
    // section de la page d'atterrissage (ex. Sport → Accueil ∈ « Général » rouvrirait
    // Général par-dessus Sport). Le prochain [ensureOpen] de route l'ignore et le désarme.
    private var suppressNextRouteOpen = false

    /** True si la section est dépliée (défaut : repliée tant qu'absente de l'ensemble). */
    fun isExpanded(key: String): Boolean = key in _openSections.value

    /**
     * Arme la suppression du prochain [ensureOpen] de suivi de route. Appelé juste
     * avant la navigation induite par une bascule de mode, pour que SEULE la section
     * du mode (posée par [resetTo]) reste ouverte après l'atterrissage.
     */
    fun suppressNextRouteOpen() {
        suppressNextRouteOpen = true
    }

    /**
     * S'assure que la section est dépliée (add-only) sans toucher aux autres.
     * Miroir de l'effet web de navigation : la section de la route courante s'ouvre,
     * celles ouvertes manuellement restent ouvertes. Exception : si une bascule de
     * mode vient d'armer [suppressNextRouteOpen], ce seul appel est ignoré (désarmé).
     */
    fun ensureOpen(key: String) {
        if (suppressNextRouteOpen) {
            suppressNextRouteOpen = false
            return
        }
        if (key !in _openSections.value) {
            _openSections.value = _openSections.value + key
        }
    }

    /** Bascule manuelle déplié/replié d'une section, indépendamment des autres. */
    fun toggle(key: String) {
        _openSections.value = _openSections.value.let {
            if (key in it) it - key else it + key
        }
    }

    /**
     * Recale l'accordéon sur UNE seule section : déplie [key], replie toutes les
     * autres. Réservé à la bascule de mode de la barre basse (Sport → Nutrition →
     * Santé) — l'ouverture manuelle et le suivi de route restent add-only.
     * La navigation qui suit la bascule est neutralisée par [suppressNextRouteOpen]
     * (armé par l'appelant avant de naviguer) → SEULE [key] reste ouverte, même si
     * la page d'atterrissage appartient à une autre section (ex. Sport → Accueil ∈
     * « Général »).
     */
    fun resetTo(key: String) {
        _openSections.value = setOf(key)
    }
}

/**
 * Section du drawer associée à un mode de navigation (cible de [DrawerSectionStateManager.resetTo]
 * à la bascule de mode) : Sport → « Sport », Nutrition → « Nutrition », Santé → « Santé ».
 */
fun sectionKeyForMode(mode: NavMode): String = when (mode) {
    NavMode.SPORT -> DrawerSectionStateManager.KEY_SPORT
    NavMode.NUTRITION -> DrawerSectionStateManager.KEY_NUTRITION
    NavMode.HEALTH -> DrawerSectionStateManager.KEY_HEALTH
}

/**
 * Section du drawer « propriétaire » d'une route, pour savoir laquelle déplier à
 * l'ouverture / à la navigation. Miroir du helper web `sectionForUrl` : on ramène
 * la route à son segment de base (sans les paramètres `/{...}`) puis on la range
 * dans sa section. Route hors drawer (aucun match) → repli sur « Général », comme
 * le web (jamais tout fermé).
 */
fun sectionKeyForRoute(route: String?): String {
    val base = route?.substringBefore("/") ?: return DrawerSectionStateManager.KEY_GENERAL
    return ROUTE_TO_SECTION[base] ?: DrawerSectionStateManager.KEY_GENERAL
}

// Route de base -> clé de section. Une entrée par écran atteignable ; les écrans
// de détail (ex. muscle/{uuid}) partagent la base de leur section. Général n'est
// pas listé : c'est le repli par défaut.
private val ROUTE_TO_SECTION: Map<String, String> = buildMap {
    // Sport
    put("session", DrawerSectionStateManager.KEY_SPORT) // SESSION_PATTERN = "session/{sessionUUID}"
    put("session_exercise", DrawerSectionStateManager.KEY_SPORT)
    put("planned_workout", DrawerSectionStateManager.KEY_SPORT)
    put(Routes.CALENDAR, DrawerSectionStateManager.KEY_SPORT)
    put(Routes.PROGRAM, DrawerSectionStateManager.KEY_SPORT)
    put(Routes.STATS, DrawerSectionStateManager.KEY_SPORT)
    put(Routes.MATERIAL, DrawerSectionStateManager.KEY_SPORT)
    put("material_detail", DrawerSectionStateManager.KEY_SPORT)
    put(Routes.EXERCISES, DrawerSectionStateManager.KEY_SPORT)
    put("exercise", DrawerSectionStateManager.KEY_SPORT)
    put("exercise_stats", DrawerSectionStateManager.KEY_SPORT)
    put(Routes.MUSCLES, DrawerSectionStateManager.KEY_SPORT)
    put("muscle", DrawerSectionStateManager.KEY_SPORT)
    put("muscle_stats", DrawerSectionStateManager.KEY_SPORT)
    put(Routes.CHRONO, DrawerSectionStateManager.KEY_SPORT)
    // Nutrition
    put(Routes.NUTRITION, DrawerSectionStateManager.KEY_NUTRITION)
    put(Routes.NUTRITION_CATALOGUE, DrawerSectionStateManager.KEY_NUTRITION)
    put(Routes.NUTRITION_RECIPES, DrawerSectionStateManager.KEY_NUTRITION)
    put(Routes.NUTRITION_GOALS, DrawerSectionStateManager.KEY_NUTRITION)
    put(Routes.NUTRITION_STATS, DrawerSectionStateManager.KEY_NUTRITION)
    put("nutrition_food_detail", DrawerSectionStateManager.KEY_NUTRITION)
    // Santé
    put(Routes.HEALTH_DASHBOARD, DrawerSectionStateManager.KEY_HEALTH)
    put(Routes.SETTINGS_HEALTH, DrawerSectionStateManager.KEY_HEALTH)
    // Compte & paramètres
    put(Routes.PROFILE, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.SETTINGS, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.SETTINGS_APPEARANCE, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.SETTINGS_LANGUAGE_FORMAT, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.SETTINGS_NOTIFICATIONS, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.SETTINGS_STARTUP, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.SETTINGS_SERVER_URL, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.EXPORT_DATAS, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    put(Routes.LANGUAGE_DISPLAY, DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
    // Admin
    put(Routes.ADMIN_USERS, DrawerSectionStateManager.KEY_ADMIN)
    put(Routes.ADMIN_UI_SHOWCASE, DrawerSectionStateManager.KEY_ADMIN)
    put(Routes.SYNC_SETTINGS, DrawerSectionStateManager.KEY_ADMIN)
    put("sync_table", DrawerSectionStateManager.KEY_ADMIN)
}
