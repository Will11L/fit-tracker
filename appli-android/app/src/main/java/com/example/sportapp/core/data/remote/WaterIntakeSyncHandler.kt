package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.WaterIntakeDao
import com.example.sportapp.core.data.model.WaterIntake
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class WaterIntakeSyncHandler @Inject constructor(
    private val dao: WaterIntakeDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "water_intake_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("WaterIntakeHandler", "🆕 WaterIntake update reçu: $payload")
                val entity = WaterIntake(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    date = payload.getString("date"),
                    amountMl = payload.getInt("amountMl"),

                    createdAt = payload.getNullableString("createdAt"),
                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "water_intake_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("WaterIntakeHandler", "🗑️ WaterIntake delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
