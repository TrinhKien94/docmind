package com.example.docmind.service.evolution;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.controller.CustomerChatController;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.EvolvedExampleInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.LearnedGuidelineInfo;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.service.CustomerChatService;
import com.example.docmind.service.FileValidation;
import com.example.docmind.service.retrieval.CustomerScenario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CustomerEvolutionMultiPathAndIsolationChallengerTest — Empirical Challenger Stress Tests")
class CustomerEvolutionMultiPathAndIsolationChallengerTest {

    // =========================================================================
    // 1. MULTI-PATH RESOLUTION PROBES
    // =========================================================================

    @Test
    @DisplayName("Challenger Probe 1.1: Multi-path resolution across root, module, relative and slash variations")
    void multiPathProbe_resolvesAcrossPathVariations() {
        CustomerSupportProperties props = new CustomerSupportProperties();
        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);

        // Path candidate 1: standard root relative path
        Path p1 = service.resolveExistingStoragePath("data/customer-evolution-memory.json");
        assertThat(p1).isNotNull();
        assertThat(Files.isRegularFile(p1)).isTrue();

        // Path candidate 2: module relative path
        Path p2 = service.resolveExistingStoragePath("api-java/data/customer-evolution-memory.json");
        assertThat(p2).isNotNull();
        assertThat(Files.isRegularFile(p2)).isTrue();

        // Path candidate 3: explicit dot-slash relative
        Path p3 = service.resolveExistingStoragePath("./data/customer-evolution-memory.json");
        assertThat(p3).isNotNull();
        assertThat(Files.isRegularFile(p3)).isTrue();

        // Path candidate 4: Windows backslash notation
        Path p4 = service.resolveExistingStoragePath("data\\customer-evolution-memory.json");
        assertThat(p4).isNotNull();
        assertThat(Files.isRegularFile(p4)).isTrue();

        // Path candidate 5: Padded with whitespace
        Path p5 = service.resolveExistingStoragePath("   data/customer-evolution-memory.json   ");
        assertThat(p5).isNotNull();
        assertThat(Files.isRegularFile(p5)).isTrue();

