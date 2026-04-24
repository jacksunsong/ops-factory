package com.huawei.opsfactory.gateway.e2e;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.hook.HookContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/** E2E tests for ReplyController endpoints. */
public class ReplyEndpointE2ETest extends BaseE2ETest {

    private ManagedInstance mockInstance;

    @Before
    public void setUp() {
        mockInstance = new ManagedInstance("test-agent", "alice", 9999, 12345L, null, "test-secret");
        mockInstance.setStatus(ManagedInstance.Status.RUNNING);
        // HookPipeline passes body through unchanged
        when(hookPipeline.executeRequest(any(HookContext.class)))
                .thenAnswer(inv -> Mono.just(((HookContext) inv.getArgument(0)).getBody()));
    }

    // ====================== Session event transport ======================

    @Test
    public void sessionReply_authenticatedUser_proxiesToGoosedSessionReply() throws Exception {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
                .thenReturn(Mono.just(mockInstance));
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just("{\"session\":{\"id\":\"session-123\"},\"extension_results\":[]}"));
        when(goosedProxy.proxyWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
                eq(HttpMethod.POST), anyString(), eq("test-secret")))
                .thenReturn(Mono.empty());

        long before = System.currentTimeMillis() / 1000;
        webClient.post().uri("/gateway/agents/test-agent/sessions/session-123/reply")
                .header(HEADER_SECRET_KEY, SECRET_KEY)
                .header(HEADER_USER_ID, "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":{\"id\":\"u1\",\"role\":\"user\",\"created\":9999999999,\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}")
                .exchange()
                .expectStatus().isOk();
        long after = System.currentTimeMillis() / 1000;

        verify(goosedProxy).fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"),
                eq("{\"session_id\":\"session-123\",\"load_model_and_extensions\":true}"), anyInt(), anyString());

        ArgumentCaptor<String> bodyCaptor = forClass(String.class);
        verify(goosedProxy).proxyWithBody(any(), eq(9999), eq("/sessions/session-123/reply"),
                eq(HttpMethod.POST), bodyCaptor.capture(), eq("test-secret"));
        com.fasterxml.jackson.databind.JsonNode relayed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(bodyCaptor.getValue());
        org.junit.Assert.assertEquals("00000000-0000-0000-0000-000000000001",
                relayed.path("request_id").asText());
        long normalizedCreated = relayed.path("user_message").path("created").asLong();
        org.junit.Assert.assertTrue(normalizedCreated >= before);
        org.junit.Assert.assertTrue(normalizedCreated <= after);
    }

    @Test
    public void sessionEvents_authenticatedUser_proxiesLastEventIdWithoutLegacyRelay() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
                .thenReturn(Mono.just(mockInstance));
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just("{\"session\":{\"id\":\"session-123\"},\"extension_results\":[]}"));
        when(goosedProxy.proxySessionEvents(any(), eq(9999), eq("/sessions/session-123/events"),
                eq("test-secret"), eq("42")))
                .thenReturn(Mono.empty());

        webClient.get().uri("/gateway/agents/test-agent/sessions/session-123/events")
                .header(HEADER_SECRET_KEY, SECRET_KEY)
                .header(HEADER_USER_ID, "alice")
                .header("Last-Event-ID", "42")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();

        verify(goosedProxy).fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"),
                eq("{\"session_id\":\"session-123\",\"load_model_and_extensions\":true}"), anyInt(), anyString());
        verify(goosedProxy).proxySessionEvents(any(), eq(9999), eq("/sessions/session-123/events"),
                eq("test-secret"), eq("42"));
    }

    @Test
    public void sessionCancel_authenticatedUser_proxiesToGoosedCancelOnly() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
                .thenReturn(Mono.just(mockInstance));
        when(goosedProxy.proxyWithBody(any(), eq(9999), eq("/sessions/session-123/cancel"),
                eq(HttpMethod.POST), anyString(), eq("test-secret")))
                .thenReturn(Mono.empty());

        webClient.post().uri("/gateway/agents/test-agent/sessions/session-123/cancel")
                .header(HEADER_SECRET_KEY, SECRET_KEY)
                .header(HEADER_USER_ID, "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"request_id\":\"00000000-0000-0000-0000-000000000001\"}")
                .exchange()
                .expectStatus().isOk();

        verify(goosedProxy).proxyWithBody(any(), eq(9999), eq("/sessions/session-123/cancel"),
                eq(HttpMethod.POST), eq("{\"request_id\":\"00000000-0000-0000-0000-000000000001\"}"),
                eq("test-secret"));
        verify(goosedProxy, never()).fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"),
                anyString(), anyInt(), anyString());
    }

    @Test
    public void sessionReply_getOrSpawnFailure_returnsStructuredError() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Agent temporarily unavailable")));

        webClient.post().uri("/gateway/agents/test-agent/sessions/session-123/reply")
                .header(HEADER_SECRET_KEY, SECRET_KEY)
                .header(HEADER_USER_ID, "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"request_id\":\"00000000-0000-0000-0000-000000000001\",\"user_message\":{\"role\":\"user\",\"created\":1776928807,\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.type").isEqualTo("Error")
                .jsonPath("$.layer").isEqualTo("gateway")
                .jsonPath("$.code").isEqualTo("gateway_goosed_unavailable")
                .jsonPath("$.retryable").isEqualTo(true)
                .jsonPath("$.request_id").isEqualTo("00000000-0000-0000-0000-000000000001")
                .jsonPath("$.session_id").isEqualTo("session-123")
                .jsonPath("$.agent_id").isEqualTo("test-agent");
    }

    // ====================== POST /agents/{agentId}/resume ======================

    @Test
    public void resume_authenticatedUser_proxiesToGoosed() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
                .thenReturn(Mono.just(mockInstance));
        when(goosedProxy.fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just("{\"session\":{\"id\":\"session-123\"},\"extension_results\":[]}"));

        webClient.post().uri("/gateway/agents/test-agent/resume")
                .header(HEADER_SECRET_KEY, SECRET_KEY)
                .header(HEADER_USER_ID, "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.session.id").isEqualTo("session-123");

        verify(goosedProxy).fetchJson(eq(9999), eq(HttpMethod.POST), eq("/agent/resume"), anyString(), anyInt(), anyString());
    }

    @Test
    public void resume_unauthenticated_returns401() {
        webClient.post().uri("/gateway/agents/test-agent/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ====================== POST /agents/{agentId}/restart ======================

    @Test
    public void restart_authenticatedUser_proxiesToGoosed() {
        when(instanceManager.getOrSpawn("test-agent", "alice"))
                .thenReturn(Mono.just(mockInstance));
        when(goosedProxy.proxyWithBody(any(), eq(9999), eq("/agent/restart"),
                eq(HttpMethod.POST), anyString(), anyString()))
                .thenReturn(Mono.empty());

        webClient.post().uri("/gateway/agents/test-agent/restart")
                .header(HEADER_SECRET_KEY, SECRET_KEY)
                .header(HEADER_USER_ID, "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }

}
