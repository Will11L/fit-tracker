package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.User
import retrofit2.http.*

interface UserApi {

    @GET("users")
    suspend fun getAll(): List<User>

    @GET("users/{id}")
    suspend fun getById(@Path("id") id: Int): User



    @PUT("users/{id}")
    suspend fun upsert(@Path("id") id: Int, @Body user: User)

    @PUT("users/bulk")
    suspend fun upsertAll(@Body users: List<User>)

    @DELETE("users/{id}")
    suspend fun delete(@Path("id") id: Int)


}
