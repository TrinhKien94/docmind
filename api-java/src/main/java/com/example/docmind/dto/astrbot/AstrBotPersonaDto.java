package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Thông tin Persona (nhân cách AI, prompt hệ thống, hội thoại mở đầu, kỹ năng)
 * theo thiết kế PersonaManager của AstrBot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotPersonaDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("folder") String folder,
        @JsonProperty("system_prompt") String systemPrompt,
        @JsonProperty("begin_dialogs") List<String> beginDialogs,
        @JsonProperty("tools") List<String> tools,
        @JsonProperty("temperature") double temperature,
        @JsonProperty("default_scenario") String defaultScenario,
        @JsonProperty("updated_at") String updatedAt
) {
}
