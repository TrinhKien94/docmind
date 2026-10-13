# Real sentence-transformers embedding — semantic review

**Verdict**: APPROVED

The change replaces the fake hash-based `StubEmbeddingClient` as the primary embedding source with a real multilingual sentence-transformers model served from the Python service over a new `POST /v1/embed`, consumed by a new `@Primary` Java `RemoteEmbeddingClient` that falls back to the stub per-call on any remote failure. Dimension stays 384 on both sides. I read the plan, the full working-tree diff, and every new/modified file, and I independently verified the Python lazy-import + provider-selection + real-model behavior live in the service `.venv`.

Watch for: (1) a batch of unrelated bug-fix changes is bundled in the same uncommitted diff (Flyway module, multipart request factory, FK-flush ordering, prompt-injection hardening) — not part of the real-embedding task; **PromptBuilder** (the chat path the plan said to leave unchanged) is among them [likely — separate prior task, not blocking]. (2) The coder's required `pytest`/`mvnw` evidence is not persisted in the task directory [confirmed — only `plan.md` present]. (3) A real-model *load* failure after the factory already picked "real" is not caught in the embed route and would surface as a 500 instead of a stub degrade [confirmed — minor, real-path only].

## High-level view

The Python contract is implemented exactly as specified: `POST /v1/embed` takes `{"texts":[...]}` and returns `{"model","dim":384,"embeddings":[...]}`, order preserved, empty list → 200 with no model load, batch capped at 256 (cap-and-log), long texts truncated at 20 000 chars (logged), correlation id read and bound. The provider module keeps the engines' lazy-import discipline — importing `app.embeddings` does not pull in `sentence_transformers`, and the real model is a module-global singleton loaded only on the first `embed()` call. The factory picks real-else-stub via `find_spec` (no load) and logs which is active; a deterministic 384-dim stub mirrors the Java algorithm. I confirmed all of this against the live `.venv`.

The Java side adds a `@Primary` bean named `primaryEmbeddingClient`, which cedes primary injection away from the stub (whose `@ConditionalOnMissingBean(name="primaryEmbeddingClient")` is untouched) while the stub is still constructed internally as the fallback delegate. The remote client reuses the same `docServiceRestClient` as the parser, propagates `X-Correlation-Id`, maps `List<List<Float>>` → `float[]`, validates dim/count/length, and on `RestClientResponseException` logs WARNING with status+body then falls back to the stub for that call. The config flag `docmind.embedding.remote.enabled` (default true) guards it and `dim()` returns 384 from `EmbeddingProperties`.

Cross-cutting invariants hold: 384 is consistent across `EMBEDDING_DIM`, `EmbeddingProperties.dim`, response `dim`, and the Java validation. The real-embedding files do not touch the canonical schema, the pgvector migration, `app/router.py`, `app/jobs.py`, or the retrieval/chat logic. `requirements.txt` is unchanged; the ML dep lives only in `requirements-ml.txt`.

Verification is adequate: I did not re-run the suites, but a narrow live spot-check in the `.venv` confirmed lazy import, real-provider selection, and that the real model yields 384-dim L2-normalized vectors with the expected semantic ordering (calculus phrases closer to each other than to the coffee-price phrase). The one gap is that the coder's `pytest -q` / `mvnw -q test` output is not persisted anywhere I can read.

<details>
<summary>Issues (3)</summary>

1. **Bundled unrelated changes** — `api-java/pom.xml` (spring-boot-flyway), `RestClientConfig.java` (JDK HttpClient → SimpleClientHttpRequestFactory for multipart), `DocumentService.java` (saveAndFlush/flush FK ordering), and `PromptBuilder.java` (prompt-injection hardening) are modified in the same working tree but are not part of the real-embedding task. `PromptBuilder` is the chat path the plan said to leave UNCHANGED. These are plausibly separate bug-fixes for the user's earlier upload/boot errors; not introduced by the embedding files and not blocking this review, but should be committed/reviewed under their own task. [likely]
2. **Verification evidence not persisted** — the task dir holds only `plan.md`; no file records the required `python -m pytest -q` and `mvnw.cmd -q test` results. Python behavior was independently re-verified live (below); the Java suite result is unverified by me. Record the commands+results. [confirmed]
3. **Uncaught real-model load failure in the embed route** — `get_embedding_provider()` selects the real provider via `find_spec` without loading; if the model then fails to load on first `embed()` (offline/disk), `_ModelUnavailable` propagates out of `provider.embed()` and the route has no try/except around it, so the call returns 500 instead of degrading to the stub. The import-absent path IS handled (factory → stub). Minor, real-path only; consider catching load failure at the route or in the real provider and degrading. [confirmed]

