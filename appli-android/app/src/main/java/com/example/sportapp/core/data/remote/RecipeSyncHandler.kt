package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.RecipeDao
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.utils.JsonUtils.getNullableFloat
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class RecipeSyncHandler @Inject constructor(
    private val dao: RecipeDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "recipe_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("RecipeHandler", "🆕 Recipe update reçu: $payload")
                val entity = Recipe(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    kind = payload.getString("kind"),
                    totalWeightG = payload.getNullableFloat("totalWeightG"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "recipe_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("RecipeHandler", "🗑️ Recipe delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
