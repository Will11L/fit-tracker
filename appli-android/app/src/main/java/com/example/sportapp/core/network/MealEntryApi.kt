package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.MealEntry
import retrofit2.http.*

interface MealEntryApi {

    @GET("meal-entries")
    suspend fun getAll(): List<MealEntry>

    @GET("meal-entries/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): MealEntry

    @PUT("meal-entries/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: MealEntry)

    @PUT("meal-entries/bulk")
    suspend fun upsertAll(@Body items: List<MealEntry>)

    @DELETE("meal-entries/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
