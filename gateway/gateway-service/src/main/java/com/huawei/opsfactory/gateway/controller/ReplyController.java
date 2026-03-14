package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.hook.HookContext;
import com.huawei.opsfactory.gateway.hook.HookPipeline;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.proxy.SseRelayService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/agents/{agentId}")
public class ReplyController {

    private static final Logger log = LogManager.getLogger(ReplyController.class);

    private final InstanceManager instanceManager;
    private final SseRelayService sseRelayService;
    private final GoosedProxy goosedProxy;
    private final HookPipeline hookPipeline;

    public ReplyController(InstanceManager instanceManager,
                           SseRelayService sseRelayService,
                           GoosedProxy goosedProxy,
                           HookPipeline hookPipeline) {
        this.instanceManager = instanceManager;
        this.sseRelayService = sseRelayService;
        this.goosedProxy = goosedProxy;
        this.hookPipeline = hookPipeline;
    }

    /**
     * SSE streaming chat reply.
     * Runs request hooks (body limit, file attachment) then proxies to goosed.
     * If the session has not been resumed on this goosed instance (e.g. after force-recycle),
     * automatically calls /agent/resume to restore the provider and extensions first.
     */
    @PostMapping(value = {"/reply", "/agent/reply"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<DataBuffer> reply(@PathVariable String agentId,
                                   @RequestBody String body,
                                   ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        HookContext ctx = new HookContext(body, agentId, userId);
        log.debug("[REPLY] agentId={} userId={} bodyLen={}", agentId, userId, body.length());
        return hookPipeline.executeRequest(ctx)
                .flatMapMany(processedBody -> {
                    log.debug("[REPLY] hooks done, getting instance for {}:{}", agentId, userId);
                    return instanceManager.getOrSpawn(agentId, userId)
                            .flatMapMany(instance -> {
                                log.info("[REPLY] instance ready {}:{} port={} pid={} sessionResumed={}",
                                        agentId, userId, instance.getPort(), instance.getPid(),
                                        instance.isSessionResumed(extractSessionId(processedBody)));
                                instance.touch();
                                instanceManager.touchAllForUser(userId);
                                return ensureSessionResumed(instance, processedBody)
                                        .thenMany(sseRelayService.relay(instance.getPort(), "/reply",
                                                processedBody, agentId, userId));
                            });
                });
    }

    /**
     * Ensure that the session referenced in the reply body has been resumed on this
     * goosed instance (provider + extensions loaded). This is a no-op when the session
     * was already resumed (normal flow). After a force-recycle, the goosed process is
     * brand-new and needs an explicit /agent/resume call before it can handle /reply.
     */
    private Mono<Void> ensureSessionResumed(ManagedInstance instance, String body) {
        String sessionId = extractSessionId(body);
        if (sessionId == null || instance.isSessionResumed(sessionId)) {
            log.debug("[REPLY] session {} already resumed or null, skipping resume", sessionId);
            return Mono.empty();
        }
        long resumeStart = System.currentTimeMillis();
        log.info("[REPLY] session {} not yet resumed on instance {}:{} (port={}), calling /agent/resume",
                sessionId, instance.getAgentId(), instance.getUserId(), instance.getPort());
        String resumeBody = "{\"session_id\":\"" + sessionId + "\",\"load_model_and_extensions\":true}";
        return goosedProxy.fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", resumeBody, 120)
                .doOnNext(r -> {
                    long resumeMs = System.currentTimeMillis() - resumeStart;
                    instance.markSessionResumed(sessionId);
                    log.info("[REPLY] session {} resumed in {}ms on instance {}:{}", sessionId,
                            resumeMs, instance.getAgentId(), instance.getUserId());
                })
                .onErrorResume(e -> {
                    long resumeMs = System.currentTimeMillis() - resumeStart;
                    log.warn("[REPLY] session {} resume failed after {}ms on instance {}:{}: {} (proceeding anyway)",
                            sessionId, resumeMs, instance.getAgentId(), instance.getUserId(), e.getMessage());
                    instance.markSessionResumed(sessionId);
                    return Mono.empty();
                })
                .then();
    }

    private static String extractSessionId(String body) {
        int idx = body.indexOf("\"session_id\"");
        if (idx < 0) idx = body.indexOf("\"sessionId\"");
        if (idx < 0) return null;
        int colonIdx = body.indexOf(':', idx);
        if (colonIdx < 0) return null;
        int startQuote = body.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = body.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return body.substring(startQuote + 1, endQuote);
    }

    @PostMapping(value = {"/resume", "/agent/resume"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> resume(@PathVariable String agentId,
                               @RequestBody String body,
                               ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.fetchJson(
                        instance.getPort(), HttpMethod.POST, "/agent/resume", body, 120));
    }

    @PostMapping({"/restart", "/agent/restart"})
    public Mono<Void> restart(@PathVariable String agentId,
                               @RequestBody String body,
                               ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxyWithBody(
                        exchange.getResponse(), instance.getPort(), "/agent/restart",
                        HttpMethod.POST, body));
    }

    @PostMapping({"/stop", "/agent/stop"})
    public Mono<Void> stop(@PathVariable String agentId,
                            @RequestBody String body,
                            ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return instanceManager.getOrSpawn(agentId, userId)
                .flatMap(instance -> goosedProxy.proxyWithBody(
                        exchange.getResponse(), instance.getPort(), "/agent/stop",
                        HttpMethod.POST, body));
    }
}
