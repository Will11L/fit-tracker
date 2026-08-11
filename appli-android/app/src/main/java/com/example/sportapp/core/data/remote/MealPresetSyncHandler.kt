package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.MealPresetDao
import com.example.sportapp.core.data.model.MealPreset
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class MealPresetSyncHandler @Inject constructor(
    private val dao: MealPresetDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "meal_preset_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("MealPresetHandler", "🆕 MealPreset update reçu: $payload")
                val entity = MealPreset(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    orderIndex = payload.getInt("orderIndex"),
                    defaultTime = payload.getNullableString("defaultTime"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "meal_preset_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("MealPresetHandler", "🗑️ MealPreset delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
