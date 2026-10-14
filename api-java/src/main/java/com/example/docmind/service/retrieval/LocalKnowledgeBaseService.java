package com.example.docmind.service.retrieval;

import com.example.docmind.client.model.CanonicalBlock;
import com.example.docmind.client.model.CanonicalDocument;
import com.example.docmind.config.EmbeddingProperties;
import com.example.docmind.config.KnowledgeBaseProperties;
import com.example.docmind.dto.DocumentStatusResponse;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.ChunkBuilder;
import com.example.docmind.service.FileValidation;
import com.example.docmind.service.embedding.EmbeddingClient;
import com.example.docmind.service.embedding.StubEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Kho tri thức tài liệu nội bộ & Động cơ Hybrid RAG In-Memory (Vector Cosine + Vietnamese BM25 + RRF).
 *
 * <p>Tự động nạp bộ tài liệu SOP chuẩn của thương hiệu (ví dụ: bộ 6 tài liệu SOP Tư vấn Mỹ phẩm,
 * Trang điểm, Nước hoa, Bán hàng VIP, Kiểm tra Chính hãng/Đổi trả & Vận chuyển Hỏa tốc của
 * <b>Parfums Christian Dior Vietnam</b> trong {@code docs/dior-knowledge-base}) ngay khi khởi động.
 *
 * <p>Giúp hệ thống truy xuất RAG chính xác tới từng đoạn tài liệu, tiêu đề mục, bảng giá và quy trình
 * ngay cả khi chạy độc lập (Standalone mode không cần PostgreSQL hay doc-service-py).
 */
