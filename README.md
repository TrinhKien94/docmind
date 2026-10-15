# DocMind

A document-grounded RAG system (NotebookLLM-style): upload documents, then ask
questions answered **only** from what was uploaded, with citations back to the
exact page / block. DocMind lives inside the `digital-spirituality` monorepo
alongside the sibling `horoscope` service.

---

## Architecture

Two services plus Postgres:

- **`api-java`** — Spring Boot 4 **orchestrator** and the **chat / RAG brain**.
  Owns the database (via Flyway + JPA), the ingest job lifecycle, chunking,
  embedding + BM25 indexing, hybrid retrieval, reranking, LLM answer generation,
  and citation validation. Listens on **port 8090**.
- **`doc-service-py`** — FastAPI **document parser**. Takes a raw file and returns
  canonical blocks (see [`docs/canonical-schema.md`](docs/canonical-schema.md)).
  It routes a file to the right engine (PyMuPDF / Docling / PaddleOCR+PP-Structure
  for scans / native office parsers), then normalizes everything into the shared
  canonical schema. Listens on **port 8091**.
- **`frontend`** — Vue 3 + Vite **web client**. Giao diện tải lên file (kéo thả), theo dõi trạng thái real-time (`READY`, `PARSING`, `FAILED`), phân bổ engine, và chat hỏi đáp RAG với trích dẫn citations. Chạy tại **port 3000**.
- **`postgres`** — PostgreSQL 16 + **pgvector** (host **port 5433** -> 5432).

**The chat path is Java-only.** During chat, `api-java` reads already-parsed,
already-indexed data from Postgres and talks to the LLM/embedding providers
itself. It **never calls** `doc-service-py` on the chat path — the Python service
is only involved during **ingest**.

```
                 upload / chat (HTTP)
                         |
                         v
+--------------------------------------------------+
|  frontend (3000)  — Vue 3 / Vite UI              |
+--------------------------------------------------+
                         |
                         v
+--------------------------------------------------+
|  api-java (8090)  — orchestrator + chat/RAG      |
|  ingest jobs | chunking | embedding | BM25 |     |
|  hybrid retrieval | rerank | LLM | citations     |
+------------------+------------------+------------+
                   |                  |
   parse (ingest   |                  | SQL + pgvector
    only)          v                  v
     +---------------------+   +---------------------+
     | doc-service-py 8091 |   |  postgres 5433->5432|
     |  parser + OCR +     |   |  pgvector extension |
     |  canonical blocks   |   |  Flyway-owned tables|
     +---------------------+   +---------------------+
```

---

## REST API contract

### `doc-service-py` (parser) — async job pattern

| Method | Path                     | Purpose |
|--------|--------------------------|---------|
| GET    | `/healthz`               | Liveness. |
| GET    | `/readyz`                | Readiness (dependencies / lazy models). |
| POST   | `/v1/parse`              | Submit a file for parsing. Returns **202** with `{ "job_id": "..." }`. |
| GET    | `/v1/jobs/{id}`          | Job status: `queued` \| `running` \| `succeeded` \| `failed`. |
| GET    | `/v1/jobs/{id}/result`   | Canonical `Document` JSON (see canonical schema) once `succeeded`. |

### `api-java` (orchestrator) — ingest async + chat

