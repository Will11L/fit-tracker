package com.example.sportapp.feature.equipment.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.model.AvailableEquipment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Écran Matériel dédié Android (2026-06-17, commit `7ad5ede`) — logique de
 * possession « mon matériel » partagée par la liste et le détail
 * ([EquipmentOwnership]). C'est le cœur métier de l'écran : posséder un
 * équipement = avoir une ligne [AvailableEquipment] user-scoped homonyme
 * (comparaison insensible à la casse), le catalogue global `Equipment` (Type C
 * admin) n'étant jamais écrit.
 *
 * Comportements observables couverts :
 * - `toggle` off→on crée la ligne perso (étoile allumée).
 * - `toggle` on→off marque la ligne `pendingDeletion` + `unsynced` (la push la
 *   supprimera côté serveur).
 * - `toggle` off→on réutilise une ligne homonyme en attente de suppression
 *   (dédup insensible à la casse) au lieu d'en créer une seconde.
 * - `addPersonal` (bouton +) est un no-op si l'équipement est déjà possédé.
 *
 * Pattern : Room in-memory réel via Robolectric (les fonctions du domaine ne
 * font que muter le DAO, pas de réseau ni de Dispatcher à gérer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EquipmentOwnershipTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AvailableEquipmentDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.availableEquipmentDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    /** Lignes perso « possédées » (actives, non en attente de suppression). */
    private suspend fun ownedNames(): List<String> =
        dao.getAllOnce().filter { !it.pendingDeletion }.map { it.name }

    @Test
    fun `toggle off to on creates an owned personal row with trimmed name`() = runTest {
        EquipmentOwnership.toggle(dao, name = "  Dumbbells  ", userId = 7)

        val rows = dao.getAllOnce()
        assertEquals("une seule ligne perso doit être créée", 1, rows.size)
        val row = rows.single()
        assertEquals("le nom doit être trimé", "Dumbbells", row.name)
        assertEquals(7, row.userId)
        assertFalse("la ligne fraîchement créée n'est pas en attente de suppression", row.pendingDeletion)
        assertFalse("une création locale doit être à pousser (synced=false)", row.synced)
    }

    @Test
    fun `toggle on to off marks the active row pendingDeletion and unsynced`() = runTest {
        // Ligne déjà synchronisée (vient du serveur) → la possession est active.
        dao.insertFromServer(AvailableEquipment(uuid = "u1", userId = 1, name = "Barbell"))

        EquipmentOwnership.toggle(dao, name = "Barbell", userId = 1)

        val row = dao.getAvailableEquipmentByUUID("u1")
        assertNotNull(row)
        assertTrue("retirer la possession marque pendingDeletion", row!!.pendingDeletion)
        assertFalse("la suppression doit être poussée (synced=false)", row.synced)
        assertTrue("plus aucun matériel possédé", ownedNames().isEmpty())
    }

    @Test
    fun `toggle off to on reactivates a pending-deletion homonym (case-insensitive dedup)`() = runTest {
        // Une ligne homonyme reste en attente de suppression (toggle off non encore poussé).
        dao.insertFromServer(AvailableEquipment(uuid = "u1", userId = 1, name = "Resistance Band"))
        dao.markAsPendingDeletion("u1")

        // Toggle ON avec une casse différente : doit réutiliser la ligne, pas en créer une 2e.
        EquipmentOwnership.toggle(dao, name = "resistance band", userId = 1)

        val rows = dao.getAllOnce()
        assertEquals("aucun doublon ne doit être créé", 1, rows.size)
        val row = rows.single()
        assertEquals("u1", row.uuid)
        assertFalse("la ligne réactivée n'est plus en attente de suppression", row.pendingDeletion)
        assertEquals(listOf("Resistance Band"), ownedNames())
    }

    @Test
    fun `addPersonal is a no-op when the equipment is already owned`() = runTest {
        dao.insertFromServer(AvailableEquipment(uuid = "u1", userId = 1, name = "Kettlebell"))

        EquipmentOwnership.addPersonal(dao, name = "kettlebell", userId = 1)

        assertEquals("aucune ligne ne doit être ajoutée pour un matériel déjà possédé", 1, dao.getAllOnce().size)
    }
}
