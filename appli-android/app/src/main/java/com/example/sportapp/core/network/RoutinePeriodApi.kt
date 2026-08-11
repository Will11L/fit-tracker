package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.RoutinePeriod
import retrofit2.http.*

interface RoutinePeriodApi {

    @GET("routine-periods")
    suspend fun getAll(): List<RoutinePeriod>

    @GET("routine-periods/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): RoutinePeriod



    @PUT("routine-periods/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: RoutinePeriod)

    @PUT("routine-periods/bulk")
    suspend fun upsertAll(@Body items: List<RoutinePeriod>)

    @DELETE("routine-periods/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
