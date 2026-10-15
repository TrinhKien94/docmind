package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Thông tin Star Plugin, Slash Command hoặc MCP Server trong ExtensionPage của AstrBot.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotExtensionDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("kind") String kind,
        @JsonProperty("author") String author,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("commands") List<String> commands,
        @JsonProperty("tools_count") int toolsCount,
        @JsonProperty("endpoint") String endpoint
) {
}
