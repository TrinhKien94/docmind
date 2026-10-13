package com.example.docmind.service;

import com.example.docmind.client.DocServiceClient;
import com.example.docmind.client.model.CanonicalBlock;
import com.example.docmind.client.model.CanonicalDocument;
import com.example.docmind.client.model.JobStatusResponse;
import com.example.docmind.client.model.ParseSubmitResponse;
import com.example.docmind.config.DocServiceProperties;
import com.example.docmind.dto.DocumentStatusResponse;
import com.example.docmind.dto.DocumentUploadResponse;
import com.example.docmind.entity.BlockEntity;
import com.example.docmind.entity.DocumentEntity;
import com.example.docmind.exception.DocServiceException;
import com.example.docmind.exception.ResourceNotFoundException;
import com.example.docmind.observability.CorrelationIds;
import com.example.docmind.repository.BlockRepository;
import com.example.docmind.repository.ChunkRepository;
import com.example.docmind.repository.DocumentRepository;
import com.example.docmind.service.embedding.EmbeddingClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Luồng INGEST. Upload: validate (Tika magic-byte + SHA-256) -> persist documents
 * (PARSING) -> gọi doc-service-py submitParse -> lưu job_id. Poll async: chờ parse
 * xong -> persist blocks + dựng chunks + embed + ghi tsvector.
 *
 * <p>Log các stage: validate / detect-mime / submit-parse / poll-job / normalize /
 * chunk / embed — kèm correlation chain qua MDC.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final BlockRepository blockRepository;
    private final ChunkRepository chunkRepository;
    private final DocServiceClient docServiceClient;
    private final EmbeddingClient embeddingClient;
    private final DocServiceProperties docServiceProps;
    private final ObjectMapper objectMapper;

    public DocumentService(DocumentRepository documentRepository,
                           BlockRepository blockRepository,
                           ChunkRepository chunkRepository,
                           DocServiceClient docServiceClient,
                           EmbeddingClient embeddingClient,
                           DocServiceProperties docServiceProps,
                           ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.blockRepository = blockRepository;
        this.chunkRepository = chunkRepository;
        this.docServiceClient = docServiceClient;
        this.embeddingClient = embeddingClient;
        this.docServiceProps = docServiceProps;
        this.objectMapper = objectMapper;
    }

    /** Upload: validate + hash + persist + submit parse. Trả về 201 payload. */
    @Transactional
    public DocumentUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File rỗng");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được file upload", e);
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

        // stage=validate + detect-mime (magic-byte, KHÔNG theo đuôi file).
        CorrelationIds.setStage("validate");
        String sha256 = FileValidation.sha256Hex(bytes);
        log.info("validate filename={} size={} sha256={}", originalName, bytes.length, sha256);

        CorrelationIds.setStage("detect-mime");
        String mime = FileValidation.detectMime(bytes, originalName);
        log.info("detect-mime detected={}", mime);
        FileValidation.requireSupported(mime);
        CorrelationIds.clearStage();

        // Dedup theo sha256: nếu đã có, trả lại document hiện tại (không parse lại).
        var existing = documentRepository.findBySha256(sha256);
        if (existing.isPresent()) {
            DocumentEntity doc = existing.get();
            CorrelationIds.putDocumentId(doc.getId().toString());
            log.info("dedup sha256 hit -> document_id={} status={}", doc.getId(), doc.getStatus());
            return new DocumentUploadResponse(doc.getId().toString(), doc.getJobId(), doc.getStatus());
        }

        String correlationId = CorrelationIds.currentCorrelationId();
        if (correlationId == null) {
            correlationId = CorrelationIds.newId();
            CorrelationIds.putCorrelationId(correlationId);
        }

        // Gọi parser (propagate X-Correlation-Id).
        ParseSubmitResponse submit = docServiceClient.submitParse(bytes, originalName, correlationId);

        // document_id ưu tiên theo id parser trả về (ổn định xuyên hệ thống).
        UUID documentId = safeUuid(submit.documentId());
        CorrelationIds.putDocumentId(documentId.toString());
        CorrelationIds.putJobId(submit.jobId());

        DocumentEntity doc = DocumentEntity.builder()
                .id(documentId)
                .filename(originalName)
                .sha256(sha256)
                .mime(mime)
                .pages(0)
                .status("PARSING")
                .jobId(submit.jobId())
                .build();
        documentRepository.save(doc);
        log.info("persist documents row document_id={} job_id={} status=PARSING", documentId, submit.jobId());

        // Nếu parser báo cached => kết quả sẵn, poll ngay (async) để persist.
        pollAndPersistAsync(documentId, submit.jobId(), correlationId);

        return new DocumentUploadResponse(documentId.toString(), submit.jobId(), "PARSING");
    }

    /**
     * Poll job tới khi succeeded/failed rồi persist blocks + chunks + embeddings.
     * Chạy async để POST /api/documents trả 201 ngay.
     */
    @Async
    public void pollAndPersistAsync(UUID documentId, String jobId, String correlationId) {
        CorrelationIds.putCorrelationId(correlationId);
        CorrelationIds.putDocumentId(documentId.toString());
        CorrelationIds.putJobId(jobId);
        try {
            CanonicalDocument result = pollUntilDone(jobId, correlationId);
            persistResult(documentId, result);
        } catch (Exception e) {
            log.error("Ingest async thất bại document_id={} job_id={}: {}", documentId, jobId, e.getMessage());
            markFailed(documentId);
        }
    }

    private CanonicalDocument pollUntilDone(String jobId, String correlationId) {
        int attempts = docServiceProps.getPollMaxAttempts();
        long interval = docServiceProps.getPollIntervalMs();
        for (int i = 0; i < attempts; i++) {
            JobStatusResponse status = docServiceClient.getJob(jobId, correlationId);
            if (status.isSucceeded()) {
                return docServiceClient.getResult(jobId, correlationId);
            }
            if (status.isFailed()) {
                throw new DocServiceException("Parser báo job failed: " + status.error());
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new DocServiceException("Poll job bị gián đoạn", ie);
            }
        }
        throw new DocServiceException("Poll job timeout sau " + attempts + " lần: " + jobId);
    }

    /** Persist blocks + dựng/embed/ghi chunks. Tách transaction riêng. */
    @Transactional
    public void persistResult(UUID documentId, CanonicalDocument result) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document không tồn tại: " + documentId));

        // QUAN TRỌNG: insertChunk (native SQL) chạy TỨC THÌ và có FK ->documents.
        // Hibernate trì hoãn INSERT documents/blocks tới cuối transaction, nên nếu
        // không flush trước, chunk sẽ vi phạm FK (document_id chưa có trong DB) ->
        // transaction abort (SQLState 25P02) -> rollback toàn bộ. Flush để ép thứ tự
        // ghi: documents + blocks xuống DB TRƯỚC khi insert chunk.
        documentRepository.saveAndFlush(doc);

        List<CanonicalBlock> blocks = result.blocks() == null ? List.of() : result.blocks();

        // stage=normalize: persist blocks nguyên trạng.
        CorrelationIds.setStage("normalize");
        List<BlockEntity> blockEntities = blocks.stream().map(b -> BlockEntity.builder()
                .id(safeUuid(b.blockId()))
                .documentId(documentId)
                .page(b.page())
                .blockOrder(b.order())
                .type(b.type())
                .text(nullToEmpty(b.text()))
                .markdown(nullToEmpty(b.markdown()))
                .html(nullToEmpty(b.html()))
                .latex(b.latex())
                .bbox(toDoubleArray(b.bbox()))
                .confidence((float) b.confidence())
                .headingPath(toStringArray(b.headingPath()))
                .parseEngine(b.parseEngine())
                .parseVersion(b.parseVersion())
                .build()).toList();
        blockRepository.saveAll(blockEntities);
        blockRepository.flush();   // blocks xuống DB trước khi dựng chunk
        log.info("normalize persisted_blocks={}", blockEntities.size());

        // stage=chunk: nhóm block -> chunk.
        CorrelationIds.setStage("chunk");
        List<ChunkBuilder.BuiltChunk> builtChunks = ChunkBuilder.build(documentId, blocks);
        log.info("chunk built_chunks={}", builtChunks.size());

        // stage=embed: embed + insert chunk (native, cast vector/tsvector).
        CorrelationIds.setStage("embed");
        for (ChunkBuilder.BuiltChunk c : builtChunks) {
            float[] embedding = embeddingClient.embed(c.text());
            chunkRepository.insertChunk(
                    c.id(), c.documentId(), c.headingPath(), c.sourceBlockIds(),
                    c.page(), c.bbox(), c.text(), embedding);
        }
        log.info("embed embedded_chunks={} dim={}", builtChunks.size(), embeddingClient.dim());
        CorrelationIds.clearStage();

        // Cập nhật document: pages + stats + READY.
        int pages = result.source() != null ? result.source().pages() : 0;
        doc.setPages(pages);
        doc.setStatus("READY");
        doc.setStatsJson(engineBreakdownJson(result));
        documentRepository.save(doc);
        log.info("ingest done document_id={} pages={} status=READY", documentId, pages);
    }

    @Transactional(readOnly = true)
    public List<DocumentStatusResponse> listAll() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream().map(doc -> {
            Map<String, Integer> breakdown = parseBreakdown(doc.getStatsJson());
            return new DocumentStatusResponse(
                    doc.getId().toString(),
                    doc.getFilename(),
                    doc.getStatus(),
                    doc.getPages(),
                    breakdown
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getStatus(String documentIdRaw) {
        UUID documentId = safeUuid(documentIdRaw);
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document không tồn tại: " + documentIdRaw));
        Map<String, Integer> breakdown = parseBreakdown(doc.getStatsJson());
        return new DocumentStatusResponse(
                doc.getId().toString(),
                doc.getFilename(),
                doc.getStatus(),
                doc.getPages(),
                breakdown
        );
    }

    // --- helpers ---

    @Transactional
    public void markFailed(UUID documentId) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus("FAILED");
            documentRepository.save(doc);
        });
    }

    private String engineBreakdownJson(CanonicalDocument result) {
        try {
            Map<String, Integer> breakdown =
                    result.stats() != null ? result.stats().engineBreakdown() : Map.of();
            return objectMapper.writeValueAsString(Map.of("engine_breakdown",
                    breakdown == null ? Map.of() : breakdown));
        } catch (JacksonException e) {
            log.warn("Không serialize được stats_json: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseBreakdown(String statsJson) {
        if (statsJson == null || statsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(statsJson, Map.class);
            Object bd = root.get("engine_breakdown");
            if (bd instanceof Map<?, ?> m) {
                return (Map<String, Integer>) m;
            }
        } catch (JacksonException e) {
            log.warn("Không parse được stats_json: {}", e.getMessage());
        }
        return Map.of();
    }

    private static UUID safeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static double[] toDoubleArray(List<Double> list) {
        if (list == null) {
            return new double[]{0, 0, 0, 0};
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i) == null ? 0.0 : list.get(i);
        }
        return out;
    }

    private static String[] toStringArray(List<String> list) {
        return list == null ? new String[0] : list.toArray(new String[0]);
    }
}
