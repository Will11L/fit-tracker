package com.example.sportapp.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val username: String,

    // Email reel optionnel (2026-06-06) -- nullable, le login reste username.
    @ColumnInfo(name = "email") val email: String? = null,

    @ColumnInfo(name = "first_name") val firstName: String? = null,
    @ColumnInfo(name = "last_name") val lastName: String? = null,

    @ColumnInfo(name = "is_admin", defaultValue = "0") val isAdmin: Boolean = false,

    // Bio (livré 2026-05-11) -- nullable, skippable dans onboarding step BIO.
    // Format ISO "YYYY-MM-DD" pour birth_date côté wire (Pydantic date).
    @ColumnInfo(name = "birth_date") val birthDate: String? = null,
    // UPPER_CASE policy 11 -- MALE/FEMALE/OTHER.
    val sex: String? = null,
    // Canoniques cm/kg (affichage selon lengthUnit/weightUnit côté UI).
    @ColumnInfo(name = "height_cm") val heightCm: Float? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: Float? = null,
)
