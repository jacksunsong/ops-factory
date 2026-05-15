/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelInstance;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manages channel runtime directory layout and provides path resolution for auth, inbox, outbox, bindings, dedup, and
 * event files.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ChannelRuntimeStorageService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final GatewayProperties properties;

    /**
     * Creates the channel runtime storage service instance.
     */
    public ChannelRuntimeStorageService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the runtime directory for a channel detail.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path runtimeDirectory(ChannelDetail channel) {
        return runtimeDirectory(channel.ownerUserId(), channel.type(), channel.id());
    }

    /**
     * Resolves the runtime directory for a channel instance.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path runtimeDirectory(ChannelInstance channel) {
        return runtimeDirectory(channel.ownerUserId(), channel.type(), channel.id());
    }

    /**
     * Resolves the runtime directory for a channel by owner user, type, and channel ID.
     *
     * @param ownerUserId owner user id
     * @param type type filter
     * @param channelId channel identifier
     * @return the result
     */
    public Path runtimeDirectory(String ownerUserId, String type, String channelId) {
        Path typeRoot = properties.getGatewayRootPath()
            .resolve("users")
            .resolve(normalizeOwnerUserId(ownerUserId))
            .resolve("channels")
            .resolve(normalizeType(type))
            .normalize();
        Path runtimeDirectory = typeRoot.resolve(requireSafeSegment(channelId, "channelId")).normalize();
        if (!runtimeDirectory.startsWith(typeRoot)) {
            throw new IllegalArgumentException("channelId must stay within the channel runtime directory");
        }
        return runtimeDirectory;
    }

    /**
     * Resolves the auth directory for a channel, validating that it stays within the runtime directory.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path authDirectory(ChannelDetail channel) {
        String configured = channel.config() == null ? "" : channel.config().authStateDir();
        Path relative = Path.of(configured == null || configured.isBlank() ? "auth" : configured.trim());
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("authStateDir must be relative to the channel runtime directory");
        }
        Path runtimeDirectory = runtimeDirectory(channel);
        Path authDirectory = runtimeDirectory.resolve(relative).normalize();
        if (!authDirectory.startsWith(runtimeDirectory)) {
            throw new IllegalArgumentException("authStateDir must stay within the channel runtime directory");
        }
        return authDirectory;
    }

    /**
     * Resolves the login state file path for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path loginStateFile(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("login-state.json");
    }

    /**
     * Resolves the PID file path for a channel login process.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path pidFile(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("login.pid");
    }

    /**
     * Resolves the log file path for a channel login process.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path logFile(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("login.log");
    }

    /**
     * Resolves the inbox directory for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path inboxDirectory(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("inbox");
    }

    /**
     * Resolves the processed inbox directory for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path processedInboxDirectory(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("processed");
    }

    /**
     * Resolves the pending outbox directory for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path outboxPendingDirectory(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("outbox").resolve("pending");
    }

    /**
     * Resolves the sent outbox directory for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path outboxSentDirectory(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("outbox").resolve("sent");
    }

    /**
     * Resolves the error outbox directory for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path outboxErrorDirectory(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("outbox").resolve("error");
    }

    /**
     * Resolves the bindings file path for a channel detail.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path bindingsFile(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("bindings.json");
    }

    /**
     * Resolves the bindings file path for a channel instance.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path bindingsFile(ChannelInstance channel) {
        return runtimeDirectory(channel).resolve("bindings.json");
    }

    /**
     * Resolves the inbound dedup file path for a channel.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path dedupFile(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("inbound-dedup.json");
    }

    /**
     * Resolves the events file path for a channel detail.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path eventsFile(ChannelDetail channel) {
        return runtimeDirectory(channel).resolve("events.json");
    }

    /**
     * Resolves the events file path for a channel instance.
     *
     * @param channel channel configuration
     * @return the result
     */
    public Path eventsFile(ChannelInstance channel) {
        return runtimeDirectory(channel).resolve("events.json");
    }

    /**
     * Initializes the runtime directory structure for a channel instance, creating required files if missing.
     *
     * @param channel initializes the runtime directory structure for a channel instance, creating required files if
     *        missing
     */
    public void initializeRuntime(ChannelInstance channel) {
        try {
            Files.createDirectories(runtimeDirectory(channel));
            initializeIfMissing(bindingsFile(channel), Map.of("bindings", List.of()));
            initializeIfMissing(runtimeDirectory(channel).resolve("inbound-dedup.json"), Map.of("messages", List.of()));
            initializeIfMissing(eventsFile(channel), Map.of("events", List.of()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize channel runtime for " + channel.id(), e);
        }
    }

    /**
     * Initializes the runtime directory structure for a channel detail, creating required files if missing.
     *
     * @param channel initializes the runtime directory structure for a channel detail, creating required files if
     *        missing
     */
    public void initializeRuntime(ChannelDetail channel) {
        try {
            Files.createDirectories(runtimeDirectory(channel));
            initializeIfMissing(bindingsFile(channel), Map.of("bindings", List.of()));
            initializeIfMissing(dedupFile(channel), Map.of("messages", List.of()));
            initializeIfMissing(eventsFile(channel), Map.of("events", List.of()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize channel runtime for " + channel.id(), e);
        }
    }

    /**
     * Deletes the entire runtime directory for a channel instance.
     *
     * @param channel channel configuration
     */
    public void deleteRuntime(ChannelInstance channel) {
        deleteDirectory(runtimeDirectory(channel));
    }

    /**
     * Deletes the entire runtime directory for a channel detail.
     *
     * @param channel channel configuration
     */
    public void deleteRuntime(ChannelDetail channel) {
        deleteDirectory(runtimeDirectory(channel));
    }

    /**
     * Deletes all runtime directories for a channel across all owner users.
     *
     * @param type type filter
     * @param channelId channel identifier
     */
    public void deleteAllRuntimes(String type, String channelId) {
        for (ChannelRuntimeRef runtime : listRuntimeRefs(type, channelId)) {
            deleteDirectory(runtime.runtimeDirectory());
        }
    }

    /**
     * Lists all runtime references for a channel across all owner users.
     *
     * @param type type filter
     * @param channelId channel identifier
     * @return the result
     */
    public List<ChannelRuntimeRef> listRuntimeRefs(String type, String channelId) {
        String normalizedType = normalizeType(type);
        String normalizedChannelId = requireSafeSegment(channelId, "channelId");
        Path usersRoot = properties.getGatewayRootPath().resolve("users").normalize();
        if (!Files.isDirectory(usersRoot)) {
            return List.of();
        }

        List<ChannelRuntimeRef> refs = new ArrayList<>();
        try (var users = Files.list(usersRoot)) {
            users.filter(Files::isDirectory).forEach(userDir -> {
                Path userName = userDir.getFileName();
                if (userName == null) {
                    return;
                }
                String ownerUserId = userName.toString();
                try {
                    Path runtimeDirectory =
                        userDir.resolve("channels").resolve(normalizedType).resolve(normalizedChannelId).normalize();
                    Path expected = runtimeDirectory(ownerUserId, normalizedType, normalizedChannelId);
                    if (runtimeDirectory.equals(expected) && Files.isDirectory(runtimeDirectory)) {
                        refs.add(
                            new ChannelRuntimeRef(ownerUserId, normalizedType, normalizedChannelId, runtimeDirectory));
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore unrelated/unsafe user directories when scanning channel runtime state.
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list channel runtime directories", e);
        }
        return refs;
    }

    private void initializeIfMissing(Path file, Object payload) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
            StandardCharsets.UTF_8);
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
            throw new IllegalStateException("Failed to delete channel runtime directory " + dir, e);
        }
    }

    private String normalizeType(String type) {
        return requireSafeSegment(type == null || type.isBlank() ? "whatsapp" : type.trim().toLowerCase(Locale.ROOT),
            "type");
    }

    private String normalizeOwnerUserId(String ownerUserId) {
        String normalized = ownerUserId == null || ownerUserId.isBlank() ? "admin" : ownerUserId.trim();
        if (normalized.contains("/") || normalized.contains("\\") || ".".equals(normalized)
            || "..".equals(normalized)) {
            throw new IllegalArgumentException("ownerUserId contains unsafe path characters");
        }
        return normalized;
    }

    private String requireSafeSegment(String value, String fieldName) {
        if (value == null || value.isBlank() || !SAFE_PATH_SEGMENT.matcher(value).matches() || ".".equals(value)
            || "..".equals(value)) {
            throw new IllegalArgumentException(fieldName + " contains unsafe path characters");
        }
        return value;
    }

    /**
     * Type definition for Channel Runtime Ref.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public record ChannelRuntimeRef(String ownerUserId, String type, String channelId, Path runtimeDirectory) {
    }
}
