package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.ActualWorkout
import retrofit2.http.*

interface ActualWorkoutApi {

    @GET("actual-workouts")
    suspend fun getAll(): List<ActualWorkout>

    @GET("actual-workouts/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): ActualWorkout

    @PUT("actual-workouts/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body actualWorkout: ActualWorkout)

    @PUT("actual-workouts/bulk")
    suspend fun upsertAll(@Body actualWorkouts: List<ActualWorkout>)

    @DELETE("actual-workouts/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
