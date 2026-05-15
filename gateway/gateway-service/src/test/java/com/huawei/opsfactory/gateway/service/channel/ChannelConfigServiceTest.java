/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectionConfig;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelReplyResult;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelUpsertRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test coverage for Channel Config Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class ChannelConfigServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path gatewayRoot;

    private ChannelConfigService service;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        gatewayRoot = tempFolder.getRoot().toPath().resolve("gateway");
        GatewayProperties properties = mock(GatewayProperties.class);
        when(properties.getGatewayRootPath()).thenReturn(gatewayRoot);

        AgentConfigService agentConfigService = mock(AgentConfigService.class);
        when(agentConfigService.findAgent("fo-copilot")).thenReturn(new AgentRegistryEntry("fo-copilot", "FO Copilot"));

        ChannelRuntimeStorageService runtimeStorageService = new ChannelRuntimeStorageService(properties);
        service = new ChannelConfigService(properties, agentConfigService, runtimeStorageService);
        service.init();
    }

    /**
     * Executes the create channel separates config and runtime state operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void createChannelSeparatesConfigAndRuntimeState() throws Exception {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");

        Path configDir = gatewayRoot.resolve("channels").resolve("whatsapp").resolve("whatsapp-main");
        Path runtimeDir = gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main");

        assertTrue(Files.exists(configDir.resolve("config.json")));
        assertFalse(Files.exists(configDir.resolve("bindings.json")));
        assertFalse(Files.exists(configDir.resolve("events.json")));
        assertFalse(Files.exists(configDir.resolve("inbound-dedup.json")));
        assertFalse(Files.exists(configDir.resolve("auth")));
        assertFalse(Files.exists(configDir.resolve("inbox")));
        assertFalse(Files.exists(configDir.resolve("processed")));
        assertFalse(Files.exists(configDir.resolve("outbox")));
        assertFalse(Files.exists(configDir.resolve("login.log")));
        assertFalse(Files.exists(configDir.resolve("login.pid")));
        assertFalse(Files.exists(configDir.resolve("whatsapp-debug.log")));

        assertTrue(Files.exists(runtimeDir.resolve("bindings.json")));
        assertTrue(Files.exists(runtimeDir.resolve("events.json")));
        assertTrue(Files.exists(runtimeDir.resolve("inbound-dedup.json")));

        @SuppressWarnings("unchecked")
        Map<String, Object> config = MAPPER.readValue(configDir.resolve("config.json").toFile(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> channelConfig = (Map<String, Object>) config.get("config");
        assertFalse(config.containsKey("ownerUserId"));
        assertEquals("auth", channelConfig.get("authStateDir"));
        assertFalse(channelConfig.containsKey("loginStatus"));
        assertFalse(channelConfig.containsKey("lastConnectedAt"));
        assertFalse(channelConfig.containsKey("selfPhone"));
    }

    /**
     * Executes the runtime state is read from user directory only operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void runtimeStateIsReadFromUserDirectoryOnly() throws Exception {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");

        Path oldState =
            gatewayRoot.resolve("channels").resolve("whatsapp").resolve("whatsapp-main").resolve("login-state.json");
        Files.writeString(oldState,
            MAPPER.writeValueAsString(Map.of("status", "connected", "selfPhone", "+10000000000")),
            StandardCharsets.UTF_8);

        ChannelDetail before = service.getChannel("whatsapp-main");
        assertEquals("disconnected", before.config().loginStatus());
        assertEquals("", before.config().selfPhone());

        Path runtimeState = gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main")
            .resolve("login-state.json");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "connected");
        payload.put("selfPhone", "+8613800000000");
        payload.put("lastConnectedAt", "2026-05-06T00:00:00Z");
        Files.writeString(runtimeState, MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8);

        ChannelDetail after = service.getChannel("whatsapp-main");
        assertEquals("connected", after.config().loginStatus());
        assertEquals("+8613800000000", after.config().selfPhone());
        assertEquals("2026-05-06T00:00:00Z", after.config().lastConnectedAt());
    }

    /**
     * Executes the shared config uses independent runtime per user operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void sharedConfigUsesIndependentRuntimePerUser() throws Exception {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");

        Path adminRuntime = gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main");
        Path aliceRuntime = gatewayRoot.resolve("users")
            .resolve("alice@example.com")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main");
        Files.createDirectories(adminRuntime);
        Files.createDirectories(aliceRuntime);
        Files.writeString(adminRuntime.resolve("login-state.json"),
            MAPPER.writeValueAsString(Map.of("status", "connected", "selfPhone", "+10000000000")),
            StandardCharsets.UTF_8);
        Files.writeString(aliceRuntime.resolve("login-state.json"),
            MAPPER.writeValueAsString(Map.of("status", "pending", "selfPhone", "+20000000000")),
            StandardCharsets.UTF_8);

        ChannelDetail adminView = service.getChannel("whatsapp-main", "admin");
        ChannelDetail aliceView = service.getChannel("whatsapp-main", "alice@example.com");

        assertEquals("admin", adminView.ownerUserId());
        assertEquals("connected", adminView.config().loginStatus());
        assertEquals("+10000000000", adminView.config().selfPhone());
        assertEquals("alice@example.com", aliceView.ownerUserId());
        assertEquals("pending", aliceView.config().loginStatus());
        assertEquals("+20000000000", aliceView.config().selfPhone());
        assertEquals("admin", service.listChannels("admin").get(0).ownerUserId());
        assertEquals("alice@example.com", service.listChannels("alice@example.com").get(0).ownerUserId());
    }

    /**
     * Executes the delete channel removes config and user runtime directories operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void deleteChannelRemovesConfigAndUserRuntimeDirectories() throws Exception {
        service.createChannel(upsertRequest("wechat-main", "wechat"), "admin");

        Path configDir = gatewayRoot.resolve("channels").resolve("wechat").resolve("wechat-main");
        Path runtimeDir =
            gatewayRoot.resolve("users").resolve("admin").resolve("channels").resolve("wechat").resolve("wechat-main");
        Path aliceRuntimeDir = gatewayRoot.resolve("users")
            .resolve("alice@example.com")
            .resolve("channels")
            .resolve("wechat")
            .resolve("wechat-main");
        Files.createDirectories(aliceRuntimeDir);
        assertTrue(Files.exists(configDir));
        assertTrue(Files.exists(runtimeDir));
        assertTrue(Files.exists(aliceRuntimeDir));

        service.deleteChannel("wechat-main");

        assertFalse(Files.exists(configDir));
        assertFalse(Files.exists(runtimeDir));
        assertFalse(Files.exists(aliceRuntimeDir));
    }

    /**
     * Executes the update channel rejects type changes operation.
     */
    @Test
    public void updateChannelRejectsTypeChanges() {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.updateChannel("whatsapp-main", upsertRequest("whatsapp-main", "wechat")));

        assertTrue(error.getMessage().contains("Channel type cannot be changed"));
        assertTrue(Files.exists(gatewayRoot.resolve("channels").resolve("whatsapp").resolve("whatsapp-main")));
        assertFalse(Files.exists(gatewayRoot.resolve("channels").resolve("wechat").resolve("whatsapp-main")));
    }

    /**
     * Executes the auth state dir cannot escape user runtime directory operation.
     */
    @Test
    public void authStateDirCannotEscapeUserRuntimeDirectory() {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");

        ChannelUpsertRequest request = new ChannelUpsertRequest("whatsapp-main", "whatsapp-main", "whatsapp", true,
            "fo-copilot", new ChannelConnectionConfig("", "../../../../channels/whatsapp/whatsapp-main/auth", "", "",
                "", "", "", ""));
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> service.updateChannel("whatsapp-main", request));

        assertTrue(error.getMessage().contains("authStateDir"));
    }

    /**
     * Executes the owner user id allows existing user id characters but rejects path traversal operation.
     */
    @Test
    public void ownerUserIdAllowsExistingUserIdCharactersButRejectsPathTraversal() {
        service.createChannel(upsertRequest("email-owner", "whatsapp"), "alice@example.com");

        Path runtimeDir = gatewayRoot.resolve("users")
            .resolve("alice@example.com")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("email-owner");
        assertTrue(Files.exists(runtimeDir.resolve("bindings.json")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.createChannel(upsertRequest("bad-owner", "whatsapp"), "../admin"));
        assertTrue(error.getMessage().contains("ownerUserId"));
        assertFalse(Files.exists(gatewayRoot.resolve("channels").resolve("whatsapp").resolve("bad-owner")));
    }

    /**
     * Executes the invalid config channel id is ignored operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void invalidConfigChannelIdIsIgnored() throws Exception {
        Path invalidConfigDir = gatewayRoot.resolve("channels").resolve("whatsapp").resolve("bad-channel");
        Files.createDirectories(invalidConfigDir);
        Files.writeString(invalidConfigDir.resolve("config.json"),
            MAPPER.writeValueAsString(Map.of("id", "../bad-channel", "name", "Bad Channel", "type", "whatsapp",
                "enabled", true, "defaultAgentId", "fo-copilot", "ownerUserId", "admin", "createdAt",
                "2026-05-06T00:00:00Z", "updatedAt", "2026-05-06T00:00:00Z", "config", Map.of("authStateDir", "auth"))),
            StandardCharsets.UTF_8);

        assertTrue(service.listChannels().isEmpty());
        assertFalse(Files.exists(gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("bad-channel")));
    }

    /**
     * Executes the binding and dedup write only to user runtime directory operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void bindingAndDedupWriteOnlyToUserRuntimeDirectory() throws Exception {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");
        GatewayProperties properties = mock(GatewayProperties.class);
        when(properties.getGatewayRootPath()).thenReturn(gatewayRoot);
        ChannelRuntimeStorageService runtimeStorageService = new ChannelRuntimeStorageService(properties);

        ChannelBindingService bindingService = new ChannelBindingService(service, runtimeStorageService);
        bindingService.ensureConversationBinding("whatsapp-main", "default", "+8613800000000", "+8613800000000", null,
            "direct");

        ChannelDedupService dedupService = new ChannelDedupService(service, runtimeStorageService);
        assertTrue(dedupService.markIfNew("whatsapp-main", "message-1"));

        Path configDir = gatewayRoot.resolve("channels").resolve("whatsapp").resolve("whatsapp-main");
        Path runtimeDir = gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main");

        assertFalse(Files.exists(configDir.resolve("bindings.json")));
        assertFalse(Files.exists(configDir.resolve("inbound-dedup.json")));
        assertTrue(Files.readString(runtimeDir.resolve("bindings.json")).contains("+8613800000000"));
        assertTrue(Files.readString(runtimeDir.resolve("inbound-dedup.json")).contains("message-1"));
    }

    /**
     * Executes the whatsapp login initializes user runtime directory only operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void whatsappLoginInitializesUserRuntimeDirectoryOnly() throws Exception {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");
        GatewayProperties properties = mock(GatewayProperties.class);
        when(properties.getGatewayRootPath()).thenReturn(gatewayRoot);
        ChannelRuntimeStorageService runtimeStorageService = new ChannelRuntimeStorageService(properties);
        WhatsAppWebLoginService loginService = new WhatsAppWebLoginService(service, runtimeStorageService);

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> loginService.startLogin("whatsapp-main"));
        assertTrue(error.getMessage().contains("WhatsApp Web helper not found"));

        Path configDir = gatewayRoot.resolve("channels").resolve("whatsapp").resolve("whatsapp-main");
        Path runtimeDir = gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main");

        assertFalse(Files.exists(configDir.resolve("auth")));
        assertFalse(Files.exists(configDir.resolve("inbox")));
        assertFalse(Files.exists(configDir.resolve("outbox")));
        assertFalse(Files.exists(configDir.resolve("login-state.json")));
        assertTrue(Files.exists(runtimeDir.resolve("auth")));
        assertTrue(Files.exists(runtimeDir.resolve("inbox")));
        assertTrue(Files.exists(runtimeDir.resolve("outbox").resolve("pending")));
        assertTrue(Files.exists(runtimeDir.resolve("login-state.json")));
        assertTrue(Files.readString(runtimeDir.resolve("login-state.json")).contains("pending"));
    }

    /**
     * Executes the whatsapp message pump uses user runtime directories only operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void whatsappMessagePumpUsesUserRuntimeDirectoriesOnly() throws Exception {
        service.createChannel(upsertRequest("whatsapp-main", "whatsapp"), "admin");
        GatewayProperties properties = mock(GatewayProperties.class);
        when(properties.getGatewayRootPath()).thenReturn(gatewayRoot);
        ChannelRuntimeStorageService runtimeStorageService = new ChannelRuntimeStorageService(properties);
        ChannelDedupService dedupService = new ChannelDedupService(service, runtimeStorageService);
        SessionBridgeService sessionBridgeService = mock(SessionBridgeService.class);
        WhatsAppWebLoginService loginService = mock(WhatsAppWebLoginService.class);
        WhatsAppMessagePumpService pumpService = new WhatsAppMessagePumpService(service, runtimeStorageService,
            dedupService, sessionBridgeService, loginService);

        when(sessionBridgeService.sendConversationText(eq("whatsapp-main"), eq("admin"), eq("default"),
            eq("+8613800000000"), eq("+8613800000000"), eq(null), eq("direct"), anyString()))
            .thenReturn(Mono.just(new ChannelReplyResult("whatsapp-main", "default", "+8613800000000", "+8613800000000",
                null, "direct", "admin", "fo-copilot", "session-1", "reply from agent")));

        Path configDir = gatewayRoot.resolve("channels").resolve("whatsapp").resolve("whatsapp-main");
        Path runtimeDir = gatewayRoot.resolve("users")
            .resolve("admin")
            .resolve("channels")
            .resolve("whatsapp")
            .resolve("whatsapp-main");
        Path inboxDir = runtimeDir.resolve("inbox");
        Files.createDirectories(inboxDir);
        Files.writeString(inboxDir.resolve("message-1.json"), MAPPER.writeValueAsString(Map.of("messageId", "message-1",
            "peerId", "+8613800000000", "conversationId", "+8613800000000", "text", "hello")), StandardCharsets.UTF_8);

        pumpService.pumpInbox();

        assertFalse(Files.exists(configDir.resolve("inbox")));
        assertFalse(Files.exists(configDir.resolve("processed")));
        assertFalse(Files.exists(configDir.resolve("outbox")));
        assertFalse(Files.exists(configDir.resolve("bindings.json")));
        assertFalse(Files.exists(configDir.resolve("inbound-dedup.json")));

        assertTrue(Files.exists(runtimeDir.resolve("inbound-dedup.json")));
        assertTrue(Files.readString(runtimeDir.resolve("inbound-dedup.json")).contains("message-1"));
        assertTrue(Files.exists(runtimeDir.resolve("processed").resolve("message-1-processed.json")));
        try (var stream = Files.list(runtimeDir.resolve("outbox").resolve("pending"))) {
            Optional<Path> outboxFile =
                stream.filter(path -> path.getFileName().toString().endsWith(".json")).findFirst();
            assertTrue(outboxFile.isPresent());
            assertTrue(Files.readString(outboxFile.get()).contains("reply from agent"));
        }
    }

    /**
     * Executes the wechat login initializes user runtime directory only operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void wechatLoginInitializesUserRuntimeDirectoryOnly() throws Exception {
        service.createChannel(upsertRequest("wechat-main", "wechat"), "admin");
        GatewayProperties properties = mock(GatewayProperties.class);
        when(properties.getGatewayRootPath()).thenReturn(gatewayRoot);
        ChannelRuntimeStorageService runtimeStorageService = new ChannelRuntimeStorageService(properties);
        WeChatLoginService loginService = new WeChatLoginService(service, runtimeStorageService);

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> loginService.startLogin("wechat-main"));
        assertTrue(error.getMessage().contains("WeChat helper not found"));

        Path configDir = gatewayRoot.resolve("channels").resolve("wechat").resolve("wechat-main");
        Path runtimeDir =
            gatewayRoot.resolve("users").resolve("admin").resolve("channels").resolve("wechat").resolve("wechat-main");

        assertFalse(Files.exists(configDir.resolve("auth")));
        assertFalse(Files.exists(configDir.resolve("inbox")));
        assertFalse(Files.exists(configDir.resolve("outbox")));
        assertFalse(Files.exists(configDir.resolve("login-state.json")));
        assertTrue(Files.exists(runtimeDir.resolve("auth")));
        assertTrue(Files.exists(runtimeDir.resolve("inbox")));
        assertTrue(Files.exists(runtimeDir.resolve("outbox").resolve("pending")));
        assertTrue(Files.exists(runtimeDir.resolve("login-state.json")));
        assertTrue(Files.readString(runtimeDir.resolve("login-state.json")).contains("pending"));
    }

    private ChannelUpsertRequest upsertRequest(String id, String type) {
        return new ChannelUpsertRequest(id, id, type, true, "fo-copilot",
            new ChannelConnectionConfig("connected", "auth", "old", "old", "old", "+1", "wxid", "Tester"));
    }
}
