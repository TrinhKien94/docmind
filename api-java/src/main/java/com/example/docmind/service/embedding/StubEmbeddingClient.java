package com.example.docmind.service.embedding;

import com.example.docmind.config.EmbeddingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Embedding stub: vector giả TẤT ĐỊNH (hash-seeded) + L2-normalize hỗ trợ tiếng Việt Unicode
 * và tổ hợp từ ghép (Unigram + Bigram feature hashing).
 *
 * <p>Mục tiêu: toán retrieval (cosine) chạy được KHÔNG cần model ngoài, giữ nguyên từ tiếng Việt
 * có dấu, đồng thời băm cả biến thể bỏ dấu và cụm 2 từ liền kề (bigrams) như "kem_nen", "nuoc_hoa",
 * "da_dau", "di_ung", "doi_tra", "capture_totale", "dior_prestige" để truy xuất ngữ nghĩa chính xác.
 *
 * <p>Active mặc định; nhường chỗ cho impl thật qua @ConditionalOnMissingBean.
 */
@Component
@ConditionalOnMissingBean(name = "primaryEmbeddingClient")
public class StubEmbeddingClient implements EmbeddingClient {

    private final int dim;

    public StubEmbeddingClient(EmbeddingProperties props) {
        this.dim = props.getDim();
    }

    @Override
    public int dim() {
        return dim;
    }

    @Override
    public float[] embed(String text) {
        String src = text == null ? "" : text.toLowerCase(Locale.ROOT);
        float[] v = new float[dim];

        // 1. Băm theo từng token Unicode nguyên bản (giữ nguyên dấu tiếng Việt).
        for (String token : src.split("(?U)[^\\p{L}\\p{N}]+")) {
            if (token.isEmpty()) {
                continue;
            }
            accumulateTokenHash(v, token, 1.0f);
        }

        // 2. Băm thêm token chuẩn hoá bỏ dấu tiếng Việt + Bigram (cụm 2 từ liền kề)
        // giúp bắt chính xác các khái niệm tiếng Việt & tên dòng sản phẩm (vd: "kem_nen", "da_nhay_cam", "miss_dior").
        String normalizedAscii = normalizeVietnameseAscii(src);
        List<String> normTokens = new ArrayList<>();
        for (String token : normalizedAscii.split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                normTokens.add(token);
                accumulateTokenHash(v, "vn_" + token, 0.85f);
            }
        }
        for (int i = 0; i + 1 < normTokens.size(); i++) {
            String bigram = normTokens.get(i) + "_" + normTokens.get(i + 1);
            accumulateTokenHash(v, "bg_" + bigram, 1.25f);
        }

        // Nếu text rỗng, tạo vector tất định nhỏ để tránh toàn 0 (chia 0 khi normalize).
        if (isAllZero(v)) {
            int seed = Math.floorMod(src.hashCode(), dim);
            v[seed] = 1f;
        }

        l2Normalize(v);
        return v;
    }

    private void accumulateTokenHash(float[] v, String feature, float weight) {
        int h = feature.hashCode();
        int idx = Math.floorMod(h, dim);
        float sign = ((h >>> 31) & 1) == 0 ? 1f : -1f;
        v[idx] += sign * weight;
    }

    private static String normalizeVietnameseAscii(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd');
    }

    private static boolean isAllZero(float[] v) {
        for (float x : v) {
            if (x != 0f) {
                return false;
            }
        }
        return true;
    }

    private static void l2Normalize(float[] v) {
        double sumSq = 0;
        for (float x : v) {
            sumSq += (double) x * x;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
    }
}

