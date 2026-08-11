package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.RecipeIngredient
import retrofit2.http.*

interface RecipeIngredientApi {

    @GET("recipe-ingredients")
    suspend fun getAll(): List<RecipeIngredient>

    @GET("recipe-ingredients/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): RecipeIngredient

    @PUT("recipe-ingredients/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: RecipeIngredient)

    @PUT("recipe-ingredients/bulk")
    suspend fun upsertAll(@Body items: List<RecipeIngredient>)

    @DELETE("recipe-ingredients/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
