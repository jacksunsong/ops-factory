/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.security;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * Auth Web Filter.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
@Order(1)
public class AuthWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthWebFilter.class);

    private static final String HEADER_SECRET_KEY = "x-secret-key";

    private static final String QUERY_KEY = "key";

    private static final String HEALTH_PATH = "/actuator/health";

    private final OperationIntelligenceProperties properties;

    /**
     * Auth Web Filter.
     *
     * @param properties the properties
     */
    public AuthWebFilter(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name()) || HEALTH_PATH.equals(path)) {
            return chain.filter(exchange);
        }

        String key = exchange.getRequest().getHeaders().getFirst(HEADER_SECRET_KEY);
        if (key == null || key.isBlank()) {
            key = exchange.getRequest().getQueryParams().getFirst(QUERY_KEY);
        }

        if (!properties.getSecretKey().equals(key)) {
            log.warn("Rejecting unauthorized request path={} reason=invalid-secret-key", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
