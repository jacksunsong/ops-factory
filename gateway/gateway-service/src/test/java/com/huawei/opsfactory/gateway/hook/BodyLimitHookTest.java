/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test coverage for Body Limit Hook.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class BodyLimitHookTest {
    private BodyLimitHook hook;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        GatewayProperties properties = new GatewayProperties();
        // 1MB limit for testing
        properties.getUpload().setMaxFileSizeMb(1);
        hook = new BodyLimitHook(properties);
    }

    /**
     * Tests small body passes.
     */
    @Test
    public void testSmallBody_passes() {
        HookContext ctx = new HookContext("{\"message\": \"hello\"}", "agent1", "user1");
        StepVerifier.create(hook.process(ctx)).expectNext(ctx).verifyComplete();
    }

    /**
     * Tests oversized body fails.
     */
    @Test
    public void testOversizedBody_fails() {
        // Create a body larger than 1MB * 4/3 ≈ 1.33MB
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1_500_000; i++) {
            sb.append('x');
        }
        HookContext ctx = new HookContext(sb.toString(), "agent1", "user1");

        StepVerifier.create(hook.process(ctx)).expectError(ResponseStatusException.class).verify();
    }

    /**
     * Tests null body passes.
     */
    @Test
    public void testNullBody_passes() {
        HookContext ctx = new HookContext(null, "agent1", "user1");
        StepVerifier.create(hook.process(ctx)).expectNext(ctx).verifyComplete();
    }
}
