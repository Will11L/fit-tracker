package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.SupersetGroup
import retrofit2.http.*

interface SupersetGroupApi {

    @GET("superset-groups")
    suspend fun getAll(): List<SupersetGroup>

    @GET("superset-groups/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): SupersetGroup



    @PUT("superset-groups/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body group: SupersetGroup)

    @PUT("superset-groups/bulk")
    suspend fun upsertAll(@Body groups: List<SupersetGroup>)

    @DELETE("superset-groups/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
