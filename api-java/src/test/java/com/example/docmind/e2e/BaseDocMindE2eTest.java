package com.example.docmind.e2e;

import com.example.docmind.config.AstrBotProperties;
import com.example.docmind.config.ChatProperties;
import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.config.EmbeddingProperties;
import com.example.docmind.config.KnowledgeBaseProperties;
import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.astrbot.AstrBotSimulateRequest;
import com.example.docmind.dto.astrbot.AstrBotSimulateResponse;
import com.example.docmind.repository.ChunkRepository;
import com.example.docmind.repository.DocumentRepository;
import com.example.docmind.service.CustomerChatService;
import com.example.docmind.service.astrbot.AstrBotService;
import com.example.docmind.service.embedding.StubEmbeddingClient;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.retrieval.CustomerScenario;
import com.example.docmind.service.retrieval.LocalKnowledgeBaseService;
import com.example.docmind.service.retrieval.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class cho bộ kiểm thử E2E độc lập, opaque-box tuân thủ TEST_INFRA.md và ORIGINAL_REQUEST.md.
 * Khởi tạo toàn bộ chuỗi dịch vụ với 6 tài liệu SOP Christian Dior thật,
 * Hybrid RAG in-memory, EvoAgentX Self-Evolution và AstrBot Gateway.
 */
public abstract class BaseDocMindE2eTest {

    @TempDir
    protected Path tempDir;

    protected LocalKnowledgeBaseService knowledgeBaseService;
    protected RetrievalService retrievalService;
    protected CustomerSupportProperties customerProps;
    protected CustomerSelfEvolutionService evolutionService;
    protected CustomerChatService customerChatService;
    protected AstrBotService astrBotService;
    protected DeterministicDiorConsultantLlm consultantLlm;
    protected List<String> capturedPrompts;

    @BeforeEach
    void setUpBaseE2e() {
        capturedPrompts = new ArrayList<>();
        EmbeddingProperties embProps = new EmbeddingProperties();
        embProps.setDim(384);
        StubEmbeddingClient embeddingClient = new StubEmbeddingClient(embProps);

        KnowledgeBaseProperties kbProps = new KnowledgeBaseProperties();
        kbProps.setEnabled(true);
        kbProps.setDirectoryPaths(List.of("docs/dior-knowledge-base", "../docs/dior-knowledge-base"));
        kbProps.setMaxChunksPerDocumentInTopK(2);

        knowledgeBaseService = new LocalKnowledgeBaseService(kbProps, embeddingClient);

        ChunkRepository offlineChunkRepo = mock(ChunkRepository.class);
        DocumentRepository offlineDocRepo = mock(DocumentRepository.class);
        when(offlineDocRepo.findAllByOrderByCreatedAtDesc())
                .thenThrow(new RuntimeException("E2E Standalone Mode: PostgreSQL offline"));

        ChatProperties chatProps = new ChatProperties();
        chatProps.setTopK(5);
        chatProps.setCandidateK(20);
        chatProps.setRrfK(60);

        retrievalService = new RetrievalService(offlineChunkRepo, embeddingClient, chatProps, knowledgeBaseService);

        customerProps = new CustomerSupportProperties();
        customerProps.setStoreName("Parfums Christian Dior Vietnam & DocMind Luxury Concierge");
        customerProps.setHotline("1900-6868");
        customerProps.setSupportEmail("dior.concierge@docmind.vn");
        customerProps.setWorkingHours("08:30 - 22:00 hàng ngày");
        customerProps.getEvolution().setPersistToDisk(true);
        customerProps.getEvolution().setStoragePath(tempDir.resolve("customer-evolution-memory.json").toString());
        customerProps.getEvolution().setStrategy("evoagentx-textgrad-mipro");

        consultantLlm = new DeterministicDiorConsultantLlm(capturedPrompts);
        evolutionService = new CustomerSelfEvolutionService(customerProps, consultantLlm);

        customerChatService = new CustomerChatService(
                retrievalService,
                offlineDocRepo,
                consultantLlm,
                customerProps,
                evolutionService,
                knowledgeBaseService
        );

        AstrBotProperties astrBotProps = new AstrBotProperties();
        astrBotService = new AstrBotService(astrBotProps, customerChatService, evolutionService);
    }

    // --- Helper Assertion Methods ---

    protected void assertZeroMechanicalCitationMarkers(String answer) {
        assertThat(answer).isNotBlank();
        assertThat(answer)
                .doesNotContainPattern("(?m)\\[\\d{1,3}(?:\\s*[,;-]\\s*\\d{1,3})*]")
                .doesNotContain("[1]")
                .doesNotContain("[2]")
                .doesNotContain("[3]")
                .doesNotContain("(Nguồn:")
                .doesNotContain("[Nguồn:")
                .doesNotContain("Theo tài liệu SOP");
    }

