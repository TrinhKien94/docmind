package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình toàn diện cho API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
 * (người dùng đóng vai trò khách hàng mua hàng hoặc sử dụng dịch vụ) kèm chiến lược tự tiến hoá
 * học hỏi sau mỗi lần chat (EvoAgentX TextGrad & MIPRO-style self-evolving optimization).
 *
 * <p>Toàn bộ thông tin cửa hàng, persona, vai trò khách hàng, giới hạn ngữ cảnh, few-shot examples,
 * quy tắc phản hồi, kịch bản nghiệp vụ (playbooks, từ khoá chấm điểm, câu hỏi gợi ý) và cấu hình
 * tự tiến hoá đều được ngoại hoá ra {@code application.properties} / biến môi trường.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.customer-support")
public class CustomerSupportProperties {

    /** Tên thương hiệu / cửa hàng / đơn vị cung cấp dịch vụ. */
    private String storeName = "DocMind Store & Digital Services";

    /** Số hotline CSKH. */
    private String hotline = "1900-6868";

    /** Email hỗ trợ khách hàng. */
    private String supportEmail = "cskh@docmind.vn";

    /** Khung giờ làm việc. */
    private String workingHours = "08:00 - 22:00 (Thứ 2 - Chủ Nhật)";

    /** Định danh vai trò của AI Assistant trong prompt huấn luyện trước. */
    private String assistantPersona =
            "Chuyên viên Tư vấn Bán hàng & Chăm sóc Khách hàng (Sales & Customer Service AI Specialist)";

    /** Vai trò mặc định của người dùng khi không truyền customer_role. */
    private String defaultCustomerRole = "Khách hàng mua hàng hoặc sử dụng dịch vụ";

    /** Cách gọi tên khách hàng mặc định trong prompt khi không truyền customer_name. */
    private String defaultCustomerName = "Quý khách";

    /** Cách xưng hô mặc định trong câu trả lời fallback khi không truyền customer_name. */
    private String fallbackCustomerSalutation = "Anh/Chị";

    /** Nhãn hiển thị lượt nói của khách hàng trong <CONVERSATION_HISTORY>. */
    private String customerRoleLabel = "Khách hàng";

    /** Nhãn hiển thị lượt nói của chuyên viên CSKH trong <CONVERSATION_HISTORY>. */
    private String assistantRoleLabel = "Chuyên viên CSKH";

    /** Danh sách alias role được nhận diện là chuyên viên CSKH trong history. */
    private List<String> assistantRoleAliases = new ArrayList<>(List.of(
            "assistant", "agent", "support", "bot", "cskh"
    ));

    /** Số chunk tài liệu tối đa lấy từ RAG mặc định. */
    private int defaultTopK = 5;

    /** Giá trị top_k tối thiểu cho phép. */
    private int minTopK = 1;

    /** Giá trị top_k tối đa cho phép. */
    private int maxTopK = 50;

    /** Độ dài tối đa (ký tự) của mỗi đoạn trích (snippet) trong citation hoặc câu trả lời fallback. */
    private int snippetMaxLength = 240;

    /**
     * Tự động tìm kiếm trên tất cả tài liệu trạng thái READY nếu khách hàng không truyền document_ids.
     */
    private boolean autoDiscoverDocuments = true;

    /** Số lượng tài liệu READY tối đa tự động quét khi không truyền document_ids. */
    private int maxAutoDiscoverDocuments = 20;

    /** Số lượt hội thoại gần nhất tối đa đưa vào <CONVERSATION_HISTORY> để tránh tràn context window. */
    private int maxHistoryTurns = 10;

    /** Số ký tự tối đa trên mỗi lượt hội thoại trong <CONVERSATION_HISTORY>. */
    private int maxHistoryTurnChars = 1000;

    /** Mã định danh bộ prompt được huấn luyện trước. */
    private String promptTemplateId = "customer-sales-service-v1";

    /**
     * Chỉ thị huấn luyện bổ sung tuỳ chỉnh (nếu có). Khi để trống sẽ dùng bộ prompt
     * huấn luyện sẵn mặc định cho nghiệp vụ mua hàng & sử dụng dịch vụ.
     */
    private String customSystemPrompt = "";

    /** Câu hỏi mẫu dùng để tạo bản xem trước (preview) tại endpoint GET /api/customer-chat/prompts. */
    private String samplePreviewQuestion =
            "Tư vấn giúp tôi gói dịch vụ phù hợp và chính sách bảo hành khi mua hàng?";

