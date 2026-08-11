package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.ExerciseDao
import com.example.sportapp.core.data.local.ExerciseEquipmentDao
import com.example.sportapp.core.data.model.ExerciseEquipment
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class ExerciseEquipmentSyncHandler @Inject constructor(
    private val dao: ExerciseEquipmentDao,
    private val exerciseDao: ExerciseDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "exercise_equipment_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("ExerciseEquipmentHandler", "🆕 ExerciseEquipment update reçu: $payload")
                val entity = ExerciseEquipment(
                    uuid = payload.getString("uuid"),
                    exerciseUUID = payload.getString("exerciseUUID"),
                    equipmentUUID = payload.getString("equipmentUUID"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                // V4.4-C — defense in depth : si le parent Exercise n'existe pas
                // localement, l'event vient probablement d'un autre user (broadcast
                // cross-user). On ignore au lieu de creer un orphelin en Room.
                // (Equipment est Type C global donc toujours present, pas de check.)
                if (exerciseDao.getExerciseByUUID(entity.exerciseUUID) == null) {
                    Log.w("ExerciseEquipmentHandler", "⚠️ Exercise parent ${entity.exerciseUUID} absent localement, payload ignoré")
                    return
                }
                val existing = dao.getExerciseEquipmentByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "exercise_equipment_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("ExerciseEquipmentHandler", "🗑️ ExerciseEquipment delete reçu: $uuid")
                val existing = dao.getExerciseEquipmentByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
