/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelBinding;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectionConfig;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelEvent;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelInstance;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelSummary;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelUpsertRequest;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelVerificationResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

/**
 * Central service for channel CRUD, configuration persistence, runtime state merging, and event recording.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ChannelConfigService {
    private static final Logger log = LoggerFactory.getLogger(ChannelConfigService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private static final int MAX_EVENTS = 200;

    private static final List<String> SUPPORTED_TYPES = List.of("whatsapp", "wechat");

    private final GatewayProperties properties;

    private final AgentConfigService agentConfigService;

    private final ChannelRuntimeStorageService runtimeStorageService;

    private Path channelsDir;

    /**
     * Creates the channel config service instance.
     */
    public ChannelConfigService(GatewayProperties properties, AgentConfigService agentConfigService,
        ChannelRuntimeStorageService runtimeStorageService) {
        this.properties = properties;
        this.agentConfigService = agentConfigService;
        this.runtimeStorageService = runtimeStorageService;
    }

    /**
     * Initializes the channels storage directory on application startup.
     */
    @PostConstruct
    public void init() {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.channelsDir = gatewayRoot.resolve("channels");

        try {
            Files.createDirectories(channelsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize channels storage", e);
        }
    }

    /**
     * Lists all channels with summary information for the default owner user.
     *
     * @return the result
     */
    public List<ChannelSummary> listChannels() {
        return listChannels("admin");
    }

    /**
     * Lists all channels with summary information for the given owner user.
     *
     * @param ownerUserId owner user id
     * @return the result
     */
    public List<ChannelSummary> listChannels(String ownerUserId) {
        List<ChannelInstance> channels = readInstances();
        List<ChannelBinding> bindings = readBindings(ownerUserId);

        return channels.stream()
            .map(channel -> toSummary(applyRuntimeState(withRuntimeUser(channel, ownerUserId)), bindings))
            .sorted(Comparator.comparing(ChannelSummary::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Gets the full detail of a channel by its ID for the default owner user.
     *
     * @param channelId channel identifier
     * @return the full detail of a channel by its ID for the default owner user
     */
    public ChannelDetail getChannel(String channelId) {
        return getChannel(channelId, "admin");
    }

    /**
     * Gets the full detail of a channel by its ID and owner user.
     *
     * @param channelId channel identifier
     * @param ownerUserId owner user id
     * @return the full detail of a channel by its ID and owner user
     */
    public ChannelDetail getChannel(String channelId, String ownerUserId) {
        ChannelInstance channel = findChannel(channelId);
        if (channel == null) {
            return null;
        }
        ChannelInstance effectiveChannel = applyRuntimeState(withRuntimeUser(channel, ownerUserId));

        List<ChannelBinding> bindings = readBindings(effectiveChannel).stream()
            .filter(binding -> channelId.equals(binding.channelId()))
            .sorted(
                Comparator.comparing(ChannelBinding::lastInboundAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        List<ChannelEvent> events = readEvents(effectiveChannel).stream()
            .filter(event -> channelId.equals(event.channelId()))
            .sorted(Comparator.comparing(ChannelEvent::createdAt, Comparator.reverseOrder()))
            .limit(20)
            .toList();

        return new ChannelDetail(effectiveChannel.id(), effectiveChannel.name(), effectiveChannel.type(),
            effectiveChannel.enabled(), effectiveChannel.defaultAgentId(), effectiveChannel.ownerUserId(),
            effectiveChannel.createdAt(), effectiveChannel.updatedAt(),
            usesWebhook(effectiveChannel.type()) ? webhookPath(effectiveChannel) : "", effectiveChannel.config(),
            verifyChannel(effectiveChannel), bindings, events);
    }

    /**
     * Lists all runtime channels of the given type across all owner users.
     *
     * @param type type filter
     * @return the result
     */
    public List<ChannelDetail> listRuntimeChannels(String type) {
        String normalizedType = normalizeType(type);
        return readInstances().stream()
            .filter(channel -> normalizedType.equals(channel.type()))
            .flatMap(channel -> runtimeStorageService.listRuntimeRefs(channel.type(), channel.id())
                .stream()
                .map(ref -> getChannel(channel.id(), ref.ownerUserId()))
                .filter(detail -> detail != null))
            .toList();
    }

    /**
     * Creates a new channel from the given upsert request.
     *
     * @param request HTTP request
     * @param ownerUserId owner user id
     * @return the result
     */
    public ChannelDetail createChannel(ChannelUpsertRequest request, String ownerUserId) {
        validateCreateRequest(request);

        List<ChannelInstance> channels = new ArrayList<>(readInstances());
        if (channels.stream().anyMatch(channel -> channel.id().equals(request.id()))) {
            throw new IllegalArgumentException("Channel '" + request.id() + "' already exists");
        }

        String now = Instant.now().toString();
        ChannelInstance created =
            new ChannelInstance(request.id().trim(), request.name().trim(), normalizeType(request.type()),
                request.enabled() == null || request.enabled(), request.defaultAgentId().trim(),
                normalizeOwnerUserId(ownerUserId), now, now, normalizeConfig(request.type(), request.config()));
        channels.add(created);
        writeChannelConfig(created);
        runtimeStorageService.initializeRuntime(created);
        appendEvent(created.id(), created.ownerUserId(), "info", "channel.created", "Channel created");
        return getChannel(created.id(), created.ownerUserId());
    }

    /**
     * Updates an existing channel using the default owner user ID.
     *
     * @param channelId an existing channel using the default owner user ID
     * @param request an existing channel using the default owner user ID
     * @return the result
     */
    public ChannelDetail updateChannel(String channelId, ChannelUpsertRequest request) {
        return updateChannel(channelId, request, "admin");
    }

    /**
     * Updates an existing channel with the given upsert request.
     *
     * @param channelId an existing channel with the given upsert request
     * @param request an existing channel with the given upsert request
     * @param ownerUserId an existing channel with the given upsert request
     * @return the result
     */
    public ChannelDetail updateChannel(String channelId, ChannelUpsertRequest request, String ownerUserId) {
        ChannelInstance existing = findChannel(channelId);
        if (existing == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }

        validateUpdateRequest(request);
        if (!isBlank(request.type()) && !normalizeType(request.type()).equals(existing.type())) {
            throw new IllegalArgumentException("Channel type cannot be changed after creation");
        }

        ChannelInstance updated = new ChannelInstance(existing.id(), normalizeName(request.name(), existing.name()),
            normalizeType(request.type() != null ? request.type() : existing.type()),
            request.enabled() != null ? request.enabled() : existing.enabled(),
            normalizeAgentId(request.defaultAgentId(), existing.defaultAgentId()), normalizeOwnerUserId(ownerUserId),
            existing.createdAt(), Instant.now().toString(),
            mergeConfig(existing.type(), existing.config(), request.config()));

        writeChannelConfig(updated);
        appendEvent(channelId, ownerUserId, "info", "channel.updated", "Channel updated");
        return getChannel(channelId, ownerUserId);
    }

    /**
     * Enables or disables a channel using the default owner user ID.
     *
     * @param channelId enables or disables a channel using the default owner user ID
     * @param enabled enables or disables a channel using the default owner user ID
     * @return the enables or disables a channel using the default owner user ID
     */
    public ChannelDetail setEnabled(String channelId, boolean enabled) {
        return setEnabled(channelId, enabled, "admin");
    }

    /**
     * Enables or disables a channel for the given owner user.
     *
     * @param channelId enables or disables a channel for the given owner user
     * @param enabled enables or disables a channel for the given owner user
     * @param ownerUserId enables or disables a channel for the given owner user
     * @return the enables or disables a channel for the given owner user
     */
    public ChannelDetail setEnabled(String channelId, boolean enabled, String ownerUserId) {
        ChannelInstance existing = findChannel(channelId);
        if (existing == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }

        ChannelInstance updated =
            new ChannelInstance(existing.id(), existing.name(), existing.type(), enabled, existing.defaultAgentId(),
                normalizeOwnerUserId(ownerUserId), existing.createdAt(), Instant.now().toString(), existing.config());

        writeChannelConfig(updated);
        appendEvent(channelId, ownerUserId, "info", enabled ? "channel.enabled" : "channel.disabled",
            enabled ? "Channel enabled" : "Channel disabled");
        return getChannel(channelId, ownerUserId);
    }

    /**
     * Deletes a channel and all its associated runtime data.
     *
     * @param channelId channel identifier
     */
    public void deleteChannel(String channelId) {
        ChannelInstance existing = findChannel(channelId);
        if (existing == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }

        runtimeStorageService.deleteAllRuntimes(existing.type(), existing.id());
        deleteDirectory(channelDir(existing.type(), channelId));
    }

    /**
     * Lists bindings for a channel using the default owner user ID.
     *
     * @param channelId channel identifier
     * @return the result
     */
    public List<ChannelBinding> listBindings(String channelId) {
        return listBindings(channelId, "admin");
    }

    /**
     * Lists bindings for a channel and owner user, sorted by last inbound timestamp descending.
     *
     * @param channelId channel identifier
     * @param ownerUserId owner user id
     * @return the result
     */
    public List<ChannelBinding> listBindings(String channelId, String ownerUserId) {
        if (findChannel(channelId) == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        return readBindings(withRuntimeUser(findChannel(channelId), ownerUserId)).stream()
            .filter(binding -> channelId.equals(binding.channelId()))
            .sorted(
                Comparator.comparing(ChannelBinding::lastInboundAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    /**
     * Lists events for a channel using the default owner user ID.
     *
     * @param channelId channel identifier
     * @return the result
     */
    public List<ChannelEvent> listEvents(String channelId) {
        return listEvents(channelId, "admin");
    }

    /**
     * Lists events for a channel and owner user, sorted by creation time descending.
     *
     * @param channelId channel identifier
     * @param ownerUserId owner user id
     * @return the result
     */
    public List<ChannelEvent> listEvents(String channelId, String ownerUserId) {
        if (findChannel(channelId) == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        return readEvents(withRuntimeUser(findChannel(channelId), ownerUserId)).stream()
            .filter(event -> channelId.equals(event.channelId()))
            .sorted(Comparator.comparing(ChannelEvent::createdAt, Comparator.reverseOrder()))
            .limit(50)
            .toList();
    }

    /**
     * Verifies the configuration of a channel using the default owner user ID.
     *
     * @param channelId verifies the configuration of a channel using the default owner user ID
     * @return the verifies the configuration of a channel using the default owner user ID
     */
    public ChannelVerificationResult verifyChannel(String channelId) {
        return verifyChannel(channelId, "admin");
    }

    /**
     * Verifies the configuration of a channel and records the result as an event.
     *
     * @param channelId verifies the configuration of a channel and records the result as an event
     * @param ownerUserId verifies the configuration of a channel and records the result as an event
     * @return the verifies the configuration of a channel and records the result as an event
     */
    public ChannelVerificationResult verifyChannel(String channelId, String ownerUserId) {
        ChannelInstance existing = findChannel(channelId);
        if (existing == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        ChannelVerificationResult result = verifyChannel(applyRuntimeState(withRuntimeUser(existing, ownerUserId)));
        appendEvent(channelId, ownerUserId, result.ok() ? "info" : "warning", "channel.verified",
            result.ok() ? "Channel configuration verified" : String.join("; ", result.issues()));
        return result;
    }

    /**
     * Records an audit event for a channel using the default owner user ID.
     *
     * @param channelId records an audit event for a channel using the default owner user ID
     * @param level records an audit event for a channel using the default owner user ID
     * @param type records an audit event for a channel using the default owner user ID
     * @param summary records an audit event for a channel using the default owner user ID
     */
    public void recordEvent(String channelId, String level, String type, String summary) {
        recordEvent(channelId, "admin", level, type, summary);
    }

    /**
     * Records an audit event for a channel with the specified level, type, and summary.
     *
     * @param channelId records an audit event for a channel with the specified level, type, and summary
     * @param ownerUserId records an audit event for a channel with the specified level, type, and summary
     * @param level records an audit event for a channel with the specified level, type, and summary
     * @param type records an audit event for a channel with the specified level, type, and summary
     * @param summary records an audit event for a channel with the specified level, type, and summary
     */
    public void recordEvent(String channelId, String ownerUserId, String level, String type, String summary) {
        appendEvent(channelId, ownerUserId, level, type, summary);
    }

    /**
     * Resets the runtime login state of a channel using the default owner user ID.
     *
     * @param channelId resets the runtime login state of a channel using the default owner user ID
     * @return the resets the runtime login state of a channel using the default owner user ID
     */
    public ChannelDetail resetChannelRuntimeState(String channelId) {
        return resetChannelRuntimeState(channelId, "admin");
    }

    /**
     * Resets the runtime login state of a channel, clearing connection data and writing a disconnected state file.
     *
     * @param channelId resets the runtime login state of a channel, clearing connection data and writing a disconnected
     *        state file
     * @param ownerUserId resets the runtime login state of a channel, clearing connection data and writing a
     *        disconnected state file
     * @return the resets the runtime login state of a channel, clearing connection data and writing a disconnected
     *         state file
     */
    public ChannelDetail resetChannelRuntimeState(String channelId, String ownerUserId) {
        ChannelInstance existing = findChannel(channelId);
        if (existing == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        existing = withRuntimeUser(existing, ownerUserId);
        Map<String, Object> runtimeState = new LinkedHashMap<>();
        runtimeState.put("channelId", existing.id());
        runtimeState.put("status", "disconnected");
        runtimeState.put("message",
            "wechat".equals(existing.type()) ? "WeChat login required" : "WhatsApp Web login required");
        runtimeState.put("authStateDir", normalizeConfig(existing.type(), existing.config()).authStateDir());
        runtimeState.put("lastConnectedAt", "");
        runtimeState.put("lastDisconnectedAt", Instant.now().toString());
        runtimeState.put("lastError", "");
        runtimeState.put("qrCodeDataUrl", null);
        writeJson(runtimeStorageService.runtimeDirectory(existing).resolve("login-state.json"), runtimeState);
        return getChannel(channelId, ownerUserId);
    }

    private ChannelSummary toSummary(ChannelInstance channel, List<ChannelBinding> allBindings) {
        List<ChannelBinding> bindings =
            allBindings.stream().filter(binding -> channel.id().equals(binding.channelId())).toList();
        ChannelVerificationResult verification = verifyChannel(channel);
        String status;
        if (!channel.enabled()) {
            status = "DISABLED";
        } else if ("connected".equals(channel.config().loginStatus())) {
            status = "ACTIVE";
        } else if ("pending".equals(channel.config().loginStatus())) {
            status = "PENDING_LOGIN";
        } else if ("error".equals(channel.config().loginStatus())) {
            status = "ERROR";
        } else if (verification.ok()) {
            status = "ACTIVE";
        } else {
            status = "LOGIN_REQUIRED";
        }

        String lastInboundAt = bindings.stream()
            .map(ChannelBinding::lastInboundAt)
            .filter(value -> value != null && !value.isBlank())
            .max(String::compareTo)
            .orElse(null);
        String lastOutboundAt = bindings.stream()
            .map(ChannelBinding::lastOutboundAt)
            .filter(value -> value != null && !value.isBlank())
            .max(String::compareTo)
            .orElse(null);

        return new ChannelSummary(channel.id(), channel.name(), channel.type(), channel.enabled(),
            channel.defaultAgentId(), channel.ownerUserId(), status, lastInboundAt, lastOutboundAt, bindings.size());
    }

    private ChannelVerificationResult verifyChannel(ChannelInstance channel) {
        List<String> issues = new ArrayList<>();
        if (isBlank(channel.name())) {
            issues.add("Channel name is required");
        }
        AgentRegistryEntry agent = agentConfigService.findAgent(channel.defaultAgentId());
        if (agent == null) {
            issues.add("Default agent '" + channel.defaultAgentId() + "' not found");
        }

        ChannelConnectionConfig config = channel.config();
        if (config == null) {
            issues.add("Channel config is required");
        } else {
            if (!isConfiguredValue(config.authStateDir()))
                issues.add("authStateDir is required");
            if ("error".equals(config.loginStatus()) && isConfiguredValue(config.lastError())) {
                issues.add(config.lastError());
            } else if (channel.enabled() && !"connected".equals(config.loginStatus())) {
                issues.add("wechat".equals(channel.type()) ? "WeChat login required" : "WhatsApp Web login required");
            }
        }

        return new ChannelVerificationResult(issues.isEmpty(), issues);
    }

    private ChannelInstance findChannel(String channelId) {
        return readInstances().stream().filter(channel -> channel.id().equals(channelId)).findFirst().orElse(null);
    }

    private void validateCreateRequest(ChannelUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (isBlank(request.id())) {
            throw new IllegalArgumentException("Channel ID is required");
        }
        String trimmedId = request.id().trim();
        if (!CHANNEL_ID_PATTERN.matcher(trimmedId).matches()) {
            throw new IllegalArgumentException("Channel ID must contain only lowercase letters, numbers, and hyphens");
        }
        validateUpdateRequest(request);
    }

    private void validateUpdateRequest(ChannelUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("Channel name is required");
        }
        if (isBlank(request.defaultAgentId())) {
            throw new IllegalArgumentException("Default agent is required");
        }
        if (agentConfigService.findAgent(request.defaultAgentId().trim()) == null) {
            throw new IllegalArgumentException("Default agent '" + request.defaultAgentId().trim() + "' not found");
        }
        String type = normalizeType(request.type());
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported channel type '" + type + "'");
        }
        if (request.config() != null && !isSafeRelativeAuthStateDir(request.config().authStateDir())) {
            throw new IllegalArgumentException("authStateDir must be relative to the channel runtime directory");
        }
    }

    private boolean isSafeRelativeAuthStateDir(String authStateDir) {
        if (authStateDir == null || authStateDir.isBlank()) {
            return true;
        }
        Path path = Path.of(authStateDir.trim());
        if (path.isAbsolute()) {
            return false;
        }
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    private String normalizeType(String type) {
        return isBlank(type) ? "whatsapp" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String maybeName, String fallback) {
        return isBlank(maybeName) ? fallback : maybeName.trim();
    }

    private String normalizeAgentId(String maybeAgentId, String fallback) {
        return isBlank(maybeAgentId) ? fallback : maybeAgentId.trim();
    }

    private String normalizeOwnerUserId(String ownerUserId) {
        String normalized = isBlank(ownerUserId) ? "admin" : ownerUserId.trim();
        if (normalized.contains("/") || normalized.contains("\\") || ".".equals(normalized)
            || "..".equals(normalized)) {
            throw new IllegalArgumentException("ownerUserId contains unsafe path characters");
        }
        return normalized;
    }

    private ChannelConnectionConfig normalizeConfig(String type, ChannelConnectionConfig config) {
        if (config == null) {
            return defaultConfig(type);
        }
        return new ChannelConnectionConfig(normalizeLoginStatus(config.loginStatus()),
            emptyIfNull(config.authStateDir()).isBlank() ? "auth" : config.authStateDir().trim(),
            emptyIfNull(config.lastConnectedAt()), emptyIfNull(config.lastDisconnectedAt()),
            emptyIfNull(config.lastError()), "whatsapp".equals(type) ? emptyIfNull(config.selfPhone()) : "",
            "wechat".equals(type) ? emptyIfNull(config.wechatId()) : "",
            "wechat".equals(type) ? emptyIfNull(config.displayName()) : "");
    }

    private ChannelConnectionConfig mergeConfig(String type, ChannelConnectionConfig existing,
        ChannelConnectionConfig updates) {
        ChannelConnectionConfig current = normalizeConfig(type, existing);
        if (updates == null) {
            return current;
        }
        return new ChannelConnectionConfig(normalizeLoginStatus(choose(updates.loginStatus(), current.loginStatus())),
            choose(updates.authStateDir(), current.authStateDir()),
            choose(updates.lastConnectedAt(), current.lastConnectedAt()),
            choose(updates.lastDisconnectedAt(), current.lastDisconnectedAt()),
            choose(updates.lastError(), current.lastError()),
            "whatsapp".equals(type) ? choose(updates.selfPhone(), current.selfPhone()) : "",
            "wechat".equals(type) ? choose(updates.wechatId(), current.wechatId()) : "",
            "wechat".equals(type) ? choose(updates.displayName(), current.displayName()) : "");
    }

    private String choose(String candidate, String fallback) {
        return candidate == null ? fallback : candidate;
    }

    private ChannelConnectionConfig defaultConfig(String type) {
        return new ChannelConnectionConfig("disconnected", "auth", "", "", "", "", "", "");
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isConfiguredValue(String value) {
        if (isBlank(value)) {
            return false;
        }
        return !value.trim().startsWith("TO" + "DO_");
    }

    private String normalizeLoginStatus(String loginStatus) {
        if (isBlank(loginStatus)) {
            return "disconnected";
        }
        return loginStatus.trim().toLowerCase(Locale.ROOT);
    }

    private void appendEvent(String channelId, String level, String type, String summary) {
        appendEvent(channelId, "admin", level, type, summary);
    }

    private void appendEvent(String channelId, String ownerUserId, String level, String type, String summary) {
        ChannelInstance channel = findChannel(channelId);
        if (channel == null) {
            return;
        }
        channel = withRuntimeUser(channel, ownerUserId);

        List<ChannelEvent> events = new ArrayList<>(readEvents(channel));
        events.add(
            new ChannelEvent(UUID.randomUUID().toString(), channelId, level, type, summary, Instant.now().toString()));
        if (events.size() > MAX_EVENTS) {
            events = events.subList(events.size() - MAX_EVENTS, events.size());
        }
        runtimeStorageService.initializeRuntime(channel);
        writeEvents(channel, events);
    }

    private ChannelInstance withRuntimeUser(ChannelInstance channel, String ownerUserId) {
        return new ChannelInstance(channel.id(), channel.name(), channel.type(), channel.enabled(),
            channel.defaultAgentId(), normalizeOwnerUserId(ownerUserId), channel.createdAt(), channel.updatedAt(),
            channel.config());
    }

    private List<ChannelInstance> readInstances() {
        if (!Files.isDirectory(channelsDir)) {
            return List.of();
        }

        List<ChannelInstance> channels = new ArrayList<>();
        try (DirectoryStream<Path> typeDirs = Files.newDirectoryStream(channelsDir)) {
            for (Path typeDir : typeDirs) {
                if (!Files.isDirectory(typeDir)) {
                    continue;
                }
                try (DirectoryStream<Path> instanceDirs = Files.newDirectoryStream(typeDir)) {
                    for (Path instanceDir : instanceDirs) {
                        if (!Files.isDirectory(instanceDir)) {
                            continue;
                        }
                        ChannelInstance channel = readChannelConfig(instanceDir);
                        if (channel != null) {
                            channels.add(channel);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read channel directories {}: {}", channelsDir, e.getMessage());
        }
        return channels;
    }

    private List<ChannelBinding> readBindings(String ownerUserId) {
        return readInstances().stream()
            .map(channel -> withRuntimeUser(channel, ownerUserId))
            .flatMap(channel -> readBindings(channel).stream())
            .toList();
    }

    private List<ChannelBinding> readBindings(ChannelInstance channel) {
        Map<String, Object> wrapper = readJson(runtimeStorageService.bindingsFile(channel));
        return MAPPER.convertValue(wrapper.getOrDefault("bindings", List.of()),
            new TypeReference<List<ChannelBinding>>() {});
    }

    private List<ChannelEvent> readEvents(String ownerUserId) {
        return readInstances().stream()
            .map(channel -> withRuntimeUser(channel, ownerUserId))
            .flatMap(channel -> readEvents(channel).stream())
            .toList();
    }

    private List<ChannelEvent> readEvents(ChannelInstance channel) {
        Map<String, Object> wrapper = readJson(runtimeStorageService.eventsFile(channel));
        return MAPPER.convertValue(wrapper.getOrDefault("events", List.of()),
            new TypeReference<List<ChannelEvent>>() {});
    }

    private void writeEvents(ChannelInstance channel, List<ChannelEvent> events) {
        writeJson(runtimeStorageService.eventsFile(channel), Map.of("events", events));
    }

    private ChannelInstance readChannelConfig(Path instanceDir) {
        try {
            String content = Files.readString(configFile(instanceDir), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = MAPPER.readValue(content, Map.class);
            ChannelInstance channel = deserializeChannelInstance(raw);
            if (!isValidConfigChannel(instanceDir, channel)) {
                return null;
            }
            return channel;
        } catch (IOException e) {
            log.warn("Failed to read channel config {}: {}", instanceDir, e.getMessage());
            return null;
        }
    }

    private boolean isValidConfigChannel(Path instanceDir, ChannelInstance channel) {
        if (channel == null || !CHANNEL_ID_PATTERN.matcher(channel.id()).matches()) {
            log.warn("Skipping invalid channel config {}: invalid id '{}'", instanceDir,
                channel == null ? "" : channel.id());
            return false;
        }
        Path directoryName = instanceDir.getFileName();
        if (directoryName != null && !channel.id().equals(directoryName.toString())) {
            log.warn("Skipping invalid channel config {}: id '{}' does not match directory", instanceDir, channel.id());
            return false;
        }
        if (!SUPPORTED_TYPES.contains(channel.type())) {
            log.warn("Skipping invalid channel config {}: unsupported type '{}'", instanceDir, channel.type());
            return false;
        }
        Path typeDirectory = instanceDir.getParent();
        Path typeName = typeDirectory == null ? null : typeDirectory.getFileName();
        if (typeName != null && !channel.type().equals(typeName.toString())) {
            log.warn("Skipping invalid channel config {}: type '{}' does not match directory", instanceDir,
                channel.type());
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private ChannelInstance deserializeChannelInstance(Map<String, Object> raw) {
        Map<String, Object> rawConfig =
            raw.get("config") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();

        return new ChannelInstance(emptyIfNull((String) raw.get("id")), emptyIfNull((String) raw.get("name")),
            normalizeType((String) raw.get("type")), raw.get("enabled") instanceof Boolean enabled && enabled,
            emptyIfNull((String) raw.get("defaultAgentId")), "admin", emptyIfNull((String) raw.get("createdAt")),
            emptyIfNull((String) raw.get("updatedAt")), deserializeChannelConfig((String) raw.get("type"), rawConfig));
    }

    private ChannelConnectionConfig deserializeChannelConfig(String type, Map<String, Object> rawConfig) {
        String normalizedType = normalizeType(type);
        if (rawConfig.containsKey("loginStatus") || rawConfig.containsKey("authStateDir")
            || rawConfig.containsKey("selfPhone") || rawConfig.containsKey("wechatId")
            || rawConfig.containsKey("displayName")) {
            return normalizeConfig(normalizedType, MAPPER.convertValue(rawConfig, ChannelConnectionConfig.class));
        }

        if ("whatsapp".equals(normalizedType) && !rawConfig.isEmpty()) {
            return new ChannelConnectionConfig("disconnected", "auth", "", "",
                "Legacy WhatsApp Cloud API config detected. Switch this channel to WhatsApp Web login.", "", "", "");
        }
        return defaultConfig(normalizedType);
    }

    private void writeChannelConfig(ChannelInstance channel) {
        ChannelConnectionConfig config = normalizeConfig(channel.type(), channel.config());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", channel.id());
        payload.put("name", channel.name());
        payload.put("type", channel.type());
        payload.put("enabled", channel.enabled());
        payload.put("defaultAgentId", channel.defaultAgentId());
        payload.put("createdAt", channel.createdAt());
        payload.put("updatedAt", channel.updatedAt());
        payload.put("config", Map.of("authStateDir", config.authStateDir()));
        writeJson(configFile(channel.id(), channel.type()), payload);
    }

    private Path typeDir(String type) {
        return channelsDir.resolve(normalizeType(type));
    }

    private Path channelDir(String type, String channelId) {
        return typeDir(type).resolve(channelId);
    }

    /**
     * Returns the gateway root path from configuration properties.
     *
     * @return the gateway root path from configuration properties
     */
    public Path getGatewayRoot() {
        return properties.getGatewayRootPath();
    }

    private Path configFile(String channelId, String type) {
        return channelDir(type, channelId).resolve("config.json");
    }

    private Path configFile(Path instanceDir) {
        return instanceDir.resolve("config.json");
    }

    private String webhookPath(ChannelInstance channel) {
        return "/gateway/channels/webhooks/" + normalizeType(channel.type()) + "/" + channel.id();
    }

    private boolean usesWebhook(String type) {
        return !List.of("whatsapp", "wechat").contains(normalizeType(type));
    }

    @SuppressWarnings("unchecked")
    private ChannelInstance applyRuntimeState(ChannelInstance channel) {
        if (!List.of("whatsapp", "wechat").contains(channel.type())) {
            return channel;
        }

        Path runtimeFile = runtimeStorageService.runtimeDirectory(channel).resolve("login-state.json");
        if (!Files.exists(runtimeFile)) {
            return channel;
        }

        try {
            Map<String, Object> raw =
                MAPPER.readValue(Files.readString(runtimeFile, StandardCharsets.UTF_8), Map.class);
            ChannelConnectionConfig current = channel.config();
            String runtimeStatus = asString(raw.get("status"));
            String runtimeSelfPhone = asString(raw.get("selfPhone"));
            String runtimeConnectedAt = asString(raw.get("lastConnectedAt"));
            String runtimeDisconnectedAt = asString(raw.get("lastDisconnectedAt"));
            String runtimeError = asString(raw.get("lastError"));
            String runtimeWechatId = asString(raw.get("wechatId"));
            String runtimeDisplayName = asString(raw.get("displayName"));

            ChannelConnectionConfig merged = new ChannelConnectionConfig(
                runtimeStatus != null ? normalizeLoginStatus(runtimeStatus) : current.loginStatus(),
                current.authStateDir(), runtimeConnectedAt != null ? runtimeConnectedAt : current.lastConnectedAt(),
                runtimeDisconnectedAt != null ? runtimeDisconnectedAt : current.lastDisconnectedAt(),
                runtimeError != null ? runtimeError : current.lastError(),
                "whatsapp".equals(channel.type()) ? (runtimeSelfPhone != null ? runtimeSelfPhone : current.selfPhone())
                    : current.selfPhone(),
                "wechat".equals(channel.type()) ? (runtimeWechatId != null ? runtimeWechatId : current.wechatId())
                    : current.wechatId(),
                "wechat".equals(channel.type())
                    ? (runtimeDisplayName != null ? runtimeDisplayName : current.displayName())
                    : current.displayName());

            return new ChannelInstance(channel.id(), channel.name(), channel.type(), channel.enabled(),
                channel.defaultAgentId(), channel.ownerUserId(), channel.createdAt(), channel.updatedAt(), merged);
        } catch (IOException e) {
            log.warn("Failed to read runtime state for channel {}: {}", channel.id(), e.getMessage());
            return channel;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void deleteDirectory(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to delete " + path, e);
                    }
                });
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete channel directory: " + dir, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(Path file) {
        try {
            if (!Files.exists(file)) {
                return new LinkedHashMap<>();
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return new LinkedHashMap<>();
            }
            return MAPPER.readValue(content, Map.class);
        } catch (IOException e) {
            log.warn("Failed to read channels file {}: {}", file, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void writeJson(Path file, Map<String, Object> payload) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write channels file: " + file, e);
        }
    }

    private void writeJson(Path file, Object payload) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write channels file: " + file, e);
        }
    }
}
