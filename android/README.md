# Android — SimpleBank

## Setup

1. Buka Android Studio → **Open** → pilih folder `android/`
2. Biarkan Gradle sync selesai
3. Copy file-file Kotlin di bawah ini ke package utama kamu

## Package structure yang dibutuhkan

```
app/src/main/java/com/example/simplebank/
├── SimpleBankApp.kt          ← Application class (OTel init)
├── OtelSetup.kt              ← OTel SDK configuration
├── data/
│   ├── ApiService.kt         ← Retrofit interface
│   ├── RetrofitClient.kt     ← OkHttp + cert pinning setup
│   └── models/
│       ├── BalanceResponse.kt
│       ├── TransferRequest.kt
│       ├── TransferResponse.kt
│       └── TransactionsResponse.kt
└── ui/
    ├── MainActivity.kt
    ├── DashboardScreen.kt    ← Jetpack Compose UI
    └── TransferDialog.kt
```

## Dependencies — tambahkan ke build.gradle (app)

```groovy
// Retrofit + OkHttp
implementation 'com.squareup.retrofit2:retrofit:2.11.0'
implementation 'com.squareup.retrofit2:converter-gson:2.11.0'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

// OpenTelemetry Android
implementation 'io.opentelemetry:opentelemetry-api:1.38.0'
implementation 'io.opentelemetry:opentelemetry-sdk:1.38.0'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp:1.38.0'
implementation 'io.opentelemetry:opentelemetry-semconv:1.25.0-alpha'

// Encrypted storage
implementation 'androidx.security:security-crypto:1.1.0-alpha06'

// Jetpack Compose
implementation platform('androidx.compose:compose-bom:2024.05.00')
implementation 'androidx.compose.ui:ui'
implementation 'androidx.compose.material3:material3'
implementation 'androidx.activity:activity-compose:1.9.0'
```

## Catatan local dev

- Backend berjalan di Docker di mesin yang sama
- Gunakan `BASE_URL = "https://10.0.2.2"` di emulator (bukan localhost)
- Tambahkan `network_security_config.xml` untuk trust self-signed cert di debug build
