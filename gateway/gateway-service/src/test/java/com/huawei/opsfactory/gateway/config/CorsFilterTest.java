/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * Unit tests for the CORS filter in WebFluxConfig.
 * Covers all origin matching scenarios after removal of isLocalDevOrigin fallback.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class CorsFilterTest {
    private GatewayProperties properties;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        properties = new GatewayProperties();
    }

    private WebFilter corsFilter() {
        return new WebFluxConfig(properties).corsFilter();
    }

    private WebFilterChain passThrough() {
        return ex -> Mono.empty();
    }

    /**
     * Executes the wildcard any origin returns request origin operation.
     */
    @Test
    public void wildcard_anyOrigin_returnsRequestOrigin() {
        properties.setCorsOrigin("*");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://10.0.1.5:5173").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("http://10.0.1.5:5173",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
        assertEquals("Origin", exchange.getResponse().getHeaders().getFirst("Vary"));
    }

    /**
     * Executes the wildcard localhost origin returns request origin operation.
     */
    @Test
    public void wildcard_localhostOrigin_returnsRequestOrigin() {
        properties.setCorsOrigin("*");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://localhost:3000").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("http://localhost:3000",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the exact match matching origin returns origin operation.
     */
    @Test
    public void exactMatch_matchingOrigin_returnsOrigin() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://app.example.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("http://app.example.com",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the exact match non matching origin no acao header operation.
     */
    @Test
    public void exactMatch_nonMatchingOrigin_noAcaoHeader() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://evil.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertNull(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the multi value second origin matches operation.
     */
    @Test
    public void multiValue_secondOriginMatches() {
        properties.setCorsOrigin("http://a.com, http://b.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://b.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("http://b.com", exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the multi value no match no acao header operation.
     */
    @Test
    public void multiValue_noMatch_noAcaoHeader() {
        properties.setCorsOrigin("http://a.com,http://b.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://c.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertNull(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the no origin header no acao header operation.
     */
    @Test
    public void noOriginHeader_noAcaoHeader() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertNull(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the options preflight matching origin returns204 operation.
     */
    @Test
    public void optionsPreflight_matchingOrigin_returns204() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.options("/test").header(HttpHeaders.ORIGIN, "http://app.example.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals(HttpStatus.NO_CONTENT, exchange.getResponse().getStatusCode());
        assertEquals("http://app.example.com",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the options preflight non matching origin returns403 operation.
     */
    @Test
    public void optionsPreflight_nonMatchingOrigin_returns403() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.options("/test").header(HttpHeaders.ORIGIN, "http://evil.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    /**
     * Executes the options preflight no origin returns204 operation.
     */
    @Test
    public void optionsPreflight_noOrigin_returns204() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request = MockServerHttpRequest.options("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals(HttpStatus.NO_CONTENT, exchange.getResponse().getStatusCode());
    }

    /**
     * Executes the regression private network5173 not auto allowed operation.
     */
    @Test
    public void regression_privateNetwork5173_notAutoAllowed() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://192.168.1.5:5173").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertNull("Private network origin should NOT be auto-allowed",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the regression localhost5173 not auto allowed operation.
     */
    @Test
    public void regression_localhost5173_notAutoAllowed() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://127.0.0.1:5173").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertNull("localhost:5173 should NOT be auto-allowed when not in configured origins",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the regression 10network5173 not auto allowed operation.
     */
    @Test
    public void regression_10network5173_notAutoAllowed() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://10.0.0.1:5173").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertNull("10.x.x.x:5173 should NOT be auto-allowed when not in configured origins",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the common headers always present operation.
     */
    @Test
    public void commonHeaders_alwaysPresent() {
        properties.setCorsOrigin("http://app.example.com");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://app.example.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("GET, POST, PUT, DELETE, OPTIONS", headers.getFirst("Access-Control-Allow-Methods"));
        assertTrue(headers.getFirst("Access-Control-Allow-Headers").contains("x-secret-key"));
        assertTrue(headers.getFirst("Access-Control-Allow-Headers").contains("x-user-id"));
        assertTrue(headers.getFirst("Access-Control-Allow-Headers").contains("x-request-id"));
        assertEquals("3600", headers.getFirst("Access-Control-Max-Age"));
    }

    /**
     * Executes the empty config treated as wildcard operation.
     */
    @Test
    public void emptyConfig_treatedAsWildcard() {
        properties.setCorsOrigin("");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://any.site.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("http://any.site.com",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the null config treated as wildcard operation.
     */
    @Test
    public void nullConfig_treatedAsWildcard() {
        properties.setCorsOrigin(null);
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "http://any.site.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("http://any.site.com",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the https origin exact match operation.
     */
    @Test
    public void httpsOrigin_exactMatch() {
        properties.setCorsOrigin("https://127.0.0.1:5173");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "https://127.0.0.1:5173").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("https://127.0.0.1:5173",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the https origin wildcard returns https origin operation.
     */
    @Test
    public void httpsOrigin_wildcard_returnsHttpsOrigin() {
        properties.setCorsOrigin("*");
        MockServerHttpRequest request =
            MockServerHttpRequest.get("/test").header(HttpHeaders.ORIGIN, "https://app.example.com").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals("https://app.example.com",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Executes the https origin options preflight returns204 operation.
     */
    @Test
    public void httpsOrigin_optionsPreflight_returns204() {
        properties.setCorsOrigin("https://127.0.0.1:5173");
        MockServerHttpRequest request =
            MockServerHttpRequest.options("/test").header(HttpHeaders.ORIGIN, "https://127.0.0.1:5173").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(corsFilter().filter(exchange, passThrough())).verifyComplete();

        assertEquals(HttpStatus.NO_CONTENT, exchange.getResponse().getStatusCode());
        assertEquals("https://127.0.0.1:5173",
            exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
    }
}