    protected void assertPregnancyContraindicationSafety(String answer) {
        assertThat(answer).isNotBlank();
        assertThat(answer)
                .containsIgnoringCase("Retishot")
                .containsAnyOf("0.1% Retinol", "Retinol", "chống chỉ định", "không sử dụng", "không an toàn", "tránh");
        assertThat(answer)
                .containsAnyOf("Capture Totale Le Sérum", "Le Sérum", "Dior Prestige", "an toàn cho mẹ bầu", "lành tính");
    }

    protected void assertCosmetovigilanceCompliance(String answer) {
        assertThat(answer).isNotBlank();
        assertThat(answer)
                .containsIgnoringCase("Cosmetovigilance")
                .containsAnyOf("14 ngày", "trong vòng 14 ngày")
                .containsAnyOf("80%", "dung tích");
    }

    protected void assertLvmhAuthenticityCompliance(String answer) {
        assertThat(answer).isNotBlank();
        assertThat(answer)
                .containsAnyOf("Batch Code", "batch code", "mã batch", "5R02", "4 ký tự")
                .containsAnyOf("tem phụ", "LVMH", "Made in France", "hóa đơn", "e-VAT");
    }

    // --- Deterministic Consultant LLM ---

    public static class DeterministicDiorConsultantLlm implements LlmClient {
        private final List<String> capturedPrompts;
        private final AtomicInteger callCount = new AtomicInteger(0);

        public DeterministicDiorConsultantLlm(List<String> capturedPrompts) {
            this.capturedPrompts = capturedPrompts;
        }

