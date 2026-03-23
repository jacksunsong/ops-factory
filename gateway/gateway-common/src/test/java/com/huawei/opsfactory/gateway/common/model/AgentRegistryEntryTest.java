package com.huawei.opsfactory.gateway.common.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AgentRegistryEntryTest {

    @Test
    public void testRecordAccessors() {
        AgentRegistryEntry entry = new AgentRegistryEntry("kb-agent", "KB Agent");
        assertEquals("kb-agent", entry.id());
        assertEquals("KB Agent", entry.name());
    }

    @Test
    public void testRecordEquality() {
        AgentRegistryEntry a = new AgentRegistryEntry("a", "A");
        AgentRegistryEntry b = new AgentRegistryEntry("a", "A");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testRecordAccessors_withDifferentValues() {
        AgentRegistryEntry entry = new AgentRegistryEntry("test-agent", "Test Agent");
        assertEquals("test-agent", entry.id());
        assertEquals("Test Agent", entry.name());
    }
}
