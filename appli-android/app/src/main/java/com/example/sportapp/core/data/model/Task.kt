package com.example.sportapp.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sportapp.core.utils.CustomDateUtils.getNowISO8601

/**
 * Phase 0 (2026-05-12) : modele unifie Task remplacant RoutineTask.
 *
 * recurrence_kind UPPER_CASE (politique 11) :
 *   - NONE    : tache one-off, dueDate REQUIRED
 *   - DAILY   : quotidienne, periodUUID REQUIRED + recurrenceStartDate REQUIRED
 *   - WEEKLY  : recurrenceWeekdays REQUIRED (List<Int> [0..6] Mon=0..Sun=6) +
 *               recurrenceStartDate REQUIRED
 *   - MONTHLY : meme jour du mois, derive de recurrenceStartDate
 *   - YEARLY  : meme date chaque annee, derive de recurrenceStartDate
 *
 * Champs conditionnels (nullable, validation cote serveur via Pydantic +
 * cote VM/UI). Pas de CHECK constraint SQLite (politique : validation au
 * niveau application, pas DB).
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["period_uuid"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = RoutinePeriod::class,
            parentColumns = ["uuid"],
            childColumns = ["period_uuid"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class Task(
    @PrimaryKey
    @ColumnInfo(name = "uuid") val uuid: String,

    @ColumnInfo(name = "user_id") val userId: Int,

    // Core fields
    val title: String,
    val notes: String? = null,

    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,

    @ColumnInfo(name = "order_index", defaultValue = "0") val order: Int = 0,

    // Recurrence
    @ColumnInfo(name = "recurrence_kind", defaultValue = "'DAILY'")
    val recurrenceKind: String = "DAILY",

    // Conditional fields (nullable)
    @ColumnInfo(name = "due_date") val dueDate: String? = null,      // YYYY-MM-DD
    @ColumnInfo(name = "due_time") val dueTime: String? = null,      // HH:MM
    @ColumnInfo(name = "period_uuid") val periodUUID: String? = null,
    @ColumnInfo(name = "recurrence_weekdays") val recurrenceWeekdays: List<Int>? = null,
    @ColumnInfo(name = "recurrence_start_date") val recurrenceStartDate: String? = null,
    @ColumnInfo(name = "recurrence_end_date") val recurrenceEndDate: String? = null,

    /** B.4 (2026-05-12) : dates ISO "YYYY-MM-DD" a exclure des occurrences
     *  pour le mode "Only this" de l'edition d'une recurrence (W/M/Y).
     *  Vide par defaut. Ignore pour NONE/DAILY. Serialise en JSON via
     *  InstructionsConverter (mirror du pattern recurrenceWeekdays).
     *  Non-nullable : toujours [] minimum (jamais null), cf. fix Room v18
     *  mismatch 2026-05-12 (schema attendu NOT NULL, migration cree NOT NULL). */
    @ColumnInfo(name = "excluded_dates", defaultValue = "'[]'")
    val excludedDates: List<String> = emptyList(),

    // Phase 3 (reminders)
    @ColumnInfo(name = "reminder_minutes_before") val reminderMinutesBefore: Int? = null,

    // Sync flags (local-only, exception politique 17)
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,

    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
