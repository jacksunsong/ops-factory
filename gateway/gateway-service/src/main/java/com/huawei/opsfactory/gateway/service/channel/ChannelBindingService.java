/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.service.channel.model.ChannelBinding;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages channel conversation bindings, including creation, session attachment, and inbound/outbound timestamp
 * tracking.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ChannelBindingService {
    private static final Logger log = LoggerFactory.getLogger(ChannelBindingService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelConfigService channelConfigService;

    private final ChannelRuntimeStorageService runtimeStorageService;

    /**
     * Creates the channel binding service instance.
     */
    public ChannelBindingService(ChannelConfigService channelConfigService,
        ChannelRuntimeStorageService runtimeStorageService) {
        this.channelConfigService = channelConfigService;
        this.runtimeStorageService = runtimeStorageService;
    }

    /**
     * Ensures a binding exists for the given channel and external user ID, creating one if necessary.
     *
     * @param channelId ensures a binding exists for the given channel and external user ID, creating one if necessary
     * @param externalUserId ensures a binding exists for the given channel and external user ID, creating one if
     *        necessary
     * @return the ensures a binding exists for the given channel and external user ID, creating one if necessary
     */
    public ChannelBinding ensureBinding(String channelId, String externalUserId) {
        return ensureConversationBinding(channelId, "admin", "default", externalUserId, externalUserId, null, "direct");
    }

    /**
     * Ensures a conversation binding exists using the default owner user ID.
     *
     * @param channelId ensures a conversation binding exists using the default owner user ID
     * @param accountId ensures a conversation binding exists using the default owner user ID
     * @param peerId ensures a conversation binding exists using the default owner user ID
     * @param conversationId ensures a conversation binding exists using the default owner user ID
     * @param threadId ensures a conversation binding exists using the default owner user ID
     * @param conversationType ensures a conversation binding exists using the default owner user ID
     * @return the ensures a conversation binding exists using the default owner user ID
     */
    public ChannelBinding ensureConversationBinding(String channelId, String accountId, String peerId,
        String conversationId, String threadId, String conversationType) {
        return ensureConversationBinding(channelId, "admin", accountId, peerId, conversationId, threadId,
            conversationType);
    }

    /**
     * Ensures a conversation binding exists for the given channel and conversation identifiers, creating one if
     * necessary.
     *
     * @param channelId ensures a conversation binding exists for the given channel and conversation identifiers,
     *        creating one if
     * @param ownerUserId ensures a conversation binding exists for the given channel and conversation identifiers,
     *        creating one if
     * @param accountId ensures a conversation binding exists for the given channel and conversation identifiers,
     *        creating one if
     * @param peerId ensures a conversation binding exists for the given channel and conversation identifiers, creating
     *        one if
     * @param conversationId ensures a conversation binding exists for the given channel and conversation identifiers,
     *        creating one if
     * @param threadId ensures a conversation binding exists for the given channel and conversation identifiers,
     *        creating one if
     * @param conversationType ensures a conversation binding exists for the given channel and conversation identifiers,
     *        creating one if
     * @return the ensures a conversation binding exists for the given channel and conversation identifiers, creating
     *         one if
     */
    public ChannelBinding ensureConversationBinding(String channelId, String ownerUserId, String accountId,
        String peerId, String conversationId, String threadId, String conversationType) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        List<ChannelBinding> bindings = new ArrayList<>(readBindings(channel));
        for (ChannelBinding binding : bindings) {
            if (matches(binding, accountId, conversationId, threadId)) {
                return binding;
            }
        }

        ChannelBinding created = new ChannelBinding(channelId, normalizeAccountId(accountId), peerId, conversationId,
            normalizeThreadId(threadId), normalizeConversationType(conversationType), channel.ownerUserId(),
            buildSyntheticUserId(channel.type(), channelId, accountId, conversationId, threadId),
            channel.defaultAgentId(), null, null, null);
        bindings.add(created);
        writeBindings(channel, bindings);
        channelConfigService.recordEvent(channelId, ownerUserId, "info", "binding.created",
            "Created binding for " + summarizeConversation(peerId, conversationId, threadId));
        return created;
    }

    /**
     * Attaches a session to the binding for the given channel and external user ID.
     *
     * @param channelId attaches a session to the binding for the given channel and external user ID
     * @param externalUserId attaches a session to the binding for the given channel and external user ID
     * @param sessionId attaches a session to the binding for the given channel and external user ID
     * @param agentId attaches a session to the binding for the given channel and external user ID
     * @return the attaches a session to the binding for the given channel and external user ID
     */
    public ChannelBinding attachSession(String channelId, String externalUserId, String sessionId, String agentId) {
        return attachConversationSession(channelId, "admin", "default", externalUserId, externalUserId, null, "direct",
            sessionId, agentId);
    }

    /**
     * Attaches a session to the conversation binding using the default owner user ID.
     *
     * @param channelId attaches a session to the conversation binding using the default owner user ID
     * @param accountId attaches a session to the conversation binding using the default owner user ID
     * @param peerId attaches a session to the conversation binding using the default owner user ID
     * @param conversationId attaches a session to the conversation binding using the default owner user ID
     * @param threadId attaches a session to the conversation binding using the default owner user ID
     * @param conversationType attaches a session to the conversation binding using the default owner user ID
     * @param sessionId attaches a session to the conversation binding using the default owner user ID
     * @param agentId attaches a session to the conversation binding using the default owner user ID
     * @return the attaches a session to the conversation binding using the default owner user ID
     */
    public ChannelBinding attachConversationSession(String channelId, String accountId, String peerId,
        String conversationId, String threadId, String conversationType, String sessionId, String agentId) {
        return attachConversationSession(channelId, "admin", accountId, peerId, conversationId, threadId,
            conversationType, sessionId, agentId);
    }

    /**
     * Attaches a session to the conversation binding, creating the binding if it does not yet exist.
     *
     * @param channelId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @param ownerUserId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @param accountId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @param peerId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @param conversationId attaches a session to the conversation binding, creating the binding if it does not yet
     *        exist
     * @param threadId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @param conversationType attaches a session to the conversation binding, creating the binding if it does not yet
     *        exist
     * @param sessionId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @param agentId attaches a session to the conversation binding, creating the binding if it does not yet exist
     * @return the attaches a session to the conversation binding, creating the binding if it does not yet exist
     */
    public ChannelBinding attachConversationSession(String channelId, String ownerUserId, String accountId,
        String peerId, String conversationId, String threadId, String conversationType, String sessionId,
        String agentId) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        List<ChannelBinding> bindings = new ArrayList<>(readBindings(channel));
        ChannelBinding nextBinding = null;

        for (int i = 0; i < bindings.size(); i++) {
            ChannelBinding binding = bindings.get(i);
            if (!matches(binding, accountId, conversationId, threadId)) {
                continue;
            }
            nextBinding = new ChannelBinding(binding.channelId(), binding.accountId(), choose(peerId, binding.peerId()),
                binding.conversationId(), binding.threadId(), binding.conversationType(),
                binding.ownerUserId() == null || binding.ownerUserId().isBlank() ? channel.ownerUserId()
                    : binding.ownerUserId(),
                binding.syntheticUserId(), agentId, sessionId, binding.lastInboundAt(), binding.lastOutboundAt());
            bindings.set(i, nextBinding);
            break;
        }

        if (nextBinding == null) {
            nextBinding = new ChannelBinding(channelId, normalizeAccountId(accountId), peerId, conversationId,
                normalizeThreadId(threadId), normalizeConversationType(conversationType), channel.ownerUserId(),
                buildSyntheticUserId(channel.type(), channelId, accountId, conversationId, threadId), agentId,
                sessionId, null, null);
            bindings.add(nextBinding);
        }

        writeBindings(channel, bindings);
        channelConfigService.recordEvent(channelId, ownerUserId, "info", "binding.session_attached",
            "Bound session " + sessionId + " to " + summarizeConversation(peerId, conversationId, threadId));
        return nextBinding;
    }

    /**
     * Marks the latest inbound timestamp for the binding associated with the given channel and external user ID.
     *
     * @param channelId marks the latest inbound timestamp for the binding associated with the given channel and
     *        external user ID
     * @param externalUserId marks the latest inbound timestamp for the binding associated with the given channel and
     *        external user ID
     * @return the marks the latest inbound timestamp for the binding associated with the given channel and external
     *         user ID
     */
    public ChannelBinding markInbound(String channelId, String externalUserId) {
        return markConversationInbound(channelId, "admin", "default", externalUserId, null);
    }

    /**
     * Marks the latest outbound timestamp for the binding associated with the given channel and external user ID.
     *
     * @param channelId marks the latest outbound timestamp for the binding associated with the given channel and
     *        external user ID
     * @param externalUserId marks the latest outbound timestamp for the binding associated with the given channel and
     *        external user ID
     * @return the marks the latest outbound timestamp for the binding associated with the given channel and external
     *         user ID
     */
    public ChannelBinding markOutbound(String channelId, String externalUserId) {
        return markConversationOutbound(channelId, "admin", "default", externalUserId, null);
    }

    /**
     * Marks the latest inbound timestamp for a conversation binding using the default owner user ID.
     *
     * @param channelId marks the latest inbound timestamp for a conversation binding using the default owner user ID
     * @param accountId marks the latest inbound timestamp for a conversation binding using the default owner user ID
     * @param conversationId marks the latest inbound timestamp for a conversation binding using the default owner user
     *        ID
     * @param threadId marks the latest inbound timestamp for a conversation binding using the default owner user ID
     * @return the marks the latest inbound timestamp for a conversation binding using the default owner user ID
     */
    public ChannelBinding markConversationInbound(String channelId, String accountId, String conversationId,
        String threadId) {
        return markConversationInbound(channelId, "admin", accountId, conversationId, threadId);
    }

    /**
     * Marks the latest inbound timestamp for a conversation binding.
     *
     * @param channelId marks the latest inbound timestamp for a conversation binding
     * @param ownerUserId marks the latest inbound timestamp for a conversation binding
     * @param accountId marks the latest inbound timestamp for a conversation binding
     * @param conversationId marks the latest inbound timestamp for a conversation binding
     * @param threadId marks the latest inbound timestamp for a conversation binding
     * @return the marks the latest inbound timestamp for a conversation binding
     */
    public ChannelBinding markConversationInbound(String channelId, String ownerUserId, String accountId,
        String conversationId, String threadId) {
        return updateTimestamps(channelId, ownerUserId, accountId, conversationId, threadId, Instant.now().toString(),
            null);
    }

    /**
     * Marks the latest outbound timestamp for a conversation binding using the default owner user ID.
     *
     * @param channelId marks the latest outbound timestamp for a conversation binding using the default owner user ID
     * @param accountId marks the latest outbound timestamp for a conversation binding using the default owner user ID
     * @param conversationId marks the latest outbound timestamp for a conversation binding using the default owner user
     *        ID
     * @param threadId marks the latest outbound timestamp for a conversation binding using the default owner user ID
     * @return the marks the latest outbound timestamp for a conversation binding using the default owner user ID
     */
    public ChannelBinding markConversationOutbound(String channelId, String accountId, String conversationId,
        String threadId) {
        return markConversationOutbound(channelId, "admin", accountId, conversationId, threadId);
    }

    /**
     * Marks the latest outbound timestamp for a conversation binding.
     *
     * @param channelId marks the latest outbound timestamp for a conversation binding
     * @param ownerUserId marks the latest outbound timestamp for a conversation binding
     * @param accountId marks the latest outbound timestamp for a conversation binding
     * @param conversationId marks the latest outbound timestamp for a conversation binding
     * @param threadId marks the latest outbound timestamp for a conversation binding
     * @return the marks the latest outbound timestamp for a conversation binding
     */
    public ChannelBinding markConversationOutbound(String channelId, String ownerUserId, String accountId,
        String conversationId, String threadId) {
        return updateTimestamps(channelId, ownerUserId, accountId, conversationId, threadId, null,
            Instant.now().toString());
    }

    private ChannelBinding updateTimestamps(String channelId, String ownerUserId, String accountId,
        String conversationId, String threadId, String lastInboundAt, String lastOutboundAt) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        List<ChannelBinding> bindings = new ArrayList<>(readBindings(channel));
        for (int i = 0; i < bindings.size(); i++) {
            ChannelBinding binding = bindings.get(i);
            if (!matches(binding, accountId, conversationId, threadId)) {
                continue;
            }
            ChannelBinding updated = new ChannelBinding(binding.channelId(), binding.accountId(), binding.peerId(),
                binding.conversationId(), binding.threadId(), binding.conversationType(), binding.ownerUserId(),
                binding.syntheticUserId(), binding.agentId(), binding.sessionId(),
                lastInboundAt != null ? lastInboundAt : binding.lastInboundAt(),
                lastOutboundAt != null ? lastOutboundAt : binding.lastOutboundAt());
            bindings.set(i, updated);
            writeBindings(channel, bindings);
            return updated;
        }
        throw new IllegalArgumentException("Binding not found for channel '" + channelId + "'");
    }

    private ChannelDetail requireChannel(String channelId) {
        return requireChannel(channelId, "admin");
    }

    private ChannelDetail requireChannel(String channelId, String ownerUserId) {
        ChannelDetail channel = channelConfigService.getChannel(channelId, ownerUserId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel '" + channelId + "' not found");
        }
        return channel;
    }

    private List<ChannelBinding> readBindings(ChannelDetail channel) {
        Path file = runtimeStorageService.bindingsFile(channel);
        Map<String, Object> wrapper = readJson(file);
        return MAPPER.convertValue(wrapper.getOrDefault("bindings", List.of()),
            new TypeReference<List<ChannelBinding>>() {});
    }

    private void writeBindings(ChannelDetail channel, List<ChannelBinding> bindings) {
        writeJson(runtimeStorageService.bindingsFile(channel), Map.of("bindings", bindings));
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
            log.warn("Failed to read channel bindings {}: {}", file, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void writeJson(Path file, Object payload) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write channel bindings: " + file, e);
        }
    }

    private String buildSyntheticUserId(String type, String channelId, String accountId, String conversationId,
        String threadId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = normalizeAccountId(accountId) + "::" + conversationId + "::" + normalizeThreadId(threadId);
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            String suffix = HexFormat.of().formatHex(hash).substring(0, 24);
            return "channel__" + type + "__" + channelId + "__" + suffix;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private boolean matches(ChannelBinding binding, String accountId, String conversationId, String threadId) {
        return normalizeAccountId(accountId).equals(normalizeAccountId(binding.accountId()))
            && normalizeConversationId(conversationId).equals(normalizeConversationId(binding.conversationId()))
            && normalizeThreadId(threadId).equals(normalizeThreadId(binding.threadId()));
    }

    private String choose(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private String normalizeAccountId(String accountId) {
        return accountId == null || accountId.isBlank() ? "default" : accountId.trim();
    }

    private String normalizeConversationId(String conversationId) {
        return conversationId == null ? "" : conversationId.trim();
    }

    private String normalizeThreadId(String threadId) {
        return threadId == null ? "" : threadId.trim();
    }

    private String normalizeConversationType(String conversationType) {
        return conversationType == null || conversationType.isBlank() ? "direct" : conversationType.trim();
    }

    private String summarizeConversation(String peerId, String conversationId, String threadId) {
        String base = peerId == null || peerId.isBlank() ? conversationId : peerId;
        if (base == null || base.isBlank()) {
            base = "unknown";
        }
        if (threadId != null && !threadId.isBlank()) {
            return base + "@" + threadId;
        }
        return base;
    }

    private String maskExternalUserId(String externalUserId) {
        if (externalUserId == null || externalUserId.length() <= 4) {
            return externalUserId;
        }
        return externalUserId.substring(0, 2) + "***" + externalUserId.substring(externalUserId.length() - 2);
    }
}
