package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

public class HostGroupServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private HostGroupService hostGroupService;
    private GatewayProperties properties;
    private Path groupsDir;

    @Before
    public void setUp() throws IOException {
        properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);

        hostGroupService = new HostGroupService(properties);
        hostGroupService.init();

        groupsDir = Path.of(tempFolder.getRoot().getAbsolutePath())
                .toAbsolutePath().normalize().resolve("gateway").resolve("data").resolve("host-groups");
    }

    // ── createGroup ──────────────────────────────────────────────

    @Test
    public void testCreateGroup_enabledByDefault() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        body.put("code", "PROD");

        Map<String, Object> result = hostGroupService.createGroup(body);

        assertNotNull(result.get("id"));
        assertEquals("PROD", result.get("name"));
        assertEquals("PROD", result.get("code"));
        assertEquals(true, result.get("enabled"));
    }

    @Test
    public void testCreateGroup_enabledFalse() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "TEST");
        body.put("code", "TEST");
        body.put("enabled", false);

        Map<String, Object> result = hostGroupService.createGroup(body);
        assertEquals(false, result.get("enabled"));
    }

    @Test
    public void testCreateGroup_enabledTrue() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "STAGING");
        body.put("enabled", true);

        Map<String, Object> result = hostGroupService.createGroup(body);
        assertEquals(true, result.get("enabled"));
    }

    // ── updateGroup ──────────────────────────────────────────────

    @Test
    public void testUpdateGroup_setEnabled() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        Map<String, Object> created = hostGroupService.createGroup(body);
        String id = (String) created.get("id");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("enabled", false);

        Map<String, Object> result = hostGroupService.updateGroup(id, updates);
        assertEquals(false, result.get("enabled"));
        assertEquals("PROD", result.get("name"));
    }

    @Test
    public void testUpdateGroup_reEnable() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        body.put("enabled", false);
        Map<String, Object> created = hostGroupService.createGroup(body);
        String id = (String) created.get("id");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("enabled", true);

        Map<String, Object> result = hostGroupService.updateGroup(id, updates);
        assertEquals(true, result.get("enabled"));
    }

    @Test
    public void testUpdateGroup_partialUpdatePreservesEnabled() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        body.put("enabled", false);
        Map<String, Object> created = hostGroupService.createGroup(body);
        String id = (String) created.get("id");

        // Update only the name — enabled should remain false
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "PROD-UPDATED");

        Map<String, Object> result = hostGroupService.updateGroup(id, updates);
        assertEquals("PROD-UPDATED", result.get("name"));
        assertEquals(false, result.get("enabled"));
    }

    @Test
    public void testUpdateGroup_defaultEnabledRemainsTrue() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        Map<String, Object> created = hostGroupService.createGroup(body);
        String id = (String) created.get("id");

        // Update description — enabled should remain default true
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("description", "Production environment");

        Map<String, Object> result = hostGroupService.updateGroup(id, updates);
        assertEquals(true, result.get("enabled"));
        assertEquals("Production environment", result.get("description"));
    }

    // ── getDisabledGroupIds ──────────────────────────────────────

    @Test
    public void testGetDisabledGroupIds_noDisabledGroups() {
        createGroup("g1", "PROD", null, true);
        createGroup("g2", "TEST", null, true);

        Set<String> disabled = hostGroupService.getDisabledGroupIds(hostGroupService.listGroups());
        assertTrue(disabled.isEmpty());
    }

    @Test
    public void testGetDisabledGroupIds_directlyDisabled() {
        createGroup("g1", "PROD", null, false);
        createGroup("g2", "TEST", null, true);

        Set<String> disabled = hostGroupService.getDisabledGroupIds(hostGroupService.listGroups());
        assertEquals(1, disabled.size());
        assertTrue(disabled.contains("g1"));
    }

    @Test
    public void testGetDisabledGroupIds_inheritedFromParent() {
        createGroup("g1", "PROD", null, false);
        createGroup("g1-1", "PROD-Sub1", "g1", true);
        createGroup("g1-1-1", "PROD-Sub1-Leaf", "g1-1", true);
        createGroup("g2", "TEST", null, true);

        Set<String> disabled = hostGroupService.getDisabledGroupIds(hostGroupService.listGroups());

        // g1 is directly disabled; g1-1 and g1-1-1 are inherited
        assertEquals(3, disabled.size());
        assertTrue(disabled.contains("g1"));
        assertTrue(disabled.contains("g1-1"));
        assertTrue(disabled.contains("g1-1-1"));
        assertFalse(disabled.contains("g2"));
    }

    @Test
    public void testGetDisabledGroupIds_deepInheritance() {
        // Root → A (disabled) → B → C (explicitly enabled=true) → D
        createGroup("root", "Root", null, true);
        createGroup("a", "A", "root", false);
        createGroup("b", "B", "a", true);
        createGroup("c", "C", "b", true);
        createGroup("d", "D", "c", true);

        Set<String> disabled = hostGroupService.getDisabledGroupIds(hostGroupService.listGroups());
        // a is disabled; b, c, d inherit disabled even though b/c have enabled=true
        assertTrue(disabled.contains("a"));
        assertTrue(disabled.contains("b"));
        assertTrue(disabled.contains("c"));
        assertTrue(disabled.contains("d"));
        assertFalse(disabled.contains("root"));
        assertEquals(4, disabled.size());
    }

    @Test
    public void testGetDisabledGroupIds_enabledMissingDefaultsToTrue() {
        // Group created without enabled field — should be treated as enabled
        createGroup("g1", "PROD", null, null);

        Set<String> disabled = hostGroupService.getDisabledGroupIds(hostGroupService.listGroups());
        assertTrue(disabled.isEmpty());
    }

    @Test
    public void testGetDisabledGroupIds_multipleRoots() {
        createGroup("g1", "PROD", null, false);
        createGroup("g1-1", "PROD-Sub", "g1", true);
        createGroup("g2", "TEST", null, true);
        createGroup("g2-1", "TEST-Sub", "g2", true);

        Set<String> disabled = hostGroupService.getDisabledGroupIds(hostGroupService.listGroups());
        assertEquals(2, disabled.size());
        assertTrue(disabled.contains("g1"));
        assertTrue(disabled.contains("g1-1"));
        assertFalse(disabled.contains("g2"));
        assertFalse(disabled.contains("g2-1"));
    }

    // ── Persistence: enabled state survives read-back ────────────

    @Test
    public void testEnabledStatePersistedAndReadBack() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        body.put("enabled", false);
        Map<String, Object> created = hostGroupService.createGroup(body);
        String id = (String) created.get("id");

        // Re-read from file
        Map<String, Object> readBack = hostGroupService.getGroup(id);
        assertEquals(false, readBack.get("enabled"));
    }

    @Test
    public void testUpdateEnabledPersistedAndReadBack() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "PROD");
        Map<String, Object> created = hostGroupService.createGroup(body);
        String id = (String) created.get("id");

        // Disable
        hostGroupService.updateGroup(id, Map.of("enabled", false));
        assertEquals(false, hostGroupService.getGroup(id).get("enabled"));

        // Re-enable
        hostGroupService.updateGroup(id, Map.of("enabled", true));
        assertEquals(true, hostGroupService.getGroup(id).get("enabled"));
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void createGroup(String id, String name, String parentId, Boolean enabled) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", id);
        group.put("name", name);
        group.put("parentId", parentId);
        group.put("description", "");
        group.put("code", "");
        if (enabled != null) {
            group.put("enabled", enabled);
        }
        group.put("createdAt", "2026-01-01T00:00:00Z");
        group.put("updatedAt", "2026-01-01T00:00:00Z");

        try {
            Path file = groupsDir.resolve(id + ".json");
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(group);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
