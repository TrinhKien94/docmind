package com.example.docmind.service.retrieval;

import com.example.docmind.repository.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit test tất định cho RRF fusion. */
class ReciprocalRankFusionTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static RetrievedChunk chunk(UUID id, double score) {
        return new RetrievedChunk(id, UUID.randomUUID(), List.of(), List.of(),
                1, new double[]{0, 0, 1, 1}, "text-" + id, score);
    }

    @Test
    void chunkInBothArmsRanksAboveSingleArmChunks() {
        // Arm vector: A, B   | Arm fulltext: A, C
        // A xuất hiện ở cả 2 arm (hạng 1 vector + hạng 1 fulltext) -> điểm cao nhất.
        List<RetrievedChunk> vector = List.of(chunk(A, 0.9), chunk(B, 0.8));
        List<RetrievedChunk> fulltext = List.of(chunk(A, 5.0), chunk(C, 2.0));

        int k = 60;
        List<ReciprocalRankFusion.FusedChunk> fused =
                ReciprocalRankFusion.fuse(List.of(vector, fulltext), k);

        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).chunk().chunkId()).isEqualTo(A);

        // Điểm A = 1/(60+1) + 1/(60+1); B = 1/(60+2); C = 1/(60+2).
        double expectedA = 1.0 / (k + 1) + 1.0 / (k + 1);
        double expectedBorC = 1.0 / (k + 2);
        assertThat(fused.get(0).score()).isEqualTo(expectedA);
        assertThat(fused.get(1).score()).isEqualTo(expectedBorC);
        assertThat(fused.get(2).score()).isEqualTo(expectedBorC);

        // Tie-break tất định theo chunkId: B (…000b) trước C (…000c).
        assertThat(fused.get(1).chunk().chunkId()).isEqualTo(B);
        assertThat(fused.get(2).chunk().chunkId()).isEqualTo(C);
    }

    @Test
    void deterministicAcrossRuns() {
        List<RetrievedChunk> vector = List.of(chunk(A, 0.9), chunk(B, 0.8), chunk(C, 0.7));
        List<RetrievedChunk> fulltext = List.of(chunk(C, 1.0), chunk(B, 0.5));

        List<ReciprocalRankFusion.FusedChunk> first =
                ReciprocalRankFusion.fuse(List.of(vector, fulltext), 60);
        List<ReciprocalRankFusion.FusedChunk> second =
                ReciprocalRankFusion.fuse(List.of(vector, fulltext), 60);

        assertThat(first.stream().map(fc -> fc.chunk().chunkId()).toList())
                .isEqualTo(second.stream().map(fc -> fc.chunk().chunkId()).toList());
    }

    @Test
    void rejectsNonPositiveK() {
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
