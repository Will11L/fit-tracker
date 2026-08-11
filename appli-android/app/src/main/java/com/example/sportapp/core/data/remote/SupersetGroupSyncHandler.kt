package com.example.sportapp.core.data.remote

import android.util.Log
import com.example.sportapp.core.data.local.SupersetGroupDao
import com.example.sportapp.core.data.model.SupersetGroup
import com.example.sportapp.core.utils.JsonUtils.getNullableString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupersetGroupSyncHandler @Inject constructor(
    private val dao: SupersetGroupDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "superset_group_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("SupersetGroupHandler", "🆕 SupersetGroup update reçu: $payload")

                val entity = SupersetGroup(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    name = payload.getString("name"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )

                val existing = dao.getSupersetGroupByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }

            "superset_group_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("SupersetGroupHandler", "🗑️ SupersetGroup delete reçu: $uuid")

                val existing = dao.getSupersetGroupByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
