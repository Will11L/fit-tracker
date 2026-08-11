package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.AvailableEquipment
import retrofit2.http.*

interface AvailableEquipmentApi {

    @GET("available-equipments")
    suspend fun getAll(): List<AvailableEquipment>

    @PUT("available-equipments/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body equipment: AvailableEquipment)

    @PUT("available-equipments/bulk")
    suspend fun upsertAll(@Body equipments: List<AvailableEquipment>)

    @DELETE("available-equipments/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
