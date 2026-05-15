/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test coverage for Agent Registry Entry.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AgentRegistryEntryTest {

    /**
     * Tests record accessors.
     */
    @Test
    public void testRecordAccessors() {
        AgentRegistryEntry entry = new AgentRegistryEntry("kb-agent", "KB Agent");
        assertEquals("kb-agent", entry.id());
        assertEquals("KB Agent", entry.name());
    }

    /**
     * Tests record equality.
     */
    @Test
    public void testRecordEquality() {
        AgentRegistryEntry a = new AgentRegistryEntry("a", "A");
        AgentRegistryEntry b = new AgentRegistryEntry("a", "A");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * Tests record accessors with different values.
     */
    @Test
    public void testRecordAccessors_withDifferentValues() {
        AgentRegistryEntry entry = new AgentRegistryEntry("test-agent", "Test Agent");
        assertEquals("test-agent", entry.id());
        assertEquals("Test Agent", entry.name());
    }
}
