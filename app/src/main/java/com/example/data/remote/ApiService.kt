package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET(".")
    suspend fun healthCheck(): HealthResponse

    @POST("c/api/chat")
    suspend fun chat(
        @Body request: ChatRequest
    ): ChatResponse

    @POST("c/api/otp/request")
    suspend fun requestOtp(
        @Body request: OtpRequest
    ): OtpResponse

    @POST("c/api/otp/verify")
    suspend fun verifyOtp(
        @Body request: OtpVerifyRequest
    ): OtpVerifyResponse
}

data class OtpRequest(
    val phone: String,
    val name: String
)

data class OtpResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class OtpVerifyRequest(
    val phone: String,
    val code: String
)

data class OtpVerifyResponse(
    val success: Boolean = false,
    val verified: Boolean = false,
    val token: String? = null,
    val error: String? = null
)

data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val success: Boolean,
    val answer: String?,
    val error: String?
)

data class HealthResponse(
    val status: String? = null,
    val service: String? = null,
    val architecture: String? = null
)
