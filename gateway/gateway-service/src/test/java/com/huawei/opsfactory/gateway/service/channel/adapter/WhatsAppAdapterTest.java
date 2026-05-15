/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel.adapter;

import static org.mockito.Mockito.mock;
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
 * Test coverage for Whats App Adapter.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class WhatsAppAdapterTest {
    private static final String OWNER_USER_ID = "alice@example.com";

    private ChannelConfigService channelConfigService;

    private WhatsAppAdapter adapter;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        channelConfigService = mock(ChannelConfigService.class);
        adapter = new WhatsAppAdapter(channelConfigService);
    }

    /**
     * Tests connected connectivity uses owner runtime.
     */
    @Test
    public void testConnectedConnectivityUsesOwnerRuntime() {
        when(channelConfigService.getChannel("whatsapp-main", OWNER_USER_ID))
            .thenReturn(channelWithStatus("connected", ""));

        StepVerifier.create(adapter.testConnectivity("whatsapp-main", OWNER_USER_ID))
            .expectNextMatches(result -> result.ok() && "WhatsApp Web session connected".equals(result.message()))
            .verifyComplete();

        verify(channelConfigService).recordEvent("whatsapp-main", OWNER_USER_ID, "info", "whatsapp.status",
            "WhatsApp Web session is connected");
    }

    private ChannelDetail channelWithStatus(String status, String lastError) {
        return new ChannelDetail("whatsapp-main", "WhatsApp Main", "whatsapp", true, "fo-copilot", OWNER_USER_ID,
            "2026-04-15T00:00:00Z", "2026-04-15T00:00:00Z", "",
            new ChannelConnectionConfig(status, "auth", "", "", lastError, "", "", ""),
            new ChannelVerificationResult(true, List.of()), List.of(), List.of());
    }
}
