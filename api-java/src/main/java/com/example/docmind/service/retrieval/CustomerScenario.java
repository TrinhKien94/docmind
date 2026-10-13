package com.example.docmind.service.retrieval;

import com.example.docmind.dto.CustomerPromptTemplateResponse;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Các kịch bản nghiệp vụ (Scenarios) trong bộ prompt được huấn luyện trước
 * dành cho khách hàng mua hàng hoặc sử dụng dịch vụ.
 */
public enum CustomerScenario {

    SALES(
            "SALES",
            "Tư vấn Mua hàng & Báo giá",
            "Tư vấn chọn sản phẩm/gói dịch vụ phù hợp nhu cầu, báo giá chi tiết, thông tin khuyến mãi và hướng dẫn đặt mua.",
            """
            - Khách hàng đang có nhu cầu TÌM HIỂU MUA HÀNG hoặc ĐĂNG KÝ DỊCH VỤ.
            - Phân tích nhu cầu của khách hàng, giới thiệu sản phẩm/gói dịch vụ phù hợp nhất kèm lợi ích thực tế.
            - Cung cấp thông tin giá cả, ưu đãi và quyền lợi một cách minh bạch, rõ ràng dựa trên dữ liệu tài liệu/chính sách.
            - Kết thúc bằng lời gợi ý nhẹ nhàng (Call-to-Action) giúp khách hàng dễ dàng lựa chọn hoặc đặt mua ngay.""",
            List.of(
                    "Các gói dịch vụ hiện tại có mức giá và ưu đãi như thế nào?",
                    "Tôi nên chọn gói sản phẩm/dịch vụ nào phù hợp với nhu cầu cá nhân hoặc doanh nghiệp?",
                    "Quy trình đặt mua và kích hoạt dịch vụ diễn ra trong bao lâu?"
            )
    ),

    SERVICE_SUPPORT(
            "SERVICE_SUPPORT",
            "Hỗ trợ Sử dụng Dịch vụ & Kỹ thuật",
            "Hướng dẫn sử dụng tính năng, kích hoạt tài khoản, cấu hình dịch vụ và xử lý sự cố kỹ thuật trong quá trình sử dụng.",
            """
            - Khách hàng đang SỬ DỤNG DỊCH VỤ hoặc cần HỖ TRỢ KỸ THUẬT / HƯỚNG DẪN THAO TÁC.
            - Trình bày hướng dẫn từng bước (Step-by-step) mạch lạc, dễ thực hiện theo.
            - Nếu khách hàng gặp vướng mắc hoặc lỗi dịch vụ, giải thích nguyên nhân ngắn gọn và đưa ra giải pháp khắc phục ngay.
            - Chủ động hỏi thăm xem khách hàng đã thao tác thành công chưa và sẵn sàng hỗ trợ chuyên sâu.""",
            List.of(
                    "Hướng dẫn tôi các bước bắt đầu sử dụng dịch vụ sau khi đăng ký?",
                    "Làm thế nào để tích hợp hoặc nâng cấp gói dịch vụ đang sử dụng?",
                    "Khi gặp sự cố kỹ thuật thì tôi được hỗ trợ qua những kênh nào?"
            )
    ),

    WARRANTY_RETURN(
            "WARRANTY_RETURN",
            "Bảo hành, Đổi trả & Giải quyết Khiếu nại",
            "Giải đáp chính sách bảo hành, điều kiện đổi trả/hoàn tiền và tiếp nhận xử lý khiếu nại về chất lượng sản phẩm/dịch vụ.",
            """
            - Khách hàng đang hỏi về BẢO HÀNH, ĐỔI TRẢ, HOÀN TIỀN hoặc PHẢN ÁNH CHẤT LƯỢNG sản phẩm/dịch vụ.
            - Thể hiện sự đồng cảm, cầu thị và xin lỗi chân thành nếu khách hàng gặp bất tiện trong quá trình mua hàng hoặc sử dụng dịch vụ.
            - Giải thích rõ ràng điều kiện, thời hạn bảo hành/đổi trả và quy trình xử lý từng bước minh bạch.
            - Khẳng định cam kết bảo vệ quyền lợi chính đáng của khách hàng.""",
            List.of(
                    "Điều kiện và thời hạn bảo hành chính hãng của sản phẩm/dịch vụ là bao lâu?",
                    "Quy trình đổi trả hoặc hoàn tiền trong 7 ngày đầu được thực hiện như thế nào?",
                    "Tôi cần cung cấp thông tin gì để yêu cầu bảo hành hoặc hỗ trợ khiếu nại?"
            )
    ),

