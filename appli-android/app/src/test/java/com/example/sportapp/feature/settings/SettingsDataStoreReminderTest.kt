package com.example.sportapp.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 2026-06-08 (qa-sport) : couverture du "rappel par défaut global"
 * (AppSettings.defaultReminderMinutesBefore) — volet "+ défaut global" de la
 * tâche "régler le rappel des tâches/routines : avant le début / avant l'échéance".
 *
 * On teste le COMPORTEMENT OBSERVABLE (ce qu'un écran relit dans settingsFlow
 * après que l'utilisateur a réglé le défaut dans Notifications), pas l'implémentation.
 * Le point sensible est l'encodage sentinelle null <-> -1 dans [SettingsDataStore] :
 *   - jamais réglé        -> 15  (défaut usine)
 *   - "Aucun" (null)      -> stocké -1, relu null  (sinon l'option "Aucun" est cassée)
 *   - 0 "pile à l'heure"  -> relu 0  (NE doit PAS être confondu avec null/-1)
 *   - N minutes           -> relu N
 *   - valeur puis "Aucun" -> relu null  (on peut revenir à Aucun)
 *
 * Le délégué `preferencesDataStore("app_settings")` est un singleton par process :
 * tout le contrat est donc exercé en UNE séquence déterministe dans un seul test,
 * la 1re lecture (avant toute écriture) capturant le défaut usine sur store vierge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SettingsDataStoreReminderTest {

    private lateinit var store: SettingsDataStore

    @Before
    fun setup() {
        store = SettingsDataStore(ApplicationProvider.getApplicationContext<Context>())
    }

    private suspend fun readDefault(): Int? =
        store.settingsFlow.first().defaultReminderMinutesBefore

    @Test
    fun `global default reminder round-trips through the -1 sentinel encoding`() = runTest {
        // 1) Store vierge -> défaut usine 15 min (clé absente).
        assertEquals("unset default reminder must be the factory 15 min", 15, readDefault())

        // 2) "Aucun" (null) -> stocké via sentinelle -1, relu null (rappel désactivé).
        store.setDefaultReminder(null)
        assertNull("selecting 'Aucun' must read back as null (disabled)", readDefault())

        // 3) 0 = "pile à l'heure" -> préservé, distinct de null (la borne `raw < 0`).
        store.setDefaultReminder(0)
        assertEquals("0 ('pile a l'heure') must survive and not collapse to null", 0, readDefault())

        // 4) Valeur normale -> round-trip exact.
        store.setDefaultReminder(30)
        assertEquals("a normal minutes value must round-trip", 30, readDefault())

        // 5) Retour à "Aucun" depuis une valeur -> relu null.
        store.setDefaultReminder(null)
        assertNull("switching back from a value to 'Aucun' must read back null", readDefault())
    }
}
