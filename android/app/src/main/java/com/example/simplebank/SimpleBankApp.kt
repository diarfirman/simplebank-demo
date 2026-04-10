package com.example.simplebank

import android.app.Application
import co.elastic.otel.android.ElasticApmAgent
import co.elastic.otel.android.connectivity.Authentication

class SimpleBankApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ElasticApmAgent.builder(this)
            .setServiceName("simplebank-android")
            .setServiceVersion("1.0.0")
            .setDeploymentEnvironment(BuildConfig.BUILD_TYPE)
            .setExportUrl("https://elasticsearch-id.ingest.ap-southeast-1.aws.elastic-cloud.com:443")
            .setExportAuthentication(Authentication.ApiKey("am5HUWRwMEI2dW45M0k3R3ZyVkI6VVFtTDlSbXVuU2FwUHl4eW9fUDliQQ=="))
            .build()
    }
}
