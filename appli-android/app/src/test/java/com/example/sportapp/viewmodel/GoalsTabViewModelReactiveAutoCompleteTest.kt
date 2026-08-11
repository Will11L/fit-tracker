package com.example.sportapp.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.utils.CustomDateUtils.getCurrentWeekISO
import com.example.sportapp.core.utils.CustomDateUtils.getStartOfWeek
import com.example.sportapp.feature.goals.viewmodel.GoalsTabViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Refactor GoalsTabViewModel (2026-05-29) : "remplacer combine() par flow
 * auto-completion". `muscleGoals` = combine().flatMapLatest LECTURE PURE ;
 * un flow dedie en init {} observe muscleGoals et delegue a
 * autoCompleteFinishedGoals. Ce test couvre le COMPORTEMENT OBSERVABLE de
 * bout en bout via le vrai pipeline reactif (Room reel).
 *
 * Un collecteur de fond reste actif sur muscleGoals pendant tout le test
 * (sinon WhileSubscribed(5000) + advanceUntilIdle stoppe le partage en
 * sautant la fenetre 5s). Scheduler partage entre viewModelScope et runTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class GoalsTabViewModelReactiveAutoCompleteTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: GoalsTabViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val weekISO = getCurrentWeekISO()
    private val workoutDate = getStartOfWeek(weekISO)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = GoalsTabViewModel(
            context = context,
            actualWorkoutExerciseDao = db.actualWorkoutExerciseDao(),
            actualWorkoutSetDao = db.actualWorkoutSetDao(),
            exerciseMuscleDao = db.exerciseMuscleDao(),
            muscleDao = db.muscleDao(),
            muscleGoalDao = db.muscleGoalDao(),
            syncEngine = mockk<SyncEngine>(relaxed = true),
            syncManager = mockk<SyncManager>(relaxed = true),
        )
    }

    @After
    fun teardown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedChain(
        muscleUuid: String,
        goalUuid: String,
        targetReps: String,
        setCount: Int,
    ) {
        db.muscleDao().insert(Muscle(uuid = muscleUuid, userId = 1, name = "M-$muscleUuid"))
        val exUuid = "ex-$muscleUuid"
        db.exerciseDao().insert(Exercise(uuid = exUuid, userId = 1, name = "E-$muscleUuid"))
        db.exerciseMuscleDao().insert(
            ExerciseMuscle(uuid = "link-$muscleUuid", exerciseUUID = exUuid, muscleUUID = muscleUuid)
        )
        val workoutUuid = "wk-$muscleUuid"
        db.actualWorkoutDao().insert(
            ActualWorkout(uuid = workoutUuid, userId = 1, name = "W", date = workoutDate)
        )
        val aweUuid = "awe-$muscleUuid"
        db.actualWorkoutExerciseDao().insert(
            ActualWorkoutExercise(
                uuid = aweUuid,
                actualWorkoutUUID = workoutUuid,
                exerciseUUID = exUuid,
                reps = "1", // expectedReps=1 -> tout set reps>=1 compte
                phase = "TRAINING",
                status = "DONE",
                order = 0,
            )
        )
        repeat(setCount) { i ->
            db.actualWorkoutSetDao().insert(
                ActualWorkoutSet(
                    uuid = "set-$muscleUuid-$i",
                    actualWorkoutExerciseUUID = aweUuid,
                    setOrder = i,
                    reps = 8,
                    weight = 20f,
                    status = "DONE",
                )
            )
        }
        db.muscleGoalDao().insertInternal(
            MuscleGoal(
                uuid = goalUuid,
                userId = 1,
                muscleUUID = muscleUuid,
                priority = "MEDIUM",
                done = 0,
                target = targetReps,
                weekISO = weekISO,
                status = "IN_PROGRESS",
            )
        )
    }

    /**
     * Attend l'ecriture REELLE de l'auto-completion. `autoCompleteFinishedGoals`
     * ecrit via une fonction DAO suspend Room qui s'execute sur l'executor reel
     * de Room (hors scheduler virtuel), et la chaine reactive (UnconfinedTest)
     * progresse sur ce thread executor. `advanceUntilIdle` garantit que le flow
     * init {} a DECLENCHE l'auto-completion, mais pas que l'UPDATE a ATTERRI : lire
     * la DB immediatement apres = course thread Room / scheduler virtuel (flaky).
     * On polle donc le statut en temps reel (hors scheduler virtuel) jusqu'a la
     * valeur attendue ou expiration.
     */
    private suspend fun awaitGoalStatus(
        uuid: String,
        expected: String,
        timeoutMs: Long = 5_000L,
    ): String? = withContext(Dispatchers.Default) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var status = db.muscleGoalDao().getByUUID(uuid)?.status
        while (status != expected && System.currentTimeMillis() < deadline) {
            delay(20)
            status = db.muscleGoalDao().getByUUID(uuid)?.status
        }
        status
    }

    @Test
    fun `cible atteinte par sets reels - le flow init complete le goal en DONE`() =
        runTest(dispatcher.scheduler) {
            seedChain(muscleUuid = "m-done", goalUuid = "g-done", targetReps = "3", setCount = 4)
            // Collecteur de fond : garde muscleGoals HOT (WhileSubscribed) pendant
            // que le flow init {} traite les emissions.
            val job = launch { viewModel.muscleGoals.collect {} }

            val goals = viewModel.muscleGoals.first { it.isNotEmpty() }
            assertEquals(4, goals.first { it.uuid == "g-done" }.done)
            advanceUntilIdle()

            // Attendre l'ecriture reelle (poll temps reel) au lieu de lire la DB
            // juste apres advanceUntilIdle -> supprime la course Room executor /
            // scheduler virtuel qui rendait ce test flaky (~50%).
            val status = awaitGoalStatus("g-done", expected = "DONE")
            job.cancel()
            assertEquals(
                "le goal dont la cible est atteinte doit passer a DONE via le flow init {}",
                "DONE",
                status,
            )
        }

    @Test
    fun `cible non atteinte - aucun passage a DONE intempestif`() =
        runTest(dispatcher.scheduler) {
            seedChain(muscleUuid = "m-partial", goalUuid = "g-partial", targetReps = "5", setCount = 2)
            val job = launch { viewModel.muscleGoals.collect {} }

            val goals = viewModel.muscleGoals.first { it.isNotEmpty() }
            assertEquals(2, goals.first { it.uuid == "g-partial" }.done)
            advanceUntilIdle()

            val status = db.muscleGoalDao().getByUUID("g-partial")!!.status
            job.cancel()
            assertEquals(
                "un goal partiellement complete ne doit jamais etre auto-complete",
                "IN_PROGRESS",
                status,
            )
        }
}
