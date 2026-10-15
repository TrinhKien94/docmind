package com.example.docmind.service.retrieval;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.dto.CustomerPromptTemplateResponse;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Các kịch bản nghiệp vụ (Scenarios) trong bộ prompt được huấn luyện trước
 * dành cho khách hàng mua hàng hoặc sử dụng dịch vụ.
 *
 * <p>Hỗ trợ đọc toàn bộ cấu hình (title, description, playbookInstructions, suggestedQuestions,
 * aliases, highKeywords, mediumKeywords và trọng số chấm điểm) từ {@link CustomerSupportProperties}
 * để có thể tuỳ biến hoàn toàn qua {@code application.properties} / biến môi trường mà không cần sửa code.
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
            ),
            List.of(
                    "sales", "mua_hang", "muahang", "ban_hang", "banhang", "buy", "purchase",
                    "tu_van_mua_hang", "tuvanmuahang", "tu_van_ban_hang", "pricing", "bao_gia", "baogia"
            ),
            List.of(
                    "muon mua", "dat mua", "tu van mua", "bao gia", "bang gia", "muc gia",
                    "bao nhieu tien", "gia bao nhieu", "tu van goi", "chon goi",
                    "goi basic", "goi pro", "goi enterprise", "chot don", "dat hang",
                    "kem nen", "son moi", "son duong", "nuoc hoa", "tinh chat", "kem duong",
                    "sua rua mat", "phan nuoc", "che khuyet diem", "capture totale", "dior prestige",
                    "dior forever", "dior snow", "rouge dior", "lip glow", "lip maximizer",
                    "miss dior", "jadore", "sauvage", "undertone", "chon mau", "hop mau nao",
                    "da dau", "da kho", "da nhay cam", "moi tham", "qua tang", "set qua",
                    "tang vo", "tang ban gai", "my exclusive dior"
            ),
            List.of(
                    "mua", "gia", "khuyen mai", "uu dai", "giam gia", "goi dich vu", "san pham",
                    "son", "serum", "cushion", "skincare", "makeup", "matte", "skin glow",
                    "edp", "edt", "parfum", "elixir", "loyalty", "hang thanh vien"
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
            ),
            List.of(
                    "service_support", "servicesupport", "support", "service", "su_dung_dich_vu",
                    "sudungdichvu", "dich_vu", "dichvu", "ky_thuat", "kythuat", "technical",
                    "ho_tro_ky_thuat", "hotrokythuat", "huong_dan", "huongdan"
            ),
            List.of(
                    "cach su dung", "huong dan su dung", "kich hoat", "ho tro ky thuat",
                    "khong vao duoc", "su dung dich vu", "nang cap goi", "cai dat", "cau hinh",
                    "chu trinh", "routine", "thu tu thoa", "cach thoa", "cach dung", "layering",
                    "thay loi", "loi refill", "kiem tra batch", "batch code", "check batch",
                    "han su dung", "phu nu mang thai", "mang thai", "ba bau", "bau dung duoc khong",
                    "khac ten", "engraving", "cach bao quan"
            ),
            List.of(
                    "huong dan", "tich hop", "su co", "dang dung", "tai khoan", "ky thuat",
                    "refill", "bao quan", "tay trang", "chong nang", "retishot", "retinol"
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
            ),
            List.of(
                    "warranty_return", "warrantyreturn", "warranty", "return", "refund",
                    "bao_hanh", "baohanh", "doi_tra", "doitra", "hoan_tien", "hoantien",
                    "khieu_nai", "khieunai", "complaint"
            ),
            List.of(
                    "bao hanh", "doi tra", "hoan tien", "khieu nai", "bi hong", "bi loi",
                    "1 doi 1", "boi thuong", "loi san pham", "loi dich vu",
                    "di ung", "kich ung", "man do", "ngua rat", "cham chich", "noi mun",
                    "gay son", "chay dau", "do mo hoi", "hong voi xit", "voi xit",
                    "hang gia", "chinh hang", "tem phu", "cosmetovigilance"
            ),
            List.of(
                    "warranty", "refund", "return", "complaint", "doi mau", "doi mui", "vo chai", "ro ri"
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
            ),
            List.of(
                    "order_billing", "orderbilling", "order", "billing", "payment", "shipping",
                    "don_hang", "donhang", "thanh_toan", "thanhtoan", "giao_hang", "giaohang",
                    "van_chuyen", "vanchuyen", "hoa_don", "hoadon"
            ),
            List.of(
                    "thanh toan", "chuyen khoan", "hoa don", "xuat vat", "thue vat",
                    "van chuyen", "giao hang", "phi giao", "ship cod", "tra cuu don",
                    "hoa toc", "giao nhanh 2h", "freeship", "mien phi van chuyen", "tra gop",
                    "dat coc", "hop qua", "goi qua", "thiep chuc", "mau thu", "dong goi",
                    "cach nhiet", "click and collect"
            ),
            List.of(
                    "don hang", "cod", "ship", "payment", "invoice", "billing", "shipping",
                    "vietqr", "the tin dung", "sample"
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
            ),
            List.of(
                    "general", "tong_hop", "tonghop", "chung", "default", "other"
            ),
            List.of(),
            List.of()
    );

    private final String code;
    private final String defaultTitle;
    private final String defaultDescription;
    private final String defaultPlaybookInstructions;
    private final List<String> defaultSuggestedQuestions;
    private final List<String> defaultAliases;
    private final List<String> defaultHighKeywords;
    private final List<String> defaultMediumKeywords;

    CustomerScenario(String code,
                     String defaultTitle,
                     String defaultDescription,
                     String defaultPlaybookInstructions,
                     List<String> defaultSuggestedQuestions,
                     List<String> defaultAliases,
                     List<String> defaultHighKeywords,
                     List<String> defaultMediumKeywords) {
        this.code = code;
        this.defaultTitle = defaultTitle;
        this.defaultDescription = defaultDescription;
        this.defaultPlaybookInstructions = defaultPlaybookInstructions;
        this.defaultSuggestedQuestions = defaultSuggestedQuestions;
        this.defaultAliases = defaultAliases;
        this.defaultHighKeywords = defaultHighKeywords;
        this.defaultMediumKeywords = defaultMediumKeywords;
    }

    public String code() {
        return code;
    }

    public String title() {
        return defaultTitle;
    }

    public String title(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getTitle() != null && !cfg.getTitle().isBlank()) {
            return cfg.getTitle().strip();
        }
        return defaultTitle;
    }

    public String description() {
        return defaultDescription;
    }

    public String description(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getDescription() != null && !cfg.getDescription().isBlank()) {
            return cfg.getDescription().strip();
        }
        return defaultDescription;
    }

    public String playbookInstructions() {
        return defaultPlaybookInstructions;
    }

    public String playbookInstructions(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getPlaybookInstructions() != null && !cfg.getPlaybookInstructions().isBlank()) {
            return cfg.getPlaybookInstructions().strip();
        }
        return defaultPlaybookInstructions;
    }

    public List<String> suggestedQuestions() {
        return defaultSuggestedQuestions;
    }

    public List<String> suggestedQuestions(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getSuggestedQuestions() != null && !cfg.getSuggestedQuestions().isEmpty()) {
            List<String> valid = cfg.getSuggestedQuestions().stream()
                    .filter(q -> q != null && !q.isBlank())
                    .map(String::strip)
                    .toList();
            if (!valid.isEmpty()) {
                return valid;
            }
        }
        return defaultSuggestedQuestions;
    }

    public List<String> aliases(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getAliases() != null && !cfg.getAliases().isEmpty()) {
            return cfg.getAliases();
        }
        return defaultAliases;
    }

    public List<String> highKeywords(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getHighKeywords() != null && !cfg.getHighKeywords().isEmpty()) {
            return cfg.getHighKeywords();
        }
        return defaultHighKeywords;
    }

    public List<String> mediumKeywords(CustomerSupportProperties props) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (cfg != null && cfg.getMediumKeywords() != null && !cfg.getMediumKeywords().isEmpty()) {
            return cfg.getMediumKeywords();
        }
        return defaultMediumKeywords;
    }

    public String defaultEvolutionGuideline(CustomerSupportProperties props, boolean hasRagChunks) {
        CustomerSupportProperties.ScenarioConfig cfg = lookupScenarioConfig(props);
        if (hasRagChunks && cfg != null
                && cfg.getDefaultRagEvolutionGuideline() != null
                && !cfg.getDefaultRagEvolutionGuideline().isBlank()) {
            return cfg.getDefaultRagEvolutionGuideline().strip();
        }
        if (cfg != null && cfg.getDefaultEvolutionGuideline() != null
                && !cfg.getDefaultEvolutionGuideline().isBlank()) {
            return cfg.getDefaultEvolutionGuideline().strip();
        }
        return switch (this) {
            case SALES -> hasRagChunks
                    ? "Khi tư vấn báo giá (SALES), luôn chia sẻ tự nhiên tên sản phẩm/gói dịch vụ, mức giá cụ thể và quyền lợi đi kèm như một chuyên viên thực thụ (không trích dẫn nguồn tài liệu), đồng thời gợi mở nhẹ nhàng để khách hàng dễ lựa chọn."
                    : "Khi tư vấn mua hàng (SALES), hãy phân loại rõ Gói Basic, Gói Pro và Gói Enterprise theo quy mô người dùng bằng giọng văn gần gũi để khách hàng dễ lựa chọn.";
            case SERVICE_SUPPORT ->
                    "Khi hướng dẫn sử dụng dịch vụ hoặc xử lý kỹ thuật (SERVICE_SUPPORT), luôn chỉ dẫn từng bước mạch lạc, dễ hiểu và hỏi thăm xem khách hàng đã thao tác thuận lợi chưa.";
            case WARRANTY_RETURN ->
                    "Khi giải đáp bảo hành & đổi trả (WARRANTY_RETURN), luôn mở đầu bằng sự đồng cảm chân thành, nêu rõ hạn bảo hành 12 tháng và quyền lợi 1 đổi 1 / hoàn tiền trong 7 ngày đầu.";
            case ORDER_BILLING ->
                    "Khi tư vấn đơn hàng & thanh toán (ORDER_BILLING), luôn chia sẻ rõ các phương thức thanh toán (QR Pay, Visa/Mastercard, COD), thời gian kích hoạt và hỗ trợ xuất hóa đơn VAT.";
            case GENERAL ->
                    "Luôn nắm bắt nhanh nhu cầu mua sản phẩm mới hay hỗ trợ dịch vụ đang dùng để trò chuyện tự nhiên, cá nhân hoá câu trả lời và cung cấp Hotline/Email hỗ trợ khi cần.";
        };
    }

    public CustomerPromptTemplateResponse.ScenarioInfo toScenarioInfo() {
        return toScenarioInfo(null);
    }

    public CustomerPromptTemplateResponse.ScenarioInfo toScenarioInfo(CustomerSupportProperties props) {
        return new CustomerPromptTemplateResponse.ScenarioInfo(
                code,
                title(props),
                description(props),
                suggestedQuestions(props)
        );
    }

    public static List<CustomerPromptTemplateResponse.ScenarioInfo> allScenarioInfos() {
        return allScenarioInfos(null);
    }

    public static List<CustomerPromptTemplateResponse.ScenarioInfo> allScenarioInfos(CustomerSupportProperties props) {
        return Arrays.stream(values())
                .map(sc -> sc.toScenarioInfo(props))
                .toList();
    }

    /**
     * Xác định kịch bản tư vấn chính từ tham số truyền vào hoặc tự động nhận diện từ câu hỏi của khách hàng.
     */
    public static CustomerScenario resolve(String explicitScenario, String question) {
        return resolve(explicitScenario, question, null);
    }

    public static CustomerScenario resolve(String explicitScenario,
                                           String question,
                                           CustomerSupportProperties props) {
        if (explicitScenario != null && !explicitScenario.isBlank()) {
            String normKey = normalizeKey(explicitScenario);
            if (!normKey.equals("auto")) {
                for (CustomerScenario sc : values()) {
                    for (String alias : sc.aliases(props)) {
                        if (normKey.equals(normalizeKey(alias))) {
                            return sc;
                        }
                    }
                }
                CustomerScenario fromExplicitText = detectFromQuestion(explicitScenario, props);
                if (fromExplicitText != GENERAL) {
                    return fromExplicitText;
                }
            }
        }
        return detectFromQuestion(question, props);
    }

    /**
     * Phát hiện các kịch bản phụ trong câu hỏi đa ý định (multi-intent).
     */
    public static List<CustomerScenario> detectSecondaryScenarios(String question, CustomerScenario primary) {
        return detectSecondaryScenarios(question, primary, null);
    }

    public static List<CustomerScenario> detectSecondaryScenarios(String question,
                                                                  CustomerScenario primary,
                                                                  CustomerSupportProperties props) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String padded = normalizeWordsPadded(question);
        boolean hasUppercaseVat = question.contains("VAT");
        int threshold = (props != null && props.getScenarioScoring() != null
                && props.getScenarioScoring().getSecondaryScenarioThreshold() > 0)
                ? props.getScenarioScoring().getSecondaryScenarioThreshold()
                : 2;

        CustomerScenario[] candidates = {SALES, SERVICE_SUPPORT, WARRANTY_RETURN, ORDER_BILLING};
        return Arrays.stream(candidates)
                .filter(sc -> sc != primary && scoreScenario(sc, padded, hasUppercaseVat, props) >= threshold)
                .toList();
    }

    private static CustomerScenario detectFromQuestion(String question, CustomerSupportProperties props) {
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
            int score = scoreScenario(sc, padded, hasUppercaseVat, props);
            if (score > bestScore) {
                bestScore = score;
                best = sc;
            }
        }
        return best;
    }

    private static int scoreScenario(CustomerScenario scenario,
                                     String padded,
                                     boolean hasUppercaseVat,
                                     CustomerSupportProperties props) {
        if (scenario == GENERAL) {
            return 0;
        }
        int highWeight = (props != null && props.getScenarioScoring() != null
                && props.getScenarioScoring().getHighKeywordWeight() > 0)
                ? props.getScenarioScoring().getHighKeywordWeight()
                : 3;
        int medWeight = (props != null && props.getScenarioScoring() != null
                && props.getScenarioScoring().getMediumKeywordWeight() > 0)
                ? props.getScenarioScoring().getMediumKeywordWeight()
                : 2;

        String targetText = padded;
        if (scenario == SALES) {
            List<String> excluded = (props != null && props.getScenarioScoring() != null
                    && props.getScenarioScoring().getSalesExcludedPhrases() != null
                    && !props.getScenarioScoring().getSalesExcludedPhrases().isEmpty())
                    ? props.getScenarioScoring().getSalesExcludedPhrases()
                    : List.of("tham gia", "danh gia", "chuyen gia", "quoc gia", "gia dinh", "gia han");
            for (String ex : excluded) {
                String normEx = normalizeWordsPadded(ex).strip();
                if (!normEx.isEmpty()) {
                    targetText = targetText.replace(" " + normEx + " ", " ");
                }
            }
        }

        int score = 0;
        score += highWeight * countMatches(targetText, scenario.highKeywords(props));
        score += medWeight * countMatches(targetText, scenario.mediumKeywords(props));

        if (scenario == ORDER_BILLING && (hasUppercaseVat || containsPhrase(targetText, "co vat"))) {
            score += medWeight;
        }
        return score;
    }

    private static int countMatches(String paddedHaystack, List<String> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String phrase : phrases) {
            if (phrase != null && !phrase.isBlank()) {
                String normPhrase = normalizeWordsPadded(phrase).strip();
                if (!normPhrase.isEmpty() && containsPhrase(paddedHaystack, normPhrase)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean containsPhrase(String paddedHaystack, String phrase) {
        return paddedHaystack.contains(" " + phrase + " ");
    }

    private CustomerSupportProperties.ScenarioConfig lookupScenarioConfig(CustomerSupportProperties props) {
        if (props == null || props.getScenarios() == null || props.getScenarios().isEmpty()) {
            return null;
        }
        Map<String, CustomerSupportProperties.ScenarioConfig> map = props.getScenarios();
        CustomerSupportProperties.ScenarioConfig direct = map.get(code);
        if (direct != null) {
            return direct;
        }
        String lower = code.toLowerCase(Locale.ROOT);
        CustomerSupportProperties.ScenarioConfig byLower = map.get(lower);
        if (byLower != null) {
            return byLower;
        }
        String kebab = lower.replace('_', '-');
        return map.get(kebab);
    }

    /**
     * Chuẩn hóa thành câu chỉ gồm chữ cái a-z và số 0-9 cách nhau đúng 1 khoảng trắng,
     * có khoảng trắng ở đầu và cuối để so khớp ranh giới từ (word boundary) chính xác.
     */
    public static String normalizeWordsPadded(String input) {
        if (input == null || input.isBlank()) {
            return " ";
        }
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
