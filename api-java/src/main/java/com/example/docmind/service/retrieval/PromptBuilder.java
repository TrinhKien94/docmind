package com.example.docmind.service.retrieval;

import java.util.List;

/** Dựng prompt RAG từ các chunk đã fuse. */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    /**
     * Prompt gồm chỉ dẫn + ngữ cảnh đánh số [1..n] (để LLM/citation tham chiếu) + câu hỏi.
     * Nhãn "Câu hỏi:" được StubLlmClient dò để echo lại.
     */
    public static String build(String question, List<ReciprocalRankFusion.FusedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý trả lời DỰA HOÀN TOÀN trên ngữ cảnh được cung cấp bên dưới.\n");
        sb.append("Nếu ngữ cảnh không đủ thông tin, hãy nói rõ là không tìm thấy trong tài liệu.\n");
        sb.append("Trả lời ngắn gọn và trích dẫn theo số đoạn [n].\n\n");
        sb.append("NGỮ CẢNH:\n");

        int i = 1;
        for (ReciprocalRankFusion.FusedChunk fc : chunks) {
            sb.append('[').append(i++).append("] ");
            String headingPath = String.join(" > ", fc.chunk().headingPath());
            if (!headingPath.isBlank()) {
                sb.append('(').append(headingPath).append(") ");
            }
            sb.append(fc.chunk().text().strip()).append('\n');
        }

        sb.append("\nCâu hỏi: ").append(question).append('\n');
        sb.append("Trả lời:");
        return sb.toString();
    }
}
