/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.common.util.FileUtil;
import com.huawei.opsfactory.gateway.filter.RequestContextFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.logging.GatewayLogContext;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.SessionCacheService;
import com.huawei.opsfactory.gateway.service.SessionService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REST controller for session lifecycle: start, list, get, rename, delete, and cleanup.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping(value = "/gateway")
public class SessionController {
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InstanceManager instanceManager;

    private final SessionService sessionService;

    private final GoosedProxy goosedProxy;

    private final AgentConfigService agentConfigService;

    private final SessionCacheService sessionCacheService;

    /**
     * Creates the session controller instance.
     *
     * @param instanceManager agent instance lifecycle manager
     * @param sessionService session query service backed by goosed instances
     * @param goosedProxy HTTP proxy for forwarding requests to goosed processes
     * @param agentConfigService agent configuration and directory resolver
     * @param sessionCacheService in-memory session list cache
     */
    public SessionController(InstanceManager instanceManager, SessionService sessionService, GoosedProxy goosedProxy,
        AgentConfigService agentConfigService, SessionCacheService sessionCacheService) {
        this.instanceManager = instanceManager;
        this.sessionService = sessionService;
        this.goosedProxy = goosedProxy;
        this.agentConfigService = agentConfigService;
        this.sessionCacheService = sessionCacheService;
    }

