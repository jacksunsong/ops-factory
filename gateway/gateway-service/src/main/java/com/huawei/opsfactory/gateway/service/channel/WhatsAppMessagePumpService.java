/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelReplyResult;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelSelfTestResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled service that polls the WhatsApp inbox directory, deduplicates inbound messages, and dispatches them to
 * agent sessions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class WhatsAppMessagePumpService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessagePumpService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelConfigService channelConfigService;

    private final ChannelRuntimeStorageService runtimeStorageService;

    private final ChannelDedupService channelDedupService;

    private final SessionBridgeService sessionBridgeService;

    private final WhatsAppWebLoginService whatsAppWebLoginService;

    /**
     * Creates the whats app message pump service instance.
     */
    public WhatsAppMessagePumpService(ChannelConfigService channelConfigService,
        ChannelRuntimeStorageService runtimeStorageService, ChannelDedupService channelDedupService,
        SessionBridgeService sessionBridgeService, WhatsAppWebLoginService whatsAppWebLoginService) {
        this.channelConfigService = channelConfigService;
        this.runtimeStorageService = runtimeStorageService;
        this.channelDedupService = channelDedupService;
        this.sessionBridgeService = sessionBridgeService;
        this.whatsAppWebLoginService = whatsAppWebLoginService;
    }

    /**
     * Periodically polls the WhatsApp inbox directory for new messages and processes them.
     */
    @Scheduled(fixedDelay = 2000)
    public void pumpInbox() {
        for (ChannelDetail detail : channelConfigService.listRuntimeChannels("whatsapp")) {
            if (!detail.enabled()) {
                continue;
            }
            processChannel(detail);
        }
    }

    private void processChannel(ChannelDetail detail) {
        Path inboxDir = inboxDir(detail);
        if (!Files.isDirectory(inboxDir)) {
            return;
        }

        try (var stream = Files.list(inboxDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted()
                .forEach(path -> processInboundFile(detail, path));
        } catch (IOException e) {
            log.warn("Failed to scan WhatsApp inbox for {}: {}", detail.id(), e.getMessage());
        }
    }

    /**
     * Runs a self-test on a WhatsApp channel by sending a text to the channel's own phone number.
     *
     * @param channelId runs a self-test on a WhatsApp channel by sending a text to the channel's own phone number
     * @param text runs a self-test on a WhatsApp channel by sending a text to the channel's own phone number
     * @return the runs a self-test on a WhatsApp channel by sending a text to the channel's own phone number
     */
    public ChannelSelfTestResult runSelfTest(String channelId, String text) {
        return runSelfTest(channelId, "admin", text);
    }

    /**
     * Runs a self-test on a WhatsApp channel by sending a text to the channel's own phone and queuing the reply.
     *
     * @param channelId runs a self-test on a WhatsApp channel by sending a text to the channel's own phone and queuing
     *        the reply
     * @param ownerUserId runs a self-test on a WhatsApp channel by sending a text to the channel's own phone and
     *        queuing the reply
     * @param text runs a self-test on a WhatsApp channel by sending a text to the channel's own phone and queuing the
     *        reply
     * @return the runs a self-test on a WhatsApp channel by sending a text to the channel's own phone and queuing the
     *         reply
     */
    public ChannelSelfTestResult runSelfTest(String channelId, String ownerUserId, String text) {
        ChannelDetail channel = channelConfigService.getChannel(channelId, ownerUserId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Self-test text is required");
        }

        var loginState = whatsAppWebLoginService.getLoginState(channelId, ownerUserId);
        if (!"connected".equals(loginState.status())) {
            throw new IllegalStateException("WhatsApp channel is not connected. Reconnect before running self-test.");
        }

        String selfPhone = loginState.selfPhone();
        if (selfPhone == null || selfPhone.isBlank()) {
            throw new IllegalStateException("WhatsApp self phone is unavailable. Connect the channel first.");
        }

        ChannelReplyResult reply =
            sessionBridgeService
                .sendConversationText(channel.id(), ownerUserId, "default", selfPhone, selfPhone, null, "direct",
                    text.trim())
                .block(Duration.ofMinutes(5));

        if (reply == null) {
            throw new IllegalStateException("Self-test did not produce a reply");
        }
        if (reply.replyText() != null && !reply.replyText().isBlank()) {
            writeOutboxCommand(channel, selfPhone, reply.replyText());
        }
        channelConfigService.recordEvent(channel.id(), ownerUserId, "info", "whatsapp.self_test",
            "Queued self-chat test reply for " + selfPhone);
        return new ChannelSelfTestResult(channel.id(), selfPhone, reply.agentId(), reply.sessionId(),
            reply.replyText());
    }

    @SuppressWarnings("unchecked")
    private void processInboundFile(ChannelDetail channel, Path file) {
        Map<String, Object> payload;
        try {
            payload = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Map.class);
        } catch (IOException e) {
            channelConfigService.recordEvent(channel.id(), channel.ownerUserId(), "warning", "whatsapp.inbox_invalid",
                "Failed to parse inbound WhatsApp file " + file.getFileName());
            moveToProcessed(channel, file, "invalid");
            return;
        }

        String messageId = asString(payload.get("messageId"));
        String peerId = asString(payload.get("peerId"));
        String conversationId = asString(payload.get("conversationId"));
        String text = asString(payload.get("text"));
        if (messageId == null || peerId == null || conversationId == null || text == null || text.isBlank()) {
            channelConfigService.recordEvent(channel.id(), channel.ownerUserId(), "warning", "whatsapp.inbox_invalid",
                "Inbound WhatsApp file missing required fields");
            moveToProcessed(channel, file, "invalid");
            return;
        }

        if (!channelDedupService.markIfNew(channel.id(), channel.ownerUserId(), messageId)) {
            moveToProcessed(channel, file, "duplicate");
            return;
        }

        try {
            ChannelReplyResult reply = sessionBridgeService
                .sendConversationText(channel.id(), channel.ownerUserId(), "default", peerId, conversationId, null,
                    "direct", text)
                .block(Duration.ofMinutes(5));

            if (reply != null && reply.replyText() != null && !reply.replyText().isBlank()) {
                writeOutboxCommand(channel, peerId, reply.replyText());
            }
            moveToProcessed(channel, file, "processed");
        } catch (IllegalArgumentException | IllegalStateException e) {
            channelConfigService.recordEvent(channel.id(), channel.ownerUserId(), "warning", "whatsapp.inbox_failed",
                "Failed to process inbound WhatsApp message: " + e.getMessage());
            moveToProcessed(channel, file, "error");
        }
    }

    private void writeOutboxCommand(ChannelDetail channel, String peerId, String text) {
        Path pendingDir = outboxPendingDir(channel);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("to", peerId);
        payload.put("text", text);
        payload.put("createdAt", Instant.now().toString());
        Path file = pendingDir.resolve(payload.get("id") + ".json");
        try {
            Files.createDirectories(pendingDir);
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
            channelConfigService.recordEvent(channel.id(), channel.ownerUserId(), "info", "whatsapp.outbox_enqueued",
                "Queued WhatsApp reply for " + peerId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write WhatsApp outbox command", e);
        }
    }

    private void moveToProcessed(ChannelDetail channel, Path file, String suffix) {
        Path processedDir = processedInboxDir(channel);
        try {
            Files.createDirectories(processedDir);
            Files.move(file,
                processedDir.resolve(file.getFileName().toString().replace(".json", "-" + suffix + ".json")));
        } catch (IOException e) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException deleteError) {
                // ignore
            }
        }
    }

    private Path inboxDir(ChannelDetail channel) {
        return runtimeStorageService.inboxDirectory(channel);
    }

    private Path processedInboxDir(ChannelDetail channel) {
        return runtimeStorageService.processedInboxDirectory(channel);
    }

    private Path outboxPendingDir(ChannelDetail channel) {
        return runtimeStorageService.outboxPendingDirectory(channel);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
