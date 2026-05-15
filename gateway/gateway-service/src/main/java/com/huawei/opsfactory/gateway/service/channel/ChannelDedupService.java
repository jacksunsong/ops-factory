/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-based deduplication service that tracks inbound message IDs to prevent duplicate processing.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ChannelDedupService {
    private static final Logger log = LoggerFactory.getLogger(ChannelDedupService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_MESSAGES = 500;

    private final ChannelConfigService channelConfigService;

    private final ChannelRuntimeStorageService runtimeStorageService;

    /**
     * Creates the channel dedup service instance.
     */
    public ChannelDedupService(ChannelConfigService channelConfigService,
        ChannelRuntimeStorageService runtimeStorageService) {
        this.channelConfigService = channelConfigService;
        this.runtimeStorageService = runtimeStorageService;
    }

    /**
     * Checks if a message is new and marks it as seen, using the default owner user ID.
     *
     * @param channelId channel identifier
     * @param externalMessageId external message id
     * @return true if if a message is new and marks it as seen, using the default owner user ID
     */
    public boolean markIfNew(String channelId, String externalMessageId) {
        return markIfNew(channelId, "admin", externalMessageId);
    }

    /**
     * Checks if a message is new and marks it as seen to prevent duplicate processing.
     *
     * @param channelId channel identifier
     * @param ownerUserId owner user id
     * @param externalMessageId external message id
     * @return true if if a message is new and marks it as seen to prevent duplicate processing
     */
    public boolean markIfNew(String channelId, String ownerUserId, String externalMessageId) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        Path file = runtimeStorageService.dedupFile(channel);
        Map<String, Object> wrapper = readJson(file);
        List<Map<String, Object>> messages = castMessages(wrapper.get("messages"));

        boolean exists =
            messages.stream().anyMatch(item -> externalMessageId.equals(String.valueOf(item.get("externalMessageId"))));
        if (exists) {
            return false;
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("externalMessageId", externalMessageId);
        entry.put("receivedAt", Instant.now().toString());
        messages.add(entry);
        if (messages.size() > MAX_MESSAGES) {
            messages = messages.subList(messages.size() - MAX_MESSAGES, messages.size());
        }
        writeJson(file, Map.of("messages", messages));
        return true;
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMessages(Object raw) {
        if (!(raw instanceof List<?> items)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            messages.add(normalized);
        }
        return messages;
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
            log.warn("Failed to read dedup file {}: {}", file, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void writeJson(Path file, Object payload) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write dedup file: " + file, e);
        }
    }
}
