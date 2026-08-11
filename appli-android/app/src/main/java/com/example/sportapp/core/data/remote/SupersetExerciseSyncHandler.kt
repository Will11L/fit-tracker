package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.SupersetExerciseDao
import com.example.sportapp.core.data.model.SupersetExercise
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupersetExerciseSyncHandler @Inject constructor(
    private val dao: SupersetExerciseDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "superset_exercise_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("SupersetExerciseHandler", "🆕 SupersetExercise update reçu: $payload")

                val entity = SupersetExercise(
                    uuid = payload.getString("uuid"),
                    supersetGroupUUID = payload.getString("supersetGroupUUID"),
                    exerciseUUID = payload.getString("exerciseUUID"),
                    orderInGroup = payload.getInt("orderInGroup"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )

                val existing = dao.getSupersetExerciseByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }

            "superset_exercise_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("SupersetExerciseHandler", "🗑️ SupersetExercise delete reçu: $uuid")

                val existing = dao.getSupersetExerciseByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
