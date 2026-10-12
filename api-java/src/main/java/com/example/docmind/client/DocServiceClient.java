package com.example.docmind.client;

import com.example.docmind.client.model.CanonicalDocument;
import com.example.docmind.client.model.JobStatusResponse;
import com.example.docmind.client.model.ParseSubmitResponse;

/**
 * Client tới doc-service-py (parser Python). Mọi call propagate X-Correlation-Id.
 * Chỉ dùng ở luồng INGEST — luồng chat/RAG KHÔNG BAO GIỜ gọi client này.
 */
public interface DocServiceClient {

    /** POST /v1/parse (multipart, field 'file') -> {job_id, document_id, cached}. */
    ParseSubmitResponse submitParse(byte[] fileBytes, String filename, String correlationId);

    /** GET /v1/jobs/{jobId} -> trạng thái + tiến độ. */
    JobStatusResponse getJob(String jobId, String correlationId);

    /** GET /v1/jobs/{jobId}/result -> canonical Document (chỉ khi succeeded). */
    CanonicalDocument getResult(String jobId, String correlationId);
}
