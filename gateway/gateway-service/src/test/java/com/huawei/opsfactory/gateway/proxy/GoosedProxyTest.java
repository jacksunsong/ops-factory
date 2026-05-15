/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Test;

/**
 * Test coverage for Goosed Proxy.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class GoosedProxyTest {
    private GoosedProxy proxy;

    private GoosedProxy proxyTls;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        GatewayProperties properties = new GatewayProperties();
        properties.setSecretKey("test-key");
        properties.setGooseTls(false);
        proxy = new GoosedProxy(properties);

        GatewayProperties tlsProps = new GatewayProperties();
        tlsProps.setSecretKey("test-key");
        tlsProps.setGooseTls(true);
        proxyTls = new GoosedProxy(tlsProps);
    }

    /**
     * Tests web client not null.
     */
    @Test
    public void testWebClientNotNull() {
        assertNotNull(proxy.getWebClient());
    }

    /**
     * Tests goosed base url tls disabled uses http.
     */
    @Test
    public void testGoosedBaseUrl_tlsDisabled_usesHttp() {
        assertEquals("http://127.0.0.1:9999", proxy.goosedBaseUrl(9999));
    }

    /**
     * Tests goosed base url tls enabled uses https.
     */
    @Test
    public void testGoosedBaseUrl_tlsEnabled_usesHttps() {
        assertEquals("https://127.0.0.1:9999", proxyTls.goosedBaseUrl(9999));
    }

    /**
     * Tests web client not null tls enabled.
     */
    @Test
    public void testWebClientNotNull_tlsEnabled() {
        assertNotNull(proxyTls.getWebClient());
    }
}
