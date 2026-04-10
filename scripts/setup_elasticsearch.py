#!/usr/bin/env python3
"""
Bootstrap Elasticsearch indexes and seed dummy data.
Run once before starting the backend.

Usage:
    pip install elasticsearch python-dotenv
    python scripts/setup_elasticsearch.py
"""

import os
import sys
from datetime import datetime, timezone

from dotenv import load_dotenv
from elasticsearch import Elasticsearch

load_dotenv()

ELASTIC_URL = os.environ.get("ELASTIC_URL")
ELASTIC_API_KEY = os.environ.get("ELASTIC_API_KEY")

if not ELASTIC_URL or not ELASTIC_API_KEY:
    print("ERROR: Set ELASTIC_URL and ELASTIC_API_KEY in .env")
    sys.exit(1)

es = Elasticsearch(hosts=[ELASTIC_URL], api_key=ELASTIC_API_KEY)

# ---------------------------------------------------------------------------
# Index: bank_accounts
# ---------------------------------------------------------------------------

ACCOUNTS_INDEX = "bank_accounts"
ACCOUNTS_MAPPING = {
    "mappings": {
        "properties": {
            "account_id": {"type": "keyword"},
            "owner_name": {"type": "text", "fields": {"keyword": {"type": "keyword"}}},
            "balance": {"type": "double"},
            "currency": {"type": "keyword"},
            "created_at": {"type": "date"},
        }
    }
}

# ---------------------------------------------------------------------------
# Index: bank_transactions
# ---------------------------------------------------------------------------

TRANSACTIONS_INDEX = "bank_transactions"
TRANSACTIONS_MAPPING = {
    "mappings": {
        "properties": {
            "transaction_id": {"type": "keyword"},
            "from_account_id": {"type": "keyword"},
            "to_account_id": {"type": "keyword"},
            "amount": {"type": "double"},
            "status": {"type": "keyword"},
            "timestamp": {"type": "date"},
            "trace_id": {"type": "keyword"},
        }
    }
}

# ---------------------------------------------------------------------------
# Dummy accounts
# ---------------------------------------------------------------------------

DUMMY_ACCOUNTS = [
    {
        "account_id": "account_001",
        "owner_name": "Budi Santoso",
        "balance": 5_000_000.0,
        "currency": "IDR",
        "created_at": datetime.now(timezone.utc).isoformat(),
    },
    {
        "account_id": "account_002",
        "owner_name": "Siti Rahayu",
        "balance": 3_500_000.0,
        "currency": "IDR",
        "created_at": datetime.now(timezone.utc).isoformat(),
    },
    {
        "account_id": "account_003",
        "owner_name": "Ahmad Fauzi",
        "balance": 8_250_000.0,
        "currency": "IDR",
        "created_at": datetime.now(timezone.utc).isoformat(),
    },
]


def create_index(name: str, mapping: dict) -> None:
    if es.indices.exists(index=name):
        print(f"  [SKIP] Index '{name}' already exists")
        return
    es.indices.create(index=name, body=mapping)
    print(f"  [OK]   Index '{name}' created")


def seed_accounts() -> None:
    for acc in DUMMY_ACCOUNTS:
        result = es.index(index=ACCOUNTS_INDEX, id=acc["account_id"], document=acc)
        print(f"  [OK]   Account '{acc['account_id']}' ({acc['owner_name']}) — result: {result['result']}")


if __name__ == "__main__":
    print("\n=== SimpleBank — Elasticsearch Bootstrap ===\n")

    print("Checking connection...")
    info = es.info()
    print(f"  Connected to: {info['name']} (Elasticsearch {info['version']['number']})\n")

    print("Creating indexes...")
    create_index(ACCOUNTS_INDEX, ACCOUNTS_MAPPING)
    create_index(TRANSACTIONS_INDEX, TRANSACTIONS_MAPPING)

    print("\nSeeding dummy accounts...")
    seed_accounts()

    print("\nDone! Verify data at your Kibana Discover page.")
