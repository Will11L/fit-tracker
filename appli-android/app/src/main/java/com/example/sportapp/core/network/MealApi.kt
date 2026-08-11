package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Meal
import retrofit2.http.*

interface MealApi {

    @GET("meals")
    suspend fun getAll(): List<Meal>

    @GET("meals/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Meal

    @PUT("meals/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: Meal)

    @PUT("meals/bulk")
    suspend fun upsertAll(@Body items: List<Meal>)

    @DELETE("meals/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
