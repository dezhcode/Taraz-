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
}

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
