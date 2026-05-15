/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Agent Config Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AgentConfigServiceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private AgentConfigService service;

    private GatewayProperties properties;

    private Path gatewayRoot;

    private String previousGatewayConfigPath;

    /**
     * Sets the up.
     *
     * @throws IOException if the operation fails
     */
    @Before
    public void setUp() throws IOException {
        gatewayRoot = tempFolder.getRoot().toPath().resolve("gateway");
        Files.createDirectories(gatewayRoot.resolve("config"));
        Files.createDirectories(gatewayRoot.resolve("agents"));
        Files.createDirectories(gatewayRoot.resolve("users"));

        String configYaml =
            "port: 3000\n" + "residentInstances:\n" + "  enabled: true\n" + "  entries:\n" + "    - userId: admin\n"
                + "      agentIds: ['*']\n" + "    - userId: robby\n" + "      agentIds: ['test-agent']\n" + "agents:\n"
                + "  - id: test-agent\n" + "    name: Test Agent\n" + "  - id: kb-agent\n" + "    name: KB Agent\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);
        previousGatewayConfigPath = System.getProperty("GATEWAY_CONFIG_PATH");
        System.setProperty("GATEWAY_CONFIG_PATH", gatewayRoot.resolve("config.yaml").toString());

        service = new AgentConfigService(properties);
        service.loadRegistry();
    }

    /**
     * Executes the tear down operation.
     */
    @After
    public void tearDown() {
        if (previousGatewayConfigPath == null) {
            System.clearProperty("GATEWAY_CONFIG_PATH");
        } else {
            System.setProperty("GATEWAY_CONFIG_PATH", previousGatewayConfigPath);
        }
    }

    /**
     * Tests load registry.
     */
    @Test
    public void testLoadRegistry() {
        List<AgentRegistryEntry> registry = service.getRegistry();
        assertEquals(2, registry.size());
        assertEquals("test-agent", registry.get(0).id());
        assertEquals("Test Agent", registry.get(0).name());
        assertEquals("kb-agent", registry.get(1).id());
        assertEquals("KB Agent", registry.get(1).name());
    }

    /**
     * Tests load registry when gateway config path points to gateway config.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistryWhenGatewayConfigPathPointsToGatewayConfig() throws IOException {
        Path externalRoot = tempFolder.getRoot().toPath().resolve("external-runtime");
        Path externalGatewayRoot = externalRoot.resolve("gateway");
        Files.createDirectories(externalGatewayRoot.resolve("agents"));
        Files.createDirectories(externalGatewayRoot.resolve("users"));
        Files.writeString(externalGatewayRoot.resolve("config.yaml"),
            "agents:\n" + "  - id: external-agent\n" + "    name: External Agent\n");

        GatewayProperties externalProperties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot("..");
        externalProperties.setPaths(paths);

        String previous = System.getProperty("GATEWAY_CONFIG_PATH");
        System.setProperty("GATEWAY_CONFIG_PATH", externalGatewayRoot.resolve("config.yaml").toString());
        try {
            AgentConfigService externalService = new AgentConfigService(externalProperties);
            externalService.loadRegistry();

            assertEquals(1, externalService.getRegistry().size());
            assertEquals("external-agent", externalService.getRegistry().get(0).id());
            assertEquals(externalGatewayRoot.normalize(), externalService.getGatewayRoot());
        } finally {
            if (previous == null) {
                System.clearProperty("GATEWAY_CONFIG_PATH");
            } else {
                System.setProperty("GATEWAY_CONFIG_PATH", previous);
            }
        }
    }

    /**
     * Tests load resident instances expands wildcard and specific agent.
     */
    @Test
    public void testLoadResidentInstances_expandsWildcardAndSpecificAgent() {
        assertTrue(service.isResidentInstance("test-agent", "admin"));
        assertTrue(service.isResidentInstance("kb-agent", "admin"));
        assertTrue(service.isResidentInstance("test-agent", "robby"));
        assertFalse(service.isResidentInstance("kb-agent", "robby"));
        assertEquals(3, service.getResidentInstances().size());
    }

    /**
     * Tests load resident instances ignores unknown and duplicate agents.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadResidentInstances_ignoresUnknownAndDuplicateAgents() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-a\n    name: Agent A\n"
            + "  - id: agent-b\n    name: Agent B\n" + "residentInstances:\n" + "  enabled: true\n" + "  entries:\n"
            + "    - userId: admin\n" + "      agentIds: ['agent-a', 'missing-agent', 'agent-a']\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertTrue(freshService.isResidentInstance("agent-a", "admin"));
        assertFalse(freshService.isResidentInstance("missing-agent", "admin"));
        assertEquals(1, freshService.getResidentInstances().size());
    }

    /**
     * Tests find agent exists.
     */
    @Test
    public void testFindAgent_exists() {
        AgentRegistryEntry entry = service.findAgent("test-agent");
        assertNotNull(entry);
        assertEquals("Test Agent", entry.name());
    }

    /**
     * Tests find agent not found.
     */
    @Test
    public void testFindAgent_notFound() {
        assertNull(service.findAgent("nonexistent"));
    }

    /**
     * Tests load agent config yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadAgentConfigYaml() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"), "GOOSE_PROVIDER: openai\nGOOSE_MODEL: gpt-4o\n");

        Map<String, Object> config = service.loadAgentConfigYaml("test-agent");
        assertEquals("openai", config.get("GOOSE_PROVIDER"));
        assertEquals("gpt-4o", config.get("GOOSE_MODEL"));
    }

    /**
     * Tests load agent config yaml no file.
     */
    @Test
    public void testLoadAgentConfigYaml_noFile() {
        Map<String, Object> config = service.loadAgentConfigYaml("nonexistent");
        assertTrue(config.isEmpty());
    }

    /**
     * Tests list custom providers parses provider json files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListCustomProviders_parsesProviderJson() throws IOException {
        Path providersDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config")
                .resolve("custom_providers");
        Files.createDirectories(providersDir);
        Files.writeString(providersDir.resolve("custom_local.json"),
                "{\n"
                        + "  \"name\": \"custom_local\",\n"
                        + "  \"display_name\": \"Local\",\n"
                        + "  \"engine\": \"openai\",\n"
                        + "  \"models\": [{ \"name\": \"qwen-local\", \"context_limit\": 32768 }]\n"
                        + "}\n");
        Files.writeString(providersDir.resolve("custom_remote.json"),
                "{\n"
                        + "  \"name\": \"custom_remote\",\n"
                        + "  \"display_name\": \"Remote\",\n"
                        + "  \"engine\": \"openai\",\n"
                        + "  \"models\": [{ \"name\": \"kimi-remote\", \"context_limit\": 128000 }]\n"
                        + "}\n");

        List<Map<String, Object>> providers = service.listCustomProviders("test-agent");

        assertEquals(2, providers.size());
        List<String> names = providers.stream().map(provider -> String.valueOf(provider.get("name"))).toList();
        assertTrue(names.contains("custom_local"));
        assertTrue(names.contains("custom_remote"));
        Map<String, Object> local = providers.stream()
                .filter(provider -> "custom_local".equals(provider.get("name"))).findFirst().orElseThrow();
        assertEquals("custom_local.json", local.get("fileName"));
    }

    /**
     * Tests extract agent config summary counts extension states.
     */
    @Test
    public void testExtractAgentConfigSummary_countsExtensions() {
        Map<String, Object> summary = service.extractAgentConfigSummary(Map.of(
                "GOOSE_MODE", "auto",
                "GOOSE_DISABLE_KEYRING", "1",
                "GOOSE_TELEMETRY_ENABLED", false,
                "extensions", Map.of(
                        "developer", Map.of("enabled", true),
                        "memory", Map.of("enabled", false),
                        "summarize", Map.of("enabled", "true"))));

        assertEquals("auto", summary.get("mode"));
        assertEquals("1", summary.get("disableKeyring"));
        assertEquals(false, summary.get("telemetryEnabled"));
        assertEquals(2, summary.get("enabledExtensions"));
        assertEquals(1, summary.get("disabledExtensions"));
    }

    /**
     * Tests update model config writes model keys to config yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateModelConfig_writesModelKeys() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir.resolve("custom_providers"));
        Files.writeString(configDir.resolve("custom_providers").resolve("custom_local.json"),
                "{\"name\":\"custom_local\",\"models\":[{\"name\":\"model-a\"}]}\n");
        Files.writeString(configDir.resolve("config.yaml"),
                "GOOSE_PROVIDER: old_provider\n"
                        + "GOOSE_MODEL: old_model\n"
                        + "GOOSE_TEMPERATURE: '0.2'\n");

        service.updateModelConfig("test-agent", Map.of(
                "GOOSE_PROVIDER", "custom_local",
                "GOOSE_MODEL", "model-a",
                "GOOSE_TEMPERATURE", "0.4",
                "GOOSE_MAX_TOKENS", "4096"));

        Map<String, Object> config = service.loadAgentConfigYaml("test-agent");
        assertEquals("custom_local", config.get("GOOSE_PROVIDER"));
        assertEquals("model-a", config.get("GOOSE_MODEL"));
        assertEquals("0.4", config.get("GOOSE_TEMPERATURE"));
        assertEquals("4096", config.get("GOOSE_MAX_TOKENS"));
    }

    /**
     * Tests create custom provider writes normalized json.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateCustomProvider_writesProviderJson() throws IOException {
        Map<String, Object> provider = service.createCustomProvider("test-agent", Map.of(
                "name", "custom_new",
                "display_name", "Custom New",
                "engine", "openai",
                "base_url", "http://127.0.0.1:11434/v1/chat/completions",
                "api_key", "test-key",
                "models", List.of(Map.of("name", "model-new", "context_limit", "32768")),
                "requires_auth", false,
                "supports_streaming", true));

        assertEquals("custom_new", provider.get("name"));
        assertEquals("CUSTOM_NEW_API_KEY", provider.get("api_key_env"));
        assertEquals("openai", provider.get("engine"));
        assertEquals(true, provider.get("requires_auth"));
        assertEquals(true, provider.get("supports_streaming"));
        assertTrue(Files.exists(gatewayRoot.resolve("agents").resolve("test-agent").resolve("config")
                .resolve("custom_providers").resolve("custom_new.json")));
        assertEquals("test-key", service.loadAgentSecretsYaml("test-agent").get("CUSTOM_NEW_API_KEY"));
        assertEquals(1, service.listCustomProviders("test-agent").size());
    }

    /**
     * Tests update custom provider preserves identity fields and updates editable values.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateCustomProvider_preservesIdentityFields() throws IOException {
        Path providersDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config")
                .resolve("custom_providers");
        Files.createDirectories(providersDir);
        Files.writeString(providersDir.resolve("custom_existing.json"),
                "{\n"
                        + "  \"name\": \"custom_existing\",\n"
                        + "  \"display_name\": \"Existing Display\",\n"
                        + "  \"engine\": \"anthropic\",\n"
                        + "  \"description\": \"old description\",\n"
                        + "  \"api_key_env\": \"CUSTOM_EXISTING_API_KEY\",\n"
                        + "  \"base_url\": \"https://old.example.com/v1\",\n"
                        + "  \"models\": [{ \"name\": \"old-model\", \"context_limit\": 1000 }]\n"
                        + "}\n");

        Map<String, Object> provider = service.updateCustomProvider("test-agent", "custom_existing", Map.of(
                "name", "custom_ignored",
                "display_name", "Ignored Display",
                "description", "new description",
                "base_url", "https://new.example.com/v1",
                "api_key", "new-key",
                "models", List.of(Map.of("name", "new-model", "context_limit", "64000"))));

        assertEquals("custom_existing", provider.get("name"));
        assertEquals("Existing Display", provider.get("display_name"));
        assertEquals("openai", provider.get("engine"));
        assertEquals("new description", provider.get("description"));
        assertEquals("https://new.example.com/v1", provider.get("base_url"));
        assertEquals("new-key", service.loadAgentSecretsYaml("test-agent").get("CUSTOM_EXISTING_API_KEY"));

        List<Map<String, Object>> providers = service.listCustomProviders("test-agent");
        assertEquals(1, providers.size());
        assertEquals("custom_existing.json", providers.get(0).get("fileName"));
    }

    /**
     * Tests read write agents md.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testReadWriteAgentsMd() throws IOException {
        Path agentDir = gatewayRoot.resolve("agents").resolve("test-agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("AGENTS.md"), "# Test Agent\n");

        String md = service.readAgentsMd("test-agent");
        assertEquals("# Test Agent\n", md);

        service.writeAgentsMd("test-agent", "# Updated\nNew content\n");
        String updated = service.readAgentsMd("test-agent");
        assertEquals("# Updated\nNew content\n", updated);
    }

    /**
     * Tests read agents md no file.
     */
    @Test
    public void testReadAgentsMd_noFile() {
        String md = service.readAgentsMd("nonexistent");
        assertEquals("", md);
    }

    /**
     * Tests list skills.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListSkills() throws IOException {
        Path skillsDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("skills");
        Files.createDirectories(skillsDir.resolve("skill-a"));
        Files.createDirectories(skillsDir.resolve("skill-b"));
        Files.writeString(skillsDir.resolve("readme.txt"), "not a skill");

        // Add SKILL.md with frontmatter to skill-a
        Files.writeString(skillsDir.resolve("skill-a").resolve("SKILL.md"),
            "---\nname: Skill A\ndescription: Description of skill A\npinned: true\ndisplay-order: "
                + "-100\n---\n# Skill A\n");

        List<Map<String, String>> skills = service.listSkills("test-agent");
        assertEquals(2, skills.size());

        List<String> names = skills.stream().map(s -> s.get("name")).toList();
        // parsed from frontmatter
        assertTrue(names.contains("Skill A"));
        // fallback to dir name
        assertTrue(names.contains("skill-b"));

        // Verify skill-a has parsed description
        Map<String, String> skillA =
            skills.stream().filter(s -> "Skill A".equals(s.get("name"))).findFirst().orElseThrow();
        assertEquals("Description of skill A", skillA.get("description"));
        assertEquals("skills/skill-a", skillA.get("path"));
        assertEquals("true", skillA.get("pinned"));
        assertEquals("-100", skillA.get("displayOrder"));

        // Verify skill-b has empty description (no SKILL.md)
        Map<String, String> skillB =
            skills.stream().filter(s -> "skill-b".equals(s.get("name"))).findFirst().orElseThrow();
        assertEquals("", skillB.get("description"));
    }

    /**
     * Tests list skills no skills dir.
     */
    @Test
    public void testListSkills_noSkillsDir() {
        List<Map<String, String>> skills = service.listSkills("nonexistent");
        assertTrue(skills.isEmpty());
    }

    /**
     * Tests create agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent() throws IOException {
        Path templateDir = gatewayRoot.resolve("agents").resolve("universal-agent").resolve("config");
        Files.createDirectories(templateDir);
        Files.writeString(templateDir.resolve("config.yaml"), "GOOSE_PROVIDER: anthropic\nGOOSE_MODEL: claude-3\n");

        Map<String, Object> result = service.createAgent("new-agent", "New Agent");
        assertEquals("new-agent", result.get("id"));
        assertEquals("New Agent", result.get("name"));
        assertEquals("anthropic", result.get("provider"));

        assertNotNull(service.findAgent("new-agent"));

        assertTrue(Files.exists(gatewayRoot.resolve("agents").resolve("new-agent").resolve("AGENTS.md")));
        assertTrue(
            Files.exists(gatewayRoot.resolve("agents").resolve("new-agent").resolve("config").resolve("config.yaml")));
        assertTrue(
            Files.exists(gatewayRoot.resolve("agents").resolve("new-agent").resolve("config").resolve("secrets.yaml")));
    }

    /**
     * Tests create agent duplicate id.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_duplicateId() throws IOException {
        service.createAgent("test-agent", "Duplicate");
    }

    /**
     * Tests create agent invalid id.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_invalidId() throws IOException {
        service.createAgent("INVALID!", "Bad ID");
    }

    /**
     * Tests delete agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteAgent() throws IOException {
        Path agentDir = gatewayRoot.resolve("agents").resolve("test-agent");
        Files.createDirectories(agentDir.resolve("config"));
        Files.writeString(agentDir.resolve("AGENTS.md"), "# Test\n");

        service.deleteAgent("test-agent");

        assertNull(service.findAgent("test-agent"));
        assertFalse(Files.exists(agentDir));
    }

    /**
     * Tests delete agent not found.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteAgent_notFound() throws IOException {
        service.deleteAgent("nonexistent");
    }

    /**
     * Tests load agent secrets yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadAgentSecretsYaml() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("test-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("secrets.yaml"), "OPENAI_API_KEY: sk-test123\nANTHROPIC_KEY: ak-test456\n");

        Map<String, Object> secrets = service.loadAgentSecretsYaml("test-agent");
        assertEquals("sk-test123", secrets.get("OPENAI_API_KEY"));
        assertEquals("ak-test456", secrets.get("ANTHROPIC_KEY"));
    }

    /**
     * Tests load agent secrets yaml no file.
     */
    @Test
    public void testLoadAgentSecretsYaml_noFile() {
        Map<String, Object> secrets = service.loadAgentSecretsYaml("nonexistent");
        assertTrue(secrets.isEmpty());
    }

    /**
     * Tests create agent duplicate name.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_duplicateName() throws IOException {
        service.createAgent("another-agent", "Test Agent");
    }

    /**
     * Tests create agent no template.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent_noTemplate() throws IOException {
        Map<String, Object> result = service.createAgent("new-agent", "New Agent");
        assertEquals("new-agent", result.get("id"));
        assertEquals("New Agent", result.get("name"));
        assertEquals("openai", result.get("provider"));
    }

    /**
     * Tests getters resolve correct paths.
     */
    @Test
    public void testGettersResolveCorrectPaths() {
        Path agentsDir = service.getAgentsDir();
        assertTrue(agentsDir.toString().endsWith("gateway/agents"));

        Path usersDir = service.getUsersDir();
        assertTrue(usersDir.toString().endsWith("gateway/users"));
    }

    /**
     * Tests get agent config dir.
     */
    @Test
    public void testGetAgentConfigDir() {
        Path configDir = service.getAgentConfigDir("test-agent");
        assertTrue(configDir.toString().endsWith("agents/test-agent/config"));
    }

    /**
     * Tests delete agent removes from yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteAgent_removesFromYaml() throws IOException {
        Path agentDir = gatewayRoot.resolve("agents").resolve("test-agent");
        Files.createDirectories(agentDir.resolve("config"));
        Files.writeString(agentDir.resolve("AGENTS.md"), "# Test\n");

        int sizeBefore = service.getRegistry().size();
        service.deleteAgent("test-agent");
        assertEquals(sizeBefore - 1, service.getRegistry().size());

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertNull(freshService.findAgent("test-agent"));
    }

    /**
     * Tests registry is unmodifiable.
     */
    @Test
    public void testRegistryIsUnmodifiable() {
        List<AgentRegistryEntry> registry = service.getRegistry();
        try {
            registry.add(new AgentRegistryEntry("illegal", "Illegal"));
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    /**
     * Tests create agent updates agents yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent_updatesAgentsYaml() throws IOException {
        Path templateDir = gatewayRoot.resolve("agents").resolve("universal-agent").resolve("config");
        Files.createDirectories(templateDir);
        Files.writeString(templateDir.resolve("config.yaml"), "GOOSE_PROVIDER: anthropic\nGOOSE_MODEL: claude-3\n");

        service.createAgent("created-agent", "Created Agent");

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertNotNull(freshService.findAgent("created-agent"));
    }

    /**
     * Tests create agent single char id.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateAgent_singleCharId() throws IOException {
        service.createAgent("a", "Single Char");
    }

    /**
     * Tests create agent skills directory created.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testCreateAgent_skillsDirectoryCreated() throws IOException {
        service.createAgent("new-agent", "New Agent");
        Path skillsDir = gatewayRoot.resolve("agents").resolve("new-agent").resolve("config").resolve("skills");
        assertTrue(Files.isDirectory(skillsDir));
    }

    /**
     * Tests load registry empty agents yaml.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_emptyAgentsYaml() throws IOException {
        Files.writeString(gatewayRoot.resolve("config.yaml"), "");
        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertTrue(freshService.getRegistry().isEmpty());
    }

    /**
     * Tests load registry no agents key.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_noAgentsKey() throws IOException {
        Files.writeString(gatewayRoot.resolve("config.yaml"), "other: value\n");
        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();
        assertTrue(freshService.getRegistry().isEmpty());
    }

    /**
     * Tests load registry enabled false excludes agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_enabledFalseExcludesAgent() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-a\n    name: Agent A\n"
            + "  - id: agent-b\n    name: Agent B\n    enabled: false\n" + "  - id: agent-c\n    name: Agent C\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        List<AgentRegistryEntry> registry = freshService.getRegistry();
        assertEquals(2, registry.size());
        assertEquals("agent-a", registry.get(0).id());
        assertEquals("agent-c", registry.get(1).id());
        assertNull(freshService.findAgent("agent-b"));
    }

    /**
     * Tests load registry enabled true includes agent.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_enabledTrueIncludesAgent() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-a\n    name: Agent A\n    enabled: true\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertEquals(1, freshService.getRegistry().size());
        assertNotNull(freshService.findAgent("agent-a"));
    }

    /**
     * Tests load registry enabled omitted defaults to true.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_enabledOmittedDefaultsToTrue() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-no-enabled\n    name: No Enabled Field\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertEquals(1, freshService.getRegistry().size());
        assertNotNull(freshService.findAgent("agent-no-enabled"));
    }

    /**
     * Tests load registry all disabled results in empty registry.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_allDisabledResultsInEmptyRegistry() throws IOException {
        String configYaml = "agents:\n" + "  - id: agent-x\n    name: Agent X\n    enabled: false\n"
            + "  - id: agent-y\n    name: Agent Y\n    enabled: false\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertTrue(freshService.getRegistry().isEmpty());
    }

    // ── Memory file tests ──────────────────────────────────────────

    /**
     * Tests list memory files empty.
     */
    @Test
    public void testListMemoryFiles_empty() {
        List<Map<String, String>> files = service.listMemoryFiles("test-agent");
        assertTrue(files.isEmpty());
    }

    /**
     * Tests list memory files with files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListMemoryFiles_withFiles() throws IOException {
        Path memoryDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("goose").resolve("memory");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("development.txt"), "# tools\nuse black for formatting");
        Files.writeString(memoryDir.resolve("personal.txt"), "prefer Chinese replies");

        List<Map<String, String>> files = service.listMemoryFiles("test-agent");
        assertEquals(2, files.size());

        List<String> categories = files.stream().map(f -> f.get("category")).toList();
        assertTrue(categories.contains("development"));
        assertTrue(categories.contains("personal"));

        Map<String, String> dev =
            files.stream().filter(f -> "development".equals(f.get("category"))).findFirst().orElseThrow();
        assertEquals("# tools\nuse black for formatting", dev.get("content"));
    }

    /**
     * Tests list memory files ignores non txt.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListMemoryFiles_ignoresNonTxt() throws IOException {
        Path memoryDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("goose").resolve("memory");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("valid.txt"), "content");
        Files.writeString(memoryDir.resolve("ignored.md"), "markdown");

        List<Map<String, String>> files = service.listMemoryFiles("test-agent");
        assertEquals(1, files.size());
        assertEquals("valid", files.get(0).get("category"));
    }

    /**
     * Tests read memory file exists.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testReadMemoryFile_exists() throws IOException {
        Path memoryDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("goose").resolve("memory");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("dev.txt"), "hello world");

        String content = service.readMemoryFile("test-agent", "dev");
        assertEquals("hello world", content);
    }

    /**
     * Tests read memory file not found.
     */
    @Test
    public void testReadMemoryFile_notFound() {
        String content = service.readMemoryFile("test-agent", "nonexistent");
        assertNull(content);
    }

    /**
     * Tests write memory file creates directory and file.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteMemoryFile_createsDirectoryAndFile() throws IOException {
        service.writeMemoryFile("test-agent", "new-category", "some content");

        Path file = gatewayRoot.resolve("agents")
            .resolve("test-agent")
            .resolve("config")
            .resolve("goose")
            .resolve("memory")
            .resolve("new-category.txt");
        assertTrue(Files.exists(file));
        assertEquals("some content", Files.readString(file));
    }

    /**
     * Tests write memory file updates existing.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteMemoryFile_updatesExisting() throws IOException {
        service.writeMemoryFile("test-agent", "cat", "v1");
        service.writeMemoryFile("test-agent", "cat", "v2");

        assertEquals("v2", service.readMemoryFile("test-agent", "cat"));
    }

    /**
     * Tests write memory file too large.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWriteMemoryFile_tooLarge() throws IOException {
        String largeContent = "x".repeat(101 * 1024);
        service.writeMemoryFile("test-agent", "big", largeContent);
    }

    /**
     * Tests delete memory file success.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteMemoryFile_success() throws IOException {
        Path memoryDir =
            gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("goose").resolve("memory");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("toDelete.txt"), "bye");

        service.deleteMemoryFile("test-agent", "toDelete");
        assertFalse(Files.exists(memoryDir.resolve("toDelete.txt")));
    }

    /**
     * Tests delete memory file not found.
     *
     * @throws IOException if the operation fails
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMemoryFile_notFound() throws IOException {
        service.deleteMemoryFile("test-agent", "nonexistent");
    }

    /**
     * Tests write and read round trip.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteAndReadRoundTrip() throws IOException {
        String content = "# formatting tools\nuse black\n\n# deployment\nuse k8s";
        service.writeMemoryFile("test-agent", "dev", content);
        assertEquals(content, service.readMemoryFile("test-agent", "dev"));
    }

    /**
     * Tests list memory files after write and delete.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListMemoryFiles_afterWriteAndDelete() throws IOException {
        service.writeMemoryFile("test-agent", "a", "content-a");
        service.writeMemoryFile("test-agent", "b", "content-b");
        assertEquals(2, service.listMemoryFiles("test-agent").size());

        service.deleteMemoryFile("test-agent", "a");
        List<Map<String, String>> remaining = service.listMemoryFiles("test-agent");
        assertEquals(1, remaining.size());
        assertEquals("b", remaining.get(0).get("category"));
    }

    /**
     * Tests write knowledge cli settings stores source id and relative artifacts root.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteKnowledgeCliSettings_storesSourceIdAndRelArtifactsRoot() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("qa-cli-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"), "extensions:\n" + "  knowledge-cli:\n"
            + "    x-opsfactory:\n" + "      scope:\n" + "        rootDir: ../data\n");

        service.writeMcpSettings("qa-cli-agent", "knowledge-cli", Map.of("sourceId", "src_123"));

        Map<String, Object> settings = service.readMcpSettings("qa-cli-agent", "knowledge-cli");
        assertEquals("src_123", settings.get("sourceId"));
        assertEquals("../../../../knowledge-service/data/artifacts/src_123", settings.get("rootDir"));
        assertEquals(configDir.resolve("../../../../knowledge-service/data/artifacts/src_123").normalize(),
            service.getKnowledgeCliRootDir("qa-cli-agent"));
    }

    /**
     * Tests write knowledge cli settings uses configured artifacts root.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteKnowledgeCliSettings_usesConfiguredArtifactsRoot() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("qa-cli-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"), "extensions:\n" + "  knowledge-cli:\n"
            + "    x-opsfactory:\n" + "      scope:\n" + "        rootDir: ../data\n");
        Path externalArtifactsRoot = tempFolder.getRoot().toPath().getParent().resolve("external-artifacts");
        properties.getKnowledge().setArtifactsRoot(externalArtifactsRoot.toString());

        service.writeMcpSettings("qa-cli-agent", "knowledge-cli", Map.of("sourceId", "src_external"));

        Map<String, Object> settings = service.readMcpSettings("qa-cli-agent", "knowledge-cli");
        assertEquals("src_external", settings.get("sourceId"));
        assertEquals(externalArtifactsRoot.resolve("src_external").normalize().toString(), settings.get("rootDir"));
    }

    /**
     * Tests write knowledge cli settings clear resets default root.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testWriteKnowledgeCliSettings_clearResetsDefaultRoot() throws IOException {
        Path configDir = gatewayRoot.resolve("agents").resolve("qa-cli-agent").resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.yaml"),
            "extensions:\n" + "  knowledge-cli:\n" + "    x-opsfactory:\n" + "      scope:\n"
                + "        sourceId: src_old\n"
                + "        rootDir: ../../../../knowledge-service/data/artifacts/src_old\n");

        service.writeMcpSettings("qa-cli-agent", "knowledge-cli", Map.of("sourceId", ""));

        Map<String, Object> settings = service.readMcpSettings("qa-cli-agent", "knowledge-cli");
        assertNull(settings.get("sourceId"));
        assertEquals("../data", settings.get("rootDir"));
    }

    /**
     * Tests load registry disabled agent is excluded from resident expansion.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testLoadRegistry_disabledAgentIsExcludedFromResidentExpansion() throws IOException {
        String configYaml = "agents:\n" + "  - id: visible-agent\n    name: Visible Agent\n"
            + "  - id: hidden-agent\n    name: Hidden Agent\n    enabled: false\n" + "residentInstances:\n"
            + "  enabled: true\n" + "  entries:\n" + "    - userId: admin\n" + "      agentIds: ['*']\n";
        Files.writeString(gatewayRoot.resolve("config.yaml"), configYaml);

        AgentConfigService freshService = new AgentConfigService(properties);
        freshService.loadRegistry();

        assertEquals(1, freshService.getRegistry().size());
        assertNotNull(freshService.findAgent("visible-agent"));
        assertNull(freshService.findAgent("hidden-agent"));
        assertTrue(freshService.isResidentInstance("visible-agent", "admin"));
        assertFalse(freshService.isResidentInstance("hidden-agent", "admin"));
    }
}