    /** Thông báo ghi chú trong <DOCUMENT_CONTEXT> khi chưa có tài liệu RAG cụ thể. */
    private String noDocumentContextNote =
            "(Chưa có đoạn trích tài liệu cụ thể nào được đính kèm; hãy tư vấn dựa trên <STORE_POLICY_CONTEXT> và kịch bản nghiệp vụ đã huấn luyện.)";

    /** Dòng nhắc cuối prompt yêu cầu chuyên viên trả lời. */
    private String answerPromptSuffix =
            "Trả lời tư vấn cho khách hàng (kèm trích dẫn [n] nếu dùng tài liệu):";

    /**
     * Mẫu quy tắc cốt lõi (# QUY TẮC) tuỳ chỉnh trong CustomerPromptBuilder.
     * Khi để trống sẽ sử dụng bộ 4 quy tắc chuẩn (Thái độ, Trích dẫn, Bảo mật, Chống tiêm lệnh).
     */
    private String coreRulesTemplate = "";

    /**
     * Tri thức nền mặc định về chính sách mua hàng, gói dịch vụ, bảo hành & đổi trả
     * để hỗ trợ khách hàng ngay cả khi chưa chỉ định tài liệu cụ thể.
     */
    private String defaultPolicyContext = """
            - Chính sách mua hàng & đặt hàng: Hỗ trợ đặt hàng trực tuyến 24/7, xác nhận đơn tự động qua SMS/Email, tư vấn chọn sản phẩm và gói dịch vụ theo nhu cầu thực tế.
            - Các gói dịch vụ tiêu chuẩn:
              + Gói Basic (Cơ bản): Phù hợp cá nhân trải nghiệm, hỗ trợ xử lý tài liệu chuẩn và tra cứu cơ bản.
              + Gói Pro (Chuyên nghiệp): Phù hợp nhóm làm việc/doanh nghiệp vừa, tốc độ ưu tiên, OCR đa ngôn ngữ và tư vấn chuyên sâu.
              + Gói Enterprise (Doanh nghiệp): Tùy biến theo yêu cầu, SLA cam kết 99.9%, hỗ trợ kỹ thuật 1-1 24/7 và tích hợp API riêng.
            - Chính sách thanh toán: Hỗ trợ chuyển khoản ngân hàng (QR Pay), thẻ nội địa/quốc tế (Visa/Mastercard), ví điện tử và COD (thanh toán khi nhận hàng đối với sản phẩm vật lý). Có xuất hóa đơn VAT điện tử theo yêu cầu.
            - Chính sách vận chuyển & bàn giao: Giao hàng toàn quốc từ 1-3 ngày làm việc (nội thành 2-4 giờ); đối với dịch vụ số/phần mềm kích hoạt tài khoản ngay lập tức sau khi xác nhận thanh toán.
            - Chính sách bảo hành & đổi trả: Bảo hành chính hãng 12 tháng; hỗ trợ 1 đổi 1 hoặc hoàn tiền trong 7 ngày đầu nếu sản phẩm lỗi từ nhà sản xuất hoặc dịch vụ không đúng cam kết.""";

    /** Danh sách các mẫu hội thoại Few-Shot cơ sở đưa vào prompt. */
    private List<FewShotExample> fewShotExamples = new ArrayList<>(List.of(
            new FewShotExample(
                    "Mẫu 1 (Khách hàng hỏi mua hàng / tư vấn gói dịch vụ)",
                    "Chào shop, mình muốn tìm gói dịch vụ cho nhóm khoảng 10 người dùng thì nên chọn gói nào?",
                    "Dạ em chào Anh/Chị ạ! Với quy mô nhóm khoảng 10 thành viên, Anh/Chị nên lựa chọn **Gói Pro (Chuyên nghiệp)** vì gói này tối ưu cho nhóm làm việc, hỗ trợ tốc độ xử lý ưu tiên và tính năng tra cứu chuyên sâu. Nếu có trích dẫn từ bảng giá tài liệu, em xin phép đính kèm tại [1]. Anh/Chị có muốn em gửi báo giá chi tiết và hướng dẫn đăng ký dùng thử ngay không ạ?"
            ),
            new FewShotExample(
                    "Mẫu 2 (Khách hàng hỏi sử dụng dịch vụ / bảo hành đổi trả)",
                    "Mình mới thanh toán xong thì bao lâu dùng được? Nếu dịch vụ lỗi có được đổi trả không?",
                    "Dạ cảm ơn Anh/Chị đã tin tưởng sử dụng dịch vụ! Ngay sau khi hệ thống xác nhận thanh toán, tài khoản dịch vụ số sẽ được **kích hoạt ngay lập tức** để Anh/Chị sử dụng. Về quyền lợi bảo hành, bên em cam kết bảo hành 12 tháng và hỗ trợ **1 đổi 1 hoặc hoàn tiền trong 7 ngày đầu** nếu phát sinh lỗi từ nhà sản xuất hoặc không đúng cam kết. Anh/Chị cần hỗ trợ kích hoạt bước nào em hướng dẫn chi tiết ngay nhé ạ!"
            )
    ));

