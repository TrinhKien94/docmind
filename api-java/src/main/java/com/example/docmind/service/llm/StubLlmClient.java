package com.example.docmind.service.llm;

import com.example.docmind.config.GeminiEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * LLM stub TẤT ĐỊNH: trả câu trả lời template hoá dựa trên prompt (đã chứa các chunk
 * đã truy xuất). Dùng khi CHƯA gắn Gemini key.
 *
 * <p>Active khi gemini.api.keys TRỐNG/vắng (GeminiEnabledCondition.Disabled) — đúng
 * mặc định của task.
 */
@Component
@Conditional(GeminiEnabledCondition.Disabled.class)
public class StubLlmClient implements LlmClient {

    @Override
    public String complete(String prompt) {
        // Prompt đã gồm câu hỏi + các đoạn ngữ cảnh (chunk). Stub tạo câu trả lời
        // tất định, nêu rõ là stub + echo lại câu hỏi để pipeline chat chạy end-to-end.
        String question = extractQuestion(prompt);
        return "[stub-llm] Dựa trên các đoạn tài liệu được trích dẫn, đây là câu trả lời tạm thời"
                + " (chưa gắn Gemini API key). Câu hỏi: \"" + question + "\"."
                + " Vui lòng xem các citation để biết nguồn trong tài liệu.";
    }

    @Override
    public String name() {
        return "stub-llm";
    }

    private String extractQuestion(String prompt) {
        if (prompt == null) {
            return "";
        }
        // Prompt builder đặt câu hỏi sau nhãn "Câu hỏi:". Lấy dòng đó nếu có.
        int idx = prompt.lastIndexOf("Câu hỏi:");
        if (idx >= 0) {
            String tail = prompt.substring(idx + "Câu hỏi:".length()).trim();
            int nl = tail.indexOf('\n');
            return nl >= 0 ? tail.substring(0, nl).trim() : tail;
        }
        return "";
    }
}
