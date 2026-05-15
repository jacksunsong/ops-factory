/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.security;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import reactor.core.publisher.Mono;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

class AuthWebFilterTest {

    private AuthWebFilter filter;

    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        OperationIntelligenceProperties props = new OperationIntelligenceProperties();
        props.setSecretKey("test-secret");
        filter = new AuthWebFilter(props);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void validSecretKeyHeader_passes() {
        ServerWebExchange exchange = MockServerWebExchange
            .from(MockServerHttpRequest.get("/test").header("x-secret-key", "test-secret").build());

        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
    }

    @Test
    void validSecretKeyQueryParam_passes() {
        ServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/test?key=test-secret").build());

        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
    }

    @Test
    void missingSecretKey_returns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidSecretKey_returns401() {
        ServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/test").header("x-secret-key", "wrong").build());

        filter.filter(exchange, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void optionsRequest_passesWithoutAuth() {
        ServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/test").build());

        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
    }

    @Test
    void healthCheck_passesWithoutAuth() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());

        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
    }

    @Test
    void blankSecretKeyHeader_treatedAsMissing() {
        ServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/test").header("x-secret-key", "   ").build());

        filter.filter(exchange, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
