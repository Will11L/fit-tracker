package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.PlannedWorkoutDao
import com.example.sportapp.core.data.model.PlannedWorkout
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannedWorkoutSyncHandler @Inject constructor(
    private val dao: PlannedWorkoutDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "planned_workout_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("PlannedWorkoutHandler", "🆕 Update reçu: $payload")

                val entity = PlannedWorkout(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    dayOfWeek = payload.getString("dayOfWeek"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )

                val existing = dao.getPlannedWorkoutByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }

            "planned_workout_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("PlannedWorkoutHandler", "🗑️ Delete reçu: $uuid")

                val existing = dao.getPlannedWorkoutByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