    /**
     * Starts a new agent session and loads model and extensions.
     *
     * @param agentId agent identifier from the URL path
     * @param body JSON request body containing session configuration
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono emitting the goosed start-session response as a JSON string
     */
    @PostMapping(value = "/agents/{agentId}/agent/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> startSession(@PathVariable("agentId") String agentId, @RequestBody String body,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        long requestStart = System.currentTimeMillis();
        // Inject working_dir into the request body (override any client-supplied value)
        String workingDir = agentConfigService.getUserAgentDir(userId, agentId).toAbsolutePath().normalize().toString();
        String modifiedBody;
        try {
            java.util.Map<String, Object> bodyMap =
                MAPPER.readValue(body, new TypeReference<java.util.Map<String, Object>>() {});
            bodyMap.put("working_dir", workingDir);
            modifiedBody = MAPPER.writeValueAsString(bodyMap);
        } catch (JsonProcessingException e) {
            modifiedBody = "{\"working_dir\":\"" + workingDir.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        }
        String finalBody = modifiedBody;
        boolean resident = agentConfigService.isResidentInstance(agentId, userId);
        GatewayLogContext.run(requestId, userId,
            () -> log.info("[SESSION-START] begin agentId={} userId={} " + "resident={} bodyLen={}", agentId, userId,
                resident, body.length()));
        return instanceManager.getOrSpawn(agentId, userId).flatMap(instance -> {
            long afterInstanceMs = System.currentTimeMillis() - requestStart;
            GatewayLogContext.run(requestId, userId,
                () -> log.info(
                    "[SESSION-START] instance resolved "
                        + "agentId={} userId={} resident={} port={} pid={} resolveMs={}",
                    agentId, userId, resident, instance.getPort(), instance.getPid(), afterInstanceMs));
            long startCallStart = System.currentTimeMillis();
            return goosedProxy
                .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/start", finalBody, 120, instance.getSecretKey())
                .flatMap(startResponse -> {
                    long startCallMs = System.currentTimeMillis() - startCallStart;
                    // Follow goosed canonical flow: start → resume(load_model_and_extensions=true)
                    // Extensions must be loaded before the session is returned to the client.
                    // This matches Node.js legacy and Goose Desktop behavior.
                    String sessionId = extractSessionId(startResponse);
                    String resumeBody = "{\"session_id\":\"" + sessionId + "\",\"load_model_and_extensions\":true}";
                    GatewayLogContext.run(requestId, userId, sessionId,
                        () -> log.info("[SESSION-START] "
                            + "goosed start complete agentId={} userId={} sessionId={} port={} " + "startCallMs={}",
                            agentId, userId, sessionId, instance.getPort(), startCallMs));
                    long resumeStart = System.currentTimeMillis();
                    return goosedProxy
                        .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", resumeBody, 120,
                            instance.getSecretKey())
                        .doOnNext(r -> {
                            long resumeMs = System.currentTimeMillis() - resumeStart;
                            instance.markSessionResumed(sessionId);
                            GatewayLogContext.run(requestId, userId, sessionId,
                                () -> log.info(
                                    "[SESSION-START] session ready agentId={} userId={} sessionId={}"
                                        + " resident={} port={} resumeMs={} totalMs={}",
                                    agentId, userId, sessionId, resident, instance.getPort(), resumeMs,
                                    System.currentTimeMillis() - requestStart));
                        })
                        .thenReturn(startResponse);
                });
        });
    }

    private String extractSessionId(String startResponse) {
        try {
            Map<String, Object> map = MAPPER.readValue(startResponse, new TypeReference<Map<String, Object>>() {});
            Object id = map.get("id");
            return id != null ? id.toString() : null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse session ID from start response", e);
        }
    }

    /**
     * Lists all sessions for the current user across all agents with pagination and filtering.
     *
     * @param pageIndex 1-based page number (defaults to 1)
     * @param pageSize number of items per page (defaults to 20, max 200)
     * @param search optional case-insensitive substring filter on session name
     * @param agentId optional filter to return only sessions for this agent
     * @param type optional session type filter: "user" or "scheduled"
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono emitting a paginated JSON object with sessions, total, pageIndex, and pageSize
     */
    @GetMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> listAllSessions(@RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "search", required = false) String search,
        @RequestParam(value = "agentId", required = false) String agentId,
        @RequestParam(value = "type", required = false) String type,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId,
            () -> log.info("[SESSION-LIST] begin userId={} page={}/{} search={} agentId={} type={}", userId, pageIndex,
                pageSize, search, agentId, type));
        int safePageIndex = Math.max(1, pageIndex);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));

        // Try cache first
        List<Map<String, Object>> cached = sessionCacheService.get(userId);
        if (cached != null) {
            String result = applyFiltersAndPaginate(cached, safePageIndex, safePageSize, search, agentId, type);
            GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-LIST] cache hit userId={}", userId));
            return Mono.just(result);
        }

        return Flux.fromIterable(instanceManager.getAllInstances())
            .filter(inst -> inst.getUserId().equals(userId))
            .filter(inst -> inst.getStatus() == ManagedInstance.Status.RUNNING)
            .flatMap(inst -> sessionService.getSessionsFromInstance(inst)
                .map(json -> extractSessionsArray(json, inst.getAgentId())))
            .collectList()
            .map(lists -> {
                List<String> allSessions = new ArrayList<>();
                for (List<String> batch : lists) {
                    allSessions.addAll(batch);
                }
                // Parse all sessions and sort
                List<Map<String, Object>> parsed = new ArrayList<>();
                for (String json : allSessions) {
                    try {
                        Map<String, Object> m = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
                        parsed.add(m);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse session JSON: {}", e.getMessage());
                    }
                }
                parsed.sort((a, b) -> {
                    String ta = a.getOrDefault("created_at", "") instanceof String s ? s : "";
                    String tb = b.getOrDefault("created_at", "") instanceof String s ? s : "";
                    try {
                        return Instant.parse(tb).compareTo(Instant.parse(ta));
                    } catch (DateTimeParseException e) {
                        return tb.compareTo(ta);
                    }
                });
                // Cache atomically: concurrent request may have already cached
                List<Map<String, Object>> effective = sessionCacheService.getOrFetch(userId, () -> parsed);
                GatewayLogContext.run(requestId, userId,
                    () -> log.info("[SESSION-LIST] fetched userId={} total={}", userId, effective.size()));
                return applyFiltersAndPaginate(effective, safePageIndex, safePageSize, search, agentId, type);
            });
    }

    /**
     * Parse goosed response and extract individual session JSON strings,
     * injecting agentId into each.
     */
    private String applyFiltersAndPaginate(List<Map<String, Object>> sortedSessions, int pageIndex, int pageSize,
        String search, String agentId, String type) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> m : sortedSessions) {
            if (agentId != null && !agentId.isBlank() && !agentId.equals(m.get("agentId"))) {
                continue;
            }
            if (type != null && !type.isBlank()) {
                String sessionType = m.getOrDefault("session_type", "user") instanceof String s ? s : "user";
                String scheduleId = m.get("schedule_id") instanceof String s && !s.isBlank() ? s : null;
                if ("user".equals(type) && (!"user".equals(sessionType) || scheduleId != null)) {
                    continue;
                }
                if ("scheduled".equals(type) && (scheduleId == null && !"scheduled".equals(sessionType))) {
                    continue;
                }
            }
            if (search != null && !search.isBlank()) {
                String name = m.getOrDefault("name", "") instanceof String s ? s.toLowerCase(Locale.ROOT) : "";
                if (!name.contains(search.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            filtered.add(m);
        }
        int total = filtered.size();
        int from = Math.min((pageIndex - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> page = from < total ? new ArrayList<>(filtered.subList(from, to)) : List.of();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessions", page);
        response.put("total", total);
        response.put("pageIndex", pageIndex);
        response.put("pageSize", pageSize);
        try {
            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize paginated response", e);
        }
    }

    /**
     * Parse goosed response and extract individual session JSON strings,
     * injecting agentId into each.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractSessionsArray(String json, String agentId) {
        List<String> result = new ArrayList<>();
        try {
            Map<String, Object> wrapper = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object sessionsObj = wrapper.get("sessions");
            if (sessionsObj instanceof List<?> sessions) {
                for (Object s : sessions) {
                    if (s instanceof Map<?, ?> sessionMap) {
                        Map<String, Object> mutable = new java.util.LinkedHashMap<>((Map<String, Object>) sessionMap);
                        mutable.put("agentId", agentId);
                        result.add(MAPPER.writeValueAsString(mutable));
                    }
                }
            }
        } catch (JsonProcessingException e) {
            // If parsing fails, try treating as a raw array
            try {
                List<Map<String, Object>> sessions =
                    MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> s : sessions) {
                    Map<String, Object> mutable = new java.util.LinkedHashMap<>(s);
                    mutable.put("agentId", agentId);
                    result.add(MAPPER.writeValueAsString(mutable));
                }
            } catch (JsonProcessingException e2) {
                log.warn("Failed to parse sessions from instance: {}", e2.getMessage());
            }
        }
        return result;
    }

    /**
     * Lists all sessions for a specific agent by proxying the request to the goosed instance.
     *
     * @param agentId agent identifier from the URL path
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono that completes when the proxied response has been written back
     */
    @GetMapping("/agents/{agentId}/sessions")
    public Mono<Void> listAgentSessions(@PathVariable("agentId") String agentId, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.proxy(exchange.getRequest(), exchange.getResponse(), instance.getPort(),
                "/sessions", instance.getSecretKey()));
    }

    /**
     * Gets a session by agent ID and session ID.
     *
     * @param agentId agent identifier from the URL path
     * @param sessionId session identifier from the URL path
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono emitting the session detail as a JSON string with agentId injected
     */
    @GetMapping(value = "/agents/{agentId}/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getSession(@PathVariable("agentId") String agentId, @PathVariable("sessionId") String sessionId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId,
            () -> log.info("[SESSION-GET] begin agentId={} userId={} sessionId={}", agentId, userId, sessionId));
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.fetchJson(instance.getPort(), "/sessions/" + sessionId,
                instance.getSecretKey()))
            .map(json -> injectAgentId(json, agentId))
            .doOnSuccess(json -> GatewayLogContext.run(requestId, userId,
                () -> log.info("[SESSION-GET]" + " complete agentId={} userId={} sessionId={}", agentId, userId,
                    sessionId)))
            .onErrorResume(WebClientResponseException.class, e -> {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
                }
                return Mono.error(e);
            });
    }

    /**
     * Global session detail: GET /sessions/{sessionId}?agentId=X.
     *
     * @param sessionId session identifier from the URL path
     * @param agentId agent identifier passed as a required query parameter
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono emitting the session detail as a JSON string with agentId injected
     */
    @GetMapping(value = "/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getSessionGlobal(@PathVariable("sessionId") String sessionId, @RequestParam("agentId") String agentId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log
            .info("[SESSION-GET] begin agentId={} userId={} sessionId={} scope=global", agentId, userId, sessionId));
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.fetchJson(instance.getPort(), "/sessions/" + sessionId,
                instance.getSecretKey()))
            .map(json -> injectAgentId(json, agentId))
            .doOnSuccess(json -> GatewayLogContext.run(requestId, userId,
                () -> log.info("[SESSION-GET] " + "complete agentId={} userId={} sessionId={} scope=global", agentId,
                    userId, sessionId)))
            .onErrorResume(WebClientResponseException.class, e -> {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
                }
                return Mono.error(e);
            });
    }

    /**
     * Deletes a session by agent ID and session ID, also cleaning up associated uploads.
     *
     * @param agentId agent identifier from the URL path
     * @param sessionId session identifier from the URL path
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono that completes when the delete has been proxied
     */
    @DeleteMapping("/agents/{agentId}/sessions/{sessionId}")
    public Mono<Void> deleteSession(@PathVariable("agentId") String agentId, @PathVariable("sessionId") String sessionId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId,
            () -> log.info("[SESSION-DELETE] begin agentId={} userId={} sessionId={}", agentId, userId, sessionId));
        Mono.fromRunnable(() -> cleanupUploads(userId, agentId, sessionId))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.proxy(exchange.getRequest(), exchange.getResponse(), instance.getPort(),
                "/sessions/" + sessionId, instance.getSecretKey()))
            .doOnSuccess(ignored -> {
                sessionCacheService.invalidate(userId);
                GatewayLogContext.run(requestId, userId,
                    () -> log.info("[SESSION-DELETE] complete agentId={} " + "userId={} sessionId={}", agentId, userId,
                        sessionId));
            });
    }

    /**
     * Deletes a session globally by session ID with an agent ID query parameter.
     *
     * @param sessionId session identifier from the URL path
     * @param agentId agent identifier passed as a required query parameter
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono that completes when the delete has been proxied
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Mono<Void> deleteSessionGlobal(@PathVariable("sessionId") String sessionId, @RequestParam("agentId") String agentId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log
            .info("[SESSION-DELETE] begin agentId={} userId={} sessionId={} scope=global", agentId, userId, sessionId));
        Mono.fromRunnable(() -> cleanupUploads(userId, agentId, sessionId))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.proxy(exchange.getRequest(), exchange.getResponse(), instance.getPort(),
                "/sessions/" + sessionId, instance.getSecretKey()))
            .doOnSuccess(ignored -> {
                sessionCacheService.invalidate(userId);
                GatewayLogContext.run(requestId, userId,
                    () -> log.info("[SESSION-DELETE] complete agentId={} " + "userId={} sessionId={} scope=global",
                        agentId, userId, sessionId));
            });
    }

    /**
     * Cleans up an empty session if it has no conversation or messages.
     *
     * @param agentId agent identifier from the URL path
     * @param sessionId session identifier from the URL path
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono emitting a map with "deleted" (boolean) and "reason" (string) keys
     */
    @PostMapping(value = "/agents/{agentId}/sessions/{sessionId}/cleanup-empty",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> cleanupEmptySession(@PathVariable("agentId") String agentId, @PathVariable("sessionId") String sessionId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log
            .info("[SESSION-CLEANUP-EMPTY] begin agentId={} " + "userId={} sessionId={}", agentId, userId, sessionId));

        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(
                instance -> goosedProxy.fetchJson(instance.getPort(), "/sessions/" + sessionId, instance.getSecretKey())
                    .flatMap(json -> {
                        EmptySessionDecision decision = shouldDeleteEmptySession(json);
                        if (!decision.delete()) {
                            GatewayLogContext.run(requestId, userId,
                                () -> log.info(
                                    "[SESSION-CLEANUP-EMPTY] skip agentId={} userId={} sessionId={} reason={}", agentId,
                                    userId, sessionId, decision.reason()));
                            return Mono.just(cleanupResult(false, decision.reason()));
                        }

                        return goosedProxy
                            .fetchJson(instance.getPort(), HttpMethod.DELETE, "/sessions/" + sessionId, null, 30,
                                instance.getSecretKey())
                            .then(Mono.fromRunnable(() -> cleanupUploads(userId, agentId, sessionId))
                                .subscribeOn(Schedulers.boundedElastic()))
                            .thenReturn(cleanupResult(true, "empty_session_deleted"))
                            .doOnSuccess(result -> GatewayLogContext.run(requestId, userId,
                                () -> log.info("[SESSION-CLEANUP-EMPTY] deleted agentId={} userId={} sessionId={}",
                                    agentId, userId, sessionId)));
                    }))
            .onErrorResume(WebClientResponseException.class, e -> {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return Mono.just(cleanupResult(false, "session_not_found"));
                }
                return Mono.error(e);
            });
    }

    private String injectAgentId(String json, String agentId) {
        try {
            java.util.Map<String, Object> map =
                MAPPER.readValue(json, new TypeReference<java.util.Map<String, Object>>() {});
            map.put("agentId", agentId);
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // If parsing fails, just return original
            return json;
        }
    }

    private void cleanupUploads(String userId, String agentId, String sessionId) {
        try {
            Path uploadsDir = agentConfigService.getUserAgentDir(userId, agentId).resolve("uploads").resolve(sessionId);
            if (Files.isDirectory(uploadsDir)) {
                FileUtil.deleteRecursively(uploadsDir);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up uploads for session {}: {}", sessionId, e.getMessage());
        }
    }

    private EmptySessionDecision shouldDeleteEmptySession(String json) {
        try {
            JsonNode session = MAPPER.readTree(json);
            String sessionType = textValue(session.get("session_type"));
            if (sessionType != null && !"user".equals(sessionType)) {
                return new EmptySessionDecision(false, "not_user_session");
            }
            if (hasText(session.get("schedule_id"))) {
                return new EmptySessionDecision(false, "scheduled_session");
            }
            if (session.path("user_set_name").asBoolean(false)) {
                return new EmptySessionDecision(false, "user_named_session");
            }

            JsonNode conversation = session.get("conversation");
            if (conversation != null && conversation.isArray()) {
                if (!conversation.isEmpty()) {
                    return new EmptySessionDecision(false, "has_conversation");
                }
            }

            JsonNode messageCount = session.get("message_count");
            if (messageCount != null && messageCount.canConvertToInt()) {
                return messageCount.asInt() == 0 ? new EmptySessionDecision(true, "empty_message_count")
                    : new EmptySessionDecision(false, "has_messages");
            }

            if (conversation != null && conversation.isArray()) {
                return new EmptySessionDecision(true, "empty_conversation");
            }

            return new EmptySessionDecision(false, "message_count_unknown");
        } catch (JsonProcessingException e) {
            log.warn("Failed to inspect session for empty cleanup: {}", e.getMessage());
            return new EmptySessionDecision(false, "invalid_session_payload");
        }
    }

    private Map<String, Object> cleanupResult(boolean deleted, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("reason", reason);
        return result;
    }

    private boolean hasText(JsonNode node) {
        return node != null && !node.isNull() && !node.asText("").isBlank();
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() ? null : value;
    }

    /**
     * Rename session: PUT /agents/{agentId}/sessions/{sessionId}/name.
     *
     * @param agentId agent identifier from the URL path
     * @param sessionId session identifier from the URL path
     * @param body JSON body containing the new session name
     * @param exchange current server web exchange carrying user context attributes
     * @return Mono that completes when the rename has been proxied
     */
    @PutMapping("/agents/{agentId}/sessions/{sessionId}/name")
    public Mono<Void> renameSession(@PathVariable("agentId") String agentId, @PathVariable("sessionId") String sessionId,
        @RequestBody String body, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId,
            () -> log.info("[SESSION-RENAME] begin agentId={} userId={} " + "sessionId={} bodyLen={}", agentId, userId,
                sessionId, body.length()));
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.proxyWithBody(exchange.getResponse(), instance.getPort(),
                "/sessions/" + sessionId + "/name", HttpMethod.PUT, body, instance.getSecretKey()))
            .doOnSuccess(ignored -> {
                sessionCacheService.invalidate(userId);
                GatewayLogContext.run(requestId, userId,
                    () -> log.info("[SESSION-RENAME] complete agentId={} " + "userId={} sessionId={}", agentId, userId,
                        sessionId));
            });
    }

    private record EmptySessionDecision(boolean delete, String reason) {
    }
}
