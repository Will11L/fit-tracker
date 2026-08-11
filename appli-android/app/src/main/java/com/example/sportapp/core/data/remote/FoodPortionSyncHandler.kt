package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.FoodPortionDao
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class FoodPortionSyncHandler @Inject constructor(
    private val dao: FoodPortionDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "food_portion_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("FoodPortionHandler", "🆕 FoodPortion update reçu: $payload")
                val entity = FoodPortion(
                    uuid = payload.getString("uuid"),
                    foodUUID = payload.getString("foodUUID"),
                    label = payload.getString("label"),
                    grams = payload.getDouble("grams").toFloat(),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "food_portion_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("FoodPortionHandler", "🗑️ FoodPortion delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
