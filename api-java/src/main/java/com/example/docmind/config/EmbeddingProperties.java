package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình embedding. dim=384 khớp paraphrase-multilingual-MiniLM-L12-v2. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.embedding")
public class EmbeddingProperties {

    private int dim = 384;
}
