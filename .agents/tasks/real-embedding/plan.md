# Implementation Plan — Real sentence-transformers embedding (DocMind)

## Goal & scope (what this plan does and does NOT touch)

Replace the FAKE embedding (`StubEmbeddingClient` hashing text into a deterministic
`float[384]`) with a REAL multilingual sentence-transformers embedding, served from the
Python service and consumed by the Java service over REST, with the stub kept as an
automatic fallback and a clean interface so switching to Gemini later is a config change.

- Model: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` (384-dim, CPU, good Vietnamese support).
- Dimension stays **384** everywhere (Flyway `vector(384)`, `docmind.embedding.dim=384`, `EmbeddingProperties.dim=384`). **DO NOT change the dimension.**
- New Python endpoint: `POST /v1/embed`.
- New Java client: `RemoteEmbeddingClient` (@Primary) calling `/v1/embed` via the existing `docServiceRestClient`; `StubEmbeddingClient` demoted to fallback.

Design decisions made here (and why):
- **Java deserializes `embeddings` as `List<List<Float>>`** then maps to `float[]`, mirroring the existing Jackson record style (`com.fasterxml.jackson.annotation.@JsonProperty` + `@JsonIgnoreProperties(ignoreUnknown=true)`) used by `ParseSubmitResponse`. Rationale: RestClient `.body(Class)` cannot infer generics cleanly from `List<float[]>`; a dedicated response record with `@JsonProperty("embeddings") List<List<Float>>` is the project-consistent, type-safe choice.
- **Fallback granularity on the Java side is per-call**: when the remote call fails (or `remote.enabled=false`), `RemoteEmbeddingClient` falls back to the injected stub for THAT call so ingest never hard-fails. Rationale: matches `DocumentService.persistResult` which calls `embeddingClient.embed(text)` per chunk in a loop — a transient parser hiccup must not abort the whole ingest transaction.
- **Primary-bean wiring reuses the existing mechanism**: `StubEmbeddingClient` is already `@ConditionalOnMissingBean(name = "primaryEmbeddingClient")`. `RemoteEmbeddingClient` is registered with bean name `primaryEmbeddingClient` and `@Primary`, so when it exists the stub is NOT the primary injection target but is still constructed (so `RemoteEmbeddingClient` can hold it as a delegate). Rationale: zero change to the stub's existing conditional; smallest safe change.
- **Docker image stays stub-only.** The Python `Dockerfile` installs only `requirements.txt`; `sentence-transformers` goes to `requirements-ml.txt` (NOT base), so the compose image keeps booting with the stub provider. Exercising the real model is a local `.venv` concern. Rationale: task says add ML dep to `requirements-ml.txt` only, and the engines' discipline is "boots with no models".

Out of scope (DO NOT modify): canonical schema, vector dimension (384), `app/router.py`, `app/jobs.py`, chat/retrieval logic, the Java chat path, and anything outside `d:\Project\digital-spirituality\docmind`.

Absolute roots:
- Python service: `d:\Project\digital-spirituality\docmind\doc-service-py`
- Java service: `d:\Project\digital-spirituality\docmind\api-java`

---

## Part A — Python (doc-service-py)

- [ ] 1. Add `sentence-transformers` to the OPTIONAL ML requirements (NOT base).
      In `d:\Project\digital-spirituality\docmind\doc-service-py\requirements-ml.txt` the line
      `sentence-transformers>=3.0.0` already exists under an "Embeddings / rerank experiments"
      comment — update that comment to state it is now the production parser-side embedding
      provider for `/v1/embed` (CPU, lazy-loaded, stub fallback). Do NOT add it to
      `requirements.txt`. Confirm `requirements.txt` is unchanged.
      Files: `d:\Project\digital-spirituality\docmind\doc-service-py\requirements-ml.txt`
      Verify: `python -c "import app.main"` from the service root still succeeds with ONLY
      base requirements installed (no new import at module load).

- [ ] 2. Create the embedding provider module `app/embeddings.py` with the lazy-import + graceful-fallback discipline mirrored from `app/engines/stub_engines.py`.
      Define a small provider protocol/base with `name: str`, `dim: int` (always 384), and
      `embed(texts: list[str]) -> list[list[float]]` (order preserved, returns normalized
      384-length vectors). Implement two providers:
      - `_RealSentenceTransformerProvider`: holds the model name
        `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`; lazy-loads the model as a
        **module-level singleton on first `embed()` call, NOT at import time** (guard with a
        module global + a function `_load_model()` that tries `from sentence_transformers import
        SentenceTransformer`, loads on CPU, encodes with `normalize_embeddings=True` so
        cosine==dot). Log model-load timing (`stage="embed-serve"`, `model_load_ms=...`) on first
        load only. On any import/load failure, raise an internal signal caught by the factory
        (do NOT let it propagate to the request).
      - `_StubProvider`: deterministic 384-dim stub replicating the Java stub's algorithm
        (lowercase, split on `\W+`, per-token `hash` -> index `floorMod(h, 384)`, sign from high
        bit, empty-text seed fallback, then L2-normalize) so Python stub vectors are deterministic
        and self-consistent. `name="stub"`, `dim=384`.
      Add module constants `EMBEDDING_DIM = 384` and `MAX_BATCH = 256`.
      Add `get_embedding_provider()`: process-wide cached (`functools.lru_cache(maxsize=1)` or a
      module global) that returns the real provider if `sentence_transformers` is importable, else
      the stub; log ONCE which provider is active (`stage="embed-serve"`, `provider=<name>`,
      `model=<name-or-"stub">`) and a `WARNING` when degrading to stub.
      Use `app.config.get_settings().embedding_model` only as a reference default; the hard model
      id for the real provider is the multilingual MiniLM above (keep `dim=384`).
      Files: `d:\Project\digital-spirituality\docmind\doc-service-py\app\embeddings.py`
      Verify: `python -c "import app.embeddings"` succeeds without importing
      `sentence_transformers` (base-only venv); `python -c "from app.embeddings import get_embedding_provider as g; p=g(); print(p.name, p.dim)"` prints `stub 384` in a base-only venv.

- [ ] 3. Add the `POST /v1/embed` route in `app/api.py` following the existing handler shape (read optional `X-Correlation-Id` via `Header`, `bind_context` / `clear_context`, structured logging via `app.logging_config.get_logger`).
      Request body `{"texts": [...]}` (define a tiny Pydantic request model, e.g. `EmbedRequest(texts: list[str])`, inline in `app/api.py` or in `app/embeddings.py` — keep it near the route for locality, consistent with how routes are declared in `app/api.py`). Behavior:
      - Empty `texts` -> `200` with `{"model": <name>, "dim": 384, "embeddings": []}` (no model load).
      - Cap batch at `MAX_BATCH` (256): if more, process only the first 256 and log a WARNING with the original count (or reject >256 with 200 on the capped slice — choose cap-and-log to match the "cap batch size ~256" instruction).
      - Truncate overly long individual texts (e.g. > a defined `MAX_CHARS`, pick ~20000 chars) before embedding and log a WARNING naming how many were truncated.
      - Call `get_embedding_provider().embed(texts)`; preserve input order.
      - Response `{"model": provider.name_or_model_id, "dim": 384, "embeddings": [[...384...], ...]}`.
      - Log `stage="embed-serve"` with `provider`, `batch_size`, and (first load only) `model_load_ms`.
      Files: `d:\Project\digital-spirituality\docmind\doc-service-py\app\api.py`
      Verify: part of step 6's pytest run; also manual curl in the verification section.

- [ ] 4. Report `sentence_transformers` importability stays visible on `/readyz` (already in `_OPTIONAL_ML_LIBS`) and add a startup/first-use note.
      No structural change required to `/readyz` (it already lists `sentence_transformers`). Confirm the embed route's active-provider log line is emitted on first call. (No code change if step 2's one-time log already covers it; otherwise ensure `get_embedding_provider()` logs once.)
      Files: none expected (verification-only); if a log is missing, adjust `app/embeddings.py` from step 2.
      Verify: `GET /readyz` still returns `optional_ml_libs` containing `sentence_transformers` (covered by existing `test_health.py`).

- [ ] 5. README note about first-call latency + optional pre-download.
      In `d:\Project\digital-spirituality\docmind\doc-service-py\README.md`, under "Optional real engines" (or a new "Embeddings" subsection), document: `POST /v1/embed` contract (`{"texts":[...]}` -> `{"model","dim":384,"embeddings":[[...]]}`); that the real provider lazy-loads `paraphrase-multilingual-MiniLM-L12-v2` on first call (first `/v1/embed` call is slow — model download + load — subsequent calls fast); that without `requirements-ml.txt` installed the service serves a deterministic 384-dim STUB and logs a WARNING; optional pre-download command (`python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2')"`).
      Files: `d:\Project\digital-spirituality\docmind\doc-service-py\README.md`
      Verify: manual read; no test.

