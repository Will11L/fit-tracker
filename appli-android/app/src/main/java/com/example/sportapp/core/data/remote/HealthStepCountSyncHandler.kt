package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class HealthStepCountSyncHandler @Inject constructor(
    private val dao: HealthStepCountDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "health_step_count_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("HealthStepCountHandler", "🆕 HealthStepCount update reçu: $payload")
                val entity = HealthStepCount(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    date = payload.getString("date"),
                    bucketStart = payload.getString("bucketStart"),
                    steps = payload.getInt("steps"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "health_step_count_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("HealthStepCountHandler", "🗑️ HealthStepCount delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
