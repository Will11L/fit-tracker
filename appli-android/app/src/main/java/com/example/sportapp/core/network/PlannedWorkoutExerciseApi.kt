package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.PlannedWorkoutExercise
import retrofit2.http.*

interface PlannedWorkoutExerciseApi {

    @GET("planned-workout-exercises")
    suspend fun getAll(): List<PlannedWorkoutExercise>

    @PUT("planned-workout-exercises/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body exercise: PlannedWorkoutExercise)

    @PUT("planned-workout-exercises/bulk")
    suspend fun upsertAll(@Body exercises: List<PlannedWorkoutExercise>)

    @DELETE("planned-workout-exercises/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
