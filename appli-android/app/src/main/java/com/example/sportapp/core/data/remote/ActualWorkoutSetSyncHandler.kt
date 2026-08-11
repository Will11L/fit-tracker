package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.ActualWorkoutSetDao
import com.example.sportapp.core.data.model.ActualWorkoutSet
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class ActualWorkoutSetSyncHandler @Inject constructor(
    private val dao: ActualWorkoutSetDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "actual_workout_set_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("ActualWorkoutSetHandler", "🆕 ActualWorkoutSet update reçu: $payload")
                val entity = ActualWorkoutSet(
                    actualWorkoutExerciseUUID = payload.getString("actualWorkoutExerciseUUID"),
                    setOrder = payload.getInt("setOrder"),
                    reps = payload.getInt("reps"),
                    weight = payload.getDouble("weight").toFloat(),
                    isDropset = payload.getBoolean("isDropset"),
                    notes = payload.getNullableString("notes"),
                    recommendation = payload.getNullableString("recommendation"),
                    status = payload.getString("status"),
                    uuid = payload.getString("uuid"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(payload.getString("uuid"))
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "actual_workout_set_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("ActualWorkoutSetHandler", "🗑️ ActualWorkoutSet delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
