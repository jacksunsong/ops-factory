package com.huawei.opsfactory.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.common.util.JsonUtil;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.hook.HookContext;
import com.huawei.opsfactory.gateway.hook.HookPipeline;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/gateway/agents/{agentId}")
public class ReplyController {

    private static final Logger log = LoggerFactory.getLogger(ReplyController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InstanceManager instanceManager;
    private final GoosedProxy goosedProxy;
    private final HookPipeline hookPipeline;
    private final ConcurrentHashMap<String, Mono<String>> inFlightResumes = new ConcurrentHashMap<>();

    public ReplyController(InstanceManager instanceManager,
                           GoosedProxy goosedProxy,
                           HookPipeline hookPipeline) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.hookPipeline = hookPipeline;
    }

    @PostMapping(value = "/sessions/{sessionId}/reply", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> sessionReply(@PathVariable String agentId,
                                   @PathVariable String sessionId,
                                   @RequestBody String body,
                                   ServerWebExchange exchange) {
        long requestStart = System.currentTimeMillis();
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        HookContext ctx = new HookContext(body, agentId, userId);
        log.info("[SESSION-REPLY] request received agentId={} userId={} sessionId={} bodyLen={}",
                agentId, userId, sessionId, body.length());
        String requestId = extractRequestId(body);

        return hookPipeline.executeRequest(ctx)
                .map(this::normalizeReplyUserMessageCreated)
                .flatMap(processedBody -> instanceManager.getOrSpawn(agentId, userId)
                        .flatMap(instance -> {
                            instance.touch();
                            instanceManager.touchAllForUser(userId);
                            String path = goosedSessionPath(sessionId, "reply");
                            return ensureSessionResumed(instance, sessionId)
                                    .then(goosedProxy.proxyWithBody(exchange.getResponse(), instance.getPort(), path,
                                            HttpMethod.POST, processedBody, instance.getSecretKey()))
                                    .doOnSubscribe(sub -> log.info("[SESSION-REPLY] forwarding agentId={} userId={} sessionId={} port={} path={}",
                                            agentId, userId, sessionId, instance.getPort(), path))
                                    .doOnSuccess(ignored -> log.info("[SESSION-REPLY] completed agentId={} userId={} sessionId={} totalMs={} status={}",
                                            agentId, userId, sessionId, System.currentTimeMillis() - requestStart,
                                            exchange.getResponse().getStatusCode()))
                                    .doOnError(err -> log.warn("[SESSION-REPLY] failed agentId={} userId={} sessionId={} totalMs={} error={}",
                                            agentId, userId, sessionId, System.currentTimeMillis() - requestStart,
                                            err.getMessage()));
                        }))
                .onErrorResume(err -> writeSessionError(exchange, err, agentId, userId, sessionId, requestId,
                        "gateway_submit_failed", requestStart));
    }

    @GetMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<Void> sessionEvents(@PathVariable String agentId,
                                    @PathVariable String sessionId,
                                    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                    ServerWebExchange exchange) {
        long requestStart = System.currentTimeMillis();
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("[SESSION-EVENTS] subscribe agentId={} userId={} sessionId={} lastEventId={}",
                agentId, userId, sessionId, lastEventId);

        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> {
                    instance.touch();
                    instanceManager.touchAllForUser(userId);
                    String path = goosedSessionPath(sessionId, "events");
                    return ensureSessionResumed(instance, sessionId)
                            .then(goosedProxy.proxySessionEvents(exchange.getResponse(), instance.getPort(), path,
                                    instance.getSecretKey(), lastEventId))
                            .doOnSubscribe(sub -> log.info("[SESSION-EVENTS] forwarding agentId={} userId={} sessionId={} port={} path={}",
                                    agentId, userId, sessionId, instance.getPort(), path))
                            .doOnSuccess(ignored -> log.info("[SESSION-EVENTS] ended agentId={} userId={} sessionId={} totalMs={} status={}",
                                    agentId, userId, sessionId, System.currentTimeMillis() - requestStart,
                                    exchange.getResponse().getStatusCode()))
                            .doOnError(err -> log.warn("[SESSION-EVENTS] failed agentId={} userId={} sessionId={} totalMs={} error={}",
                                    agentId, userId, sessionId, System.currentTimeMillis() - requestStart,
                                    err.getMessage()));
                })
                .onErrorResume(err -> writeSessionError(exchange, err, agentId, userId, sessionId, null,
                        "gateway_events_failed", requestStart));
    }

