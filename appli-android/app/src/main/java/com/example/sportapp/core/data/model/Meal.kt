package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Nutrition A1 (2026-06-17) — portage Android du modèle serveur `meals`
 * (cf. serveur/app/models/meal.py). Repas du journal quotidien. Type A user-scoped.
 *
 * `date` en String "YYYY-MM-DD" (convention projet). `preset_uuid` = lien stable
 * vers le meal_preset dont ce repas est issu (SET NULL en DB : le repas survit à
 * la suppression du preset, il bascule en « ad hoc »). FK Room SET_NULL miroir.
 */
@Entity(
    tableName = "meals",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["date"]),
        Index(value = ["preset_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MealPreset::class,
            parentColumns = ["uuid"],
            childColumns = ["preset_uuid"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Meal(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val date: String,                                          // "YYYY-MM-DD"
    val name: String,                                          // user-typed (« Petit-déj »...)
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "preset_uuid") val presetUuid: String? = null,

    /** "HH:MM" heure réelle du repas (facultative) ; surclasse le defaultTime du preset
     *  à l'affichage. Miroir du champ wire `time` (serveur meals.time + web Meal.time). */
    val time: String? = null,

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
