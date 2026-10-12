-- ============================================================
-- DocMind — khởi tạo cơ sở dữ liệu (chạy MỘT LẦN khi volume Postgres
-- được tạo lần đầu, do /docker-entrypoint-initdb.d gọi).
--
-- File này CHỈ đảm bảo extension pgvector tồn tại. Toàn bộ bảng ứng
-- dụng (documents, blocks, chunks, embeddings, jobs, ...) do Flyway
-- trong service api-java quản lý — KHÔNG tạo DDL ứng dụng ở đây để
-- tránh trùng lặp và lệch schema. Xem docmind/db/README.md.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;
