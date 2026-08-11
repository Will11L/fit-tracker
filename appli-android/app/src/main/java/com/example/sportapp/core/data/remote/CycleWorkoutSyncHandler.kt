package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.CycleWorkoutDao
import com.example.sportapp.core.data.model.CycleWorkout
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class CycleWorkoutSyncHandler @Inject constructor(
    private val dao: CycleWorkoutDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "cycle_workout_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("CycleWorkoutHandler", "🆕 CycleWorkout update reçu: $payload")
                val entity = CycleWorkout(
                    uuid = payload.getString("uuid"),
                    trainingCycleUUID = payload.getString("trainingCycleUUID"),
                    plannedWorkoutUUID = payload.getString("plannedWorkoutUUID"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getCycleWorkoutByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "cycle_workout_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("CycleWorkoutHandler", "🗑️ CycleWorkout delete reçu: $uuid")
                val existing = dao.getCycleWorkoutByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
