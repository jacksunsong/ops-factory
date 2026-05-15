/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.hook.HookContext;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;

/**
 * Test coverage for Reply Endpoint Performance E2 E.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class ReplyEndpointPerformanceE2ETest extends BaseE2ETest {
    private ManagedInstance mockInstance;

    /**
     * Sets the up.
     *
     * @throws Exception if the operation fails
     */
    @Before
    public void setUp() throws Exception {
        mockInstance = new ManagedInstance("test-agent", "alice", 9999, 12345L, null, "test-secret");
        mockInstance.setStatus(ManagedInstance.Status.RUNNING);
        when(agentConfigService.getUserAgentDir(anyString(), anyString()))
            .thenReturn(Paths.get(System.getProperty("java.io.tmpdir")));
        when(fileService.listFiles(any())).thenReturn(Collections.emptyList());
        when(fileService.diffFiles(any(), any())).thenReturn(Collections.emptyList());
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(),
            anyString())).thenReturn(Mono.just("{\"ok\":true}"));
    }

    /**
     * Executes the session reply response latency includes hook and spawn delay operation.
     */
    @Test
    public void sessionReply_responseLatencyIncludesHookAndSpawnDelay() {
        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(invocation -> Mono.delay(Duration.ofMillis(80))
                .thenReturn(((HookContext) invocation.getArgument(0)).getBody()));
        when(instanceManager.getOrSpawn("test-agent", "alice"))
            .thenReturn(Mono.delay(Duration.ofMillis(90)).thenReturn(mockInstance));
        when(goosedProxy.proxySessionCommandWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
            eq(HttpMethod.POST), anyString(), eq("test-secret"))).thenReturn(Mono.empty());

        long elapsedMs = executeSessionReplyAndMeasure(replyBody("00000000-0000-4000-8000-000000000001"));

        assertTrue("reply latency should include hook + spawn delays, actual=" + elapsedMs, elapsedMs >= 140);
        assertTrue("reply latency should stay within a reasonable bound, actual=" + elapsedMs, elapsedMs < 5000);
    }

    /**
     * Executes the session reply response latency includes resume delay operation.
     */
    @Test
    public void sessionReply_responseLatencyIncludesResumeDelay() {
        String body = replyBody("00000000-0000-4000-8000-000000000002");

        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(invocation -> Mono.just(((HookContext) invocation.getArgument(0)).getBody()));
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(mockInstance));
        when(goosedProxy.fetchJson(eq(9999), eq(org.springframework.http.HttpMethod.POST), eq("/agent/resume"),
            anyString(), anyInt(), anyString()))
            .thenReturn(Mono.delay(Duration.ofMillis(120)).thenReturn("{\"ok\":true}"));
        when(goosedProxy.proxySessionCommandWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
            eq(HttpMethod.POST), anyString(), eq("test-secret"))).thenReturn(Mono.empty());

        long elapsedMs = executeSessionReplyAndMeasure(body);

        verify(goosedProxy).fetchJson(eq(9999), eq(org.springframework.http.HttpMethod.POST), eq("/agent/resume"),
            anyString(), anyInt(), anyString());
        assertTrue("reply latency should include resume delay, actual=" + elapsedMs, elapsedMs >= 100);
        assertTrue("reply latency should stay within a reasonable bound, actual=" + elapsedMs, elapsedMs < 5000);
    }

    /**
     * Executes the session reply response latency includes upstream completion delay operation.
     */
    @Test
    public void sessionReply_responseLatencyIncludesUpstreamCompletionDelay() {
        when(hookPipeline.executeRequest(any(HookContext.class)))
            .thenAnswer(invocation -> Mono.just(((HookContext) invocation.getArgument(0)).getBody()));
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(mockInstance));
        when(goosedProxy.proxySessionCommandWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
            eq(HttpMethod.POST), anyString(), eq("test-secret"))).thenReturn(Mono.delay(Duration.ofMillis(150)).then());

        long elapsedMs = executeSessionReplyAndMeasure(replyBody("00000000-0000-4000-8000-000000000003"));

        assertTrue("reply latency should include upstream first chunk delay, actual=" + elapsedMs, elapsedMs >= 130);
        assertTrue("reply latency should stay within a reasonable bound, actual=" + elapsedMs, elapsedMs < 5000);
    }

    private long executeSessionReplyAndMeasure(String body) {
        long startNs = System.nanoTime();
        String responseBody = webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-123/reply")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
        return elapsedMs;
    }

    private String replyBody(String requestId) {
        return "{\"request_id\":\"" + requestId + "\",\"user_message\":{\"role\":\"user\",\"created\":1,"
            + "\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],"
            + "\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}";
    }
}
