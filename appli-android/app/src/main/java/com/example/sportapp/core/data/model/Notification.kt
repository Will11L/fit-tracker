package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["created_at"]),
        Index(value = ["user_id", "created_at"]),
    ]
)
data class Notification(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    // Type & display
    val type: String,
    @ColumnInfo(name = "level") val level: String = "info",

    val title: String,
    val body: String? = null,

    // Free payload (JSON). String->String pour eviter ambiguite Gson
    // (numbers deserialises en Double par defaut). Voir Converters.kt.
    @ColumnInfo(name = "data") val data: Map<String, String>? = null,

    // Deduplication
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String? = null,

    // Status
    @ColumnInfo(name = "created_at") val createdAt: String? = getNowISO8601(),
    @ColumnInfo(name = "read_at") val readAt: String? = null,
    @ColumnInfo(name = "archived_at") val archivedAt: String? = null,

    // Sync flags
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
