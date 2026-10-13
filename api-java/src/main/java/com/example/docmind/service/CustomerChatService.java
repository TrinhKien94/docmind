package com.example.docmind.service;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerPromptTemplateResponse;
import com.example.docmind.entity.DocumentEntity;
import com.example.docmind.repository.DocumentRepository;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.retrieval.CustomerPromptBuilder;
import com.example.docmind.service.retrieval.CustomerScenario;
import com.example.docmind.service.retrieval.ReciprocalRankFusion;
import com.example.docmind.service.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service xử lý API trả lời người dùng đóng vai trò khách hàng mua hàng hoặc sử dụng dịch vụ
 * bằng bộ prompt được huấn luyện trước ({@link CustomerPromptBuilder}).
 *
 * <p>Kết hợp:
 * <ol>
 *   <li>Nhận diện kịch bản khách hàng (Mua hàng, Sử dụng dịch vụ, Bảo hành/Đổi trả, Đơn hàng/Thanh toán).</li>
 *   <li>Hybrid RAG retrieval trên tài liệu chỉ định hoặc tự động lấy các tài liệu READY trong hệ thống.</li>
 *   <li>Dựng prompt được huấn luyện trước (Persona + Scenario Playbook + Few-Shot + Store Policy + RAG Context).</li>
 *   <li>Gọi LLM (9Router Codex GPT-6 Astra mặc định, hoặc Gemini/Stub) và kiểm tra tính hợp lệ của citations.</li>
 * </ol>
 */
@Service
public class CustomerChatService {

    private static final Logger log = LoggerFactory.getLogger(CustomerChatService.class);
    private static final int SNIPPET_MAX = 240;

    private final RetrievalService retrievalService;
    private final DocumentRepository documentRepository;
    private final LlmClient llmClient;
    private final CustomerSupportProperties customerProps;

    @Autowired
    public CustomerChatService(RetrievalService retrievalService,
                               DocumentRepository documentRepository,
                               LlmClient llmClient,
                               CustomerSupportProperties customerProps) {
        this.retrievalService = retrievalService;
        this.documentRepository = documentRepository;
        this.llmClient = llmClient;
        this.customerProps = customerProps;
    }

    public CustomerChatResponse chat(CustomerChatRequest request) {
        if (request == null || !request.isQuestionProvided()) {
            throw new IllegalArgumentException("question hoặc message của khách hàng không được để trống");
        }
        if (request.topK() != null && (request.topK() < 1 || request.topK() > 50)) {
            throw new IllegalArgumentException("top_k phải nằm trong khoảng từ 1 đến 50");
        }

        String question = request.resolvedQuestion();
        CustomerScenario scenario = CustomerScenario.resolve(request.scenario(), question);
        String customerRole = (request.customerRole() != null && !request.customerRole().isBlank())
                ? request.customerRole().strip()
                : CustomerPromptBuilder.DEFAULT_CUSTOMER_ROLE;
        int defaultTopK = (customerProps.getDefaultTopK() >= 1 && customerProps.getDefaultTopK() <= 50)
                ? customerProps.getDefaultTopK()
                : 5;
        int topK = request.topK() != null ? request.topK() : defaultTopK;

        // 1) Truy xuất ngữ cảnh tài liệu (nếu khách hàng truyền document_ids hoặc tự động tìm tài liệu READY).
        List<ReciprocalRankFusion.FusedChunk> fused = retrieveChunksForCustomer(request.documentIds(), question, topK);

        // 2) Dựng prompt được huấn luyện trước cho vai trò tư vấn khách hàng mua hàng / sử dụng dịch vụ.
        String prompt = CustomerPromptBuilder.build(request, scenario, customerProps, fused);
        String answer = llmClient != null ? llmClient.complete(prompt) : null;
        if (answer == null || answer.isBlank() || answer.strip().startsWith("[9router-error]")) {
            log.warn("LLM returned empty or error response; generating pre-trained customer fallback for scenario={}",
                    scenario.code());
            answer = buildPretrainedFallbackAnswer(request, scenario, fused);
        }
        String modelName = llmClient != null ? llmClient.name() : "pretrained-fallback";
        log.info("stage=customer-chat engine={} scenario={} fused_chunks={}",
                modelName, scenario.code(), fused.size());

        // 3) Dựng citations truy ngược về chunk/document/block/page/bbox/heading_path.
        List<Citation> citations = new ArrayList<>(fused.size());
        Set<UUID> retrievedIds = new HashSet<>();
        for (ReciprocalRankFusion.FusedChunk fc : fused) {
            RetrievedChunk c = fc.chunk();
            retrievedIds.add(c.chunkId());
            citations.add(new Citation(
                    c.chunkId().toString(),
                    c.documentId().toString(),
                    c.sourceBlockIds() != null
                            ? c.sourceBlockIds().stream().map(UUID::toString).toList()
                            : List.of(),
                    c.page(),
                    toBboxList(c.bbox()),
                    c.headingPath() != null ? c.headingPath() : List.of(),
                    snippet(c.text())
            ));
        }

        // 4) Citation-validate: đảm bảo mọi citation đều thuộc tập chunk thực sự được retrieve.
        long invalid = citations.stream()
                .filter(cit -> !retrievedIds.contains(UUID.fromString(cit.chunkId())))
                .count();
        if (invalid > 0) {
            citations.removeIf(cit -> !retrievedIds.contains(UUID.fromString(cit.chunkId())));
        }

        return new CustomerChatResponse(
                answer,
                customerRole,
                scenario.code(),
                customerProps.getPromptTemplateId(),
                modelName,
                scenario.suggestedQuestions(),
                citations
        );
    }

