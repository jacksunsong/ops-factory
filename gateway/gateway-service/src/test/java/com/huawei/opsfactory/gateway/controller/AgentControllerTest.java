/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.AgentConfigService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Agent Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebFluxTest(AgentController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class AgentControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PrewarmService prewarmService;

    @MockBean
    private AgentConfigService agentConfigService;

    @MockBean
    private InstanceManager instanceManager;

    /**
     * Tests list agents.
     */
    @Test
    public void testListAgents() {
        when(agentConfigService.getRegistry()).thenReturn(
            List.of(new AgentRegistryEntry("agent1", "Agent One"), new AgentRegistryEntry("agent2", "Agent Two")));
        when(agentConfigService.loadAgentConfigYaml("agent1"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "openai", "GOOSE_MODEL", "gpt-4o"));
        when(agentConfigService.loadAgentConfigYaml("agent2"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "anthropic", "GOOSE_MODEL", "claude-3"));
        when(agentConfigService.listSkills("agent1")).thenReturn(List
            .of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming")));
        when(agentConfigService.listSkills("agent2")).thenReturn(Collections.emptyList());

        webTestClient.get()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agents[0].id")
            .isEqualTo("agent1")
            .jsonPath("$.agents[0].name")
            .isEqualTo("Agent One")
            .jsonPath("$.agents[0].sysOnly")
            .doesNotExist()
            .jsonPath("$.agents[0].provider")
            .isEqualTo("openai")
            .jsonPath("$.agents[0].skills.length()")
            .isEqualTo(1)
            .jsonPath("$.agents[0].skills[0].name")
            .isEqualTo("brainstorming")
            .jsonPath("$.agents[0].skills[0].description")
            .isEqualTo("Brainstorm ideas")
            .jsonPath("$.agents[1].id")
            .isEqualTo("agent2");
    }

    /**
     * Tests list agents empty.
     */
    @Test
    public void testListAgents_empty() {
        when(agentConfigService.getRegistry()).thenReturn(List.of());

        webTestClient.get()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agents.length()")
            .isEqualTo(0);
    }

    /**
     * Tests create agent as admin.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_asAdmin() throws Exception {
        Map<String, Object> agent = new HashMap<>();
        agent.put("id", "new-agent");
        agent.put("name", "New Agent");
        agent.put("provider", "openai");
        agent.put("model", "gpt-4o");
        when(agentConfigService.createAgent(eq("new-agent"), eq("New Agent"))).thenReturn(agent);

        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\": \"new-agent\", \"name\": \"New Agent\"}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.agent.id")
            .isEqualTo("new-agent");
    }

    /**
     * Tests create agent succeeds for any authenticated user.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_succeeds_forAnyUser() throws Exception {
        Map<String, Object> agent = new HashMap<>();
        agent.put("id", "new-agent");
        agent.put("name", "New Agent");
        agent.put("provider", "openai");
        agent.put("model", "gpt-4o");
        when(agentConfigService.createAgent(eq("new-agent"), eq("New Agent"))).thenReturn(agent);

        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\": \"new-agent\", \"name\": \"New Agent\"}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.agent.id")
            .isEqualTo("new-agent");
    }

    /**
     * Tests create agent missing id.
     */
    @Test
    public void testCreateAgent_missingId() {
        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\": \"New Agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Tests delete agent as admin.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testDeleteAgent_asAdmin() throws Exception {
        Mockito.doNothing().when(instanceManager).stopAllForAgent("agent1");
        Mockito.doNothing().when(agentConfigService).deleteAgent("agent1");

        webTestClient.delete()
            .uri("/gateway/agents/agent1")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests delete agent succeeds for any authenticated user.
     */
    @Test
    public void testDeleteAgent_succeeds_forAnyUser() {
        Mockito.doNothing().when(instanceManager).stopAllForAgent("agent1");
        Mockito.doNothing().when(agentConfigService).deleteAgent("agent1");

        webTestClient.delete()
            .uri("/gateway/agents/agent1")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests get skills as admin.
     */
    @Test
    public void testGetSkills_asAdmin() {
        when(agentConfigService.listSkills("agent1")).thenReturn(
            List.of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming"),
                Map.of("name", "analysis", "description", "Analyze data", "path", "skills/analysis")));

        webTestClient.get()
            .uri("/gateway/agents/agent1/skills")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skills[0].name")
            .isEqualTo("brainstorming")
            .jsonPath("$.skills[0].description")
            .isEqualTo("Brainstorm ideas")
            .jsonPath("$.skills[1].name")
            .isEqualTo("analysis")
            .jsonPath("$.skills[1].description")
            .isEqualTo("Analyze data");
    }

    /**
     * Tests get config as admin.
     */
    @Test
    public void testGetConfig_asAdmin() {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        when(agentConfigService.readAgentsMd("agent1")).thenReturn("# Agent One\n");
        Map<String, Object> config = new HashMap<>();
        config.put("GOOSE_PROVIDER", "anthropic");
        config.put("GOOSE_MODEL", "claude-3");
        when(agentConfigService.loadAgentConfigYaml("agent1")).thenReturn(config);
        when(agentConfigService.getAgentsDir()).thenReturn(java.nio.file.Path.of("/tmp/agents"));

        webTestClient.get()
            .uri("/gateway/agents/agent1/config")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agentsMd")
            .isEqualTo("# Agent One\n")
            .jsonPath("$.provider")
            .isEqualTo("anthropic")
            .jsonPath("$.model")
            .isEqualTo("claude-3");
    }

    /**
     * Tests update config as admin.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testUpdateConfig_asAdmin() throws Exception {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        Mockito.doNothing().when(agentConfigService).writeAgentsMd("agent1", "# Updated\n");

        webTestClient.put()
            .uri("/gateway/agents/agent1/config")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"agentsMd\": \"# Updated\\n\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests update config succeeds for any authenticated user.
     */
    @Test
    public void testUpdateConfig_succeeds_forAnyUser() {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        Mockito.doNothing().when(agentConfigService).writeAgentsMd("agent1", "# Updated\n");

        webTestClient.put()
            .uri("/gateway/agents/agent1/config")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"agentsMd\": \"# Updated\\n\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests create agent missing name.
     */
    @Test
    public void testCreateAgent_missingName() {
        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\": \"new-agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Tests create agent blank id.
     */
    @Test
    public void testCreateAgent_blankId() {
        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\": \"   \", \"name\": \"New Agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Tests create agent duplicate returns400.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_duplicateReturns400() throws Exception {
        when(agentConfigService.createAgent(eq("dup-agent"), eq("Dup Agent")))
            .thenThrow(new IllegalArgumentException("Agent already exists"));

        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\": \"dup-agent\", \"name\": \"Dup Agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Tests create agent io failure returns500.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testCreateAgent_ioFailureReturns500() throws Exception {
        when(agentConfigService.createAgent(eq("io-agent"), eq("IO Agent")))
            .thenThrow(new IllegalStateException("disk full"));

        webTestClient.post()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\": \"io-agent\", \"name\": \"IO Agent\"}")
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false)
            .jsonPath("$.error")
            .isEqualTo("Failed to create agent");
    }

    /**
     * Tests get skills succeeds for any authenticated user.
     */
    @Test
    public void testGetSkills_succeeds_forAnyUser() {
        when(agentConfigService.listSkills("agent1")).thenReturn(
            List.of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming"),
                Map.of("name", "analysis", "description", "Analyze data", "path", "skills/analysis")));

        webTestClient.get()
            .uri("/gateway/agents/agent1/skills")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skills[0].name")
            .isEqualTo("brainstorming")
            .jsonPath("$.skills[0].description")
            .isEqualTo("Brainstorm ideas")
            .jsonPath("$.skills[1].name")
            .isEqualTo("analysis")
            .jsonPath("$.skills[1].description")
            .isEqualTo("Analyze data");
    }

    /**
     * Tests get config succeeds for any authenticated user.
     */
    @Test
    public void testGetConfig_succeeds_forAnyUser() {
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        when(agentConfigService.readAgentsMd("agent1")).thenReturn("# Agent One\n");
        Map<String, Object> config = new HashMap<>();
        config.put("GOOSE_PROVIDER", "anthropic");
        config.put("GOOSE_MODEL", "claude-3");
        when(agentConfigService.loadAgentConfigYaml("agent1")).thenReturn(config);
        when(agentConfigService.getAgentsDir()).thenReturn(java.nio.file.Path.of("/tmp/agents"));

        webTestClient.get()
            .uri("/gateway/agents/agent1/config")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agentsMd")
            .isEqualTo("# Agent One\n")
            .jsonPath("$.provider")
            .isEqualTo("anthropic")
            .jsonPath("$.model")
            .isEqualTo("claude-3");
    }

    /**
     * Tests list agents no auth required.
     */
    @Test
    public void testListAgents_noAuthRequired() {
        // listAgents does not require admin, just auth
        when(agentConfigService.getRegistry()).thenReturn(List.of());

        webTestClient.get()
            .uri("/gateway/agents")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agents")
            .isArray();
    }
}
