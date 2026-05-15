/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.hook.HookContext;
import com.huawei.opsfactory.gateway.hook.HookPipeline;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.FileService;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Reply Controller Real Proxy.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class ReplyControllerRealProxyTest {

    /**
     * Executes the session reply real goosed400 returns gateway error envelope operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sessionReply_realGoosed400ReturnsGatewayErrorEnvelope() throws Exception {
        DisposableServer server =
            HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(
                    routes -> routes
                        .post("/agent/resume",
                            (request,
                                response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono
                                        .just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                        .post("/sessions/session-123/reply",
                            (request, response) -> response.status(400)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .sendString(Mono.just("Session already has an active request. Cancel " + "it first."))))
                .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
            when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
            when(agentConfigService.getUserAgentDir("alice", "test-agent")).thenReturn(Path.of("."));
            when(fileService.listCapsuleRelevantFiles(any())).thenReturn(Collections.emptyList());

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            WebTestClient client = WebTestClient.bindToController(controller).webFilter((exchange, chain) -> {
                exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");
                return chain.filter(exchange);
            }).build();

            client.post()
                .uri("/gateway/agents/test-agent/sessions/session-123/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":"
                    + "{\"role\":\"user\",\"created\":1776928807,\"content\":[{\"type\":\"text\",\"text\":"
                    + "\"hello\"}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("Error")
                .jsonPath("$.layer")
                .isEqualTo("goosed")
                .jsonPath("$.code")
                .isEqualTo("goosed_active_request_conflict")
                .jsonPath("$.message")
                .isEqualTo("Session already has an active request. " + "Cancel it first.")
                .jsonPath("$.retryable")
                .isEqualTo(true)
                .jsonPath("$.suggested_actions[0]")
                .isEqualTo("wait")
                .jsonPath("$.suggested_actions[1]")
                .isEqualTo("cancel")
                .jsonPath("$.suggested_actions[2]")
                .isEqualTo("retry")
                .jsonPath("$.request_id")
                .isEqualTo("00000000-0000-0000-0000-000000000001")
                .jsonPath("$.session_id")
                .isEqualTo("session-123")
                .jsonPath("$.agent_id")
                .isEqualTo("test-agent")
                .jsonPath("$.upstream_status")
                .isEqualTo(400);
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Executes the session events real goosed404 returns gateway error envelope operation.
     */
    @Test
    public void sessionEvents_realGoosed404ReturnsGatewayErrorEnvelope() {
        DisposableServer server =
            HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(
                    routes -> routes
                        .post("/agent/resume",
                            (request,
                                response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .sendString(Mono
                                        .just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                        .get("/sessions/session-123/events",
                            (request, response) -> response.status(404)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .sendString(Mono.just("session not found"))))
                .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            WebTestClient client = WebTestClient.bindToController(controller).webFilter((exchange, chain) -> {
                exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");
                return chain.filter(exchange);
            }).build();

            client.get()
                .uri("/gateway/agents/test-agent/sessions/session-123/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("Error")
                .jsonPath("$.layer")
                .isEqualTo("goosed")
                .jsonPath("$.code")
                .isEqualTo("goosed_error")
                .jsonPath("$.message")
                .isEqualTo("session not found")
                .jsonPath("$.session_id")
                .isEqualTo("session-123")
                .jsonPath("$.agent_id")
                .isEqualTo("test-agent")
                .jsonPath("$.upstream_status")
                .isEqualTo(404);
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Executes the session events active requests drained emits output files after original event operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sessionEvents_drainedActiveReqEmitsOutputFilesAfterEvent() throws Exception {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes
                .post("/agent/resume",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                .post("/sessions/session-123/reply",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"request_id\":" + "\"00000000-0000-0000-0000-000000000001\"}")))
                .get("/sessions/session-123/events",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .sendString(Mono.just("data: {\"type\":\"ActiveRequests\"," + "\"request_ids\":[]}\n\n"))))
            .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            List<Map<String, Object>> beforeFiles = Collections.emptyList();
            List<Map<String, Object>> afterFiles =
                List.of(Map.of("path", "goose-intro.md", "name", "goose-intro.md", "type", "md", "rootId", "workingDir",
                    "displayPath", "goose-intro.md", "size", 16, "modifiedAt", "2026-04-25T00:00:00Z"));
            List<Map<String, String>> changedFiles = List.of(Map.of("path", "goose-intro.md", "name", "goose-intro.md",
                "ext", "md", "rootId", "workingDir", "displayPath", "goose-intro.md"));

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
            when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
            when(agentConfigService.getUserAgentDir("alice", "test-agent")).thenReturn(Path.of("."));
            when(fileService.listCapsuleRelevantFiles(any())).thenReturn(beforeFiles, afterFiles);
            when(fileService.diffFiles(anyList(), anyList())).thenReturn(changedFiles);

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            WebTestClient client = WebTestClient.bindToController(controller).webFilter((exchange, chain) -> {
                exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");
                return chain.filter(exchange);
            }).build();

            client.post()
                .uri("/gateway/agents/test-agent/sessions/session-123/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":"
                    + "{\"role\":\"user\",\"created\":1776928807,\"content\":[{\"type\":\"text\",\"text\":"
                    + "\"create a file\"}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}")
                .exchange()
                .expectStatus()
                .isOk();

            client.get()
                .uri("/gateway/agents/test-agent/sessions/session-123/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> {
                    org.junit.Assert.assertTrue(body.contains("\"type\":\"OutputFiles\""));
                    org.junit.Assert.assertTrue(body.contains("\"sessionId\":\"session-123\""));
                    org.junit.Assert
                        .assertTrue(body.contains("\"chat_request_id\":" + "\"00000000-0000-0000-0000-000000000001\""));
                    org.junit.Assert.assertTrue(body.contains("\"displayPath\":\"goose-intro.md\""));
                    org.junit.Assert.assertTrue(
                        body.indexOf("\"type\":\"ActiveRequests\"") < body.indexOf("\"type\":\"OutputFiles\""));
                });
        } finally {
            server.disposeNow();
        }
    }

    /**
     * Executes the session reply invalid json body still returns gateway error envelope operation.
     */
    @Test
    public void sessionReply_invalidJsonBodyStillReturnsGatewayErrorEnvelope() {
        InstanceManager instanceManager = mock(InstanceManager.class);
        HookPipeline hookPipeline = mock(HookPipeline.class);
        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        FileService fileService = mock(FileService.class);

        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
        when(instanceManager.getOrSpawn("test-agent", "alice"))
            .thenReturn(Mono.error(new IllegalStateException("spawn failed")));

        GatewayProperties properties = new GatewayProperties();
        properties.setGooseTls(false);
        GoosedProxy goosedProxy = new GoosedProxy(properties);
        ReplyController controller =
            new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
        WebTestClient client = WebTestClient.bindToController(controller).webFilter((exchange, chain) -> {
            exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");
            return chain.filter(exchange);
        }).build();

        client.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("not-json")
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("Error")
            .jsonPath("$.code")
            .isEqualTo("gateway_submit_failed")
            .jsonPath("$.session_id")
            .isEqualTo("session-123")
            .jsonPath("$.agent_id")
            .isEqualTo("test-agent");
    }

    /**
     * Executes the session reply snapshot io failure still proxies request operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sessionReply_snapshotIoFailureStillProxiesRequest() throws Exception {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes
                .post("/agent/resume",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"session\":{\"id\":\"session-123\"}," + "\"extension_results\":[]}")))
                .post("/sessions/session-123/reply",
                    (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .sendString(Mono.just("{\"ok\":true}"))))
            .bindNow();

        try {
            InstanceManager instanceManager = mock(InstanceManager.class);
            HookPipeline hookPipeline = mock(HookPipeline.class);
            AgentConfigService agentConfigService = mock(AgentConfigService.class);
            FileService fileService = mock(FileService.class);
            ManagedInstance instance =
                new ManagedInstance("test-agent", "alice", server.port(), 12345L, null, "test-secret");
            instance.setStatus(ManagedInstance.Status.RUNNING);

            when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
            when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
            when(agentConfigService.getUserAgentDir("alice", "test-agent")).thenReturn(Path.of("."));
            when(fileService.listCapsuleRelevantFiles(any()))
                .thenThrow(new IllegalStateException("disk busy"));

            GatewayProperties properties = new GatewayProperties();
            properties.setGooseTls(false);
            GoosedProxy goosedProxy = new GoosedProxy(properties);
            ReplyController controller =
                new ReplyController(instanceManager, goosedProxy, hookPipeline, agentConfigService, fileService);
            WebTestClient client = WebTestClient.bindToController(controller).webFilter((exchange, chain) -> {
                exchange.getAttributes().put(UserContextFilter.USER_ID_ATTR, "alice");
                return chain.filter(exchange);
            }).build();

            client.post()
                .uri("/gateway/agents/test-agent/sessions/session-123/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":{"
                    + "\"role\":\"user\",\"created\":1776928807}}")
                .exchange()
                .expectStatus()
                .isOk();
        } finally {
            server.disposeNow();
        }
    }
}
