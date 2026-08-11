package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.SupersetExercise
import retrofit2.http.*

interface SupersetExerciseApi {

    @GET("superset-exercises")
    suspend fun getAll(): List<SupersetExercise>

    @GET("superset-exercises/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): SupersetExercise



    @PUT("superset-exercises/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body exercise: SupersetExercise)

    @PUT("superset-exercises/bulk")
    suspend fun upsertAll(@Body exercises: List<SupersetExercise>)

    @DELETE("superset-exercises/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
