package com.example.docmind.service;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.EvolvedExampleInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.LearnedGuidelineInfo;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.dto.CustomerPromptTemplateResponse;
import com.example.docmind.entity.DocumentEntity;
import com.example.docmind.repository.DocumentRepository;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
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
 * bằng bộ prompt được huấn luyện trước ({@link CustomerPromptBuilder}) kết hợp chiến lược
 * tự tiến hoá học hỏi sau các lần chat ({@link CustomerSelfEvolutionService} - EvoAgentX).
 *
 * <p>Kết hợp:
 * <ol>
 *   <li>Nhận diện kịch bản khách hàng (Mua hàng, Sử dụng dịch vụ, Bảo hành/Đổi trả, Đơn hàng/Thanh toán).</li>
 *   <li>Hybrid RAG retrieval trên tài liệu chỉ định hoặc tự động lấy các tài liệu READY trong hệ thống.</li>
 *   <li>Truy xuất các quy tắc đã học (Learned Guidelines) và mẫu hội thoại tiến hoá (Evolved Few-Shots) từ EvoAgentX engine.</li>
 *   <li>Dựng prompt được huấn luyện trước + tri thức tự tiến hoá + RAG Context.</li>
 *   <li>Gọi LLM (9Router Codex GPT-6 Astra mặc định, hoặc Gemini/Stub), kiểm tra citations và thực thi bước tự tiến hoá sau chat.</li>
 * </ol>
 */
@Service
public class CustomerChatService {

    private static final Logger log = LoggerFactory.getLogger(CustomerChatService.class);

    private final RetrievalService retrievalService;
    private final DocumentRepository documentRepository;
    private final LlmClient llmClient;
    private final CustomerSupportProperties customerProps;
    private final CustomerSelfEvolutionService evolutionService;

    /**
     * Constructor 4 tham số giữ tương thích ngược cho các unit test hiện hữu.
     */
    public CustomerChatService(RetrievalService retrievalService,
                               DocumentRepository documentRepository,
                               LlmClient llmClient,
                               CustomerSupportProperties customerProps) {
        this(retrievalService, documentRepository, llmClient, customerProps,
                new CustomerSelfEvolutionService(customerProps, llmClient));
    }

    @Autowired
    public CustomerChatService(RetrievalService retrievalService,
                               DocumentRepository documentRepository,
                               LlmClient llmClient,
                               CustomerSupportProperties customerProps,
                               CustomerSelfEvolutionService evolutionService) {
        this.retrievalService = retrievalService;
        this.documentRepository = documentRepository;
        this.llmClient = llmClient;
        this.customerProps = customerProps != null ? customerProps : new CustomerSupportProperties();
        this.evolutionService = evolutionService != null
                ? evolutionService
                : new CustomerSelfEvolutionService(this.customerProps, llmClient);
    }

    public CustomerChatResponse chat(CustomerChatRequest request) {
        if (request == null || !request.isQuestionProvided()) {
            throw new IllegalArgumentException("question hoặc message của khách hàng không được để trống");
        }
        int minTopK = customerProps.getMinTopK() > 0 ? customerProps.getMinTopK() : 1;
        int maxTopK = customerProps.getMaxTopK() >= minTopK ? customerProps.getMaxTopK() : 50;
        if (request.topK() != null && (request.topK() < minTopK || request.topK() > maxTopK)) {
            throw new IllegalArgumentException(
                    "top_k phải nằm trong khoảng từ " + minTopK + " đến " + maxTopK);
        }

        String question = request.resolvedQuestion();
        CustomerScenario scenario = CustomerScenario.resolve(request.scenario(), question, customerProps);
        List<CustomerScenario> secondaryScenarios =
                CustomerScenario.detectSecondaryScenarios(question, scenario, customerProps);

        String defaultRole = (customerProps.getDefaultCustomerRole() != null
                && !customerProps.getDefaultCustomerRole().isBlank())
                ? customerProps.getDefaultCustomerRole().strip()
                : CustomerPromptBuilder.DEFAULT_CUSTOMER_ROLE;
        String customerRole = (request.customerRole() != null && !request.customerRole().isBlank())
                ? request.customerRole().strip()
                : defaultRole;

        int defaultTopK = (customerProps.getDefaultTopK() >= minTopK && customerProps.getDefaultTopK() <= maxTopK)
                ? customerProps.getDefaultTopK()
                : 5;
        int topK = request.topK() != null ? request.topK() : defaultTopK;

        // 1) Truy xuất ngữ cảnh tài liệu (nếu khách hàng truyền document_ids hoặc tự động tìm tài liệu READY).
        List<ReciprocalRankFusion.FusedChunk> fused = retrieveChunksForCustomer(request.documentIds(), question, topK);

        // 2) Lấy tri thức đã tiến hoá từ EvoAgentX Self-Evolution Engine cho kịch bản hiện tại.
        List<LearnedGuidelineInfo> activeGuidelines =
                evolutionService.getActiveGuidelinesForScenario(scenario);
        List<EvolvedExampleInfo> activeExamples =
                evolutionService.getActiveExamplesForScenario(scenario);

        // 3) Dựng prompt được huấn luyện trước + tri thức tự tiến hoá.
        String prompt = CustomerPromptBuilder.build(
                request, scenario, customerProps, fused, activeGuidelines, activeExamples);
        String answer = llmClient != null ? llmClient.complete(prompt) : null;
        if (answer == null || answer.isBlank() || answer.strip().startsWith("[9router-error]")) {
            log.warn("LLM returned empty or error response; generating pre-trained customer fallback for scenario={}",
                    scenario.code());
            answer = buildPretrainedFallbackAnswer(request, scenario, fused);
        }
        String modelName = llmClient != null ? llmClient.name() : "pretrained-fallback";
        log.info("stage=customer-chat engine={} scenario={} fused_chunks={} applied_guidelines={}",
                modelName, scenario.code(), fused.size(), activeGuidelines.size());

        // 4) Dựng citations truy ngược về chunk/document/block/page/bbox/heading_path.
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

        // 5) Citation-validate: đảm bảo mọi citation đều thuộc tập chunk thực sự được retrieve.
        long invalid = citations.stream()
                .filter(cit -> !retrievedIds.contains(UUID.fromString(cit.chunkId())))
                .count();
        if (invalid > 0) {
            citations.removeIf(cit -> !retrievedIds.contains(UUID.fromString(cit.chunkId())));
        }

        // 6) Thực thi chiến lược tự tiến hoá học hỏi sau lần chat (EvoAgentX TextGrad & MIPRO).
        String interactionId = evolutionService.recordAndEvolveAfterChat(
                request, scenario, secondaryScenarios, answer, fused, modelName);

        return new CustomerChatResponse(
                answer,
                customerRole,
                scenario.code(),
                customerProps.getPromptTemplateId(),
                modelName,
                scenario.suggestedQuestions(customerProps),
                citations,
                interactionId,
                evolutionService.getCurrentGeneration(),
                activeGuidelines.size()
        );
    }

