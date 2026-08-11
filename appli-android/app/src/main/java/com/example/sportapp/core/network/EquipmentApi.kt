package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Equipment
import retrofit2.http.*

interface EquipmentApi {

    @GET("equipments")
    suspend fun getAll(): List<Equipment>

    @GET("equipments/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Equipment



    @PUT("equipments/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body equipment: Equipment)

    @PUT("equipments/bulk")
    suspend fun upsertAll(@Body equipments: List<Equipment>)

    @DELETE("equipments/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