| Method | Path                     | Purpose |
|--------|--------------------------|---------|
| POST   | `/api/documents`         | Upload a file (`multipart/form-data`, field `file`) -> starts ingest. Returns **201** with `{ "document_id": "...", "job_id": "...", "status": "..." }`. |
| GET    | `/api/documents`         | List all uploaded documents with status, pages count, and engine breakdown. |
| GET    | `/api/documents/{id}`    | Ingest job status (mirrors parser states + indexing). |
| POST   | `/api/chat`              | Ask a question. Request `{ "question": "...", "document_ids": [...], "top_k": N }`; response includes the answer and `citations[]` referencing `document_id` + `page` + `bbox` + `block_ids`. |
| POST   | `/api/customer-chat`     | Customer sales & service consultation using pre-trained prompt (`SALES`, `SERVICE_SUPPORT`, `WARRANTY_RETURN`, `ORDER_BILLING`, `GENERAL`) + EvoAgentX self-evolving guidelines & exemplars. Aliases: `/api/chat/customer`, `/api/customer-support/chat`. |
| GET    | `/api/customer-chat/prompts` | Inspect the pre-trained customer support prompt template, supported scenarios, and suggested customer questions. |
| GET    | `/api/customer-chat/evolution` | Inspect the EvoAgentX self-evolution engine state: current generation, quality score, learned guidelines per scenario, evolved MIPRO few-shot exemplars, and recent TextGrad reflections. |
| POST   | `/api/customer-chat/feedback` | Submit explicit customer feedback (`interaction_id`, `rating` 1..5, `helpful`, `feedback`) to trigger TextGrad guideline learning or MIPRO exemplar promotion. |
| POST   | `/api/customer-chat/evolve` | Proactively trigger an EvoAgentX TextGrad + MIPRO optimization cycle (optionally scoped by `scenario`). |
| DELETE | `/api/customer-chat/evolution` | Reset the self-evolving memory store to its initial state. |
| GET    | `/api/astrbot/overview`  | Full snapshot of the AstrBot Multi-Platform Control Center (19 platform adapter types, active bots, UMO sessions, personas, LLM providers, cron jobs, extensions/MCP servers, traces, and console logs). |
| GET/POST/PUT/DELETE | `/api/astrbot/bots/**` | Manage multi-platform bot instances (`telegram`, `discord`, `slack`, `lark`, `dingtalk`, `line`, `wecom`, `wecom_ai_bot`, `weixin_official_account`, `weixin_oc`, `qq_official`, `qq_official_webhook`, `aiocqhttp`, `satori`, `kook`, `mattermost`, `misskey`, `facebook_messenger`, `webchat`), test connections, and perform QR registration (`/api/astrbot/bot-types/{type}/registration`). |
| GET/POST | `/api/astrbot/webhooks/platforms/{uuid}` | Unified Webhook callback verification & event ingestion across supported chat platforms. |
| POST   | `/api/astrbot/simulate`  | Simulate multi-platform `AstrMessageEvent` and internal Star Slash Commands (`/help`, `/status`, `/reset`, `/persona`, `/scenario`, `/evolve`, `/whoami`) routed through `CustomerChatService` + Dior KB + EvoAgentX. |
| GET/POST/DELETE | `/api/astrbot/sessions`, `/routes`, `/personas`, `/providers`, `/cron`, `/extensions`, `/logs`, `/traces` | Manage UMO session custom rules, UMO route bindings, AI Personas, LLM Provider sources, proactive Cron Jobs, Star Plugins/MCP Servers, pipeline traces, and console logs. |

The exact block/chunk fields these endpoints exchange are defined in
[`docs/canonical-schema.md`](docs/canonical-schema.md) — read that first.

---

## EvoAgentX Self-Evolving Customer Support Strategy

After every customer chat turn (`POST /api/customer-chat`) and explicit feedback (`POST /api/customer-chat/feedback`), `CustomerSelfEvolutionService` executes a 3-pillar self-evolving learning loop inspired by **EvoAgentX**:

1. **TextGrad Multi-Criteria Loss & Textual Gradient Reflection**:
   - Evaluates each consultation response across 4 weighted dimensions (`salutation & tone`, `grounding & citation`, `completeness & structure`, `actionability & contact`) plus implicit customer correction signals in conversation history.
   - Generates a structured **Textual Gradient** (either deterministically or via LLM `<EVOLVED_GUIDELINE>` reflection) and synthesizes concrete `LearnedGuidelineInfo` rules for the active scenario.
2. **MIPRO Few-Shot Exemplar Bootstrapping**:
   - High-scoring or positively rated Q&A turns are automatically promoted into `EvolvedExampleInfo` few-shot exemplars and injected into subsequent prompts alongside base few-shot examples.
3. **Long-Term Memory SHA-256 Deduplication & Persistence**:
   - Uses normalized SHA-256 content hashes to deduplicate learned rules and exemplars, boosting `reinforcement_count` and `confidence_score` when a rule is repeatedly validated, pruning to top-K utility per scenario, and optionally persisting atomically to disk (`CUSTOMER_EVOLUTION_PERSIST_TO_DISK=true`).

---

## Externalized Configuration

All store identity, personas, scenario playbooks, keywords, few-shot examples, EvoAgentX evolution parameters, CORS rules, upload MIME allow-lists, and upstream service paths are externalized into [`api-java/src/main/resources/application.properties`](api-java/src/main/resources/application.properties) and [`.env.example`](.env.example):