- [ ] 6. Add pytest tests for the embed endpoint + provider, keeping the existing ~20 tests green.
      New file `tests/test_embed.py` using the existing `client` fixture from `tests/conftest.py`:
      - batch of 3 texts -> `200`, `dim==384`, 3 embeddings each length 384, order preserved.
      - empty list -> `200`, `embeddings == []`, `dim == 384`.
      - deterministic stub fallback: calling the stub provider directly (import `_StubProvider` or force the stub path) returns identical vectors for identical input and 384 length (works with base-only install).
      - model-guarded semantic sanity check: skip via `pytest.importorskip("sentence_transformers")`; when present, embed `["đạo hàm của hàm số", "công thức tính đạo hàm", "giá cà phê hôm nay"]` and assert cosine(0,1) > cosine(0,2) and > cosine(1,2) (the two calculus phrases are closer to each other than to the coffee-price phrase).
      Files: `d:\Project\digital-spirituality\docmind\doc-service-py\tests\test_embed.py`
      Verify: `python -m pytest -q` from `d:\Project\digital-spirituality\docmind\doc-service-py` passes; previously ~20 tests + the new ones all green (the model-guarded test skips when `sentence_transformers` is absent).

---

## Part B — Java (api-java)

- [ ] 7. Add the `/v1/embed` response model record `EmbedResponse` mirroring the existing Jackson record style.
      New record in the client model package next to `ParseSubmitResponse`: `@JsonIgnoreProperties(ignoreUnknown = true)` with `@JsonProperty("model") String model`, `@JsonProperty("dim") int dim`, `@JsonProperty("embeddings") List<List<Float>> embeddings`. Also add an `EmbedRequest` record (or reuse a simple `Map.of("texts", texts)` body — prefer a small `record EmbedRequest(List<String> texts)` with `@JsonProperty("texts")` for symmetry).
      Files:
      `d:\Project\digital-spirituality\docmind\api-java\src\main\java\com\example\docmind\client\model\EmbedResponse.java`,
      `d:\Project\digital-spirituality\docmind\api-java\src\main\java\com\example\docmind\client\model\EmbedRequest.java`
      Verify: compiles in step 11's `mvnw.cmd -q test`.

