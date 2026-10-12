# DocMind Scaffold — Implementation Plan

NotebookLLM-style document-RAG MVP skeleton inside the monorepo at `d:\Project\digital-spirituality`. Two services joined by a REST contract: **api-java** (Spring Boot 4 / Java 21 orchestrator + Java-only chat/RAG) and **doc-service-py** (FastAPI document parser). Infra via docker-compose with Postgres+pgvector. Build it to RUN; ML is stubbed, but routing, interfaces, structured logging, job lifecycle, and the Java↔Python HTTP contract are real and wired end to end.

## Design decisions (grounded in the monorepo)
- **Build tool = Maven** for the Java service, matching sibling `horoscope` (uses `mvnw`, `spring-boot-starter-parent`, Lombok with explicit annotation-processor paths, `spring-dotenv`, `${ENV:default}` property style, `.env.example` committed / `.env` gitignored). Reused for DocMind.
- **Spring Boot 4.0.0 (GA 2025-11-20, on Maven Central), Java 21** — mandated by the task. This is a deliberate deviation from horoscope (3.3.11); noted in README and context.json. Boot 4 gives first-class `RestClient` for the Python client.
- **Datastore = PostgreSQL 16 + pgvector** (`pgvector/pgvector:pg16`) with Flyway-managed schema. Deviation from horoscope (Redis, no DB) and from the MySQL steering file — the task explicitly requires Postgres/pgvector. `vector(384)` matches `paraphrase-multilingual-MiniLM-L12-v2`.
- **Ports**: api-java `8090`, doc-service-py `8091`, Postgres `5433:5432` — all chosen to avoid horoscope's `8084` and a local `5432`.
- **ML heavy deps are lazy-imported behind stub engines/clients** so both services boot and tests pass on CPU with no models. Routing, interfaces, logging, job lifecycle, HTTP contract are real.
- **Decomposed into 3 features** (infra/contract → Python service → Java service). FEAT-001 first; FEAT-002 and FEAT-003 both depend on FEAT-001 but are independent of each other (shared only via the FEAT-001 contract). FEAT artifacts live in `.agents/tasks/docmind-scaffold/`.

Verification commands come from the monorepo: Java `mvnw.cmd -DskipTests package` / `mvnw.cmd test`; Python `python -m pytest -q`; compose `docker compose -f docmind/docker-compose.yml config`. Windows shell → use `mvnw.cmd` and `python`.

---

# Implementation Plan

## FEAT-001 — Infra + shared canonical contract + docs (do first)

- [ ] 1. Create `docmind/docker-compose.yml` with three services: `postgres` (`pgvector/pgvector:pg16`, db/user/pass `docmind`, `5433:5432`, data volume, `./db/init`→`/docker-entrypoint-initdb.d`, `pg_isready` healthcheck), `doc-service-py` (build `./doc-service-py`, `8091:8091`), `api-java` (build `./api-java`, `8090:8090`, `env_file ./.env`, `depends_on` postgres-healthy + doc-service-py, env for `SPRING_DATASOURCE_URL`, DB creds, `DOC_SERVICE_BASE_URL=http://doc-service-py:8091`, `GEMINI_API_KEYS`).
      Files: `docmind/docker-compose.yml`
      Verify: `docker compose -f docmind/docker-compose.yml config` exits 0 and shows all three services + port mappings. If Docker CLI is absent, assert the YAML parses and the required keys/ports are present by reading the file.

- [ ] 2. Create `docmind/db/init/001_init.sql` containing `CREATE EXTENSION IF NOT EXISTS vector;` plus a comment that app tables are owned by Flyway (api-java). Create `docmind/db/README.md` explaining init-SQL vs Flyway ownership.
      Files: `docmind/db/init/001_init.sql`, `docmind/db/README.md`
      Verify: file contains the `CREATE EXTENSION` statement.

- [ ] 3. Create `docmind/docs/canonical-schema.md` documenting the data contract EXACTLY: `Document{document_id, source{filename, sha256, mime, pages}, blocks[], stats{engine_breakdown}}`; `Block{block_id, page, order, type(heading|paragraph|table|formula|key_value|checkbox|image|list), text, markdown, html, latex (only when type=formula), bbox[x0,y0,x1,y1], confidence(0..1), heading_path[], parse_engine(paddleocr|docling|pymupdf|office), parse_version}`; chunks inherit `document_id, heading_path, source block_ids, page, bbox`. Include one worked JSON example.
      Files: `docmind/docs/canonical-schema.md`
      Verify: all fields + enum values + the formula→latex rule + chunk inheritance are present.

