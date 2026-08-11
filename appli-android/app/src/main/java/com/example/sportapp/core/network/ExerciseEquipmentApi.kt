package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.ExerciseEquipment
import retrofit2.http.*

interface ExerciseEquipmentApi {

    @GET("exercise-equipments")
    suspend fun getAll(): List<ExerciseEquipment>

    @GET("exercise-equipments/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): ExerciseEquipment



    @PUT("exercise-equipments/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body equipment: ExerciseEquipment)

    @PUT("exercise-equipments/bulk")
    suspend fun upsertAll(@Body equipments: List<ExerciseEquipment>)

    @DELETE("exercise-equipments/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
