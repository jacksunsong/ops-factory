/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.hook.HookContext;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

/**
 * E2E tests verifying HookPipeline integration with ReplyController.
 * Tests that hook rejections (413, 403) are properly propagated to the client,
 * and that successful hooks allow the request through to goosed session reply.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class HookPipelineE2ETest extends BaseE2ETest {
    private ManagedInstance mockInstance;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        mockInstance = new ManagedInstance("test-agent", "alice", 9999, 12345L, null, "test-secret");
        mockInstance.setStatus(ManagedInstance.Status.RUNNING);
    }

    /**
     * Executes the session reply hook pass through relays to goosed operation.
     */
    @Test
    public void sessionReply_hookPassThrough_relaysToGoosed() {
        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
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
     * Executes the session reply hook rejects with payload too large returns413 operation.
     */
    @Test
    public void sessionReply_hookRejectsWithPayloadTooLarge_returns413() {
        when(hookPipeline.executeRequest(any(HookContext.class))).thenReturn(Mono.error(
            new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds maximum allowed size")));

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
            .isEqualTo(413);

        verify(goosedProxy, never()).proxySessionCommandWithBody(any(), anyInt(), anyString(), any(), anyString(),
            anyString());
    }

    /**
     * Executes the session reply hook rejects with forbidden returns403 operation.
     */
    @Test
    public void sessionReply_hookRejectsWithForbidden_returns403() {
        when(hookPipeline.executeRequest(any(HookContext.class))).thenReturn(
            Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "File path escapes allowed directory")));

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
            .isForbidden();

        verify(goosedProxy, never()).proxySessionCommandWithBody(any(), anyInt(), anyString(), any(), anyString(),
            anyString());
    }

    /**
     * Executes the session reply hook throws unexpected exception returns500 operation.
     */
    @Test
    public void sessionReply_hookThrowsUnexpectedException_returns500() {
        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenReturn(Mono.error(new RuntimeException("Unexpected hook failure")));

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

        verify(goosedProxy, never()).proxySessionCommandWithBody(any(), anyInt(), anyString(), any(), anyString(),
            anyString());
    }

    /**
     * Executes the session reply hook modifies body modified body reaches goosed operation.
     */
    @Test
    public void sessionReply_hookModifiesBody_modifiedBodyReachesGoosed() {
        // Hook transforms the body (e.g., injects file content)
        when(hookPipeline.executeRequest(any(HookContext.class))).thenReturn(Mono.just("{\"modified\":true}"));
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(mockInstance));
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(),
            anyString())).thenReturn(Mono.just("{\"session\":{\"id\":\"session-123\"},\"extension_results\":[]}"));
        when(goosedProxy.proxySessionCommandWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
            eq(HttpMethod.POST), eq("{\"modified\":true}"), eq("test-secret"))).thenReturn(Mono.empty());

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"original\":true}")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxySessionCommandWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
            eq(HttpMethod.POST), eq("{\"modified\":true}"), eq("test-secret"));
    }
}
