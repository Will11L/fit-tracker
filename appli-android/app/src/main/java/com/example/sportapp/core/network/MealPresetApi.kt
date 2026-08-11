package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.MealPreset
import retrofit2.http.*

interface MealPresetApi {

    @GET("meal-presets")
    suspend fun getAll(): List<MealPreset>

    @GET("meal-presets/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): MealPreset

    @PUT("meal-presets/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: MealPreset)

    @PUT("meal-presets/bulk")
    suspend fun upsertAll(@Body items: List<MealPreset>)

    @DELETE("meal-presets/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
