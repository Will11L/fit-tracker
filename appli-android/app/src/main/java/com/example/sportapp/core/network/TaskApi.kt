package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Task
import retrofit2.http.*

/**
 * Phase 0 (2026-05-12) : remplace RoutineTaskApi. Routes /api/v1/tasks/...
 */
interface TaskApi {

    @GET("tasks")
    suspend fun getAll(): List<Task>

    @GET("tasks/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Task

    @PUT("tasks/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: Task)

    @PUT("tasks/bulk")
    suspend fun upsertAll(@Body items: List<Task>)

    @DELETE("tasks/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
