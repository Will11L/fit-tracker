package com.example.sportapp.designsystem.drawer

import com.example.sportapp.app.navigation.NavMode
import com.example.sportapp.app.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Couvre le COMPORTEMENT OBSERVABLE de la tâche « drawer : sections repliées par
 * défaut sauf celle du mode actif » — transposition fidèle du drawer web
 * (`appli-web/src/app/shell/app-shell.ts`, signal `openSections`).
 *
 * Le rendu Compose relève du test instrumenté ; ce qui EST testable en JVM, et
 * constitue le contrat de la tâche, vit dans [DrawerSectionStateManager] +
 * [sectionKeyForRoute] :
 *
 *  1. état PAR DÉFAUT = replié (une section absente de l'ensemble est fermée) ;
 *  2. [DrawerSectionStateManager.ensureOpen] déplie une section (add-only,
 *     idempotent) — c'est l'effet web « la section de la route courante s'ouvre » ;
 *  3. [DrawerSectionStateManager.toggle] bascule déplié -> replié -> déplié ;
 *  4. INDÉPENDANCE : ouvrir/fermer une section n'affecte pas les autres ;
 *  5. [sectionKeyForRoute] range chaque route dans sa section, repli « Général »
 *     pour une route hors drawer (jamais tout fermé, comme `sectionForUrl` web) ;
 *  6. [DrawerSectionStateManager.resetTo] (bascule de mode de la barre basse) :
 *     SEULE opération non add-only — ouvre LA section du mode, ferme les autres ;
 *  7. [sectionKeyForMode] associe chaque mode de nav à sa section drawer ;
 *  8. [DrawerSectionStateManager.suppressNextRouteOpen] : après une bascule de mode,
 *     la navigation induite ne ré-ouvre PAS la section d'atterrissage (one-shot).
 *
 * [DrawerSectionStateManager] est un `object` (singleton JVM) à état mutable :
 * chaque test repart d'un état propre en refermant les 6 sections connues et en
 * désarmant un éventuel drapeau [DrawerSectionStateManager.suppressNextRouteOpen].
 */
class DrawerSectionStateManagerTest {

    private val allKeys = listOf(
        DrawerSectionStateManager.KEY_GENERAL,
        DrawerSectionStateManager.KEY_SPORT,
        DrawerSectionStateManager.KEY_NUTRITION,
        DrawerSectionStateManager.KEY_HEALTH,
        DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS,
        DrawerSectionStateManager.KEY_ADMIN,
    )

    // Clé factice (hors sections nommées) pour désarmer le drapeau one-shot en @Before.
    private val SENTINEL = "test_sentinel"

    @Before
    fun resetState() {
        // Referme tout : l'ensemble session-only repart vide (pas de persistance).
        allKeys.forEach { if (DrawerSectionStateManager.isExpanded(it)) DrawerSectionStateManager.toggle(it) }
        // Désarme un drapeau suppressNextRouteOpen resté d'un test précédent : ensureOpen
        // le consomme (early-return sans ouvrir) ; sinon il ouvre la sentinelle qu'on
        // referme aussitôt. Dans les deux cas on repart drapeau désarmé, sections vides.
        DrawerSectionStateManager.ensureOpen(SENTINEL)
        if (DrawerSectionStateManager.isExpanded(SENTINEL)) DrawerSectionStateManager.toggle(SENTINEL)
    }

    @Test
    fun `une section jamais ouverte est repliee par defaut`() {
        allKeys.forEach { assertFalse(it, DrawerSectionStateManager.isExpanded(it)) }
        // Une clé inconnue est également repliée par défaut.
        assertFalse(DrawerSectionStateManager.isExpanded("clé_inexistante"))
    }

    @Test
    fun `ensureOpen deplie la section et est idempotent (add-only)`() {
        val key = DrawerSectionStateManager.KEY_SPORT

        DrawerSectionStateManager.ensureOpen(key)
        assertTrue(DrawerSectionStateManager.isExpanded(key))

        // 2e appel : reste dépliée, ne referme pas.
        DrawerSectionStateManager.ensureOpen(key)
        assertTrue(DrawerSectionStateManager.isExpanded(key))
    }

    @Test
    fun `toggle bascule deplie - replie - deplie`() {
        val key = DrawerSectionStateManager.KEY_NUTRITION

        DrawerSectionStateManager.toggle(key)
        assertTrue("1er toggle -> déplié", DrawerSectionStateManager.isExpanded(key))

        DrawerSectionStateManager.toggle(key)
        assertFalse("2e toggle -> replié", DrawerSectionStateManager.isExpanded(key))
    }

    @Test
    fun `toggle emet sur le StateFlow observe par le drawer`() {
        val key = DrawerSectionStateManager.KEY_NUTRITION
        assertFalse(key in DrawerSectionStateManager.openSections.value)

        DrawerSectionStateManager.toggle(key)

        assertTrue(key in DrawerSectionStateManager.openSections.value)
    }

    @Test
    fun `ouvrir une section n'affecte pas les autres (accordeon multi-ouvert)`() {
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_SPORT)

        assertTrue(DrawerSectionStateManager.isExpanded(DrawerSectionStateManager.KEY_SPORT))
        // Les autres restent repliées.
        assertFalse(DrawerSectionStateManager.isExpanded(DrawerSectionStateManager.KEY_GENERAL))
        assertFalse(DrawerSectionStateManager.isExpanded(DrawerSectionStateManager.KEY_NUTRITION))
        assertFalse(DrawerSectionStateManager.isExpanded(DrawerSectionStateManager.KEY_ADMIN))
    }

    @Test
    fun `sectionKeyForRoute range chaque route dans sa section`() {
        assertEquals(DrawerSectionStateManager.KEY_GENERAL, sectionKeyForRoute(Routes.HOME))
        assertEquals(DrawerSectionStateManager.KEY_SPORT, sectionKeyForRoute(Routes.STATS))
        assertEquals(DrawerSectionStateManager.KEY_SPORT, sectionKeyForRoute(Routes.session("abc-123")))
        assertEquals(DrawerSectionStateManager.KEY_SPORT, sectionKeyForRoute(Routes.muscleStats("m1")))
        assertEquals(DrawerSectionStateManager.KEY_NUTRITION, sectionKeyForRoute(Routes.NUTRITION_GOALS))
        assertEquals(DrawerSectionStateManager.KEY_NUTRITION, sectionKeyForRoute(Routes.nutritionFoodDetail("f1")))
        assertEquals(DrawerSectionStateManager.KEY_HEALTH, sectionKeyForRoute(Routes.HEALTH_DASHBOARD))
        assertEquals(DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS, sectionKeyForRoute(Routes.PROFILE))
        assertEquals(DrawerSectionStateManager.KEY_ADMIN, sectionKeyForRoute(Routes.ADMIN_USERS))
    }

    @Test
    fun `resetTo ouvre la section cible et ferme toutes les autres`() {
        // Plusieurs sections ouvertes (manuellement / par navigation)…
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_GENERAL)
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_SPORT)
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_ADMIN)

        // …la bascule de mode recale l'accordéon sur LA section du mode.
        DrawerSectionStateManager.resetTo(DrawerSectionStateManager.KEY_NUTRITION)

        assertEquals(
            setOf(DrawerSectionStateManager.KEY_NUTRITION),
            DrawerSectionStateManager.openSections.value,
        )
    }

    @Test
    fun `bascule de mode Sport - la navigation induite ne rouvre PAS General (seule Sport reste)`() {
        // Reproduction du flux réel de la barre basse : bascule vers Sport = reset sur
        // la section Sport + armement du drapeau one-shot AVANT la navigation induite…
        DrawerSectionStateManager.resetTo(DrawerSectionStateManager.KEY_SPORT)
        DrawerSectionStateManager.suppressNextRouteOpen()
        // …puis l'atterrissage sur HOME (∈ « Général ») déclenche le suivi de route :
        // le ensureOpen est neutralisé → SEULE la section Sport reste ouverte.
        DrawerSectionStateManager.ensureOpen(sectionKeyForRoute(Routes.HOME))

        assertEquals(
            setOf(DrawerSectionStateManager.KEY_SPORT),
            DrawerSectionStateManager.openSections.value,
        )
    }

    @Test
    fun `suppressNextRouteOpen est one-shot - seul le ensureOpen suivant est neutralise`() {
        DrawerSectionStateManager.resetTo(DrawerSectionStateManager.KEY_SPORT)
        DrawerSectionStateManager.suppressNextRouteOpen()

        // 1er ensureOpen (navigation induite par la bascule) : neutralisé.
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_GENERAL)
        assertFalse(DrawerSectionStateManager.isExpanded(DrawerSectionStateManager.KEY_GENERAL))

        // 2e ensureOpen (navigation utilisateur ultérieure) : add-only à nouveau actif.
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS)
        assertTrue(DrawerSectionStateManager.isExpanded(DrawerSectionStateManager.KEY_ACCOUNT_SETTINGS))
    }

    @Test
    fun `navigation ordinaire (sans bascule) - le suivi de route reste add-only`() {
        // Hors bascule de mode : une navigation déplie add-only la section de la route,
        // sans refermer les sections déjà ouvertes (comportement route→section inchangé).
        DrawerSectionStateManager.ensureOpen(DrawerSectionStateManager.KEY_SPORT)
        DrawerSectionStateManager.ensureOpen(sectionKeyForRoute(Routes.HOME))

        assertEquals(
            setOf(DrawerSectionStateManager.KEY_SPORT, DrawerSectionStateManager.KEY_GENERAL),
            DrawerSectionStateManager.openSections.value,
        )
    }

    @Test
    fun `sectionKeyForMode associe chaque mode a sa section drawer`() {
        assertEquals(DrawerSectionStateManager.KEY_SPORT, sectionKeyForMode(NavMode.SPORT))
        assertEquals(DrawerSectionStateManager.KEY_NUTRITION, sectionKeyForMode(NavMode.NUTRITION))
        assertEquals(DrawerSectionStateManager.KEY_HEALTH, sectionKeyForMode(NavMode.HEALTH))
    }

    @Test
    fun `sectionKeyForRoute retombe sur General hors drawer ou route nulle`() {
        assertEquals(DrawerSectionStateManager.KEY_GENERAL, sectionKeyForRoute(null))
        assertEquals(DrawerSectionStateManager.KEY_GENERAL, sectionKeyForRoute("route_inconnue"))
        // Route de démarrage hors sections nommées -> repli Général.
        assertEquals(DrawerSectionStateManager.KEY_GENERAL, sectionKeyForRoute(Routes.SPLASH))
    }
}
