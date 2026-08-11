package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

@Entity(
    tableName = "available_equipments",
    indices = [
        androidx.room.Index(value = ["uuid"], unique = true),
        androidx.room.Index(value = ["user_id"]),
    ]
)
data class AvailableEquipment(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val name: String,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