- [ ] 8. Add the remote-embedding config keys to `EmbeddingProperties`.
      Extend `com.example.docmind.config.EmbeddingProperties` (prefix `docmind.embedding`) with a nested `remote` group: `boolean enabled = true` and `String endpointPath = "/v1/embed"`. Keep `dim = 384`. Use Lombok `@Getter/@Setter` as the class already does; add a nested static `Remote` class (`@Getter/@Setter`) with field `private Remote remote = new Remote();` so keys bind as `docmind.embedding.remote.enabled` / `docmind.embedding.remote.endpoint-path`.
      Files: `d:\Project\digital-spirituality\docmind\api-java\src\main\java\com\example\docmind\config\EmbeddingProperties.java`
      Verify: compiles; bound in step 10's properties, asserted by step 11 tests.

- [ ] 9. Implement `RemoteEmbeddingClient` as the @Primary embedding client with per-call fallback to the stub.
      New class `com.example.docmind.service.embedding.RemoteEmbeddingClient implements EmbeddingClient`,
      annotated `@Component` + `@Primary`, with bean name `primaryEmbeddingClient` (so the existing
      `@ConditionalOnMissingBean(name = "primaryEmbeddingClient")` on `StubEmbeddingClient` yields
      primary selection to this bean while still constructing the stub). Constructor injects:
      `RestClient docServiceRestClient` (the SAME bean the parser uses — same base URL), the
      `StubEmbeddingClient` (as the fallback delegate), and `EmbeddingProperties`.
      - `dim()` returns `props.getDim()` (384), consistent with `EmbeddingProperties`.
      - `embed(String text)` delegates to `embedBatch(List.of(text)).get(0)`.
      - Override `embedBatch(List<String> texts)`:
        - If `!props.getRemote().isEnabled()` -> return `stub.embedBatch(texts)` (log DEBUG that remote is disabled).
        - Else POST `props.getRemote().getEndpointPath()` on `docServiceRestClient` with body
          `new EmbedRequest(texts)`, `contentType(MediaType.APPLICATION_JSON)`, header
          `CorrelationIds.HEADER = CorrelationIds.currentCorrelationId()` (propagate the chain,
          like `RestDocServiceClient`), `.retrieve().body(EmbedResponse.class)`.
        - Validate response non-null and `dim == props.getDim()`; map each `List<Float>` to a
          `float[]` (size must be 384 — if a vector length mismatches, treat as failure).
        - On `RestClientResponseException`: log a WARNING with `status` + `responseBodyAsString`
          (reuse the exact pattern from `RestDocServiceClient.submitParse`), then fall back to
          `stub.embedBatch(texts)` for this call. On `RestClientException` (no response) or any
          mapping/validation error: log WARNING with the message and fall back to the stub.
          Ingest must never hard-fail from embedding.
      - Use `CorrelationIds.setStage("embed-serve")` / `clearStage()` around the call for log symmetry.
      Files: `d:\Project\digital-spirituality\docmind\api-java\src\main\java\com\example\docmind\service\embedding\RemoteEmbeddingClient.java`
      Verify: compiles; behavior covered by step 12 tests; `DocumentService.persistResult` is
      UNCHANGED (it keeps calling `embeddingClient.embed(c.text())` and `embeddingClient.dim()` —
      now resolving to the @Primary remote client).

