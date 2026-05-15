/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * Web filter that validates the secret key on every non-preflight, non-webhook request.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
@Order(2)
public class AuthWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthWebFilter.class);

    private static final String CHANNEL_WEBHOOK_PREFIX = "/gateway/channels/webhooks/";

    private final GatewayProperties properties;

    /**
     * Creates the auth web filter instance.
     *
     * @param properties gateway configuration properties containing the secret key
     */
    public AuthWebFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Filters incoming HTTP requests by validating the secret key.
     *
     * @param exchange current HTTP exchange
     * @param chain filter chain to continue processing
     * @return Mono that completes when filtering is done
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // OPTIONS preflight passes through
        if (request.getMethod() != null && "OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        if (request.getURI().getPath().startsWith(CHANNEL_WEBHOOK_PREFIX)) {
            return chain.filter(exchange);
        }

        // Check secret key from header or query param
        String key = request.getHeaders().getFirst(GatewayConstants.HEADER_SECRET_KEY);
        if (key == null || key.isBlank()) {
            key = request.getQueryParams().getFirst(GatewayConstants.QUERY_KEY);
        }

        if (!properties.getSecretKey().equals(key)) {
            log.warn("Rejecting unauthorized request path={} reason=invalid-secret-key", request.getURI().getPath());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
