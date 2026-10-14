package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình kiểm tra hợp lệ file upload (MIME allow-list, tên file mặc định).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.upload")
public class UploadProperties {

    /** Danh sách MIME được hỗ trợ mặc định. */
    private List<String> allowedMimes = new ArrayList<>(List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/html",
            "text/markdown",
            "text/x-web-markdown",
            "text/x-markdown",
            "text/plain",
            "image/png",
            "image/jpeg"
    ));

    /** Tên file mặc định khi client không gửi originalFilename. */
    private String defaultFilename = "upload";

    /** MIME mặc định khi Tika không xác định được. */
    private String fallbackMime = "application/octet-stream";
}
