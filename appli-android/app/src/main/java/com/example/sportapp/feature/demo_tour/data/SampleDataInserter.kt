package com.example.sportapp.feature.demo_tour.data

import androidx.room.withTransaction
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.local.AppDatabase
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Insère 5 fake workouts en local Room pour permettre au tour visuel (sessions 2+)
 * de montrer du contenu (Stats avec data, Calendar avec séances vertes, etc.).
 *
 * Convention : tous les UUIDs sont préfixés `sample-` pour cleanup trivial via
 * `WHERE uuid LIKE 'sample-%'`. Marqués `synced=true` permanent → JAMAIS poussés
 * au serveur Pi (= pas de pollution prod). Cleanup auto au prochain ColdStart
 * via SplashScreenViewModel (crash-safe : si l'app crash pendant le tour, le
 * flag survit en SharedPreferences et le cleanup tourne au prochain démarrage).
 *
 * Insert atomic via `db.withTransaction { ... }` : si une étape plante, rollback
 * complet. La FK CASCADE sur les enfants (actual_workout_exercises +
 * actual_workout_sets) garantit aussi un cleanup propre par cascade quand on
 * delete le parent.
 */
@Singleton
class SampleDataInserter @Inject constructor(
    private val db: AppDatabase,
    private val workoutDao: ActualWorkoutDao,
    private val workoutExerciseDao: ActualWorkoutExerciseDao,
    private val workoutSetDao: ActualWorkoutSetDao,
    private val exerciseDao: ExerciseDao,
) {

    /**
     * 5 fake workouts étalés sur les 7 derniers jours. Mappés sur les noms du
     * starter pack V8.4 (cf. serveur/app/seed_database.py:152 _STARTER_EXERCISE_SPECS).
     * Si un nom n'existe pas en Room (catalogue désélectionné par l'user), l'exercise
     * est silencieusement skippé. Si <2 exercises restants dans un workout, on
     * skip le workout entier (un workout vide a peu d'intérêt visuel).
     */
    private val sampleWorkouts: List<SampleWorkoutSpec> = listOf(
        SampleWorkoutSpec("Push Day", daysAgo = 7, exerciseSpecs = listOf(
            SampleExerciseSpec("Bench Press", reps = 8, weight = 60f),
            SampleExerciseSpec("Overhead Press", reps = 8, weight = 40f),
            SampleExerciseSpec("Lateral Raise", reps = 12, weight = 10f),
            SampleExerciseSpec("Tricep Extension", reps = 12, weight = 20f),
        )),
        SampleWorkoutSpec("Pull Day", daysAgo = 5, exerciseSpecs = listOf(
            SampleExerciseSpec("Pull-Up", reps = 8, weight = 0f),
            SampleExerciseSpec("Barbell Row", reps = 8, weight = 60f),
            SampleExerciseSpec("Lat Pulldown", reps = 10, weight = 50f),
            SampleExerciseSpec("Bicep Curl", reps = 12, weight = 15f),
        )),
        SampleWorkoutSpec("Leg Day", daysAgo = 3, exerciseSpecs = listOf(
            SampleExerciseSpec("Squat", reps = 6, weight = 80f),
            SampleExerciseSpec("Romanian Deadlift", reps = 10, weight = 70f),
            SampleExerciseSpec("Leg Press", reps = 12, weight = 100f),
            SampleExerciseSpec("Leg Curl", reps = 12, weight = 30f),
        )),
        SampleWorkoutSpec("Full Body", daysAgo = 1, exerciseSpecs = listOf(
            SampleExerciseSpec("Deadlift", reps = 6, weight = 90f),
            SampleExerciseSpec("Bench Press", reps = 10, weight = 55f),
            SampleExerciseSpec("Pull-Up", reps = 8, weight = 0f),
            SampleExerciseSpec("Squat", reps = 8, weight = 70f),
        )),
        SampleWorkoutSpec("Cardio Mix", daysAgo = 0, exerciseSpecs = listOf(
            SampleExerciseSpec("Push-Up", reps = 15, weight = 0f),
            SampleExerciseSpec("Lunges", reps = 12, weight = 0f),
            SampleExerciseSpec("Plank", reps = 1, weight = 0f),
            SampleExerciseSpec("Calf Raises", reps = 15, weight = 0f),
        )),
    )

    private data class SampleWorkoutSpec(
        val name: String,
        val daysAgo: Int,
        val exerciseSpecs: List<SampleExerciseSpec>,
    )

    private data class SampleExerciseSpec(
        val exerciseName: String,
        val reps: Int,
        val weight: Float,
        val setsCount: Int = 3,
    )

    suspend fun insertSampleWorkouts(userId: Int) {
        val exerciseByName = exerciseDao.getAllActiveExercises().associateBy { it.name }
        val nowIso = getNowISO8601()

        db.withTransaction {
            sampleWorkouts.forEachIndexed { workoutIdx, spec ->
                val resolved = spec.exerciseSpecs.mapNotNull { es ->
                    exerciseByName[es.exerciseName]?.let { ex -> es to ex }
                }
                if (resolved.size < 2) return@forEachIndexed

                val workoutUuid = "sample-w$workoutIdx-${UUID.randomUUID()}"
                val date = LocalDate.now().minusDays(spec.daysAgo.toLong()).toString()
                workoutDao.insertInternal(
                    ActualWorkout(
                        uuid = workoutUuid,
                        userId = userId,
                        name = spec.name,
                        date = date,
                        notes = null,
                        location = null,
                        isDone = true,
                        synced = true,
                        pendingDeletion = false,
                        updatedAt = nowIso,
                    )
                )

                resolved.forEachIndexed { exIdx, (es, ex) ->
                    val workoutExerciseUuid = "sample-we$workoutIdx-$exIdx-${UUID.randomUUID()}"
                    workoutExerciseDao.insertInternal(
                        ActualWorkoutExercise(
                            uuid = workoutExerciseUuid,
                            actualWorkoutUUID = workoutUuid,
                            exerciseUUID = ex.uuid,
                            sets = es.setsCount,
                            reps = es.reps.toString(),
                            phase = "TRAINING",
                            status = "DONE",
                            order = exIdx,
                            addedManually = false,
                            synced = true,
                            pendingDeletion = false,
                            updatedAt = nowIso,
                        )
                    )

                    val sets = (0 until es.setsCount).map { setIdx ->
                        ActualWorkoutSet(
                            uuid = "sample-s$workoutIdx-$exIdx-$setIdx-${UUID.randomUUID()}",
                            actualWorkoutExerciseUUID = workoutExerciseUuid,
                            setOrder = setIdx,
                            reps = es.reps,
                            weight = es.weight,
                            isDropset = false,
                            notes = null,
                            recommendation = null,
                            status = "DONE",
                            synced = true,
                            pendingDeletion = false,
                            updatedAt = nowIso,
                        )
                    }
                    workoutSetDao.insertAllInternal(sets)
                }
            }
        }
    }

    /**
     * Supprime tous les rows insérés par cette classe (préfixe `sample-`).
     * La FK CASCADE Room sur actual_workout_exercises et actual_workout_sets
     * supprime automatiquement les enfants — 1 seule query suffit sur le parent.
     */
    suspend fun cleanupSampleWorkouts() {
        workoutDao.deleteSampleWorkouts()
    }
}
