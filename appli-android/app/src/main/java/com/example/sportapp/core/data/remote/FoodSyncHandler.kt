package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.utils.JsonUtils.getNullableFloat
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class FoodSyncHandler @Inject constructor(
    private val dao: FoodDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "food_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("FoodHandler", "🆕 Food update reçu: $payload")
                val entity = Food(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    brand = payload.getNullableString("brand"),
                    source = payload.getString("source"),
                    sourceRef = payload.getNullableString("sourceRef"),
                    foodGroup = payload.getNullableString("foodGroup"),
                    kcalPer100g = payload.getDouble("kcalPer100g").toFloat(),
                    proteinPer100g = payload.getDouble("proteinPer100g").toFloat(),
                    carbsPer100g = payload.getDouble("carbsPer100g").toFloat(),
                    fatPer100g = payload.getDouble("fatPer100g").toFloat(),
                    fiberPer100g = payload.getNullableFloat("fiberPer100g"),
                    sugarPer100g = payload.getNullableFloat("sugarPer100g"),
                    satFatPer100g = payload.getNullableFloat("satFatPer100g"),
                    saltPer100g = payload.getNullableFloat("saltPer100g"),
                    ironPer100g = payload.getNullableFloat("ironPer100g"),
                    calciumPer100g = payload.getNullableFloat("calciumPer100g"),
                    magnesiumPer100g = payload.getNullableFloat("magnesiumPer100g"),
                    zincPer100g = payload.getNullableFloat("zincPer100g"),
                    potassiumPer100g = payload.getNullableFloat("potassiumPer100g"),
                    sodiumPer100g = payload.getNullableFloat("sodiumPer100g"),
                    vitaminCPer100g = payload.getNullableFloat("vitaminCPer100g"),
                    vitaminDPer100g = payload.getNullableFloat("vitaminDPer100g"),
                    vitaminB12Per100g = payload.getNullableFloat("vitaminB12Per100g"),
                    vitaminAPer100g = payload.getNullableFloat("vitaminAPer100g"),
                    isFavorite = payload.getBoolean("isFavorite"),
                    archived = payload.getBoolean("archived"),
                    isWater = payload.optBoolean("isWater", false),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "food_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("FoodHandler", "🗑️ Food delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
