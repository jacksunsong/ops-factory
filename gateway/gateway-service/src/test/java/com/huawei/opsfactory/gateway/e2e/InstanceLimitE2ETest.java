/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.hook.HookContext;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * E2E tests verifying instance limit enforcement at the HTTP layer.
 * When InstanceManager.getOrSpawn throws IllegalStateException for
 * per-user or global limits, the session reply endpoint should return 5xx.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class InstanceLimitE2ETest extends BaseE2ETest {

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        // HookPipeline passes body through unchanged
        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
    }

    /**
     * Executes the session reply per user limit reached returns5xx operation.
     */
    @Test
    public void sessionReply_perUserLimitReached_returns5xx() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
            .thenReturn(Mono.error(new IllegalStateException("Per-user instance limit reached (5)")));

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"request_id\":\"req-1\",\"user_message\":{\"role\":\"user\",\"created\":1776928807,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"metadata\":{\"userVisible\":true,"
                + "\"agentVisible\":true}}}")
            .exchange()
            .expectStatus()
            .is5xxServerError();
    }

    /**
     * Executes the session reply global limit reached returns5xx operation.
     */
    @Test
    public void sessionReply_globalLimitReached_returns5xx() {
        when(instanceManager.getOrSpawn("test-agent", "bob"))
            .thenReturn(Mono.error(new IllegalStateException("Global instance limit reached (50)")));

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "bob")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"request_id\":\"req-1\",\"user_message\":{\"role\":\"user\",\"created\":1776928807,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"metadata\":{\"userVisible\":true,"
                + "\"agentVisible\":true}}}")
            .exchange()
            .expectStatus()
            .is5xxServerError();
    }

    /**
     * Executes the session reply normal spawn returns200 operation.
     */
    @Test
    public void sessionReply_normalSpawn_returns200() {
        ManagedInstance mockInstance = new ManagedInstance("test-agent", "alice", 9999, 12345L, null, "test-secret");
        mockInstance.setStatus(ManagedInstance.Status.RUNNING);

        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(mockInstance));
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(),
            anyString())).thenReturn(Mono.just("{\"session\":{\"id\":\"session-123\"},\"extension_results\":[]}"));
        when(goosedProxy.proxySessionCommandWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
            eq(HttpMethod.POST), anyString(), eq("test-secret"))).thenReturn(Mono.empty());

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"request_id\":\"req-1\",\"user_message\":{\"role\":\"user\",\"created\":1776928807,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"metadata\":{\"userVisible\":true,"
                + "\"agentVisible\":true}}}")
            .exchange()
            .expectStatus()
            .isOk();
    }

    /**
     * Executes the session reply unauthenticated returns401 operation.
     */
    @Test
    public void sessionReply_unauthenticated_returns401() {
        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"request_id\":\"req-1\",\"user_message\":{\"role\":\"user\",\"created\":1776928807,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"metadata\":{\"userVisible\":true,"
                + "\"agentVisible\":true}}}")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Executes the resume limit reached returns5xx operation.
     */
    @Test
    public void resume_limitReached_returns5xx() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
            .thenReturn(Mono.error(new IllegalStateException("Per-user instance limit reached (5)")));

        webClient.post()
            .uri("/gateway/agents/test-agent/resume")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus()
            .is5xxServerError();
    }
}
