package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.NutritionGoal
import retrofit2.http.*

interface NutritionGoalApi {

    @GET("nutrition-goals")
    suspend fun getAll(): List<NutritionGoal>

    @GET("nutrition-goals/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): NutritionGoal

    @PUT("nutrition-goals/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: NutritionGoal)

    @PUT("nutrition-goals/bulk")
    suspend fun upsertAll(@Body items: List<NutritionGoal>)

    @DELETE("nutrition-goals/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
