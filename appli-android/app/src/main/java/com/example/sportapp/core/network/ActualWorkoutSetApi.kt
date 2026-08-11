package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.ActualWorkoutSet
import retrofit2.http.*

interface ActualWorkoutSetApi {

    @GET("actual-workout-sets")
    suspend fun getAll(): List<ActualWorkoutSet>

    @GET("actual-workout-sets/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): ActualWorkoutSet



    @PUT("actual-workout-sets/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body actualWorkoutSet: ActualWorkoutSet)

    @PUT("actual-workout-sets/bulk")
    suspend fun upsertAll(@Body actualWorkoutSets: List<ActualWorkoutSet>)

    @DELETE("actual-workout-sets/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
