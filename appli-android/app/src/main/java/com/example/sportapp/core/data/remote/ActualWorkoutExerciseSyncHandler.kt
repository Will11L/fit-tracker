package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.ActualWorkoutExerciseDao
import com.example.sportapp.core.data.model.ActualWorkoutExercise
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class ActualWorkoutExerciseSyncHandler @Inject constructor(
    private val dao: ActualWorkoutExerciseDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "actual_workout_exercise_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("ActualWorkoutExerciseHandler", "🆕 ActualWorkoutExercise update reçu: $payload")
                val entity = ActualWorkoutExercise(
                    actualWorkoutUUID = payload.getString("actualWorkoutUUID"),
                    exerciseUUID = payload.getString("exerciseUUID"),
                    sets = payload.getInt("sets"),
                    reps = payload.getString("reps"),
                    phase = payload.getString("phase"),
                    status = payload.getString("status"),
                    order = payload.getInt("order"),
                    addedManually = payload.getBoolean("addedManually"),
                    uuid = payload.getString("uuid"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getActualWorkoutExerciseByUUID(payload.getString("uuid"))
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "actual_workout_exercise_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("ActualWorkoutExerciseHandler", "🗑️ ActualWorkoutExercise delete reçu: $uuid")
                val existing = dao.getActualWorkoutExerciseByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
