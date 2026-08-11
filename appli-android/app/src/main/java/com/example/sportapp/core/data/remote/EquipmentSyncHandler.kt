package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.EquipmentDao
import com.example.sportapp.core.data.model.Equipment
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class EquipmentSyncHandler @Inject constructor(
    private val dao: EquipmentDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "equipment_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("EquipmentHandler", "🆕 Equipment update reçu: $payload")
                val entity = Equipment(
                    name = payload.getString("name"),
                    uuid = payload.getString("uuid"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getEquipmentByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "equipment_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("EquipmentHandler", "🗑️ Equipment delete reçu: $uuid")
                val existing = dao.getEquipmentByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