- `docmind.customer-support.*` — Store branding, persona, default customer role, salutations, RAG `top_k` bounds, history window limits, base few-shot examples, per-scenario playbooks/keywords/aliases (`scenarios.SALES.*`, `scenarios.SERVICE_SUPPORT.*`, etc.), and EvoAgentX self-evolution settings (`evolution.*`).
- `docmind.cors.*` — Path pattern, allowed origin patterns, methods, headers, exposed headers, credentials, and max-age.
- `docmind.upload.*` — Max upload bytes and allowed MIME types.
- `docmind.chat.*` — Default/min/max `top_k`, snippet max length, and PostgreSQL FTS configuration (`fts-config`).
- `docmind.doc-service.*` — Parser endpoint paths (`parse-path`, `job-status-path`, `job-result-path`, `embed-path`, `rerank-path`).
- `ninerouter.*` & `gemini.*` — Primary/fallback LLM URLs, endpoint paths, timeouts, and fallback messages.

---

## Ingest flow

```
upload (POST /api/documents to api-java)
   |
   v
validate / hash (sha256) / detect mime     [antivirus hook: later]
   |
   v
api-java calls doc-service-py POST /v1/parse  -> 202 job_id
   |
   v
doc-service-py routes by file type:
   .md/.html/.docx/.pptx/.xlsx -> office parser
   .pdf -> classify:
       text simple   -> pymupdf
       text complex  -> docling
       scanned       -> paddleocr (render page -> OCR + PP-Structure)
       mixed         -> per page: text parser or paddleocr
   |
   v
Normalizer -> canonical Document { blocks[] }  (see canonical-schema.md)
   |
   v
api-java: chunk (inherit document_id/heading_path/block_ids/page/bbox)
   |
   v
embedding + BM25 index  (pgvector + text index in Postgres)
```

## Chat flow (Java-only; never calls Python)

```
question (POST /api/chat or POST /api/customer-chat to api-java)
   |
   v
embed query + BM25 query  (hybrid)
   |
   v
hybrid retrieval over Postgres (pgvector + BM25)
   |
   v
reranker + EvoAgentX active guidelines & few-shot exemplars
   |
   v
LLM answer with citations  (9Router -> Codex GPT-6 Astra, Gemini key, or stub LLM)
   |
   v
citation validation + EvoAgentX post-chat TextGrad & MIPRO self-evolution
```

---

## Observability / correlation-id model

Every ingest and chat request carries a **correlation id** (generated by
`api-java` if absent, propagated to `doc-service-py` as a header, and attached
to the parse job). Both services emit **structured JSON logs** that include the
correlation id, the `job_id`, the service name, and the stage, so a single
request can be traced end-to-end across Java -> Python -> Postgres. This is
deliberate: the pipeline has many stages, so logs must make it obvious **where**
a failure happened. Log level is controlled by `DOCMIND_LOG_LEVEL`.

---

## Deliberate scope notes

- **Tích hợp 9Router + Codex (GPT-6 Astra)**: Hệ thống định tuyến mặc định luồng sinh câu trả lời RAG qua **9Router** (`http://127.0.0.1:20128/v1`) với model `codex/gpt-6-astra`, bảo lưu nguyên vẹn setting config hiện tại của OpenAI Codex CLI và `ch-relay` (`127.0.0.1:11500`). Khi 9Router chưa bật hoặc cần dự phòng, `NineRouterLlmClient` tự động chuyển tiếp an toàn (fallback) sang direct Codex relay hoặc stub mà không làm gián đoạn luồng chat. Script khởi chạy tiện ích đi kèm: `start_9router.ps1`.
- **MinerU is intentionally DEFERRED** until its license is verified. It is not
  wired into the parser router and is **not** a valid `parse_engine` value in the
  canonical schema. PyMuPDF / Docling / PaddleOCR / office parsers cover the
  current paths.
- **Formula handling targets LaTeX output.** A `formula` block carries its LaTeX
  in the `latex` field (non-null only for `type == "formula"`); see the canonical
  schema for the exact rule.
- **Chat uses 9Router / Codex** mặc định, hoặc Gemini nếu cấu hình `GEMINI_API_KEYS`,
  hoặc stub LLM nếu tắt cả hai, đảm bảo hệ thống luôn khởi động và trả lời mà không bị chết.
- Heavy ML dependencies in the Python service are **lazy-imported behind stubs**
  so the service boots and tests pass CPU-only without model downloads.

See [`db/README.md`](db/README.md) for the init-SQL vs. Flyway ownership split
and [`docs/canonical-schema.md`](docs/canonical-schema.md) for the shared data
contract.
