package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.HealthStepCount
import retrofit2.http.*

interface HealthStepCountApi {

    @GET("health-step-counts")
    suspend fun getAll(): List<HealthStepCount>

    @GET("health-step-counts/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): HealthStepCount

    @PUT("health-step-counts/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: HealthStepCount)

    @PUT("health-step-counts/bulk")
    suspend fun upsertAll(@Body items: List<HealthStepCount>)

    @DELETE("health-step-counts/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
