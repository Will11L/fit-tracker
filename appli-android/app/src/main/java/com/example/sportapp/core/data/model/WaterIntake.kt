package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Hydratation (2026-07-05) — portage Android du modèle serveur `water_intakes`
 * (cf. serveur/app/models/water_intake.py). Type A user-scoped.
 *
 * Chaque row = une prise d'eau horodatée (un verre / une bouteille). Le total du
 * jour se dérive côté client par SUM(amountMl) sur la date. `date` = jour local
 * "YYYY-MM-DD" (regroupement) ; `createdAt` = instant de la prise (heure de la
 * journée) — préservé au fil des updates (seul `updatedAt` est rafraîchi).
 * Objectif journalier versionné via HealthGoal (type WATER_ML), pas ici.
 */
@Entity(
    tableName = "water_intakes",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["date"]),
    ]
)
data class WaterIntake(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    val date: String,                                    // "YYYY-MM-DD" (jour local)
    @ColumnInfo(name = "amount_ml") val amountMl: Int,   // volume d'une prise en ml (> 0)

    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "created_at") val createdAt: String? = getNowISO8601(),  // instant de la prise
    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
