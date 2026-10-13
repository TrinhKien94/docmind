# Real sentence-transformers embedding — final verification

**Verdict: PASS. The REAL multilingual model was exercised live (not just the stub).**

Scope of this pass: verify only, no new features. No fix was needed — every check passed and
no code breakage was found (one transient 404 was a stale leftover server process from a prior
session, not a defect; see note in §1).

Environment: Windows, Python 3.14.7, OpenJDK 21.0.9, FastAPI 0.143.0 / Starlette 1.7.0.
`doc-service-py/.venv` is present and already has `sentence-transformers` installed
(`importlib.util.find_spec('sentence_transformers')` → True), so the real provider is selectable here.

---

## 1. Python (`d:\Project\digital-spirituality\docmind\doc-service-py`)

Commands run from the service root with `.venv\Scripts\python`:

- `python -c "import app.main"` → **OK** (prints "import app.main OK", exits 0).
- Lazy-import discipline: `import app.embeddings` then checked `sys.modules` →
  `'sentence_transformers' in sys.modules == False`. **Heavy ML lib is NOT pulled in at import.**
- `python -m pytest -q` → **24 passed, 0 failed** (1 StarletteDeprecationWarning only).
  - `tests/test_embed.py` = **4 passed**, including the model-guarded semantic test
    (`pytest.importorskip("sentence_transformers")`) — it **ran (not skipped)** because the model
    is installed in this `.venv`.

Live endpoint (started `uvicorn app.main:app --port 8091`, then POST `/v1/embed`):

- First attempt returned 404. **Root cause: stale uvicorn instances from an earlier session were
  still bound to 8091 serving older code** (openapi lacked `/v1/embed`). Killed PIDs 3696/1216/10468,
  restarted clean. This was an environment artifact, **not a code bug** — `app/api.py` defines
  `@router.post("/v1/embed")` correctly and the TestClient-based pytest exercised it fine.
- After clean restart, live `openapi.json` paths include `/v1/embed` alongside the parse/health routes.
- `POST /v1/embed` with body
  `{"texts":["đạo hàm của hàm số","công thức tính đạo hàm","giá cà phê hôm nay"]}`:
  - status **200**
  - `model` = **`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`** (the REAL model, not `"stub"`)
  - `dim` = **384**; returned **3** embeddings, each length **384**; vectors L2-normalized (‖v0‖²≈1.0)
  - Cosine similarity (dot product of normalized vectors):
    - cos(0,1) = **0.7427** (two calculus phrases)
    - cos(0,2) = **0.1882**, cos(1,2) = **0.0751** (vs. the coffee-price phrase)
    - **Semantic ordering holds**: cos(0,1) > cos(0,2) and > cos(1,2). The calculus phrases cluster;
      the unrelated coffee-price phrase is far. This proves genuine semantic embedding, not hashing.

Server was stopped and temp logs cleaned afterward.

## 2. Java (`d:\Project\digital-spirituality\docmind\api-java`)

- `mvnw.cmd test` → **BUILD SUCCESS**, **Tests run: 22, Failures: 0, Errors: 0, Skipped: 0.**
  (18 pre-existing + the new `RemoteEmbeddingClientTest` = 4.)
  - Per-class: ChatControllerTest 3, DocumentControllerTest 2, DocmindApiApplicationTests 1,
    ChatServiceTest 1, ChunkBuilderTest 3, RemoteEmbeddingClientTest 4, FileValidationTest 5,
    ReciprocalRankFusionTest 3.
  - The WARNING `embed-serve FAILED ... -> fallback stub` seen in the log is the **expected**
    RemoteEmbeddingClientTest failure-path assertion (mocked 500 → per-call stub fallback), not a
    real error. `DocmindApiApplicationTests` confirms the injected `EmbeddingClient` is the
    `@Primary` `RemoteEmbeddingClient` with `dim()==384` and the context loads with no live Python
    service.

## 3. Full stack (Docker compose + Postgres pgvector)

**NOT feasible in this environment — Docker daemon is not running.**

- `docker --version` → 29.1.3 and `docker compose version` → v2.40.3 (CLIs installed), **but**
  `docker info` / `docker compose ps` fail with
  `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`
  (Docker Desktop engine is stopped).
- Postgres is **not** listening on host 5433 (no out-of-compose instance either).
- Therefore `docker compose build/up`, re-ingest, and the pgvector row/dimension query could not be
  run. Starting Docker Desktop is a heavyweight, out-of-scope action for a verify pass, so it was not
  attempted.
- Note (by design, per plan): even if run, the compose `doc-service-py` image installs only
  `requirements.txt` → it would serve the **STUB** 384-dim provider in-container; the REAL model is a
  local `.venv` concern and was already exercised in §1. The E2E DB check would only have confirmed
  non-null 384-dim `chunks.embedding` rows via the stub path, which is independently covered by the
  Java `dim()==384` + per-call fallback tests and the Python stub tests.

---

## Summary

- **What ran and passed:** Python import (lazy, no ML at load) ✓; Python `pytest` 24/24 ✓
  (embed suite 4/4, semantic test ran); live `/v1/embed` 200 with correct model/dim/order ✓;
  Java `mvnw test` 22/22 BUILD SUCCESS ✓.
- **Real vs stub:** the **REAL** `paraphrase-multilingual-MiniLM-L12-v2` model was exercised live
  (384-dim, L2-normalized, correct Vietnamese semantic ordering) because the service `.venv` already
  has `sentence-transformers`. The stub fallback is also verified green on both sides (Python
  `_StubProvider` tests; Java per-call fallback test).
- **Residual risk (low):**
  1. **Docker E2E not run** (daemon down) — the pgvector row/dimension write was not confirmed
     end-to-end here; it is covered indirectly by unit/integration tests and the live embed response
     shape. Re-run §3 once Docker Desktop is up to close this gap.
  2. **Uncaught real-model *load* failure in the embed route** (noted in review Issue 3): if the real
     provider is selected but the model fails to load on the first call (offline/disk), the route
     returns 500 instead of degrading to the stub. Real-path only; the import-absent path already
     degrades correctly. Not exercised here (model loaded fine). Consider wrapping the route in a
     try/except → stub to fully harden, outside this verify pass.
  3. **Bundled unrelated edits** (Flyway module, multipart request factory, FK-flush ordering,
     PromptBuilder hardening) remain in the same working tree; they are separate prior bug-fixes, not
     part of this task, and did not affect any check above.
