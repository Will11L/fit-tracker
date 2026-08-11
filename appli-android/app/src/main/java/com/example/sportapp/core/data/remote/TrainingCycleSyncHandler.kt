package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.TrainingCycleDao
import com.example.sportapp.core.data.model.TrainingCycle
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingCycleSyncHandler @Inject constructor(
    private val dao: TrainingCycleDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "training_cycle_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("TrainingCycleHandler", "🆕 TrainingCycle update reçu: $payload")

                val entity = TrainingCycle(
                    uuid = payload.getString("uuid"),
                    name = payload.getString("name"),
                    startDate = payload.getString("startDate"),
                    endDate = payload.getString("endDate"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )

                val existing = dao.getTrainingCycleByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }

            "training_cycle_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("TrainingCycleHandler", "🗑️ TrainingCycle delete reçu: $uuid")

                val existing = dao.getTrainingCycleByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
