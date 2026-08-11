package com.example.sportapp.feature.equipment.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.sync.SyncEngine
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Écran Matériel dédié Android (2026-06-17, commit `7ad5ede`) — liste unifiée du
 * `EquipmentListViewModel`. Vérifie le COMPORTEMENT OBSERVABLE de `buildItems` au
 * travers du `StateFlow` `items` (la fonction est privée — on teste le contrat,
 * pas l'implémentation) :
 * - catalogue global `Equipment` ∪ matériel perso `AvailableEquipment` hors
 *   catalogue, dédup par nom insensible à la casse ;
 * - flag `owned` (un AvailableEquipment homonyme actif existe) + `inCatalog` ;
 * - flag `synced` faux dès qu'un changement local (catalogue OU possession) est
 *   en attente.
 * Et que `toggleOwned` mute la possession PUIS pousse `AvailableEquipment`.
 *
 * Pattern (validé sur `GoalsTabViewModelReactiveAutoCompleteTest` /
 * `RecipesViewModelTest`) : Room in-memory réel + `SyncEngine` mocké (relaxed,
 * car il pousse vers le serveur — pas de réseau en JVM). Un collecteur de fond
 * garde `items` HOT (WhileSubscribed). `Equipment`/`AvailableEquipment` sont
 * seedés via `insertFromServer` quand on veut une row déjà synchronisée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EquipmentListViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var syncEngine: SyncEngine
    private lateinit var viewModel: EquipmentListViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncEngine = mockk(relaxed = true)
        viewModel = EquipmentListViewModel(
            equipmentDao = db.equipmentDao(),
            availableEquipmentDao = db.availableEquipmentDao(),
            syncEngine = syncEngine,
        )
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `items merge catalog and personal-out-of-catalog with case-insensitive ownership`() =
        runTest(dispatcher.scheduler) {
            // Catalogue global (synced) : Barbell possédé (homonyme casse différente), Treadmill non.
            db.equipmentDao().insertFromServer(Equipment(uuid = "c-barbell", name = "Barbell"))
            db.equipmentDao().insertFromServer(Equipment(uuid = "c-treadmill", name = "Treadmill"))
            // Possession perso : "barbell" (matche le catalogue en casse insensible) + matériel hors catalogue.
            db.availableEquipmentDao().insertFromServer(AvailableEquipment(uuid = "a-barbell", userId = 1, name = "barbell"))
            db.availableEquipmentDao().insertFromServer(AvailableEquipment(uuid = "a-bands", userId = 1, name = "Resistance Bands"))

            val job = launch { viewModel.items.collect {} }
            val items = viewModel.items.first { it.size == 3 }
            job.cancel()

            assertEquals("catalogue (2) + perso hors catalogue (1)", 3, items.size)

            val barbell = items.first { it.name == "Barbell" }
            assertTrue("Barbell est possédé via l'homonyme perso (casse insensible)", barbell.owned)
            assertTrue(barbell.inCatalog)

            val treadmill = items.first { it.name == "Treadmill" }
            assertFalse("Treadmill n'a pas d'homonyme perso", treadmill.owned)
            assertTrue(treadmill.inCatalog)

            val bands = items.first { it.name == "Resistance Bands" }
            assertTrue("le matériel perso est possédé", bands.owned)
            assertFalse("le matériel perso n'est pas dans le catalogue global", bands.inCatalog)
        }

    @Test
    fun `item is not synced when its ownership row has a pending local change`() =
        runTest(dispatcher.scheduler) {
            db.equipmentDao().insertFromServer(Equipment(uuid = "c-barbell", name = "Barbell"))
            // insert (pas insertFromServer) → la possession est locale non synchronisée.
            db.availableEquipmentDao().insert(AvailableEquipment(uuid = "a-barbell", userId = 1, name = "Barbell"))

            val job = launch { viewModel.items.collect {} }
            val items = viewModel.items.first { it.isNotEmpty() }
            job.cancel()

            val barbell = items.first { it.name == "Barbell" }
            assertTrue(barbell.owned)
            assertFalse(
                "catalogue synced mais possession en attente → la row n'est pas synchronisée",
                barbell.synced,
            )
        }

    @Test
    fun `toggleOwned creates the ownership row and pushes AvailableEquipment`() =
        runTest(dispatcher.scheduler) {
            db.equipmentDao().insertFromServer(Equipment(uuid = "c-barbell", name = "Barbell"))
            com.example.sportapp.core.network.CurrentUserManager.setUserId(
                ApplicationProvider.getApplicationContext(), 42,
            )

            val job = launch { viewModel.items.collect {} }
            viewModel.items.first { it.any { i -> i.name == "Barbell" } }

            viewModel.toggleOwned("Barbell")
            advanceUntilIdle()

            val owned = viewModel.items.first { it.first { i -> i.name == "Barbell" }.owned }
            job.cancel()

            assertTrue("après toggle, Barbell doit être possédé", owned.first { it.name == "Barbell" }.owned)
            assertEquals(
                "une ligne AvailableEquipment doit avoir été créée pour user 42",
                42,
                db.availableEquipmentDao().getAllOnce().single().userId,
            )
            coVerify { syncEngine.pushEntityClass(AvailableEquipment::class) }
        }
}