@Service
public class LocalKnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeBaseService.class);

    private static final Set<String> VIETNAMESE_STOPWORDS = Set.of(
            "la", "cua", "va", "co", "cho", "thi", "ma", "nhu", "voi", "cac", "nhung",
            "duoc", "khong", "trong", "nay", "do", "khi", "hay", "hoac", "neu", "de",
            "toi", "minh", "em", "anh", "chi", "shop", "ben", "ad", "admin", "hoi",
            "muon", "can", "giup", "xin", "chao", "da", "a", "nha", "nhe", "vay"
    );

    private final KnowledgeBaseProperties kbProps;
    private final EmbeddingClient embeddingClient;
    private final StubEmbeddingClient fallbackEmbeddingClient;

    private final Map<UUID, IndexedDocument> documentsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> documentIdBySha256 = new ConcurrentHashMap<>();
    private final List<IndexedChunk> allChunks = new ArrayList<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private double avgChunkTokenLength = 120.0;

    public LocalKnowledgeBaseService() {
        this(new KnowledgeBaseProperties(), new StubEmbeddingClient(new EmbeddingProperties()));
    }

    @Autowired
    public LocalKnowledgeBaseService(KnowledgeBaseProperties kbProps, EmbeddingClient embeddingClient) {
        this.kbProps = kbProps != null ? kbProps : new KnowledgeBaseProperties();
        EmbeddingProperties embProps = new EmbeddingProperties();
        if (embeddingClient != null && embeddingClient.dim() > 0) {
            embProps.setDim(embeddingClient.dim());
        }
        this.fallbackEmbeddingClient = new StubEmbeddingClient(embProps);
        this.embeddingClient = embeddingClient != null ? embeddingClient : this.fallbackEmbeddingClient;
        reloadDefaultKnowledgeBase();
    }

    /**
     * Quét và nạp các tệp tài liệu SOP từ các thư mục cấu hình trong {@link KnowledgeBaseProperties}.
     */
    public final synchronized void reloadDefaultKnowledgeBase() {
        if (!kbProps.isEnabled()) {
            log.info("LocalKnowledgeBaseService is disabled via configuration.");
            return;
        }
        List<String> dirs = kbProps.getDirectoryPaths() != null ? kbProps.getDirectoryPaths() : List.of();
        int loadedDocs = 0;

        for (String rawDir : dirs) {
            if (rawDir == null || rawDir.isBlank()) {
                continue;
            }
            Path dirPath = Path.of(rawDir.strip()).toAbsolutePath().normalize();
            if (!Files.isDirectory(dirPath)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dirPath)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(this::isSupportedExtension)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();

                for (Path file : files) {
                    try {
                        byte[] bytes = Files.readAllBytes(file);
                        String sha256 = FileValidation.sha256Hex(bytes);
                        if (documentIdBySha256.containsKey(sha256)) {
                            continue;
                        }
                        String filename = file.getFileName().toString();
                        UUID docId = deterministicDocumentId(filename);
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        indexMarkdownOrTextDocumentInternal(docId, filename, sha256, content, "dior_sop_kb");
                        loadedDocs++;
                    } catch (IOException e) {
                        log.warn("Không đọc được tài liệu KB {}: {}", file, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.warn("Không thể quét thư mục KB {}: {}", dirPath, e.getMessage());
            }
        }

        recomputeCorpusStats();
        if (loadedDocs > 0) {
            log.info("Loaded {} local knowledge-base documents ({} indexed chunks) into Hybrid RAG memory.",
                    documentsById.size(), allChunks.size());
        }
    }

    /**
     * Lập chỉ mục tức thì cho tài liệu người dùng upload (dùng khi chạy Standalone không có DB/doc-service-py
     * hoặc để bổ sung vào bộ nhớ truy xuất nhanh).
     */
    public synchronized DocumentStatusResponse indexUploadedBytes(UUID preferredDocId,
                                                                  String filename,
                                                                  String sha256,
                                                                  byte[] bytes) {
        if (sha256 != null && documentIdBySha256.containsKey(sha256)) {
            UUID existingId = documentIdBySha256.get(sha256);
            IndexedDocument existingDoc = documentsById.get(existingId);
            if (existingDoc != null) {
                return existingDoc.toStatusResponse();
            }
        }
        UUID docId = preferredDocId != null ? preferredDocId : deterministicDocumentId(filename);
        String textContent = bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "";
        IndexedDocument indexed = indexMarkdownOrTextDocumentInternal(
                docId,
                filename != null ? filename : "uploaded-document.md",
                sha256 != null ? sha256 : FileValidation.sha256Hex(bytes != null ? bytes : new byte[0]),
                textContent,
                "local_upload_parser"
        );
        recomputeCorpusStats();
        return indexed.toStatusResponse();
    }

    /**
     * Lập chỉ mục từ {@link CanonicalDocument} đã qua parser.
     */
    public synchronized DocumentStatusResponse indexCanonicalDocument(UUID documentId,
                                                                      String filename,
                                                                      String sha256,
                                                                      CanonicalDocument result) {
        if (result == null || result.blocks() == null || result.blocks().isEmpty()) {
            return indexUploadedBytes(documentId, filename, sha256, new byte[0]);
        }
        List<ChunkBuilder.BuiltChunk> builtChunks = ChunkBuilder.build(documentId, result.blocks());
        allChunks.removeIf(c -> c.documentId().equals(documentId));

        int maxPage = 1;
        for (ChunkBuilder.BuiltChunk bc : builtChunks) {
            maxPage = Math.max(maxPage, bc.page());
            List<String> headingPath = bc.headingPath() != null ? List.of(bc.headingPath()) : List.of();
            List<UUID> blockIds = bc.sourceBlockIds() != null ? List.of(bc.sourceBlockIds()) : List.of();
            String searchableText = buildSearchableChunkText(filename, headingPath, bc.text());
            float[] vector = safeEmbed(searchableText);
            allChunks.add(createIndexedChunk(
                    bc.id(),
                    documentId,
                    filename,
                    headingPath,
                    blockIds,
                    bc.page(),
                    bc.bbox() != null ? bc.bbox() : new double[]{0, 0, 0, 0},
                    bc.text(),
                    vector
            ));
        }

        int pages = result.source() != null && result.source().pages() > 0 ? result.source().pages() : maxPage;
        Map<String, Integer> breakdown = result.stats() != null && result.stats().engineBreakdown() != null
                ? result.stats().engineBreakdown()
                : Map.of("canonical_blocks", result.blocks().size(), "indexed_chunks", builtChunks.size());

        IndexedDocument doc = new IndexedDocument(
                documentId,
                filename != null ? filename : documentId.toString(),
                sha256 != null ? sha256 : "",
                "READY",
                pages,
                breakdown
        );
        documentsById.put(documentId, doc);
        if (sha256 != null && !sha256.isBlank()) {
            documentIdBySha256.put(sha256, documentId);
        }
        recomputeCorpusStats();
        return doc.toStatusResponse();
    }

    public List<DocumentStatusResponse> listDocuments() {
        return documentsById.values().stream()
                .sorted(Comparator.comparing(IndexedDocument::filename))
                .map(IndexedDocument::toStatusResponse)
                .toList();
    }

    public List<UUID> listReadyDocumentIds() {
        return documentsById.values().stream()
                .filter(d -> "READY".equalsIgnoreCase(d.status()))
                .sorted(Comparator.comparing(IndexedDocument::filename))
                .map(IndexedDocument::id)
                .toList();
    }

    public Optional<DocumentStatusResponse> findDocumentStatus(UUID documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        IndexedDocument doc = documentsById.get(documentId);
        return doc != null ? Optional.of(doc.toStatusResponse()) : Optional.empty();
    }

    public Optional<DocumentStatusResponse> findBySha256(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return Optional.empty();
        }
        UUID docId = documentIdBySha256.get(sha256);
        return docId != null ? findDocumentStatus(docId) : Optional.empty();
    }

    public boolean hasDocuments() {
        return !documentsById.isEmpty();
    }

    /**
     * Arm 1: Tìm kiếm theo độ tương đồng Cosine Vector trên các chunk thuộc {@code documentIds}.
     */
    public synchronized List<RetrievedChunk> searchByVector(List<UUID> documentIds,
                                                            float[] queryEmbedding,
                                                            int candidateK) {
        if (allChunks.isEmpty()) {
            return List.of();
        }
        float[] queryVec = (queryEmbedding != null && queryEmbedding.length > 0)
                ? queryEmbedding
                : fallbackEmbeddingClient.embed("");
        Set<UUID> allowedDocs = (documentIds != null && !documentIds.isEmpty())
                ? new HashSet<>(documentIds)
                : null;
        int limit = Math.max(1, candidateK);

        return allChunks.stream()
                .filter(c -> allowedDocs == null || allowedDocs.contains(c.documentId()))
                .map(c -> {
                    double sim = cosineSimilarity(queryVec, c.embedding());
                    return new RetrievedChunk(
                            c.chunkId(),
                            c.documentId(),
                            c.headingPath(),
                            c.sourceBlockIds(),
                            c.page(),
                            c.bbox(),
                            c.text(),
                            sim
                    );
                })
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Arm 2: Tìm kiếm toàn văn BM25 tiếng Việt (kết hợp có dấu + bỏ dấu + Bigram + khớp tiêu đề mục).
     */
    public synchronized List<RetrievedChunk> searchByFullText(List<UUID> documentIds,
                                                              String question,
                                                              int candidateK) {
        if (allChunks.isEmpty() || question == null || question.isBlank()) {
            return List.of();
        }
        Set<UUID> allowedDocs = (documentIds != null && !documentIds.isEmpty())
                ? new HashSet<>(documentIds)
                : null;
        int limit = Math.max(1, candidateK);

        List<String> queryTokens = extractContentTokens(question);
        List<String> queryBigrams = extractBigrams(queryTokens);
        List<String> queryTrigrams = extractTrigrams(queryTokens);
        String paddedQueryNorm = CustomerScenario.normalizeWordsPadded(question);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        int totalDocs = Math.max(1, allChunks.size());
        double k1 = kbProps.getBm25K1() > 0 ? kbProps.getBm25K1() : 1.5;
        double b = kbProps.getBm25B() >= 0 ? kbProps.getBm25B() : 0.75;
        double headingBoost = kbProps.getHeadingMatchBoost() > 0 ? kbProps.getHeadingMatchBoost() : 2.4;
        double bigramBoost = kbProps.getBigramMatchBoost() > 0 ? kbProps.getBigramMatchBoost() : 1.8;
        double phraseBoost = kbProps.getExactPhraseBoost() > 0 ? kbProps.getExactPhraseBoost() : 3.2;

        List<RetrievedChunk> scored = new ArrayList<>();
        for (IndexedChunk chunk : allChunks) {
            if (allowedDocs != null && !allowedDocs.contains(chunk.documentId())) {
                continue;
            }
            double score = 0.0;
            int docLen = Math.max(1, chunk.totalTokens());

            // 1. Điểm BM25 trên từng từ khoá nội dung
            for (String qTok : queryTokens) {
                int tf = chunk.tokenFrequencies().getOrDefault(qTok, 0);
                if (tf > 0) {
                    int df = documentFrequency.getOrDefault(qTok, 1);
                    double idf = Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));
                    double tfNorm = (tf * (k1 + 1.0))
                            / (tf + k1 * (1.0 - b + b * (docLen / Math.max(1.0, avgChunkTokenLength))));
                    score += idf * tfNorm;
                }
                if (chunk.normalizedLeafHeading().contains(" " + qTok + " ")) {
                    score += headingBoost * 0.85;
                } else if (chunk.normalizedHeading().contains(" " + qTok + " ")) {
                    score += headingBoost * 0.15;
                }
            }

            // 2. Điểm cộng khi khớp cụm 2 từ liền kề (Bigram tiếng Việt & tên sản phẩm Dior)
            for (String bg : queryBigrams) {
                String spacedBg = bg.replace('_', ' ');
                if (chunk.normalizedText().contains(" " + spacedBg + " ")) {
                    score += bigramBoost;
                }
                if (chunk.normalizedLeafHeading().contains(" " + spacedBg + " ")) {
                    score += headingBoost * 1.35;
                } else if (chunk.normalizedHeading().contains(" " + spacedBg + " ")) {
                    score += headingBoost * 0.25;
                }
            }

            // 3. Điểm cộng khi khớp cụm 3 từ liền kề (Exact Phrase Match)
            for (String tg : queryTrigrams) {
                if (chunk.normalizedText().contains(" " + tg + " ")) {
                    score += phraseBoost;
                }
                if (chunk.normalizedLeafHeading().contains(" " + tg + " ")) {
                    score += phraseBoost * 1.4;
                } else if (chunk.normalizedHeading().contains(" " + tg + " ")) {
                    score += phraseBoost * 0.3;
                }
            }

            // 4. Điểm cộng theo miền kiến thức (Domain-Specific Intent Alignment)
            score += computeDomainAlignmentBonus(paddedQueryNorm, chunk);

            if (score > 0.0) {
                scored.add(new RetrievedChunk(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.headingPath(),
                        chunk.sourceBlockIds(),
                        chunk.page(),
                        chunk.bbox(),
                        chunk.text(),
                        score
                ));
            }
        }

        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return scored.size() > limit ? scored.subList(0, limit) : scored;
    }

    /**
     * Đa dạng hoá kết quả sau khi RRF fuse theo cả Độ phủ Từ khoá Câu hỏi (Query-Coverage MMR)
     * lẫn Đa dạng hoá Tài liệu (Cross-Document Diversity) để câu hỏi đa ý định lấy trọn vẹn
     * tất cả các mục sản phẩm và chính sách được hỏi.
     */
    public List<ReciprocalRankFusion.FusedChunk> diversifyTopChunks(
            List<ReciprocalRankFusion.FusedChunk> fused,
            int finalK) {
        return diversifyTopChunks(fused, null, finalK);
    }

    public List<ReciprocalRankFusion.FusedChunk> diversifyTopChunks(
            List<ReciprocalRankFusion.FusedChunk> fused,
            String question,
            int finalK) {
        if (fused == null || fused.size() <= finalK) {
            return fused != null ? fused : List.of();
        }
        int maxPerDoc = kbProps.getMaxChunksPerDocumentInTopK() > 0
                ? kbProps.getMaxChunksPerDocumentInTopK()
                : 3;

        List<String> queryTokens = extractContentTokens(question);
        List<String> queryBigrams = extractBigrams(queryTokens);

        List<ReciprocalRankFusion.FusedChunk> remaining = new ArrayList<>(fused);
        List<ReciprocalRankFusion.FusedChunk> selected = new ArrayList<>(finalK);
        Map<UUID, Integer> countByDoc = new HashMap<>();
        Set<String> coveredFeatures = new HashSet<>();

        while (selected.size() < finalK && !remaining.isEmpty()) {
            int bestIdx = 0;
            double bestMmrScore = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < remaining.size(); i++) {
                ReciprocalRankFusion.FusedChunk candidate = remaining.get(i);
                UUID docId = candidate.chunk().documentId();
                int docCount = countByDoc.getOrDefault(docId, 0);

                String leafHeading = (candidate.chunk().headingPath() != null && !candidate.chunk().headingPath().isEmpty())
                        ? candidate.chunk().headingPath().get(candidate.chunk().headingPath().size() - 1)
                        : "";
                String normCandidate = CustomerScenario.normalizeWordsPadded(leafHeading + " " + candidate.chunk().text());

                int newUncoveredTokens = 0;
                for (String qt : queryTokens) {
                    if (!coveredFeatures.contains("tok:" + qt) && normCandidate.contains(" " + qt + " ")) {
                        newUncoveredTokens++;
                    }
                }
                int newUncoveredBigrams = 0;
                for (String bg : queryBigrams) {
                    String spacedBg = bg.replace('_', ' ');
                    if (!coveredFeatures.contains("bg:" + bg) && normCandidate.contains(" " + spacedBg + " ")) {
                        newUncoveredBigrams++;
                    }
                }

                double docPenalty = docCount >= maxPerDoc ? 3.5 : (docCount * 0.35);
                double mmrScore = (candidate.score() * 100.0)
                        + (newUncoveredTokens * 0.85)
                        + (newUncoveredBigrams * 1.45)
                        - docPenalty;

                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore;
                    bestIdx = i;
                }
            }

            ReciprocalRankFusion.FusedChunk chosen = remaining.remove(bestIdx);
            selected.add(chosen);
            countByDoc.merge(chosen.chunk().documentId(), 1, Integer::sum);

            String chosenLeaf = (chosen.chunk().headingPath() != null && !chosen.chunk().headingPath().isEmpty())
                    ? chosen.chunk().headingPath().get(chosen.chunk().headingPath().size() - 1)
                    : "";
            String normChosen = CustomerScenario.normalizeWordsPadded(chosenLeaf + " " + chosen.chunk().text());
            for (String qt : queryTokens) {
                if (normChosen.contains(" " + qt + " ")) {
                    coveredFeatures.add("tok:" + qt);
                }
            }
            for (String bg : queryBigrams) {
                String spacedBg = bg.replace('_', ' ');
                if (normChosen.contains(" " + spacedBg + " ")) {
                    coveredFeatures.add("bg:" + bg);
                }
            }
        }

        return selected;
    }

    public static UUID deterministicDocumentId(String filename) {
        String key = "docmind-kb-doc:" + (filename != null ? filename.strip().toLowerCase(Locale.ROOT) : "unknown");
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    // =========================================================================
    // Nội bộ: Phân đoạn Markdown theo Heading Hierarchy & Tính toán thống kê BM25
    // =========================================================================

    private IndexedDocument indexMarkdownOrTextDocumentInternal(UUID docId,
                                                                String filename,
                                                                String sha256,
                                                                String rawContent,
                                                                String engineName) {
        allChunks.removeIf(c -> c.documentId().equals(docId));
        List<ParsedSection> sections = parseMarkdownSections(rawContent, kbProps.getMaxChunkChars());
        int linesPerPage = kbProps.getLinesPerPageEstimate() > 0 ? kbProps.getLinesPerPageEstimate() : 45;

        int maxPage = 1;
        int chunkIdx = 0;
        for (ParsedSection sec : sections) {
            int page = Math.max(1, ((sec.startLine() - 1) / linesPerPage) + 1);
            maxPage = Math.max(maxPage, page);

            UUID chunkId = UUID.nameUUIDFromBytes(
                    ("docmind-kb-chunk:" + docId + ":" + chunkIdx).getBytes(StandardCharsets.UTF_8));
            UUID blockId = UUID.nameUUIDFromBytes(
                    ("docmind-kb-block:" + docId + ":" + chunkIdx).getBytes(StandardCharsets.UTF_8));
            double y0 = 60.0 + (chunkIdx % 6) * 110.0;
            double[] bbox = new double[]{48.0, y0, 547.0, y0 + 95.0};

            String searchableText = buildSearchableChunkText(filename, sec.headingPath(), sec.text());
            float[] vector = safeEmbed(searchableText);

            allChunks.add(createIndexedChunk(
                    chunkId,
                    docId,
                    filename,
                    sec.headingPath(),
                    List.of(blockId),
                    page,
                    bbox,
                    sec.text(),
                    vector
            ));
            chunkIdx++;
        }

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put(engineName, sections.size());
        IndexedDocument indexedDoc = new IndexedDocument(
                docId,
                filename,
                sha256,
                "READY",
                maxPage,
                Map.copyOf(breakdown)
        );
        documentsById.put(docId, indexedDoc);
        if (sha256 != null && !sha256.isBlank()) {
            documentIdBySha256.put(sha256, docId);
        }
        return indexedDoc;
    }

    private IndexedChunk createIndexedChunk(UUID chunkId,
                                            UUID documentId,
                                            String filename,
                                            List<String> headingPath,
                                            List<UUID> sourceBlockIds,
                                            int page,
                                            double[] bbox,
                                            String text,
                                            float[] embedding) {
        String joinedHeading = headingPath != null ? String.join(" > ", headingPath) : "";
        String leafHeading = (headingPath != null && !headingPath.isEmpty())
                ? headingPath.get(headingPath.size() - 1)
                : "";
        String normHeading = CustomerScenario.normalizeWordsPadded(filename + " " + joinedHeading);
        String normLeafHeading = CustomerScenario.normalizeWordsPadded(leafHeading);
        String normText = CustomerScenario.normalizeWordsPadded(text);

        List<String> tokens = extractContentTokens(leafHeading + " " + text);
        Map<String, Integer> freq = new HashMap<>();
        for (String tok : tokens) {
            freq.merge(tok, 1, Integer::sum);
        }

        return new IndexedChunk(
                chunkId,
                documentId,
                filename,
                headingPath != null ? List.copyOf(headingPath) : List.of(),
                sourceBlockIds != null ? List.copyOf(sourceBlockIds) : List.of(),
                page,
                bbox,
                text,
                embedding,
                normHeading,
                normLeafHeading,
                normText,
                Map.copyOf(freq),
                tokens.size()
        );
    }

    private void recomputeCorpusStats() {
        documentFrequency.clear();
        if (allChunks.isEmpty()) {
            avgChunkTokenLength = 120.0;
            return;
        }
        long totalTokens = 0;
        for (IndexedChunk chunk : allChunks) {
            totalTokens += chunk.totalTokens();
            for (String uniqueTok : chunk.tokenFrequencies().keySet()) {
                documentFrequency.merge(uniqueTok, 1, Integer::sum);
            }
        }
        avgChunkTokenLength = Math.max(20.0, (double) totalTokens / allChunks.size());
    }

    private static List<ParsedSection> parseMarkdownSections(String content, int maxCharsPerChunk) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int limitChars = maxCharsPerChunk > 200 ? maxCharsPerChunk : 1400;
        String[] lines = content.split("\\r?\\n");

        String h1 = null;
        String h2 = null;
        String h3 = null;
        List<String> currentHeadingPath = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean hasBodyContent = false;
        int sectionStartLine = 1;
        List<ParsedSection> sections = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            String trimmed = rawLine.strip();
            int lineNum = i + 1;

            if (trimmed.equals("---")) {
                continue;
            }

            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                String headingTitle = trimmed.substring(level).strip();
                if (!headingTitle.isEmpty() && level <= 3) {
                    if (hasBodyContent && !buffer.toString().isBlank()) {
                        flushSectionChunks(sections, currentHeadingPath, buffer.toString().strip(), sectionStartLine, limitChars);
                    }
                    buffer.setLength(0);
                    hasBodyContent = false;

                    if (level == 1) {
                        h1 = headingTitle;
                        h2 = null;
                        h3 = null;
                    } else if (level == 2) {
                        h2 = headingTitle;
                        h3 = null;
                    } else {
                        h3 = headingTitle;
                    }
                    currentHeadingPath = buildHeadingList(h1, h2, h3);
                    sectionStartLine = lineNum;
                    buffer.append(headingTitle).append("\n");
                    continue;
                }
            }

            if (buffer.isEmpty()) {
                sectionStartLine = lineNum;
            }
            if (!trimmed.isEmpty()) {
                hasBodyContent = true;
            }
            buffer.append(rawLine).append("\n");
        }

        if (hasBodyContent && !buffer.toString().isBlank()) {
            flushSectionChunks(sections, currentHeadingPath, buffer.toString().strip(), sectionStartLine, limitChars);
        }
        return sections;
    }

    private static void flushSectionChunks(List<ParsedSection> out,
                                           List<String> headingPath,
                                           String sectionText,
                                           int startLine,
                                           int maxChars) {
        List<String> safeHeading = headingPath != null && !headingPath.isEmpty()
                ? List.copyOf(headingPath)
                : List.of("Tổng quan tài liệu");
        if (sectionText.length() <= maxChars) {
            out.add(new ParsedSection(safeHeading, sectionText, startLine));
            return;
        }

        String[] paragraphs = sectionText.split("\\n\\s*\\n");
        StringBuilder chunkBuf = new StringBuilder();
        String sectionTitlePrefix = safeHeading.get(safeHeading.size() - 1);

        for (String para : paragraphs) {
            String cleanPara = para.strip();
            if (cleanPara.isEmpty()) {
                continue;
            }
            if (chunkBuf.length() + cleanPara.length() + 2 > maxChars && !chunkBuf.isEmpty()) {
                out.add(new ParsedSection(safeHeading, chunkBuf.toString().strip(), startLine));
                chunkBuf.setLength(0);
                chunkBuf.append("[").append(sectionTitlePrefix).append("]\n");
            }
            if (!chunkBuf.isEmpty()) {
                chunkBuf.append("\n\n");
            }
            chunkBuf.append(cleanPara);
        }
        if (!chunkBuf.isEmpty()) {
            out.add(new ParsedSection(safeHeading, chunkBuf.toString().strip(), startLine));
        }
    }

    private static List<String> buildHeadingList(String h1, String h2, String h3) {
        List<String> list = new ArrayList<>(3);
        if (h1 != null && !h1.isBlank()) {
            list.add(h1);
        }
        if (h2 != null && !h2.isBlank()) {
            list.add(h2);
        }
        if (h3 != null && !h3.isBlank()) {
            list.add(h3);
        }
        return list;
    }

    private static String buildSearchableChunkText(String filename, List<String> headingPath, String text) {
        StringBuilder sb = new StringBuilder();
        if (filename != null && !filename.isBlank()) {
            sb.append(filename).append(" ");
        }
        if (headingPath != null && !headingPath.isEmpty()) {
            sb.append(String.join(" > ", headingPath)).append("\n");
        }
        if (text != null) {
            sb.append(text);
        }
        return sb.toString();
    }

    private float[] safeEmbed(String text) {
        try {
            float[] v = embeddingClient.embed(text);
            if (v != null && v.length > 0) {
                return v;
            }
        } catch (Exception e) {
            log.debug("Primary embedding failed ({}); using fallback stub embedding", e.getMessage());
        }
        return fallbackEmbeddingClient.embed(text);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
        int len = Math.min(a.length, b.length);
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static List<String> extractContentTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String nfd = Normalizer.normalize(text.strip(), Normalizer.Form.NFD);
        String ascii = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String tok : ascii.split("[^a-z0-9]+")) {
            if (tok.length() >= 2 && !VIETNAMESE_STOPWORDS.contains(tok)) {
                out.add(tok);
            }
        }
        return out;
    }

    private static List<String> extractBigrams(List<String> tokens) {
        if (tokens == null || tokens.size() < 2) {
            return List.of();
        }
        List<String> bigrams = new ArrayList<>(tokens.size() - 1);
        for (int i = 0; i + 1 < tokens.size(); i++) {
            bigrams.add(tokens.get(i) + "_" + tokens.get(i + 1));
        }
        return bigrams;
    }

    private static List<String> extractTrigrams(List<String> tokens) {
        if (tokens == null || tokens.size() < 3) {
            return List.of();
        }
        List<String> trigrams = new ArrayList<>(tokens.size() - 2);
        for (int i = 0; i + 2 < tokens.size(); i++) {
            trigrams.add(tokens.get(i) + " " + tokens.get(i + 1) + " " + tokens.get(i + 2));
        }
        return trigrams;
    }

    private static double computeDomainAlignmentBonus(String paddedQueryNorm, IndexedChunk chunk) {
        double bonus = 0.0;
        String docName = chunk.filename() != null ? chunk.filename().toLowerCase(Locale.ROOT) : "";

        // Skincare & Thai kỳ / Da nhạy cảm / Lão hóa -> SOP 01
        if (containsAny(paddedQueryNorm, "da dau", "da kho", "da nhay cam", "lao hoa", "nam", "mang thai",
                "bau", "capture totale", "prestige", "dior snow", "la mousse", "retishot", "serum", "kem duong")) {
            if (docName.contains("skincare")) {
                bonus += 2.2;
            }
        }
        // Makeup, Kem nền, Bảng màu son, Undertone -> SOP 02
        if (containsAny(paddedQueryNorm, "kem nen", "undertone", "cushion", "son", "rouge dior", "lip glow",
                "maximizer", "moi tham", "che khuyet diem", "phan", "matte", "skin glow", "999", "720", "772", "100")) {
            if (docName.contains("makeup")) {
                bonus += 2.2;
            }
        }
        // Nước hoa, Quà tặng, Khắc tên Engraving -> SOP 03
        if (containsAny(paddedQueryNorm, "nuoc hoa", "miss dior", "jadore", "sauvage", "elixir", "gris dior",
                "khac ten", "engraving", "qua tang", "tang vo", "tang ban gai", "hop qua", "sample", "mau thu")) {
            if (docName.contains("fragrance") || docName.contains("gift")) {
                bonus += 2.2;
            }
        }
        // Chốt sale, Hạng thành viên VIP Loyalty, Try First -> SOP 04
        if (containsAny(paddedQueryNorm, "hang thanh vien", "loyalty", "tich diem", "white", "silver", "gold",
                "platinum", "spa", "facial", "try first", "upsell", "cross sell")) {
            if (docName.contains("sales-closing") || docName.contains("loyalty")) {
                bonus += 2.2;
            }
        }
        // Chính hãng, Batch code, Đổi trả, Dị ứng / Kích ứng, Gãy son, Hỏng vòi xịt -> SOP 05
        if (containsAny(paddedQueryNorm, "chinh hang", "batch code", "tem phu", "doi tra", "bao hanh", "hoan tien",
                "di ung", "kich ung", "man do", "ngua", "gay son", "chay dau", "do mo hoi", "voi xit", "cosmetovigilance")) {
            if (docName.contains("authenticity") || docName.contains("warranty") || docName.contains("return")) {
                bonus += 2.6;
            }
        }
        // Thanh toán, Trả góp, Đặt cọc, Hóa đơn VAT, Vận chuyển Hỏa tốc 2h, Đóng gói cách nhiệt -> SOP 06
        if (containsAny(paddedQueryNorm, "thanh toan", "tra gop", "dat coc", "cod", "hoa don", "vat",
                "van chuyen", "giao hang", "hoa toc", "2h", "2 gio", "freeship", "dong goi", "cach nhiet")) {
            if (docName.contains("order") || docName.contains("payment") || docName.contains("delivery")) {
                bonus += 2.4;
            }
        }
        return bonus;
    }

    private static boolean containsAny(String paddedHaystack, String... phrases) {
        for (String p : phrases) {
            if (paddedHaystack.contains(" " + p + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Collection<String> exts = kbProps.getFileExtensions() != null
                ? kbProps.getFileExtensions()
                : List.of(".md", ".markdown", ".txt");
        for (String ext : exts) {
            if (ext != null && !ext.isBlank() && name.endsWith(ext.strip().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record ParsedSection(
            List<String> headingPath,
            String text,
            int startLine
    ) {
    }

    private record IndexedDocument(
            UUID id,
            String filename,
            String sha256,
            String status,
            int pages,
            Map<String, Integer> engineBreakdown
    ) {
        DocumentStatusResponse toStatusResponse() {
            return new DocumentStatusResponse(
                    id.toString(),
                    filename,
                    status,
                    pages,
                    engineBreakdown
            );
        }
    }

    private record IndexedChunk(
            UUID chunkId,
            UUID documentId,
            String filename,
            List<String> headingPath,
            List<UUID> sourceBlockIds,
            int page,
            double[] bbox,
            String text,
            float[] embedding,
            String normalizedHeading,
            String normalizedLeafHeading,
            String normalizedText,
            Map<String, Integer> tokenFrequencies,
            int totalTokens
    ) {
    }
}
