/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

/**
 * Test coverage for Request Context Filter.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class RequestContextFilterTest {
    private RequestContextFilter filter;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        GatewayProperties properties = new GatewayProperties();
        filter = new RequestContextFilter(properties);
    }

    /**
     * Tests generates request id when missing.
     */
    @Test
    public void testGeneratesRequestIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/gateway/status").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        assertNotNull(requestId);
        assertEquals(requestId, exchange.getResponse().getHeaders().getFirst(RequestContextFilter.REQUEST_ID_HEADER));
    }

    /**
     * Tests reuses incoming request id.
     */
    @Test
    public void testReusesIncomingRequestId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/gateway/status")
            .header(RequestContextFilter.REQUEST_ID_HEADER, "req-123")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals("req-123", exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR));
        assertEquals("req-123", exchange.getResponse().getHeaders().getFirst(RequestContextFilter.REQUEST_ID_HEADER));
    }
}
