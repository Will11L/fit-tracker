package com.example.sportapp.core.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

import org.json.JSONObject
import com.example.sportapp.core.data.local.QuoteDao
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.core.utils.JsonUtils.getNullableString

@Singleton
class QuoteSyncHandler @Inject constructor(
    private val dao: QuoteDao
) {
    suspend fun handle(json: JSONObject) {
        when (json.getString("type")) {
            "quote_updated" -> {
                val payload = json.getJSONObject("payload")
                Log.d("QuoteHandler", "🆕 Quote update reçu: $payload")
                val entity = Quote(
                    uuid = payload.getString("uuid"),
                    userId = payload.getInt("userId"),
                    text = payload.getString("text"),
                    author = payload.getNullableString("author"),

                    updatedAt = payload.getNullableString("updatedAt"),
                    synced = true
                )
                val existing = dao.getByUUID(entity.uuid)
                if (existing == null) dao.insertFromServer(entity) else dao.updateFromServer(entity)
            }
            "quote_deleted" -> {
                val uuid = json.getString("uuid")
                Log.d("QuoteHandler", "🗑️ Quote delete reçu: $uuid")
                val existing = dao.getByUUID(uuid)
                if (existing != null) {
                    dao.delete(existing)
                }
            }
        }
    }
}
