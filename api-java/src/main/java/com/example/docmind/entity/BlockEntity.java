package com.example.docmind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Canonical Block đã persist. Khớp bảng blocks (V1__init.sql) và canonical-schema §2.
 * Parser là writer; Java persist nguyên trạng.
 */
@Entity
@Table(name = "blocks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "page", nullable = false)
    private int page;

    @Column(name = "block_order", nullable = false)
    private int blockOrder;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "markdown", nullable = false)
    private String markdown;

    @Column(name = "html", nullable = false)
    private String html;

    /** non-null CHỈ khi type=formula. */
    @Column(name = "latex")
    private String latex;

    /** [x0,y0,x1,y1] top-left origin. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "bbox", nullable = false)
    private double[] bbox;

    @Column(name = "confidence", nullable = false)
    private float confidence;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "heading_path", nullable = false)
    private String[] headingPath;

    @Column(name = "parse_engine", nullable = false)
    private String parseEngine;

    @Column(name = "parse_version", nullable = false)
    private String parseVersion;
}
