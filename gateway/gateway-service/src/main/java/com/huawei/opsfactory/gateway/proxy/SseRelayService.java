package com.huawei.opsfactory.gateway.proxy;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SseRelayService {

    private static final Logger log = LogManager.getLogger(SseRelayService.class);

    private final GoosedProxy goosedProxy;
    private final WebClient webClient;
    private final GatewayProperties properties;
    private final InstanceManager instanceManager;

    public SseRelayService(GoosedProxy goosedProxy, GatewayProperties properties,
                           InstanceManager instanceManager) {
        this.goosedProxy = goosedProxy;
        this.webClient = goosedProxy.getWebClient();
        this.properties = properties;
        this.instanceManager = instanceManager;
    }

    /**
     * Relay SSE stream from a goosed instance.
     * Returns a Flux of raw DataBuffer chunks for zero-copy streaming.
     *
     * Three timeout layers protect against goosed hangs:
     * 1. firstByteTimeout — abort if no data arrives at all (goosed truly hung) → RECYCLE instance
     * 2. idleTimeout — abort if no real content for too long (LLM slow) → ERROR to client, NO recycle
     *    Pings prove goosed is alive; the LLM is just slow. Killing the instance is wrong.
     * 3. maxDuration — hard ceiling on any single reply
     */
    public Flux<DataBuffer> relay(int port, String path, String body,
                                   String agentId, String userId) {
        String target = goosedProxy.goosedBaseUrl(port) + path;
        long startTime = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicInteger pingCount = new AtomicInteger(0);
        AtomicLong lastChunkTime = new AtomicLong(startTime);
        AtomicLong lastContentTime = new AtomicLong(startTime);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicBoolean upstreamDone = new AtomicBoolean(false);

        GatewayProperties.Sse sseConfig = properties.getSse();
        Duration firstByteTimeout = Duration.ofSeconds(sseConfig.getFirstByteTimeoutSec());
        Duration idleTimeout = Duration.ofSeconds(sseConfig.getIdleTimeoutSec());
        Duration maxDuration = Duration.ofSeconds(sseConfig.getMaxDurationSec());

        log.info("[SSE-DIAG] relay START → {} body={}chars firstByte={}s idle={}s max={}s",
                target, body.length(),
                sseConfig.getFirstByteTimeoutSec(),
                sseConfig.getIdleTimeoutSec(),
                sseConfig.getMaxDurationSec());

        Flux<DataBuffer> upstream = webClient.post()
                .uri(target)
                .header(GatewayConstants.HEADER_SECRET_KEY, properties.getSecretKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnNext(buf -> {
                    int seq = chunkCount.incrementAndGet();
                    long now = System.currentTimeMillis();
                    long gap = now - lastChunkTime.getAndSet(now);
                    int readable = buf.readableByteCount();
                    totalBytes.addAndGet(readable);

                    // Only reset content idle timer for non-Ping chunks
                    if (isPingChunk(buf)) {
                        pingCount.incrementAndGet();
                    } else {
                        lastContentTime.set(now);
                    }

                    // DEBUG: log every chunk for diagnosis
                    String preview = peekContent(buf, 200);
                    boolean isPing = isPingChunk(buf);
                    long contentIdleMs = now - lastContentTime.get();
                    if (seq <= 3 || seq % 10 == 0 || gap > 5000 || !isPing) {
                        log.info("[SSE-DIAG] chunk#{} {}B gap={}ms elapsed={}ms ping={} contentIdle={}ms preview={}",
                                seq, readable, gap, now - startTime, isPing, contentIdleMs, preview);
                    } else {
                        log.debug("[SSE-DIAG] chunk#{} {}B gap={}ms elapsed={}ms ping={} contentIdle={}ms preview={}",
                                seq, readable, gap, now - startTime, isPing, contentIdleMs, preview);
                    }
                })
                .doOnError(e -> {
                    upstreamDone.set(true);
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.error("[SSE-DIAG] relay ERROR after {}ms, chunks={}, bytes={}: {}",
                            elapsed, chunkCount.get(), totalBytes.get(), e.getMessage());
                })
                .doOnComplete(() -> {
                    upstreamDone.set(true);
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("[SSE-DIAG] relay COMPLETE {}ms chunks={} bytes={}",
                            elapsed, chunkCount.get(), totalBytes.get());
                })
                .doOnCancel(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.warn("[SSE-DIAG] relay CANCELLED after {}ms, chunks={}, bytes={}",
                            elapsed, chunkCount.get(), totalBytes.get());
                    // Client disconnected — tell goosed to stop processing the reply
                    stopAgentAsync(port, body);
                });

        // Layer 1: First-byte timeout — abort if no data at all (goosed hung).
        // Layer 2: Content idle timeout — abort if no real content (ignoring Pings)
        //          for idleTimeout seconds. If pings ARE flowing, goosed is alive but
        //          the LLM is slow — send error to client but do NOT recycle instance.
        // Layer 3: Hard max duration ceiling.
        Flux<DataBuffer> contentIdleWatchdog = Flux.interval(Duration.ofSeconds(2))
                .takeWhile(tick -> !upstreamDone.get())
                .doOnNext(tick -> {
                    long contentIdleMs = System.currentTimeMillis() - lastContentTime.get();
                    long chunkIdleMs = System.currentTimeMillis() - lastChunkTime.get();
                    log.debug("[SSE-DIAG] watchdog tick#{} contentIdle={}ms chunkIdle={}ms threshold={}ms chunks={} pings={}",
                            tick, contentIdleMs, chunkIdleMs, idleTimeout.toMillis(),
                            chunkCount.get(), pingCount.get());
                })
                .filter(tick -> {
                    long contentIdleMs = System.currentTimeMillis() - lastContentTime.get();
                    boolean expired = contentIdleMs > idleTimeout.toMillis();
                    if (expired) {
                        log.warn("[SSE-DIAG] watchdog FIRED: contentIdle={}ms > threshold={}ms, chunks={}, pings={}",
                                contentIdleMs, idleTimeout.toMillis(), chunkCount.get(), pingCount.get());
                    }
                    return expired;
                })
                .next()
                .flatMapMany(tick -> Flux.<DataBuffer>error(new TimeoutException("Content idle timeout")));

        Flux<DataBuffer> withTimeouts = Flux.merge(
                        upstream.timeout(Mono.delay(firstByteTimeout), item -> Mono.never()),
                        contentIdleWatchdog
                )
                .take(maxDuration);

        // On timeout or connection error, emit a synthetic SSE error event.
        // Only recycle instance when goosed is truly unresponsive (no data at all).
        // If pings are flowing, goosed is alive — the LLM is just slow.
        return withTimeouts
                .onErrorResume(e -> {
                    if (e instanceof TimeoutException) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        int chunks = chunkCount.get();
                        int pings = pingCount.get();
                        boolean goosedAlive = pings > 0;

                        String reason;
                        if (chunks == 0) {
                            // No data at all — goosed is truly hung
                            reason = "No response from agent in " + sseConfig.getFirstByteTimeoutSec() + "s";
                            log.warn("[SSE-DIAG] relay TIMEOUT (goosed hung) after {}ms, chunks=0, bytes={}: {}",
                                    elapsed, totalBytes.get(), reason);
                            recycleAsync(agentId, userId, "timeout");
                        } else {
                            // Chunks arrived (pings and/or content) — goosed was responsive.
                            // Do NOT recycle: the LLM is slow or Ping detection may be imperfect.
                            reason = "LLM did not respond within " + sseConfig.getIdleTimeoutSec()
                                    + "s (chunks=" + chunks + ", pings=" + pings + ")";
                            log.warn("[SSE-DIAG] relay TIMEOUT (LLM slow) after {}ms, chunks={}, pings={}, bytes={}: {}",
                                    elapsed, chunks, pings, totalBytes.get(), reason);
                        }
                        return sseErrorEvent(reason);
                    }
                    if (e instanceof WebClientRequestException) {
                        log.warn("[SSE-DIAG] relay CONNECTION ERROR: {}", e.getMessage());
                        return sseErrorEvent("Agent connection failed: " + e.getMessage());
                    }
                    // PrematureCloseException comes wrapped in WebClientResponseException
                    if (isPrematureClose(e)) {
                        log.warn("[SSE-DIAG] relay PREMATURE CLOSE after {}ms, chunks={}, bytes={}: {}",
                                System.currentTimeMillis() - startTime,
                                chunkCount.get(), totalBytes.get(), e.getMessage());
                        // Instance likely already dead/recycled — emit error event
                        return sseErrorEvent("Agent connection lost, please retry");
                    }
                    // Other errors: propagate
                    return Flux.error(e);
                });
    }

    /**
     * Send POST /agent/stop to goosed so it aborts the current reply.
     * Extracts session_id from the original request body.
     */
    private void stopAgentAsync(int port, String body) {
        Mono.fromRunnable(() -> {
            try {
                // Extract session_id from the reply body (JSON: {"session_id":"...","user_message":...})
                String sessionId = extractSessionId(body);
                if (sessionId == null) {
                    log.warn("[SSE-DIAG] Cannot stop agent: no session_id in body");
                    return;
                }
                String stopBody = "{\"session_id\":\"" + sessionId + "\"}";
                String target = goosedProxy.goosedBaseUrl(port) + "/agent/stop";
                webClient.post()
                        .uri(target)
                        .header(GatewayConstants.HEADER_SECRET_KEY, properties.getSecretKey())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .bodyValue(stopBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(5))
                        .subscribe(
                                resp -> log.info("[SSE-DIAG] stop sent for session {}", sessionId),
                                err -> log.warn("[SSE-DIAG] stop failed for session {}: {}", sessionId, err.getMessage())
                        );
            } catch (Exception e) {
                log.warn("[SSE-DIAG] stopAgentAsync error: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private static String extractSessionId(String body) {
        // Simple extraction without Jackson dependency — find "session_id":"<value>"
        int idx = body.indexOf("\"session_id\"");
        if (idx < 0) return null;
        int colonIdx = body.indexOf(':', idx);
        if (colonIdx < 0) return null;
        int startQuote = body.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = body.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return body.substring(startQuote + 1, endQuote);
    }

    /**
     * Kill the hung instance on a separate thread to avoid blocking the SSE response.
     */
    private void recycleAsync(String agentId, String userId, String reason) {
        Mono.fromRunnable(() -> {
            log.info("[SSE-DIAG] Recycling hung instance {}:{} reason={}", agentId, userId, reason);
            instanceManager.forceRecycle(agentId, userId);
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * Check if a DataBuffer contains ONLY Ping SSE events (no real content).
     * A single Ping is 'data: {"type":"Ping"}\n\n' (~23 bytes).
     * Multiple Pings can be batched into one DataBuffer by TCP buffering.
     */
    private static boolean isPingChunk(DataBuffer buf) {
        String content = peekContent(buf, 500);
        if (!content.contains("\"type\":\"Ping\"")) return false;
        // Contains Ping — check that there's no other event type mixed in
        String stripped = content
                .replace("data: {\"type\":\"Ping\"}", "")
                .replace("\\n", "")
                .trim();
        return stripped.isEmpty();
    }

    /**
     * Check if the error is a PrematureCloseException (connection lost mid-stream).
     * This occurs when goosed is killed while an SSE stream is active.
     */
    private boolean isPrematureClose(Throwable e) {
        // Direct PrematureCloseException
        if (e.getClass().getSimpleName().equals("PrematureCloseException")) return true;
        // Wrapped in WebClientResponseException
        Throwable cause = e.getCause();
        return cause != null && cause.getClass().getSimpleName().equals("PrematureCloseException");
    }

    /**
     * Create a synthetic SSE error event that the webapp can parse and display.
     */
    private Flux<DataBuffer> sseErrorEvent(String reason) {
        String ssePayload = "data: {\"type\":\"Error\",\"error\":\"" +
                reason.replace("\"", "\\\"") + "\"}\n\n";
        DataBuffer buf = DefaultDataBufferFactory.sharedInstance
                .wrap(ssePayload.getBytes(StandardCharsets.UTF_8));
        return Flux.just(buf);
    }

    /**
     * Peek at the first N bytes of a DataBuffer without consuming it.
     */
    private static String peekContent(DataBuffer buf, int maxLen) {
        try {
            int readable = buf.readableByteCount();
            int len = Math.min(readable, maxLen);
            byte[] bytes = new byte[len];
            int pos = buf.readPosition();
            buf.read(bytes);
            buf.readPosition(pos); // reset position so downstream can still read
            String s = new String(bytes, StandardCharsets.UTF_8)
                    .replace("\n", "\\n").replace("\r", "\\r");
            return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
        } catch (Exception e) {
            return "<peek-error>";
        }
    }
}
