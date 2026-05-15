/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectionConfig;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelLoginState;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Manages WeChat channel login lifecycle including QR code login, logout, and runtime state file management.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class WeChatLoginService {
    private static final Logger log = LoggerFactory.getLogger(WeChatLoginService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelConfigService channelConfigService;

    private final ChannelRuntimeStorageService runtimeStorageService;

    /**
     * Creates the we chat login service instance.
     */
    public WeChatLoginService(ChannelConfigService channelConfigService,
        ChannelRuntimeStorageService runtimeStorageService) {
        this.channelConfigService = channelConfigService;
        this.runtimeStorageService = runtimeStorageService;
    }

    /**
     * Gets the current login state for a WeChat channel using the default owner user ID.
     *
     * @param channelId channel identifier
     * @return the current login state for a WeChat channel using the default owner user ID
     */
    public ChannelLoginState getLoginState(String channelId) {
        return getLoginState(channelId, "admin");
    }

    /**
     * Gets the current login state for a WeChat channel, merging configuration and runtime state.
     *
     * @param channelId channel identifier
     * @param ownerUserId owner user id
     * @return the current login state for a WeChat channel, merging configuration and runtime state
     */
    public ChannelLoginState getLoginState(String channelId, String ownerUserId) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        ChannelConnectionConfig config = channel.config();
        Map<String, Object> runtimeState = readRuntimeState(channel);
        String status = normalizeStatus(config.loginStatus());
        if (runtimeState.get("status") instanceof String runtimeStatus && !runtimeStatus.isBlank()) {
            status = normalizeStatus(runtimeStatus);
        }
        String message = switch (status) {
            case "connected":
                yield "WeChat session connected";
            case "pending":
                yield "WeChat QR login is pending";
            case "error":
                yield config.lastError() == null || config.lastError().isBlank() ? "WeChat connection error"
                    : config.lastError();
            default:
                yield "WeChat login required";
        };

        String stateMessage = asString(runtimeState.get("message"));
        if (stateMessage != null && !stateMessage.isBlank()) {
            message = stateMessage;
        }
        String stateConnectedAt = asString(runtimeState.get("lastConnectedAt"));
        String stateDisconnectedAt = asString(runtimeState.get("lastDisconnectedAt"));
        String stateError = asString(runtimeState.get("lastError"));
        String stateQr = asString(runtimeState.get("qrCodeDataUrl"));
        String stateWechatId = asString(runtimeState.get("wechatId"));

        return new ChannelLoginState(channel.id(), status, message, config.authStateDir(),
            stateWechatId != null ? stateWechatId : config.wechatId(),
            stateConnectedAt != null ? stateConnectedAt : config.lastConnectedAt(),
            stateDisconnectedAt != null ? stateDisconnectedAt : config.lastDisconnectedAt(),
            stateError != null ? stateError : config.lastError(), stateQr);
    }

    /**
     * Starts the WeChat QR login flow using the default owner user ID.
     *
     * @param channelId channel identifier
     * @return the starts the WeChat QR login flow using the default owner user ID
     */
    public ChannelLoginState startLogin(String channelId) {
        return startLogin(channelId, "admin");
    }

    /**
     * Starts the WeChat QR login flow, preparing the auth directory and launching the helper process.
     *
     * @param channelId channel identifier
     * @param ownerUserId owner user id
     * @return the starts the WeChat QR login flow, preparing the auth directory and launching the helper process
     */
    public ChannelLoginState startLogin(String channelId, String ownerUserId) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        Path authDir = resolveAuthDir(channel);
        Path stateFile = loginStateFile(channel);
        Path pidFile = pidFile(channel);
        Path logFile = logFile(channel);
        Path inboxDir = inboxDir(channel);
        Path outboxPendingDir = outboxPendingDir(channel);
        Path outboxSentDir = outboxSentDir(channel);
        Path outboxErrorDir = outboxErrorDir(channel);
        killIfRunning(pidFile);
        try {
            Files.createDirectories(authDir);
            Files.createDirectories(inboxDir);
            Files.createDirectories(outboxPendingDir);
            Files.createDirectories(outboxSentDir);
            Files.createDirectories(outboxErrorDir);
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create WeChat runtime directory", e);
        }

        writeInitialStateFile(channel, stateFile);
        startHelperProcess(channel, authDir, stateFile, pidFile, logFile, inboxDir, outboxPendingDir, outboxSentDir,
            outboxErrorDir);
        channelConfigService.recordEvent(channelId, ownerUserId, "info", "wechat.login_requested",
            "WeChat login requested; auth directory prepared at " + authDir);

        return getLoginState(channelId, ownerUserId);
    }

    /**
     * Logs out of a WeChat channel using the default owner user ID.
     *
     * @param channelId logs out of a WeChat channel using the default owner user ID
     * @return the logs out of a WeChat channel using the default owner user ID
     */
    public ChannelLoginState logout(String channelId) {
        return logout(channelId, "admin");
    }

    /**
     * Logs out of a WeChat channel, stopping the helper process and clearing auth state.
     *
     * @param channelId logs out of a WeChat channel, stopping the helper process and clearing auth state
     * @param ownerUserId logs out of a WeChat channel, stopping the helper process and clearing auth state
     * @return the logs out of a WeChat channel, stopping the helper process and clearing auth state
     */
    public ChannelLoginState logout(String channelId, String ownerUserId) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        Path authDir = resolveAuthDir(channel);
        Path stateFile = loginStateFile(channel);
        Path pidFile = pidFile(channel);
        try {
            killIfRunning(pidFile);
        } catch (IllegalStateException e) {
            log.debug("Failed to stop existing WeChat helper for {}", channelId, e);
        }
        try {
            clearDirectory(authDir);
        } catch (IllegalStateException e) {
            log.debug("Failed to clear WeChat auth dir for {}", channelId, e);
        }
        deleteQuietly(stateFile);

        writeDisconnectedStateFile(channel, stateFile);
        channelConfigService.recordEvent(channelId, ownerUserId, "info", "wechat.logged_out",
            "Cleared WeChat auth state");
        ChannelDetail updated = channelConfigService.getChannel(channelId, ownerUserId);

        return new ChannelLoginState(updated.id(), "disconnected", "WeChat login required",
            updated.config().authStateDir(), updated.config().wechatId(), updated.config().lastConnectedAt(),
            updated.config().lastDisconnectedAt(), updated.config().lastError(), null);
    }

    private ChannelDetail requireChannel(String channelId, String ownerUserId) {
        ChannelDetail channel = channelConfigService.getChannel(channelId, ownerUserId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        if (!"wechat".equals(channel.type())) {
            throw new IllegalArgumentException("Channel '" + channelId + "' is not a WeChat channel");
        }
        return channel;
    }

    private Path resolveAuthDir(ChannelDetail channel) {
        return runtimeStorageService.authDirectory(channel);
    }

    private Path loginStateFile(ChannelDetail channel) {
        return runtimeStorageService.loginStateFile(channel);
    }

    private Path pidFile(ChannelDetail channel) {
        return runtimeStorageService.pidFile(channel);
    }

    private Path logFile(ChannelDetail channel) {
        return runtimeStorageService.logFile(channel);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRuntimeState(ChannelDetail channel) {
        Path stateFile = loginStateFile(channel);
        try {
            if (!Files.exists(stateFile)) {
                return Map.of();
            }
            String raw = Files.readString(stateFile, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return Map.of();
            }
            return MAPPER.readValue(raw, Map.class);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void writeInitialStateFile(ChannelDetail channel, Path stateFile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelId", channel.id());
        payload.put("status", "pending");
        payload.put("message", "Preparing WeChat QR login...");
        payload.put("authStateDir", channel.config().authStateDir());
        payload.put("wechatId", channel.config().wechatId());
        payload.put("displayName", channel.config().displayName());
        payload.put("lastConnectedAt", channel.config().lastConnectedAt());
        payload.put("lastDisconnectedAt", channel.config().lastDisconnectedAt());
        payload.put("lastError", "");
        payload.put("qrCodeDataUrl", null);
        try {
            Files.writeString(stateFile, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write WeChat login state file", e);
        }
    }

    private void writeDisconnectedStateFile(ChannelDetail channel, Path stateFile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelId", channel.id());
        payload.put("status", "disconnected");
        payload.put("message", "WeChat login required");
        payload.put("authStateDir", channel.config().authStateDir());
        payload.put("wechatId", channel.config().wechatId());
        payload.put("displayName", channel.config().displayName());
        payload.put("lastConnectedAt", channel.config().lastConnectedAt());
        payload.put("lastDisconnectedAt", Instant.now().toString());
        payload.put("lastError", "");
        payload.put("qrCodeDataUrl", null);
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write WeChat login state file", e);
        }
    }

    private void startHelperProcess(ChannelDetail channel, Path authDir, Path stateFile, Path pidFile, Path logFile,
        Path inboxDir, Path outboxPendingDir, Path outboxSentDir, Path outboxErrorDir) {
        Path helperDir = channelConfigService.getGatewayRoot().resolve("tools").resolve("wechat-helper");
        Path helperEntry = helperDir.resolve("index.mjs");
        if (!Files.exists(helperEntry)) {
            throw new IllegalStateException("WeChat helper not found: " + helperEntry);
        }

        List<String> command = new ArrayList<>();
        command.add("node");
        command.add(helperEntry.toString());
        command.add("--command");
        command.add("login");
        command.add("--channel-id");
        command.add(channel.id());
        command.add("--state-file");
        command.add(stateFile.toString());
        command.add("--pid-file");
        command.add(pidFile.toString());
        command.add("--auth-dir");
        command.add(authDir.toString());
        command.add("--inbox-dir");
        command.add(inboxDir.toString());
        command.add("--outbox-pending-dir");
        command.add(outboxPendingDir.toString());
        command.add("--outbox-sent-dir");
        command.add(outboxSentDir.toString());
        command.add("--outbox-error-dir");
        command.add(outboxErrorDir.toString());
        command.add("--log-file");
        command.add(logFile.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(helperDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        try {
            builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start WeChat helper", e);
        }
    }

    private void killIfRunning(Path pidFile) {
        try {
            if (!Files.exists(pidFile)) {
                return;
            }
            String raw = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            if (raw.isBlank()) {
                Files.deleteIfExists(pidFile);
                return;
            }
            Map<String, Object> pidPayload = MAPPER.readValue(raw, Map.class);
            Object pidObj = pidPayload.get("pid");
            if (!(pidObj instanceof Number number)) {
                Files.deleteIfExists(pidFile);
                return;
            }
            long pid = number.longValue();
            ProcessHandle.of(pid).ifPresent(handle -> {
                handle.destroy();
                try {
                    handle.onExit().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handle.destroyForcibly();
                } catch (ExecutionException e) {
                    handle.destroyForcibly();
                }
            });
            Files.deleteIfExists(pidFile);
        } catch (IOException | NumberFormatException e) {
            try {
                Files.deleteIfExists(pidFile);
            } catch (IOException deleteError) {
                // ignore
            }
        }
    }

    private Path inboxDir(ChannelDetail channel) {
        return runtimeStorageService.inboxDirectory(channel);
    }

    private Path outboxPendingDir(ChannelDetail channel) {
        return runtimeStorageService.outboxPendingDirectory(channel);
    }

    private Path outboxSentDir(ChannelDetail channel) {
        return runtimeStorageService.outboxSentDirectory(channel);
    }

    private Path outboxErrorDir(ChannelDetail channel) {
        return runtimeStorageService.outboxErrorDirectory(channel);
    }

    private void clearDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to clear directory " + dir, e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best-effort cleanup
        }
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "disconnected";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
