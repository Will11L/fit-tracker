package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Food
import retrofit2.http.*

interface FoodApi {

    @GET("foods")
    suspend fun getAll(): List<Food>

    @GET("foods/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Food

    @PUT("foods/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: Food)

    @PUT("foods/bulk")
    suspend fun upsertAll(@Body items: List<Food>)

    @DELETE("foods/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
