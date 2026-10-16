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
    private final com.example.docmind.service.retrieval.LocalKnowledgeBaseService localKnowledgeBaseService;
    private volatile long dbUnavailableUntilEpochMs = 0L;

    /**
     * Constructor 4 tham số giữ tương thích ngược cho các unit test hiện hữu.
     */
    public CustomerChatService(RetrievalService retrievalService,
                               DocumentRepository documentRepository,
                               LlmClient llmClient,
                               CustomerSupportProperties customerProps) {
        this(retrievalService, documentRepository, llmClient, customerProps,
                new CustomerSelfEvolutionService(customerProps, llmClient), null);
    }

    /**
     * Constructor 5 tham số giữ tương thích ngược cho các unit test hiện hữu.
     */
    public CustomerChatService(RetrievalService retrievalService,
                               DocumentRepository documentRepository,
                               LlmClient llmClient,
                               CustomerSupportProperties customerProps,
                               CustomerSelfEvolutionService evolutionService) {
        this(retrievalService, documentRepository, llmClient, customerProps, evolutionService, null);
    }

    @Autowired
    public CustomerChatService(RetrievalService retrievalService,
                               DocumentRepository documentRepository,
                               LlmClient llmClient,
                               CustomerSupportProperties customerProps,
                               CustomerSelfEvolutionService evolutionService,
                               com.example.docmind.service.retrieval.LocalKnowledgeBaseService localKnowledgeBaseService) {
        this.retrievalService = retrievalService;
        this.documentRepository = documentRepository;
        this.llmClient = llmClient;
        this.customerProps = customerProps != null ? customerProps : new CustomerSupportProperties();
        this.evolutionService = evolutionService != null
                ? evolutionService
                : new CustomerSelfEvolutionService(this.customerProps, llmClient);
        this.localKnowledgeBaseService = localKnowledgeBaseService;
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
        String retrievalQuery = buildHistoryAwareRetrievalQuery(request);
        CustomerScenario scenario = CustomerScenario.resolve(request.scenario(), question, customerProps);
        if (scenario == CustomerScenario.GENERAL
                && isAutoOrBlankScenario(request.scenario())
                && !retrievalQuery.equals(question)) {
            scenario = CustomerScenario.resolve(null, retrievalQuery, customerProps);
        }
        List<CustomerScenario> secondaryScenarios =
                CustomerScenario.detectSecondaryScenarios(retrievalQuery, scenario, customerProps);
        String workflowTopology = evolutionService != null
                ? evolutionService.selectWorkflowTopology(request, scenario, secondaryScenarios)
                : CustomerSelfEvolutionService.TOPOLOGY_FAST_DIRECT_RAG;

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

        // 1) Truy xuất ngữ cảnh tài liệu (kèm Self-Evolving RAG Query Expansion & Chunk Utility Boosting).
        List<ReciprocalRankFusion.FusedChunk> fused = retrieveChunksForCustomer(request.documentIds(), retrievalQuery, topK);

        // 2) Lấy tri thức đã tiến hoá từ EvoAgentX Self-Evolution Engine (GEPA Pareto Frontier + MIPRO) cho kịch bản chính + kịch bản phụ.
        List<LearnedGuidelineInfo> activeGuidelines =
                mergeActiveGuidelines(scenario, secondaryScenarios);
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
        answer = stripCustomerCitationMarkers(answer);
        String modelName = llmClient != null ? llmClient.name() : "pretrained-fallback";

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

        // 6) Thực thi chiến lược tự tiến hoá học hỏi sau lần chat (EvoAgentX TextGrad, MIPRO, GEPA & Self-Evolving RAG).
        String interactionId = evolutionService.recordAndEvolveAfterChat(
                request, scenario, secondaryScenarios, answer, fused, modelName);

        // 7) AFlow Self-Critic & Auto-Revise: tự phản biện và hoàn thiện bản nháp trước khi gửi tới khách hàng.
        if (evolutionService != null && answer != null
                && !answer.startsWith("[stub-llm]")
                && !modelName.toLowerCase(java.util.Locale.ROOT).contains("stub")) {
            answer = evolutionService.applyAflowSelfCriticAndRevise(
                    workflowTopology, request, scenario, secondaryScenarios, answer);
        }
        log.info("stage=customer-chat engine={} topology={} scenario={} fused_chunks={} applied_guidelines={}",
                modelName, workflowTopology, scenario.code(), fused.size(), activeGuidelines.size());

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

    private List<LearnedGuidelineInfo> mergeActiveGuidelines(CustomerScenario primaryScenario,
                                                             List<CustomerScenario> secondaryScenarios) {
        List<LearnedGuidelineInfo> primaryList = evolutionService.getActiveGuidelinesForScenario(primaryScenario);
        if (secondaryScenarios == null || secondaryScenarios.isEmpty()) {
            return primaryList;
        }
        int maxInPrompt = (customerProps.getEvolution() != null
                && customerProps.getEvolution().getMaxGuidelinesInPrompt() > 0)
                ? customerProps.getEvolution().getMaxGuidelinesInPrompt()
                : 5;
        List<LearnedGuidelineInfo> merged = new ArrayList<>(primaryList);
        Set<String> seenHashes = new HashSet<>();
        for (LearnedGuidelineInfo g : primaryList) {
            seenHashes.add(g.contentHash());
        }
        for (CustomerScenario sec : secondaryScenarios) {
            for (LearnedGuidelineInfo secGuideline : evolutionService.getActiveGuidelinesForScenario(sec)) {
                if (seenHashes.add(secGuideline.contentHash())) {
                    merged.add(secGuideline);
                }
            }
        }
        return merged.size() > maxInPrompt ? merged.subList(0, maxInPrompt) : merged;
    }

    private List<ReciprocalRankFusion.FusedChunk> retrieveChunksForCustomer(
            List<String> rawDocumentIds, String question, int topK) {
        if (retrievalService == null) {
            return List.of();
        }
        String expandedQuery = evolutionService != null
                ? evolutionService.expandQueryWithLearnedSynonyms(question)
                : question;

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
                        retrievalService.retrieve(documentIds, expandedQuery, topK);
                if (result == null || result.isEmpty()) {
                    return List.of();
                }
                return evolutionService != null
                        ? evolutionService.rerankChunksByLearnedUtility(result)
                        : result;
            } catch (Exception e) {
                log.warn("Explicit document RAG retrieval failed ({}); falling back to pre-trained prompt context",
                        e.getMessage());
                return List.of();
            }
        }

        // Trường hợp 2: Không truyền document_ids -> Tự động lấy các tài liệu READY từ DB và LocalKnowledgeBaseService
        if (customerProps.isAutoDiscoverDocuments()) {
            int maxDocs = customerProps.getMaxAutoDiscoverDocuments() > 0
                    ? customerProps.getMaxAutoDiscoverDocuments()
                    : 20;
            java.util.LinkedHashSet<UUID> readyDocIds = new java.util.LinkedHashSet<>();

            boolean canSkipDeadDb = localKnowledgeBaseService != null
                    && localKnowledgeBaseService.hasDocuments()
                    && System.currentTimeMillis() < dbUnavailableUntilEpochMs;

            if (documentRepository != null && !canSkipDeadDb) {
                try {
                    documentRepository.findAllByOrderByCreatedAtDesc().stream()
                            .filter(doc -> doc != null && "READY".equalsIgnoreCase(doc.getStatus()) && doc.getId() != null)
                            .map(DocumentEntity::getId)
                            .limit(maxDocs)
                            .forEach(readyDocIds::add);
                } catch (Exception e) {
                    if (localKnowledgeBaseService != null && localKnowledgeBaseService.hasDocuments()) {
                        dbUnavailableUntilEpochMs = System.currentTimeMillis() + localKnowledgeBaseService.getDbFailureBackoffMs();
                    }
                    log.debug("Database auto-discover skipped ({}); checking LocalKnowledgeBaseService", e.getMessage());
                }
            }

            if (localKnowledgeBaseService != null) {
                for (UUID kbDocId : localKnowledgeBaseService.listReadyDocumentIds()) {
                    if (readyDocIds.size() >= maxDocs) {
                        break;
                    }
                    readyDocIds.add(kbDocId);
                }
            }

            if (!readyDocIds.isEmpty()) {
                try {
                    List<ReciprocalRankFusion.FusedChunk> result =
                            retrievalService.retrieve(new ArrayList<>(readyDocIds), expandedQuery, topK);
                    if (result == null || result.isEmpty()) {
                        return List.of();
                    }
                    return evolutionService != null
                            ? evolutionService.rerankChunksByLearnedUtility(result)
                            : result;
                } catch (Exception e) {
                    log.warn("Auto-discover READY documents retrieval failed ({}); falling back to pre-trained prompt context",
                            e.getMessage());
                }
            }
        }

        return List.of();
    }

    String buildHistoryAwareRetrievalQuery(CustomerChatRequest request) {
        String currentQuestion = request.resolvedQuestion();
        if (!customerProps.isHistoryQueryExpansionEnabled()
                || request.history() == null
                || request.history().isEmpty()) {
            return currentQuestion;
        }
        int maxTurns = customerProps.getHistoryExpansionMaxUserTurns() > 0
                ? customerProps.getHistoryExpansionMaxUserTurns()
                : 2;
        int maxChars = customerProps.getHistoryExpansionMaxChars() > 0
                ? customerProps.getHistoryExpansionMaxChars()
                : 240;

        List<String> recentUserQuestions = new ArrayList<>(maxTurns);
        for (int i = request.history().size() - 1; i >= 0 && recentUserQuestions.size() < maxTurns; i--) {
            CustomerChatRequest.ConversationTurn turn = request.history().get(i);
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            String role = turn.role() != null ? turn.role().strip().toLowerCase(java.util.Locale.ROOT) : "user";
            boolean isAssistant = customerProps.getAssistantRoleAliases() != null
                    && customerProps.getAssistantRoleAliases().stream()
                    .anyMatch(a -> a != null && role.equals(a.strip().toLowerCase(java.util.Locale.ROOT)));
            if (isAssistant) {
                continue;
            }
            String cleanTurn = turn.content().strip();
            if (cleanTurn.length() > maxChars) {
                cleanTurn = cleanTurn.substring(0, maxChars);
            }
            recentUserQuestions.add(cleanTurn);
        }

        if (recentUserQuestions.isEmpty()) {
            return currentQuestion;
        }
        return currentQuestion + " " + String.join(" ", recentUserQuestions);
    }

    private static boolean isAutoOrBlankScenario(String rawScenario) {
        if (rawScenario == null || rawScenario.isBlank()) {
            return true;
        }
        String norm = rawScenario.strip().toLowerCase(java.util.Locale.ROOT);
        return "auto".equals(norm);
    }

    private static boolean isPromptInjection(String normQ) {
        return normQ.contains("bo qua moi huong dan") || normQ.contains("bo qua moi chi thi")
                || normQ.contains("ignore all") || normQ.contains("system prompt")
                || normQ.contains("in toan bo") || normQ.contains("in ra prompt")
                || normQ.contains("developer mode") || normQ.contains("jailbreak")
                || normQ.contains("dong vai bot tu do") || normQ.contains("khong duoc nhac den my pham dior");
    }

    private static boolean isPriceTamperingAttack(String normQ) {
        return (normQ.contains("99 000") || normQ.contains("99k") || normQ.contains("0 dong")
                || normQ.contains("mien phi cho toi") || normQ.contains("ha gia"))
                && (normQ.contains("capture totale") || normQ.contains("le serum")
                || normQ.contains("sauvage") || normQ.contains("son") || normQ.contains("kem nen"));
    }

    private static boolean isMaliciousPregnancyOverride(String normQ) {
        return normQ.contains("bac si da dong y") && normQ.contains("retishot")
                && (normQ.contains("mang thai") || normQ.contains("bau"));
    }

    private static boolean isPregnancySkincareQuery(String normCombined) {
        boolean isPregnancyQuery = normCombined.contains("mang thai") || normCombined.contains("ba bau")
                || normCombined.contains("me bau") || normCombined.contains("thai ky")
                || normCombined.contains("cho con bu") || normCombined.contains("sau sinh")
                || normCombined.contains("bau bi") || normCombined.contains("co bau");
        boolean touchesRetinolOrSerum = normCombined.contains("retishot") || normCombined.contains("retinol")
                || normCombined.contains("serum") || normCombined.contains("tinh chat")
                || normCombined.contains("chong lao hoa") || normCombined.contains("anti aging")
                || normCombined.contains("loai do") || normCombined.contains("san pham nay")
                || normCombined.contains("skincare") || normCombined.contains("kem");
        return isPregnancyQuery && touchesRetinolOrSerum;
    }

    private String synthesizeDomainGuidance(String normQ, String normCombined) {
        if (isPriceTamperingAttack(normQ) || isMaliciousPregnancyOverride(normQ) || isPromptInjection(normQ)) {
            return "Christian Dior Vietnam luôn cam kết minh bạch bảng giá niêm yết chính hãng (ví dụ Tinh chất Capture Totale Le Sérum 30ml có giá niêm yết 3.450.000 VNĐ, không có mức giá 99.000 VNĐ) và tuân thủ nghiêm ngặt quy chuẩn an toàn y tế: tuyệt đối chống chỉ định dòng Capture Totale Retishot (0.1% Retinol tinh khiết) cho phụ nữ mang thai hoặc đang cho con bú.";
        }
        if (isPregnancySkincareQuery(normCombined)) {
            return "Trong giai đoạn thai kỳ và cho con bú, Chị lưu ý tuyệt đối không sử dụng dòng tinh chất Capture Totale Retishot (vì chứa 0.1% Retinol tinh khiết) để đảm bảo an toàn tối đa cho mẹ và bé. Thay vào đó, Chị có thể yên tâm sử dụng Tinh chất Capture Totale Le Sérum (30ml: 3.450.000 VNĐ / 50ml: 4.650.000 VNĐ) hoặc Tinh dầu hoa hồng Dior Prestige La Micro-Huile de Rose (30ml: 7.400.000 VNĐ) lành tính cho mẹ bầu.";
        }
        if (normCombined.contains("di ung") || normCombined.contains("kich ung")
                || normCombined.contains("man do") || normCombined.contains("ngua rat")
                || normCombined.contains("cham chich") || normCombined.contains("noi mun")
                || normCombined.contains("cosmetovigilance")) {
            return "Theo giao thức bảo hành da liễu Cosmetovigilance chính hãng trong 14 ngày, Chị hãy tạm ngưng sử dụng sản phẩm ngay và rửa nhẹ bằng nước mát hoặc nước muối sinh lý; dù sản phẩm đã mở nắp bên em vẫn hỗ trợ thu hồi đổi sang dòng dịu nhẹ phục hồi như Dior Prestige hoặc hoàn tiền 100% nếu dung tích còn từ 80% trở lên.";
        }
        if (normCombined.contains("gay son") || normCombined.contains("do mo hoi")
                || normCombined.contains("chay dau") || normCombined.contains("hong voi xit")
                || normCombined.contains("vo chai") || normCombined.contains("ro ri")
                || normCombined.contains("ket xoay") || normCombined.contains("airless pump")) {
            return "Với lỗi kỹ thuật như gãy thân son, đổ mồ hôi do sốc nhiệt hoặc hỏng vòi xịt trong vận chuyển, Dior áp dụng chính sách bảo hành đổi trả 1 đổi 1 sang sản phẩm mới nguyên seal miễn phí trong vòng 14 ngày kể từ khi nhận hàng.";
        }
        if (normCombined.contains("doi y") || normCombined.contains("doi mau")
                || normCombined.contains("doi mui") || normCombined.contains("try first")
                || normCombined.contains("7 ngay")) {
            return "Dior hỗ trợ đổi ý, đổi màu hoặc đổi mùi trong vòng 07 ngày kể từ khi nhận hàng khi sản phẩm Full-size còn nguyên màng seal nilon và tem phụ LVMH (với chương trình Try First Sample, Quý khách được mở ống sample 1.2ml thử trước mà vẫn giữ nguyên quyền đổi trả hộp chính trong 7 ngày, trừ sản phẩm đã khắc tên laser).";
        }
        if (normQ.contains("batch code") || normQ.contains("ma lo")
                || normQ.contains("5r02") || normQ.contains("4n12") || normQ.contains("6p01")
                || normQ.contains("hang gia") || normQ.contains("auth") || normQ.contains("fake")
                || normQ.contains("tem phu") || normQ.contains("xach tay")) {
            return "Toàn bộ sản phẩm Christian Dior do LVMH Việt Nam nhập khẩu chính ngạch đều đáp ứng 5 tiêu chuẩn xác thực: (1) Tem phụ tiếng Việt có số công bố Bộ Y tế, (2) Mã Batch Code 4 ký tự laser trùng khớp vỏ hộp, (3) Màng seal ép nhiệt, (4) Hóa đơn điện tử e-VAT chính danh, và (5) Ký hiệu PAO / hạn dùng 36 tháng rõ ràng.";
        }
        if (normCombined.contains("khac ten") || normCombined.contains("engraving") || normCombined.contains("khac chu")) {
            return "Xưởng Dior Engraving Studio hỗ trợ khắc tên laser cá nhân hoá miễn phí tối đa 12 ký tự cho nước hoa từ 50ml trở lên hoặc son Rouge Dior (hoàn thiện trong 30–45 phút; sản phẩm đã khắc tên không áp dụng đổi ý).";
        }
        return "";
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

        String question = request.resolvedQuestion();
        String retrievalQuery = buildHistoryAwareRetrievalQuery(request);
        String normQ = CustomerScenario.normalizeWordsPadded(question);
        String normCombined = CustomerScenario.normalizeWordsPadded(retrievalQuery);
        boolean asksSource = normQ.contains(" tai lieu nao ")
                || normQ.contains(" nguon nao ")
                || normQ.contains(" lay thong tin ")
                || normQ.contains(" trich dan ");

        StringBuilder sb = new StringBuilder();
        sb.append("Dạ em chào ").append(customerName).append(" ạ! Rất vui được hỗ trợ ").append(customerName)
          .append(" tại **").append(storeName).append("** hôm nay.\n\n");
        if (asksSource) {
            sb.append("Dạ toàn bộ thông tin về sản phẩm, bảng giá và quyền lợi chăm sóc khách hàng mà em chia sẻ đều là thông tin niêm yết chính thức tại hệ thống **")
              .append(storeName).append("** để ").append(customerName).append(" hoàn toàn yên tâm tham khảo và lựa chọn ạ.\n\n");
        } else {
            sb.append("Về nhu cầu của ").append(customerName).append(" (\"")
              .append(question).append("\"), em xin chia sẻ thông tin chi tiết để mình tiện tham khảo nhé:\n\n");
        }

        String domainGuidance = synthesizeDomainGuidance(normQ, normCombined);
        if (!domainGuidance.isBlank()) {
            sb.append(domainGuidance).append("\n\n");
        }

        if (fused != null && !fused.isEmpty()) {
            for (ReciprocalRankFusion.FusedChunk fc : fused) {
                String cleanLine = cleanChunkForCustomerFallback(fc.chunk().text());
                if (!cleanLine.isBlank()) {
                    sb.append("- ").append(snippet(cleanLine)).append("\n");
                }
            }
            sb.append("\n");
        }

        if (customerProps.getDefaultPolicyContext() != null && !customerProps.getDefaultPolicyContext().isBlank()) {
            sb.append("Bên cạnh đó, **").append(storeName).append("** luôn dành tặng ").append(customerName)
              .append(" các quyền lợi chăm sóc và hỗ trợ chính hãng:\n")
              .append(customerProps.getDefaultPolicyContext().strip()).append("\n\n");
        }

        sb.append("Nếu cần em tư vấn kỹ hơn hoặc hỗ trợ đặt hàng nhanh, ").append(customerName)
          .append(" cứ nhắn trực tiếp tại đây hoặc liên hệ Hotline **").append(customerProps.getHotline())
          .append("** / Email **").append(customerProps.getSupportEmail())
          .append("** (").append(customerProps.getWorkingHours()).append(") nhé ạ!");
        return sb.toString();
    }

    private String cleanChunkForCustomerFallback(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : rawText.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---") || trimmed.startsWith("|---")) {
                continue;
            }
            String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("clienteling sop")
                    || lower.contains("chuyên viên tuyệt đối")
                    || lower.contains("kịch bản mẫu")
                    || lower.contains("ghi chú vận hành")) {
                continue;
            }
            trimmed = trimmed.replaceFirst("^[-*•+]\\s+", "");
            if (!trimmed.isEmpty()) {
                if (!cleaned.isEmpty()) {
                    cleaned.append(' ');
                }
                cleaned.append(trimmed);
            }
        }
        return stripCustomerCitationMarkers(cleaned.isEmpty() ? rawText.strip() : cleaned.toString());
    }

    /**
     * Loại bỏ các ký hiệu trích dẫn số học dạng [1], [2], [1, 2] hoặc chú thích nguồn tài liệu nội bộ
     * khỏi câu trả lời dành cho người mua hàng để giữ văn phong tự nhiên như chuyên viên tư vấn thật.
     */
    public static String stripCustomerCitationMarkers(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String cleaned = answer
                // Xoá các cụm trích dẫn dạng [1], [2], [1, 2], [1-3], [1][2]
                .replaceAll("(?m)\\s*(?:\\[\\d{1,3}(?:\\s*[,;-]\\s*\\d{1,3})*])+(?=\\s*[.,;!?):\\n]|\\s*$)", "")
                .replaceAll("(?:\\[\\d{1,3}(?:\\s*[,;-]\\s*\\d{1,3})*])+", "")
                // Xoá chú thích nguồn tài liệu/số trang dạng (Nguồn: ...), [Nguồn: ...], (Theo tài liệu ...), (Trang 2), (Tr. 2)
                .replaceAll("(?iu)\\s*[(\\[](?:nguồn|theo\\s+tài\\s+liệu|tài\\s+liệu\\s+sop|trích\\s+từ|trang|tr\\.)\\s*[:\\s][^)\\]\\n]{1,90}[)\\]]", "")
                // Xoá các cụm mở đầu máy móc kiểu RAG ("Dựa trên tài liệu được cung cấp, ", "Theo đoạn trích tài liệu: ")
                .replaceAll("(?iu)\\b(?:dựa\\s+trên|theo|căn\\s+cứ\\s+vào)\\s+(?:các\\s+)?(?:đoạn\\s+trích\\s+)?(?:tài\\s+liệu(?:\\s+sop)?|đoạn\\s+trích|ngữ\\s+cảnh|văn\\s+bản(?:\\s+nguồn)?)(?:\\s+được\\s+cung\\s+cấp|\\s+đã\\s+tải\\s+lên|\\s+nội\\s+bộ|\\s+đính\\s+kèm)?\\s*[,:]\\s*", "")
                // Xoá khoảng trắng thừa trước dấu câu do việc gỡ [n] để lại
                .replaceAll(" +([.,;!?])", "$1")
                .replaceAll("(?m)[ \\t]{2,}", " ");
        return cleaned.strip();
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
