package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.RecipeIngredientDao
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class RecipeIngredientSyncHandler @Inject constructor(
    private val dao: RecipeIngredientDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "recipe_ingredient_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("RecipeIngredientHandler", "🆕 RecipeIngredient update reçu: $payload")
                val entity = RecipeIngredient(
                    uuid = payload.getString("uuid"),
                    recipeUUID = payload.getString("recipeUUID"),
                    foodUUID = payload.getString("foodUUID"),
                    quantityG = payload.getDouble("quantityG").toFloat(),
                    orderIndex = payload.getInt("orderIndex"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "recipe_ingredient_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("RecipeIngredientHandler", "🗑️ RecipeIngredient delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
