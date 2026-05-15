/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.channel.ChannelAdapter;
import com.huawei.opsfactory.gateway.service.channel.ChannelAdapterRegistry;
import com.huawei.opsfactory.gateway.service.channel.ChannelConfigService;
import com.huawei.opsfactory.gateway.service.channel.WeChatLoginService;
import com.huawei.opsfactory.gateway.service.channel.WhatsAppMessagePumpService;
import com.huawei.opsfactory.gateway.service.channel.WhatsAppWebLoginService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectionConfig;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectivityResult;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelLoginState;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelVerificationResult;

import reactor.core.publisher.Mono;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

/**
 * Test coverage for Channel Admin Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebFluxTest(ChannelAdminController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class ChannelAdminControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ChannelConfigService channelConfigService;

    @MockBean
    private ChannelAdapterRegistry channelAdapterRegistry;

    @MockBean
    private WhatsAppWebLoginService whatsAppWebLoginService;

    @MockBean
    private WhatsAppMessagePumpService whatsAppMessagePumpService;

    @MockBean
    private WeChatLoginService weChatLoginService;

    @MockBean
    private PrewarmService prewarmService;

    /**
     * Tests get login state dispatches to we chat service.
     */
    @Test
    public void testGetLoginStateDispatchesToWeChatService() {
        when(channelConfigService.getChannel("wechat-main", "admin")).thenReturn(channelDetail("wechat"));
        when(weChatLoginService.getLoginState("wechat-main", "admin")).thenReturn(new ChannelLoginState("wechat-main",
            "connected", "WeChat session connected", "auth", "wxid_123", "", "", "", null));

        webTestClient.get()
            .uri("/gateway/channels/wechat-main/login-state")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.state.status")
            .isEqualTo("connected")
            .jsonPath("$.state.message")
            .isEqualTo("WeChat session connected");

        verify(weChatLoginService).getLoginState("wechat-main", "admin");
        verify(whatsAppWebLoginService, never()).getLoginState(Mockito.anyString(), Mockito.anyString());
    }

    /**
     * Tests start login dispatches to we chat service.
     */
    @Test
    public void testStartLoginDispatchesToWeChatService() {
        when(channelConfigService.getChannel("wechat-main", "admin")).thenReturn(channelDetail("wechat"));
        when(weChatLoginService.startLogin("wechat-main", "admin")).thenReturn(new ChannelLoginState("wechat-main",
            "pending", "WeChat QR login is pending", "auth", "wxid_123", "", "", "", "https://example.com/qr.png"));

        webTestClient.post()
            .uri("/gateway/channels/wechat-main/login")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.state.status")
            .isEqualTo("pending");

        verify(weChatLoginService).startLogin("wechat-main", "admin");
        verify(whatsAppWebLoginService, never()).startLogin(eq("wechat-main"), eq("admin"));
    }

    /**
     * Tests probe passes current user to adapter.
     */
    @Test
    public void testProbePassesCurrentUserToAdapter() {
        ChannelAdapter adapter = Mockito.mock(ChannelAdapter.class);
        when(channelConfigService.getChannel("wechat-main", "admin")).thenReturn(channelDetail("wechat"));
        when(channelAdapterRegistry.require("wechat")).thenReturn(adapter);
        when(adapter.testConnectivity("wechat-main", "admin"))
            .thenReturn(Mono.just(new ChannelConnectivityResult(true, "connected")));

        webTestClient.post()
            .uri("/gateway/channels/wechat-main/probe")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.connectivity.ok")
            .isEqualTo(true);

        verify(adapter).testConnectivity("wechat-main", "admin");
    }

    /**
     * Tests create channel unexpected failure is sanitized.
     */
    @Test
    public void testCreateChannel_unexpectedFailureIsSanitized() {
        when(channelConfigService.createChannel(any(), eq("admin"))).thenThrow(new IllegalStateException("disk full"));

        webTestClient.post()
            .uri("/gateway/channels")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "id": "wechat-main",
                  "name": "WeChat Main",
                  "type": "wechat",
                  "defaultAgentId": "fo-copilot",
                  "config": {
                    "authStateDir": "auth"
                  }
                }
                """)
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false)
            .jsonPath("$.error")
            .isEqualTo("Internal server error");
    }

    /**
     * Tests logout falls back to disconnected state when helper cleanup fails.
     */
    @Test
    public void testLogout_fallsBackToDisconnectedStateWhenHelperCleanupFails() {
        when(channelConfigService.getChannel("wechat-main", "admin")).thenReturn(channelDetail("wechat"));
        when(channelConfigService.resetChannelRuntimeState("wechat-main", "admin")).thenReturn(channelDetail("wechat"));
        when(weChatLoginService.logout("wechat-main", "admin"))
            .thenThrow(new IllegalStateException("helper process still running"));

        webTestClient.post()
            .uri("/gateway/channels/wechat-main/logout")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.state.status")
            .isEqualTo("disconnected")
            .jsonPath("$.state.message")
            .isEqualTo("WeChat login required");
    }

    private ChannelDetail channelDetail(String type) {
        return new ChannelDetail("wechat-main", "WeChat Main", type, true, "fo-copilot", "admin",
            "2026-04-15T00:00:00Z", "2026-04-15T00:00:00Z", "",
            new ChannelConnectionConfig("disconnected", "auth", "", "", "", "", "", ""),
            new ChannelVerificationResult(true, List.of()), List.of(), List.of());
    }
}
