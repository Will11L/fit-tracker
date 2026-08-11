package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.MuscleGoal
import retrofit2.http.*

interface MuscleGoalApi {

    @GET("muscle-goals")
    suspend fun getAll(): List<MuscleGoal>



    @PUT("muscle-goals/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body goal: MuscleGoal)

    @PUT("muscle-goals/bulk")
    suspend fun upsertAll(@Body goals: List<MuscleGoal>)

    @DELETE("muscle-goals/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