    /**
     * Trả về thông tin cấu hình bộ prompt được huấn luyện trước và danh sách các kịch bản hỗ trợ khách hàng.
     */
    public CustomerPromptTemplateResponse getPromptTemplates() {
        String sampleQuestion = (customerProps.getSamplePreviewQuestion() != null
                && !customerProps.getSamplePreviewQuestion().isBlank())
                ? customerProps.getSamplePreviewQuestion().strip()
                : "Tư vấn giúp tôi gói dịch vụ phù hợp và chính sách bảo hành khi mua hàng?";

        CustomerChatRequest sampleReq = new CustomerChatRequest(
                sampleQuestion,
                CustomerScenario.SALES.code(),
                null,
                customerProps.getDefaultTopK()
        );
        List<LearnedGuidelineInfo> salesGuidelines =
                evolutionService.getActiveGuidelinesForScenario(CustomerScenario.SALES);
        List<EvolvedExampleInfo> salesExamples =
                evolutionService.getActiveExamplesForScenario(CustomerScenario.SALES);
        String preview = CustomerPromptBuilder.build(
                sampleReq, CustomerScenario.SALES, customerProps, List.of(), salesGuidelines, salesExamples);

        String defaultRole = (customerProps.getDefaultCustomerRole() != null
                && !customerProps.getDefaultCustomerRole().isBlank())
                ? customerProps.getDefaultCustomerRole().strip()
                : CustomerPromptBuilder.DEFAULT_CUSTOMER_ROLE;
        String persona = (customerProps.getAssistantPersona() != null
                && !customerProps.getAssistantPersona().isBlank())
                ? customerProps.getAssistantPersona().strip()
                : CustomerPromptBuilder.ASSISTANT_PERSONA;

        return new CustomerPromptTemplateResponse(
                customerProps.getPromptTemplateId(),
                customerProps.getStoreName(),
                defaultRole,
                persona,
                CustomerScenario.allScenarioInfos(customerProps),
                preview
        );
    }

    /**
     * Tiếp nhận phản hồi từ khách hàng (rating, helpful, comment/correction) để cập nhật bộ nhớ tiến hoá.
     */
    public CustomerEvolutionStatusResponse submitFeedback(CustomerFeedbackRequest feedbackRequest) {
        return evolutionService.recordFeedbackAndEvolve(feedbackRequest);
    }

    /**
     * Kích hoạt chủ động 1 chu kỳ tối ưu hoá EvoAgentX TextGrad + MIPRO.
     */
    public CustomerEvolutionStatusResponse triggerEvolution(String scenario) {
        return evolutionService.triggerEvolutionCycle(scenario);
    }

    /**
     * Lấy trạng thái hiện tại của động cơ tự tiến hoá EvoAgentX và danh sách quy tắc đã học.
     */
    public CustomerEvolutionStatusResponse getEvolutionStatus() {
        return evolutionService.getStatus();
    }

    /**
     * Đặt lại bộ nhớ tự tiến hoá về trạng thái khởi đầu.
     */
    public CustomerEvolutionStatusResponse resetEvolution() {
        return evolutionService.resetEvolutionMemory();
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
        String defaultSalutation = (customerProps.getFallbackCustomerSalutation() != null
                && !customerProps.getFallbackCustomerSalutation().isBlank())
                ? customerProps.getFallbackCustomerSalutation().strip()
                : "Anh/Chị";
        String customerName = (request.customerName() != null && !request.customerName().isBlank())
                ? request.customerName().strip()
                : defaultSalutation;
        String storeName = (customerProps.getStoreName() != null && !customerProps.getStoreName().isBlank())
                ? customerProps.getStoreName().strip()
                : "DocMind Store & Digital Services";

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ em chào ").append(customerName).append("! Cảm ơn ").append(customerName)
          .append(" đã quan tâm đến sản phẩm và dịch vụ của **").append(storeName).append("**.\n\n");
        sb.append("Về câu hỏi của ").append(customerName).append(" (\"")
          .append(request.resolvedQuestion()).append("\") thuộc kịch bản **")
          .append(scenario.title(customerProps)).append("**, em xin phép thông tin chi tiết như sau:\n\n");

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

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        int maxLen = customerProps.getSnippetMaxLength() > 0 ? customerProps.getSnippetMaxLength() : 240;
        String t = text.strip();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "…";
    }
}
