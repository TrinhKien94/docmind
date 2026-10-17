package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình kho tri thức tài liệu nội bộ (Local Knowledge-Base & In-Memory Hybrid RAG Index),
 * ngoại hoá hoàn toàn ra {@code application.properties} / biến môi trường.
 *
 * <p>Cho phép hệ thống tự động nạp các tài liệu SOP/Knowledge-Base (ví dụ: bộ tài liệu chuẩn
 * Christian Dior Vietnam tại {@code docs/dior-knowledge-base}) ngay khi khởi động và hỗ trợ
 * Hybrid Retrieval (Vector Cosine + BM25 tiếng Việt có dấu/bỏ dấu + RRF + Đa dạng hoá tài liệu)
 * ngay cả khi chạy chế độ độc lập (Standalone / không cần PostgreSQL hay doc-service-py).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.knowledge-base")
public class KnowledgeBaseProperties {

    /** Bật/tắt tự động nạp và lập chỉ mục kho tài liệu nội bộ. */
    private boolean enabled = true;

    /**
     * Danh sách đường dẫn thư mục chứa tài liệu kho tri thức (hỗ trợ cả khi chạy từ root repo
     * hoặc từ thư mục con {@code api-java}).
     */
    private List<String> directoryPaths = new ArrayList<>(List.of(
            "docs/dior-knowledge-base",
            "../docs/dior-knowledge-base"
    ));

    /** Các phần mở rộng tệp tài liệu được tự động quét và nạp. */
    private List<String> fileExtensions = new ArrayList<>(List.of(
            ".md", ".markdown", ".txt"
    ));

    /** Số ký tự tối đa trên mỗi chunk khi phân đoạn theo cấu trúc tiêu đề Markdown. */
    private int maxChunkChars = 2200;

    /** Số dòng ước tính trên mỗi trang tài liệu (dùng để gán số trang cho Markdown/Text). */
    private int linesPerPageEstimate = 45;

    /** Tham số k1 của thuật toán xếp hạng toàn văn BM25. */
    private double bm25K1 = 1.5;

    /** Tham số b của thuật toán xếp hạng toàn văn BM25. */
    private double bm25B = 0.75;

    /** Hệ số cộng thưởng khi từ khoá câu hỏi khớp trực tiếp với đường dẫn tiêu đề (heading_path). */
    private double headingMatchBoost = 2.4;

    /** Hệ số cộng thưởng khi cụm từ trong câu hỏi khớp chính xác trong nội dung chunk. */
    private double exactPhraseBoost = 3.2;

    /** Hệ số cộng thưởng khi khớp cụm 2 từ liền kề (bigram tiếng Việt). */
    private double bigramMatchBoost = 1.8;

    /**
     * Giới hạn số chunk tối đa lấy từ cùng 1 tài liệu trong đợt chọn ưu tiên đa dạng hoá đầu tiên
     * khi truy vấn trên nhiều tài liệu (giúp câu hỏi đa ý định lấy được ngữ cảnh từ nhiều tài liệu SOP khác nhau).
     */
    private int maxChunksPerDocumentInTopK = 3;

    /**
     * Thời gian ngắt mạch (circuit-breaker backoff, ms) khi cơ sở dữ liệu PostgreSQL không khả dụng
     * trong chế độ Standalone, giúp hệ thống chuyển thẳng sang LocalKnowledgeBaseService.
     */
    private long dbFailureBackoffMs = 60_000L;

    /** Trọng số điểm RRF cơ sở khi tính điểm đa dạng hoá Query-Coverage MMR. */
    private double mmrRrfScoreWeight = 100.0;

    /** Điểm cộng cho mỗi từ khoá câu hỏi mới chưa được bao phủ bởi các chunk đã chọn trong MMR. */
    private double mmrUncoveredTokenBonus = 0.85;

    /** Điểm cộng cho mỗi cụm 2 từ (bigram) mới chưa được bao phủ trong MMR. */
    private double mmrUncoveredBigramBonus = 1.45;

    /** Mức phạt nhẹ trên mỗi chunk đã lấy từ cùng một tài liệu trong MMR. */
    private double mmrPerDocSoftPenalty = 0.35;

    /** Mức phạt cứng khi số chunk của một tài liệu đã chạm ngưỡng maxChunksPerDocumentInTopK. */
    private double mmrMaxDocHardPenalty = 3.5;

    /** Danh sách từ dừng (stopwords) tiếng Việt bỏ dấu khi tách từ khoá tìm kiếm BM25. */
    private List<String> vietnameseStopwords = new ArrayList<>(List.of(
            "la", "cua", "va", "co", "cho", "thi", "ma", "nhu", "voi", "cac", "nhung",
            "duoc", "khong", "trong", "nay", "do", "khi", "hay", "hoac", "neu", "de",
            "toi", "minh", "em", "anh", "chi", "shop", "ben", "ad", "admin", "hoi",
            "muon", "can", "giup", "xin", "chao", "da", "a", "nha", "nhe", "vay"
    ));

    /**
     * Danh sách quy tắc cộng điểm khớp miền kiến thức (Domain-Specific Intent Alignment Rules)
     * ngoại hoá hoàn toàn ra tệp cấu hình {@code domain-knowledge-pack.json} / {@code application.properties}.
     */
    private List<DomainAlignmentRule> domainAlignmentRules = new ArrayList<>(defaultDomainAlignmentRules());

    public static List<DomainAlignmentRule> defaultDomainAlignmentRules() {
        List<DomainAlignmentRule> fromPack = DomainKnowledgePack.loadDefault().getDomainAlignmentRules();
        return fromPack != null ? List.copyOf(fromPack) : List.of();
    }

    @Getter
    @Setter
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainAlignmentRule {
        @com.fasterxml.jackson.annotation.JsonProperty("name")
        private String name = "";

        @com.fasterxml.jackson.annotation.JsonProperty("filename_keywords")
        private List<String> filenameKeywords = new ArrayList<>();

        @com.fasterxml.jackson.annotation.JsonProperty("query_phrases")
        private List<String> queryPhrases = new ArrayList<>();

        @com.fasterxml.jackson.annotation.JsonProperty("bonus_score")
        private double bonusScore = 2.2;

        public DomainAlignmentRule() {
        }

        public static DomainAlignmentRule of(String name,
                                             List<String> filenameKeywords,
                                             List<String> queryPhrases,
                                             double bonusScore) {
            DomainAlignmentRule rule = new DomainAlignmentRule();
            rule.setName(name);
            rule.setFilenameKeywords(new ArrayList<>(filenameKeywords));
            rule.setQueryPhrases(new ArrayList<>(queryPhrases));
            rule.setBonusScore(bonusScore);
            return rule;
        }
    }
}
