package com.example.docmind.service;

import com.example.docmind.config.ChatProperties;
import com.example.docmind.dto.ChatRequest;
import com.example.docmind.dto.ChatResponse;
import com.example.docmind.dto.Citation;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.retrieval.PromptBuilder;
import com.example.docmind.service.retrieval.ReciprocalRankFusion;
import com.example.docmind.service.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Luồng chat/RAG CHỈ-JAVA: hybrid retrieval -> RRF rerank -> build prompt -> LLM
 * -> answer + citations -> citation-validate. KHÔNG BAO GIỜ gọi doc-service-py.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RetrievalService retrievalService;
    private final LlmClient llmClient;
    private final ChatProperties chatProps;

    public ChatService(RetrievalService retrievalService, LlmClient llmClient) {
        this(retrievalService, llmClient, new ChatProperties());
    }

    @Autowired
    public ChatService(RetrievalService retrievalService, LlmClient llmClient, ChatProperties chatProps) {
        this.retrievalService = retrievalService;
        this.llmClient = llmClient;
        this.chatProps = chatProps != null ? chatProps : new ChatProperties();
    }

    public ChatResponse chat(ChatRequest request) {
        List<UUID> documentIds = request.documentIds().stream()
                .map(UUID::fromString)
                .toList();

        // 1) Hybrid retrieval + RRF rerank.
        List<ReciprocalRankFusion.FusedChunk> fused =
                retrievalService.retrieve(documentIds, request.question(), request.topK());

        // 2) Build prompt + gọi LLM (9Router mặc định; Gemini hoặc Stub khi cấu hình).
        String prompt = PromptBuilder.build(request.question(), fused, chatProps);
        String answer = llmClient.complete(prompt);
        log.info("stage=llm engine={} fused_chunks={}", llmClient.name(), fused.size());

        // 3) Dựng citations truy ngược block/page/bbox/heading_path.
        List<Citation> citations = new ArrayList<>(fused.size());
        Set<UUID> retrievedIds = new HashSet<>();
        for (ReciprocalRankFusion.FusedChunk fc : fused) {
            RetrievedChunk c = fc.chunk();
            retrievedIds.add(c.chunkId());
            citations.add(new Citation(
                    c.chunkId().toString(),
                    c.documentId().toString(),
                    c.sourceBlockIds() != null
                            ? c.sourceBlockIds().stream().map(UUID::toString).toList()
                            : List.of(),
                    c.page(),
                    toBboxList(c.bbox()),
                    c.headingPath() != null ? c.headingPath() : List.of(),
                    snippet(c.text())
            ));
        }

        // 4) Citation-validate: mọi citation phải trỏ về chunk thực sự được retrieve.
        long invalid = citations.stream()
                .filter(cit -> !retrievedIds.contains(UUID.fromString(cit.chunkId())))
                .count();
        log.info("stage=citation-validate total={} invalid={}", citations.size(), invalid);
        if (invalid > 0) {
            citations.removeIf(cit -> !retrievedIds.contains(UUID.fromString(cit.chunkId())));
        }

        return new ChatResponse(answer, citations);
    }

    private static List<Double> toBboxList(double[] bbox) {
        if (bbox == null) {
            return List.of();
        }
        return Arrays.stream(bbox).boxed().toList();
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        int maxLen = chatProps.getSnippetMaxLength() > 0 ? chatProps.getSnippetMaxLength() : 240;
        String t = text.strip();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "…";
    }
}
