package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.AvailableEquipmentDao
import com.example.sportapp.core.data.model.AvailableEquipment
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class AvailableEquipmentSyncHandler @Inject constructor(
    private val dao: AvailableEquipmentDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "available_equipment_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("AvailableEquipmentHandler", "🆕 AvailableEquipment update reçu: $payload")
                val entity = AvailableEquipment(
                    name = payload.getString("name"),
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getAvailableEquipmentByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)

            }
            "available_equipment_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("AvailableEquipmentHandler", "🗑️ AvailableEquipment delete reçu: $uuid")
                val existing = dao.getAvailableEquipmentByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
