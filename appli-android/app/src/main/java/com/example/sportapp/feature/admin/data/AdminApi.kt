package com.example.sportapp.feature.admin.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * API admin-only (gated par require_admin côté serveur).
 * Tous les calls renvoient 403 si le user courant n'est pas admin.
 */
interface AdminApi {

    /** GET /api/v1/users : liste complète des users (admin only). */
    @GET("users")
    suspend fun listUsers(): List<AdminUserDto>

    /**
     * PATCH /api/v1/users/{userId}/admin : toggle is_admin.
     * Erreurs métier serveur :
     * - 400 si self-demote tenté.
     * - 400 si demote du dernier admin.
     * - 403 si caller pas admin.
     * - 404 si userId inexistant.
     */
    @PATCH("users/{userId}/admin")
    suspend fun toggleAdmin(
        @Path("userId") userId: Int,
        @Body body: AdminToggleRequest,
    ): AdminUserDto
}
