package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class MealSyncHandler @Inject constructor(
    private val dao: MealDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "meal_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("MealHandler", "🆕 Meal update reçu: $payload")
                val entity = Meal(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    date = payload.getString("date"),
                    name = payload.getString("name"),
                    orderIndex = payload.getInt("orderIndex"),
                    presetUuid = payload.getNullableString("presetUuid"),
                    time = payload.getNullableString("time"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "meal_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("MealHandler", "🗑️ Meal delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
