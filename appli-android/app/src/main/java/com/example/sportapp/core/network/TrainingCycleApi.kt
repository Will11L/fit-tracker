package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.TrainingCycle
import retrofit2.http.*

interface TrainingCycleApi {

    @GET("training-cycles")
    suspend fun getAll(): List<TrainingCycle>

    @GET("training-cycles/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): TrainingCycle



    @PUT("training-cycles/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body cycle: TrainingCycle)

    @PUT("training-cycles/bulk")
    suspend fun upsertAll(@Body cycles: List<TrainingCycle>)

    @DELETE("training-cycles/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