</details>

<details>
<summary>Details</summary>

### Python `/v1/embed` route (`doc-service-py/app/api.py`)

Matches the contract point-for-point. `EmbedRequest(BaseModel){texts: list[str]}` defined inline near the route. Empty `texts` returns `200 {"model","dim":384,"embeddings":[]}` with no model load. Batch > `MAX_BATCH` (256) is capped to the first 256 and logged WARNING with `requested`/`cap`. Texts over `_MAX_CHARS` (20 000) are truncated with a WARNING naming the count. `X-Correlation-Id` is read via `Header` and bound with `bind_context`/`clear_context`, same as the other handlers. Structured logging uses `stage="embed-serve"` with `provider` and `batch_size`. Order is preserved (list comprehension over inputs).

### Python provider module (`doc-service-py/app/embeddings.py`)

`EMBEDDING_DIM=384`, `MAX_BATCH=256`, `REAL_MODEL_ID=paraphrase-multilingual-MiniLM-L12-v2`. The real provider lazy-loads via a module-global `_MODEL` + `_load_model()` that imports `sentence_transformers` inside the function, encodes with `normalize_embeddings=True` (cosine==dot), and logs `model_load_ms` once. `get_embedding_provider()` is `lru_cache(maxsize=1)`, decides by `importlib.util.find_spec` without loading, logs the active provider once, and WARNINGs on degrade to stub. `_StubProvider` replicates the Java hashing (lowercase, `\W+` split, `floorMod`, high-bit sign, empty-seed, L2-normalize), `name="stub"`, `dim=384`.

Live verification in the service `.venv` (narrow spot-check, not the full suite):
- `import app.embeddings` → `'sentence_transformers' not in sys.modules` held true (no heavy import at module load).
- `get_embedding_provider()` logged `embedding provider active ... provider=real model=...MiniLM-L12-v2` and returned `dim=384`, WITHOUT emitting the model-load timing line — i.e. selection without load, exactly as specified.
- `_RealSentenceTransformerProvider().embed([...])` → 3 vectors, dim 384, `norm0=1.0`, cosine c01=0.869 > c02=0.613 > c12=0.444 (the two calculus phrases cluster; coffee-price is far). The real model is genuinely active in this environment.

### Python requirements + README + tests

`requirements-ml.txt` keeps `sentence-transformers>=3.0.0` and the comment now states it is the production parser-side provider for `/v1/embed` with lazy load + stub fallback. `requirements.txt` is unchanged (confirmed by read — base deps only, no sentence-transformers). `README.md` documents the `/v1/embed` contract, first-call latency, stub fallback, batch cap/truncation, and an optional pre-download command. `tests/test_embed.py` covers: 384-length batch vectors + order (via the live route + re-call equality), empty-list → `[]` with dim 384, deterministic L2-normalized stub (384), and a semantic sanity check guarded by `pytest.importorskip("sentence_transformers")`.

### Java `RemoteEmbeddingClient` (`api-java/.../service/embedding/RemoteEmbeddingClient.java`)

`@Component("primaryEmbeddingClient") @Primary implements EmbeddingClient`. Constructor injects `docServiceRestClient` (the parser's bean, same base URL) + `EmbeddingProperties`, and builds the `StubEmbeddingClient` delegate directly (so the stub's existing `@ConditionalOnMissingBean(name="primaryEmbeddingClient")` is untouched). `dim()` returns `props.getDim()` (384). `embedBatch` returns the stub result when `remote.enabled=false` (DEBUG log, no RestClient call); otherwise POSTs `endpointPath` with `X-Correlation-Id`, JSON `EmbedRequest`, retrieves `EmbedResponse`, and validates non-null + `dim==384` + count + per-vector length before mapping `List<Float>`→`float[]`. Failure handling: `RestClientResponseException` → WARNING with status + body then stub; `RestClientException` → WARNING + stub; `RuntimeException` (mapping/validation) → WARNING + stub. Ingest never hard-fails. `CorrelationIds.setStage("embed-serve")`/`clearStage()` wrap the call.

