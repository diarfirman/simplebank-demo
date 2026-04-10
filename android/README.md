# Android — SimpleBank

Aplikasi Android demo SimpleBank yang diinstrumentasi menggunakan **[EDOT Android SDK](https://www.elastic.co/docs/reference/opentelemetry/edot-sdks/android)** (Elastic Distribution of OpenTelemetry).

## Fitur Observability

- **RUM Sessions** — lifecycle app otomatis dilacak sebagai session
- **HTTP Spans** — setiap request Retrofit/OkHttp otomatis menghasilkan span dengan W3C `traceparent` header
- **Trace Propagation** — trace context diteruskan ke backend sehingga request Android dan backend terhubung dalam satu trace

Semua instrumentasi ditangani oleh dua Gradle plugin EDOT — **tidak ada kode OTel manual** yang diperlukan.

## Prasyarat

- Android Studio Meerkat 2024.3.1 atau lebih baru
- JDK 17+
- Emulator dengan API level 26+ (atau device fisik)
- Backend SimpleBank berjalan (lihat root README)

## Setup

### 1. Buka proyek

Android Studio → **Open** → pilih folder `android/` (bukan root repo).

Tunggu Gradle sync selesai. Gradle 8.13 akan didownload otomatis oleh wrapper.

### 2. Trust self-signed cert lokal

Copy cert dari nginx ke Android raw resources:

```bash
cp ../nginx/certs/fullchain.pem app/src/main/res/raw/local_cert.pem
```

Cert ini dipakai oleh `network_security_config.xml` untuk mempercayai backend lokal di `10.0.2.2`.

### 3. Konfigurasi EDOT endpoint

Edit `app/src/main/java/com/example/simplebank/SimpleBankApp.kt`:

```kotlin
ElasticApmAgent.builder(this)
    .setServiceName("simplebank-android")
    .setServiceVersion("1.0.0")
    .setDeploymentEnvironment(BuildConfig.BUILD_TYPE)
    .setExportUrl("https://<cluster-id>.ingest.<region>.aws.elastic-cloud.com:443")
    .setExportAuthentication(Authentication.ApiKey("<base64-api-key>"))
    .build()
```

Nilai `setExportUrl` dan `setExportAuthentication` didapat dari Kibana → **Observability → Add data → OpenTelemetry**.

### 4. Jalankan emulator

**Device Manager** → pilih/buat AVD dengan API 26+ → klik **Run**.

Lalu **Run 'app'** (Shift+F10).

## Struktur Kode

```
app/src/main/java/com/example/simplebank/
├── SimpleBankApp.kt          # Application class — init EDOT agent
├── data/
│   ├── ApiService.kt         # Retrofit interface (GET/POST endpoints)
│   ├── RetrofitClient.kt     # OkHttp client (logging interceptor)
│   └── models/
│       └── Models.kt         # Data classes: BalanceResponse, Transaction, dll
└── ui/
    ├── MainActivity.kt       # Entry point Compose
    ├── DashboardScreen.kt    # Layar utama: saldo + riwayat transaksi
    └── TransferDialog.kt     # Dialog transfer dana
```

## Versi

| Komponen | Versi |
|----------|-------|
| AGP (Android Gradle Plugin) | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.3.0 |
| EDOT Android SDK | 1.5.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| Compose BOM | 2024.05.00 |

## Gradle Plugins EDOT

```groovy
// android/app/build.gradle
id 'co.elastic.otel.android.agent' version '1.5.0'
id 'co.elastic.otel.android.instrumentation.okhttp' version '1.5.0'
```

- `android.agent` — core EDOT agent: session tracking, resource attributes, OTLP export
- `instrumentation.okhttp` — auto-instrumentation OkHttp: HTTP spans + W3C traceparent header injection

## Catatan Local Dev

- Emulator mengakses host machine melalui IP `10.0.2.2` (bukan `localhost`)
- TLS diperlukan oleh EDOT agent — nginx menyediakan HTTPS dengan self-signed cert
- `network_security_config.xml` mengkonfigurasi Android untuk mempercayai cert tersebut di debug build
- Untuk production: ganti `BASE_URL` dengan domain publik yang memiliki cert valid
