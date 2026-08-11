package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601
import java.util.UUID

@Entity(
    tableName = "exercise_equipment",
    indices = [androidx.room.Index(value = ["uuid"], unique = true)],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Exercise::class,
            parentColumns = ["uuid"],
            childColumns = ["exercise_uuid"],
            onDelete = androidx.room.ForeignKey.CASCADE
        ),
        androidx.room.ForeignKey(
            entity = Equipment::class,
            parentColumns = ["uuid"],
            childColumns = ["equipment_uuid"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class ExerciseEquipment(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "exercise_uuid") val exerciseUUID: String,
    @ColumnInfo(name = "equipment_uuid") val equipmentUUID: String,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
