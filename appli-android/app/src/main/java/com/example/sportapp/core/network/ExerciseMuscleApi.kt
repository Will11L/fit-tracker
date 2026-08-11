package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.ExerciseMuscle
import retrofit2.http.*

interface ExerciseMuscleApi {

    @GET("exercise-muscles")
    suspend fun getAll(): List<ExerciseMuscle>

    @PUT("exercise-muscles/{uuid}")
    suspend fun upsert(
        @Path("uuid") uuid: String,
        @Body exerciseMuscle: ExerciseMuscle
    )

    @PUT("exercise-muscles/bulk")
    suspend fun upsertAll(@Body exerciseMuscles: List<ExerciseMuscle>)

    @DELETE("exercise-muscles/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
