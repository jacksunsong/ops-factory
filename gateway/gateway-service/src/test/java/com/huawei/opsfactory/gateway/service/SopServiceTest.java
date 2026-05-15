/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Sop Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class SopServiceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private SopService sopService;

    private Path sopsDir;

    /**
     * Sets the up.
     *
     * @throws IOException if the operation fails
     */
    @Before
    public void setUp() throws IOException {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);

        CommandWhitelistService whitelistService = new CommandWhitelistService(properties);
        whitelistService.init();

        sopService = new SopService(properties, whitelistService);
        sopService.init();

        sopsDir = Path.of(tempFolder.getRoot().getAbsolutePath())
            .toAbsolutePath()
            .normalize()
            .resolve("gateway")
            .resolve("data")
            .resolve("sops");
    }

    // ── listSops ─────────────────────────────────────────────────

    /**
     * Tests list sops empty.
     */
    @Test
    public void testListSops_empty() {
        List<Map<String, Object>> sops = sopService.listSops();
        assertTrue(sops.isEmpty());
    }

    /**
     * Tests list sops returns all.
     */
    @Test
    public void testListSops_returnsAll() {
        createSop("sop-1", "SOP1", "desc1");
        createSop("sop-2", "SOP2", "desc2");

        List<Map<String, Object>> sops = sopService.listSops();
        assertEquals(2, sops.size());
    }

    /**
     * Tests list sops skips corrupt file.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListSops_skipsCorruptFile() throws IOException {
        createSop("sop-1", "SOP1", "desc1");
        Files.writeString(sopsDir.resolve("bad.json"), "not valid json {}", StandardCharsets.UTF_8);

        List<Map<String, Object>> sops = sopService.listSops();
        assertEquals(1, sops.size());
    }

    // ── getSop ───────────────────────────────────────────────────

    /**
     * Tests get sop existing.
     */
    @Test
    public void testGetSop_existing() {
        createSop("sop-1", "TestSOP", "test description");

        Map<String, Object> sop = sopService.getSop("sop-1");
        assertNotNull(sop);
        assertEquals("TestSOP", sop.get("name"));
        assertEquals("test description", sop.get("description"));
    }

    /**
     * Tests get sop not found.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetSop_notFound() {
        sopService.getSop("nonexistent");
    }

    // ── createSop ────────────────────────────────────────────────

    /**
     * Tests create sop success.
     */
    @Test
    public void testCreateSop_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "RCPA诊断SOP");
        body.put("description", "RCPA进程异常分析");
        body.put("version", "2.0.0");
        body.put("triggerCondition", "RCPA进程异常");
        body.put("nodes", List.of(Map.of("id", "node-1", "name", "步骤1", "command", "ps -ef")));

        Map<String, Object> result = sopService.createSop(body);

        assertNotNull(result.get("id"));
        assertEquals("RCPA诊断SOP", result.get("name"));
        assertEquals("RCPA进程异常分析", result.get("description"));
        assertEquals("2.0.0", result.get("version"));
        assertEquals("RCPA进程异常", result.get("triggerCondition"));
        assertNotNull(result.get("nodes"));
    }

    /**
     * Tests create sop default values.
     */
    @Test
    public void testCreateSop_defaultValues() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "MinimalSOP");

        Map<String, Object> result = sopService.createSop(body);

        assertNotNull(result.get("id"));
        assertEquals("MinimalSOP", result.get("name"));
        assertEquals("", result.get("description"));
        assertEquals("1.0.0", result.get("version"));
        assertEquals("", result.get("triggerCondition"));
        assertEquals(List.of(), result.get("nodes"));
    }

    // ── updateSop ────────────────────────────────────────────────

    /**
     * Tests update sop success.
     */
    @Test
    public void testUpdateSop_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Original");
        body.put("description", "orig desc");
        Map<String, Object> created = sopService.createSop(body);
        String id = (String) created.get("id");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "Updated");
        updates.put("description", "new desc");

        Map<String, Object> result = sopService.updateSop(id, updates);
        assertEquals("Updated", result.get("name"));
        assertEquals("new desc", result.get("description"));
    }

    /**
     * Tests update sop partial update.
     */
    @Test
    public void testUpdateSop_partialUpdate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Original");
        body.put("version", "1.0.0");
        body.put("triggerCondition", "orig condition");
        Map<String, Object> created = sopService.createSop(body);
        String id = (String) created.get("id");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("triggerCondition", "new condition");

        Map<String, Object> result = sopService.updateSop(id, updates);
        assertEquals("Original", result.get("name"));
        assertEquals("1.0.0", result.get("version"));
        assertEquals("new condition", result.get("triggerCondition"));
    }

    /**
     * Tests update sop update nodes.
     */
    @Test
    public void testUpdateSop_updateNodes() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "SOP");
        Map<String, Object> created = sopService.createSop(body);
        String id = (String) created.get("id");

        List<Map<String, Object>> newNodes =
            List.of(Map.of("id", "n1", "name", "Node1"), Map.of("id", "n2", "name", "Node2"));
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("nodes", newNodes);

        Map<String, Object> result = sopService.updateSop(id, updates);
        assertEquals(newNodes, result.get("nodes"));
    }

    /**
     * Tests update sop not found.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUpdateSop_notFound() {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "NewName");
        sopService.updateSop("nonexistent", updates);
    }

    // ── deleteSop ────────────────────────────────────────────────

    /**
     * Tests delete sop success.
     */
    @Test
    public void testDeleteSop_success() {
        createSop("sop-del", "ToDelete", "desc");

        boolean deleted = sopService.deleteSop("sop-del");
        assertTrue(deleted);
        assertTrue(sopService.listSops().isEmpty());
    }

    /**
     * Tests delete sop not found.
     */
    @Test
    public void testDeleteSop_notFound() {
        boolean deleted = sopService.deleteSop("nonexistent");
        assertFalse(deleted);
    }

    /**
     * Tests delete sop file removed.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteSop_fileRemoved() throws IOException {
        createSop("sop-del", "ToDelete", "desc");
        assertTrue(Files.exists(sopsDir.resolve("sop-del.json")));

        sopService.deleteSop("sop-del");
        assertFalse(Files.exists(sopsDir.resolve("sop-del.json")));
    }

    // ── Duplicate Name Validation ────────────────────────────────

    /**
     * Tests create sop duplicate name rejected.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateSop_duplicateName_rejected() {
        createSop("sop-1", "DiagnoseRCPA", "desc1");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "DiagnoseRCPA");
        sopService.createSop(body);
    }

    /**
     * Tests create sop duplicate name case insensitive rejected.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateSop_duplicateNameCaseInsensitive_rejected() {
        createSop("sop-1", "DiagnoseRCPA", "desc1");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "diagnosercpa");
        sopService.createSop(body);
    }

    /**
     * Tests create sop different name allowed.
     */
    @Test
    public void testCreateSop_differentName_allowed() {
        createSop("sop-1", "DiagnoseRCPA", "desc1");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "DiagnoseOther");

        Map<String, Object> result = sopService.createSop(body);
        assertNotNull(result.get("id"));
        assertEquals("DiagnoseOther", result.get("name"));
    }

    /**
     * Tests update sop duplicate name rejected.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUpdateSop_duplicateName_rejected() {
        createSop("sop-1", "DiagnoseRCPA", "desc1");
        createSop("sop-2", "DiagnoseOther", "desc2");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "DiagnoseRCPA");
        sopService.updateSop("sop-2", updates);
    }

    /**
     * Tests update sop same name same id allowed.
     */
    @Test
    public void testUpdateSop_sameNameSameId_allowed() {
        createSop("sop-1", "DiagnoseRCPA", "desc1");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "DiagnoseRCPA");
        updates.put("description", "updated desc");

        Map<String, Object> result = sopService.updateSop("sop-1", updates);
        assertEquals("DiagnoseRCPA", result.get("name"));
        assertEquals("updated desc", result.get("description"));
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void createSop(String id, String name, String description) {
        Map<String, Object> sop = new LinkedHashMap<>();
        sop.put("id", id);
        sop.put("name", name);
        sop.put("description", description);
        sop.put("version", "1.0.0");
        sop.put("triggerCondition", "");
        sop.put("nodes", List.of());

        try {
            Path file = sopsDir.resolve(id + ".json");
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(sop);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
