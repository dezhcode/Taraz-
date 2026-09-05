package com.example.data.remote

import com.example.services.ApiConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var cachedBaseUrl: String? = null
    private var cachedApiService: ApiService? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Synchronized
    fun getApiService(): ApiService {
        val currentBaseUrl = ApiConfig.getBaseUrl()
        
        // If the base URL changed or hasn't been instantiated yet, recreate Retrofit
        if (cachedApiService == null || cachedBaseUrl != currentBaseUrl) {
            val formattedBaseUrl = if (currentBaseUrl.endsWith("/")) currentBaseUrl else "$currentBaseUrl/"
            val retrofit = Retrofit.Builder()
                .baseUrl(formattedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            
            cachedBaseUrl = currentBaseUrl
            cachedApiService = retrofit.create(ApiService::class.java)
        }
        
        return cachedApiService!!
    }
}
