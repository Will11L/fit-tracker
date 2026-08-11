package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.HealthMetricDao
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class HealthMetricSyncHandler @Inject constructor(
    private val dao: HealthMetricDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "health_metric_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("HealthMetricHandler", "🆕 HealthMetric update reçu: $payload")
                val entity = HealthMetric(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    type = payload.getString("type"),
                    value = payload.getDouble("value").toFloat(),
                    unit = payload.getString("unit"),
                    date = payload.getString("date"),
                    startTime = payload.getNullableString("startTime"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "health_metric_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("HealthMetricHandler", "🗑️ HealthMetric delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
