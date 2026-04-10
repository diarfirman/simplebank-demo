package com.example.simplebank

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import java.util.concurrent.TimeUnit

object OtelSetup {

    private const val PREFS_FILE = "simplebank_secure_prefs"
    private const val KEY_OTLP_ENDPOINT = "otlp_endpoint"
    private const val KEY_OTLP_TOKEN = "otlp_token"

    // Fallback defaults — override via EncryptedSharedPreferences at runtime
    private const val DEFAULT_OTLP_ENDPOINT = "https://your-apm.apm.io:443"
    private const val DEFAULT_OTLP_TOKEN = "your_secret_token"

    fun init(context: Context) {
        val (endpoint, token) = loadCredentials(context)

        val resource = Resource.getDefault().merge(
            Resource.create(
                io.opentelemetry.api.common.Attributes.of(
                    ResourceAttributes.SERVICE_NAME, "simplebank-android",
                    ResourceAttributes.SERVICE_VERSION, "1.0.0",
                    ResourceAttributes.DEPLOYMENT_ENVIRONMENT, BuildConfig.BUILD_TYPE,
                )
            )
        )

        // --- Tracer ---
        val spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint("$endpoint/v1/traces")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build()

        // --- Meter ---
        val metricExporter = OtlpHttpMetricExporter.builder()
            .setEndpoint("$endpoint/v1/metrics")
            .addHeader("Authorization", "Bearer $token")
            .build()

        val metricReader = PeriodicMetricReader.builder(metricExporter)
            .setInterval(30, TimeUnit.SECONDS)
            .build()

        val meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(metricReader)
            .build()

        val otel = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .buildAndRegisterGlobal()

        android.util.Log.i("OtelSetup", "OpenTelemetry initialised (service: simplebank-android)")
    }

    private fun loadCredentials(context: Context): Pair<String, String> {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val endpoint = prefs.getString(KEY_OTLP_ENDPOINT, DEFAULT_OTLP_ENDPOINT)!!
            val token = prefs.getString(KEY_OTLP_TOKEN, DEFAULT_OTLP_TOKEN)!!
            Pair(endpoint, token)
        } catch (e: Exception) {
            android.util.Log.w("OtelSetup", "Could not read encrypted prefs, using defaults: ${e.message}")
            Pair(DEFAULT_OTLP_ENDPOINT, DEFAULT_OTLP_TOKEN)
        }
    }
}
