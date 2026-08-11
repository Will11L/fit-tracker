package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.TaskCheck
import retrofit2.http.*

/**
 * Phase 0 (2026-05-12) : remplace RoutineTaskCheckApi. Routes /api/v1/task-checks/...
 */
interface TaskCheckApi {

    @GET("task-checks")
    suspend fun getAll(): List<TaskCheck>

    @GET("task-checks/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): TaskCheck

    @PUT("task-checks/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: TaskCheck)

    @PUT("task-checks/bulk")
    suspend fun upsertAll(@Body items: List<TaskCheck>)

    @DELETE("task-checks/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
