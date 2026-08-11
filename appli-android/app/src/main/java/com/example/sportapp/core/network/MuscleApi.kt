package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Muscle
import retrofit2.http.*

interface MuscleApi {

    @GET("muscles")
    suspend fun getAll(): List<Muscle>

    @GET("muscles/{uuid}")
    suspend fun getMuscleByUUID(@Path("uuid") uuid: String): Muscle



    @PUT("muscles/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body muscle: Muscle)

    @PUT("muscles/bulk")
    suspend fun upsertAll(@Body muscles: List<Muscle>)

    @DELETE("muscles/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
