/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.proxy;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.net.ssl.SSLException;

/**
 * Proxies HTTP and SSE requests from the gateway to downstream goosed agent instances.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
public class GoosedProxy {
    private static final Logger log = LoggerFactory.getLogger(GoosedProxy.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Duration EVENT_AUGMENT_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

    private final GatewayProperties properties;

    /**
     * Creates the goosed proxy instance.
     *
     * @param properties gateway configuration properties
     */
    public GoosedProxy(GatewayProperties properties) {
        this.properties = properties;

        // Use newConnection() to disable connection pooling.
        // Each goosed instance is localhost on a dynamic port; pooled connections
        // become stale when a goosed process restarts on a different port,
        // causing SslHandshakeTimeoutException cascades.
        HttpClient httpClient = HttpClient.newConnection().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);

        if (properties.isGooseTls()) {
            try {
                SslContext sslContext =
                    SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                httpClient = httpClient.secure(t -> t.sslContext(sslContext).handshakeTimeout(Duration.ofSeconds(5)));
            } catch (SSLException e) {
                throw new RuntimeException("Failed to configure TLS for goosed proxy", e);
            }
        }

        this.webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
            .build();
    }

    /**
     * Builds the base URL for a goosed instance on the given port.
     *
     * @param port port number of the target goosed instance
     * @return base URL string for the goosed instance
     */
    public String goosedBaseUrl(int port) {
        return properties.gooseScheme() + "://127.0.0.1:" + port;
    }

    /**
     * Proxy an arbitrary request to a goosed instance.
     *
     * @param request incoming HTTP request to forward
     * @param response outgoing HTTP response to write to
     * @param port port number of the target goosed instance
     * @param path target path on the goosed instance
     * @param secretKey secret key for authenticating with the goosed instance
     * @return Mono that completes when the proxy response has been written
     */
    public Mono<Void> proxy(ServerHttpRequest request, ServerHttpResponse response, int port, String path,
        String secretKey) {
        String target = goosedBaseUrl(port) + path;
        HttpMethod method = request.getMethod();

        WebClient.RequestBodySpec spec = webClient.method(method != null ? method : HttpMethod.GET)
            .uri(target)
            .headers(h -> copyHeaders(request.getHeaders(), h, secretKey));

        WebClient.RequestHeadersSpec<?> ready;
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            ready = spec.body(BodyInserters.fromDataBuffers(request.getBody()));
        } else {
            ready = spec;
        }

        return ready.exchangeToMono(upstream -> {
            response.setStatusCode(upstream.statusCode());
            copyUpstreamHeaders(upstream.headers().asHttpHeaders(), response.getHeaders());
            return response.writeWith(upstream.bodyToFlux(DataBuffer.class));
        }).timeout(Duration.ofSeconds(60)).onErrorMap(this::isProxyError, this::mapProxyError);
    }

    /**
     * Proxy with a pre-read JSON body string (for routes that need body inspection).
     *
     * @param response outgoing HTTP response to write to
     * @param port port number of the target goosed instance
     * @param path target path on the goosed instance
     * @param method HTTP method for the upstream request
     * @param body pre-read JSON body string to send
     * @param secretKey secret key for authenticating with the goosed instance
     * @return Mono that completes when the proxy response has been written
     */
    public Mono<Void> proxyWithBody(ServerHttpResponse response, int port, String path, HttpMethod method, String body,
        String secretKey) {
        String target = goosedBaseUrl(port) + path;

        return webClient.method(method)
            .uri(target)
            .header(GatewayConstants.HEADER_SECRET_KEY, secretKey)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(body)
            .exchangeToMono(upstream -> {
                response.setStatusCode(upstream.statusCode());
                copyUpstreamHeaders(upstream.headers().asHttpHeaders(), response.getHeaders());
                return response.writeWith(upstream.bodyToFlux(DataBuffer.class));
            })
            .timeout(Duration.ofSeconds(60))
            .onErrorMap(this::isProxyError, this::mapProxyError);
    }

    /**
     * Proxy a session command and leave non-2xx responses uncommitted so callers
     * can turn upstream errors into the Gateway session Error envelope.
     *
     * @param response outgoing HTTP response to write to
     * @param port port number of the target goosed instance
     * @param path target path on the goosed instance
     * @param method HTTP method for the upstream request
     * @param body pre-read JSON body string to send
     * @param secretKey secret key for authenticating with the goosed instance
     * @return Mono that completes when the proxy response has been written
     */
    public Mono<Void> proxySessionCommandWithBody(ServerHttpResponse response, int port, String path, HttpMethod method,
        String body, String secretKey) {
        String target = goosedBaseUrl(port) + path;
        long start = System.currentTimeMillis();
        log.info("[GOOSED-PROXY] request method={} path={} port={}", method, path, port);

        return webClient.method(method)
            .uri(target)
            .header(GatewayConstants.HEADER_SECRET_KEY, secretKey)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .bodyValue(body)
            .exchangeToMono(upstream -> {
                if (upstream.statusCode().isError()) {
                    return upstream.bodyToMono(String.class).defaultIfEmpty("").flatMap(errorBody -> {
                        log.warn(
                            "[GOOSED-PROXY] upstream-error method={} path={} port={} status={} "
                                + "error={} durationMs={}",
                            method, path, port, upstream.rawStatusCode(), truncate(errorBody, 200),
                            System.currentTimeMillis() - start);
                        return Mono.error(toUpstreamResponseException(upstream.rawStatusCode(),
                            upstream.headers().asHttpHeaders(), errorBody));
                    });
                }
                log.info("[GOOSED-PROXY] response method={} path={} port={} status={} durationMs={}", method, path,
                    port, upstream.statusCode(), System.currentTimeMillis() - start);
                response.setStatusCode(upstream.statusCode());
                copyUpstreamHeaders(upstream.headers().asHttpHeaders(), response.getHeaders());
                return response.writeWith(upstream.bodyToFlux(DataBuffer.class));
            })
            .timeout(Duration.ofSeconds(60))
            .onErrorMap(this::isProxyError, this::mapProxyError);
    }

    /**
     * Proxy a goosed session event stream. This method intentionally does not
     * apply a whole-stream timeout: the session events channel is long-lived,
     * and client disconnect must not imply agent cancellation.
     *
     * @param response outgoing SSE response to write to
     * @param port port number of the target goosed instance
     * @param path target SSE endpoint path
     * @param secretKey secret key for authenticating with the goosed instance
     * @param lastEventId Last-Event-ID header value for resuming the stream
     * @param agentId agent instance identifier
     * @param userId user identifier
     * @param sessionId session identifier
     * @param beforeTerminalEventFactory function that augments terminal events before forwarding
     * @return Mono that completes when the SSE stream ends
     */
    public Mono<Void> proxySessionEvents(ServerHttpResponse response, int port, String path, String secretKey,
        String lastEventId, String agentId, String userId, String sessionId,
        Function<String, Mono<String>> beforeTerminalEventFactory) {
        String target = goosedBaseUrl(port) + path;
        log.info("[GOOSED-PROXY] sse-connect port={} path={} agentId={} userId={} sessionId={} lastEventId={}", port,
            path, agentId, userId, sessionId, lastEventId);

        WebClient.RequestHeadersSpec<?> spec = webClient.get()
            .uri(target)
            .header(GatewayConstants.HEADER_SECRET_KEY, secretKey)
            .accept(MediaType.TEXT_EVENT_STREAM);
        if (lastEventId != null && !lastEventId.isBlank()) {
            spec = spec.header("Last-Event-ID", lastEventId);
        }

        return spec.exchangeToMono(upstream -> {
            if (upstream.statusCode().isError()) {
                log.warn("[GOOSED-PROXY] sse-upstream-error port={} path={} status={} agentId={} sessionId={}", port,
                    path, upstream.rawStatusCode(), agentId, sessionId);
                return upstream.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(errorBody -> Mono.error(toUpstreamResponseException(upstream.rawStatusCode(),
                        upstream.headers().asHttpHeaders(), errorBody)));
            }
            log.info("[GOOSED-PROXY] sse-connected port={} path={} status={} agentId={} sessionId={}", port, path,
                upstream.statusCode(), agentId, sessionId);
            response.setStatusCode(upstream.statusCode());
            copyUpstreamHeaders(upstream.headers().asHttpHeaders(), response.getHeaders());
            Flux<DataBuffer> body =
                transformSessionEventStream(
                    upstream.bodyToFlux(DataBuffer.class)
                        .onErrorResume(err -> Flux.just(response.bufferFactory()
                            .wrap(gatewayEventStreamError(err, agentId, userId, sessionId)
                                .getBytes(StandardCharsets.UTF_8)))),
                    response.bufferFactory(), beforeTerminalEventFactory);
            return response.writeWith(body);
        }).onErrorMap(this::isProxyError, this::mapProxyError);
    }

    private Flux<DataBuffer> transformSessionEventStream(Flux<DataBuffer> upstream, DataBufferFactory bufferFactory,
        Function<String, Mono<String>> beforeTerminalEventFactory) {
        StringBuilder buffer = new StringBuilder();

        return Flux.concat(upstream.concatMap(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            buffer.append(new String(bytes, StandardCharsets.UTF_8));

            List<String> frames = new ArrayList<>();
            int separatorIndex;
            int separatorLength;
            while (true) {
                int lfIndex = buffer.indexOf("\n\n");
                int crlfIndex = buffer.indexOf("\r\n\r\n");
                if (lfIndex < 0 && crlfIndex < 0) {
                    break;
                }
                if (crlfIndex >= 0 && (lfIndex < 0 || crlfIndex < lfIndex)) {
                    separatorIndex = crlfIndex;
                    separatorLength = 4;
                } else {
                    separatorIndex = lfIndex;
                    separatorLength = 2;
                }
                String frame = buffer.substring(0, separatorIndex);
                buffer.delete(0, separatorIndex + separatorLength);
                frames.add(frame);
            }

            return Flux.fromIterable(frames)
                .concatMap(frame -> emitTransformedFrame(frame, bufferFactory, beforeTerminalEventFactory));
        }), Mono.defer(() -> {
            if (buffer.isEmpty()) {
                return Mono.empty();
            }
            String remaining = buffer.toString();
            buffer.setLength(0);
            return Mono.just(bufferFactory.wrap(remaining.getBytes(StandardCharsets.UTF_8)));
        }));
    }

    private Flux<DataBuffer> emitTransformedFrame(String frame, DataBufferFactory bufferFactory,
        Function<String, Mono<String>> beforeTerminalEventFactory) {
        String data = extractSseData(frame);
        Mono<String> injected = data == null || data.isBlank() ? Mono.empty() : beforeTerminalEventFactory.apply(data);

        Mono<DataBuffer> originalFrame =
            Mono.just(bufferFactory.wrap((frame + "\n\n").getBytes(StandardCharsets.UTF_8)));
        Mono<DataBuffer> extraFrame = injected.timeout(EVENT_AUGMENT_TIMEOUT).onErrorResume(err -> {
            log.warn("[SESSION-EVENTS] skipped supplemental event after original frame: {}", err.getMessage());
            return Mono.empty();
        })
            .filter(extraPayload -> extraPayload != null && !extraPayload.isBlank())
            .map(extraPayload -> bufferFactory.wrap(extraPayload.getBytes(StandardCharsets.UTF_8)));

        return Flux.concat(originalFrame, extraFrame);
    }

    private String extractSseData(String frame) {
        StringBuilder data = new StringBuilder();
        for (String line : frame.split("\n")) {
            String trimmed = line.replace("\r", "");
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            if (!data.isEmpty()) {
                data.append('\n');
            }
            data.append(trimmed.substring(5).trim());
        }
        return data.isEmpty() ? null : data.toString();
    }

    private String gatewayEventStreamError(Throwable err, String agentId, String userId, String sessionId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", "Error");
            body.put("layer", "gateway");
            body.put("code", "gateway_goosed_unavailable");
            body.put("severity", "error");
            body.put("message_key", "chat.sessionErrors.gatewayGoosedUnavailable");
            body.put("message", "Gateway lost the agent event stream.");
            body.put("detail", err.getMessage());
            body.put("retryable", true);
            body.put("suggested_actions", List.of("reconnect", "wait", "contact_support"));
            body.put("session_id", sessionId);
            body.put("agent_id", agentId);
            body.put("user_id", userId);
            body.put("trace_id", UUID.randomUUID().toString());
            return "data: " + MAPPER.writeValueAsString(body) + "\n\n";
        } catch (JsonProcessingException writeErr) {
            return "data: {\"type\":\"Error\",\"layer\":\"gateway\",\"code\":\"gateway_goosed_unavailable\","
                + "\"message\":\"Gateway lost the agent event stream.\",\"retryable\":true}\n\n";
        }
    }

    /**
     * Fetch JSON from a goosed instance and return the raw body string.
     *
     * @param port port number of the target goosed instance
     * @param path target path on the goosed instance
     * @param secretKey secret key for authenticating with the goosed instance
     * @return Mono emitting the raw JSON response body
     */
    public Mono<String> fetchJson(int port, String path, String secretKey) {
        String target = goosedBaseUrl(port) + path;
        long start = System.currentTimeMillis();
        log.info("[GOOSED-PROXY] request method=GET path={} port={}", path, port);
        return webClient.get()
            .uri(target)
            .header(GatewayConstants.HEADER_SECRET_KEY, secretKey)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
            .doOnNext(body -> log.info(
                "[GOOSED-PROXY] response method=GET path={} port={} status=200" + " bodyLen={} durationMs={}", path,
                port, body != null ? body.length() : 0, System.currentTimeMillis() - start))
            .doOnError(err -> log.warn("[GOOSED-PROXY] error method=GET path={} port={} error={} " + "durationMs={}",
                path, port, err.getMessage(), System.currentTimeMillis() - start));
    }

    /**
     * Fetches JSON from a goosed instance using the given HTTP method and request body.
     *
     * @param port port number of the target goosed instance
     * @param method HTTP method for the request
     * @param path target path on the goosed instance
     * @param body JSON request body, or null for no body
     * @param secretKey secret key for authenticating with the goosed instance
     * @return Mono emitting the raw JSON response body
     */
    public Mono<String> fetchJson(int port, HttpMethod method, String path, String body, String secretKey) {
        return fetchJson(port, method, path, body, 30, secretKey);
    }

    /**
     * Fetches JSON from a goosed instance with a configurable timeout.
     *
     * @param port port number of the target goosed instance
     * @param method HTTP method for the request
     * @param path target path on the goosed instance
     * @param body JSON request body, or null for no body
     * @param timeoutSec request timeout in seconds
     * @param secretKey secret key for authenticating with the goosed instance
     * @return Mono emitting the raw JSON response body
     */
    public Mono<String> fetchJson(int port, HttpMethod method, String path, String body, int timeoutSec,
        String secretKey) {
        String target = goosedBaseUrl(port) + path;
        long start = System.currentTimeMillis();
        log.info("[GOOSED-PROXY] request method={} path={} port={}", method, path, port);
        WebClient.RequestBodySpec spec = webClient.method(method)
            .uri(target)
            .header(GatewayConstants.HEADER_SECRET_KEY, secretKey)
            .header(HttpHeaders.CONTENT_TYPE, "application/json");

        WebClient.RequestHeadersSpec<?> ready = body != null ? spec.bodyValue(body) : spec;

        return ready.retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(timeoutSec))
            .doOnNext(resp -> log.info(
                "[GOOSED-PROXY] response method={} path={} port={} status=200 " + "bodyLen={} durationMs={}", method,
                path, port, resp != null ? resp.length() : 0, System.currentTimeMillis() - start))
            .doOnError(err -> log.warn("[GOOSED-PROXY] error method={} path={} port={} error={} " + "durationMs={}",
                method, path, port, err.getMessage(), System.currentTimeMillis() - start))
            .onErrorMap(this::isProxyError, this::mapProxyError);
    }

    /**
     * Gets the underlying WebClient instance.
     *
     * @return the shared WebClient instance
     */
    public WebClient getWebClient() {
        return webClient;
    }

    private boolean isProxyError(Throwable e) {
        return e instanceof WebClientRequestException || e instanceof TimeoutException;
    }

    /**
     * Map low-level Netty connection errors and timeouts to 503 Service Unavailable
     * instead of letting them bubble as 500 Internal Server Error.
     */
    private Throwable mapProxyError(Throwable e) {
        if (e instanceof TimeoutException) {
            log.warn("Goosed proxy timeout: {}", e.getMessage());
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Agent did not respond in time");
        }
        log.warn("Goosed connection error: {}", e.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Agent temporarily unavailable: " + e.getMessage());
    }

    private WebClientResponseException toUpstreamResponseException(int rawStatusCode, HttpHeaders headers,
        String body) {
        HttpStatus status = HttpStatus.resolve(rawStatusCode);
        String statusText = status != null ? status.getReasonPhrase() : "HTTP " + rawStatusCode;
        return WebClientResponseException.create(rawStatusCode, statusText, headers,
            body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private void copyHeaders(HttpHeaders source, HttpHeaders target, String secretKey) {
        target.addAll(source);
        target.set(GatewayConstants.HEADER_SECRET_KEY, secretKey);
    }

    private void copyUpstreamHeaders(HttpHeaders source, HttpHeaders target) {
        // CORS is handled by gateway filter; do not forward upstream CORS headers.
        source.forEach((name, values) -> {
            if (HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.equalsIgnoreCase(name)
                || HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS.equalsIgnoreCase(name)
                || HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS.equalsIgnoreCase(name)
                || HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.equalsIgnoreCase(name)
                || HttpHeaders.ACCESS_CONTROL_MAX_AGE.equalsIgnoreCase(name)
                || HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS.equalsIgnoreCase(name)) {
                return;
            }
            target.put(name, values);
        });
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
