package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.core.data.model.ActualWorkout
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class ActualWorkoutSyncHandler @Inject constructor(
    private val dao: ActualWorkoutDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "actual_workout_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("ActualWorkoutHandler", "🆕 ActualWorkout update reçu: $payload")
                val workout = ActualWorkout(
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    date = payload.getString("date"), // ⚠️ stocké en String ISO si ton Entity le permet
                    notes = payload.getNullableString("notes"),
                    location = payload.getNullableString("location"),
                    isDone = payload.getBoolean("isDone"),
                    uuid = payload.getString("uuid"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getActualWorkoutByUUID(payload.getString("uuid"))
                if (existing == null) dao.insertFromServer(workout) else dao.updateFromServer(workout)
            }
            "actual_workout_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("ActualWorkoutHandler", "🗑️ ActualWorkout delete reçu: $uuid")
                val existing = dao.getActualWorkoutByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
