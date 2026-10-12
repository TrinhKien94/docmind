package com.example.docmind.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Impl native cho pgvector/tsvector (JdbcTemplate).
 *
 * <p>Vector được truyền dưới dạng literal '[a,b,c]'::vector — đây là format text
 * chính thức của pgvector. tsvector dựng bằng to_tsvector('simple', text) để trung
 * lập ngôn ngữ (tài liệu có thể tiếng Việt). plainto_tsquery('simple', ?) cho query.
 */
@Repository
public class ChunkRepositoryCustomImpl implements ChunkRepositoryCustom {

    private final JdbcTemplate jdbc;
    private final int embeddingDim;

    public ChunkRepositoryCustomImpl(JdbcTemplate jdbc,
                                     @Value("${docmind.embedding.dim:384}") int embeddingDim) {
        this.jdbc = jdbc;
        this.embeddingDim = embeddingDim;
    }

    @Override
    public void insertChunk(UUID id,
                            UUID documentId,
                            String[] headingPath,
                            UUID[] sourceBlockIds,
                            int page,
                            double[] bbox,
                            String text,
                            float[] embedding) {

        String vectorLiteral = embedding == null ? null : toVectorLiteral(embedding);

        jdbc.update(connection -> {
            String sql = """
                    INSERT INTO chunks
                        (id, document_id, heading_path, source_block_ids, page, bbox, text, embedding, tsv)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), to_tsvector('simple', ?))
                    """;
            var ps = connection.prepareStatement(sql);
            ps.setObject(1, id);
            ps.setObject(2, documentId);
            Array headingArray = connection.createArrayOf("text", headingPath);
            Array blockIdsArray = connection.createArrayOf("uuid", sourceBlockIds);
            Array bboxArray = connection.createArrayOf("float8", toObjectArray(bbox));
            ps.setArray(3, headingArray);
            ps.setArray(4, blockIdsArray);
            ps.setInt(5, page);
            ps.setArray(6, bboxArray);
            ps.setString(7, text);
            ps.setString(8, vectorLiteral); // null => NULL::vector
            ps.setString(9, text);
            return ps;
        });
    }

    @Override
    public List<RetrievedChunk> searchByVector(List<UUID> documentIds, float[] queryEmbedding, int limit) {
        if (documentIds == null || documentIds.isEmpty() || queryEmbedding == null) {
            return List.of();
        }
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        String inClause = inPlaceholders(documentIds.size());
        // score = 1 - cosine_distance; ORDER BY distance (toán tử <=> = cosine distance).
        String sql = """
                SELECT id, document_id, heading_path, source_block_ids, page, bbox, text,
                       (1 - (embedding <=> CAST(? AS vector))) AS score
                FROM chunks
                WHERE embedding IS NOT NULL
                  AND document_id IN (%s)
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """.formatted(inClause);

        Object[] args = new Object[1 + documentIds.size() + 2];
        int i = 0;
        args[i++] = vectorLiteral;
        for (UUID d : documentIds) {
            args[i++] = d;
        }
        args[i++] = vectorLiteral;
        args[i] = limit;

        return jdbc.query(sql, this::mapRow, args);
    }

    @Override
    public List<RetrievedChunk> searchByFullText(List<UUID> documentIds, String query, int limit) {
        if (documentIds == null || documentIds.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String inClause = inPlaceholders(documentIds.size());
        String sql = """
                SELECT id, document_id, heading_path, source_block_ids, page, bbox, text,
                       ts_rank(tsv, plainto_tsquery('simple', ?)) AS score
                FROM chunks
                WHERE tsv @@ plainto_tsquery('simple', ?)
                  AND document_id IN (%s)
                ORDER BY score DESC
                LIMIT ?
                """.formatted(inClause);

        Object[] args = new Object[2 + documentIds.size() + 1];
        int i = 0;
        args[i++] = query;
        args[i++] = query;
        for (UUID d : documentIds) {
            args[i++] = d;
        }
        args[i] = limit;

        return jdbc.query(sql, this::mapRow, args);
    }

    // --- helpers ---

    private RetrievedChunk mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UUID chunkId = rs.getObject("id", UUID.class);
        UUID documentId = rs.getObject("document_id", UUID.class);
        List<String> headingPath = toStringList(rs.getArray("heading_path"));
        List<UUID> blockIds = toUuidList(rs.getArray("source_block_ids"));
        int page = rs.getInt("page");
        double[] bbox = toDoubleArray(rs.getArray("bbox"));
        String text = rs.getString("text");
        double score = rs.getDouble("score");
        return new RetrievedChunk(chunkId, documentId, headingPath, blockIds, page, bbox, text, score);
    }

    private String toVectorLiteral(float[] v) {
        if (v.length != embeddingDim) {
            throw new IllegalArgumentException(
                    "Embedding dim không khớp: expected " + embeddingDim + " nhưng nhận " + v.length);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static Double[] toObjectArray(double[] arr) {
        Double[] out = new Double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    private static String inPlaceholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static List<String> toStringList(Array sqlArray) throws java.sql.SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object[] raw = (Object[]) sqlArray.getArray();
        List<String> out = new ArrayList<>(raw.length);
        for (Object o : raw) {
            out.add(o == null ? null : o.toString());
        }
        return out;
    }

    private static List<UUID> toUuidList(Array sqlArray) throws java.sql.SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object[] raw = (Object[]) sqlArray.getArray();
        List<UUID> out = new ArrayList<>(raw.length);
        for (Object o : raw) {
            if (o instanceof UUID u) {
                out.add(u);
            } else if (o != null) {
                out.add(UUID.fromString(o.toString()));
            }
        }
        return out;
    }

    private static double[] toDoubleArray(Array sqlArray) throws java.sql.SQLException {
        if (sqlArray == null) {
            return new double[0];
        }
        Object[] raw = (Object[]) sqlArray.getArray();
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = raw[i] == null ? 0.0 : ((Number) raw[i]).doubleValue();
        }
        return out;
    }
}
