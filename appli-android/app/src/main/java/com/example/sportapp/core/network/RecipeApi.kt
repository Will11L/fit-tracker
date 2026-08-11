package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Recipe
import retrofit2.http.*

interface RecipeApi {

    @GET("recipes")
    suspend fun getAll(): List<Recipe>

    @GET("recipes/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Recipe

    @PUT("recipes/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: Recipe)

    @PUT("recipes/bulk")
    suspend fun upsertAll(@Body items: List<Recipe>)

    @DELETE("recipes/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
