package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Exercise
import retrofit2.http.*

interface ExerciseApi {

    @GET("exercises")
    suspend fun getAll(): List<Exercise>

    @GET("exercises/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Exercise



    @PUT("exercises/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body exercise: Exercise)

    @PUT("exercises/bulk")
    suspend fun upsertAll(@Body exercises: List<Exercise>)

    @DELETE("exercises/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
