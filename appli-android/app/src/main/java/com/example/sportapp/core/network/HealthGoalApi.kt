package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.HealthGoal
import retrofit2.http.*

interface HealthGoalApi {

    @GET("health-goals")
    suspend fun getAll(): List<HealthGoal>

    @GET("health-goals/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): HealthGoal

    @PUT("health-goals/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: HealthGoal)

    @PUT("health-goals/bulk")
    suspend fun upsertAll(@Body items: List<HealthGoal>)

    @DELETE("health-goals/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
