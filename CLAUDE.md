# SimpleBank Demo — Project Notes for Claude

## Ringkasan Proyek

Demo observability end-to-end: Android app (EDOT RUM) + FastAPI backend (OTel traces/metrics) → Elastic Cloud.

## Stack

| Layer | Teknologi |
|-------|-----------|
| Android | Jetpack Compose, Retrofit/OkHttp, EDOT Android SDK 1.5.0 |
| Backend | FastAPI (Python), Elasticsearch async client |
| Infra | Docker Compose, nginx (TLS termination) |
| Observability | EDOT Android + OTel Python → Elastic APM (OTLP/HTTP) |

## Konfigurasi Penting

### Backend (.env)

```
ELASTICSEARCH_URL=https://<cluster-id>.ap-southeast-1.aws.elastic-cloud.com
ELASTICSEARCH_API_KEY=<elasticsearch-api-key>
OTLP_ENDPOINT=https://<cluster-id>.ingest.ap-southeast-1.aws.elastic-cloud.com:443
OTLP_TOKEN=<base64-api-key>
```

- `OTLP_TOKEN` adalah base64 dari `id:key` — dikirim sebagai header `Authorization: ApiKey <token>`
- **Jangan** gunakan `Bearer` — Elastic APM OTLP endpoint menolak dengan `ApiKey prefix not found`

### Android (SimpleBankApp.kt)

- `setExportUrl` → OTLP ingest endpoint Elastic Cloud (port 443)
- `setExportAuthentication(Authentication.ApiKey(...))` → sama dengan `OTLP_TOKEN` di backend
- `BASE_URL = "https://10.0.2.2"` → emulator mengakses host machine lewat IP ini (bukan localhost)

## Elasticsearch Indexes

| Index | Isi |
|-------|-----|
| `bank_accounts` | Data akun (account_001: Budi, account_002: Siti) |
| `simplebank-transactions` | Riwayat transaksi (awalnya kosong) |

Seed dengan: `python scripts/setup_elasticsearch.py`

## Android Build

| Komponen | Versi |
|----------|-------|
| AGP | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.3.0 |
| EDOT Android | 1.5.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

- EDOT 1.5.0 mensyaratkan compileSdk 36 + AGP 8.9.1+
- Kotlin 2.x: gunakan plugin `org.jetbrains.kotlin.plugin.compose` — **hapus** `composeOptions.kotlinCompilerExtensionVersion`

## Instrumentasi Android

Semua instrumentasi **otomatis via Gradle plugin** — tidak ada kode OTel manual:

```groovy
id 'co.elastic.otel.android.agent' version '1.5.0'           // RUM sessions, OTLP export
id 'co.elastic.otel.android.instrumentation.okhttp' version '1.5.0'  // HTTP spans + traceparent
```

`OtelSetup.kt` sudah dihapus. Jangan tambahkan kembali manual OTel SDK.

## TLS / Cert Lokal

- Self-signed cert di `nginx/certs/` untuk `CN=10.0.2.2`
- Di-copy ke `android/app/src/main/res/raw/local_cert.pem`
- Android mempercayainya via `network_security_config.xml` (domain-config untuk 10.0.2.2)
- Regenerate cert: `cd nginx && bash gen_self_signed_cert.sh`

## Menjalankan Backend

```bash
docker compose up --build          # start
docker compose logs -f backend     # lihat log
curl -k https://localhost/health   # verifikasi
```

## Hal yang Sudah Pernah Salah (Jangan Diulang)

1. **Auth OTLP**: Header harus `ApiKey`, bukan `Bearer`
2. **Index name**: `simplebank-transactions` (bukan `bank_transactions`)
3. **Android RUM**: Harus pakai EDOT Android SDK (`ElasticApmAgent`), bukan manual OTel SDK — manual SDK tidak terdeteksi sebagai RUM di Kibana
4. **Kotlin 2.x + Compose**: Tidak perlu `kotlinCompilerExtensionVersion` lagi
5. **setuptools**: Pin ke `==70.3.0` — versi ≥71 menghapus `pkg_resources` dari top-level
