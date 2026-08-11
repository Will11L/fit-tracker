package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.HealthGoalDao
import com.example.sportapp.core.data.model.HealthGoal
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class HealthGoalSyncHandler @Inject constructor(
    private val dao: HealthGoalDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "health_goal_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("HealthGoalHandler", "🆕 HealthGoal update reçu: $payload")
                val entity = HealthGoal(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    type = payload.getString("type"),
                    target = payload.getDouble("target").toFloat(),
                    effectiveFrom = payload.getString("effectiveFrom"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "health_goal_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("HealthGoalHandler", "🗑️ HealthGoal delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
