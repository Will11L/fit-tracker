package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.CycleWorkout
import retrofit2.http.*

interface CycleWorkoutApi {

    @GET("cycle-workouts")
    suspend fun getAll(): List<CycleWorkout>

    @PUT("cycle-workouts/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body cycleWorkout: CycleWorkout)

    @PUT("cycle-workouts/bulk")
    suspend fun upsertAll(@Body cycleWorkouts: List<CycleWorkout>)

    @DELETE("cycle-workouts/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