    /**
     * Trả về thông tin cấu hình bộ prompt được huấn luyện trước và danh sách các kịch bản hỗ trợ khách hàng.
     */
    public CustomerPromptTemplateResponse getPromptTemplates() {
        CustomerChatRequest sampleReq = new CustomerChatRequest(
                "Tư vấn giúp tôi gói dịch vụ phù hợp và chính sách bảo hành khi mua hàng?",
                CustomerScenario.SALES.code(),
                null,
                customerProps.getDefaultTopK()
        );
        String preview = CustomerPromptBuilder.build(sampleReq, CustomerScenario.SALES, customerProps, List.of());

        return new CustomerPromptTemplateResponse(
                customerProps.getPromptTemplateId(),
                customerProps.getStoreName(),
                CustomerPromptBuilder.DEFAULT_CUSTOMER_ROLE,
                CustomerPromptBuilder.ASSISTANT_PERSONA,
                CustomerScenario.allScenarioInfos(),
                preview
        );
    }

    private List<ReciprocalRankFusion.FusedChunk> retrieveChunksForCustomer(
            List<String> rawDocumentIds, String question, int topK) {
        if (retrievalService == null) {
            return List.of();
        }

        // Trường hợp 1: Khách hàng hoặc client truyền rõ danh sách document_ids
        if (rawDocumentIds != null && !rawDocumentIds.isEmpty()) {
            List<UUID> documentIds = new ArrayList<>(rawDocumentIds.size());
            for (String rawId : rawDocumentIds) {
                if (rawId == null || rawId.isBlank()) {
                    throw new IllegalArgumentException("document_id không được để trống");
                }
                UUID parsed = UUID.fromString(rawId.strip());
                if (!documentIds.contains(parsed)) {
                    documentIds.add(parsed);
                }
            }
            try {
                List<ReciprocalRankFusion.FusedChunk> result =
                        retrievalService.retrieve(documentIds, question, topK);
                return result != null ? result : List.of();
            } catch (Exception e) {
                log.warn("Explicit document RAG retrieval failed ({}); falling back to pre-trained prompt context",
                        e.getMessage());
                return List.of();
            }
        }

        // Trường hợp 2: Không truyền document_ids -> Tự động lấy các tài liệu READY (nếu bật, có giới hạn số lượng)
        if (customerProps.isAutoDiscoverDocuments() && documentRepository != null) {
            try {
                int maxDocs = customerProps.getMaxAutoDiscoverDocuments() > 0
                        ? customerProps.getMaxAutoDiscoverDocuments()
                        : 20;
                List<UUID> readyDocIds = documentRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(doc -> doc != null && "READY".equalsIgnoreCase(doc.getStatus()) && doc.getId() != null)
                        .map(DocumentEntity::getId)
                        .distinct()
                        .limit(maxDocs)
                        .toList();
                if (!readyDocIds.isEmpty()) {
                    List<ReciprocalRankFusion.FusedChunk> result =
                            retrievalService.retrieve(readyDocIds, question, topK);
                    return result != null ? result : List.of();
                }
            } catch (Exception e) {
                log.warn("Auto-discover READY documents skipped ({}); falling back to pre-trained prompt context",
                        e.getMessage());
            }
        }

        return List.of();
    }

    private String buildPretrainedFallbackAnswer(CustomerChatRequest request,
                                                 CustomerScenario scenario,
                                                 List<ReciprocalRankFusion.FusedChunk> fused) {
        String customerName = (request.customerName() != null && !request.customerName().isBlank())
                ? request.customerName().strip()
                : "Anh/Chị";
        String storeName = (customerProps.getStoreName() != null && !customerProps.getStoreName().isBlank())
                ? customerProps.getStoreName().strip()
                : "DocMind Store & Digital Services";

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ em chào ").append(customerName).append("! Cảm ơn ").append(customerName)
          .append(" đã quan tâm đến sản phẩm và dịch vụ của **").append(storeName).append("**.\n\n");
        sb.append("Về câu hỏi của ").append(customerName).append(" (\"")
          .append(request.resolvedQuestion()).append("\") thuộc kịch bản **")
          .append(scenario.title()).append("**, em xin phép thông tin chi tiết như sau:\n\n");

        if (fused != null && !fused.isEmpty()) {
            int idx = 1;
            for (ReciprocalRankFusion.FusedChunk fc : fused) {
                sb.append("- ").append(snippet(fc.chunk().text())).append(" [").append(idx++).append("]\n");
            }
            sb.append("\n");
        }

        if (customerProps.getDefaultPolicyContext() != null && !customerProps.getDefaultPolicyContext().isBlank()) {
            sb.append("**Chính sách áp dụng tại ").append(storeName).append(":**\n")
              .append(customerProps.getDefaultPolicyContext().strip()).append("\n\n");
        }

        sb.append("Nếu cần hỗ trợ trực tiếp hoặc đặt hàng nhanh, ").append(customerName)
          .append(" vui lòng liên hệ Hotline **").append(customerProps.getHotline())
          .append("** hoặc Email **").append(customerProps.getSupportEmail())
          .append("** (").append(customerProps.getWorkingHours()).append(") nhé ạ!");
        return sb.toString();
    }

    private static List<Double> toBboxList(double[] bbox) {
        if (bbox == null) {
            return List.of();
        }
        return Arrays.stream(bbox).boxed().toList();
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= SNIPPET_MAX ? t : t.substring(0, SNIPPET_MAX) + "…";
    }
}
