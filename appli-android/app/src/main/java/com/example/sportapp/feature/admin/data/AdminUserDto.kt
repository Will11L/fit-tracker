package com.example.sportapp.feature.admin.data

import com.google.gson.annotations.SerializedName

/**
 * DTO de l'écran admin pour gérer is_admin.
 *
 * Match du `UserOut` Pydantic côté serveur (id, username, firstName, lastName,
 * isAdmin). Utilisé par GET /api/v1/users (admin only) qui retourne la liste
 * complète de tous les users du système.
 *
 * Note : volontairement DTO séparé de l'entité Room `User` qui n'a pas
 * besoin de stocker tous les autres users localement (vue admin transient,
 * fetch direct REST sans cache).
 */
data class AdminUserDto(
    val id: Int,
    val username: String,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("isAdmin") val isAdmin: Boolean = false,
)

/** Body de PATCH /api/v1/users/{id}/admin. */
data class AdminToggleRequest(
    @SerializedName("isAdmin") val isAdmin: Boolean,
)
