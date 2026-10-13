package com.example.docmind.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS đọc cấu hình từ {@link CorsProperties} (application.properties / env). */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProps;

    public CorsConfig() {
        this(new CorsProperties());
    }

    @Autowired
    public CorsConfig(@Autowired(required = false) CorsProperties corsProps) {
        this.corsProps = corsProps != null ? corsProps : new CorsProperties();
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        String pattern = (corsProps.getPathPattern() != null && !corsProps.getPathPattern().isBlank())
                ? corsProps.getPathPattern().strip()
                : "/api/**";
        String[] origins = toArrayOrDefault(corsProps.getAllowedOriginPatterns(), new String[]{"*"});
        String[] methods = toArrayOrDefault(corsProps.getAllowedMethods(),
                new String[]{"GET", "POST", "PUT", "DELETE", "OPTIONS"});
        String[] headers = toArrayOrDefault(corsProps.getAllowedHeaders(), new String[]{"*"});
        String[] exposed = toArrayOrDefault(corsProps.getExposedHeaders(), new String[]{"X-Correlation-Id"});

        registry.addMapping(pattern)
                .allowedOriginPatterns(origins)
                .allowedMethods(methods)
                .allowedHeaders(headers)
                .exposedHeaders(exposed)
                .allowCredentials(corsProps.isAllowCredentials())
                .maxAge(corsProps.getMaxAgeSeconds() > 0 ? corsProps.getMaxAgeSeconds() : 3600);
    }

    private static String[] toArrayOrDefault(java.util.List<String> list, String[] fallback) {
        if (list == null || list.isEmpty()) {
            return fallback;
        }
        String[] filtered = list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::strip)
                .toArray(String[]::new);
        return filtered.length > 0 ? filtered : fallback;
    }
}