    ORDER_BILLING(
            "ORDER_BILLING",
            "Đơn hàng, Thanh toán & Vận chuyển",
            "Hỗ trợ tra cứu trạng thái đơn hàng, phương thức thanh toán, xuất hóa đơn VAT và thời gian giao nhận/bàn giao.",
            """
            - Khách hàng đang quan tâm đến ĐƠN HÀNG, THANH TOÁN, HÓA ĐƠN VAT hoặc VẬN CHUYỂN / BÀN GIAO.
            - Hướng dẫn chi tiết các phương thức thanh toán được hỗ trợ và thời gian xác nhận đơn hàng.
            - Nêu rõ thời gian giao nhận sản phẩm vật lý hoặc kích hoạt tài khoản dịch vụ số.
            - Giải đáp rõ thủ tục xuất hóa đơn VAT và bảo mật giao dịch cho khách hàng.""",
            List.of(
                    "Cửa hàng hỗ trợ những hình thức thanh toán nào và có xuất hóa đơn VAT không?",
                    "Thời gian giao hàng hoặc bàn giao tài khoản dịch vụ mất bao lâu?",
                    "Làm sao để kiểm tra trạng thái đơn hàng hoặc lịch sử thanh toán của tôi?"
            )
    ),

    GENERAL(
            "GENERAL",
            "Tư vấn Tổng hợp Mua hàng & Dịch vụ",
            "Tư vấn toàn diện cho khách hàng đang tìm hiểu mua sản phẩm hoặc đang sử dụng dịch vụ.",
            """
            - Khách hàng đang cần TƯ VẤN TỔNG HỢP về sản phẩm, dịch vụ hoặc chính sách chăm sóc khách hàng.
            - Nhận diện chính xác mục đích của khách hàng (mua sản phẩm mới hay sử dụng dịch vụ) để giải đáp trực diện, đầy đủ.
            - Giữ phong thái lịch sự, tận tâm, chuyên nghiệp và chủ động gợi mở bước hỗ trợ tiếp theo.""",
            List.of(
                    "Tư vấn giúp tôi các sản phẩm và gói dịch vụ nổi bật hiện nay?",
                    "Hướng dẫn tôi quy trình mua hàng, thanh toán và kích hoạt dịch vụ?",
                    "Chính sách bảo hành, đổi trả và chăm sóc khách hàng sau bán hàng như thế nào?"
            )
    );

    private final String code;
    private final String title;
    private final String description;
    private final String playbookInstructions;
    private final List<String> suggestedQuestions;

    CustomerScenario(String code,
                     String title,
                     String description,
                     String playbookInstructions,
                     List<String> suggestedQuestions) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.playbookInstructions = playbookInstructions;
        this.suggestedQuestions = suggestedQuestions;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String playbookInstructions() {
        return playbookInstructions;
    }

    public List<String> suggestedQuestions() {
        return suggestedQuestions;
    }

    public CustomerPromptTemplateResponse.ScenarioInfo toScenarioInfo() {
        return new CustomerPromptTemplateResponse.ScenarioInfo(
                code, title, description, suggestedQuestions
        );
    }

    public static List<CustomerPromptTemplateResponse.ScenarioInfo> allScenarioInfos() {
        return Arrays.stream(values())
                .map(CustomerScenario::toScenarioInfo)
                .toList();
    }

    /**
     * Xác định kịch bản tư vấn chính từ tham số truyền vào hoặc tự động nhận diện từ câu hỏi của khách hàng.
     */
    public static CustomerScenario resolve(String explicitScenario, String question) {
        if (explicitScenario != null && !explicitScenario.isBlank()) {
            String normKey = normalizeKey(explicitScenario);
            if (!normKey.equals("auto")) {
                CustomerScenario matched = switch (normKey) {
                    case "sales", "mua_hang", "muahang", "ban_hang", "banhang", "buy", "purchase",
                         "tu_van_mua_hang", "tuvanmuahang", "tu_van_ban_hang", "pricing", "bao_gia", "baogia" ->
                            SALES;
                    case "service_support", "servicesupport", "support", "service", "su_dung_dich_vu",
                         "sudungdichvu", "dich_vu", "dichvu", "ky_thuat", "kythuat", "technical",
                         "ho_tro_ky_thuat", "hotrokythuat", "huong_dan", "huongdan" ->
                            SERVICE_SUPPORT;
                    case "warranty_return", "warrantyreturn", "warranty", "return", "refund",
                         "bao_hanh", "baohanh", "doi_tra", "doitra", "hoan_tien", "hoantien",
                         "khieu_nai", "khieunai", "complaint" ->
                            WARRANTY_RETURN;
                    case "order_billing", "orderbilling", "order", "billing", "payment", "shipping",
                         "don_hang", "donhang", "thanh_toan", "thanhtoan", "giao_hang", "giaohang",
                         "van_chuyen", "vanchuyen", "hoa_don", "hoadon" ->
                            ORDER_BILLING;
                    case "general", "tong_hop", "tonghop", "chung", "default", "other" ->
                            GENERAL;
                    default -> null;
                };
                if (matched != null) {
                    return matched;
                }
                CustomerScenario fromExplicitText = detectFromQuestion(explicitScenario);
                if (fromExplicitText != GENERAL) {
                    return fromExplicitText;
                }
            }
        }
        return detectFromQuestion(question);
    }

