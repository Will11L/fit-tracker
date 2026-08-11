package com.example.sportapp.app.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 2026-06-17 (qa-sport) : couvre le COMPORTEMENT OBSERVABLE du mode de navigation
 * persisté (A7 — "bascule Sport/Nutrition", critères "mode persisté qui suit la
 * page" + "survie au cold start"). [NavModeTest] couvre déjà les helpers purs
 * (modeForRoute / nextMode / homeRouteForMode) ; ici on exerce la partie
 * Android : SharedPreferences "nav_prefs" + StateFlow [NavModeManager.mode].
 *
 * [NavModeManager] est un `object` (état in-memory partagé tout le process). Chaque
 * test repart d'un store vierge (clear des prefs) + reset in-memory via init().
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NavModeManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Store vierge + recale l'état in-memory (object singleton) sur le défaut.
        context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        NavModeManager.init(context)
    }

    @Test
    fun `fresh store defaults to SPORT`() {
        assertEquals(NavMode.SPORT, NavModeManager.current)
        assertEquals(NavMode.SPORT, NavModeManager.mode.value)
    }

    @Test
    fun `setMode persists and survives cold start`() {
        NavModeManager.setMode(context, NavMode.NUTRITION)
        assertEquals(NavMode.NUTRITION, NavModeManager.mode.value)

        // Simule un cold start RÉEL : on force l'in-memory à autre chose que ce qui
        // est sur le disque, puis init() doit restaurer la valeur PERSISTÉE (NUTRITION),
        // pas la valeur in-memory. Prouve que la pref survit au redémarrage process.
        forceInMemory(NavMode.SPORT)
        NavModeManager.init(context)
        assertEquals(
            "le mode persisté NUTRITION doit être restauré au cold start",
            NavMode.NUTRITION, NavModeManager.current,
        )
    }

    @Test
    fun `HEALTH mode persists and survives cold start (3e mode)`() {
        NavModeManager.setMode(context, NavMode.HEALTH)
        assertEquals(NavMode.HEALTH, NavModeManager.mode.value)

        forceInMemory(NavMode.SPORT)
        NavModeManager.init(context)
        assertEquals(
            "le mode persisté HEALTH doit être restauré au cold start",
            NavMode.HEALTH, NavModeManager.current,
        )
    }

    @Test
    fun `setMode is a no-op when value unchanged`() {
        // Disque vierge (SPORT par défaut). setMode(SPORT) ne doit RIEN écrire :
        // un init() ultérieur reste sur le défaut SPORT (la clé n'a pas été posée).
        NavModeManager.setMode(context, NavMode.SPORT)
        assertEquals(
            "aucune valeur persistée -> init reste sur le défaut SPORT",
            null,
            context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
                .getString("nav_mode", null),
        )
        assertEquals(NavMode.SPORT, NavModeManager.current)
    }

    @Test
    fun `updateFromRoute follows nutrition routes and persists`() {
        NavModeManager.updateFromRoute(context, Routes.NUTRITION_GOALS)
        assertEquals(NavMode.NUTRITION, NavModeManager.mode.value)

        // Persisté : un cold start le retrouve.
        forceInMemory(NavMode.SPORT)
        NavModeManager.init(context)
        assertEquals(NavMode.NUTRITION, NavModeManager.current)
    }

    @Test
    fun `updateFromRoute on a sport route recales back to SPORT`() {
        NavModeManager.setMode(context, NavMode.NUTRITION)
        assertEquals(NavMode.NUTRITION, NavModeManager.current)

        // Le mode suit la page : naviguer sur une route sport recale en SPORT + persiste.
        NavModeManager.updateFromRoute(context, Routes.STATS)
        assertEquals(NavMode.SPORT, NavModeManager.mode.value)

        forceInMemory(NavMode.NUTRITION)
        NavModeManager.init(context)
        assertEquals(NavMode.SPORT, NavModeManager.current)
    }

    /**
     * Écrit la pref "à la main" (même clé que [NavModeManager]) SANS toucher au
     * StateFlow in-memory, puis force l'in-memory à [memoryValue] via setMode. Sert
     * à recréer l'écart disque≠mémoire d'un vrai cold start avant un init().
     */
    private fun forceInMemory(memoryValue: NavMode) {
        val onDisk = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
            .getString("nav_mode", null)
        NavModeManager.setMode(context, memoryValue)   // recale l'in-memory…
        // …mais setMode a réécrit le disque : on restaure la valeur disque d'origine.
        context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE).edit().apply {
            if (onDisk == null) remove("nav_mode") else putString("nav_mode", onDisk)
            commit()
        }
    }
}
