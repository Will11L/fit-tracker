package com.example.sportapp

import androidx.compose.material3.SnackbarDuration
import com.example.sportapp.app.SnackbarAction
import com.example.sportapp.app.SnackbarController
import com.example.sportapp.app.SnackbarEvent
import com.example.sportapp.app.toMillis
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests JVM du comportement observable derriere [AppSnackbarHost] (refactor
 * 2026-05-31, commit 31f9e43 : extraction du bloc snackbarHost inline de
 * MainActivity vers ui/components/common_components/AppSnackbarHost.kt).
 *
 * Le composable lui-meme (rendu Material3, animation slide, mapping
 * type -> accent/icone via `appColors` theme-aware) depend de Compose UI et ne
 * se teste qu'en instrumented sur device -> HORS scope JVM (cf. precedent
 * CalendarMonthGridLogicTest). Ce qui est testable et load-bearing, c'est la
 * SOURCE que le host rend : le [SnackbarController] (la file `List<SnackbarEvent>`
 * exposee en StateFlow, keyee par `id` cote host), le mapping
 * [SnackbarDuration.toMillis] qui pilote l'auto-dismiss, et le contrat de
 * [showSnackbar] (resolution du "Close" secondaire par defaut quand Indefinite).
 *
 * [SnackbarController] est un `object` singleton + un scope interne sur
 * `Dispatchers.Main` -> on installe un [StandardTestDispatcher] via setMain pour
 * controler le temps de l'auto-dismiss de maniere deterministe, et on vide la
 * file entre chaque test (@After) pour eviter la pollution cross-test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarControllerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() = runTest(testDispatcher) {
        SnackbarController.dismissAll()
        Dispatchers.resetMain()
    }

    // ----- SnackbarDuration.toMillis (pilote l'auto-dismiss) -----

    @Test
    fun `toMillis maps the three durations`() {
        assertEquals(2000L, SnackbarDuration.Short.toMillis())
        assertEquals(4000L, SnackbarDuration.Long.toMillis())
        assertEquals(Long.MAX_VALUE, SnackbarDuration.Indefinite.toMillis())
    }

    // ----- SnackbarController : la file que le host rend -----

    @Test
    fun `show appends event to the rendered queue`() = runTest(testDispatcher) {
        SnackbarController.show(
            SnackbarEvent(message = "hello", duration = SnackbarDuration.Indefinite)
        )
        val queue = SnackbarController.snackbars.value
        assertEquals(1, queue.size)
        assertEquals("hello", queue.first().message)
    }

    @Test
    fun `multiple snackbars coexist in insertion order`() = runTest(testDispatcher) {
        SnackbarController.show(SnackbarEvent(message = "first", duration = SnackbarDuration.Indefinite))
        SnackbarController.show(SnackbarEvent(message = "second", duration = SnackbarDuration.Indefinite))
        SnackbarController.show(SnackbarEvent(message = "third", duration = SnackbarDuration.Indefinite))

        val messages = SnackbarController.snackbars.value.map { it.message }
        assertEquals(listOf("first", "second", "third"), messages)
    }

    @Test
    fun `dismissSnackbarById removes only the matching event`() = runTest(testDispatcher) {
        val keep = SnackbarEvent(message = "keep", duration = SnackbarDuration.Indefinite)
        val drop = SnackbarEvent(message = "drop", duration = SnackbarDuration.Indefinite)
        SnackbarController.show(keep)
        SnackbarController.show(drop)

        SnackbarController.dismissSnackbarById(drop.id)

        val queue = SnackbarController.snackbars.value
        assertEquals(1, queue.size)
        assertEquals("keep", queue.first().message)
        // L'event conserve est bien l'instance d'origine (key `id` stable cote host).
        assertSame(keep, queue.first())
    }

    @Test
    fun `dismissSnackbarById with unknown id is a no-op`() = runTest(testDispatcher) {
        SnackbarController.show(SnackbarEvent(message = "still here", duration = SnackbarDuration.Indefinite))

        SnackbarController.dismissSnackbarById("does-not-exist")

        assertEquals(1, SnackbarController.snackbars.value.size)
    }

    @Test
    fun `dismissAll empties the queue`() = runTest(testDispatcher) {
        SnackbarController.show(SnackbarEvent(message = "a", duration = SnackbarDuration.Indefinite))
        SnackbarController.show(SnackbarEvent(message = "b", duration = SnackbarDuration.Indefinite))

        SnackbarController.dismissAll()

        assertTrue(SnackbarController.snackbars.value.isEmpty())
    }

    // ----- auto-dismiss temporel -----

    @Test
    fun `non-indefinite snackbar auto-dismisses after its duration`() = runTest(testDispatcher) {
        SnackbarController.show(SnackbarEvent(message = "transient", duration = SnackbarDuration.Short))

        // Tout de suite : present.
        assertEquals(1, SnackbarController.snackbars.value.size)

        // Juste avant l'echeance (2000ms) : toujours present.
        advanceTimeBy(1999L)
        assertEquals(1, SnackbarController.snackbars.value.size)

        // Apres l'echeance : retire automatiquement.
        advanceTimeBy(2L)
        advanceUntilIdle()
        assertTrue(SnackbarController.snackbars.value.isEmpty())
    }

    @Test
    fun `indefinite snackbar is never auto-dismissed by time`() = runTest(testDispatcher) {
        SnackbarController.show(SnackbarEvent(message = "sticky", duration = SnackbarDuration.Indefinite))

        // Avance bien au-dela de toute duree finie.
        advanceTimeBy(60_000L)
        advanceUntilIdle()

        assertEquals(1, SnackbarController.snackbars.value.size)
        assertEquals("sticky", SnackbarController.snackbars.value.first().message)
    }

    // ----- showSnackbar : contrat du helper qui alimente le host -----

    @Test
    fun `showSnackbar with indefinite duration injects a default Close secondary action`() =
        runTest(testDispatcher) {
            showSnackbar(message = "needs manual close", duration = SnackbarDuration.Indefinite)
            advanceUntilIdle()

            val event = SnackbarController.snackbars.value.single()
            assertEquals("needs manual close", event.message)
            assertNotNull("Indefinite snackbar must get a fallback action", event.secondaryAction)
            assertEquals("Close", event.secondaryAction?.name)
        }

    @Test
    fun `showSnackbar with finite duration gets no default secondary action`() =
        runTest(testDispatcher) {
            showSnackbar(message = "auto", type = SnackbarType.SUCCESS, duration = SnackbarDuration.Short)
            // runCurrent() (et non advanceUntilIdle) : on execute le `show` deja
            // planifie SANS franchir le delai d'auto-dismiss (2000ms), sinon
            // l'event serait affiche puis immediatement retire.
            runCurrent()

            val event = SnackbarController.snackbars.value.single()
            assertNull(event.secondaryAction)
            assertEquals(SnackbarType.SUCCESS, event.type)
        }

    @Test
    fun `showSnackbar preserves an explicitly provided secondary action`() =
        runTest(testDispatcher) {
            val custom = SnackbarAction("Undo") {}
            showSnackbar(
                message = "deleted",
                secondaryAction = custom,
                duration = SnackbarDuration.Indefinite,
            )
            advanceUntilIdle()

            val event = SnackbarController.snackbars.value.single()
            // Le secondaryAction fourni n'est pas ecrase par le "Close" par defaut.
            assertEquals("Undo", event.secondaryAction?.name)
        }

    @Test
    fun `default Close action dismisses its own snackbar when invoked`() =
        runTest(testDispatcher) {
            val id = showSnackbar(message = "close me", duration = SnackbarDuration.Indefinite)
            advanceUntilIdle()

            val event = SnackbarController.snackbars.value.single()
            assertEquals(id, event.id)

            // Invoquer l'action "Close" doit retirer CET event de la file.
            event.secondaryAction!!.action()
            advanceUntilIdle()

            assertTrue(SnackbarController.snackbars.value.isEmpty())
        }
}
