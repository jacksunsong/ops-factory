/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebFlux configuration that registers the CORS filter and related web-layer beans.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Configuration
public class WebFluxConfig {
    private final GatewayProperties properties;

    /**
     * Creates the web flux config instance.
     */
    public WebFluxConfig(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates and configures the CORS filter bean.
     *
     * @return the result
     */
    @Bean
    @Order(0)
    public WebFilter corsFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            String configured = properties.getCorsOrigin();
            String requestOrigin = exchange.getRequest().getHeaders().getOrigin();
            String allowOrigin = resolveAllowOrigin(configured, requestOrigin);
            var response = exchange.getResponse();
            var headers = response.getHeaders();

            if (allowOrigin != null) {
                headers.set("Access-Control-Allow-Origin", allowOrigin);
                headers.set("Vary", "Origin");
            }
            headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.set("Access-Control-Allow-Headers",
                "x-secret-key, x-user-id, x-request-id, content-type, authorization");
            headers.set("Access-Control-Expose-Headers", "*");
            headers.set("Access-Control-Max-Age", "3600");

            if (exchange.getRequest().getMethod() != null
                && "OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
                if (requestOrigin != null && allowOrigin == null) {
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return response.setComplete();
                }
                response.setStatusCode(HttpStatus.NO_CONTENT);
                return response.setComplete();
            }

            return chain.filter(exchange);
        };
    }

    private String resolveAllowOrigin(String configured, String requestOrigin) {
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return null;
        }
        if (configured == null || configured.isBlank() || "*".equals(configured.trim())) {
            return requestOrigin;
        }

        Set<String> exactOrigins = Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
        return exactOrigins.contains(requestOrigin) ? requestOrigin : null;
    }
}
