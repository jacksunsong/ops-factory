/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelBinding;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelReplyResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges external channel conversations to internal agent sessions, handling session creation, SSE streaming, and
 * reply extraction.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class SessionBridgeService {
    private static final Logger log = LoggerFactory.getLogger(SessionBridgeService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelConfigService channelConfigService;

    private final ChannelBindingService channelBindingService;

    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    private final AgentConfigService agentConfigService;

    private final WebClient webClient;

    /**
     * Creates the session bridge service instance.
     */
    public SessionBridgeService(ChannelConfigService channelConfigService, ChannelBindingService channelBindingService,
        InstanceManager instanceManager, GoosedProxy goosedProxy, AgentConfigService agentConfigService) {
        this.channelConfigService = channelConfigService;
        this.channelBindingService = channelBindingService;
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.agentConfigService = agentConfigService;
        this.webClient = goosedProxy.getWebClient();
    }

    /**
     * Ensures a session exists for the given channel and external user ID, creating one if necessary.
     *
     * @param channelId ensures a session exists for the given channel and external user ID, creating one if necessary
     * @param externalUserId ensures a session exists for the given channel and external user ID, creating one if
     *        necessary
     * @return the ensures a session exists for the given channel and external user ID, creating one if necessary
     */
    public Mono<ChannelBinding> ensureSession(String channelId, String externalUserId) {
        return ensureConversationSession(channelId, "admin", "default", externalUserId, externalUserId, null, "direct");
    }

    /**
     * Ensures a conversation session exists using the default owner user ID.
     *
     * @param channelId ensures a conversation session exists using the default owner user ID
     * @param accountId ensures a conversation session exists using the default owner user ID
     * @param peerId ensures a conversation session exists using the default owner user ID
     * @param conversationId ensures a conversation session exists using the default owner user ID
     * @param threadId ensures a conversation session exists using the default owner user ID
     * @param conversationType ensures a conversation session exists using the default owner user ID
     * @return the ensures a conversation session exists using the default owner user ID
     */
    public Mono<ChannelBinding> ensureConversationSession(String channelId, String accountId, String peerId,
        String conversationId, String threadId, String conversationType) {
        return ensureConversationSession(channelId, "admin", accountId, peerId, conversationId, threadId,
            conversationType);
    }

    /**
     * Ensures a conversation session exists for the given channel, creating a binding and starting a session if needed.
     *
     * @param channelId ensures a conversation session exists for the given channel, creating a binding and starting a
     *        session if needed
     * @param ownerUserId ensures a conversation session exists for the given channel, creating a binding and starting a
     *        session if needed
     * @param accountId ensures a conversation session exists for the given channel, creating a binding and starting a
     *        session if needed
     * @param peerId ensures a conversation session exists for the given channel, creating a binding and starting a
     *        session if needed
     * @param conversationId ensures a conversation session exists for the given channel, creating a binding and
     *        starting a session if needed
     * @param threadId ensures a conversation session exists for the given channel, creating a binding and starting a
     *        session if needed
     * @param conversationType ensures a conversation session exists for the given channel, creating a binding and
     *        starting a session if needed
     * @return the ensures a conversation session exists for the given channel, creating a binding and starting a
     *         session if needed
     */
    public Mono<ChannelBinding> ensureConversationSession(String channelId, String ownerUserId, String accountId,
        String peerId, String conversationId, String threadId, String conversationType) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        ChannelBinding binding = channelBindingService.ensureConversationBinding(channelId, ownerUserId, accountId,
            peerId, conversationId, threadId, conversationType);
        if (binding.sessionId() != null && !binding.sessionId().isBlank()) {
            return Mono.just(binding);
        }

        String effectiveOwnerUserId = binding.ownerUserId() == null || binding.ownerUserId().isBlank()
            ? channel.ownerUserId() : binding.ownerUserId();
        return startSession(channel.defaultAgentId(), effectiveOwnerUserId)
            .map(sessionId -> channelBindingService.attachConversationSession(channelId, effectiveOwnerUserId,
                accountId, peerId, conversationId, threadId, conversationType, sessionId, channel.defaultAgentId()));
    }

    /**
     * Sends text to a channel conversation using the default owner user ID and direct conversation parameters.
     *
     * @param channelId unique identifier of the target channel
     * @param externalUserId external user ID of the conversation peer
     * @param text message text to send
     * @return the reply result from the channel adapter
     */
    public Mono<ChannelReplyResult> sendText(String channelId, String externalUserId, String text) {
        return sendConversationText(channelId, "admin", "default", externalUserId, externalUserId, null, "direct",
            text);
    }

    /**
     * Sends text to a conversation session using the default owner user ID.
     *
     * @param channelId sends text to a conversation session using the default owner user ID
     * @param accountId sends text to a conversation session using the default owner user ID
     * @param peerId sends text to a conversation session using the default owner user ID
     * @param conversationId sends text to a conversation session using the default owner user ID
     * @param threadId sends text to a conversation session using the default owner user ID
     * @param conversationType sends text to a conversation session using the default owner user ID
     * @param text sends text to a conversation session using the default owner user ID
     * @return the sends text to a conversation session using the default owner user ID
     */
    public Mono<ChannelReplyResult> sendConversationText(String channelId, String accountId, String peerId,
        String conversationId, String threadId, String conversationType, String text) {
        return sendConversationText(channelId, "admin", accountId, peerId, conversationId, threadId, conversationType,
            text);
    }

    /**
     * Sends text to a conversation session, ensuring the session exists and extracting the agent reply.
     *
     * @param channelId sends text to a conversation session, ensuring the session exists and extracting the agent reply
     * @param ownerUserId sends text to a conversation session, ensuring the session exists and extracting the agent
     *        reply
     * @param accountId sends text to a conversation session, ensuring the session exists and extracting the agent reply
     * @param peerId sends text to a conversation session, ensuring the session exists and extracting the agent reply
     * @param conversationId sends text to a conversation session, ensuring the session exists and extracting the agent
     *        reply
     * @param threadId sends text to a conversation session, ensuring the session exists and extracting the agent reply
     * @param conversationType sends text to a conversation session, ensuring the session exists and extracting the
     *        agent reply
     * @param text sends text to a conversation session, ensuring the session exists and extracting the agent reply
     * @return the sends text to a conversation session, ensuring the session exists and extracting the agent reply
     */
    public Mono<ChannelReplyResult> sendConversationText(String channelId, String ownerUserId, String accountId,
        String peerId, String conversationId, String threadId, String conversationType, String text) {
        ChannelDetail channel = requireChannel(channelId, ownerUserId);
        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("Text is required"));
        }

        return ensureConversationSession(channelId, ownerUserId, accountId, peerId, conversationId, threadId,
            conversationType).flatMap(binding -> {
                channelBindingService.markConversationInbound(channelId, ownerUserId, accountId, conversationId,
                    threadId);
                String effectiveOwnerUserId = binding.ownerUserId() == null || binding.ownerUserId().isBlank()
                    ? channel.ownerUserId() : binding.ownerUserId();
                return sendTextToSession(binding.agentId(), effectiveOwnerUserId, binding.sessionId(), text.trim())
                    .onErrorResume(WebClientResponseException.class, error -> {
                        if (error.getStatusCode().value() != 404) {
                            return Mono.error(error);
                        }
                        return startSession(binding.agentId(), effectiveOwnerUserId)
                            .map(sessionId -> channelBindingService.attachConversationSession(channelId,
                                effectiveOwnerUserId, accountId, peerId, conversationId, threadId, conversationType,
                                sessionId, binding.agentId()))
                            .flatMap(rebound -> sendTextToSession(rebound.agentId(), effectiveOwnerUserId,
                                rebound.sessionId(), text.trim()));
                    })
                    .onErrorResume(IllegalStateException.class, error -> {
                        String message = error.getMessage() == null ? "" : error.getMessage();
                        if (!message.contains("404")) {
                            return Mono.error(error);
                        }
                        return startSession(binding.agentId(), effectiveOwnerUserId)
                            .map(sessionId -> channelBindingService.attachConversationSession(channelId,
                                effectiveOwnerUserId, accountId, peerId, conversationId, threadId, conversationType,
                                sessionId, binding.agentId()))
                            .flatMap(rebound -> sendTextToSession(rebound.agentId(), effectiveOwnerUserId,
                                rebound.sessionId(), text.trim()));
                    })
                    .map(replyText -> {
                        channelBindingService.markConversationOutbound(channelId, effectiveOwnerUserId, accountId,
                            conversationId, threadId);
                        channelConfigService.recordEvent(channelId, effectiveOwnerUserId, "info", "session.reply",
                            "Delivered text reply for session " + binding.sessionId());
                        return new ChannelReplyResult(channelId, binding.accountId(), binding.peerId(),
                            binding.conversationId(), binding.threadId(), binding.conversationType(),
                            effectiveOwnerUserId, binding.agentId(), binding.sessionId(), replyText);
                    });
            });
    }

    private Mono<String> startSession(String agentId, String ownerUserId) {
        Path workingDir = agentConfigService.getUserAgentDir(ownerUserId, agentId).toAbsolutePath().normalize();
        String requestBody;
        try {
            requestBody = MAPPER.writeValueAsString(Map.of("working_dir", workingDir.toString()));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to build session start payload", e));
        }

        return instanceManager.getOrSpawn(agentId, ownerUserId)
            .flatMap(
                instance -> goosedProxy
                    .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/start", requestBody, 120,
                        instance.getSecretKey())
                    .flatMap(startResponse -> {
                        String sessionId = extractSessionId(startResponse);
                        String resumeBody;
                        try {
                            resumeBody = MAPPER
                                .writeValueAsString(Map.of("session_id", sessionId, "load_model_and_extensions", true));
                        } catch (JsonProcessingException e) {
                            return Mono.error(new IllegalStateException("Failed to build session resume payload", e));
                        }
                        return goosedProxy
                            .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", resumeBody, 120,
                                instance.getSecretKey())
                            .thenReturn(sessionId);
                    }));
    }

    private Mono<String> sendTextToSession(String agentId, String ownerUserId, String sessionId, String text) {
        return instanceManager.getOrSpawn(agentId, ownerUserId)
            .flatMap(instance -> resumeSession(instance, sessionId).thenMany(streamReply(instance, sessionId, text))
                .collectList()
                .map(events -> extractFinalAssistantText(events, sessionId)));
    }

    private Mono<String> resumeSession(ManagedInstance instance, String sessionId) {
        try {
            String resumeBody =
                MAPPER.writeValueAsString(Map.of("session_id", sessionId, "load_model_and_extensions", true));
            return goosedProxy
                .fetchJson(instance.getPort(), HttpMethod.POST, "/agent/resume", resumeBody, 120,
                    instance.getSecretKey())
                .thenReturn(sessionId);
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to build session resume payload", e));
        }
    }

    private Flux<Map<String, Object>> streamReply(ManagedInstance instance, String sessionId, String text) {
        String requestId = UUID.randomUUID().toString();
        String body;
        try {
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("created", Math.floorDiv(System.currentTimeMillis(), 1000));
            userMessage.put("content", List.of(Map.of("type", "text", "text", text)));
            userMessage.put("metadata", Map.of("userVisible", true, "agentVisible", true));

            body = MAPPER.writeValueAsString(Map.of("request_id", requestId, "user_message", userMessage));
        } catch (JsonProcessingException e) {
            return Flux.error(new IllegalStateException("Failed to build reply payload", e));
        }

        Mono<Void> submit = webClient.post()
            .uri(goosedSessionUrl(instance, sessionId, "reply"))
            .header("x-secret-key", instance.getSecretKey())
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .then();

        Flux<Map<String, Object>> events = webClient.get()
            .uri(goosedSessionUrl(instance, sessionId, "events"))
            .header("x-secret-key", instance.getSecretKey())
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(DataBuffer.class)
            .transform(this::decodeSseEvents)
            .timeout(Duration.ofMinutes(5))
            .map(this::parseEventJson)
            .filter(event -> belongsToRequest(event, requestId))
            .takeUntil(event -> {
                String type = String.valueOf(event.getOrDefault("type", ""));
                return "Finish".equals(type) || "Error".equals(type);
            });

        return Flux.merge(events, submit.thenMany(Flux.empty()));
    }

    private String goosedSessionUrl(ManagedInstance instance, String sessionId, String suffix) {
        return goosedProxy.goosedBaseUrl(instance.getPort()) + "/sessions/"
            + UriUtils.encodePathSegment(sessionId, StandardCharsets.UTF_8) + "/" + suffix;
    }

    private boolean belongsToRequest(Map<String, Object> event, String requestId) {
        Object type = event.get("type");
        if ("ActiveRequests".equals(type) || "Ping".equals(type)) {
            return false;
        }
        Object chatRequestId = event.get("chat_request_id");
        Object eventRequestId = event.get("request_id");
        if (chatRequestId == null && eventRequestId == null) {
            return true;
        }
        return requestId.equals(String.valueOf(chatRequestId)) || requestId.equals(String.valueOf(eventRequestId));
    }

    private Flux<String> decodeSseEvents(Flux<DataBuffer> buffers) {
        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();
            buffers.subscribe(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                buffer.append(new String(bytes, StandardCharsets.UTF_8));

                int separatorIndex;
                while ((separatorIndex = buffer.indexOf("\n\n")) >= 0) {
                    String eventBlock = buffer.substring(0, separatorIndex);
                    buffer.delete(0, separatorIndex + 2);

                    StringBuilder dataLines = new StringBuilder();
                    for (String line : eventBlock.split("\n")) {
                        String trimmed = line.replace("\r", "");
                        if (trimmed.startsWith("data:")) {
                            if (!dataLines.isEmpty()) {
                                dataLines.append('\n');
                            }
                            dataLines.append(trimmed.substring(5).trim());
                        }
                    }
                    if (!dataLines.isEmpty()) {
                        sink.next(dataLines.toString());
                    }
                }
            }, sink::error, sink::complete);
        });
    }

    private Map<String, Object> parseEventJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse SSE event: " + json, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractFinalAssistantText(List<Map<String, Object>> events, String sessionId) {
        StringBuilder output = new StringBuilder();

        for (Map<String, Object> event : events) {
            Object typeObj = event.get("type");
            String type = typeObj != null ? String.valueOf(typeObj) : "";

            if ("Error".equals(type)) {
                Object error = event.get("error");
                throw new IllegalStateException(error != null ? String.valueOf(error) : "Unknown reply error");
            }

            if (!"Message".equals(type)) {
                continue;
            }

            Object rawMessage = event.get("message");
            if (!(rawMessage instanceof Map<?, ?> message)) {
                continue;
            }

            Object role = message.get("role");
            if (!"assistant".equals(role)) {
                continue;
            }

            Object rawMetadata = message.get("metadata");
            if (rawMetadata instanceof Map<?, ?> metadata) {
                Object userVisible = metadata.get("userVisible");
                if (Boolean.FALSE.equals(userVisible)) {
                    continue;
                }
            }

            Object rawContent = message.get("content");
            if (!(rawContent instanceof List<?> contentItems)) {
                continue;
            }

            for (Object item : contentItems) {
                if (!(item instanceof Map<?, ?> content)) {
                    continue;
                }
                if (!"text".equals(content.get("type"))) {
                    continue;
                }
                Object textPart = content.get("text");
                if (textPart != null) {
                    output.append(textPart);
                }
            }
        }

        String reply = output.toString().trim();
        if (reply.isBlank()) {
            log.warn("No assistant text extracted for session {}", sessionId);
        }
        return reply;
    }

    private String extractSessionId(String startResponse) {
        Map<String, Object> map;
        try {
            map = MAPPER.readValue(startResponse, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse session start response", e);
        }
        Object id = map.get("id");
        if (id == null) {
            throw new IllegalStateException("Session ID missing from start response");
        }
        return id.toString();
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
}
