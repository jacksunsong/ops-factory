/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.adapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.service.channel.ChannelConfigService;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelConnectionConfig;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelDetail;
import com.huawei.opsfactory.gateway.service.channel.model.ChannelVerificationResult;

import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Test coverage for We Chat Adapter.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class WeChatAdapterTest {
    private static final String OWNER_USER_ID = "alice@example.com";

    private ChannelConfigService channelConfigService;

    private WeChatAdapter adapter;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        channelConfigService = mock(ChannelConfigService.class);
        adapter = new WeChatAdapter(channelConfigService);
    }

    /**
     * Tests connected connectivity.
     */
    @Test
    public void testConnectedConnectivity() {
        when(channelConfigService.getChannel("wechat-main", OWNER_USER_ID))
            .thenReturn(channelWithStatus("connected", ""));

        StepVerifier.create(adapter.testConnectivity("wechat-main", OWNER_USER_ID))
            .expectNextMatches(result -> result.ok() && "WeChat session connected".equals(result.message()))
            .verifyComplete();

        verify(channelConfigService).recordEvent("wechat-main", OWNER_USER_ID, "info", "wechat.status",
            "WeChat session is connected");
    }

    /**
     * Tests pending connectivity.
     */
    @Test
    public void testPendingConnectivity() {
        when(channelConfigService.getChannel("wechat-main", OWNER_USER_ID))
            .thenReturn(channelWithStatus("pending", ""));

        StepVerifier.create(adapter.testConnectivity("wechat-main", OWNER_USER_ID))
            .expectNextMatches(result -> !result.ok() && "WeChat QR login is pending".equals(result.message()))
            .verifyComplete();

        verify(channelConfigService, never()).recordEvent("wechat-main", OWNER_USER_ID, "info", "wechat.status",
            "WeChat session is connected");
    }

    /**
     * Tests error connectivity uses last error.
     */
    @Test
    public void testErrorConnectivityUsesLastError() {
        when(channelConfigService.getChannel("wechat-main", OWNER_USER_ID))
            .thenReturn(channelWithStatus("error", "session expired"));

        StepVerifier.create(adapter.testConnectivity("wechat-main", OWNER_USER_ID))
            .expectNextMatches(result -> !result.ok() && "session expired".equals(result.message()))
            .verifyComplete();
    }

    private ChannelDetail channelWithStatus(String status, String lastError) {
        return new ChannelDetail("wechat-main", "WeChat Main", "wechat", true, "fo-copilot", OWNER_USER_ID,
            "2026-04-15T00:00:00Z", "2026-04-15T00:00:00Z", "",
            new ChannelConnectionConfig(status, "auth", "", "", lastError, "", "wxid_123", "Tester"),
            new ChannelVerificationResult(true, List.of()), List.of(), List.of());
    }
}
