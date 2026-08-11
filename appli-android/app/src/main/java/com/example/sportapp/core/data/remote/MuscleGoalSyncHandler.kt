package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.MuscleGoalDao
import com.example.sportapp.core.data.model.MuscleGoal
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class MuscleGoalSyncHandler @Inject constructor(
    private val dao: MuscleGoalDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "muscle_goal_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("MuscleGoalHandler", "🆕 MuscleGoal update reçu: $payload")
                val entity = MuscleGoal(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    muscleUUID = payload.getString("muscleUUID"),
                    priority = payload.getString("priority"),
                    done = payload.getInt("done"),
                    target = payload.getString("target"),
                    weekISO = payload.getString("weekISO"),
                    status = payload.getString("status"),
                    addedManually = payload.getBoolean("addedManually"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "muscle_goal_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("MuscleGoalHandler", "🗑️ MuscleGoal delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
