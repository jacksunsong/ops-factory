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

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.nio.file.Path;

/**
 * Extended E2E tests for SessionController covering previously missing endpoints:
 * GET /sessions/{sessionId}?agentId=X (global session get)
 * DELETE /sessions/{sessionId}?agentId=X (global session delete)
 * PUT /agents/{agentId}/sessions/{sessionId}/name (rename session)
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class SessionEndpointExtendedE2ETest extends BaseE2ETest {
    private ManagedInstance runningInstance;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        runningInstance = new ManagedInstance("test-agent", "alice", 9999, 12345L, null, "test-secret");
        runningInstance.setStatus(ManagedInstance.Status.RUNNING);
        when(agentConfigService.getUserAgentDir(any(String.class), any(String.class)))
            .thenAnswer(inv -> Path.of("/tmp/test-users")
                .resolve(inv.getArgument(0, String.class))
                .resolve("agents")
                .resolve(inv.getArgument(1, String.class)));
    }

    /**
     * Returns the session global authenticated returns session with agent id.
     */
    @Test
    public void getSessionGlobal_authenticated_returnsSessionWithAgentId() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.fetchJson(eq(9999), eq("/sessions/session-abc"), anyString()))
            .thenReturn(Mono.just("{\"id\":\"session-abc\",\"conversation\":[]}"));

        webClient.get()
            .uri("/gateway/sessions/session-abc?agentId=test-agent")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo("session-abc")
            .jsonPath("$.agentId")
            .isEqualTo("test-agent");
    }

    /**
     * Returns the session global unauthenticated returns401.
     */
    @Test
    public void getSessionGlobal_unauthenticated_returns401() {
        webClient.get()
            .uri("/gateway/sessions/session-abc?agentId=test-agent")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Executes the delete session global authenticated removes owner and proxies operation.
     */
    @Test
    public void deleteSessionGlobal_authenticated_removesOwnerAndProxies() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.proxy(any(), any(), eq(9999), eq("/sessions/session-xyz"), any())).thenReturn(Mono.empty());

        webClient.delete()
            .uri("/gateway/sessions/session-xyz?agentId=test-agent")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxy(any(), any(), eq(9999), eq("/sessions/session-xyz"), any());
    }

    /**
     * Executes the delete session global unauthenticated returns401 operation.
     */
    @Test
    public void deleteSessionGlobal_unauthenticated_returns401() {
        webClient.delete()
            .uri("/gateway/sessions/session-xyz?agentId=test-agent")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Executes the cleanup empty session empty user session deletes session operation.
     */
    @Test
    public void cleanupEmptySession_emptyUserSession_deletesSession() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.fetchJson(eq(9999), eq("/sessions/session-empty"), anyString())).thenReturn(Mono
            .just("{\"id\":\"session-empty\",\"session_type\":\"user\",\"message_count\"" + ":0,\"conversation\":[]}"));
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.DELETE), eq("/sessions/session-empty"), eq(null), anyInt(),
            anyString())).thenReturn(Mono.empty());

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-empty/cleanup-empty")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.deleted")
            .isEqualTo(true)
            .jsonPath("$.reason")
            .isEqualTo("empty_session_deleted");

        verify(goosedProxy).fetchJson(eq(9999), eq(HttpMethod.DELETE), eq("/sessions/session-empty"), eq(null),
            anyInt(), anyString());
    }

    /**
     * Executes the cleanup empty session session with messages skips delete operation.
     */
    @Test
    public void cleanupEmptySession_sessionWithMessages_skipsDelete() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.fetchJson(eq(9999), eq("/sessions/session-used"), anyString()))
            .thenReturn(Mono.just("{\"id\":\"session-used\",\"session_type\":\"user\",\"message_count\":2}"));

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-used/cleanup-empty")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.deleted")
            .isEqualTo(false)
            .jsonPath("$.reason")
            .isEqualTo("has_messages");

        verify(goosedProxy, never()).fetchJson(eq(9999), eq(HttpMethod.DELETE), eq("/sessions/session-used"), eq(null),
            anyInt(), anyString());
    }

    /**
     * Executes the cleanup empty session non empty conversation with zero message count skips delete operation.
     */
    @Test
    public void cleanupEmptySession_nonEmptyConvZeroMsgCount_skipsDelete() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.fetchJson(eq(9999), eq("/sessions/session-conversation"), anyString()))
            .thenReturn(Mono.just("{\"id\":\"session-conversation\",\"session_type\":\"user\","
                + "\"message_count\":0,\"conversation\":[{\"role\":\"user\"}]}"));

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-conversation/cleanup-empty")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.deleted")
            .isEqualTo(false)
            .jsonPath("$.reason")
            .isEqualTo("has_conversation");

        verify(goosedProxy, never()).fetchJson(eq(9999), eq(HttpMethod.DELETE), eq("/sessions/session-conversation"),
            eq(null), anyInt(), anyString());
    }

    /**
     * Executes the cleanup empty session scheduled session skips delete operation.
     */
    @Test
    public void cleanupEmptySession_scheduledSession_skipsDelete() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.fetchJson(eq(9999), eq("/sessions/session-scheduled"), anyString()))
            .thenReturn(Mono.just("{\"id\":\"session-scheduled\",\"session_type\""
                + ":\"scheduled\",\"message_count\":0,\"conversation\":[]}"));

        webClient.post()
            .uri("/gateway/agents/test-agent/sessions/session-scheduled/cleanup-empty")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.deleted")
            .isEqualTo(false)
            .jsonPath("$.reason")
            .isEqualTo("not_user_session");

        verify(goosedProxy, never()).fetchJson(eq(9999), eq(HttpMethod.DELETE), eq("/sessions/session-scheduled"),
            eq(null), anyInt(), anyString());
    }

    /**
     * Executes the rename session authenticated proxies to goosed operation.
     */
    @Test
    public void renameSession_authenticated_proxiesToGoosed() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.proxyWithBody(any(), eq(9999), eq("/sessions/session-123/name"), eq(HttpMethod.PUT),
            anyString(), anyString())).thenReturn(Mono.empty());

        webClient.put()
            .uri("/gateway/agents/test-agent/sessions/session-123/name")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"My Chat\"}")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxyWithBody(any(), eq(9999), eq("/sessions/session-123/name"), eq(HttpMethod.PUT),
            anyString(), anyString());
    }

    /**
     * Executes the rename session unauthenticated returns401 operation.
     */
    @Test
    public void renameSession_unauthenticated_returns401() {
        webClient.put()
            .uri("/gateway/agents/test-agent/sessions/session-123/name")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"test\"}")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Returns the session not found from goosed returns404.
     */
    @Test
    public void getSession_notFoundFromGoosed_returns404() {
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(runningInstance));
        when(goosedProxy.fetchJson(eq(9999), eq("/sessions/nonexistent"), anyString()))
            .thenReturn(Mono.error(org.springframework.web.reactive.function.client.WebClientResponseException
                .create(404, "Not Found", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null)));

        webClient.get()
            .uri("/gateway/agents/test-agent/sessions/nonexistent")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isNotFound();
    }
}
