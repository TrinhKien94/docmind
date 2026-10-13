# DocMind — doc-service-py (FastAPI document parser)

Parses uploaded documents into **canonical blocks** (see
`../docs/canonical-schema.md`) for the DocMind RAG pipeline. The Java
orchestrator (`api-java`) is the only caller; the chat path never touches this
service.

Heavy ML libraries (PyMuPDF, Docling, PaddleOCR/PaddlePaddle, firecrawl
pdf-inspector, sentence-transformers) are **optional** and **lazy-imported**
behind stub engines, so the service boots and all tests pass on CPU with only
`requirements.txt` installed and no model downloads.

## Base URL / port

- Local / Docker: `http://localhost:8091`
- Correlation header (set by the Java side): **`X-Correlation-Id`** — echoed on
  every structured JSON log line as `correlation_id`.

## Endpoints (`/v1` contract)

| Method | Path | Response |
|--------|------|----------|
| POST | `/v1/parse` | `202` → `{ "job_id": str, "document_id": str, "cached": bool }` (multipart field `file`) |
| GET  | `/v1/jobs/{job_id}` | `200` → `{ job_id, document_id, status, filename, sha256, cached, pages_total, pages_done, page_progress[], engine_breakdown, error }` (`404` if unknown) |
| GET  | `/v1/jobs/{job_id}/result` | `200` → canonical `Document` JSON (`404` until `status == succeeded`) |
| POST | `/v1/embed` | `200` → `{ "model": str, "dim": 384, "embeddings": [[...384 floats...], ...] }` (request `{ "texts": [...] }`) |
| GET  | `/healthz` | `200` liveness |
| GET  | `/readyz` | `200` readiness + `optional_ml_libs` importability map |

`status` ∈ `queued | running | succeeded | failed`.

## Run locally (Windows)

```powershell
cd docmind\doc-service-py
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python -m pytest -q
.venv\Scripts\python -c "import app.main; print('import ok')"
.venv\Scripts\python -m uvicorn app.main:app --port 8091
```

Then:

```powershell
curl http://localhost:8091/healthz
curl http://localhost:8091/readyz
curl -F "file=@somefile.pdf" http://localhost:8091/v1/parse
curl http://localhost:8091/v1/jobs/<job_id>
curl http://localhost:8091/v1/jobs/<job_id>/result
```

## Optional real engines

```powershell
.venv\Scripts\python -m pip install -r requirements-ml.txt   # CPU, optional
```

Engines detect their backend at parse time and transparently fall back to stub
output when a library or model is unavailable. **MinerU is intentionally
deferred** (license to be verified) and is not a valid `parse_engine`.

## Embeddings (`POST /v1/embed`)

The Java service calls `POST /v1/embed` to turn chunk/query text into vectors.

- Contract: request `{ "texts": [...] }` → `{ "model": <id|"stub">, "dim": 384,
  "embeddings": [[...384 floats...], ...] }`. One vector per input, order
  preserved; an empty `texts` list returns `200` with `embeddings: []`.
- Real provider: when `requirements-ml.txt` is installed, the service uses
  `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` (384-dim, CPU,
  Vietnamese-friendly), **lazy-loaded on the FIRST `/v1/embed` call**. That
  first call is slow (model download + load — can be tens of seconds offline the
  first time); subsequent calls are fast. Vectors are L2-normalized
  (cosine == dot).
- Stub fallback: without `requirements-ml.txt` (the default Docker image), the
  endpoint serves a **deterministic 384-dim STUB** and logs a WARNING. The
  service always answers `/v1/embed`.
- Batch is capped at 256 (cap-and-log) and individual texts over 20 000 chars
  are truncated (logged). Every line carries `stage="embed-serve"` + the
  correlation chain.

Optional model pre-download (avoids first-call latency in production):

```powershell
.venv\Scripts\python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2')"
```

## Configuration (env vars)

| Var | Default | Meaning |
|-----|---------|---------|
| `DOCMIND_LOG_LEVEL` | `INFO` | JSON log verbosity |
| `DOCMIND_PERSIST_ARTIFACTS` | `false` | Write per-page debug placeholders under `artifacts/<job_id>/` |
| `PORT` | `8091` | Bind port |

## Pipeline

```
upload → validate (size + sha256) → detect-mime (magic bytes, not extension)
       → classify (per-page PageKind) → route + parse (engine per page)
       → normalize (global order + engine_breakdown + schema validation)
       → store result (+ sha256 result cache)
```

Every stage logs one JSON line with `stage` + the correlation chain
(`correlation_id`, `document_id`, `job_id`, and `page`/`block_id`/`engine`
where applicable).