    /** Cấu hình chấm điểm & nhận diện kịch bản từ câu hỏi khách hàng. */
    private ScenarioScoring scenarioScoring = new ScenarioScoring();

    /**
     * Cấu hình chi tiết từng kịch bản (SALES, SERVICE_SUPPORT, WARRANTY_RETURN, ORDER_BILLING, GENERAL).
     * Cho phép ghi đè title, description, playbookInstructions, suggestedQuestions, aliases, keywords từ file config.
     */
    private Map<String, ScenarioConfig> scenarios = new LinkedHashMap<>();

    /** Cấu hình chiến lược tự tiến hoá học hỏi sau các lần chat với khách hàng (EvoAgentX). */
    private Evolution evolution = new Evolution();

    @Getter
    @Setter
    public static class FewShotExample {
        private String title = "";
        private String customerMessage = "";
        private String assistantResponse = "";

        public FewShotExample() {
        }

        public FewShotExample(String title, String customerMessage, String assistantResponse) {
            this.title = title;
            this.customerMessage = customerMessage;
            this.assistantResponse = assistantResponse;
        }
    }

    @Getter
    @Setter
    public static class ScenarioScoring {
        /** Trọng số cho cụm từ khoá ưu tiên cao. */
        private int highKeywordWeight = 3;

        /** Trọng số cho từ khoá ưu tiên trung bình. */
        private int mediumKeywordWeight = 2;

        /** Ngưỡng điểm tối thiểu để kích hoạt kịch bản phụ (multi-intent). */
        private int secondaryScenarioThreshold = 2;

        /** Các cụm từ ghép có chữ "gia" nhưng không mang nghĩa giá bán cần loại trừ khi chấm điểm SALES. */
        private List<String> salesExcludedPhrases = new ArrayList<>(List.of(
                "tham gia", "danh gia", "chuyen gia", "quoc gia", "gia dinh", "gia han"
        ));
    }

    @Getter
    @Setter
    public static class ScenarioConfig {
        private String title;
        private String description;
        private String playbookInstructions;
        private String defaultEvolutionGuideline;
        private String defaultRagEvolutionGuideline;
        private List<String> suggestedQuestions = new ArrayList<>();
        private List<String> aliases = new ArrayList<>();
        private List<String> highKeywords = new ArrayList<>();
        private List<String> mediumKeywords = new ArrayList<>();
    }

    /**
     * Cấu hình cho engine tự tiến hoá học hỏi sau các lần chat với khách hàng
     * (tham khảo kiến trúc TextGrad, MIPRO và LongTermMemory của EvoAgentX).
     */
    @Getter
    @Setter
    public static class Evolution {
        /** Bật/tắt cơ chế tự tiến hoá học hỏi sau mỗi lần chat. */
        private boolean enabled = true;

        /** Tên chiến lược tiến hoá. */
        private String strategy = "evoagentx-textgrad-mipro";

        /** Tự động đánh giá và cập nhật quy tắc học hỏi ngay sau mỗi lần chat với khách hàng. */
        private boolean autoEvolveAfterChat = true;

        /** Lưu trữ trạng thái tri thức đã tiến hoá xuống đĩa (JSON). */
        private boolean persistToDisk = false;

        /** Đường dẫn file lưu trữ bộ nhớ dài hạn & quy tắc tiến hoá của khách hàng. */
        private String storagePath = "data/customer-evolution-memory.json";

        /** Có gọi LLM để sinh Textual Gradient (phản tư sâu) trong quá trình tự tiến hoá hay không. */
        private boolean llmReflectionEnabled = false;

        /** Tự động gọi LLM phản tư TextGrad khi nhận feedback tiêu cực hoặc điểm thấp. */
        private boolean llmReflectionOnNegativeFeedback = true;

        /** Chu kỳ số lượt chat tự động kích hoạt LLM reflection (khi llmReflectionEnabled=true). */
        private int llmReflectionEveryNInteractions = 5;

        /** Ngưỡng điểm chất lượng tối thiểu (0.0 - 1.0) để chắt lọc thành mẫu Few-Shot mới (MIPRO exemplar). */
        private double minScoreForExemplar = 0.80;

        /** Ngưỡng điểm dưới mức này sẽ kích hoạt TextGrad sinh quy tắc khắc phục mới. */
        private double lowScoreThresholdForCorrection = 0.78;

