package com.example.docmind.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đọc hoặc sinh X-Correlation-Id cho mỗi HTTP request, đưa vào MDC để toàn bộ
 * log line của request mang đúng chuỗi correlation. Echo lại header ra response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationIds.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIds.newId();
        }
        CorrelationIds.putCorrelationId(correlationId);
        response.setHeader(CorrelationIds.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
