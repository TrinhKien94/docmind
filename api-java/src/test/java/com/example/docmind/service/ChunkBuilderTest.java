package com.example.docmind.service;

import com.example.docmind.client.model.CanonicalBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test dựng chunk: nhóm theo (page, heading_path), union bbox, kế thừa block ids. */
class ChunkBuilderTest {

    private static CanonicalBlock block(String id, int page, int order, List<String> hp,
                                        double x0, double y0, double x1, double y1, String text) {
        return new CanonicalBlock(id, page, order, "paragraph", text, text, "<p>" + text + "</p>",
                null, List.of(x0, y0, x1, y1), 0.9, hp, "pymupdf", "pymupdf-1.24.0");
    }

    @Test
    void groupsSamePageAndHeadingIntoOneChunkWithUnionBbox() {
        UUID docId = UUID.randomUUID();
        String b1 = UUID.randomUUID().toString();
        String b2 = UUID.randomUUID().toString();
        List<CanonicalBlock> blocks = List.of(
                block(b1, 1, 0, List.of("Intro"), 10, 10, 100, 20, "Hello"),
                block(b2, 1, 1, List.of("Intro"), 10, 30, 120, 40, "World")
        );

        List<ChunkBuilder.BuiltChunk> chunks = ChunkBuilder.build(docId, blocks);

        assertThat(chunks).hasSize(1);
        ChunkBuilder.BuiltChunk c = chunks.get(0);
        assertThat(c.documentId()).isEqualTo(docId);
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.headingPath()).containsExactly("Intro");
        assertThat(c.sourceBlockIds()).containsExactly(UUID.fromString(b1), UUID.fromString(b2));
        // Union bbox: min x0/y0, max x1/y1.
        assertThat(c.bbox()).containsExactly(10.0, 10.0, 120.0, 40.0);
        assertThat(c.text()).isEqualTo("Hello\nWorld");
    }

    @Test
    void splitsOnPageOrHeadingChange() {
        UUID docId = UUID.randomUUID();
        List<CanonicalBlock> blocks = List.of(
                block(UUID.randomUUID().toString(), 1, 0, List.of("A"), 0, 0, 1, 1, "a"),
                block(UUID.randomUUID().toString(), 1, 1, List.of("B"), 0, 0, 1, 1, "b"), // heading đổi
                block(UUID.randomUUID().toString(), 2, 2, List.of("B"), 0, 0, 1, 1, "c")  // page đổi
        );

        List<ChunkBuilder.BuiltChunk> chunks = ChunkBuilder.build(docId, blocks);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).headingPath()).containsExactly("A");
        assertThat(chunks.get(1).headingPath()).containsExactly("B");
        assertThat(chunks.get(1).page()).isEqualTo(1);
        assertThat(chunks.get(2).page()).isEqualTo(2);
    }

    @Test
    void emptyBlocksProduceNoChunks() {
        assertThat(ChunkBuilder.build(UUID.randomUUID(), List.of())).isEmpty();
    }
}
