package com.example.docmind.service.embedding;

import java.util.List;

/**
 * Sinh embedding cho text. Impl mặc định là stub (vector giả tất định) để toán
 * retrieval chạy mà không cần model. Impl thật (sentence-transformers qua Python
 * hoặc ONNX in-JVM) sẽ cắm sau — xem GeminiEmbeddingClient / TODO.
 */
public interface EmbeddingClient {

    /** Số chiều vector (phải khớp cột pgvector trong V1__init.sql). */
    int dim();

    /** Embed 1 đoạn text -> float[dim]. */
    float[] embed(String text);

    /** Embed theo batch (mặc định map từng phần tử). */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
