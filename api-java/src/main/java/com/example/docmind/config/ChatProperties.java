package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Tham số luồng chat/RAG (Java-only), ngoại hoá ra application.properties. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.chat")
public class ChatProperties {

    /** Số chunk cuối cùng đưa vào prompt. */
    private int topK = 5;

    /** Số ứng viên lấy mỗi arm (vector / full-text) trước khi RRF fuse. */
    private int candidateK = 20;

    /** Hằng số RRF (thường 60). */
    private int rrfK = 60;

    /** Độ dài tối đa (ký tự) của đoạn trích (snippet) trong citation. */
    private int snippetMaxLength = 240;

    /** Cấu hình từ điển tìm kiếm toàn văn PostgreSQL (to_tsvector / plainto_tsquery). */
    private String ftsConfig = "simple";

    /** Mô tả vai trò hệ thống trong PromptBuilder của luồng hỏi đáp tài liệu RAG. */
    private String systemRoleInstruction =
            "Bạn là trợ lý hỏi-đáp tài liệu. Trả lời bằng tiếng Việt, DỰA HOÀN TOÀN vào phần <DOCUMENT_CONTEXT> bên dưới.";

    /** Câu thông báo khi không tìm thấy thông tin trong tài liệu. */
    private String notFoundMessage = "Không tìm thấy thông tin này trong tài liệu.";

    /** Dòng nhắc cuối prompt RAG. */
    private String answerPromptSuffix = "Trả lời (kèm trích dẫn [n]):";
}
