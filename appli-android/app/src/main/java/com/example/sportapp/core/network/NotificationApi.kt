package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Notification
import retrofit2.http.*

interface NotificationApi {

    @GET("notifications")
    suspend fun getAll(): List<Notification>

    @GET("notifications/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Notification



    @PUT("notifications/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body notification: Notification)

    @PUT("notifications/bulk")
    suspend fun upsertAll(@Body notifications: List<Notification>)

    @DELETE("notifications/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
