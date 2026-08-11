package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.util.UUID

@Entity(
    tableName = "muscles",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)]
)
data class Muscle(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,
    val name: String,
    @ColumnInfo(name = "muscle_group") val muscleGroup: String? = null,
    val zone: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
