/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import static org.junit.Assert.assertEquals;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

/**
 * Test coverage for Auth Web Filter.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AuthWebFilterTest {
    private AuthWebFilter filter;

    private GatewayProperties properties;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        properties = new GatewayProperties();
        properties.setSecretKey("test-secret");
        filter = new AuthWebFilter(properties);
    }

    /**
     * Tests status endpoint is public.
     */
    @Test
    public void testStatusEndpointIsPublic() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/status").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    /**
     * Tests options passes through.
     */
    @Test
    public void testOptionsPassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/agents").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    /**
     * Tests valid secret key in header.
     */
    @Test
    public void testValidSecretKeyInHeader() {
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/agents").header("x-secret-key", "test-secret").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    /**
     * Tests valid secret key in query param.
     */
    @Test
    public void testValidSecretKeyInQueryParam() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents?key=test-secret").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    /**
     * Tests invalid secret key returns401.
     */
    @Test
    public void testInvalidSecretKeyReturns401() {
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/agents").header("x-secret-key", "wrong-key").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    /**
     * Tests missing secret key returns401.
     */
    @Test
    public void testMissingSecretKeyReturns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/agents").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
