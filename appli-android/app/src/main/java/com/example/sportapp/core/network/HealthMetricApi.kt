package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.HealthMetric
import retrofit2.http.*

interface HealthMetricApi {

    @GET("health-metrics")
    suspend fun getAll(): List<HealthMetric>

    @GET("health-metrics/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): HealthMetric

    @PUT("health-metrics/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: HealthMetric)

    @PUT("health-metrics/bulk")
    suspend fun upsertAll(@Body items: List<HealthMetric>)

    @DELETE("health-metrics/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