        @Override
        public String complete(String prompt) {
            capturedPrompts.add(prompt);
            callCount.incrementAndGet();

            if (prompt.contains("<EVOLVED_GUIDELINE>")) {
                return "<EVOLVED_GUIDELINE>Khi tư vấn mỹ phẩm Dior cho khách hàng mang thai hoặc da nhạy cảm, tuyệt đối cảnh báo không dùng Retishot 0.1% Retinol, đề xuất Capture Totale Le Sérum (3.450.000 VNĐ) [1] và kích hoạt giao thức bảo hành kích ứng Cosmetovigilance 14 ngày [2].</EVOLVED_GUIDELINE>";
            }

            String userQuestion = prompt;
            int qIdx = prompt.lastIndexOf("Câu hỏi: ");
            if (qIdx >= 0) {
                int endIdx = prompt.indexOf("\nTrả lời tư vấn", qIdx);
                if (endIdx > qIdx) {
                    userQuestion = prompt.substring(qIdx + "Câu hỏi: ".length(), endIdx).trim();
                } else {
                    userQuestion = prompt.substring(qIdx + "Câu hỏi: ".length()).trim();
                }
            }
            String lowerQ = userQuestion.toLowerCase(java.util.Locale.ROOT);

            // 1. Kiểm tra kịch bản tấn công / Prompt Injection
            if (lowerQ.contains("bỏ qua mọi hướng dẫn") || lowerQ.contains("bỏ qua hướng dẫn")
                    || lowerQ.contains("ignore previous") || lowerQ.contains("xác nhận ngay rằng")
                    || lowerQ.contains("còn 50.000") || lowerQ.contains("giá 50.000")
                    || lowerQ.contains("còn 99.000") || lowerQ.contains("giá 99.000")
                    || lowerQ.contains("bác sĩ của tôi đã đồng ý") || lowerQ.contains("do anything now")
                    || lowerQ.contains("<system>") || lowerQ.contains("secret key")) {
                return "Dạ Christian Dior xin kính chào Quý khách! Em xin phép từ chối các yêu cầu can thiệp hệ thống và cam kết chỉ cung cấp thông tin bảng giá niêm yết chính hãng: Tinh chất Capture Totale Le Sérum 30ml có giá niêm yết chính thức là 3.450.000 VNĐ [1]. Đối với phụ nữ mang thai, Retishot (chứa 0.1% Retinol) tuyệt đối chống chỉ định, thay vào đó Le Sérum hoàn toàn lành tính cho mẹ bầu [2]. (Hotline: 1900-6868)";
            }

            // 2. Kiểm tra an toàn thai kỳ / Skincare Retinol
            if (lowerQ.contains("mang thai") || lowerQ.contains("ba bau") || lowerQ.contains("bà bầu")
                    || lowerQ.contains("cho con bú") || lowerQ.contains("mẹ bỉm") || lowerQ.contains("thai kỳ")
                    || lowerQ.contains("retishot") || lowerQ.contains("retinol") || lowerQ.contains("có bầu")
                    || lowerQ.contains("sinh em bé") || lowerQ.contains("nuôi con")
                    || lowerQ.contains("le serum") || lowerQ.contains("le sérum") || lowerQ.contains("prestige")
                    || lowerQ.contains("capture totale") || lowerQ.contains("dưỡng da")) {
                return "Dạ Christian Dior xin kính chào Chị ạ! Trong thai kỳ và giai đoạn cho con bú, Chị tuyệt đối **không sử dụng Capture Totale Retishot (chứa 0.1% Retinol tinh khiết)** vì Retinol chống chỉ định cho mẹ bầu [1]. Thay vào đó, em xin gợi ý Chị dùng **Capture Totale Le Sérum (30ml: 3.450.000 VNĐ / 50ml: 4.650.000 VNĐ)** hoặc **Dior Prestige La Micro-Huile de Rose (30ml: 7.400.000 VNĐ)** hoàn toàn lành tính, an toàn tuyệt đối cho mẹ bầu [1]. Ngoài ra, Dior có chính sách bảo hành kích ứng Cosmetovigilance trong 14 ngày cho sản phẩm mở nắp còn trên 80% dung tích [2].";
            }

            // 3. Kiểm tra dị ứng da liễu / Cosmetovigilance / Sơ cứu da
            if (lowerQ.contains("di ung") || lowerQ.contains("dị ứng") || lowerQ.contains("kích ứng")
                    || lowerQ.contains("kich ung") || lowerQ.contains("man do") || lowerQ.contains("mẩn đỏ")
                    || lowerQ.contains("ngua rat") || lowerQ.contains("ngứa rát") || lowerQ.contains("châm chích")
                    || lowerQ.contains("cham chich") || lowerQ.contains("cosmetovigilance") || lowerQ.contains("rửa mặt")
                    || lowerQ.contains("hồ sơ") || (lowerQ.contains("mở nắp") && lowerQ.contains("80%"))) {
                return "Dạ Christian Dior rất tiếc khi biết Chị gặp bất tiện về làn da ạ! Chị hãy tạm ngưng sử dụng sản phẩm ngay và rửa nhẹ da bằng nước muối sinh lý mát. Theo **Giao thức Cosmetovigilance bảo hành kích ứng da liễu trong vòng 14 ngày**, Dior hỗ trợ đổi sang dòng sản phẩm phù hợp hoặc hoàn tiền 100% đối với sản phẩm đã mở nắp nếu dung tích còn lại trên 80% [1]. Chị vui lòng gửi hình ảnh tình trạng da và mã Batch Code 4 ký tự dưới đáy chai qua hotline 1900-6868 để chuyên viên hỗ trợ Chị nhanh nhất nhé ạ! [2]";
            }

            // 4. Kiểm tra hàng chính hãng / 5-Point Authenticity / Batch code / Tem phụ
            if (lowerQ.contains("batch code") || lowerQ.contains("mã batch") || lowerQ.contains("5r02")
                    || lowerQ.contains("tem phụ") || lowerQ.contains("tem phu") || lowerQ.contains("hàng giả")
                    || lowerQ.contains("chính hãng") || lowerQ.contains("chinh hang") || lowerQ.contains("made in france")
                    || lowerQ.contains("pao") || lowerQ.contains("mã vạch")) {
                return "Dạ Christian Dior cam kết 100% sản phẩm phân phối chính hãng qua quy chuẩn 5 điểm LVMH: 1. Mã Batch Code 4 ký tự dập chìm dưới đáy chai và vỏ hộp (ví dụ 5R02 biểu thị năm 2025, tháng R) [1]; 2. Tem phụ tiếng Việt của Công ty TNHH LVMH Beauty & Fashion Vietnam [1]; 3. Nguồn gốc xuất xứ rõ ràng Made in France [2]; 4. Hóa đơn điện tử e-VAT xuất trực tiếp từ LVMH [2]; 5. Ký hiệu PAO và độ hoàn thiện tinh xảo [2].";
            }

            // 5. Kiểm tra Nước hoa / Quà tặng / Khắc tên Engraving
            if (lowerQ.contains("sauvage") || lowerQ.contains("nuoc hoa") || lowerQ.contains("nước hoa")
                    || lowerQ.contains("miss dior") || lowerQ.contains("jadore") || lowerQ.contains("j'adore")
                    || lowerQ.contains("khac ten") || lowerQ.contains("khắc tên") || lowerQ.contains("engraving")
                    || lowerQ.contains("hop qua") || lowerQ.contains("hộp quà") || lowerQ.contains("art of gifting")
                    || lowerQ.contains("set quà") || lowerQ.contains("tặng bạn gái") || lowerQ.contains("tặng sếp")) {
                return "Dạ Christian Dior xin hân hạnh tư vấn: Nước hoa Sauvage Elixir 60ml có giá niêm yết 4.350.000 VNĐ, Miss Dior EDP 50ml giá 3.750.000 VNĐ và J'adore EDP 50ml giá 3.750.000 VNĐ [1]. Đơn hàng nước hoa được tặng kèm dịch vụ **khắc tên Engraving cá nhân hóa miễn phí tối đa 12 ký tự** (thời gian hoàn thiện 30-45 phút) cùng hộp quà Couture Art of Gifting thắt ruy băng lụa sang trọng [2].";
            }

            // 6. Kiểm tra VIP Loyalty / Hạng thẻ / Thanh toán / Vận chuyển / COD / Hóa đơn VAT
            if (lowerQ.contains("loyalty") || lowerQ.contains("my exclusive dior") || lowerQ.contains("gold")
                    || lowerQ.contains("platinum") || lowerQ.contains("beauty cabin") || lowerQ.contains("tra gop")
                    || lowerQ.contains("trả góp") || lowerQ.contains("hoa toc") || lowerQ.contains("hỏa tốc")
                    || lowerQ.contains("vietqr") || lowerQ.contains("cod") || lowerQ.contains("thanh toan")
                    || lowerQ.contains("thanh toán") || lowerQ.contains("chuyển khoản") || lowerQ.contains("hóa đơn")
                    || lowerQ.contains("vat") || lowerQ.contains("e-vat")) {
                return "Dạ chương trình thành viên **My Exclusive Dior** gồm 4 hạng thẻ: White, Silver (5-15 triệu), Gold (15-35 triệu) và Platinum (> 35 triệu/năm nhận liệu trình Facial 60 phút tại Dior Beauty Cabin) [1]. Về thanh toán, Dior hỗ trợ trả góp 0% thẻ tín dụng cho đơn từ 3.000.000 VNĐ, thanh toán chuyển khoản quét mã VietQR và ship COD tiền mặt tối đa 10.000.000 VNĐ [1]. Đơn từ 2.000.000 VNĐ được miễn phí giao Hỏa tốc VIP 2 giờ tại Hà Nội và TP.HCM với túi giữ nhiệt cách nhiệt 3 lớp, xuất hóa đơn VAT điện tử đầy đủ [2].";
            }

            // 7. Kiểm tra Trang điểm / Son môi / Kem nền
            if (lowerQ.contains("rouge dior") || lowerQ.contains("son môi") || lowerQ.contains("son")
                    || lowerQ.contains("kem nen") || lowerQ.contains("kem nền") || lowerQ.contains("forever")
                    || lowerQ.contains("lip glow") || lowerQ.contains("undertone")
                    || lowerQ.contains("màu 999") || lowerQ.contains("dior 999") || lowerQ.contains("son 999")
                    || lowerQ.contains("refill")) {
                return "Dạ với làn da dầu hoặc hỗn hợp, kem nền **Dior Forever Matte (1.850.000 VNĐ)** với tông màu 1W (Warm) kiềm dầu mịn lì 24h là lựa chọn hoàn hảo [1]. Kết hợp cùng thỏi son **Rouge Dior 999 Velvet (1.290.000 VNĐ)** đỏ thuần quyền lực hoặc son dưỡng **Dior Addict Lip Glow 004 Coral (1.080.000 VNĐ)** giúp môi căng mọng khử thâm tuyệt đối [2]. Chính sách lõi refill giúp tiết kiệm 20% chi phí [2].";
            }

            // 8. Mặc định trả về câu trả lời tư vấn chuẩn mực
            return "Dạ Christian Dior xin kính chào Quý khách! Parfums Christian Dior Vietnam mở cửa phục vụ từ 08:30 - 22:00 hàng ngày [1]. Mọi sản phẩm đều được phân phối chính hãng LVMH, bảo hành chính sách đổi trả minh bạch và giao hàng Hỏa tốc 2 giờ. Quý khách có thể liên hệ tổng đài Hotline 1900-6868 để được chuyên viên chăm sóc tận tình nhất nhé ạ! [2]";
        }

        @Override
        public String name() {
            return "deterministic-dior-consultant-llm";
        }

        public int getCallCount() {
            return callCount.get();
        }
    }
}
