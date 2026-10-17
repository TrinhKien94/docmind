package com.example.docmind.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cấu trúc dữ liệu gói tri thức miền (Domain Knowledge & Policy Guardrail Pack) được ngoại hoá
 * hoàn toàn sang tệp JSON ({@code domain-knowledge-pack.json}) hoặc {@code application.properties}.
 *
 * <p>Giúp tách biệt hoàn toàn thuật toán lõi của động cơ tự tiến hoá EvoAgentX
 * ({@code CustomerSelfEvolutionService}, {@code CustomerChatService}, {@code CustomerScenario})
 * khỏi dữ liệu đặc thù của bất kỳ ngành nghề/thương hiệu đơn lẻ nào.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainKnowledgePack {

    private static final Logger log = LoggerFactory.getLogger(DomainKnowledgePack.class);
    private static final String DEFAULT_RESOURCE_PATH = "domain-knowledge-pack.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile DomainKnowledgePack cachedDefaultPack;

    @JsonProperty("pack_id")
    private String packId = "domain-agnostic-default";

    @JsonProperty("description")
    private String description = "";

    @JsonProperty("seed_query_expansions")
    private Map<String, String> seedQueryExpansions = new LinkedHashMap<>();

    @JsonProperty("policy_guardrails")
    private List<PolicyGuardrailRule> policyGuardrails = new ArrayList<>();

    @JsonProperty("scenario_keywords")
    private Map<String, ScenarioKeywordExtension> scenarioKeywords = new LinkedHashMap<>();

    @JsonProperty("domain_alignment_rules")
    private List<KnowledgeBaseProperties.DomainAlignmentRule> domainAlignmentRules = new ArrayList<>();

    public static DomainKnowledgePack empty() {
        return new DomainKnowledgePack();
    }

    public static DomainKnowledgePack loadDefault() {
        DomainKnowledgePack local = cachedDefaultPack;
        if (local != null) {
            return local;
        }
        synchronized (DomainKnowledgePack.class) {
            if (cachedDefaultPack == null) {
                cachedDefaultPack = loadFromPathOrResource(DEFAULT_RESOURCE_PATH);
            }
            return cachedDefaultPack;
        }
    }

    public static DomainKnowledgePack loadFromPathOrResource(String pathOrResource) {
        if (pathOrResource == null || pathOrResource.isBlank()) {
            return empty();
        }
        String cleaned = pathOrResource.strip().replace('\\', '/');
        try {
            Path fileCandidate = Path.of(cleaned);
            if (Files.isRegularFile(fileCandidate)) {
                return MAPPER.readValue(fileCandidate.toFile(), DomainKnowledgePack.class);
            }
            String resName = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
            try (InputStream is = DomainKnowledgePack.class.getClassLoader().getResourceAsStream(resName)) {
                if (is != null) {
                    return MAPPER.readValue(is, DomainKnowledgePack.class);
                }
            }
        } catch (Exception e) {
            log.warn("Không thể đọc gói tri thức miền từ {}: {}", pathOrResource, e.getMessage());
        }
        return empty();
    }

    /**
     * Một quy tắc rào chắn chính sách / an toàn / kiểm duyệt tự động (Policy Guardrail Rule)
     * dùng cho cả bộ chấm điểm TextGrad lẫn vòng lặp tự phản biện AFlow Self-Critic.
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolicyGuardrailRule {

        @JsonProperty("id")
        private String id = "";

        @JsonProperty("high_risk")
        private boolean highRisk = false;

        @JsonProperty("evaluate_on_current_question_only")
        private boolean evaluateOnCurrentQuestionOnly = false;

        @JsonProperty("question_triggers")
        private List<String> questionTriggers = new ArrayList<>();

        @JsonProperty("secondary_triggers")
        private List<String> secondaryTriggers = new ArrayList<>();

        @JsonProperty("required_answer_keywords")
        private List<String> requiredAnswerKeywords = new ArrayList<>();

        @JsonProperty("critique_message")
        private String critiqueMessage = "";

        @JsonProperty("gradient_instruction")
        private String gradientInstruction = "";

        @JsonProperty("revision_guidance")
        private String revisionGuidance = "";

        public PolicyGuardrailRule() {
        }

        public PolicyGuardrailRule(String id,
                                   boolean highRisk,
                                   boolean evaluateOnCurrentQuestionOnly,
                                   List<String> questionTriggers,
                                   List<String> secondaryTriggers,
                                   List<String> requiredAnswerKeywords,
                                   String critiqueMessage,
                                   String gradientInstruction,
                                   String revisionGuidance) {
            this.id = id;
            this.highRisk = highRisk;
            this.evaluateOnCurrentQuestionOnly = evaluateOnCurrentQuestionOnly;
            this.questionTriggers = questionTriggers != null ? new ArrayList<>(questionTriggers) : new ArrayList<>();
            this.secondaryTriggers = secondaryTriggers != null ? new ArrayList<>(secondaryTriggers) : new ArrayList<>();
            this.requiredAnswerKeywords = requiredAnswerKeywords != null ? new ArrayList<>(requiredAnswerKeywords) : new ArrayList<>();
            this.critiqueMessage = critiqueMessage;
            this.gradientInstruction = gradientInstruction;
            this.revisionGuidance = revisionGuidance;
        }
    }

    /**
     * Từ khoá mở rộng theo miền cho từng kịch bản (SALES, SERVICE_SUPPORT, WARRANTY_RETURN, ORDER_BILLING).
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioKeywordExtension {

        @JsonProperty("high_keywords")
        private List<String> highKeywords = new ArrayList<>();

        @JsonProperty("medium_keywords")
        private List<String> mediumKeywords = new ArrayList<>();
    }
}
