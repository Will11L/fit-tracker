package com.example.sportapp.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.feature.goals.viewmodel.GoalsTabViewModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Refactor GoalsTabViewModel (ex-TODO_FIXES §5, 2026-05-29) : l'auto-completion
 * d'un goal etait injectee en effet de bord dans la lambda du combine() (ne se
 * declenchait pas de maniere fiable). Elle est desormais portee par un flow
 * dedie dans init {} qui delegue a `autoCompleteFinishedGoals(uuids)`.
 *
 * Ces tests protegent le contrat de cette methode (Room in-memory reel +
 * SyncEngine/SyncManager mockes) :
 *  1. Goal dont la cible est atteinte -> passe a DONE + push de sync declenche.
 *  2. Goal partiellement complete (`shouldAutoComplete` = false) -> jamais
 *     eligible (non-regression : pas de completion intempestive).
 *  3. Goal SKIPPED / pendingDeletion / deja DONE -> ignore (garde defensive).
 *  4. Aucun goal complete -> pas de push.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class GoalsTabViewModelAutoCompleteTest {

    private lateinit var db: AppDatabase
    private lateinit var goalDao: MuscleGoalDao
    private lateinit var syncEngine: SyncEngine
    private lateinit var viewModel: GoalsTabViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        goalDao = db.muscleGoalDao()
        syncEngine = mockk(relaxed = true)

        viewModel = GoalsTabViewModel(
            context = context,
            actualWorkoutExerciseDao = db.actualWorkoutExerciseDao(),
            actualWorkoutSetDao = db.actualWorkoutSetDao(),
            exerciseMuscleDao = db.exerciseMuscleDao(),
            muscleDao = db.muscleDao(),
            muscleGoalDao = goalDao,
            syncEngine = syncEngine,
            syncManager = mockk<SyncManager>(relaxed = true),
        )
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Insere le muscle parent (FK) puis le goal. */
    private suspend fun seedGoal(
        uuid: String,
        target: String,
        done: Int,
        status: String,
        pendingDeletion: Boolean = false,
    ) {
        val muscleUuid = "muscle-$uuid"
        db.muscleDao().insert(Muscle(uuid = muscleUuid, userId = 1, name = "M-$uuid"))
        goalDao.insertInternal(
            MuscleGoal(
                uuid = uuid,
                userId = 1,
                muscleUUID = muscleUuid,
                priority = "MEDIUM",
                done = done,
                target = target,
                weekISO = "2026-W22",
                status = status,
                pendingDeletion = pendingDeletion,
            )
        )
    }

    @Test
    fun `goal atteint passe a DONE et declenche un push`() = runTest {
        seedGoal(uuid = "g1", target = "10", done = 10, status = "IN_PROGRESS")

        val completed = viewModel.autoCompleteFinishedGoals(setOf("g1"))

        assertEquals(1, completed)
        assertEquals("DONE", goalDao.getByUUID("g1")!!.status)
        coVerify(exactly = 1) { syncEngine.pushEntityClass(MuscleGoal::class) }
    }

    @Test
    fun `goal partiellement complete n'est jamais eligible (shouldAutoComplete false)`() = runTest {
        // done=4 < target=10 -> ne doit pas etre propose a la completion.
        seedGoal(uuid = "partial", target = "10", done = 4, status = "IN_PROGRESS")
        val goal = goalDao.getByUUID("partial")!!

        assertEquals(false, viewModel.shouldAutoComplete(goal))

        // Et meme si on force l'uuid, l'etat partiel reste IN_PROGRESS apres run
        // tant que la cible n'est pas atteinte cote calcul amont : ici on ne
        // l'inclut pas (le flow ne l'aurait pas inclus). On verifie le filtre.
        assertEquals("IN_PROGRESS", goalDao.getByUUID("partial")!!.status)
    }

    @Test
    fun `goal SKIPPED est ignore par la garde defensive`() = runTest {
        seedGoal(uuid = "skip", target = "10", done = 10, status = "SKIPPED")

        val completed = viewModel.autoCompleteFinishedGoals(setOf("skip"))

        assertEquals(0, completed)
        assertEquals("SKIPPED", goalDao.getByUUID("skip")!!.status)
        coVerify(exactly = 0) { syncEngine.pushEntityClass(MuscleGoal::class) }
    }

    @Test
    fun `goal pendingDeletion est ignore par la garde defensive`() = runTest {
        seedGoal(uuid = "del", target = "10", done = 10, status = "IN_PROGRESS", pendingDeletion = true)

        val completed = viewModel.autoCompleteFinishedGoals(setOf("del"))

        assertEquals(0, completed)
        assertEquals("IN_PROGRESS", goalDao.getByUUID("del")!!.status)
        coVerify(exactly = 0) { syncEngine.pushEntityClass(MuscleGoal::class) }
    }

    @Test
    fun `goal deja DONE est ignore et ne declenche pas de push`() = runTest {
        seedGoal(uuid = "done", target = "10", done = 10, status = "DONE")

        val completed = viewModel.autoCompleteFinishedGoals(setOf("done"))

        assertEquals(0, completed)
        coVerify(exactly = 0) { syncEngine.pushEntityClass(MuscleGoal::class) }
    }

    @Test
    fun `completion partielle d'un lot complete seulement les eligibles`() = runTest {
        seedGoal(uuid = "ok", target = "10", done = 10, status = "IN_PROGRESS")
        seedGoal(uuid = "skipped", target = "10", done = 10, status = "SKIPPED")

        val completed = viewModel.autoCompleteFinishedGoals(setOf("ok", "skipped"))

        assertEquals(1, completed)
        assertEquals("DONE", goalDao.getByUUID("ok")!!.status)
        assertEquals("SKIPPED", goalDao.getByUUID("skipped")!!.status)
        coVerify(exactly = 1) { syncEngine.pushEntityClass(MuscleGoal::class) }
    }
}
