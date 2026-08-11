package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.ActualWorkoutExercise
import retrofit2.http.*

interface ActualWorkoutExerciseApi {

    @GET("actual-workout-exercises")
    suspend fun getAll(): List<ActualWorkoutExercise>

    @GET("actual-workout-exercises/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): ActualWorkoutExercise

    @PUT("actual-workout-exercises/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body actualWorkoutExercise: ActualWorkoutExercise)

    @PUT("actual-workout-exercises/bulk")
    suspend fun upsertAll(@Body actualWorkoutExercises: List<ActualWorkoutExercise>)

    @DELETE("actual-workout-exercises/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
