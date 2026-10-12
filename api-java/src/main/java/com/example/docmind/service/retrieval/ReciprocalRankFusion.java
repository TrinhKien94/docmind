package com.example.docmind.service.retrieval;

import com.example.docmind.repository.RetrievedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal Rank Fusion (RRF) hợp nhất nhiều danh sách xếp hạng.
 *
 * <p>Công thức: score(d) = sum_over_arms( 1 / (k + rank_arm(d)) ), rank 1-based.
 * k là hằng số (thường 60) giảm ảnh hưởng của hạng thấp. Chunk xuất hiện ở nhiều
 * arm với hạng cao sẽ được đẩy lên. TẤT ĐỊNH: cùng input -> cùng thứ tự.
 */
public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    /**
     * Fuse nhiều ranked list thành 1 list đã sắp theo RRF giảm dần.
     *
     * @param rankedLists mỗi list đã sắp theo độ liên quan giảm dần của 1 arm.
     * @param k           hằng số RRF (>0).
     * @return danh sách chunk duy nhất theo id, sắp theo RRF score giảm dần.
     *         Khi hoà điểm, tie-break theo chunkId để tất định.
     */
    public static List<FusedChunk> fuse(List<List<RetrievedChunk>> rankedLists, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("RRF k phải > 0");
        }
        Map<UUID, FusedAccumulator> acc = new LinkedHashMap<>();

        for (List<RetrievedChunk> list : rankedLists) {
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                RetrievedChunk rc = list.get(i);
                int rank = i + 1; // 1-based
                double contribution = 1.0 / (k + rank);
                acc.computeIfAbsent(rc.chunkId(), id -> new FusedAccumulator(rc))
                        .add(contribution);
            }
        }

        List<FusedChunk> fused = new ArrayList<>(acc.size());
        for (FusedAccumulator a : acc.values()) {
            fused.add(new FusedChunk(a.chunk, a.score));
        }

        fused.sort(Comparator
                .comparingDouble(FusedChunk::score).reversed()
                .thenComparing(fc -> fc.chunk().chunkId()));
        return fused;
    }

    /** Chunk kèm điểm RRF sau fusion. */
    public record FusedChunk(RetrievedChunk chunk, double score) {
    }

    private static final class FusedAccumulator {
        final RetrievedChunk chunk;
        double score;

        FusedAccumulator(RetrievedChunk chunk) {
            this.chunk = chunk;
        }

        void add(double contribution) {
            this.score += contribution;
        }
    }
}
