package com.huawei.opsfactory.gateway.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.common.util.FileUtil;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.filter.RequestContextFilter;
import com.huawei.opsfactory.gateway.logging.GatewayLogContext;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;

@RestController
@RequestMapping(value = "/gateway")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InstanceManager instanceManager;
    private final SessionService sessionService;
    private final GoosedProxy goosedProxy;
    private final AgentConfigService agentConfigService;
    public SessionController(InstanceManager instanceManager,
                             SessionService sessionService,
                             GoosedProxy goosedProxy,
                             AgentConfigService agentConfigService) {
        this.instanceManager = instanceManager;
        this.sessionService = sessionService;
        this.goosedProxy = goosedProxy;
        this.agentConfigService = agentConfigService;
    }

    @PostMapping(value = "/agents/{agentId}/agent/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> startSession(@PathVariable String agentId,
                                     @RequestBody String body,
                                     ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        long requestStart = System.currentTimeMillis();
        // Inject working_dir into the request body (override any client-supplied value)
        String workingDir = agentConfigService.getUserAgentDir(userId, agentId)
                .toAbsolutePath().normalize().toString();
        String modifiedBody;
        try {
            java.util.Map<String, Object> bodyMap = MAPPER.readValue(body,
                    new TypeReference<java.util.Map<String, Object>>() {});
            bodyMap.put("working_dir", workingDir);
            modifiedBody = MAPPER.writeValueAsString(bodyMap);
        } catch (Exception e) {
            modifiedBody = "{\"working_dir\":\"" + workingDir.replace("\\", "\\\\")
                    .replace("\"", "\\\"") + "\"}";
        }
        String finalBody = modifiedBody;
        boolean resident = agentConfigService.isResidentInstance(agentId, userId);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-START] begin agentId={} userId={} resident={} bodyLen={}",
                agentId, userId, resident, body.length()));
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> {
                    long afterInstanceMs = System.currentTimeMillis() - requestStart;
                    GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-START] instance resolved agentId={} userId={} resident={} port={} pid={} resolveMs={}",
                            agentId, userId, resident, instance.getPort(), instance.getPid(), afterInstanceMs));
                    long startCallStart = System.currentTimeMillis();
                    return goosedProxy.fetchJson(
                        instance.getPort(), HttpMethod.POST, "/agent/start", finalBody, 120, instance.getSecretKey())
                        .flatMap(startResponse -> {
                            long startCallMs = System.currentTimeMillis() - startCallStart;
                            // Follow goosed canonical flow: start → resume(load_model_and_extensions=true)
                            // Extensions must be loaded before the session is returned to the client.
                            // This matches Node.js legacy and Goose Desktop behavior.
                            String sessionId = extractSessionId(startResponse);
                            String resumeBody = "{\"session_id\":\"" + sessionId
                                    + "\",\"load_model_and_extensions\":true}";
                            GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-START] goosed start complete agentId={} userId={} sessionId={} port={} startCallMs={}",
                                    agentId, userId, sessionId, instance.getPort(), startCallMs));
                            long resumeStart = System.currentTimeMillis();
                            return goosedProxy.fetchJson(
                                    instance.getPort(), HttpMethod.POST, "/agent/resume", resumeBody, 120, instance.getSecretKey())
                                    .doOnNext(r -> {
                                        long resumeMs = System.currentTimeMillis() - resumeStart;
                                        instance.markSessionResumed(sessionId);
                                        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-START] session ready agentId={} userId={} sessionId={} resident={} port={} resumeMs={} totalMs={}",
                                                agentId, userId, sessionId, resident, instance.getPort(), resumeMs,
                                                System.currentTimeMillis() - requestStart));
                                    })
                                    .thenReturn(startResponse);
                        });
                });
    }

    private String extractSessionId(String startResponse) {
        try {
            Map<String, Object> map = MAPPER.readValue(startResponse,
                    new TypeReference<Map<String, Object>>() {});
            Object id = map.get("id");
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse session ID from start response", e);
        }
    }

    @GetMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> listAllSessions(ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-LIST] begin userId={}", userId));
        return Flux.fromIterable(instanceManager.getAllInstances())
                .filter(inst -> inst.getUserId().equals(userId)
                        || GatewayConstants.SYSTEM_USER.equals(inst.getUserId()))
                .filter(inst -> inst.getStatus() == ManagedInstance.Status.RUNNING)
                .flatMap(inst -> sessionService.getSessionsFromInstance(inst)
                        .map(json -> extractSessionsArray(json, inst.getAgentId())))
                .collectList()
                .map(lists -> {
                    List<String> allSessions = new ArrayList<>();
                    for (List<String> batch : lists) {
                        allSessions.addAll(batch);
                    }
                    GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-LIST] complete userId={} sessions={}", userId, allSessions.size()));
                    return "{\"sessions\":[" + String.join(",", allSessions) + "]}";
                });
    }

    /**
     * Parse goosed response and extract individual session JSON strings,
     * injecting agentId into each.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractSessionsArray(String json, String agentId) {
        List<String> result = new ArrayList<>();
        try {
            Map<String, Object> wrapper = MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
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
        } catch (Exception e) {
            // If parsing fails, try treating as a raw array
            try {
                List<Map<String, Object>> sessions = MAPPER.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> s : sessions) {
                    Map<String, Object> mutable = new java.util.LinkedHashMap<>(s);
                    mutable.put("agentId", agentId);
                    result.add(MAPPER.writeValueAsString(mutable));
                }
            } catch (Exception e2) {
                log.warn("Failed to parse sessions from instance: {}", e2.getMessage());
            }
        }
        return result;
    }

    @GetMapping("/agents/{agentId}/sessions")
    public Mono<Void> listAgentSessions(@PathVariable String agentId,
                                         ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxy(
                        exchange.getRequest(), exchange.getResponse(),
                        instance.getPort(), "/sessions", instance.getSecretKey()));
    }

    @GetMapping(value = "/agents/{agentId}/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getSession(@PathVariable String agentId,
                                    @PathVariable String sessionId,
                                    ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-GET] begin agentId={} userId={} sessionId={}", agentId, userId, sessionId));
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.fetchJson(instance.getPort(), "/sessions/" + sessionId, instance.getSecretKey()))
                .map(json -> injectAgentId(json, agentId))
                .doOnSuccess(json -> GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-GET] complete agentId={} userId={} sessionId={}",
                        agentId, userId, sessionId)))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
                    }
                    return Mono.error(e);
                });
    }

    /**
     * Global session detail: GET /sessions/{sessionId}?agentId=X
     */
    @GetMapping(value = "/sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getSessionGlobal(@PathVariable String sessionId,
                                          @RequestParam String agentId,
                                          ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-GET] begin agentId={} userId={} sessionId={} scope=global", agentId, userId, sessionId));
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.fetchJson(instance.getPort(), "/sessions/" + sessionId, instance.getSecretKey()))
                .map(json -> injectAgentId(json, agentId))
                .doOnSuccess(json -> GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-GET] complete agentId={} userId={} sessionId={} scope=global",
                        agentId, userId, sessionId)))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
                    }
                    return Mono.error(e);
                });
    }

    @DeleteMapping("/agents/{agentId}/sessions/{sessionId}")
    public Mono<Void> deleteSession(@PathVariable String agentId,
                                     @PathVariable String sessionId,
                                     ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-DELETE] begin agentId={} userId={} sessionId={}", agentId, userId, sessionId));
        Mono.fromRunnable(() -> cleanupUploads(userId, agentId, sessionId))
                .subscribeOn(Schedulers.boundedElastic()).subscribe();
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxy(
                        exchange.getRequest(), exchange.getResponse(),
                        instance.getPort(), "/sessions/" + sessionId, instance.getSecretKey()))
                .doOnSuccess(ignored -> GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-DELETE] complete agentId={} userId={} sessionId={}",
                        agentId, userId, sessionId)));
    }

    /**
     * Global session delete: DELETE /sessions/{sessionId}?agentId=X
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Mono<Void> deleteSessionGlobal(@PathVariable String sessionId,
                                           @RequestParam String agentId,
                                           ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-DELETE] begin agentId={} userId={} sessionId={} scope=global", agentId, userId, sessionId));
        Mono.fromRunnable(() -> cleanupUploads(userId, agentId, sessionId))
                .subscribeOn(Schedulers.boundedElastic()).subscribe();
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxy(
                        exchange.getRequest(), exchange.getResponse(),
                        instance.getPort(), "/sessions/" + sessionId, instance.getSecretKey()))
                .doOnSuccess(ignored -> GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-DELETE] complete agentId={} userId={} sessionId={} scope=global",
                        agentId, userId, sessionId)));
    }

    /**
     * Inject agentId into a session JSON response.
     */
    private String injectAgentId(String json, String agentId) {
        try {
            java.util.Map<String, Object> map = MAPPER.readValue(json,
                    new TypeReference<java.util.Map<String, Object>>() {});
            normalizeConversationOrder(map);
            map.put("agentId", agentId);
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            // If parsing fails, just return original
            return json;
        }
    }

    private static Long coerceEpochSeconds(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long raw = ((Number) value).longValue();
            if (raw <= 0) {
                return null;
            }
            if (raw > 1_000_000_000_000L) {
                return raw / 1000;
            }
            if (raw > 1_000_000_000L) {
                return raw;
            }
            return raw;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                if (s.matches("^\\d+$")) {
                    return coerceEpochSeconds(Long.parseLong(s));
                }
            } catch (Exception ignored) {
            }
            try {
                return coerceEpochSeconds(Instant.parse(s).toEpochMilli());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void normalizeConversationOrder(java.util.Map<String, Object> sessionMap) {
        Object convObj = sessionMap.get("conversation");
        if (!(convObj instanceof List)) {
            return;
        }
        List<?> conv = (List<?>) convObj;
        if (conv.size() < 2) {
            return;
        }

        List<java.util.Map<String, Object>> convMaps = new ArrayList<>(conv.size());
        for (Object item : conv) {
            if (!(item instanceof java.util.Map)) {
                return;
            }
            convMaps.add((java.util.Map<String, Object>) item);
        }

        int createdCount = 0;
        for (java.util.Map<String, Object> msg : convMaps) {
            Long created = coerceEpochSeconds(msg.get("created"));
            if (created != null) {
                createdCount += 1;
            }
        }

        if (createdCount >= Math.max(2, (int) Math.floor(convMaps.size() * 0.6))) {
            class Item {
                final int index;
                final java.util.Map<String, Object> msg;
                final Long created;
                final String role;

                Item(int index, java.util.Map<String, Object> msg) {
                    this.index = index;
                    this.msg = msg;
                    this.created = coerceEpochSeconds(msg.get("created"));
                    Object r = msg.get("role");
                    this.role = r instanceof String ? (String) r : "";
                }
            }

            List<Item> items = new ArrayList<>(convMaps.size());
            for (int i = 0; i < convMaps.size(); i++) {
                items.add(new Item(i, convMaps.get(i)));
            }

            items.sort((a, b) -> {
                if (a.created != null && b.created != null) {
                    int cmp = Long.compare(a.created, b.created);
                    if (cmp != 0) {
                        return cmp;
                    }
                    int roleA = "user".equals(a.role) ? 0 : 1;
                    int roleB = "user".equals(b.role) ? 0 : 1;
                    if (roleA != roleB) {
                        return Integer.compare(roleA, roleB);
                    }
                }
                return Integer.compare(a.index, b.index);
            });

            List<java.util.Map<String, Object>> sorted = new ArrayList<>(convMaps.size());
            for (Item item : items) {
                sorted.add(item.msg);
            }
            sessionMap.put("conversation", sorted);
            return;
        }

        boolean hasUser = false;
        boolean hasAssistant = false;
        int inversionCount = 0;
        for (int i = 0; i < convMaps.size(); i++) {
            Object roleObj = convMaps.get(i).get("role");
            String role = roleObj instanceof String ? (String) roleObj : "";
            if ("user".equals(role)) {
                hasUser = true;
            } else if ("assistant".equals(role)) {
                hasAssistant = true;
            }
            if (i + 1 < convMaps.size()) {
                Object nextRoleObj = convMaps.get(i + 1).get("role");
                String nextRole = nextRoleObj instanceof String ? (String) nextRoleObj : "";
                if ("assistant".equals(role) && "user".equals(nextRole)) {
                    inversionCount += 1;
                }
            }
        }

        if (hasUser && hasAssistant && inversionCount >= Math.floor((convMaps.size() - 1) / 2.0)) {
            java.util.Collections.reverse(convMaps);
            sessionMap.put("conversation", convMaps);
        }
    }

    /**
     * Clean up uploaded files for a deleted session.
     */
    private void cleanupUploads(String userId, String agentId, String sessionId) {
        try {
            Path uploadsDir = agentConfigService.getUserAgentDir(userId, agentId)
                    .resolve("uploads").resolve(sessionId);
            if (Files.isDirectory(uploadsDir)) {
                FileUtil.deleteRecursively(uploadsDir);
            }
        } catch (Exception e) {
            log.warn("Failed to clean up uploads for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Rename session: PUT /agents/{agentId}/sessions/{sessionId}/name
     */
    @PutMapping("/agents/{agentId}/sessions/{sessionId}/name")
    public Mono<Void> renameSession(@PathVariable String agentId,
                                     @PathVariable String sessionId,
                                     @RequestBody String body,
                                     ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        String requestId = exchange.getAttribute(RequestContextFilter.REQUEST_ID_ATTR);
        GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-RENAME] begin agentId={} userId={} sessionId={} bodyLen={}",
                agentId, userId, sessionId, body.length()));
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxyWithBody(
                        exchange.getResponse(), instance.getPort(),
                        "/sessions/" + sessionId + "/name",
                        HttpMethod.PUT, body, instance.getSecretKey()))
                .doOnSuccess(ignored -> GatewayLogContext.run(requestId, userId, () -> log.info("[SESSION-RENAME] complete agentId={} userId={} sessionId={}",
                        agentId, userId, sessionId)));
    }
}
