package com.example.simplebank.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // For local emulator dev: "https://10.0.2.2" (host machine from emulator)
    private const val BASE_URL = "https://10.0.2.2"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.example.simplebank.BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    // W3C Trace Context propagation is handled automatically by the
    // EDOT OkHttp Gradle plugin (co.elastic.otel.android.instrumentation.okhttp)
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
