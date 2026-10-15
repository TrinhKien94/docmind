package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Schema mô tả một trường cấu hình của nền tảng chat trong AstrBot
 * (tương ứng CONFIG_METADATA_2.platform_group.metadata.platform.items).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotFieldSchemaDto(
        @JsonProperty("key") String key,
        @JsonProperty("description") String description,
        @JsonProperty("type") String type,
        @JsonProperty("hint") String hint,
        @JsonProperty("secret") boolean secret,
        @JsonProperty("show_key") boolean showKey,
        @JsonProperty("required") boolean required,
        @JsonProperty("options") List<String> options,
        @JsonProperty("default_value") Object defaultValue
) {
    @JsonProperty("label")
    public String label() {
        return description;
    }
}

