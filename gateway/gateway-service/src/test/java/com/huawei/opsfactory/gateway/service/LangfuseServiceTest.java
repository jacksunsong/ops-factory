/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Test;

/**
 * Test coverage for Langfuse Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class LangfuseServiceTest {

    /**
     * Tests is configured all set.
     */
    @Test
    public void testIsConfigured_allSet() {
        GatewayProperties props = new GatewayProperties();
        GatewayProperties.Langfuse langfuse = new GatewayProperties.Langfuse();
        langfuse.setHost("http://langfuse.example.com");
        langfuse.setPublicKey("pk-123");
        langfuse.setSecretKey("sk-456");
        props.setLangfuse(langfuse);

        LangfuseService service = new LangfuseService(props);
        assertTrue(service.isConfigured());
    }

    /**
     * Tests is configured missing host.
     */
    @Test
    public void testIsConfigured_missingHost() {
        GatewayProperties props = new GatewayProperties();
        GatewayProperties.Langfuse langfuse = new GatewayProperties.Langfuse();
        langfuse.setHost("");
        langfuse.setPublicKey("pk-123");
        langfuse.setSecretKey("sk-456");
        props.setLangfuse(langfuse);

        LangfuseService service = new LangfuseService(props);
        assertFalse(service.isConfigured());
    }

    /**
     * Tests is configured defaults.
     */
    @Test
    public void testIsConfigured_defaults() {
        GatewayProperties props = new GatewayProperties();
        LangfuseService service = new LangfuseService(props);
        assertFalse(service.isConfigured());
    }
}
