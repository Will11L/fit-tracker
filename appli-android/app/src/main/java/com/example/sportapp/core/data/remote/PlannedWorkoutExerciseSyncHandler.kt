package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.PlannedWorkoutExerciseDao
import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannedWorkoutExerciseSyncHandler @Inject constructor(
    private val dao: PlannedWorkoutExerciseDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "planned_workout_exercise_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("PlannedWorkoutExerciseHandler", "🆕 Update reçu: $payload")

                val entity = PlannedWorkoutExercise(
                    uuid = payload.getString("uuid"),
                    plannedWorkoutUUID = payload.getString("plannedWorkoutUUID"),
                    exerciseUUID = payload.getString("exerciseUUID"),
                    sets = payload.getInt("sets"),
                    reps = payload.getString("reps"),
                    phase = payload.optString("phase", "TRAINING"),
                    status = payload.optString("status", "PLANNED"),
                    order = payload.optInt("order", 0),
                    ignored = payload.optBoolean("ignored", false),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )

                val existing = dao.getPlannedWorkoutExerciseByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }

            "planned_workout_exercise_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("PlannedWorkoutExerciseHandler", "🗑️ Delete reçu: $uuid")

                val existing = dao.getPlannedWorkoutExerciseByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
