/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Business Service Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class BusinessServiceServiceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BusinessServiceService businessServiceService;

    private GatewayProperties properties;

    private Path businessServicesDir;

    /**
     * Sets the up.
     *
     * @throws IOException if the operation fails
     */
    @Before
    public void setUp() throws IOException {
        properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);

        businessServiceService = new BusinessServiceService(properties);
        businessServiceService.init();

        businessServicesDir = Path.of(tempFolder.getRoot().getAbsolutePath())
            .toAbsolutePath()
            .normalize()
            .resolve("gateway")
            .resolve("data")
            .resolve("business-services");
    }

    // ── createBusinessService ──────────────────────────────────────

    /**
     * Tests create business service.
     */
    @Test
    public void testCreateBusinessService() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "OrderService");
        body.put("code", "ORDER");
        body.put("groupId", "group-1");
        body.put("description", "Order management service");
        body.put("hostIds", List.of("cluster-1", "cluster-2"));
        body.put("tags", List.of("core", "production"));
        body.put("priority", "high");
        body.put("contactInfo", "team-order@example.com");

        Map<String, Object> result = businessServiceService.createBusinessService(body);

        assertNotNull(result.get("id"));
        assertEquals("OrderService", result.get("name"));
        assertEquals("ORDER", result.get("code"));
        assertEquals("group-1", result.get("groupId"));
        assertEquals("Order management service", result.get("description"));
        assertEquals(List.of("cluster-1", "cluster-2"), result.get("hostIds"));
        assertEquals(List.of("core", "production"), result.get("tags"));
        assertEquals("high", result.get("priority"));
        assertEquals("team-order@example.com", result.get("contactInfo"));
        assertNotNull(result.get("createdAt"));
        assertNotNull(result.get("updatedAt"));
    }

    /**
     * Tests create business service defaults.
     */
    @Test
    public void testCreateBusinessService_defaults() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "MinimalService");

        Map<String, Object> result = businessServiceService.createBusinessService(body);

        assertNotNull(result.get("id"));
        assertEquals("MinimalService", result.get("name"));
        assertEquals("", result.get("code"));
        assertNull(result.get("groupId"));
        assertEquals("", result.get("description"));
        assertEquals(Collections.emptyList(), result.get("hostIds"));
        assertEquals(Collections.emptyList(), result.get("tags"));
        assertEquals("", result.get("priority"));
        assertEquals("", result.get("contactInfo"));
    }

    // ── getBusinessService ─────────────────────────────────────────

    /**
     * Tests get business service.
     */
    @Test
    public void testGetBusinessService() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "GetTest");
        body.put("code", "GT");

        Map<String, Object> created = businessServiceService.createBusinessService(body);
        String id = (String) created.get("id");

        Map<String, Object> result = businessServiceService.getBusinessService(id);
        assertEquals("GetTest", result.get("name"));
        assertEquals("GT", result.get("code"));
    }

    /**
     * Tests get business service not found.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetBusinessService_notFound() {
        businessServiceService.getBusinessService("nonexistent");
    }

    // ── listBusinessServices ───────────────────────────────────────

    /**
     * Tests list business services empty.
     */
    @Test
    public void testListBusinessServices_empty() {
        List<Map<String, Object>> services = businessServiceService.listBusinessServices(null, null);
        assertTrue(services.isEmpty());
    }

    /**
     * Tests list business services returns all.
     */
    @Test
    public void testListBusinessServices_returnsAll() {
        createBs("bs-1", "Svc1", "S1", "group-1", List.of());
        createBs("bs-2", "Svc2", "S2", "group-1", List.of());
        createBs("bs-3", "Svc3", "S3", "group-2", List.of());

        List<Map<String, Object>> services = businessServiceService.listBusinessServices(null, null);
        assertEquals(3, services.size());
    }

    /**
     * Tests list business services filter by group id.
     */
    @Test
    public void testListBusinessServices_filterByGroupId() {
        createBs("bs-1", "Svc1", "S1", "group-1", List.of());
        createBs("bs-2", "Svc2", "S2", "group-2", List.of());

        List<Map<String, Object>> services = businessServiceService.listBusinessServices("group-1", null);
        assertEquals(1, services.size());
        assertEquals("Svc1", services.get(0).get("name"));
    }

    /**
     * Tests list business services filter by host id.
     */
    @Test
    public void testListBusinessServices_filterByHostId() {
        createBs("bs-1", "Svc1", "S1", "group-1", List.of("host-1", "host-2"));
        createBs("bs-2", "Svc2", "S2", "group-1", List.of("host-3"));

        List<Map<String, Object>> services = businessServiceService.listBusinessServices(null, "host-2");
        assertEquals(1, services.size());
        assertEquals("Svc1", services.get(0).get("name"));
    }

    // ── updateBusinessService ──────────────────────────────────────

    /**
     * Tests update business service.
     */
    @Test
    public void testUpdateBusinessService() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Original");
        body.put("code", "ORIG");
        body.put("tags", List.of("v1"));

        Map<String, Object> created = businessServiceService.createBusinessService(body);
        String id = (String) created.get("id");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "Updated");
        updates.put("code", "UPD");
        updates.put("tags", List.of("v2"));

        Map<String, Object> result = businessServiceService.updateBusinessService(id, updates);
        assertEquals("Updated", result.get("name"));
        assertEquals("UPD", result.get("code"));
        assertEquals(List.of("v2"), result.get("tags"));
    }

    /**
     * Tests update business service partial update.
     */
    @Test
    public void testUpdateBusinessService_partialUpdate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Original");
        body.put("code", "ORIG");
        body.put("description", "original desc");

        Map<String, Object> created = businessServiceService.createBusinessService(body);
        String id = (String) created.get("id");

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("description", "new desc");

        Map<String, Object> result = businessServiceService.updateBusinessService(id, updates);
        assertEquals("Original", result.get("name"));
        assertEquals("ORIG", result.get("code"));
        assertEquals("new desc", result.get("description"));
    }

    /**
     * Tests update business service not found.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testUpdateBusinessService_notFound() {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("name", "NewName");
        businessServiceService.updateBusinessService("nonexistent", updates);
    }

    // ── deleteBusinessService ──────────────────────────────────────

    /**
     * Tests delete business service.
     */
    @Test
    public void testDeleteBusinessService() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "ToDelete");
        Map<String, Object> created = businessServiceService.createBusinessService(body);
        String id = (String) created.get("id");

        assertTrue(businessServiceService.deleteBusinessService(id));
        assertFalse(Files.exists(businessServicesDir.resolve(id + ".json")));
    }

    /**
     * Tests delete business service not found.
     */
    @Test
    public void testDeleteBusinessService_notFound() {
        assertFalse(businessServiceService.deleteBusinessService("nonexistent"));
    }

    // ── searchByKeyword ────────────────────────────────────────────

    /**
     * Tests search by keyword.
     */
    @Test
    public void testSearchByKeyword() {
        createBs("bs-1", "OrderService", "ORDER", null, List.of(), List.of("core"));
        createBs("bs-2", "PaymentService", "PAY", null, List.of(), List.of("billing"));
        createBs("bs-3", "ShippingService", "SHIP", null, List.of(), List.of("order"));

        List<Map<String, Object>> byName = businessServiceService.searchByKeyword("order");
        // OrderService (name) + ShippingService (tag "order")
        assertEquals(2, byName.size());

        List<Map<String, Object>> byCode = businessServiceService.searchByKeyword("pay");
        assertEquals(1, byCode.size());
        assertEquals("PaymentService", byCode.get(0).get("name"));

        List<Map<String, Object>> byTag = businessServiceService.searchByKeyword("billing");
        assertEquals(1, byTag.size());
    }

    /**
     * Tests search by keyword empty keyword.
     */
    @Test
    public void testSearchByKeyword_emptyKeyword() {
        createBs("bs-1", "Svc1", "S1", null, List.of());

        List<Map<String, Object>> all = businessServiceService.searchByKeyword("");
        assertEquals(1, all.size());

        List<Map<String, Object>> nullKw = businessServiceService.searchByKeyword(null);
        assertEquals(1, nullKw.size());
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void createBs(String id, String name, String code, String groupId, List<String> hostIds) {
        createBs(id, name, code, groupId, hostIds, List.of());
    }

    private void createBs(String id, String name, String code, String groupId, List<String> hostIds,
        List<String> tags) {
        Map<String, Object> bs = new LinkedHashMap<>();
        bs.put("id", id);
        bs.put("name", name);
        bs.put("code", code);
        bs.put("groupId", groupId);
        bs.put("description", "");
        bs.put("hostIds", hostIds);
        bs.put("tags", tags);
        bs.put("priority", "");
        bs.put("contactInfo", "");
        bs.put("createdAt", "2026-01-01T00:00:00Z");
        bs.put("updatedAt", "2026-01-01T00:00:00Z");

        try {
            Path file = businessServicesDir.resolve(id + ".json");
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(bs);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
