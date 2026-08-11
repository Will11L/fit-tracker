package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseMuscleDao
import com.example.sportapp.core.data.model.ExerciseMuscle
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class ExerciseMuscleSyncHandler @Inject constructor(
    private val dao: ExerciseMuscleDao,
    private val exerciseDao: ExerciseDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "exercise_muscle_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("ExerciseMuscleHandler", "🆕 ExerciseMuscle update reçu: $payload")
                val entity = ExerciseMuscle(
                    uuid = payload.getString("uuid"),
                    exerciseUUID = payload.getString("exerciseUUID"),
                    muscleUUID = payload.getString("muscleUUID"),
                    coefficient = payload.optDouble("coefficient", 1.0).toFloat(),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                // V4.4-C — defense in depth : si le parent Exercise n'existe pas
                // localement, l'event vient probablement d'un autre user (broadcast
                // cross-user). On ignore au lieu de creer un orphelin en Room.
                if (exerciseDao.getExerciseByUUID(entity.exerciseUUID) == null) {
                    Log.w("ExerciseMuscleHandler", "⚠️ Exercise parent ${entity.exerciseUUID} absent localement, payload ignoré")
                    return
                }
                val existing = dao.getExerciseMuscleByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "exercise_muscle_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("ExerciseMuscleHandler", "🗑️ ExerciseMuscle delete reçu: $uuid")
                val existing = dao.getExerciseMuscleByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