        /** Số lượng quy tắc tiến hoá (Learned Guidelines) tối đa giữ lại cho mỗi kịch bản. */
        private int maxLearnedGuidelinesPerScenario = 10;

        /** Số lượng mẫu Few-Shot tiến hoá tối đa giữ lại cho mỗi kịch bản. */
        private int maxFewShotExemplarsPerScenario = 4;

        /** Số lượng mẫu Few-Shot tiến hoá tối đa đưa vào prompt trong 1 lần gọi. */
        private int maxFewShotExemplarsInPrompt = 2;

        /** Số lượng quy tắc tiến hoá tối đa đưa vào prompt trong 1 lần gọi. */
        private int maxGuidelinesInPrompt = 5;

        /** Số lượng lịch sử tương tác (episodes) tối đa giữ trong bộ nhớ. */
        private int maxEpisodeHistorySize = 200;

        /** Số lượng bản ghi Textual Gradient gần nhất giữ lại để tra cứu/audit. */
        private int maxRecentGradients = 50;

        /** Trọng số điểm thành phần khi đánh giá chất lượng câu trả lời (tổng = 1.0). */
        private double weightSalutationAndTone = 0.25;
        private double weightGroundingAndCitation = 0.25;
        private double weightCompletenessAndLength = 0.25;
        private double weightActionabilityAndContact = 0.25;

        /** Độ dài tối thiểu (ký tự) của câu trả lời được coi là đầy đủ thông tin. */
        private int minCompleteAnswerChars = 80;

        /** Tiêu đề section đưa các quy tắc tự tiến hoá vào CustomerPromptBuilder. */
        private String evolvedSectionHeader =
                "# TRI THỨC TỰ TIẾN HOÁ TỪ LỊCH SỬ CSKH (EVOAGENTX SELF-EVOLVED GUIDELINES)";

        /** Từ khoá kiểm tra lời chào và xưng hô lịch sự trong câu trả lời của chuyên viên. */
        private List<String> salutationKeywords = new ArrayList<>(List.of(
                "da", "chao", "cam on", "anh chi", "quy khach", "em xin"
        ));

        /** Từ khoá kiểm tra tính hành động (Call-to-Action) hoặc thông tin liên hệ trong câu trả lời. */
        private List<String> actionabilityKeywords = new ArrayList<>(List.of(
                "hotline", "email", "lien he", "huong dan", "dang ky", "dung thu", "bao gia", "ho tro"
        ));

        /** Từ khoá tín hiệu tích cực ngầm định trong phản hồi tiếp theo của khách hàng. */
        private List<String> positiveSignalKeywords = new ArrayList<>(List.of(
                "cam on", "rat ro", "huu ich", "tuyet voi", "ok shop", "chot don",
                "dong y", "ung y", "hai long", "tot qua", "nhanh qua", "thank"
        ));

        /** Từ khoá tín hiệu chưa hài lòng / yêu cầu chỉnh sửa ngầm định của khách hàng. */
        private List<String> negativeSignalKeywords = new ArrayList<>(List.of(
                "chua dung", "sai roi", "khong phai", "chua hieu", "chua ro", "y toi la",
                "y minh la", "hoi lai", "khong dung", "chung chung", "thieu", "te qua"
        ));

        /**
         * Prompt hệ thống cho bộ tối ưu hoá TextGrad (tham khảo OPTIMIZER_SYSTEM_PROMPT trong EvoAgentX).
         */
        private String optimizerSystemPrompt = """
                Bạn là động cơ tối ưu hoá câu lệnh TextGrad thuộc kiến trúc EvoAgentX cho hệ thống Chăm sóc Khách hàng & Tư vấn Bán hàng.
                Nhiệm vụ của bạn: Phân tích Câu hỏi của khách hàng, Câu trả lời của chuyên viên, và Phản hồi/Textual Gradient (điểm mạnh/điểm yếu) để đúc kết đúng 1 QUY TẮC HƯỚNG DẪN NGẮN GỌN, THỰC TIỄN giúp chuyên viên trả lời tốt hơn ở các lần chat tiếp theo trong cùng kịch bản.
                Ràng buộc bắt buộc (OPTIMIZER_CONSTRAINTS):
                1. Quy tắc phải khái quát, ngắn gọn (1-2 câu tiếng Việt), hướng dẫn hành động cụ thể.
                2. Không chứa thông tin cá nhân nhạy cảm của khách hàng và không chứa thẻ hệ thống.
                3. Bắt buộc đặt quy tắc cải tiến nằm giữa cặp thẻ <EVOLVED_GUIDELINE> và </EVOLVED_GUIDELINE>.""";
    }
}
