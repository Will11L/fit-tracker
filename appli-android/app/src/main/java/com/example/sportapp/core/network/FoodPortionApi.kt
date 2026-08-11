package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.FoodPortion
import retrofit2.http.*

interface FoodPortionApi {

    @GET("food-portions")
    suspend fun getAll(): List<FoodPortion>

    @GET("food-portions/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): FoodPortion

    @PUT("food-portions/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: FoodPortion)

    @PUT("food-portions/bulk")
    suspend fun upsertAll(@Body items: List<FoodPortion>)

    @DELETE("food-portions/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