    @PostMapping(value = "/sessions/{sessionId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> sessionCancel(@PathVariable String agentId,
                                    @PathVariable String sessionId,
                                    @RequestBody String body,
                                    ServerWebExchange exchange) {
        long requestStart = System.currentTimeMillis();
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        log.info("[SESSION-CANCEL] request received agentId={} userId={} sessionId={} bodyLen={}",
                agentId, userId, sessionId, body.length());
        String requestId = extractRequestId(body);

        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> {
                    instance.touch();
                    instanceManager.touchAllForUser(userId);
                    String path = goosedSessionPath(sessionId, "cancel");
                    return goosedProxy.proxyWithBody(exchange.getResponse(), instance.getPort(), path,
                                    HttpMethod.POST, body, instance.getSecretKey())
                            .doOnSubscribe(sub -> log.info("[SESSION-CANCEL] forwarding agentId={} userId={} sessionId={} port={} path={}",
                                    agentId, userId, sessionId, instance.getPort(), path))
                            .doOnSuccess(ignored -> log.info("[SESSION-CANCEL] completed agentId={} userId={} sessionId={} totalMs={} status={}",
                                    agentId, userId, sessionId, System.currentTimeMillis() - requestStart,
                                    exchange.getResponse().getStatusCode()))
                            .doOnError(err -> log.warn("[SESSION-CANCEL] failed agentId={} userId={} sessionId={} totalMs={} error={}",
                                    agentId, userId, sessionId, System.currentTimeMillis() - requestStart,
                                    err.getMessage()));
                })
                .onErrorResume(err -> writeSessionError(exchange, err, agentId, userId, sessionId, requestId,
                        "gateway_cancel_failed", requestStart));
    }

    private String goosedSessionPath(String sessionId, String suffix) {
        return "/sessions/" + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8) + "/" + suffix;
    }

    private String extractRequestId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = MAPPER.readTree(body);
            JsonNode requestIdNode = rootNode.get("request_id");
            return requestIdNode != null && requestIdNode.isTextual() ? requestIdNode.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Mono<Void> writeSessionError(ServerWebExchange exchange, Throwable err, String agentId, String userId,
                                         String sessionId, String requestId, String fallbackCode, long requestStart) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(err);
        }

        HttpStatus status = sessionErrorStatus(err);
        Map<String, Object> body = sessionErrorBody(err, status, agentId, userId, sessionId, requestId,
                fallbackCode, System.currentTimeMillis() - requestStart);
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(body);
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception writeErr) {
            return Mono.error(writeErr);
        }
    }

    private HttpStatus sessionErrorStatus(Throwable err) {
        if (err instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getStatus();
        }
        if (err instanceof WebClientResponseException webClientResponseException) {
            HttpStatus status = HttpStatus.resolve(webClientResponseException.getRawStatusCode());
            return status != null ? status : HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Map<String, Object> sessionErrorBody(Throwable err, HttpStatus status, String agentId, String userId,
                                                 String sessionId, String requestId, String fallbackCode,
                                                 long elapsedMs) {
        String code = sessionErrorCode(err, status, fallbackCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Error");
        body.put("layer", sessionErrorLayer(err, status));
        body.put("code", code);
        body.put("severity", "error");
        body.put("message_key", "chat.sessionErrors." + code);
        body.put("message", sessionErrorMessage(err, status));
        body.put("detail", err.getMessage());
        body.put("retryable", sessionErrorRetryable(code, status));
        body.put("suggested_actions", sessionSuggestedActions(code));
        body.put("session_id", sessionId);
        body.put("request_id", requestId);
        body.put("agent_id", agentId);
        body.put("user_id", userId);
        body.put("elapsed_ms", elapsedMs);
        body.put("http_status", status.value());
        body.put("trace_id", UUID.randomUUID().toString());
        if (err instanceof WebClientResponseException webClientResponseException) {
            body.put("upstream_status", webClientResponseException.getRawStatusCode());
        }
        return body;
    }

    private String sessionErrorLayer(Throwable err, HttpStatus status) {
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "policy";
        }
        if (err instanceof WebClientResponseException) {
            return "goosed";
        }
        return "gateway";
    }

    private String sessionErrorCode(Throwable err, HttpStatus status, String fallbackCode) {
        if (err instanceof WebClientResponseException) {
            if (status == HttpStatus.CONFLICT) {
                return "goosed_active_request_conflict";
            }
            if (status == HttpStatus.BAD_REQUEST) {
                return "goosed_request_rejected";
            }
            return "goosed_error";
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return "gateway_unauthorized";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "gateway_agent_not_found";
        }
        if (status == HttpStatus.FAILED_DEPENDENCY) {
            return "gateway_agent_unavailable";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "gateway_goosed_unavailable";
        }
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            return "gateway_submit_timeout";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "gateway_rate_limited";
        }
        return fallbackCode;
    }

    private String sessionErrorMessage(Throwable err, HttpStatus status) {
        if (err instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        if (err instanceof WebClientResponseException webClientResponseException
                && !webClientResponseException.getResponseBodyAsString().isBlank()) {
            return webClientResponseException.getResponseBodyAsString();
        }
        if (err.getMessage() != null && !err.getMessage().isBlank()) {
            return err.getMessage();
        }
        return status.getReasonPhrase();
    }

    private boolean sessionErrorRetryable(String code, HttpStatus status) {
        if (status.is5xxServerError()) {
            return true;
        }
        return "goosed_active_request_conflict".equals(code)
                || "gateway_rate_limited".equals(code)
                || "gateway_submit_timeout".equals(code)
                || "gateway_goosed_unavailable".equals(code);
    }

    private List<String> sessionSuggestedActions(String code) {
        return switch (code) {
            case "gateway_unauthorized" -> List.of("login", "contact_support");
            case "gateway_agent_not_found", "gateway_agent_unavailable" -> List.of("retry", "contact_support");
            case "gateway_submit_timeout", "gateway_goosed_unavailable", "gateway_rate_limited" -> List.of("retry");
            case "goosed_active_request_conflict" -> List.of("wait", "cancel", "retry");
            case "goosed_request_rejected" -> List.of("retry");
            default -> List.of("contact_support");
        };
    }

    private String normalizeReplyUserMessageCreated(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            JsonNode rootNode = MAPPER.readTree(body);
            if (!(rootNode instanceof ObjectNode)) {
                return body;
            }
            ObjectNode root = (ObjectNode) rootNode;
            JsonNode userMessageNode = root.get("user_message");
            if (!(userMessageNode instanceof ObjectNode)) {
                return body;
            }
            ObjectNode userMessage = (ObjectNode) userMessageNode;
            JsonNode roleNode = userMessage.get("role");
            if (roleNode != null && !"user".equals(roleNode.asText())) {
                return body;
            }

            JsonNode previous = userMessage.get("created");
            Long previousCreated = previous != null && previous.canConvertToLong() ? previous.asLong() : null;
            long created = Instant.now().getEpochSecond();
            userMessage.put("created", created);

            if (log.isDebugEnabled()) {
                Long delta = previousCreated != null ? created - previousCreated : null;
                log.debug("[REPLY] normalized user_message.created old={} new={} deltaSeconds={}",
                        previousCreated, created, delta);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[REPLY] failed to normalize user_message.created: {}", e.getMessage());
            return body;
        }
    }

    /**
     * Ensure that any follow-up reply against an existing session first restores that
     * session on the current goosed instance (provider + extensions loaded).
     *
     * We intentionally do not trust the gateway's local resumed-session cache here:
     * stop/recycle/page-switch flows can leave the cache and goosed's real state out
     * of sync. Treating resume as the standard precondition for session continuation
     * keeps "continue chatting" aligned with the explicit history/resume entrypoint.
     */
    private Mono<Void> ensureSessionResumed(ManagedInstance instance, String sessionId) {
        if (sessionId == null) {
            log.debug("[REPLY] session id missing, skipping pre-reply resume");
            return Mono.empty();
        }
        String resumeBody = "{\"session_id\":\"" + sessionId + "\",\"load_model_and_extensions\":true}";
        return resumeSession(instance, sessionId, resumeBody, "[REPLY]")
                .onErrorResume(e -> {
                    log.warn("[REPLY] session {} resume failed on instance {}:{}: {} (will retry next request)",
                            sessionId, instance.getAgentId(), instance.getUserId(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> resumeSession(ManagedInstance instance, String sessionId, String body, String logPrefix) {
        if (sessionId == null || sessionId.isBlank()) {
            return goosedProxy.fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", body, 120, instance.getSecretKey());
        }

        String dedupeKey = instance.getKey() + ":" + sessionId;
        return inFlightResumes.computeIfAbsent(dedupeKey, ignored -> {
            long resumeStart = System.currentTimeMillis();
            log.info("{} session {} not yet resumed on instance {}:{} (port={}), calling /agent/resume",
                    logPrefix, sessionId, instance.getAgentId(), instance.getUserId(), instance.getPort());
            return goosedProxy.fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", body, 120, instance.getSecretKey())
                    .doOnNext(r -> {
                        long resumeMs = System.currentTimeMillis() - resumeStart;
                        instance.markSessionResumed(sessionId);
                        log.info("{} session {} resumed in {}ms on instance {}:{}",
                                logPrefix, sessionId, resumeMs, instance.getAgentId(), instance.getUserId());
                    })
                    .doOnSubscribe(sub -> log.debug("{} joining in-flight resume key={}", logPrefix, dedupeKey))
                    .doFinally(signalType -> inFlightResumes.remove(dedupeKey))
                    .cache();
        });
    }

    @PostMapping(value = {"/resume", "/agent/resume"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> resume(@PathVariable String agentId,
                               @RequestBody String body,
                               ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String sessionId = JsonUtil.extractSessionId(body);
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> resumeSession(instance, sessionId, body, "[RESUME]"))
                .doOnNext(json -> logResumeConversationDigest(agentId, userId, sessionId, json))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Session not found"));
                    }
                    return Mono.error(e);
                });
    }

    private void logResumeConversationDigest(String agentId, String userId, String sessionId, String json) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode sessionNode = root.get("session");
            if (sessionNode == null || sessionNode.isNull()) {
                log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} noSessionNode jsonLen={}",
                        agentId, userId, sessionId, json != null ? json.length() : 0);
                return;
            }
            JsonNode conv = sessionNode.get("conversation");
            if (conv == null || !conv.isArray()) {
                log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} noConversation jsonLen={}",
                        agentId, userId, sessionId, json != null ? json.length() : 0);
                return;
            }

            int total = conv.size();
            int limit = Math.min(total, 30);
            List<String> head = new ArrayList<>(limit);

            int createdCount = 0;
            int inversionCount = 0;
            String prevRole = null;

            for (int i = 0; i < total; i++) {
                JsonNode m = conv.get(i);
                String role = m.hasNonNull("role") ? m.get("role").asText() : "";
                if ("user".equals(role) || "assistant".equals(role)) {
                    prevRole = prevRole == null ? role : prevRole;
                }
                if (prevRole != null && "assistant".equals(prevRole) && "user".equals(role)) {
                    inversionCount += 1;
                }
                prevRole = role;

                JsonNode created = m.get("created");
                if (created != null && created.isNumber()) {
                    createdCount += 1;
                } else if (created != null && created.isTextual() && !created.asText().trim().isEmpty()) {
                    createdCount += 1;
                } else if (m.hasNonNull("created_at") || m.hasNonNull("createdAt")) {
                    createdCount += 1;
                }

                if (i < limit) {
                    String id = m.hasNonNull("id") ? m.get("id").asText() : "";
                    String createdRaw = created != null && !created.isNull() ? created.asText() : "";
                    if (createdRaw.isEmpty() && m.hasNonNull("created_at")) {
                        createdRaw = m.get("created_at").asText();
                    }
                    if (createdRaw.isEmpty() && m.hasNonNull("createdAt")) {
                        createdRaw = m.get("createdAt").asText();
                    }
                    head.add(i + ":" + role + ":" + id + ":" + createdRaw);
                }
            }

            log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} total={} createdCount={} inversionCount={} head={}",
                    agentId, userId, sessionId, total, createdCount, inversionCount, head);
        } catch (Exception e) {
            log.debug("[CHAT-ORDER] resume agentId={} userId={} sessionId={} parseFailed err={}",
                    agentId, userId, sessionId, e.getMessage());
        }
    }

    @PostMapping({"/restart", "/agent/restart"})
    public Mono<Void> restart(@PathVariable String agentId,
                               @RequestBody String body,
                               ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxyWithBody(
                        exchange.getResponse(), instance.getPort(), "/agent/restart",
                        HttpMethod.POST, body, instance.getSecretKey()));
    }

}
