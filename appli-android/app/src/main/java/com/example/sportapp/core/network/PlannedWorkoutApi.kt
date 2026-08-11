package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.PlannedWorkout
import retrofit2.http.*

interface PlannedWorkoutApi {

    @GET("planned-workouts")
    suspend fun getAll(): List<PlannedWorkout>

    @GET("planned-workouts/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): PlannedWorkout



    @PUT("planned-workouts/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body workout: PlannedWorkout)

    @PUT("planned-workouts/bulk")
    suspend fun upsertAll(@Body workouts: List<PlannedWorkout>)

    @DELETE("planned-workouts/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