- [ ] 10. Add the new embedding keys to `application.properties` in the existing `${ENV:default}` style.
      In `d:\Project\digital-spirituality\docmind\api-java\src\main\resources\application.properties`,
      under the existing "Embedding" section, add:
      `docmind.embedding.remote.enabled=${DOCMIND_EMBEDDING_REMOTE_ENABLED:true}` and
      `docmind.embedding.remote.endpoint-path=${DOCMIND_EMBEDDING_ENDPOINT_PATH:/v1/embed}`.
      Keep `docmind.embedding.dim=${DOCMIND_EMBEDDING_DIM:384}` unchanged. (No docker-compose change
      required; defaults make remote on and point at the existing `docmind.doc-service.base-url`.)
      Files: `d:\Project\digital-spirituality\docmind\api-java\src\main\resources\application.properties`
      Verify: context loads in `DocmindApiApplicationTests` under the `test` profile (step 11).

- [ ] 11. Keep the existing 18 Java tests green with the new @Primary bean present.
      Confirm `DocmindApiApplicationTests` (context load) still passes with `RemoteEmbeddingClient`
      now @Primary (it needs the `docServiceRestClient` + `StubEmbeddingClient` + `EmbeddingProperties`
      beans, all already present). No test should start a live Python service. If the context-load
      test fails due to the new bean, resolve by ensuring `RemoteEmbeddingClient` only depends on
      beans that exist in the `test` profile (RestClient bean is created from `DocServiceProperties`
      and does not open a connection at startup).
      Files: none expected (verification step); if needed, no production-contract change — only
      wiring adjustments in step 9.
      Verify: `mvnw.cmd -q test` from `d:\Project\digital-spirituality\docmind\api-java` — all
      pre-existing tests pass.

