package com.example.sportapp.core.network

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun getToken(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @POST("refresh")
    suspend fun refresh(
        @Body body: RefreshRequest
    ): TokenResponse

    @POST("logout")
    suspend fun logout(
        @Body body: RefreshRequest
    )

    @POST("signup")
    suspend fun signup(
        @Body body: SignupRequest
    )
}

data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String
)

data class RefreshRequest(
    val refresh_token: String
)

data class SignupRequest(
    val username: String,
    val password: String,
    // Email optionnel (2026-06-06) -- le login reste username.
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)
