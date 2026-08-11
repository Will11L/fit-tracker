package com.example.sportapp.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde les helpers purs du mode de navigation (A7), miroir de `nav-mode.spec.ts`
 * côté web. Aucune dépendance Android (Routes = object de String constantes).
 */
class NavModeTest {

    @Test
    fun `modeForRoute maps the 5 nutrition routes to NUTRITION`() {
        listOf(
            Routes.NUTRITION,
            Routes.NUTRITION_GOALS,
            Routes.NUTRITION_STATS,
            Routes.NUTRITION_CATALOGUE,
            Routes.NUTRITION_RECIPES,
        ).forEach { route ->
            assertEquals("route=$route", NavMode.NUTRITION, modeForRoute(route))
        }
    }

    @Test
    fun `modeForRoute maps the 2 health routes to HEALTH`() {
        listOf(
            Routes.HEALTH_DASHBOARD,
            Routes.SETTINGS_HEALTH,   // Données santé = domaine Santé (cohérent drawer KEY_HEALTH)
        ).forEach { route ->
            assertEquals("route=$route", NavMode.HEALTH, modeForRoute(route))
        }
    }

    @Test
    fun `modeForRoute maps sport routes (incl null and sport stats) to SPORT`() {
        listOf(
            Routes.HOME,
            Routes.CALENDAR,
            Routes.CHRONO,
            Routes.STATS,
            Routes.PROFILE,
            Routes.SETTINGS,
            Routes.muscleStats("uuid"),   // "muscle_stats/..." ne doit pas matcher "nutrition"
            null,
        ).forEach { route ->
            assertEquals("route=$route", NavMode.SPORT, modeForRoute(route))
        }
    }

    @Test
    fun `nextMode cycles Sport - Nutrition - Health - Sport`() {
        assertEquals(NavMode.NUTRITION, nextMode(NavMode.SPORT))
        assertEquals(NavMode.HEALTH, nextMode(NavMode.NUTRITION))
        assertEquals(NavMode.SPORT, nextMode(NavMode.HEALTH))
    }

    @Test
    fun `homeRouteForMode returns the domain home route`() {
        assertEquals(Routes.HOME, homeRouteForMode(NavMode.SPORT))
        assertEquals(Routes.NUTRITION, homeRouteForMode(NavMode.NUTRITION))
        assertEquals(Routes.HEALTH_DASHBOARD, homeRouteForMode(NavMode.HEALTH))
    }
}