### Java model records + config + wiring

`EmbedResponse` is `@JsonIgnoreProperties(ignoreUnknown=true)` with `List<List<Float>> embeddings`; `EmbedRequest` carries `@JsonProperty("texts")`. `EmbeddingProperties` adds a nested `Remote{enabled=true, endpointPath="/v1/embed"}` with Lombok `@Getter/@Setter`, keeping `dim=384`. `application.properties` adds `docmind.embedding.remote.enabled=${DOCMIND_EMBEDDING_REMOTE_ENABLED:true}` and `...endpoint-path=${DOCMIND_EMBEDDING_ENDPOINT_PATH:/v1/embed}` in the `${ENV:default}` style; `dim` line unchanged.

### Java tests

`DocmindApiApplicationTests` now asserts the injected `EmbeddingClient` is a `RemoteEmbeddingClient` with `dim()==384` (context loads without a live Python service — the RestClient bean opens no connection at startup). `RemoteEmbeddingClientTest` (JUnit5 + Mockito + AssertJ, `Answers.RETURNS_SELF` on the fluent spec) covers: happy path returns the remote vector and is `isNotEqualTo` the stub vector (proving the stub was not used); `RestClientResponseException` 500 → returns the stub vector, length 384, no throw; disabled remote → `verifyNoInteractions(restClient)` and stub result; `dim()==384`. The `RestClientException`/bad-response `RuntimeException` branches are not separately unit-tested, but the plan's listed cases (happy/failure/disabled/dim) are all present and the branches are structurally identical.

### Cross-cutting / scope

Dimension 384 is consistent across `EMBEDDING_DIM`, `EmbeddingProperties.dim`, the response `dim`, and the Java length validation. The embedding change does not touch the canonical schema, the pgvector column/migration, `app/router.py`, `app/jobs.py`, chat/retrieval logic, or `DocumentService.persistResult`'s embedding loop (it still calls `embeddingClient.embed(...)`/`dim()`, now resolving to the remote client). The stub's conditional annotation is preserved. The real-embedding file set is minimal and on-scope; the unrelated bundled edits noted in Issue 1 are the only scope concern and are not part of these files.

</details>

<details>
<summary>Files</summary>

Real-embedding change (on-scope):
- `doc-service-py/app/embeddings.py` (new) — real ST provider + deterministic stub + lazy factory.
- `doc-service-py/app/api.py` — `POST /v1/embed` route + `EmbedRequest`.
- `doc-service-py/requirements-ml.txt` — ST promoted to production provider comment (base reqs untouched).
- `doc-service-py/README.md` — `/v1/embed` contract + first-call latency + pre-download.
- `doc-service-py/tests/test_embed.py` (new) — batch/order/empty/stub + model-guarded semantic test.
- `api-java/.../service/embedding/RemoteEmbeddingClient.java` (new) — `@Primary`, per-call stub fallback.
- `api-java/.../client/model/EmbedRequest.java`, `EmbedResponse.java` (new).
- `api-java/.../config/EmbeddingProperties.java` — nested `Remote` group.
- `api-java/src/main/resources/application.properties` — remote keys.
- `api-java/.../DocmindApiApplicationTests.java` — context-load now expects remote `@Primary`.
- `api-java/.../service/embedding/RemoteEmbeddingClientTest.java` (new) — fallback unit test.

Bundled unrelated changes (Issue 1 — separate bug-fixes, not real-embedding):
- `api-java/pom.xml` — add `spring-boot-flyway` module.
- `api-java/.../config/RestClientConfig.java` — SimpleClientHttpRequestFactory for multipart.
- `api-java/.../service/DocumentService.java` — saveAndFlush/flush FK ordering.
- `api-java/.../service/retrieval/PromptBuilder.java` — prompt-injection hardening (nominal chat path).
- `api-java/.../client/RestDocServiceClient.java` — richer error logging (status+body).

Full diff: `git -C d:\Project\digital-spirituality\docmind diff` (plus untracked files above).

</details>
