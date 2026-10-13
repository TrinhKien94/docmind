/**
 * Dữ liệu Demo khi chưa khởi động Docker / backend Spring Boot
 */

export const initialMockDocuments = [
  {
    document_id: "doc-sample-ai-arch-01",
    filename: "DocMind-Architecture-Specification.pdf",
    status: "READY",
    pages: 18,
    engine_breakdown: {
      pymupdf: 12,
      docling: 6
    },
    created_at: new Date(Date.now() - 3600000).toISOString()
  },
  {
    document_id: "doc-sample-financial-02",
    filename: "Bao_Cao_Tai_Chinh_Q3_2025.docx",
    status: "READY",
    pages: 34,
    engine_breakdown: {
      docx_office: 34
    },
    created_at: new Date(Date.now() - 7200000).toISOString()
  },
  {
    document_id: "doc-sample-scan-ocr-03",
    filename: "Hop_Dong_Mua_Ban_Scan.pdf",
    status: "PARSING",
    pages: 5,
    engine_breakdown: {
      paddleocr: 3
    },
    created_at: new Date(Date.now() - 120000).toISOString()
  },
  {
    document_id: "doc-sample-corrupt-04",
    filename: "Corrupted_Archive_Data.bin",
    status: "FAILED",
    pages: 0,
    engine_breakdown: {},
    created_at: new Date(Date.now() - 18000000).toISOString()
  }
]

export const sampleQA = {
  "kiến trúc": {
    answer: "DocMind là hệ thống RAG độc lập dựa trên tài liệu (phong cách NotebookLLM). Kiến trúc bao gồm hai dịch vụ chính và PostgreSQL:\n\n1. **`api-java` (Port 8090)**: Orchestrator viết bằng Spring Boot 4, quản lý vòng đời ingest, chunking, hybrid retrieval (pgvector + BM25 Reciprocal Rank Fusion), gọi LLM và xác thực trích dẫn (citation validation).\n2. **`doc-service-py` (Port 8091)**: FastAPI parser trích xuất các block chuẩn hóa từ PDF, DOCX, PPTX với PyMuPDF, Docling và PaddleOCR.\n3. **`PostgreSQL 16`**: Lưu trữ vector embedding và schema chuẩn hóa qua Flyway.\n\nĐặc biệt, luồng chat hoàn toàn chạy trên Java và **không bao giờ gọi sang Python service** lúc suy luận.",
    citations: [
      {
        chunk_id: "chk-arch-001",
        document_id: "doc-sample-ai-arch-01",
        block_ids: ["blk-01", "blk-02"],
        page: 1,
        bbox: [72.0, 100.5, 540.0, 320.0],
        heading_path: ["Architecture", "Overview"],
        snippet: "Two services plus Postgres: api-java (8090) orchestrator and chat/RAG brain; doc-service-py (8091) FastAPI parser."
      },
      {
        chunk_id: "chk-arch-002",
        document_id: "doc-sample-ai-arch-01",
        block_ids: ["blk-05"],
        page: 2,
        bbox: [72.0, 450.0, 520.0, 590.0],
        heading_path: ["Architecture", "Chat Path"],
        snippet: "The chat path is Java-only. During chat, api-java reads already-indexed data and never calls doc-service-py."
      }
    ]
  },
  "default": {
    answer: "Dựa trên các tài liệu đã tải lên được chọn, hệ thống tìm thấy thông tin phù hợp và trích xuất nguyên văn kèm theo số trang và khối văn bản tương ứng.",
    citations: [
      {
        chunk_id: "chk-gen-01",
        document_id: "doc-sample-ai-arch-01",
        block_ids: ["blk-10"],
        page: 3,
        bbox: [50.0, 120.0, 500.0, 240.0],
        heading_path: ["Ingest Flow", "Hybrid Retrieval"],
        snippet: "Hybrid retrieval kết hợp Dense Vector Search qua pgvector và Sparse Lexical Search qua BM25, hợp nhất bằng Reciprocal Rank Fusion (RRF k=60)."
      }
    ]
  }
}
