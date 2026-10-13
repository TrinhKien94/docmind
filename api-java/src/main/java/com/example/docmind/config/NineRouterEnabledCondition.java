package com.example.docmind.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Điều kiện kích hoạt NineRouterLlmClient:
 * Trả về true khi property ninerouter.enabled=true (hoặc không cấu hình, mặc định true).
 */
public class NineRouterEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isNineRouterEnabled(context);
    }

    public static boolean isNineRouterEnabled(ConditionContext context) {
        String enabledStr = context.getEnvironment().getProperty("ninerouter.enabled");
        if (enabledStr == null || enabledStr.isBlank()) {
            return true; // Mặc định kích hoạt
        }
        return Boolean.parseBoolean(enabledStr.trim());
    }
}