        // Candidate 6: Parent directory reference
        Path p6 = service.resolveExistingStoragePath("../api-java/data/customer-evolution-memory.json");
        assertThat(p6).isNotNull();
        assertThat(Files.isRegularFile(p6)).isTrue();
    }

    @Test
    @DisplayName("Challenger Probe 1.2: Boundary inputs (directories, non-existent files, blank) return null safely")
    void multiPathProbe_boundaryInputsReturnNullSafely(@TempDir Path tempDir) {
        CustomerSupportProperties props = new CustomerSupportProperties();
        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);

        // Blank and null
        assertThat(service.resolveExistingStoragePath(null)).isNull();
        assertThat(service.resolveExistingStoragePath("")).isNull();
        assertThat(service.resolveExistingStoragePath("    \t\n  ")).isNull();

        // Non-existent relative file
        assertThat(service.resolveExistingStoragePath("data/non-existent-probe-12345.json")).isNull();
        assertThat(service.resolveExistingStoragePath("api-java/data/non-existent-probe-12345.json")).isNull();

        // Directory paths must NOT be resolved as regular file
        assertThat(service.resolveExistingStoragePath("data")).isNull();
        assertThat(service.resolveExistingStoragePath("data/")).isNull();
        assertThat(service.resolveExistingStoragePath("api-java/data")).isNull();

        // Absolute non-existent path
        Path absNonExistent = tempDir.resolve("ghost-memory.json").toAbsolutePath();
        assertThat(service.resolveExistingStoragePath(absNonExistent.toString())).isNull();
    }

    @Test
    @DisplayName("Challenger Probe 1.3: resolveWritableStoragePath preserves absolute temp paths without pollution")
    void multiPathProbe_writablePathPreservesAbsoluteTempPaths(@TempDir Path tempDir) {
        CustomerSupportProperties props = new CustomerSupportProperties();
        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);

        Path absTempTarget = tempDir.resolve("isolated-write.json").toAbsolutePath();
        Path resolved = service.resolveWritableStoragePath(absTempTarget.toString());

        assertThat(resolved).isEqualTo(absTempTarget.normalize());
        assertThat(resolved.isAbsolute()).isTrue();
        assertThat(resolved.toString()).doesNotContain("api-java" + File.separator + "api-java");
    }

    // =========================================================================
    // 2. TEMP ISOLATION PROBES (NO POLLUTION & NO FALLBACK)
    // =========================================================================

    @Test
    @DisplayName("Challenger Probe 2.1: Non-existent absolute TempDir path NEVER falls back to persistent disk files")
    void tempIsolation_nonExistentAbsoluteTempDirNeverFallsBackToPersistentDisk(@TempDir Path tempDir) {
        Path absNonExistent = tempDir.resolve("clean-isolated-nonexistent.json").toAbsolutePath();

        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath(absNonExistent.toString());

        CustomerSelfEvolutionService isolatedService = new CustomerSelfEvolutionService(props, null);
        CustomerEvolutionStatusResponse status = isolatedService.getStatus();

        // Must remain clean slate: NOT contaminated by pre-evolved generation 73 or score 0.967
        assertThat(isolatedService.getResolvedStoragePath()).isNull();
        assertThat(status.evolutionGeneration()).isEqualTo(0);
        assertThat(status.totalInteractions()).isEqualTo(0L);
        assertThat(status.totalFeedbackCount()).isEqualTo(0L);
        assertThat(status.averageQualityScore()).isEqualTo(0.0);

        // Guidelines in all domains must be completely empty
        for (CustomerScenario sc : CustomerScenario.values()) {
            assertThat(isolatedService.getActiveGuidelinesForScenario(sc))
                    .as("Domain %s must have 0 guidelines in clean slate", sc.code())
                    .isEmpty();
            assertThat(isolatedService.getActiveExemplarsForScenario(sc))
                    .as("Domain %s must have 0 exemplars in clean slate", sc.code())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Challenger Probe 2.2: Mutative operations in TempDir do NOT alter persistent project files")
    void tempIsolation_tempOperationsDoNotMutatePersistentProjectFiles(@TempDir Path tempDir) throws Exception {
        // Locate project persistent file
        Path rootPersistentFile = Path.of("..", "data", "customer-evolution-memory.json").toAbsolutePath().normalize();
        if (!Files.isRegularFile(rootPersistentFile)) {
            rootPersistentFile = Path.of("data", "customer-evolution-memory.json").toAbsolutePath().normalize();
        }
        assertThat(Files.isRegularFile(rootPersistentFile)).isTrue();

        byte[] baselineBytes = Files.readAllBytes(rootPersistentFile);
        String baselineHash = FileValidation.sha256Hex(baselineBytes);

        // Create isolated service pointing to tempDir
        Path tempMemoryFile = tempDir.resolve("isolated-mutative.json").toAbsolutePath();
        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath(tempMemoryFile.toString());

        CustomerSelfEvolutionService isolatedService = new CustomerSelfEvolutionService(props, null);

        // Perform mutative operations: feedback, evolve cycle, and reset
        isolatedService.recordFeedbackAndEvolve(new CustomerFeedbackRequest(
                "temp-id-999", "Câu hỏi thử nghiệm", "Câu trả lời thử nghiệm",
                "SALES", 5, true, "Phản hồi thử nghiệm rất tốt"
        ));
        isolatedService.triggerEvolutionCycle("SALES");
        isolatedService.resetEvolutionMemory();

        // Verify temp file was created and written
        assertThat(Files.isRegularFile(tempMemoryFile)).isTrue();
        assertThat(Files.size(tempMemoryFile)).isGreaterThan(10L);

        // Verify project persistent file is 100% UNTOUCHED
        byte[] postBytes = Files.readAllBytes(rootPersistentFile);
        String postHash = FileValidation.sha256Hex(postBytes);
        assertThat(postHash)
                .as("Persistent project memory file must NOT be modified during temp tests")
                .isEqualTo(baselineHash);
    }

    @Test
    @DisplayName("Challenger Probe 2.3: Pre-populated TempDir file loads isolated data and ignores project persistent file")
    void tempIsolation_loadsCustomTempFileExclusively(@TempDir Path tempDir) throws Exception {
        Path customTempFile = tempDir.resolve("custom-seed.json").toAbsolutePath();
        String customJson = """
                {
                  "enabled": true,
                  "strategy": "custom-challenger-strategy",
                  "evolution_generation": 42,
                  "total_interactions": 100,
                  "total_feedback_count": 25,
                  "average_quality_score": 0.945,
                  "learned_guidelines": {
                    "SALES": [
                      {
                        "id": "custom-id-001",
                        "scenario": "SALES",
                        "rule": "Quy tắc cách ly đặc biệt",
                        "source_gradient": "Challenger gradient",
                        "confidence_score": 0.95,
                        "reinforcement_count": 5,
                        "content_hash": "customhash123",
                        "updated_at": "2026-10-15T00:00:00Z"
                      }
                    ]
                  },
                  "evolved_few_shot_examples": {},
                  "recent_gradients": []
                }
                """;
        Files.writeString(customTempFile, customJson, StandardCharsets.UTF_8);

        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setStrategy("custom-challenger-strategy");
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath(customTempFile.toString());

        CustomerSelfEvolutionService isolatedService = new CustomerSelfEvolutionService(props, null);
        CustomerEvolutionStatusResponse status = isolatedService.getStatus();

        assertThat(status.strategy()).isEqualTo("custom-challenger-strategy");
        assertThat(status.evolutionGeneration()).isEqualTo(42);
        assertThat(status.totalInteractions()).isEqualTo(100L);
        assertThat(status.totalFeedbackCount()).isEqualTo(25L);
        assertThat(status.averageQualityScore()).isEqualTo(0.945);
        assertThat(status.learnedGuidelines().get("SALES")).hasSize(1);
        assertThat(status.learnedGuidelines().get("SALES").get(0).rule()).isEqualTo("Quy tắc cách ly đặc biệt");
    }

    // =========================================================================
    // 3. CONTROLLER STATUS ENDPOINT EMPIRICAL VERIFICATION (HTTP 200, SCORE >= 0.92)
    // =========================================================================

    @Test
    @DisplayName("Challenger Probe 3.1: Controller /api/customer-chat/evolution/status with REAL evolution service")
    void controllerEndpoint_returnsHttp200WithQualityScoreAboveThreshold() throws Exception {
        // Build real evolution service loading persistent state
        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath("data/customer-evolution-memory.json");

        CustomerSelfEvolutionService realEvolutionService = new CustomerSelfEvolutionService(props, null);
        assertThat(realEvolutionService.getResolvedStoragePath()).isNotNull();

        CustomerChatService realChatService = new CustomerChatService(
                null, null, null, props, realEvolutionService, null);
        CustomerChatController controller = new CustomerChatController(realChatService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // 1. Primary endpoint check
        mockMvc.perform(get("/api/customer-chat/evolution/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.strategy").value("evoagentx-textgrad-mipro"))
                .andExpect(jsonPath("$.evolution_generation").value(73))
                .andExpect(jsonPath("$.total_interactions").value(65))
                .andExpect(jsonPath("$.average_quality_score").value(0.967))
                .andExpect(jsonPath("$.learned_guidelines.SALES").isArray())
                .andExpect(jsonPath("$.learned_guidelines.SERVICE_SUPPORT").isArray())
                .andExpect(jsonPath("$.learned_guidelines.WARRANTY_RETURN").isArray())
                .andExpect(jsonPath("$.learned_guidelines.ORDER_BILLING").isArray())
                .andExpect(jsonPath("$.learned_guidelines.GENERAL").isArray())
                .andExpect(jsonPath("$.evolved_few_shot_examples.SALES").isArray())
                .andExpect(jsonPath("$.evolved_few_shot_examples.SERVICE_SUPPORT").isArray())
                .andExpect(jsonPath("$.evolved_few_shot_examples.WARRANTY_RETURN").isArray())
                .andExpect(jsonPath("$.evolved_few_shot_examples.ORDER_BILLING").isArray())
                .andExpect(jsonPath("$.evolved_few_shot_examples.GENERAL").isArray());

        // 2. Verify all mapped route aliases return HTTP 200
        String[] aliasRoutes = new String[]{
                "/api/customer-chat/evolution",
                "/api/customer-chat/status",
                "/api/chat/customer/evolution",
                "/api/chat/customer/evolution/status",
                "/api/customer-support/evolution",
                "/api/customer-support/evolution/status",
                "/evolution",
                "/evolution/status"
        };
        for (String route : aliasRoutes) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.average_quality_score").value(0.967));
        }

        // 3. Verify zero mechanical citation markers in pre-evolved guidelines and exemplars
        CustomerEvolutionStatusResponse status = realEvolutionService.getStatus();
        for (Map.Entry<String, List<LearnedGuidelineInfo>> entry : status.learnedGuidelines().entrySet()) {
            for (LearnedGuidelineInfo g : entry.getValue()) {
                assertThat(g.rule()).doesNotContain("[1]", "[2]", "[3]", "[4]");
            }
        }
        for (Map.Entry<String, List<EvolvedExampleInfo>> entry : status.evolvedFewShotExamples().entrySet()) {
            for (EvolvedExampleInfo ex : entry.getValue()) {
                assertThat(ex.assistantAnswer()).doesNotContain("[1]", "[2]", "[3]", "[4]");
                assertThat(ex.qualityScore()).isGreaterThanOrEqualTo(0.90);
            }
        }
    }
}
