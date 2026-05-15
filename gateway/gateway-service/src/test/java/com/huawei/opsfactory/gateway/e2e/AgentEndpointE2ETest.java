/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;

import org.junit.Test;
import org.springframework.http.MediaType;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * E2E tests for AgentController endpoints.
 * Tests the full HTTP pipeline: AuthWebFilter → UserContextFilter → AgentController.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AgentEndpointE2ETest extends BaseE2ETest {

    /**
     * Executes the list agents authenticated returns agent list operation.
     */
    @Test
    public void listAgents_authenticated_returnsAgentList() {
        when(agentConfigService.getRegistry())
            .thenReturn(List.of(new AgentRegistryEntry("universal-agent", "Universal Agent"),
                new AgentRegistryEntry("kb-agent", "Knowledge Base Agent")));
        when(agentConfigService.loadAgentConfigYaml("universal-agent"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "openai", "GOOSE_MODEL", "gpt-4o"));
        when(agentConfigService.loadAgentConfigYaml("kb-agent"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "anthropic", "GOOSE_MODEL", "claude-3"));
        when(agentConfigService.listSkills("universal-agent")).thenReturn(List
            .of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming")));
        when(agentConfigService.listSkills("kb-agent")).thenReturn(Collections.emptyList());

        webClient.get()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agents")
            .isArray()
            .jsonPath("$.agents.length()")
            .isEqualTo(2)
            .jsonPath("$.agents[0].id")
            .isEqualTo("universal-agent")
            .jsonPath("$.agents[0].name")
            .isEqualTo("Universal Agent")
            .jsonPath("$.agents[0].sysOnly")
            .doesNotExist()
            .jsonPath("$.agents[0].provider")
            .isEqualTo("openai")
            .jsonPath("$.agents[0].model")
            .isEqualTo("gpt-4o")
            .jsonPath("$.agents[0].skills.length()")
            .isEqualTo(1)
            .jsonPath("$.agents[0].skills[0].name")
            .isEqualTo("brainstorming")
            .jsonPath("$.agents[1].id")
            .isEqualTo("kb-agent");
    }

    /**
     * Executes the list agents empty registry returns empty array operation.
     */
    @Test
    public void listAgents_emptyRegistry_returnsEmptyArray() {
        when(agentConfigService.getRegistry()).thenReturn(Collections.emptyList());

        webClient.get()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agents")
            .isArray()
            .jsonPath("$.agents.length()")
            .isEqualTo(0);
    }

    /**
     * Executes the list agents unauthenticated returns401 operation.
     */
    @Test
    public void listAgents_unauthenticated_returns401() {
        webClient.get().uri("/gateway/agents").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Executes the list agents regular user can access operation.
     */
    @Test
    public void listAgents_regularUser_canAccess() {
        when(agentConfigService.getRegistry()).thenReturn(Collections.emptyList());

        webClient.get()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agents")
            .isArray();
    }

    /**
     * Executes the create agent admin success operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void createAgent_admin_success() throws Exception {
        when(agentConfigService.createAgent("test-agent", "Test Agent"))
            .thenReturn(Map.of("id", "test-agent", "name", "Test Agent", "provider", "openai", "model", "gpt-4o"));

        webClient.post()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":\"test-agent\",\"name\":\"Test Agent\"}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.agent.id")
            .isEqualTo("test-agent")
            .jsonPath("$.agent.name")
            .isEqualTo("Test Agent");
    }

    /**
     * Executes the create agent non admin returns403 operation.
     */
    @Test
    public void createAgent_nonAdmin_returns403() {
        webClient.post()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":\"test\",\"name\":\"Test\"}")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Executes the create agent missing id returns400 operation.
     */
    @Test
    public void createAgent_missingId_returns400() {
        webClient.post()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"Test Agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Executes the create agent blank id returns400 operation.
     */
    @Test
    public void createAgent_blankId_returns400() {
        webClient.post()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":\"  \",\"name\":\"Test Agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Executes the create agent missing name returns400 operation.
     */
    @Test
    public void createAgent_missingName_returns400() {
        webClient.post()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":\"test-agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Executes the create agent duplicate id returns400 operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void createAgent_duplicateId_returns400() throws Exception {
        when(agentConfigService.createAgent(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Agent with ID 'test-agent' already exists"));

        webClient.post()
            .uri("/gateway/agents")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":\"test-agent\",\"name\":\"Test Agent\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Executes the delete agent admin success operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void deleteAgent_admin_success() throws Exception {
        doNothing().when(instanceManager).stopAllForAgent("test-agent");
        doNothing().when(agentConfigService).deleteAgent("test-agent");

        webClient.delete()
            .uri("/gateway/agents/test-agent")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);

        verify(instanceManager).stopAllForAgent("test-agent");
        verify(agentConfigService).deleteAgent("test-agent");
    }

    /**
     * Executes the delete agent non admin returns403 operation.
     */
    @Test
    public void deleteAgent_nonAdmin_returns403() {
        webClient.delete()
            .uri("/gateway/agents/test-agent")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "bob")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Executes the delete agent not found returns400 operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void deleteAgent_notFound_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Agent 'nonexistent' not found")).when(agentConfigService)
            .deleteAgent("nonexistent");

        webClient.delete()
            .uri("/gateway/agents/nonexistent")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    /**
     * Executes the list skills admin returns skills list operation.
     */
    @Test
    public void listSkills_admin_returnsSkillsList() {
        when(agentConfigService.listSkills("universal-agent")).thenReturn(
            List.of(Map.of("name", "brainstorming", "description", "Brainstorm ideas", "path", "skills/brainstorming"),
                Map.of("name", "coding", "description", "Code assistance", "path", "skills/coding")));

        webClient.get()
            .uri("/gateway/agents/universal-agent/skills")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.skills.length()")
            .isEqualTo(2)
            .jsonPath("$.skills[0].name")
            .isEqualTo("brainstorming")
            .jsonPath("$.skills[0].description")
            .isEqualTo("Brainstorm ideas")
            .jsonPath("$.skills[1].name")
            .isEqualTo("coding")
            .jsonPath("$.skills[1].description")
            .isEqualTo("Code assistance");
    }

    /**
     * Executes the list skills non admin returns403 operation.
     */
    @Test
    public void listSkills_nonAdmin_returns403() {
        webClient.get()
            .uri("/gateway/agents/universal-agent/skills")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Returns the config admin returns agent config.
     */
    @Test
    public void getConfig_admin_returnsAgentConfig() {
        when(agentConfigService.findAgent("universal-agent"))
            .thenReturn(new AgentRegistryEntry("universal-agent", "Universal Agent"));
        when(agentConfigService.loadAgentConfigYaml("universal-agent"))
            .thenReturn(Map.of("GOOSE_PROVIDER", "openai", "GOOSE_MODEL", "gpt-4o"));
        when(agentConfigService.readAgentsMd("universal-agent"))
            .thenReturn("# Universal Agent\nA general purpose agent.");
        when(agentConfigService.getAgentsDir()).thenReturn(Path.of("/tmp/agents"));

        webClient.get()
            .uri("/gateway/agents/universal-agent/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agentsMd")
            .isEqualTo("# Universal Agent\nA general purpose agent.")
            .jsonPath("$.provider")
            .isEqualTo("openai")
            .jsonPath("$.model")
            .isEqualTo("gpt-4o");
    }

    /**
     * Returns the config non admin returns403.
     */
    @Test
    public void getConfig_nonAdmin_returns403() {
        webClient.get()
            .uri("/gateway/agents/universal-agent/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "bob")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Returns the config missing provider model returns empty strings.
     */
    @Test
    public void getConfig_missingProviderModel_returnsEmptyStrings() {
        when(agentConfigService.findAgent("minimal-agent"))
            .thenReturn(new AgentRegistryEntry("minimal-agent", "Minimal Agent"));
        when(agentConfigService.loadAgentConfigYaml("minimal-agent")).thenReturn(Collections.emptyMap());
        when(agentConfigService.readAgentsMd("minimal-agent")).thenReturn("");
        when(agentConfigService.getAgentsDir()).thenReturn(Path.of("/tmp/agents"));

        webClient.get()
            .uri("/gateway/agents/minimal-agent/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.agentsMd")
            .isEqualTo("")
            .jsonPath("$.provider")
            .isEqualTo("")
            .jsonPath("$.model")
            .isEqualTo("");
    }

    /**
     * Executes the update config admin success operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void updateConfig_admin_success() throws Exception {
        when(agentConfigService.findAgent("universal-agent"))
            .thenReturn(new AgentRegistryEntry("universal-agent", "Universal Agent"));
        doNothing().when(agentConfigService).writeAgentsMd(eq("universal-agent"), anyString());

        webClient.put()
            .uri("/gateway/agents/universal-agent/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"agentsMd\":\"# Updated content\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);

        verify(agentConfigService).writeAgentsMd("universal-agent", "# Updated content");
    }

    /**
     * Executes the update config non admin returns403 operation.
     */
    @Test
    public void updateConfig_nonAdmin_returns403() {
        webClient.put()
            .uri("/gateway/agents/universal-agent/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"agentsMd\":\"hacked\"}")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Executes the update config null agents md still returns updated operation.
     */
    @Test
    public void updateConfig_nullAgentsMd_stillReturnsUpdated() {
        when(agentConfigService.findAgent("universal-agent"))
            .thenReturn(new AgentRegistryEntry("universal-agent", "Universal Agent"));

        webClient.put()
            .uri("/gateway/agents/universal-agent/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"other\":\"field\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }
}
