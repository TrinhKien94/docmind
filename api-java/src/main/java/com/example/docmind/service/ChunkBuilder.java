package com.example.docmind.service;

import com.example.docmind.client.model.CanonicalBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dựng chunk từ các block (canonical-schema §3): nhóm theo (page, heading_path).
 * Mỗi chunk NẰM GỌN trong 1 page; kế thừa heading_path/page từ block; bbox là union
 * các block; source_block_ids là id các block cấu thành; text ghép theo thứ tự đọc.
 */
public final class ChunkBuilder {

    private ChunkBuilder() {
    }

    /** 1 chunk đã dựng (chưa persist). */
    public record BuiltChunk(
            UUID id,
            UUID documentId,
            String[] headingPath,
            UUID[] sourceBlockIds,
            int page,
            double[] bbox,
            String text
    ) {
    }

    /**
     * Nhóm block liên tiếp cùng (page + heading_path) thành 1 chunk.
     * Giả định block đã theo thứ tự reading order (order asc).
     */
    public static List<BuiltChunk> build(UUID documentId, List<CanonicalBlock> blocks) {
        List<BuiltChunk> chunks = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) {
            return chunks;
        }

        List<CanonicalBlock> group = new ArrayList<>();
        Integer curPage = null;
        List<String> curHeading = null;

        for (CanonicalBlock b : blocks) {
            List<String> hp = b.headingPath() == null ? List.of() : b.headingPath();
            boolean sameGroup = curPage != null
                    && curPage == b.page()
                    && hp.equals(curHeading);
            if (!sameGroup && !group.isEmpty()) {
                chunks.add(flush(documentId, group));
                group = new ArrayList<>();
            }
            group.add(b);
            curPage = b.page();
            curHeading = hp;
        }
        if (!group.isEmpty()) {
            chunks.add(flush(documentId, group));
        }
        return chunks;
    }

    private static BuiltChunk flush(UUID documentId, List<CanonicalBlock> group) {
        CanonicalBlock first = group.get(0);
        List<String> headingPath = first.headingPath() == null ? List.of() : first.headingPath();

        UUID[] blockIds = group.stream()
                .map(b -> UUID.fromString(b.blockId()))
                .toArray(UUID[]::new);

        double[] bbox = unionBbox(group);

        StringBuilder text = new StringBuilder();
        for (CanonicalBlock b : group) {
            if (b.text() != null && !b.text().isBlank()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(b.text().strip());
            }
        }

        return new BuiltChunk(
                UUID.randomUUID(),
                documentId,
                headingPath.toArray(new String[0]),
                blockIds,
                first.page(),
                bbox,
                text.toString()
        );
    }

    /** Union bbox [x0,y0,x1,y1] top-left origin (x1,y1 là góc dưới-phải). */
    private static double[] unionBbox(List<CanonicalBlock> group) {
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        boolean any = false;
        for (CanonicalBlock b : group) {
            List<Double> bb = b.bbox();
            if (bb == null || bb.size() < 4) {
                continue;
            }
            any = true;
            x0 = Math.min(x0, bb.get(0));
            y0 = Math.min(y0, bb.get(1));
            x1 = Math.max(x1, bb.get(2));
            y1 = Math.max(y1, bb.get(3));
        }
        if (!any) {
            return new double[]{0, 0, 0, 0};
        }
        return new double[]{x0, y0, x1, y1};
    }
}
