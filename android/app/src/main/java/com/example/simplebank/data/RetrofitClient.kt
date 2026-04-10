package com.example.simplebank.data

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.propagation.TextMapSetter
import okhttp3.Interceptor
import okhttp3.Response

// ---------------------------------------------------------------------------
// W3C Trace Context propagation interceptor
// ---------------------------------------------------------------------------

class TraceparentInterceptor : Interceptor {
    private val propagator = GlobalOpenTelemetry.getPropagators().textMapPropagator
    private val setter = TextMapSetter<okhttp3.Request.Builder> { carrier, key, value ->
        carrier?.header(key, value)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        propagator.inject(
            io.opentelemetry.context.Context.current(),
            requestBuilder,
            setter,
        )
        return chain.proceed(requestBuilder.build())
    }
}

// ---------------------------------------------------------------------------
// Retrofit + OkHttp client
// ---------------------------------------------------------------------------

object RetrofitClient {

    // For local emulator dev use: "https://10.0.2.2"
    // For production use your domain: "https://your-domain.com"
    private const val BASE_URL = "https://10.0.2.2"

    // Certificate Pinning — SHA-256 of the server's certificate public key
    // Active only in release builds (production Let's Encrypt cert)
    // To obtain the pin: run `openssl s_client -connect your-domain.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`
    private val certificatePinner = CertificatePinner.Builder()
        // Replace with actual SHA-256 pins from your Let's Encrypt cert + intermediate CA
        // .add("your-domain.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.example.simplebank.BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .apply {
            // Only apply cert pinning in release builds
            if (!com.example.simplebank.BuildConfig.DEBUG) {
                certificatePinner(certificatePinner)
            }
        }
        .addInterceptor(TraceparentInterceptor())
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
