package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.TaskDao
import com.example.sportapp.core.data.model.Task
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Phase 0 (2026-05-12) : handler WS pour Task (remplace l'absence de RoutineTaskSyncHandler).
 * Recoit task_updated / task_deleted depuis le trigger Postgres et applique au DAO local.
 */
@Singleton
class TaskSyncHandler @Inject constructor(
    private val dao: TaskDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "task_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("TaskHandler", "Task update recu: $payload")

                // recurrenceWeekdays : JSONB int[] cote serveur, parse en List<Int>
                val weekdays: List<Int>? = if (payload.has("recurrenceWeekdays") && !payload.isNull("recurrenceWeekdays")) {
                    val arr = payload.getJSONArray("recurrenceWeekdays")
                    List(arr.length()) { arr.getInt(it) }
                } else null

                val task = Task(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    title = payload.getString("title"),
                    notes = payload.getNullableString("notes"),
                    isActive = payload.getBoolean("isActive"),
                    order = payload.getInt("order"),
                    recurrenceKind = payload.getString("recurrenceKind"),
                    dueDate = payload.getNullableString("dueDate"),
                    dueTime = payload.getNullableString("dueTime"),
                    periodUUID = payload.getNullableString("periodUUID"),
                    recurrenceWeekdays = weekdays,
                    recurrenceStartDate = payload.getNullableString("recurrenceStartDate"),
                    recurrenceEndDate = payload.getNullableString("recurrenceEndDate"),
                    reminderMinutesBefore = if (payload.has("reminderMinutesBefore") && !payload.isNull("reminderMinutesBefore")) payload.getInt("reminderMinutesBefore") else null,
                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true,
                )

                val existing = dao.getByUUID(task.uuid)
                if (existing == null) dao.insertFromServer(task) else dao.updateFromServer(task)
            }
            "task_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("TaskHandler", "Task delete recu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) dao.delete(existing)
            }
        }
    }
}
