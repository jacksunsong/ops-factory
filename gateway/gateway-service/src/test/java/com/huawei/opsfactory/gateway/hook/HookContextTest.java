/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test coverage for Hook Context.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class HookContextTest {

    /**
     * Tests constructor.
     */
    @Test
    public void testConstructor() {
        HookContext ctx = new HookContext("{}", "agent1", "user1");
        assertEquals("{}", ctx.getBody());
        assertEquals("agent1", ctx.getAgentId());
        assertEquals("user1", ctx.getUserId());
        assertNotNull(ctx.getState());
        assertTrue(ctx.getState().isEmpty());
    }

    /**
     * Tests set body.
     */
    @Test
    public void testSetBody() {
        HookContext ctx = new HookContext("original", "agent1", "user1");
        ctx.setBody("modified");
        assertEquals("modified", ctx.getBody());
    }

    /**
     * Tests state.
     */
    @Test
    public void testState() {
        HookContext ctx = new HookContext("{}", "agent1", "user1");
        ctx.getState().put("key", "value");
        assertEquals("value", ctx.getState().get("key"));
    }
}
