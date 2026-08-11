package com.example.sportapp.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 2026-06-09 (qa-sport) : couverture du COMPORTEMENT OBSERVABLE des toggles
 * "Activer/désactiver les notifications par catégorie" (commit 7a9566c).
 *
 * Source de vérité = [SettingsDataStore] : c'est ce que l'écran Notifications
 * relit dans settingsFlow après que l'utilisateur a basculé un switch de catégorie.
 *
 * Contrat couvert :
 *   - défaut usine : les 3 catégories notifient (store vierge -> true).
 *   - round-trip : couper puis ré-activer une catégorie persiste.
 *   - INDÉPENDANCE (le coeur de la tâche) : couper une catégorie ne touche
 *     JAMAIS les deux autres, dans les deux sens (off puis on).
 *
 * `preferencesDataStore("app_settings")` est un singleton par process : tout le
 * contrat est exercé en UNE séquence déterministe (même précaution que
 * [SettingsDataStoreReminderTest]), la 1re lecture capturant le défaut usine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SettingsDataStoreNotifyCategoriesTest {

    private lateinit var store: SettingsDataStore

    @Before
    fun setup() {
        store = SettingsDataStore(ApplicationProvider.getApplicationContext<Context>())
    }

    private suspend fun read(): AppSettings = store.settingsFlow.first()

    @Test
    fun `category toggles persist and are mutually independent`() = runTest {
        // 1) Store vierge -> les 3 catégories notifient (défaut usine).
        read().let {
            assertTrue("tasks default true", it.notifyTasks)
            assertTrue("timers default true", it.notifyTimers)
            assertTrue("routines default true", it.notifyRoutines)
        }

        // 2) Couper les TIMERS -> timers off, tâches + routines INTACTES.
        store.setNotifyTimers(false)
        read().let {
            assertFalse("timers off persists", it.notifyTimers)
            assertTrue("cutting timers must NOT affect tasks", it.notifyTasks)
            assertTrue("cutting timers must NOT affect routines", it.notifyRoutines)
        }

        // 3) Couper aussi les TÂCHES -> tâches + timers off, routines TOUJOURS on.
        store.setNotifyTasks(false)
        read().let {
            assertFalse("tasks off persists", it.notifyTasks)
            assertFalse("timers still off", it.notifyTimers)
            assertTrue("cutting tasks must NOT affect routines", it.notifyRoutines)
        }

        // 4) Couper les ROUTINES -> les 3 off.
        store.setNotifyRoutines(false)
        read().let {
            assertFalse("tasks off", it.notifyTasks)
            assertFalse("timers off", it.notifyTimers)
            assertFalse("routines off", it.notifyRoutines)
        }

        // 5) Ré-activer SEULEMENT les timers -> timers on, les deux autres restent off
        //    (indépendance dans le sens off -> on).
        store.setNotifyTimers(true)
        read().let {
            assertTrue("timers re-enabled", it.notifyTimers)
            assertFalse("re-enabling timers must NOT re-enable tasks", it.notifyTasks)
            assertFalse("re-enabling timers must NOT re-enable routines", it.notifyRoutines)
        }
    }
}