    /**
     * Phát hiện các kịch bản phụ trong câu hỏi đa ý định (multi-intent), ví dụ khách hàng vừa hỏi
     * mua gói dịch vụ mới vừa hỏi chính sách bảo hành/đổi trả hoặc thanh toán.
     */
    public static List<CustomerScenario> detectSecondaryScenarios(String question, CustomerScenario primary) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String padded = normalizeWordsPadded(question);
        boolean hasUppercaseVat = question.contains("VAT");

        CustomerScenario[] candidates = {SALES, SERVICE_SUPPORT, WARRANTY_RETURN, ORDER_BILLING};
        return Arrays.stream(candidates)
                .filter(sc -> sc != primary && scoreScenario(sc, padded, hasUppercaseVat) >= 2)
                .toList();
    }

    private static CustomerScenario detectFromQuestion(String question) {
        if (question == null || question.isBlank()) {
            return GENERAL;
        }
        String padded = normalizeWordsPadded(question);
        boolean hasUppercaseVat = question.contains("VAT");

        // Thứ tự ưu tiên khi điểm bằng nhau: WARRANTY_RETURN > ORDER_BILLING > SERVICE_SUPPORT > SALES
        CustomerScenario[] order = {WARRANTY_RETURN, ORDER_BILLING, SERVICE_SUPPORT, SALES};
        CustomerScenario best = GENERAL;
        int bestScore = 0;

        for (CustomerScenario sc : order) {
            int score = scoreScenario(sc, padded, hasUppercaseVat);
            if (score > bestScore) {
                bestScore = score;
                best = sc;
            }
        }
        return best;
    }

    private static int scoreScenario(CustomerScenario scenario, String padded, boolean hasUppercaseVat) {
        return switch (scenario) {
            case WARRANTY_RETURN -> {
                int s = 0;
                s += 3 * countMatches(padded,
                        "bao hanh", "doi tra", "hoan tien", "khieu nai", "bi hong", "bi loi",
                        "1 doi 1", "boi thuong", "loi san pham", "loi dich vu");
                s += 2 * countMatches(padded, "warranty", "refund", "return", "complaint");
                yield s;
            }
            case ORDER_BILLING -> {
                int s = 0;
                s += 3 * countMatches(padded,
                        "thanh toan", "chuyen khoan", "hoa don", "xuat vat", "thue vat",
                        "van chuyen", "giao hang", "phi giao", "ship cod", "tra cuu don");
                s += 2 * countMatches(padded,
                        "don hang", "cod", "ship", "payment", "invoice", "billing", "shipping");
                if (hasUppercaseVat || containsPhrase(padded, "co vat")) {
                    s += 2;
                }
                yield s;
            }
            case SERVICE_SUPPORT -> {
                int s = 0;
                s += 3 * countMatches(padded,
                        "cach su dung", "huong dan su dung", "kich hoat", "ho tro ky thuat",
                        "khong vao duoc", "su dung dich vu", "nang cap goi", "cai dat", "cau hinh");
                s += 2 * countMatches(padded,
                        "huong dan", "tich hop", "su co", "dang dung", "tai khoan", "ky thuat");
                yield s;
            }
            case SALES -> {
                // Loại bỏ các cụm từ ghép phổ biến có chữ "gia" nhưng không mang nghĩa giá bán
                String salesCleaned = padded
                        .replace(" tham gia ", " ")
                        .replace(" danh gia ", " ")
                        .replace(" chuyen gia ", " ")
                        .replace(" quoc gia ", " ")
                        .replace(" gia dinh ", " ")
                        .replace(" gia han ", " ");
                int s = 0;
                s += 3 * countMatches(salesCleaned,
                        "muon mua", "dat mua", "tu van mua", "bao gia", "bang gia", "muc gia",
                        "bao nhieu tien", "gia bao nhieu", "tu van goi", "chon goi",
                        "goi basic", "goi pro", "goi enterprise", "chot don", "dat hang");
                s += 2 * countMatches(salesCleaned,
                        "mua", "gia", "khuyen mai", "uu dai", "giam gia", "goi dich vu", "san pham");
                yield s;
            }
            case GENERAL -> 0;
        };
    }

    private static int countMatches(String paddedHaystack, String... phrases) {
        int count = 0;
        for (String phrase : phrases) {
            if (containsPhrase(paddedHaystack, phrase)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsPhrase(String paddedHaystack, String phrase) {
        return paddedHaystack.contains(" " + phrase + " ");
    }

    /**
     * Chuẩn hóa thành câu chỉ gồm chữ cái a-z và số 0-9 cách nhau đúng 1 khoảng trắng,
     * có khoảng trắng ở đầu và cuối để so khớp ranh giới từ (word boundary) chính xác.
     */
    private static String normalizeWordsPadded(String input) {
        String nfd = Normalizer.normalize(input.strip(), Normalizer.Form.NFD);
        String ascii = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip();
        return ascii.isEmpty() ? " " : " " + ascii + " ";
    }

    /**
     * Chuẩn hóa mã kịch bản (alias key): nối các từ bằng dấu gạch dưới '_'.
     */
    private static String normalizeKey(String input) {
        String padded = normalizeWordsPadded(input).strip();
        return padded.replace(' ', '_');
    }
}
