import logging
import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from dotenv import load_dotenv
from elasticsearch import AsyncElasticsearch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, field_validator

# OpenTelemetry
from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.elasticsearch import ElasticsearchInstrumentor
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

load_dotenv()

# ---------------------------------------------------------------------------
# OpenTelemetry Setup
# ---------------------------------------------------------------------------

_resource = Resource.create({"service.name": "simplebank-backend", "service.version": "1.0.0"})

# Traces
_tracer_provider = TracerProvider(resource=_resource)
_tracer_provider.add_span_processor(
    BatchSpanProcessor(
        OTLPSpanExporter(
            endpoint=os.environ["OTLP_ENDPOINT"] + "/v1/traces",
            headers={"Authorization": "ApiKey " + os.environ["OTLP_TOKEN"]},
        )
    )
)
trace.set_tracer_provider(_tracer_provider)
tracer = trace.get_tracer("simplebank.backend")

# Metrics
_metric_reader = PeriodicExportingMetricReader(
    OTLPMetricExporter(
        endpoint=os.environ["OTLP_ENDPOINT"] + "/v1/metrics",
        headers={"Authorization": "ApiKey " + os.environ["OTLP_TOKEN"]},
    )
)
_meter_provider = MeterProvider(resource=_resource, metric_readers=[_metric_reader])
metrics.set_meter_provider(_meter_provider)
meter = metrics.get_meter("simplebank.backend")

transfer_counter = meter.create_counter(
    name="simplebank.transfer.total",
    description="Total number of transfer attempts",
    unit="1",
)
balance_query_counter = meter.create_counter(
    name="simplebank.balance.queries",
    description="Total balance queries",
    unit="1",
)

# Logs — inject trace_id / span_id into log records
LoggingInstrumentor().instrument(set_logging_format=True)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("simplebank")

# Elasticsearch auto-instrumentation
ElasticsearchInstrumentor().instrument()

# ---------------------------------------------------------------------------
# Elasticsearch client
# ---------------------------------------------------------------------------

es: AsyncElasticsearch


@asynccontextmanager
async def lifespan(app: FastAPI):
    global es
    es = AsyncElasticsearch(
        hosts=[os.environ["ELASTIC_URL"]],
        api_key=os.environ["ELASTIC_API_KEY"],
    )
    logger.info("Elasticsearch client initialised")
    yield
    await es.close()
    logger.info("Elasticsearch client closed")


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="SimpleBank API", version="1.0.0", lifespan=lifespan)
FastAPIInstrumentor.instrument_app(app, tracer_provider=_tracer_provider)


# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------

class TransferRequest(BaseModel):
    from_account_id: str
    to_account_id: str
    amount: float

    @field_validator("amount")
    @classmethod
    def amount_must_be_positive(cls, v: float) -> float:
        if v <= 0:
            raise ValueError("amount must be greater than zero")
        return v


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

async def _get_account(account_id: str) -> dict:
    result = await es.get(index="bank_accounts", id=account_id, ignore=[404])
    if not result.get("found"):
        raise HTTPException(status_code=404, detail=f"Account '{account_id}' not found")
    return result["_source"]


def _current_trace_id() -> str:
    span = trace.get_current_span()
    ctx = span.get_span_context()
    if ctx and ctx.is_valid:
        return format(ctx.trace_id, "032x")
    return ""


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}


@app.get("/balance/{account_id}")
async def get_balance(account_id: str):
    with tracer.start_as_current_span("fetch_balance") as span:
        span.set_attribute("account.id", account_id)
        account = await _get_account(account_id)
        balance_query_counter.add(1, {"account_id": account_id})
        logger.info("Balance fetched for account %s", account_id)
        return {
            "account_id": account_id,
            "owner_name": account["owner_name"],
            "balance": account["balance"],
            "currency": account["currency"],
        }


@app.post("/transfer", status_code=200)
async def transfer(req: TransferRequest):
    with tracer.start_as_current_span("transfer") as span:
        span.set_attribute("transfer.from", req.from_account_id)
        span.set_attribute("transfer.to", req.to_account_id)
        span.set_attribute("transfer.amount", req.amount)

        # Step 1: Validate balances
        with tracer.start_as_current_span("validate_balance"):
            if req.from_account_id == req.to_account_id:
                raise HTTPException(status_code=400, detail="Cannot transfer to the same account")
            sender = await _get_account(req.from_account_id)
            await _get_account(req.to_account_id)  # verify receiver exists
            if sender["balance"] < req.amount:
                transfer_counter.add(1, {"status": "failed", "reason": "insufficient_funds"})
                raise HTTPException(status_code=422, detail="Insufficient balance")

        transaction_id = str(uuid.uuid4())
        trace_id = _current_trace_id()
        now = datetime.now(timezone.utc).isoformat()

        # Step 2: Update balances (debit sender, credit receiver)
        with tracer.start_as_current_span("update_accounts"):
            await es.update(
                index="bank_accounts",
                id=req.from_account_id,
                body={"script": {"source": "ctx._source.balance -= params.amt", "params": {"amt": req.amount}}},
            )
            await es.update(
                index="bank_accounts",
                id=req.to_account_id,
                body={"script": {"source": "ctx._source.balance += params.amt", "params": {"amt": req.amount}}},
            )

        # Step 3: Record transaction
        with tracer.start_as_current_span("save_transaction"):
            await es.index(
                index="simplebank-transactions",
                id=transaction_id,
                document={
                    "transaction_id": transaction_id,
                    "from_account_id": req.from_account_id,
                    "to_account_id": req.to_account_id,
                    "amount": req.amount,
                    "status": "success",
                    "timestamp": now,
                    "trace_id": trace_id,
                },
            )

        transfer_counter.add(1, {"status": "success"})
        logger.info(
            "Transfer success: %s -> %s, amount=%.2f, tx=%s",
            req.from_account_id,
            req.to_account_id,
            req.amount,
            transaction_id,
        )
        return {"transaction_id": transaction_id, "status": "success", "amount": req.amount}


@app.get("/transactions/{account_id}")
async def get_transactions(account_id: str):
    with tracer.start_as_current_span("fetch_transactions") as span:
        span.set_attribute("account.id", account_id)
        result = await es.search(
            index="simplebank-transactions",
            body={
                "query": {
                    "bool": {
                        "should": [
                            {"term": {"from_account_id": account_id}},
                            {"term": {"to_account_id": account_id}},
                        ]
                    }
                },
                "sort": [{"timestamp": {"order": "desc"}}],
                "size": 20,
            },
        )
        hits = [h["_source"] for h in result["hits"]["hits"]]
        return {"account_id": account_id, "transactions": hits}
