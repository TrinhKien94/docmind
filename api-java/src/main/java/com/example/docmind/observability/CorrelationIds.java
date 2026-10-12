package com.example.docmind.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Hằng số + helper cho chuỗi correlation ghi vào SLF4J MDC.
 * Mọi dòng log (logback JSON encoder, withMDC=true) sẽ tự mang các field này.
 */
public final class CorrelationIds {

    /** Tên header truyền giữa api-java <-> doc-service-py (khớp FEAT-002). */
    public static final String HEADER = "X-Correlation-Id";

    public static final String MDC_CORRELATION_ID = "correlation_id";
    public static final String MDC_DOCUMENT_ID = "document_id";
    public static final String MDC_JOB_ID = "job_id";
    public static final String MDC_STAGE = "stage";

    private CorrelationIds() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static void putCorrelationId(String id) {
        if (id != null && !id.isBlank()) {
            MDC.put(MDC_CORRELATION_ID, id);
        }
    }

    public static void putDocumentId(String id) {
        if (id != null) {
            MDC.put(MDC_DOCUMENT_ID, id);
        }
    }

    public static void putJobId(String id) {
        if (id != null) {
            MDC.put(MDC_JOB_ID, id);
        }
    }

    public static String currentCorrelationId() {
        return MDC.get(MDC_CORRELATION_ID);
    }

    /** Đặt stage, chạy action, rồi xóa stage (để log mỗi stage gọn). */
    public static void withStage(String stage, Runnable action) {
        MDC.put(MDC_STAGE, stage);
        try {
            action.run();
        } finally {
            MDC.remove(MDC_STAGE);
        }
    }

    public static void setStage(String stage) {
        MDC.put(MDC_STAGE, stage);
    }

    public static void clearStage() {
        MDC.remove(MDC_STAGE);
    }
}
