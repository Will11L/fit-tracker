package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.WaterIntake
import retrofit2.http.*

interface WaterIntakeApi {

    @GET("water-intakes")
    suspend fun getAll(): List<WaterIntake>

    @GET("water-intakes/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): WaterIntake

    @PUT("water-intakes/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: WaterIntake)

    @PUT("water-intakes/bulk")
    suspend fun upsertAll(@Body items: List<WaterIntake>)

    @DELETE("water-intakes/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
