package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.TaskCheckDao
import com.example.sportapp.core.data.model.TaskCheck
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Phase 0 (2026-05-12) : handler WS pour TaskCheck. Recoit task_check_updated /
 * task_check_deleted depuis le trigger Postgres.
 */
@Singleton
class TaskCheckSyncHandler @Inject constructor(
    private val dao: TaskCheckDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "task_check_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("TaskCheckHandler", "TaskCheck update recu: $payload")
                val check = TaskCheck(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    taskUUID = payload.getString("taskUUID"),
                    occurrenceDate = payload.getString("occurrenceDate"),
                    isChecked = payload.getBoolean("isChecked"),
                    checkedAt = payload.getNullableString("checkedAt"),
                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true,
                )
                val existing = dao.getByUUID(check.uuid)
                if (existing == null) dao.insertFromServer(check) else dao.updateFromServer(check)
            }
            "task_check_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("TaskCheckHandler", "TaskCheck delete recu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) dao.delete(existing)
            }
        }
    }
}