- [ ] 4. Create `docmind/README.md`: architecture (Java orchestrator vs Python parser; **chat is Java-only, never calls Python**), run-via-docker-compose (ports 8090/8091/5433), REST contract for both services (async ingest job pattern + chat), the ingest and chat flows as ASCII pipelines, the correlation-id/observability model, pointer to `docs/canonical-schema.md`, and explicit notes that **MinerU is intentionally deferred until its license is verified** and **formula handling targets LaTeX output**. Note the Spring Boot 4 / pgvector deviation from horoscope.
      Files: `docmind/README.md`
      Verify: README mentions port 8090, the MinerU-deferred + LaTeX notes, and the Java-only chat path.

- [ ] 5. Create `docmind/.env.example` (committed) with placeholders in horoscope style (`POSTGRES_PASSWORD`, `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `DOC_SERVICE_BASE_URL`, `GEMINI_API_KEYS` empty with a comment that chat uses a stub LLM until set, `DOCMIND_LOG_LEVEL`, `DOCMIND_PERSIST_ARTIFACTS`). Create/extend `docmind/.gitignore` to ignore `.env`, Java `target/`, Python `__pycache__/ .venv/ *.pyc`, and `artifacts/`.
      Files: `docmind/.env.example`, `docmind/.gitignore`
      Verify: `.env.example` present; `.gitignore` ignores `.env` and `target/`.

## FEAT-002 — doc-service-py (FastAPI parser; real lifecycle, stubbed ML)

- [ ] 6. Create `docmind/doc-service-py/requirements.txt` (base: fastapi, uvicorn[standard], pydantic v2, python-magic[-bin], python-multipart, structlog/stdlib-JSON) and `requirements-ml.txt` (optional, NOT installed in CI: pymupdf, docling, paddlepaddle-CPU, paddleocr, firecrawl pdf-inspector binding, sentence-transformers), with a comment that engines fall back to stubs when ML imports fail.
      Files: `docmind/doc-service-py/requirements.txt`, `docmind/doc-service-py/requirements-ml.txt`
      Verify: base file installs cleanly in a fresh venv; no ML libs in `requirements.txt`.

- [ ] 7. Create `app/main.py` (FastAPI app factory, router registration, JSON-logging init on startup) and `app/config.py` (pydantic-settings: `DOCMIND_LOG_LEVEL`, `DOCMIND_PERSIST_ARTIFACTS`, host/port 8091, model names).
      Files: `docmind/doc-service-py/app/main.py`, `docmind/doc-service-py/app/config.py`
      Verify: `python -c "import app.main"` succeeds with only base requirements.

- [ ] 8. Create `app/schema.py` — pydantic Block/Document/Chunk/JobStatus matching `docs/canonical-schema.md` exactly (type Enum, `latex` Optional with a validator asserting it is set only for `type==formula`, bbox 4-float tuple, parse_engine Enum, heading_path list[str], stats.engine_breakdown dict[str,int]).
      Files: `docmind/doc-service-py/app/schema.py`
      Verify: schema unit test (step 17) — latex rejected on non-formula block; valid Document round-trips.

- [ ] 9. Create `app/logging_config.py` — one JSON object per log line with `ts, level, stage, document_id, job_id, page, block_id, engine, duration_ms, confidence, msg, correlation_id`; a context/bind helper per stage; read incoming `X-Correlation-Id` and attach to every line of a request.
      Files: `docmind/doc-service-py/app/logging_config.py`
      Verify: during a parse, emitted lines are valid JSON carrying `stage` + correlation fields (asserted in step 17 parse test via log capture or a smoke run).

- [ ] 10. Create `app/validation.py` — `detect_mime(bytes)` via python-magic magic-byte sniffing (NOT extension) with safe fallback, `sha256_hex(bytes)`, size guard; logs `stage=validate` and `stage=detect-mime` (mime + reason).
      Files: `docmind/doc-service-py/app/validation.py`
      Verify: validation unit test — SHA-256 deterministic; a PDF-magic byte sample named `.txt` detected as `application/pdf`.

- [ ] 11. Create `app/engines/base.py` (`EngineAdapter` abstract: `name`, `version`, `supports(page_kind)`, `parse(payload, ctx)->list[Block]`; `PageKind` enum TEXT_SIMPLE/TEXT_COMPLEX/SCANNED/FORMULA/OFFICE/WEB) and `app/engines/stub_engines.py` (PyMuPdfEngine, DoclingEngine, PaddleOcrEngine, OfficeEngine + formula path) — each lazy-imports its real lib inside `parse()` wrapped in try/except ImportError and ALWAYS falls back to deterministic sample canonical Blocks (realistic bbox/confidence/heading_path, correct parse_engine + parse_version). Logs `stage=parse` with engine, duration_ms, mean confidence.
      Files: `docmind/doc-service-py/app/engines/base.py`, `docmind/doc-service-py/app/engines/stub_engines.py`
      Verify: engines import and run with no ML libs installed; produce schema-valid blocks (covered by step 17 parse test).

- [ ] 12. Create `app/router.py` — `ParserRouter.classify(mime, bytes)` → per-page `PageKind`; `classify_pdf(bytes)` lazily uses pdf-inspector when present else a heuristic stub, returning per-page kind + human-readable reason (logs `stage=classify` per page: text vs scanned + reason); office/md/html/xlsx/pptx/docx→OFFICE, formula pages→FORMULA; routes each page to the matching EngineAdapter.
      Files: `docmind/doc-service-py/app/router.py`
      Verify: classification runs without pdf-inspector installed; `stage=classify` lines include a reason (step 17).

- [ ] 13. Create `app/normalizer.py` — assemble engine blocks into a Document: assign global `order`, fill `source{filename,sha256,mime,pages}`, compute `stats.engine_breakdown` (block count per engine), validate against `app/schema.py`. Logs `stage=normalize`.
      Files: `docmind/doc-service-py/app/normalizer.py`
      Verify: normalized Document validates and `engine_breakdown` sums to block count (step 17).

- [ ] 14. Create `app/jobs.py` — in-memory JobStore (create/set_status/set_page_progress/set_result/get; statuses queued/running/succeeded/failed) + hash-based result cache keyed by sha256 (cache hit returns cached Document without re-parsing, logged). TODO comments to move to Postgres/queue.
      Files: `docmind/doc-service-py/app/jobs.py`
      Verify: cache test (step 17) — identical bytes twice → second is `cached:true`, engines not re-run.

- [ ] 15. Create `app/service.py` — `process_parse_job`: validate → detect-mime → classify(per page) → route+parse(per page) → normalize → store; updates per-page progress + job status; honors `DOCMIND_PERSIST_ARTIFACTS` (when true, write placeholder rendered-image / raw-engine-output files under `artifacts/{job_id}/`). Runs as a FastAPI BackgroundTask so POST returns 202 immediately.
      Files: `docmind/doc-service-py/app/service.py`
      Verify: parse flow test (step 17) sees job transition queued→running→succeeded with per-page progress.

- [ ] 16. Create `app/api.py` — routes: `POST /v1/parse` (multipart → 202 `{job_id, document_id, cached}`), `GET /v1/jobs/{job_id}` (status + per-page progress + engine_breakdown-so-far), `GET /v1/jobs/{job_id}/result` (canonical Document, 404 if not done), `GET /healthz` (200 liveness), `GET /readyz` (reports importability of optional ML libs, still 200 via stubs). Every handler reads `X-Correlation-Id` and binds the logging context.
      Files: `docmind/doc-service-py/app/api.py`
      Verify: health test (step 17) — healthz/readyz 200; parse endpoint returns 202.

- [ ] 17. Create `tests/` (pytest + FastAPI TestClient): `test_health`, `test_validation`, `test_parse_flow` (202 → poll → succeeded → schema-valid result with blocks/engine_breakdown/global order), `test_cache` (cached:true on 2nd identical upload), `test_schema` (latex rejected on non-formula). Add `tests/conftest.py`. Create `docmind/doc-service-py/Dockerfile` (python:3.11-slim, install libmagic, base requirements only, CMD uvicorn on 8091) and `.dockerignore`.
      Files: `docmind/doc-service-py/tests/*.py`, `docmind/doc-service-py/tests/conftest.py`, `docmind/doc-service-py/Dockerfile`, `docmind/doc-service-py/.dockerignore`
      Verify: `cd docmind/doc-service-py && python -m venv .venv && .venv\Scripts\python -m pip install -r requirements.txt && .venv\Scripts\python -m pytest -q` → all pass; `.venv\Scripts\python -c "import app.main"` → no ImportError.

## FEAT-003 — api-java (Spring Boot 4 orchestrator + Java-only chat/RAG)

- [ ] 18. Scaffold `docmind/api-java` as a Maven project mirroring horoscope: `mvnw/mvnw.cmd/.mvn`, `pom.xml` (parent `spring-boot-starter-parent:4.0.0`, groupId `com.example`, artifactId `docmind-api`, `java.version` 21). Deps: `spring-boot-starter-web`, `-validation`, `-data-jpa`, `org.postgresql:postgresql`, `flyway-core` + `flyway-database-postgresql`, `com.pgvector:pgvector` (or JDBC-mapped vector), `org.apache.tika:tika-core`, `lombok` (optional, annotation-processor paths like horoscope), `me.paulschwarz:spring-dotenv`, `spring-boot-starter-test`. `spring-boot-maven-plugin` excluding lombok.
      Files: `docmind/api-java/pom.xml`, `docmind/api-java/mvnw`, `docmind/api-java/mvnw.cmd`, `docmind/api-java/.mvn/**`, `docmind/api-java/.dockerignore`
      Verify: `cd docmind/api-java && mvnw.cmd -q -DskipTests package` resolves deps and compiles (after later steps add sources). After this step alone, `mvnw.cmd -q -DskipTests dependency:resolve` succeeds.

- [ ] 19. Create `src/main/resources/application.properties` (horoscope style): `spring.application.name=docmind-api`, `server.port=8090`, `spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/docmind}` + env-backed creds (default docmind/docmind), `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `docmind.doc-service.base-url=${DOC_SERVICE_BASE_URL:http://localhost:8091}` + timeout, `gemini.api.keys=${GEMINI_API_KEYS:}` + model defaults, `docmind.chat.top-k`, multipart size limits. Add `logback-spring.xml` emitting JSON logs.
      Files: `docmind/api-java/src/main/resources/application.properties`, `docmind/api-java/src/main/resources/logback-spring.xml`
      Verify: present; `server.port=8090`; Flyway enabled.

- [ ] 20. Create Flyway `src/main/resources/db/migration/V1__init.sql`: `documents(id uuid pk, filename, sha256 unique, mime, pages int, status, created_at, stats_json jsonb)`; `blocks(id uuid pk, document_id fk, page int, block_order int, type, text, markdown, html, latex, bbox jsonb, confidence real, heading_path text[], parse_engine, parse_version)`; `chunks(id uuid pk, document_id fk, heading_path text[], source_block_ids uuid[], page int, bbox jsonb, text, embedding vector(384), tsv tsvector)`. GIN index on `chunks.tsv`, ivfflat/hnsw index on `chunks.embedding` (comment: needs the extension from FEAT-001 init SQL), FK indexes on `blocks.document_id` + `chunks.document_id`. Document the 384 dim choice.
      Files: `docmind/api-java/src/main/resources/db/migration/V1__init.sql`
      Verify: file contains `vector(384)`, a `tsvector` column + GIN index, and the FK indexes; fields match `docs/canonical-schema.md`.

- [ ] 21. Create JPA `entity/` (DocumentEntity, BlockEntity, ChunkEntity) + Spring Data `repository/` interfaces. Map vector/tsvector pragmatically (`@Column(columnDefinition="vector(384)")` + converter, or native queries for vector/tsv ops). Entities clean per conventions; DTOs separate.
      Files: `docmind/api-java/src/main/java/com/example/docmind/entity/*.java`, `.../repository/*.java`
      Verify: compiles under `mvnw.cmd -q -DskipTests package`.

- [ ] 22. Create `dto/` records: DocumentUploadResponse, DocumentStatusResponse (incl. engine_breakdown), ChatRequest (`@Valid` constraints, optional top_k), ChatResponse (answer + citations[], each Citation carrying block/chunk id, page, bbox, heading_path, snippet).
      Files: `docmind/api-java/src/main/java/com/example/docmind/dto/*.java`
      Verify: compiles.

- [ ] 23. Create `client/DocServiceClient` (interface) + `client/RestDocServiceClient` (Boot 4 `RestClient`): `submitParse(file, correlationId)`, `getJob(jobId, correlationId)`, `getResult(jobId, correlationId)` mapping the Python canonical JSON to Java model classes; propagate `X-Correlation-Id` on every call (generate one per upload if absent); timeouts from config; log `stage=submit-parse`/`poll-job` with the correlation id.
      Files: `docmind/api-java/src/main/java/com/example/docmind/client/*.java`, `.../model/*.java` (canonical model)
      Verify: compiles; correlation-header + mapping covered by controller slice test with mocked client (step 28).

- [ ] 24. Create `config/` beans: `RestClient` bean (base-url + timeouts), `CorsConfig` (match horoscope if a frontend is expected), and a `CorrelationIdFilter` (`OncePerRequestFilter`) that reads/generates `X-Correlation-Id` and puts `document_id/job_id/correlation_id` into SLF4J MDC so every log line carries the chain.
      Files: `docmind/api-java/src/main/java/com/example/docmind/config/*.java`
      Verify: compiles; MDC propagation observable in logs during tests/smoke run.

- [ ] 25. Create `service/DocumentService`: Tika magic-byte MIME validation (reject mismatch/unsupported), SHA-256, persist `documents` row (status=PARSING), `DocServiceClient.submitParse`, store job_id, and an async/on-demand poll that pulls the result when ready → persists blocks → builds chunks (chunk by heading_path/structure, inheriting document_id/heading_path/source block_ids/page/bbox) → computes embeddings via EmbeddingClient → writes tsvector via SQL. Log stages validate/detect-mime/normalize/chunk/embed with durations.
      Files: `docmind/api-java/src/main/java/com/example/docmind/service/DocumentService.java`
      Verify: SHA-256 + Tika MIME unit test (step 28) passes; compiles.

- [ ] 26. Create `service/llm/LlmClient` (interface) + `StubLlmClient` (deterministic templated answer citing retrieved chunks) + `GeminiLlmClient` skeleton (active only when `gemini.api.keys` non-empty, else falls back to stub). Create `service/embedding/EmbeddingClient` (interface) + `StubEmbeddingClient` (deterministic hash-seeded, L2-normalized `float[384]`) + a TODO skeleton for a real sentence-transformers-backed impl. Select impls via `@ConditionalOnProperty`.
      Files: `docmind/api-java/src/main/java/com/example/docmind/service/llm/*.java`, `.../service/embedding/*.java`
      Verify: stub impls active by default; compiles; used by chat test (step 28).

- [ ] 27. Create `service/retrieval`: hybrid retrieval over chunks — pgvector cosine (`ORDER BY embedding <=> query`) + full-text (`tsvector @@ plainto_tsquery` with `ts_rank`) via native queries; fuse with Reciprocal Rank Fusion (RRF). Build prompt from top-k fused chunks → `LlmClient` → answer + citations tracing to block_ids/page/bbox/heading_path; a citation-validate step confirms each citation references a retrieved chunk. Log stages retrieve/rerank/citation-validate. Path is Java-only and never calls Python.
      Files: `docmind/api-java/src/main/java/com/example/docmind/service/retrieval/*.java`
      Verify: RRF fusion unit test (step 28) is deterministic; chat path returns answer+citations without touching the Python client.

- [ ] 28. Create `controller/DocumentController` (`POST /api/documents` → 201 DocumentUploadResponse; `GET /api/documents/{id}` → DocumentStatusResponse), `controller/ChatController` (`POST /api/chat` `@Valid` → ChatResponse), `exception/GlobalExceptionHandler` (`@RestControllerAdvice`: 400 validation, 404 not-found, unsupported-media, 500 — mirroring horoscope), `DocmindApiApplication` (`@SpringBootApplication`) + a health endpoint, and `Dockerfile` (two-stage: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`, EXPOSE 8090). Create tests: `@WebMvcTest` for DocumentController (mock DocumentService, 201 + validation), `@WebMvcTest` for ChatController (stub retrieval + StubLlmClient → answer + citations), RRF unit test, SHA-256/Tika-MIME unit test; `@MockBean` the DocServiceClient and DB-touching beans; a context-load test that does not require a live DB (exclude DataSource autoconfig in the test profile).
      Files: `docmind/api-java/src/main/java/com/example/docmind/controller/*.java`, `.../exception/GlobalExceptionHandler.java`, `.../DocmindApiApplication.java`, `docmind/api-java/Dockerfile`, `docmind/api-java/src/test/java/com/example/docmind/**/*.java`
      Verify: `cd docmind/api-java && mvnw.cmd -q -DskipTests package` → BUILD SUCCESS; `mvnw.cmd -q test` → all tests pass without a running Postgres or any ML model.

## Cross-feature integration (final convergence)
- [ ] 29. Validate the whole scaffold builds and the two-service contract lines up: compose config passes; Python tests + import check pass; Java package + tests pass; the canonical schema fields in `docs/canonical-schema.md`, Python `app/schema.py`, Java canonical model, and Flyway `V1__init.sql` all agree (type enums, bbox shape, parse_engine values, chunk inheritance). Optional, Docker-gated end-to-end: `docker compose up` → `POST /api/documents` (201) → poll `GET /api/documents/{id}` to parsed → `POST /api/chat` returns answer+citations, with `X-Correlation-Id` visible in doc-service-py logs.
      Files: (integration only — fix seams in the above files as needed)
      Verify: compose `config` 0; `docmind/doc-service-py` pytest green + `import app.main` ok; `docmind/api-java` `mvnw.cmd test` green; schema parity confirmed across the four locations.
