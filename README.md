# SimpleBank Demo — Elastic EDOT Observability

Demo aplikasi perbankan sederhana yang menunjukkan **end-to-end observability** menggunakan [Elastic Distribution of OpenTelemetry (EDOT)](https://www.elastic.co/docs/reference/opentelemetry):

- **Backend** — FastAPI (Python) dengan EDOT auto-instrumentation: traces, metrics, logs
- **Android** — Jetpack Compose dengan EDOT Android SDK: RUM, HTTP spans (W3C Trace Context propagation)
- **Infrastructure** — nginx (TLS termination) + Elasticsearch (Elastic Cloud)

```
Android App
    │  HTTPS (W3C traceparent header auto-injected by EDOT OkHttp plugin)
    ▼
nginx (TLS) → FastAPI Backend
                    │  OTLP/HTTP (traces + metrics)
                    ▼
            Elastic APM / Elastic Cloud
```

## Prasyarat

- Docker & Docker Compose
- Android Studio (Meerkat 2024.3.1+) dengan emulator API 26+
- Elastic Cloud cluster (APM + Elasticsearch)

## Struktur Proyek

```
simplebank-demo/
├── backend/            # FastAPI service
│   ├── main.py
│   ├── requirements.txt
│   └── Dockerfile
├── nginx/              # Reverse proxy + TLS
│   ├── nginx.conf
│   └── certs/          # Self-signed cert untuk local dev
├── android/            # Android app (Jetpack Compose + EDOT)
├── scripts/
│   └── setup_elasticsearch.py   # Seed data awal
├── docker-compose.yml
└── .env                # Konfigurasi secrets (tidak di-commit)
```

## Setup Backend

### 1. Generate self-signed TLS cert (sekali saja)

```bash
cd nginx
bash gen_self_signed_cert.sh
```

Ini menghasilkan `nginx/certs/fullchain.pem` dan `nginx/certs/privkey.pem` untuk `CN=10.0.2.2`.

### 2. Konfigurasi environment

Buat file `.env` di root proyek:

```env
ELASTICSEARCH_URL=https://<cluster-id>.ap-southeast-1.aws.elastic-cloud.com
ELASTICSEARCH_API_KEY=<elasticsearch-api-key>
OTLP_ENDPOINT=https://<cluster-id>.ingest.ap-southeast-1.aws.elastic-cloud.com:443
OTLP_TOKEN=<base64-api-key>
```

Nilai `OTLP_TOKEN` adalah API key dari Kibana → **Observability → Add data → OpenTelemetry** (format base64 dari `id:key`).

### 3. Seed data Elasticsearch

```bash
pip install elasticsearch python-dotenv
python scripts/setup_elasticsearch.py
```

Ini membuat index `bank_accounts` (2 akun test) dan `simplebank-transactions`.

### 4. Jalankan backend

```bash
docker compose up --build
```

Verifikasi:

```bash
curl -k https://localhost/health
# {"status":"ok"}
```

## Setup Android

Lihat [android/README.md](android/README.md) untuk instruksi lengkap.

### Quick start

1. Buka Android Studio → **Open** → pilih folder `android/`
2. Tunggu Gradle sync selesai (akan download Gradle 8.13 + AGP 8.13.2)
3. Update `SimpleBankApp.kt` dengan endpoint dan API key Elastic kamu
4. Copy `nginx/certs/fullchain.pem` ke `android/app/src/main/res/raw/local_cert.pem`
5. Jalankan di emulator (API 26+)

## API Endpoints

| Method | Path | Deskripsi |
|--------|------|-----------|
| `GET` | `/health` | Health check |
| `GET` | `/balance/{account_id}` | Saldo rekening |
| `GET` | `/transactions/{account_id}` | Riwayat transaksi |
| `POST` | `/transfer` | Transfer dana |

## Data Test

Setelah seed, dua akun tersedia:

| Account ID | Nama | Saldo Awal |
|------------|------|-----------|
| `account_001` | Budi Santoso | Rp 10.000.000 |
| `account_002` | Siti Rahayu | Rp 5.000.000 |

## Observability di Elastic

Setelah app berjalan, data akan muncul di Kibana:

- **APM → Services → simplebank-backend** — traces dari FastAPI
- **APM → Services → simplebank-android** — RUM sessions, HTTP spans dari Android
- **Discover** — index `simplebank-transactions`, `bank_accounts`

Untuk dashboard siap pakai, install content package **"Android OpenTelemetry Assets"** di Kibana → **Integrations**.
