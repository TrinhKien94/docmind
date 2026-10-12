package com.example.docmind.dto;

import java.util.List;

/** Response của POST /api/chat: câu trả lời + danh sách citation. */
public record ChatResponse(
        String answer,
        List<Citation> citations
) {
}
