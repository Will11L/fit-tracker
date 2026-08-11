package com.example.sportapp.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.sync.base.SyncableEntity
import com.example.sportapp.core.sync.base.isRemoteNewer
import com.example.sportapp.core.sync.base.mergeFromRemote
import com.example.sportapp.core.sync.base.pullThenReplace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T4.2 Phase 1.3 (2026-05-07) : test smoke pilote du SyncEngine sur Muscle.
 *
 * Teste la logique pure de `mergeFromRemote` + `pullThenReplace` extraites
 * dans `SyncMergeOps.kt`. Utilise un `FakeMuscleSyncable` qui implémente
 * `SyncableEntity<Muscle>` avec un Room in-memory + un payload remote stubbé
 * (permet de valider sans hit RetrofitInstance).
 *
 * Critères protégés (= contrat du SyncEngine) :
 * 1. Remote nouveau (uuid absent local) → inséré
 * 2. Remote plus récent (`updatedAt` > local) → écrase local
 * 3. Remote plus ancien (`updatedAt` < local) → local préservé
 * 4. Local `synced=true` absent du remote → supprimé (prune)
 * 5. Local `synced=false` absent du remote → préservé (création en attente)
 * 6. `insertFromServer` préserve `synced=true` du payload (pas le wrapper insert)
 * 7. `pullThenReplace(syncedAfter=true)` : clear + bulk insert
 * 8. `isRemoteNewer` est ABSOLUE (Z vs +00:00 doivent être égaux)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SyncMergeOpsTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MuscleDao
    private lateinit var fake: FakeMuscleSyncable

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.muscleDao()
        fake = FakeMuscleSyncable(dao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `mergeFromRemote inserts new remote items absent from local`() = runTest {
        fake.fakeRemote = listOf(
            Muscle(uuid = "m-1", userId = 1, name = "Biceps", synced = true, updatedAt = "2025-01-15T10:00:00Z"),
            Muscle(uuid = "m-2", userId = 1, name = "Triceps", synced = true, updatedAt = "2025-01-15T11:00:00Z"),
        )

        mergeFromRemote(fake)

        val all = dao.getAllOnce()
        assertEquals(2, all.size)
        assertTrue(all.all { it.synced })  // insertFromServer préserve synced=true
    }

    @Test
    fun `mergeFromRemote updates local when remote is newer`() = runTest {
        dao.insertFromServer(Muscle(
            uuid = "m-1", userId = 1, name = "OldName",
            synced = true, updatedAt = "2025-01-15T08:00:00Z",
        ))
        fake.fakeRemote = listOf(
            Muscle(uuid = "m-1", userId = 1, name = "NewName",
                synced = true, updatedAt = "2025-01-15T10:00:00Z"),
        )

        mergeFromRemote(fake)

        val stored = dao.getMuscleByUUID("m-1")!!
        assertEquals("NewName", stored.name)
    }

    @Test
    fun `mergeFromRemote preserves local when remote is older`() = runTest {
        dao.insertFromServer(Muscle(
            uuid = "m-1", userId = 1, name = "LocalName",
            synced = true, updatedAt = "2025-01-15T10:00:00Z",
        ))
        fake.fakeRemote = listOf(
            Muscle(uuid = "m-1", userId = 1, name = "OldRemoteName",
                synced = true, updatedAt = "2025-01-15T08:00:00Z"),
        )

        mergeFromRemote(fake)

        val stored = dao.getMuscleByUUID("m-1")!!
        assertEquals("LocalName", stored.name)
    }

    @Test
    fun `mergeFromRemote prunes local synced rows absent from remote`() = runTest {
        // Local : 2 rows synced=true, dont 1 sera absente du remote
        dao.insertFromServer(Muscle(uuid = "keep", userId = 1, name = "Keep", synced = true, updatedAt = "2025-01-15T10:00:00Z"))
        dao.insertFromServer(Muscle(uuid = "stale", userId = 1, name = "Stale", synced = true, updatedAt = "2025-01-15T10:00:00Z"))
        // Remote : ne contient que "keep"
        fake.fakeRemote = listOf(
            Muscle(uuid = "keep", userId = 1, name = "Keep", synced = true, updatedAt = "2025-01-15T10:00:00Z"),
        )

        mergeFromRemote(fake)

        val all = dao.getAllOnce()
        assertEquals("stale doit être pruné", 1, all.size)
        assertEquals("keep", all[0].uuid)
        assertNull(dao.getMuscleByUUID("stale"))
    }

    @Test
    fun `mergeFromRemote preserves local unsynced rows even if absent from remote`() = runTest {
        // Création locale en attente de push (synced=false) → ne doit PAS être prunée
        dao.insert(Muscle(uuid = "pending-push", userId = 1, name = "PendingPush"))
        // Remote vide
        fake.fakeRemote = emptyList()

        mergeFromRemote(fake)

        val all = dao.getAllOnce()
        assertEquals(1, all.size)
        assertEquals("pending-push", all[0].uuid)
        assertFalse("synced=false doit être préservé", all[0].synced)
    }

    @Test
    fun `pullThenReplace with syncedAfter=true clears local and bulk inserts`() = runTest {
        dao.insert(Muscle(uuid = "old-1", userId = 1, name = "Old1"))
        dao.insert(Muscle(uuid = "old-2", userId = 1, name = "Old2"))
        fake.fakeRemote = listOf(
            Muscle(uuid = "new-1", userId = 1, name = "New1", synced = true, updatedAt = "2025-01-15T10:00:00Z"),
        )

        pullThenReplace(fake, syncedAfter = true)

        val all = dao.getAllOnce()
        assertEquals(1, all.size)
        assertEquals("new-1", all[0].uuid)
        assertTrue("syncedAfter=true doit préserver synced=true du payload", all[0].synced)
    }

    @Test
    fun `pullThenReplace with syncedAfter=false flips synced to false post-insert`() = runTest {
        fake.fakeRemote = listOf(
            Muscle(uuid = "m-1", userId = 1, name = "M1", synced = true, updatedAt = "2025-01-15T10:00:00Z"),
        )

        pullThenReplace(fake, syncedAfter = false)

        val stored = dao.getMuscleByUUID("m-1")!!
        assertFalse("syncedAfter=false → markAsUnsynced → synced=false", stored.synced)
    }

    @Test
    fun `insertFromServer forces synced=true and pendingDeletion=false (regression 2026-05-07)`() = runTest {
        // Simule un payload désérialisé depuis JSON serveur sans champ `synced`
        // (le data class default est false) — situation réelle du bug pré-fix.
        // Idem pour pendingDeletion. Sans le fix, le merge laissait synced=false
        // et le pushAll suivant re-pushait inutilement la row au serveur.
        fake.fakeRemote = listOf(
            Muscle(uuid = "m-bug", userId = 1, name = "FromServer",
                synced = false, pendingDeletion = true,
                updatedAt = "2025-01-15T10:00:00Z"),
        )

        mergeFromRemote(fake)

        val stored = dao.getMuscleByUUID("m-bug")!!
        assertTrue("payload synced=false doit être stocké synced=true", stored.synced)
        assertFalse("payload pendingDeletion=true doit être stocké pendingDeletion=false", stored.pendingDeletion)
    }

    @Test
    fun `isRemoteNewer treats Z and +00-00 as equivalent (absolute time)`() {
        // Les 2 chaînes représentent le même instant UTC, mais lexicographiquement Z < +
        assertFalse("Z et +00:00 = même instant", isRemoteNewer("2025-01-15T10:00:00Z", "2025-01-15T10:00:00+00:00"))
        assertFalse("inverse : +00:00 et Z = même instant", isRemoteNewer("2025-01-15T10:00:00+00:00", "2025-01-15T10:00:00Z"))
    }

    @Test
    fun `isRemoteNewer NULL convention`() {
        assertFalse("remote=null → pas plus récent", isRemoteNewer("2025-01-15T10:00:00Z", null))
        assertTrue("local=null → remote plus récent", isRemoteNewer(null, "2025-01-15T10:00:00Z"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FakeMuscleSyncable : implémentation de SyncableEntity<Muscle> avec
    // remote stubbé. Évite RetrofitInstance.
    // ─────────────────────────────────────────────────────────────────────────
    private class FakeMuscleSyncable(
        private val dao: MuscleDao,
    ) : SyncableEntity<Muscle> {
        var fakeRemote: List<Muscle> = emptyList()

        override val entityName = "Muscles"
        override val displayName = "Muscle"
        override val iconRes = 0  // unused in test
        override val entityClass = Muscle::class

        override fun observeAll(): Flow<List<Muscle>> = dao.observeAll()
        override suspend fun getAllOnce() = dao.getAllOnce()
        override suspend fun getUnsyncedLocals() = dao.getAllUnsynced()
        override suspend fun getPendingDeletions() = dao.getPendingDeletions()
        override suspend fun hasUnsynced() = dao.hasUnsynced()

        override suspend fun getRemote(): List<Muscle> = fakeRemote

        override suspend fun clearLocal() = dao.clearAll()
        override suspend fun insertFromServer(item: Muscle) = dao.insertFromServer(item)
        override suspend fun bulkInsertFromServer(items: List<Muscle>) = dao.insertAllFromServer(items)

        override suspend fun upsert(item: Muscle) {} // unused
        override suspend fun upsertBulk(items: List<Muscle>) {} // unused
        override suspend fun deleteRemote(item: Muscle) {} // unused

        override suspend fun markAsSynced(item: Muscle) = dao.markAsSynced(item.uuid)
        override suspend fun markAsUnsynced(item: Muscle) = dao.markAsUnsynced(item.uuid)
        override suspend fun markAsPendingDeletion(item: Muscle) = dao.markAsPendingDeletion(item.uuid)
        override suspend fun deleteLocal(item: Muscle) = dao.delete(item)

        override fun keyOf(item: Muscle) = item.uuid
        override fun updatedAtOf(item: Muscle) = item.updatedAt
        override fun syncedOf(item: Muscle) = item.synced
        override fun pendingDeletionOf(item: Muscle) = item.pendingDeletion

        override fun pagingSourceRaw(query: androidx.sqlite.db.SupportSQLiteQuery) =
            dao.pagingSourceRaw(query)

        override suspend fun selectRowsRaw(query: androidx.sqlite.db.SupportSQLiteQuery) =
            dao.selectRowsRaw(query)

        override suspend fun selectCountRaw(query: androidx.sqlite.db.SupportSQLiteQuery) =
            dao.selectCountRaw(query)
    }
}