- [ ] 12. Add a unit test proving remote-client fallback-to-stub WITHOUT a live Python service (mock the RestClient).
      New test `com.example.docmind.service.embedding.RemoteEmbeddingClientTest` (JUnit 5 + Mockito +
      AssertJ, matching `ChatServiceTest` style). Build the client with a `mock(RestClient.class)`
      (stub the fluent chain enough to drive the two paths) OR, simpler and more robust against the
      fluent builder, inject a mock `EmbeddingClient` stub delegate and drive the remote path via a
      spy/mocked RestClient:
      - Happy path: mock the fluent `post()...retrieve().body(EmbedResponse.class)` chain to return
        an `EmbedResponse("paraphrase-multilingual-MiniLM-L12-v2", 384, <one 384-length list>)`;
        assert `embed("x")` returns a `float[384]` equal to the mocked vector and that the stub
        delegate was NOT called.
      - Failure path: make the mocked chain throw a `RestClientResponseException` (e.g. 500 with a
        body); assert `embed("x")` returns the STUB vector (equal to `new StubEmbeddingClient(props).embed("x")`)
        and length 384, proving per-call fallback and no exception propagation.
      - Disabled path: `props.getRemote().setEnabled(false)` -> assert the RestClient is never
        invoked and the stub result is returned.
      - `dim()` returns 384.
      Keep the mock chain assertions behavior-focused (observable return value + fallback), not
      implementation-coupled beyond what the fluent API requires.
      Files: `d:\Project\digital-spirituality\docmind\api-java\src\test\java\com\example\docmind\service\embedding\RemoteEmbeddingClientTest.java`
      Verify: `mvnw.cmd -q test` passes including the new test; total count = 18 existing + new.

---

## Verification plan (record results when executing)

Python (`d:\Project\digital-spirituality\docmind\doc-service-py`):
1. If feasible, create/activate `.venv` and install base + ML:
   `python -m venv .venv`; `.venv\Scripts\python -m pip install -r requirements.txt`;
   then (optional, large download) `.venv\Scripts\python -m pip install -r requirements-ml.txt`.
2. `.venv\Scripts\python -c "import app.main"` -> prints nothing/exits 0 (no heavy import at load).
3. `.venv\Scripts\python -m pytest -q` -> report pass count; previously ~20 + the new `test_embed.py`
   tests; the model-guarded semantic test SKIPS if `sentence_transformers` is not installed.
4. `.venv\Scripts\python -m uvicorn app.main:app --port 8091` then:
   `curl -s -X POST http://localhost:8091/v1/embed -H "Content-Type: application/json" -d "{\"texts\":[\"đạo hàm của hàm số\",\"công thức tính đạo hàm\",\"giá cà phê hôm nay\"]}"`
   -> JSON with `dim:384` and 3 embeddings of length 384. If the real model loaded, verify
   cosine(vec0,vec1) > cosine(vec0,vec2) and > cosine(vec1,vec2).

Java (`d:\Project\digital-spirituality\docmind\api-java`):
5. `mvnw.cmd -q test` -> report pass count (18 existing + new `RemoteEmbeddingClientTest`); all green.

End-to-end (optional, if Docker feasible):
6. `docker compose -f d:\Project\digital-spirituality\docmind\docker-compose.yml build` and `up`.
   NOTE: the compose `doc-service-py` image installs ONLY `requirements.txt`, so in Docker the
   embed endpoint serves the STUB provider (real model NOT exercised in the container). Re-ingest a
   small docx/xlsx and confirm `chunks.embedding` is non-null with dimension 384 (query Postgres on
   host 5433). To exercise the REAL model, run doc-service-py locally in a `.venv` with
   `requirements-ml.txt` installed (step 1 above) rather than via the stub-only image.

If the ML install / model download is NOT feasible in this environment:
- Keep the stub fallback working on BOTH sides (Python `_StubProvider`, Java per-call stub fallback).
- The implementer MUST report clearly, in the final summary, that the real model was NOT exercised,
  state WHY (e.g. offline environment / disk / wheel availability), and confirm that: (a) base-only
  `python -m pytest -q` is green with the model-guarded test skipped, (b) `mvnw.cmd -q test` is green,
  and (c) ingest still produces non-null 384-dim embeddings via the stub path.
