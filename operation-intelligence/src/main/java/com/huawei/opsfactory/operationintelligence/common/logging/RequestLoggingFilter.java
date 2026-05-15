/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.common.logging;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.UUID;

/**
 * Request Logging Filter.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
@Order(2)
public class RequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final OperationIntelligenceProperties properties;

    /**
     * Request Logging Filter.
     *
     * @param properties the properties
     */
    public RequestLoggingFilter(OperationIntelligenceProperties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange);
        exchange.getResponse().getHeaders().set(LoggingKeys.REQUEST_ID_HEADER, requestId);

        long startedAt = System.currentTimeMillis();
        MDC.put(LoggingKeys.REQUEST_ID, requestId);
        return chain.filter(exchange).doFinally(signalType -> {
            try {
                if (properties.getLogging().isAccessLogEnabled()) {
                    log.info("HTTP {} {} completed status={} durationMs={}", exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(), exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - startedAt);
                }
            } finally {
                MDC.remove(LoggingKeys.REQUEST_ID);
            }
        });
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(LoggingKeys.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
