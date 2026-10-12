-- ============================================================
-- DocMind api-java — Flyway V1 (schema đời thực do Flyway sở hữu).
--
-- LƯU Ý về pgvector: extension `vector` được bật bởi FEAT-001
-- (docmind/db/init/001_init.sql: CREATE EXTENSION IF NOT EXISTS vector;).
-- Migration này chạy CREATE EXTENSION phòng hờ (idempotent) để chạy được cả
-- khi Flyway migrate trên một DB chưa qua init script đó.
--
-- Embedding dim = 384 khớp model paraphrase-multilingual-MiniLM-L12-v2
-- (multilingual, CPU-friendly) — xem docmind/docs + FEAT-003.
-- Schema field KHỚP docmind/docs/canonical-schema.md (nguồn chân lý).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------------------
-- documents: 1 dòng / 1 file đã upload + parse.
-- ------------------------------------------------------------
CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    filename    TEXT        NOT NULL,
    sha256      TEXT        NOT NULL UNIQUE,   -- dedup / integrity (hex)
    mime        TEXT        NOT NULL,          -- MIME nhận diện theo magic-byte
    pages       INTEGER     NOT NULL DEFAULT 0,
    status      TEXT        NOT NULL,          -- PARSING | READY | FAILED
    job_id      TEXT,                          -- job_id phía doc-service-py (để poll)
    stats_json  JSONB,                         -- stats.engine_breakdown {engine: count}
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- blocks: canonical Block (parser là writer duy nhất; Java persist nguyên trạng).
-- Khớp §2 canonical-schema.md.
-- ------------------------------------------------------------
CREATE TABLE blocks (
    id            UUID PRIMARY KEY,                 -- Block.block_id
    document_id   UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page          INTEGER NOT NULL,                 -- 1-based
    block_order   INTEGER NOT NULL,                 -- 0-based reading order
    type          TEXT NOT NULL,                    -- heading|paragraph|table|formula|key_value|checkbox|image|list
    text          TEXT NOT NULL DEFAULT '',
    markdown      TEXT NOT NULL DEFAULT '',
    html          TEXT NOT NULL DEFAULT '',
    latex         TEXT,                             -- non-null CHỈ khi type=formula, else null
    bbox          DOUBLE PRECISION[] NOT NULL,      -- [x0,y0,x1,y1] top-left origin
    confidence    REAL NOT NULL DEFAULT 0,          -- 0.0..1.0
    heading_path  TEXT[] NOT NULL DEFAULT '{}',     -- breadcrumb heading
    parse_engine  TEXT NOT NULL,                    -- paddleocr|docling|pymupdf|office
    parse_version TEXT NOT NULL
);

-- FK index (Postgres không tự tạo index cho FK) + truy vấn theo document.
CREATE INDEX idx_blocks_document_id ON blocks (document_id);
-- Đọc theo thứ tự trong 1 document.
CREATE INDEX idx_blocks_doc_order ON blocks (document_id, block_order);

-- ------------------------------------------------------------
-- chunks: đơn vị retrieval, Java dựng từ blocks (kế thừa theo canonical-schema §3).
-- ------------------------------------------------------------
CREATE TABLE chunks (
    id               UUID PRIMARY KEY,
    document_id      UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    heading_path     TEXT[] NOT NULL DEFAULT '{}',  -- kế thừa từ Block
    source_block_ids UUID[] NOT NULL DEFAULT '{}',  -- Block.block_id tạo nên chunk
    page             INTEGER NOT NULL,              -- chunk nằm gọn trong 1 page
    bbox             DOUBLE PRECISION[] NOT NULL,   -- union bbox các block
    text             TEXT NOT NULL,
    embedding        vector(384),                   -- pgvector; dim 384 (MiniLM-L12-v2)
    tsv              TSVECTOR                       -- full-text (BM25-ish qua ts_rank)
);

-- FK index + truy vấn theo document.
CREATE INDEX idx_chunks_document_id ON chunks (document_id);

-- GIN index cho full-text search trên tsvector.
CREATE INDEX idx_chunks_tsv ON chunks USING GIN (tsv);

-- ANN index cho vector search (cosine). ivfflat cần extension `vector` (bật ở trên).
-- lists=100 là mặc định hợp lý cho dataset nhỏ/vừa; tăng khi dữ liệu lớn.
-- Dùng vector_cosine_ops vì retrieval sắp xếp theo cosine distance (<=>).
CREATE INDEX idx_chunks_embedding ON chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
