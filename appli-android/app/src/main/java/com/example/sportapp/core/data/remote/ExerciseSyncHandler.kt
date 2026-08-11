package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject

import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.model.Exercise
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class ExerciseSyncHandler @Inject constructor(
    private val dao: ExerciseDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "exercise_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("ExerciseHandler", "🆕 Exercise update reçu: $payload")
                val instructions: List<String>? =
                    if (payload.has("instructions") && !payload.isNull("instructions")) {
                        val arr = payload.getJSONArray("instructions")
                        (0 until arr.length()).map { arr.getString(it) }
                    } else null
                val exercise = Exercise(
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),
                    description = payload.getNullableString("description"),
                    instructions = instructions,
                    recommendedSets = payload.optInt("recommendedSets", 0),
                    recommendedReps = payload.getNullableString("recommendedReps"),
                    restTimeSeconds = payload.optInt("restTimeSeconds", 0),
                    durationInSeconds = payload.optInt("durationInSeconds", 0),
                    gifUrl = payload.getNullableString("gifUrl"),
                    isFavorite = payload.getBoolean("isFavorite"),
                    lastDone = payload.getNullableString("lastDone"),
                    uuid = payload.getString("uuid"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getExerciseByUUID(exercise.uuid)
                if (existing == null) dao.insertFromServer(exercise) else dao.updateFromServer(exercise)
            }
            "exercise_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("ExerciseHandler", "🗑️ Exercise delete reçu: $uuid")
                val existing = dao.getExerciseByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
