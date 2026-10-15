package com.example.docmind.service.evolution;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.EvolvedExampleInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.LearnedGuidelineInfo;
import com.example.docmind.service.retrieval.CustomerScenario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomerSelfEvolutionService — Multi-Path Loading & Pre-Evolved State Tests")
class CustomerSelfEvolutionServiceTest {

    @Test
    @DisplayName("resolveExistingStoragePath — Định vị chính xác file bộ nhớ tiến hoá qua nhiều đường dẫn ứng viên")
    void resolveExistingStoragePathFindsCandidatePaths() {
        CustomerSupportProperties props = new CustomerSupportProperties();
        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);

        // Thử đường dẫn chuẩn data/customer-evolution-memory.json
        Path path1 = service.resolveExistingStoragePath("data/customer-evolution-memory.json");
        assertThat(path1).isNotNull();
        assertThat(Files.isRegularFile(path1)).isTrue();
        assertThat(path1.toString()).contains("customer-evolution-memory.json");

        // Thử đường dẫn api-java/data/customer-evolution-memory.json
        Path path2 = service.resolveExistingStoragePath("api-java/data/customer-evolution-memory.json");
        assertThat(path2).isNotNull();
        assertThat(Files.isRegularFile(path2)).isTrue();

        // Đường dẫn rỗng hoặc null trả về null an toàn không crash
        assertThat(service.resolveExistingStoragePath(null)).isNull();
        assertThat(service.resolveExistingStoragePath("   ")).isNull();
        assertThat(service.resolveExistingStoragePath("data/path-khong-ton-tai-12345.json")).isNull();
    }

    @Test
    @DisplayName("Pre-Evolved State — Tự động tải tri thức đã tiến hoá khi khởi động (khi persistToDisk = true)")
    void startupLoadsPreEvolvedStateWhenPersistToDiskIsTrue() {
        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath("data/customer-evolution-memory.json");

        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);
        CustomerEvolutionStatusResponse status = service.getStatus();

        // Kiểm định trạng thái tổng thể
        assertThat(status.enabled()).isTrue();
        assertThat(status.strategy()).isEqualTo("evoagentx-textgrad-mipro");
        assertThat(status.evolutionGeneration()).isGreaterThanOrEqualTo(70);
        assertThat(status.totalInteractions()).isGreaterThanOrEqualTo(60L);
        assertThat(status.averageQualityScore()).isGreaterThanOrEqualTo(0.92);

        // Kiểm định đường dẫn đã phân giải thành công
        assertThat(service.getResolvedStoragePath()).isNotNull();
        assertThat(Files.isRegularFile(service.getResolvedStoragePath())).isTrue();
        assertThat(service.getResolvedStateFilePath()).isNotEmpty();

        // Kiểm định độ phủ toàn diện trên cả 5 miền kịch bản
        Map<String, List<LearnedGuidelineInfo>> guidelines = status.learnedGuidelines();
        assertThat(guidelines).isNotNull();
        for (CustomerScenario sc : CustomerScenario.values()) {
            assertThat(guidelines)
                    .as("Learned guidelines phải bao phủ miền %s", sc.code())
                    .containsKey(sc.code());
            assertThat(guidelines.get(sc.code()))
                    .as("Miền %s phải có ít nhất 1 quy tắc tiến hoá", sc.code())
                    .isNotEmpty();
        }

        // Kiểm định kho mẫu Few-Shot MIPRO trên cả 5 miền kịch bản
        Map<String, List<EvolvedExampleInfo>> exemplars = status.evolvedFewShotExamples();
        assertThat(exemplars).isNotNull();
        for (CustomerScenario sc : CustomerScenario.values()) {
            assertThat(exemplars)
                    .as("Evolved exemplars phải bao phủ miền %s", sc.code())
                    .containsKey(sc.code());
            assertThat(exemplars.get(sc.code()))
                    .as("Miền %s phải có ít nhất 1 mẫu hội thoại Few-Shot", sc.code())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("loadStateFromDisk — Tải tri thức tiến hoá kể cả khi persistToDisk = false")
    void loadStateFromDiskLoadsPreEvolvedStateEvenWhenPersistToDiskIsFalse() {
        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(false); // Ràng buộc: persistToDisk = false vẫn đọc được khi gọi loadStateFromDisk
        props.getEvolution().setStoragePath("data/customer-evolution-memory.json");

        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);
        assertThat(service.getStatus().totalInteractions()).isEqualTo(0L);

        // Gọi loadStateFromDisk()
        service.loadStateFromDisk();
        CustomerEvolutionStatusResponse status = service.getStatus();

        assertThat(status.evolutionGeneration()).isGreaterThanOrEqualTo(70);
        assertThat(status.totalInteractions()).isGreaterThanOrEqualTo(60L);
        assertThat(status.averageQualityScore()).isGreaterThanOrEqualTo(0.92);
        assertThat(service.getResolvedStoragePath()).isNotNull();
    }

    @Test
    @DisplayName("Active Guidelines & Exemplars — Trích xuất tri thức vào prompt không chứa ký hiệu trích dẫn máy móc [n]")
    void activeGuidelinesAndExemplarsAreCleanAndApplicable() {
        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath("data/customer-evolution-memory.json");

        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);

        // Lấy guidelines cho SALES
        List<LearnedGuidelineInfo> salesGuidelines = service.getActiveGuidelinesForScenario(CustomerScenario.SALES);
        assertThat(salesGuidelines).isNotEmpty();
        assertThat(salesGuidelines.get(0).rule()).isNotBlank();

        // Lấy exemplars cho WARRANTY_RETURN & SALES
        List<EvolvedExampleInfo> salesExemplars = service.getActiveExemplarsForScenario(CustomerScenario.SALES);
        assertThat(salesExemplars).isNotEmpty();
        for (EvolvedExampleInfo ex : salesExemplars) {
            assertThat(ex.assistantAnswer())
                    .as("Mẫu hội thoại không được chứa ký hiệu trích dẫn máy móc [n]")
                    .doesNotContain("[1]", "[2]", "[3]");
            assertThat(ex.qualityScore()).isGreaterThanOrEqualTo(0.90);
        }
    }

    @Test
    @DisplayName("Resilience — Khởi tạo an toàn khi file chỉ định không tồn tại hoặc rỗng")
    void initializesSafelyWhenFileDoesNotExist() {
        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(false);
        props.getEvolution().setStoragePath("data/non-existent-file-xyz.json");

        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);
        CustomerEvolutionStatusResponse status = service.getStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.evolutionGeneration()).isEqualTo(0);
        assertThat(status.totalInteractions()).isEqualTo(0L);
        assertThat(status.averageQualityScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Persistence — Lưu trữ bền vững xuống đĩa khi persistToDisk = true")
    void persistsToDiskWhenEnabled(@TempDir Path tempDir) throws Exception {
        Path memoryFile = tempDir.resolve("test-evolution-memory.json");

        CustomerSupportProperties props = new CustomerSupportProperties();
        props.getEvolution().setEnabled(true);
        props.getEvolution().setPersistToDisk(true);
        props.getEvolution().setStoragePath(memoryFile.toString());

        CustomerSelfEvolutionService service = new CustomerSelfEvolutionService(props, null);
        CustomerEvolutionStatusResponse resetStatus = service.resetEvolutionMemory();

        assertThat(resetStatus.evolutionGeneration()).isEqualTo(0);
        assertThat(Files.exists(memoryFile)).isTrue();
        assertThat(Files.size(memoryFile)).isGreaterThan(10L);
    }
}
