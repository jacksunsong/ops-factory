/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;

/**
 * Extended tests for GoosedProxy covering:
 * - copyHeaders: secret key injection
 * - copyUpstreamHeaders: CORS header filtering
 * - fetchJson: returns non-null Mono (construction-level)
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class GoosedProxyExtendedTest {
    private GoosedProxy proxy;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecretKey("my-secret");
        properties.setGooseTls(false);
        proxy = new GoosedProxy(properties);
    }

    /**
     * Tests copy headers injects secret key.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCopyHeaders_injectsSecretKey() throws Exception {
        HttpHeaders source = new HttpHeaders();
        source.add("Content-Type", "application/json");
        source.add("X-Custom", "value");

        HttpHeaders target = new HttpHeaders();

        Method copyHeaders =
            GoosedProxy.class.getDeclaredMethod("copyHeaders", HttpHeaders.class, HttpHeaders.class, String.class);
        copyHeaders.setAccessible(true);
        copyHeaders.invoke(proxy, source, target, "my-secret");

        assertEquals("application/json", target.getFirst("Content-Type"));
        assertEquals("value", target.getFirst("X-Custom"));
        assertEquals("my-secret", target.getFirst("x-secret-key"));
    }

    /**
     * Tests copy headers overrides existing secret key.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCopyHeaders_overridesExistingSecretKey() throws Exception {
        HttpHeaders source = new HttpHeaders();
        source.add("x-secret-key", "client-key-should-be-overridden");

        HttpHeaders target = new HttpHeaders();

        Method copyHeaders =
            GoosedProxy.class.getDeclaredMethod("copyHeaders", HttpHeaders.class, HttpHeaders.class, String.class);
        copyHeaders.setAccessible(true);
        copyHeaders.invoke(proxy, source, target, "my-secret");

        // Should be overridden by gateway's secret key
        assertEquals("my-secret", target.getFirst("x-secret-key"));
    }

    /**
     * Tests copy upstream headers filters cors headers.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCopyUpstreamHeaders_filtersCorsHeaders() throws Exception {
        HttpHeaders source = new HttpHeaders();
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST");
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        source.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Custom");
        source.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
        source.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        source.add("X-Custom-Header", "keep-this");
        source.add("Content-Type", "application/json");

        HttpHeaders target = new HttpHeaders();

        Method copyUpstream =
            GoosedProxy.class.getDeclaredMethod("copyUpstreamHeaders", HttpHeaders.class, HttpHeaders.class);
        copyUpstream.setAccessible(true);
        copyUpstream.invoke(proxy, source, target);

        // CORS headers should be filtered out
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_MAX_AGE));
        assertFalse(target.containsKey(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        // Non-CORS headers should be kept
        assertEquals("keep-this", target.getFirst("X-Custom-Header"));
        assertEquals("application/json", target.getFirst("Content-Type"));
    }

    /**
     * Tests copy upstream headers empty source.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCopyUpstreamHeaders_emptySource() throws Exception {
        HttpHeaders source = new HttpHeaders();
        HttpHeaders target = new HttpHeaders();

        Method copyUpstream =
            GoosedProxy.class.getDeclaredMethod("copyUpstreamHeaders", HttpHeaders.class, HttpHeaders.class);
        copyUpstream.setAccessible(true);
        copyUpstream.invoke(proxy, source, target);

        assertTrue(target.isEmpty());
    }

    /**
     * Tests fetch json returns non null mono.
     */
    @Test
    public void testFetchJson_returnsNonNullMono() {
        // Construction-level test: verifies Mono is created without errors
        assertNotNull(proxy.fetchJson(99999, "/test", "test-secret"));
    }

    /**
     * Tests proxy with body returns non null mono.
     */
    @Test
    public void testProxyWithBody_returnsNonNullMono() {
        // Construction-level test: verifies Mono is created
        assertNotNull(
            proxy.proxyWithBody(null, 99999, "/test", org.springframework.http.HttpMethod.POST, "{}", "test-secret"));
    }

    /**
     * Tests proxy session command with body non2xx throws upstream error without committing response.
     */
    @Test
    public void testProxySessionCmdWithBody_non2xxThrowsErrorWithoutCommit() {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes.post("/sessions/session-123/reply",
                (request, response) -> response.status(400)
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .sendString(Mono.just("Session already has an active request. Cancel it first."))))
            .bindNow();

        try {
            MockServerHttpResponse response = new MockServerHttpResponse();
            WebClientResponseException error = assertThrows(WebClientResponseException.class,
                () -> proxy
                    .proxySessionCommandWithBody(response, server.port(), "/sessions/session-123/reply",
                        HttpMethod.POST, "{}", "test-secret")
                    .block(Duration.ofSeconds(5)));

            assertEquals(400, error.getRawStatusCode());
            assertEquals("Session already has an active request. Cancel it first.", error.getResponseBodyAsString());
            assertFalse(response.isCommitted());
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Tests emit transformed frame emits original before supplemental event completes.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testEmitTransformedFrame_emitsOriginalBeforeSupplemental() throws Exception {
        Method emitTransformedFrame = GoosedProxy.class.getDeclaredMethod("emitTransformedFrame", String.class,
            org.springframework.core.io.buffer.DataBufferFactory.class, Function.class);
        emitTransformedFrame.setAccessible(true);

        String frame = "id: 42\ndata: {\"type\":\"Finish\",\"chat_request_id\":\"req-1\"}";
        @SuppressWarnings("unchecked")
        Flux<DataBuffer> result = (Flux<DataBuffer>) emitTransformedFrame.invoke(proxy, frame,
            new DefaultDataBufferFactory(), (Function<String, Mono<String>>) ignored -> Mono.never());

        StepVerifier.create(result.map(this::dataBufferToString))
            .expectNext(frame + "\n\n")
            .thenCancel()
            .verify(Duration.ofSeconds(1));
    }

    private String dataBufferToString(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
