package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject

import com.example.sportapp.core.data.local.MuscleDao
import com.example.sportapp.core.data.model.Muscle
import com.example.sportapp.core.utils.JsonUtils.getNullableString


@Singleton
class MuscleSyncHandler @Inject constructor(
    private val dao: MuscleDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "muscle_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("MuscleHandler", "🆕 Muscle update reçu: $payload")
                val muscle = Muscle(
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    zone = payload.getNullableString("zone"),
                    isFavorite = payload.getBoolean("isFavorite"),
                    uuid = payload.getString("uuid"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getMuscleByUUID(muscle.uuid)
                if (existing == null) dao.insertFromServer(muscle) else dao.updateFromServer(muscle)
            }
            "muscle_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("MuscleHandler", "🗑️ Muscle delete reçu: $uuid")
                val existing = dao.getMuscleByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
