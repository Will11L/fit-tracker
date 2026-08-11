package com.example.sportapp.core.network

import com.example.sportapp.core.data.model.Quote
import retrofit2.http.*

interface QuoteApi {

    @GET("quotes")
    suspend fun getAll(): List<Quote>

    @GET("quotes/{uuid}")
    suspend fun getByUUID(@Path("uuid") uuid: String): Quote

    @PUT("quotes/{uuid}")
    suspend fun upsert(@Path("uuid") uuid: String, @Body item: Quote)

    @PUT("quotes/bulk")
    suspend fun upsertAll(@Body items: List<Quote>)

    @DELETE("quotes/{uuid}")
    suspend fun delete(@Path("uuid") uuid: String)
}
