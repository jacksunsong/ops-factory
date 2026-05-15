/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.common.model.ResidentInstanceTarget;
import com.huawei.opsfactory.gateway.common.util.FileUtil;
import com.huawei.opsfactory.gateway.common.util.YamlLoader;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

/**
 * Manages agent configuration, registry, skills, memory files, and MCP settings.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class AgentConfigService {
    private static final Logger log = LoggerFactory.getLogger(AgentConfigService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String KNOWLEDGE_SERVICE_MCP = "knowledge-service";

    private static final String KNOWLEDGE_CLI_MCP = "knowledge-cli";

    private static final String DEFAULT_KNOWLEDGE_CLI_ROOT_DIR = "../data";

    private static final Pattern PROVIDER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final List<String> MODEL_CONFIG_KEYS = List.of("GOOSE_PROVIDER", "GOOSE_MODEL", "GOOSE_MODE",
        "GOOSE_CONTEXT_LIMIT", "GOOSE_MAX_TOKENS", "GOOSE_TEMPERATURE", "GOOSE_CONTEXT_STRATEGY",
        "GOOSE_AUTO_COMPACT_THRESHOLD", "GOOSE_MAX_TURNS");

    private final GatewayProperties properties;

    private final CopyOnWriteArrayList<AgentRegistryEntry> registry = new CopyOnWriteArrayList<>();

    private final CopyOnWriteArrayList<ResidentInstanceTarget> residentInstances = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<String, Map<String, Object>> configCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Map<String, Object>> secretsCache = new ConcurrentHashMap<>();

    private final Set<String> residentInstanceKeys = ConcurrentHashMap.newKeySet();

    private Path gatewayRoot;

    /**
     * Creates the agent config service instance.
     *
     * @param properties gateway configuration properties
     */
    public AgentConfigService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Loads the agent registry from the gateway config.yaml at startup.
     */
    @PostConstruct
    public void loadRegistry() {
        registry.clear();
        residentInstances.clear();
        residentInstanceKeys.clear();

        this.gatewayRoot = properties.getGatewayRootPath();
        Path configYaml = gatewayRoot.resolve("config.yaml");
        Map<String, Object> data = YamlLoader.load(configYaml);

        Object agentsObj = data.get("agents");
        if (agentsObj instanceof List<?> agentsList) {
            for (Object item : agentsList) {
                if (item instanceof Map<?, ?> rawMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) rawMap;
                    boolean enabled = !Boolean.FALSE.equals(map.get("enabled"));
                    if (!enabled) {
                        log.info("Skipping disabled agent: {}", map.get("id"));
                        continue;
                    }
                    String id = YamlLoader.getString(map, "id", "");
                    String name = YamlLoader.getString(map, "name", "");
                    registry.add(new AgentRegistryEntry(id, name));
                }
            }
        }
        loadResidentInstances(data);
        log.info("Loaded {} agents from registry", registry.size());
        log.info("Loaded {} resident instance targets", residentInstances.size());
    }

    /**
     * Returns an unmodifiable view of the current agent registry.
     *
     * @return unmodifiable list of registered agent entries
     */
    public List<AgentRegistryEntry> getRegistry() {
        return Collections.unmodifiableList(registry);
    }

    /**
     * Returns an unmodifiable view of the configured resident instance targets.
     *
     * @return unmodifiable list of resident instance targets
     */
    public List<ResidentInstanceTarget> getResidentInstances() {
        return Collections.unmodifiableList(residentInstances);
    }

    /**
     * Checks whether the given agent-user pair is a resident instance.
     *
     * @param agentId agent instance identifier
     * @param userId user identifier
     * @return {@code true} if the agent-user pair is configured as a resident instance
     */
    public boolean isResidentInstance(String agentId, String userId) {
        return residentInstanceKeys.contains(ManagedInstance.buildKey(agentId, userId));
    }

    /**
     * Finds an agent registry entry by its ID.
     *
     * @param agentId agent instance identifier
     * @return the matching registry entry, or {@code null} if not found
     */
    public AgentRegistryEntry findAgent(String agentId) {
        for (AgentRegistryEntry entry : registry) {
            if (entry.id().equals(agentId)) {
                return entry;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void loadResidentInstances(Map<String, Object> data) {
        Object residentObj = data.get("residentInstances");
        if (!(residentObj instanceof Map<?, ?> residentMapObj)) {
            return;
        }
        Map<String, Object> residentMap = (Map<String, Object>) residentMapObj;
        if (Boolean.FALSE.equals(residentMap.get("enabled"))) {
            return;
        }

        Object entriesObj = residentMap.get("entries");
        if (!(entriesObj instanceof List<?> entries)) {
            return;
        }

        List<String> configuredAgentIds = registry.stream().map(AgentRegistryEntry::id).toList();
        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map<?, ?> rawEntry)) {
                continue;
            }
            Map<String, Object> entry = (Map<String, Object>) rawEntry;
            String userId = YamlLoader.getString(entry, "userId", "").trim();
            if (userId.isEmpty()) {
                log.warn("Skipping residentInstances entry with blank userId");
                continue;
            }
            Object agentIdsObj = entry.get("agentIds");
            if (!(agentIdsObj instanceof List<?> rawAgentIds) || rawAgentIds.isEmpty()) {
                log.warn("Skipping residentInstances entry for user {} without agentIds", userId);
                continue;
            }

            List<String> agentIds = rawAgentIds.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
            if (agentIds.contains("*")) {
                addResidentTargets(userId, configuredAgentIds);
                continue;
            }
            List<String> validAgentIds = agentIds.stream().filter(agentId -> {
                boolean exists = configuredAgentIds.contains(agentId);
                if (!exists) {
                    log.warn("Skipping unknown resident agent {} for user {}", agentId, userId);
                }
                return exists;
            }).toList();
            addResidentTargets(userId, validAgentIds);
        }
    }

    private void addResidentTargets(String userId, List<String> agentIds) {
        for (String agentId : agentIds) {
            String key = ManagedInstance.buildKey(agentId, userId);
            if (!residentInstanceKeys.add(key)) {
                continue;
            }
            residentInstances.add(new ResidentInstanceTarget(userId, agentId));
        }
    }

    /**
     * Load the agent's config.yaml as a Map (cached).
     *
     * @param agentId agent instance identifier
     * @return cached YAML config map for the agent
     */
    public Map<String, Object> loadAgentConfigYaml(String agentId) {
        return configCache.computeIfAbsent(agentId, id -> {
            Path configPath = getAgentConfigDir(id).resolve("config.yaml");
            return YamlLoader.load(configPath);
        });
    }

    /**
     * Load the agent's secrets.yaml as a Map (cached).
     *
     * @param agentId agent instance identifier
     * @return cached YAML secrets map for the agent
     */
    public Map<String, Object> loadAgentSecretsYaml(String agentId) {
        return secretsCache.computeIfAbsent(agentId, id -> {
            Path secretsPath = getAgentConfigDir(id).resolve("secrets.yaml");
            return YamlLoader.load(secretsPath);
        });
    }

    /**
     * Invalidate cached config/secrets for an agent.
     *
     * @param agentId agent instance identifier
     */
    public void invalidateCache(String agentId) {
        configCache.remove(agentId);
        secretsCache.remove(agentId);
    }

    /**
     * Returns model-related config values from an agent config.
     *
     * @param config the config parameter
     * @return the result
     */
    public Map<String, Object> extractModelConfig(Map<String, Object> config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : MODEL_CONFIG_KEYS) {
            result.put(key, config.getOrDefault(key, ""));
        }
        return result;
    }

    /**
     * Returns a compact summary of the agent config.yaml for overview rendering.
     *
     * @param config the parsed config.yaml content
     * @return the result
     */
    public Map<String, Object> extractAgentConfigSummary(Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", config.getOrDefault("GOOSE_MODE", ""));
        result.put("disableKeyring", config.getOrDefault("GOOSE_DISABLE_KEYRING", ""));
        result.put("telemetryEnabled", config.getOrDefault("GOOSE_TELEMETRY_ENABLED", ""));

        int enabledExtensions = 0;
        int disabledExtensions = 0;
        Object extensionsObj = config.get("extensions");
        if (extensionsObj instanceof Map<?, ?> extensions) {
            for (Object extensionObj : extensions.values()) {
                if (extensionObj instanceof Map<?, ?> extension) {
                    if (isEnabled(extension.get("enabled"))) {
                        enabledExtensions++;
                    } else {
                        disabledExtensions++;
                    }
                }
            }
        }
        result.put("enabledExtensions", enabledExtensions);
        result.put("disabledExtensions", disabledExtensions);
        return result;
    }

    /**
     * Lists custom provider JSON definitions for an agent.
     *
     * @param agentId the agentId parameter
     * @return the result
     */
    public List<Map<String, Object>> listCustomProviders(String agentId) {
        Path providersDir = getCustomProvidersDir(agentId);
        if (!Files.isDirectory(providersDir)) {
            return List.of();
        }
        List<Map<String, Object>> providers = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(providersDir, "*.json")) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                try {
                    Map<String, Object> provider =
                        OBJECT_MAPPER.readValue(entry.toFile(), new TypeReference<Map<String, Object>>() {});
                    provider.put("fileName", entry.getFileName().toString());
                    providers.add(provider);
                } catch (IOException | IllegalArgumentException e) {
                    log.warn("Failed to parse custom provider {} for {}: {}", entry.getFileName(), agentId,
                        e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list custom providers for {}: {}", agentId, e.getMessage());
        }
        providers.sort((left, right) -> String.valueOf(left.getOrDefault("display_name", left.get("name")))
            .compareToIgnoreCase(String.valueOf(right.getOrDefault("display_name", right.get("name")))));
        return providers;
    }

    /**
     * Updates model-related keys in config.yaml.
     *
     * @param agentId the agentId parameter
     * @param modelConfig the modelConfig parameter
     * @throws IllegalStateException if the configuration cannot be written
     */
    public void updateModelConfig(String agentId, Map<String, String> modelConfig) {
        try {
            doUpdateModelConfig(agentId, modelConfig);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update model config for " + agentId, e);
        }
    }

    private void doUpdateModelConfig(String agentId, Map<String, String> modelConfig) throws IOException {
        Path configPath = getAgentConfigDir(agentId).resolve("config.yaml");
        Map<String, Object> config = new LinkedHashMap<>(YamlLoader.load(configPath));
        String provider = trimToNull(modelConfig.get("GOOSE_PROVIDER"));
        String model = trimToNull(modelConfig.get("GOOSE_MODEL"));
        if (provider == null) {
            throw new IllegalArgumentException("GOOSE_PROVIDER is required");
        }
        if (model == null) {
            throw new IllegalArgumentException("GOOSE_MODEL is required");
        }
        if (!customProviderExists(agentId, provider) && !provider.equals(config.get("GOOSE_PROVIDER"))) {
            throw new IllegalArgumentException("Provider '" + provider + "' not found for agent '" + agentId + "'");
        }

        for (String key : MODEL_CONFIG_KEYS) {
            if (modelConfig.containsKey(key)) {
                String value = trimToNull(modelConfig.get(key));
                if (value == null) {
                    config.remove(key);
                } else {
                    config.put(key, value);
                }
            }
        }

        Yaml yaml = createBlockYaml();
        Files.writeString(configPath, yaml.dump(config), StandardCharsets.UTF_8);
        invalidateCache(agentId);
    }

    /**
     * Creates a custom provider JSON definition for an agent.
     *
     * @param agentId the agentId parameter
     * @param provider the provider parameter
     * @return the created provider definition
     * @throws IllegalStateException if the provider cannot be written
     */
    public Map<String, Object> createCustomProvider(String agentId, Map<String, Object> provider) {
        try {
            return doCreateCustomProvider(agentId, provider);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create custom provider for " + agentId, e);
        }
    }

    private Map<String, Object> doCreateCustomProvider(String agentId, Map<String, Object> provider) throws IOException {
        String name = trimToNull(asString(provider.get("name")));
        if (name == null) {
            throw new IllegalArgumentException("Provider name is required");
        }
        if (!PROVIDER_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Provider name contains unsupported characters");
        }
        Path providersDir = getCustomProvidersDir(agentId);
        Files.createDirectories(providersDir);
        Path providerPath = providersDir.resolve(name + ".json");
        if (Files.exists(providerPath)) {
            throw new IllegalArgumentException("Provider '" + name + "' already exists");
        }

        Map<String, Object> normalized = normalizeProvider(provider, name);
        Files.writeString(providerPath, OBJECT_MAPPER.writeValueAsString(normalized) + System.lineSeparator(),
            StandardCharsets.UTF_8);
        writeProviderSecret(agentId, String.valueOf(normalized.get("api_key_env")), trimToNull(asString(provider.get("api_key"))));
        normalized.put("fileName", providerPath.getFileName().toString());
        return normalized;
    }

    /**
     * Updates editable fields in an existing custom provider JSON definition.
     *
     * @param agentId the agentId parameter
     * @param providerName the providerName parameter
     * @param provider the provider parameter
     * @return the updated provider definition
     * @throws IllegalStateException if the provider cannot be written
     */
    public Map<String, Object> updateCustomProvider(String agentId, String providerName, Map<String, Object> provider) {
        try {
            return doUpdateCustomProvider(agentId, providerName, provider);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update custom provider for " + agentId, e);
        }
    }

    private Map<String, Object> doUpdateCustomProvider(String agentId, String providerName,
        Map<String, Object> provider) throws IOException {
        String name = trimToNull(providerName);
        if (name == null) {
            throw new IllegalArgumentException("Provider name is required");
        }
        if (!PROVIDER_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Provider name contains unsupported characters");
        }
        Path providerPath = getCustomProvidersDir(agentId).resolve(name + ".json");
        if (!Files.exists(providerPath)) {
            throw new IllegalArgumentException("Provider '" + name + "' not found");
        }

        Map<String, Object> existing =
            OBJECT_MAPPER.readValue(providerPath.toFile(), new TypeReference<Map<String, Object>>() {});
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("name", defaultString(existing.get("name"), name));
        updated.put("engine", "openai");
        updated.put("display_name", defaultString(existing.get("display_name"), name));
        updated.put("description", defaultString(provider.get("description"), ""));
        updated.put("api_key_env", defaultString(existing.get("api_key_env"), buildApiKeyEnv(name)));
        updated.put("base_url", defaultString(provider.get("base_url"), ""));
        updated.put("models", normalizeProviderModels(provider.get("models")));
        updated.put("supports_streaming", true);
        updated.put("requires_auth", true);

        Files.writeString(providerPath, OBJECT_MAPPER.writeValueAsString(updated) + System.lineSeparator(),
            StandardCharsets.UTF_8);
        writeProviderSecret(agentId, String.valueOf(updated.get("api_key_env")), trimToNull(asString(provider.get("api_key"))));
        updated.put("fileName", providerPath.getFileName().toString());
        return updated;
    }

    private Map<String, Object> normalizeProvider(Map<String, Object> provider, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("engine", "openai");
        result.put("display_name", defaultString(provider.get("display_name"), name));
        result.put("description", defaultString(provider.get("description"), ""));
        result.put("api_key_env", buildApiKeyEnv(name));
        result.put("base_url", defaultString(provider.get("base_url"), ""));
        result.put("models", normalizeProviderModels(provider.get("models")));
        result.put("supports_streaming", true);
        result.put("requires_auth", true);
        return result;
    }

    private void writeProviderSecret(String agentId, String apiKeyEnv, String apiKey) throws IOException {
        if (apiKey == null || apiKeyEnv == null || apiKeyEnv.isBlank()) {
            return;
        }
        Path secretsPath = getAgentConfigDir(agentId).resolve("secrets.yaml");
        Map<String, Object> secrets = new LinkedHashMap<>(YamlLoader.load(secretsPath));
        secrets.put(apiKeyEnv, apiKey);
        Yaml yaml = createBlockYaml();
        Files.writeString(secretsPath, yaml.dump(secrets), StandardCharsets.UTF_8);
        invalidateCache(agentId);
    }

    private String buildApiKeyEnv(String providerName) {
        return "CUSTOM_" + providerName.replaceFirst("^custom[_-]?", "")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("^_+|_+$", "")
            .toUpperCase(java.util.Locale.ROOT) + "_API_KEY";
    }

    private List<Map<String, Object>> normalizeProviderModels(Object modelsObj) {
        if (!(modelsObj instanceof List<?> rawModels)) {
            throw new IllegalArgumentException("At least one model is required");
        }
        List<Map<String, Object>> models = new ArrayList<>();
        for (Object rawModel : rawModels) {
            if (!(rawModel instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String name = defaultString(rawMap.get("name"), "");
            Map<String, Object> model = new HashMap<>();
            model.put("name", name);
            Object contextLimit = rawMap.get("context_limit");
            if (contextLimit instanceof Number || contextLimit instanceof String) {
                model.put("context_limit", contextLimit);
            }
            models.add(model);
        }
        if (models.isEmpty()) {
            throw new IllegalArgumentException("At least one model is required");
        }
        return models;
    }

    private boolean customProviderExists(String agentId, String providerName) {
        return listCustomProviders(agentId).stream().anyMatch(provider -> providerName.equals(provider.get("name")));
    }

    private Path getCustomProvidersDir(String agentId) {
        return getAgentConfigDir(agentId).resolve("custom_providers");
    }

    private String defaultString(Object value, String fallback) {
        String result = trimToNull(asString(value));
        return result != null ? result : fallback;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private boolean isEnabled(Object value) {
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * List skills for an agent, parsing SKILL.md frontmatter for metadata.
     *
     * @param agentId agent instance identifier
     * @return list of skill metadata maps, each containing id, name, description, and path
     */
    public List<Map<String, String>> listSkills(String agentId) {
        Path skillsDir = getAgentConfigDir(agentId).resolve("skills");
        List<Map<String, String>> skills = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String dirName = entry.getFileName().toString();
                    Map<String, String> skill = new HashMap<>();
                    skill.put("id", dirName);
                    skill.put("name", dirName);
                    skill.put("description", "");
                    skill.put("path", "skills/" + dirName);

                    // Parse SKILL.md YAML frontmatter for name/description
                    Path skillMd = entry.resolve("SKILL.md");
                    if (Files.exists(skillMd)) {
                        try {
                            Map<String, String> frontmatter = parseMarkdownFrontmatter(skillMd);
                            if (frontmatter.containsKey("name")) {
                                skill.put("name", frontmatter.get("name"));
                            }
                            if (frontmatter.containsKey("description")) {
                                skill.put("description", frontmatter.get("description"));
                            }
                            putFrontmatterAlias(skill, frontmatter, "pinned", "pinned");
                            putFrontmatterAlias(skill, frontmatter, "displayOrder", "displayOrder", "display-order",
                                "x-display-order");
                        } catch (IOException e) {
                            log.warn("Failed to parse SKILL.md for skill {}/{}", agentId, dirName, e);
                        }
                    }
                    skills.add(skill);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list skills for {}", agentId, e);
        }
        return skills;
    }

    private void putFrontmatterAlias(Map<String, String> target, Map<String, String> frontmatter, String targetKey,
        String... aliases) {
        for (String alias : aliases) {
            String value = frontmatter.get(alias);
            if (value != null && !value.isBlank()) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    /**
     * Parse YAML frontmatter (between --- delimiters) from a Markdown file.
     *
     * @param mdPath path to the markdown file to parse
     * @return map of frontmatter key-value pairs, empty if no valid frontmatter found
     * @throws IOException if an I/O error occurs reading the markdown file
     */
    private Map<String, String> parseMarkdownFrontmatter(Path mdPath) throws IOException {
        Map<String, String> result = new HashMap<>();
        String content = Files.readString(mdPath);
        if (!content.startsWith("---")) {
            return result;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex < 0) {
            return result;
        }
        String yamlBlock = content.substring(3, endIndex).trim();
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        Object parsed;
        try {
            parsed = yaml.load(yamlBlock);
        } catch (YAMLException e) {
            log.warn("Invalid YAML frontmatter in {}: {}", mdPath, e.getMessage());
            return result;
        }
        if (parsed instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    result.put(e.getKey().toString(), e.getValue().toString());
                }
            }
        }
        return result;
    }

    /**
     * Read AGENTS.md content for an agent.
     *
     * @param agentId agent instance identifier
     * @return AGENTS.md file content, or empty string if not found
     */
    public String readAgentsMd(String agentId) {
        Path mdPath = getAgentsDir().resolve(agentId).resolve("AGENTS.md");
        if (!Files.exists(mdPath)) {
            return "";
        }
        try {
            return Files.readString(mdPath);
        } catch (IOException e) {
            log.error("Failed to read AGENTS.md for {}", agentId, e);
            return "";
        }
    }

    /**
     * Write AGENTS.md content for an agent.
     *
     * @param agentId agent instance identifier
     * @param content markdown content to write
     */
    public void writeAgentsMd(String agentId, String content) {
        Path mdPath = getAgentsDir().resolve(agentId).resolve("AGENTS.md");
        try {
            Files.writeString(mdPath, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write AGENTS.md for agent: " + agentId, e);
        }
    }

    // ── Memory file management ──────────────────────────────────────────

    // 100KB
    private static final int MAX_MEMORY_CONTENT_SIZE = 100 * 1024;

    private Path getGooseMemoryDir(String agentId) {
        return getAgentConfigDir(agentId).resolve("goose").resolve("memory");
    }

    /**
     * List all memory files (*.txt) for an agent, returning category name + content.
     *
     * @param agentId agent instance identifier
     * @return list of memory file maps, each containing category and content keys
     */
    public List<Map<String, String>> listMemoryFiles(String agentId) {
        Path memoryDir = getGooseMemoryDir(agentId);
        List<Map<String, String>> files = new ArrayList<>();
        if (!Files.isDirectory(memoryDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir, "*.txt")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    String fileName = entry.getFileName().toString();
                    // strip .txt
                    String category = fileName.substring(0, fileName.length() - 4);
                    Map<String, String> file = new HashMap<>();
                    file.put("category", category);
                    try {
                        file.put("content", Files.readString(entry));
                    } catch (IOException e) {
                        log.warn("Failed to read memory file {}/{}", agentId, fileName, e);
                        file.put("content", "");
                    }
                    files.add(file);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list memory files for {}", agentId, e);
        }
        return files;
    }

    /**
     * Read a single memory file content.
     *
     * @param agentId agent instance identifier
     * @param category memory file category name (without .txt extension)
     * @return file content string, or {@code null} if not found or unreadable
     */
    public String readMemoryFile(String agentId, String category) {
        Path filePath = getGooseMemoryDir(agentId).resolve(category + ".txt");
        try {
            return Files.readString(filePath);
        } catch (java.nio.file.NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            log.error("Failed to read memory file {}/{}", agentId, category, e);
            return null;
        }
    }

    /**
     * Write (create/update) a memory file. Creates the memory directory if needed.
     *
     * @param agentId agent instance identifier
     * @param category memory file category name (without .txt extension)
     * @param content text content to write; must not exceed 100KB
     */
    public void writeMemoryFile(String agentId, String category, String content) {
        if (content != null
            && content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_MEMORY_CONTENT_SIZE) {
            throw new IllegalArgumentException("Memory file content exceeds maximum size of 100KB");
        }
        Path memoryDir = getGooseMemoryDir(agentId);
        try {
            Files.createDirectories(memoryDir);
            Files.writeString(memoryDir.resolve(category + ".txt"), content != null ? content : "");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory file for agent: " + agentId, e);
        }
    }

    /**
     * Delete a memory file.
     *
     * @param agentId agent instance identifier
     * @param category memory file category name (without .txt extension)
     */
    public void deleteMemoryFile(String agentId, String category) {
        Path filePath = getGooseMemoryDir(agentId).resolve(category + ".txt");
        try {
            Files.delete(filePath);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new IllegalArgumentException("Memory file '" + category + "' not found");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete memory file for agent: " + agentId, e);
        }
    }

    /**
     * Reads MCP settings for a given agent and MCP name.
     *
     * @param agentId agent instance identifier
     * @param mcpName MCP extension name
     * @return parsed settings map, or {@code null} if not found or invalid
     */
    public Map<String, Object> readMcpSettings(String agentId, String mcpName) {
        if (KNOWLEDGE_SERVICE_MCP.equals(mcpName)) {
            return readKnowledgeServiceScopeFromConfig(agentId);
        }
        if (KNOWLEDGE_CLI_MCP.equals(mcpName)) {
            return readKnowledgeCliScopeFromConfig(agentId);
        }
        Path settingsPath = getAgentConfigDir(agentId).resolve("mcp").resolve(mcpName).resolve("settings.json");
        if (!Files.exists(settingsPath)) {
            return null;
        }
        try {
            String content = Files.readString(settingsPath);
            if (content == null || content.isBlank()) {
                return null;
            }
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(content);
            if (parsed instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) rawMap;
                return cast;
            }
            return null;
        } catch (IOException | YAMLException | IllegalArgumentException e) {
            log.warn("Failed to parse MCP settings for {}/{}: {}", agentId, mcpName, e.getMessage());
            return null;
        }
    }

    /**
     * Writes MCP settings for a given agent and MCP name.
     *
     * @param agentId agent instance identifier
     * @param mcpName MCP extension name
     * @param settings settings map to persist
     */
    public void writeMcpSettings(String agentId, String mcpName, Map<String, Object> settings) {
        try {
            if (KNOWLEDGE_SERVICE_MCP.equals(mcpName)) {
                writeKnowledgeServiceScopeToConfig(agentId, settings);
                invalidateCache(agentId);
                return;
            }
            if (KNOWLEDGE_CLI_MCP.equals(mcpName)) {
                writeKnowledgeCliScopeToConfig(agentId, settings);
                invalidateCache(agentId);
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write MCP settings for " + agentId + "/" + mcpName, e);
        }
        Path mcpDir = getAgentConfigDir(agentId).resolve("mcp").resolve(mcpName);
        if (!Files.isDirectory(mcpDir)) {
            throw new IllegalArgumentException("MCP '" + mcpName + "' not found for agent '" + agentId + "'");
        }
        Path settingsPath = mcpDir.resolve("settings.json");
        try {
            Files.createDirectories(mcpDir);
            Yaml yaml = createBlockYaml();
            Files.writeString(settingsPath, yaml.dump(settings));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write MCP settings for " + agentId + "/" + mcpName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readKnowledgeServiceScopeFromConfig(String agentId) {
        Map<String, Object> config = loadAgentConfigYaml(agentId);
        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> extensions)) {
            return null;
        }
        Object extensionObj = extensions.get(KNOWLEDGE_SERVICE_MCP);
        if (!(extensionObj instanceof Map<?, ?> extension)) {
            return null;
        }
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (!(opsfactoryObj instanceof Map<?, ?> opsfactory)) {
            return null;
        }
        Object knowledgeScopeObj = opsfactory.get("knowledgeScope");
        if (!(knowledgeScopeObj instanceof Map<?, ?> knowledgeScope)) {
            return null;
        }
        Object sourceId = knowledgeScope.get("sourceId");
        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", sourceId instanceof String source ? source : null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void writeKnowledgeServiceScopeToConfig(String agentId, Map<String, Object> settings) throws IOException {
        Path configPath = getAgentConfigDir(agentId).resolve("config.yaml");
        Map<String, Object> config = YamlLoader.load(configPath);

        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> rawExtensions)) {
            throw new IllegalArgumentException("Agent config for '" + agentId + "' does not contain extensions");
        }
        Map<String, Object> extensions = (Map<String, Object>) rawExtensions;
        Object extensionObj = extensions.get(KNOWLEDGE_SERVICE_MCP);
        if (!(extensionObj instanceof Map<?, ?> rawExtension)) {
            throw new IllegalArgumentException("MCP 'knowledge-service' not found for agent '" + agentId + "'");
        }
        Map<String, Object> extension = (Map<String, Object>) rawExtension;

        Map<String, Object> opsfactory;
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (opsfactoryObj instanceof Map<?, ?> rawOpsfactory) {
            opsfactory = (Map<String, Object>) rawOpsfactory;
        } else {
            opsfactory = new HashMap<>();
            extension.put("x-opsfactory", opsfactory);
        }

        Map<String, Object> knowledgeScope;
        Object knowledgeScopeObj = opsfactory.get("knowledgeScope");
        if (knowledgeScopeObj instanceof Map<?, ?> rawKnowledgeScope) {
            knowledgeScope = (Map<String, Object>) rawKnowledgeScope;
        } else {
            knowledgeScope = new HashMap<>();
            opsfactory.put("knowledgeScope", knowledgeScope);
        }

        Object sourceIdObj = settings != null ? settings.get("sourceId") : null;
        String sourceId = sourceIdObj instanceof String s && !s.isBlank() ? s.trim() : null;
        knowledgeScope.put("sourceId", sourceId);

        Yaml yaml = createBlockYaml();
        Files.writeString(configPath, yaml.dump(config));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readKnowledgeCliScopeFromConfig(String agentId) {
        Map<String, Object> config = loadAgentConfigYaml(agentId);
        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> extensions)) {
            return null;
        }
        Object extensionObj = extensions.get(KNOWLEDGE_CLI_MCP);
        if (!(extensionObj instanceof Map<?, ?> extension)) {
            return null;
        }
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (!(opsfactoryObj instanceof Map<?, ?> opsfactory)) {
            return null;
        }
        Object scopeObj = opsfactory.get("scope");
        if (!(scopeObj instanceof Map<?, ?> scope)) {
            return null;
        }

        Object sourceIdObj = scope.get("sourceId");
        Object rootDirObj = scope.get("rootDir");
        String sourceId = sourceIdObj instanceof String source ? source : null;
        String rootDir = rootDirObj instanceof String root ? root : null;

        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", sourceId);
        result.put("rootDir", rootDir);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void writeKnowledgeCliScopeToConfig(String agentId, Map<String, Object> settings) throws IOException {
        Path configPath = getAgentConfigDir(agentId).resolve("config.yaml");
        Map<String, Object> config = YamlLoader.load(configPath);

        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> rawExtensions)) {
            throw new IllegalArgumentException("Agent config for '" + agentId + "' does not contain extensions");
        }
        Map<String, Object> extensions = (Map<String, Object>) rawExtensions;
        Object extensionObj = extensions.get(KNOWLEDGE_CLI_MCP);
        if (!(extensionObj instanceof Map<?, ?> rawExtension)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' not found for agent '" + agentId + "'");
        }
        Map<String, Object> extension = (Map<String, Object>) rawExtension;

        Map<String, Object> opsfactory;
        Object opsfactoryObj = extension.get("x-opsfactory");
        if (opsfactoryObj instanceof Map<?, ?> rawOpsfactory) {
            opsfactory = (Map<String, Object>) rawOpsfactory;
        } else {
            opsfactory = new HashMap<>();
            extension.put("x-opsfactory", opsfactory);
        }

        Map<String, Object> scope;
        Object scopeObj = opsfactory.get("scope");
        if (scopeObj instanceof Map<?, ?> rawScope) {
            scope = (Map<String, Object>) rawScope;
        } else {
            scope = new HashMap<>();
            opsfactory.put("scope", scope);
        }

        Object sourceIdObj = settings != null ? settings.get("sourceId") : null;
        String sourceId = sourceIdObj instanceof String s && !s.isBlank() ? s.trim() : null;

        if (sourceId == null) {
            scope.put("sourceId", null);
            scope.put("rootDir", DEFAULT_KNOWLEDGE_CLI_ROOT_DIR);
        } else {
            validateKnowledgeSourceId(sourceId);
            Path artifactsDir = resolveKnowledgeArtifactsRoot().resolve(sourceId).normalize();
            scope.put("sourceId", sourceId);
            scope.put("rootDir", relativizeForAgentConfig(agentId, artifactsDir));
        }

        Yaml yaml = createBlockYaml();
        Files.writeString(configPath, yaml.dump(config));
    }

    private void validateKnowledgeSourceId(String sourceId) {
        if (!sourceId.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid knowledge sourceId '" + sourceId + "'");
        }
    }

    private Path resolveKnowledgeArtifactsRoot() {
        String configuredRoot = properties.getKnowledge() != null ? properties.getKnowledge().getArtifactsRoot() : null;
        String artifactsRoot = configuredRoot != null && !configuredRoot.isBlank() ? configuredRoot.trim()
            : "../knowledge-service/data/artifacts";
        Path rootPath = Path.of(artifactsRoot);
        return rootPath.isAbsolute() ? rootPath.normalize() : gatewayRoot.resolve(rootPath).normalize();
    }

    private String relativizeForAgentConfig(String agentId, Path targetPath) {
        Path configDir = getAgentConfigDir(agentId).normalize();
        Path projectRoot =
            gatewayRoot.getParent() != null ? gatewayRoot.getParent().normalize() : properties.getProjectRootPath();
        Path normalizedTarget = targetPath.normalize();

        if (normalizedTarget.startsWith(projectRoot)) {
            return configDir.relativize(normalizedTarget).toString().replace('\\', '/');
        }
        return normalizedTarget.toString();
    }

    private Yaml createBlockYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    /**
     * Create a new agent: directory structure, config files, registry update.
     *
     * @param id unique agent identifier (lowercase alphanumeric with hyphens)
     * @param name display name for the agent
     * @return map containing id, name, provider, and model of the created agent
     */
    public Map<String, Object> createAgent(String id, String name) {
        // Validate ID format
        if (!id.matches("^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?$") || id.length() < 2) {
            throw new IllegalArgumentException(
                "Agent ID must be at least 2 chars, lowercase letters, numbers, and hyphens only "
                    + "(no leading/trailing hyphens)");
        }

        // Check duplicate ID
        if (findAgent(id) != null) {
            throw new IllegalArgumentException("Agent with ID '" + id + "' already exists");
        }

        // Check duplicate name
        for (AgentRegistryEntry entry : registry) {
            if (entry.name().equals(name)) {
                throw new IllegalArgumentException("Agent with name '" + name + "' already exists");
            }
        }

        try {
            // Create directory structure
            Path agentDir = getAgentsDir().resolve(id);
            Path configDir = agentDir.resolve("config");
            Files.createDirectories(configDir.resolve("skills"));

            // Copy config template from universal-agent or use defaults
            Path templateConfig = getAgentsDir().resolve("universal-agent").resolve("config").resolve("config.yaml");
            Path targetConfig = configDir.resolve("config.yaml");
            if (Files.exists(templateConfig)) {
                Files.copy(templateConfig, targetConfig);
            } else {
                Files.writeString(targetConfig, "GOOSE_PROVIDER: openai\nGOOSE_MODEL: gpt-4o\n");
            }

            // Write empty secrets.yaml
            Files.writeString(configDir.resolve("secrets.yaml"), "");

            // Write AGENTS.md
            Files.writeString(agentDir.resolve("AGENTS.md"), "# " + name + "\n");

            // Update config.yaml on disk
            updateAgentsYaml(id, name, false);

            // Update in-memory registry and invalidate cache
            registry.add(new AgentRegistryEntry(id, name));
            invalidateCache(id);

            // Read provider/model from created config
            Map<String, Object> config = YamlLoader.load(targetConfig);
            return Map.of("id", id, "name", name, "provider", config.getOrDefault("GOOSE_PROVIDER", ""), "model",
                config.getOrDefault("GOOSE_MODEL", ""));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create agent: " + id, e);
        }
    }

    /**
     * Delete an agent: stop instances, remove files, update registry.
     *
     * @param id agent identifier to delete
     */
    public void deleteAgent(String id) {
        AgentRegistryEntry entry = findAgent(id);
        if (entry == null) {
            throw new IllegalArgumentException("Agent '" + id + "' not found");
        }

        try {
            // Remove agent directory
            Path agentDir = getAgentsDir().resolve(id);
            if (Files.exists(agentDir)) {
                FileUtil.deleteRecursively(agentDir);
            }

            // Update config.yaml
            updateAgentsYaml(id, null, true);

            // Remove from in-memory registry and invalidate cache
            registry.removeIf(e -> e.id().equals(id));
            invalidateCache(id);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete agent: " + id, e);
        }
    }

    private void updateAgentsYaml(String id, String name, boolean remove) throws IOException {
        Path configYaml = getGatewayRoot().resolve("config.yaml");
        Map<String, Object> data = YamlLoader.load(configYaml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agents = (List<Map<String, Object>>) data.get("agents");
        if (agents == null) {
            agents = new ArrayList<>();
        }

        if (remove) {
            agents.removeIf(a -> id.equals(a.get("id")));
        } else {
            Map<String, Object> newAgent = new HashMap<>();
            newAgent.put("id", id);
            newAgent.put("name", name);
            agents.add(newAgent);
        }

        data.put("agents", agents);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        Files.writeString(configYaml, yaml.dump(data));
    }

    /**
     * Returns the base directory containing all agent configurations.
     *
     * @return path to the agents directory
     */
    public Path getAgentsDir() {
        return gatewayRoot.resolve(properties.getPaths().getAgentsDir());
    }

    /**
     * Returns the base directory containing user-specific data.
     *
     * @return path to the users directory
     */
    public Path getUsersDir() {
        return gatewayRoot.resolve(properties.getPaths().getUsersDir());
    }

    /**
     * Resolves the knowledge CLI root directory for a given agent from its configuration.
     *
     * @param agentId agent instance identifier
     * @return resolved absolute or normalized relative path to the knowledge CLI root directory
     */
    @SuppressWarnings("unchecked")
    public Path getKnowledgeCliRootDir(String agentId) {
        Map<String, Object> config = loadAgentConfigYaml(agentId);
        Object extensionsObj = config.get("extensions");
        if (!(extensionsObj instanceof Map<?, ?> extensions)) {
            throw new IllegalArgumentException("Agent config for '" + agentId + "' does not contain extensions");
        }

        Object extensionObj = extensions.get(KNOWLEDGE_CLI_MCP);
        if (!(extensionObj instanceof Map<?, ?> extension)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' not found for agent '" + agentId + "'");
        }

        Object opsfactoryObj = extension.get("x-opsfactory");
        if (!(opsfactoryObj instanceof Map<?, ?> opsfactory)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' does not contain x-opsfactory scope");
        }

        Object scopeObj = opsfactory.get("scope");
        if (!(scopeObj instanceof Map<?, ?> scope)) {
            throw new IllegalArgumentException("MCP 'knowledge-cli' does not contain scope");
        }

        Object rootDirObj = scope.get("rootDir");
        String configuredRoot =
            rootDirObj instanceof String s && !s.isBlank() ? s.trim() : DEFAULT_KNOWLEDGE_CLI_ROOT_DIR;
        Path configDir = getAgentConfigDir(agentId);
        return Path.of(configuredRoot).isAbsolute() ? Path.of(configuredRoot).normalize()
            : configDir.resolve(configuredRoot).normalize();
    }

    /**
     * Returns the per-user agent directory path.
     *
     * @param userId user identifier
     * @param agentId agent instance identifier
     * @return path to the user-specific agent directory
     */
    public Path getUserAgentDir(String userId, String agentId) {
        return getUsersDir().resolve(userId).resolve("agents").resolve(agentId);
    }

    /**
     * Returns the config directory for the given agent.
     *
     * @param agentId agent instance identifier
     * @return path to the agent configuration directory
     */
    public Path getAgentConfigDir(String agentId) {
        return getAgentsDir().resolve(agentId).resolve("config");
    }

    /**
     * Returns the resolved gateway root path.
     *
     * @return the gateway root directory path
     */
    public Path getGatewayRoot() {
        return gatewayRoot;
    }
}
