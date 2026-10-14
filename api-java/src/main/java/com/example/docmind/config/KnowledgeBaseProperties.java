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
    private int maxChunkChars = 1400;

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
}
