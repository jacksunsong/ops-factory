/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Tests for LangfuseService data parsing and aggregation logic:
 * buildOverview, parseTraces, parseObservations, emptyOverview.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class LangfuseServiceBuildOverviewTest {
    private LangfuseService service;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        GatewayProperties props = new GatewayProperties();
        // Configured but won't make real HTTP calls — we test private methods via reflection
        GatewayProperties.Langfuse langfuse = new GatewayProperties.Langfuse();
        langfuse.setHost("http://localhost:9999");
        langfuse.setPublicKey("pk");
        langfuse.setSecretKey("sk");
        props.setLangfuse(langfuse);
        service = new LangfuseService(props);
    }

    /**
     * Tests build overview with traces and observations.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testBuildOverview_withTracesAndObservations() throws Exception {
        String tracesJson = "{\"data\":[" + "{\"id\":\"t1\",\"latency\":2.0,\"totalCost\":0.01,\"level\":\"DEFAULT\","
            + "\"timestamp\":\"2024-01-15T10:00:00Z\"},"
            + "{\"id\":\"t2\",\"latency\":5.0,\"totalCost\":0.02,\"level\":\"ERROR\","
            + "\"timestamp\":\"2024-01-15T11:00:00Z\"},"
            + "{\"id\":\"t3\",\"latency\":1.0,\"totalCost\":0.005,\"level\":\"DEFAULT\","
            + "\"timestamp\":\"2024-01-16T09:00:00Z\"}" + "]}";
        String obsJson =
            "{\"data\":[" + "{\"name\":\"gen\",\"totalCost\":0.03,\"startTime\":\"2024-01-15T10:00:00Z\"}" + "]}";

        Method buildOverview = LangfuseService.class.getDeclaredMethod("buildOverview", String.class, String.class);
        buildOverview.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildOverview.invoke(service, tracesJson, obsJson);

        assertEquals(3, result.get("totalTraces"));
        assertEquals(1, result.get("totalObservations"));
        // totalCost = 0.01 + 0.02 + 0.005 + 0.03 = 0.065
        assertEquals(0.065, (double) result.get("totalCost"), 0.001);
        // avgLatency = (2.0 + 5.0 + 1.0) / 3 = 2.666...
        assertEquals(2.666, (double) result.get("avgLatency"), 0.01);
        // p95 with 3 values: ceil(3*0.95)-1 = ceil(2.85)-1 = 3-1 = 2 → sorted [1.0,2.0,5.0] → index 2 = 5.0
        assertEquals(5.0, (double) result.get("p95Latency"), 0.001);
        assertEquals(1, result.get("errorCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> daily = (List<Map<String, Object>>) result.get("daily");
        assertNotNull(daily);
        // 2024-01-15 and 2024-01-16
        assertEquals(2, daily.size());
        assertEquals("2024-01-15", daily.get(0).get("date"));
        assertEquals(2, daily.get(0).get("traces"));
        assertEquals(1, daily.get(0).get("observations"));
        assertEquals("2024-01-16", daily.get(1).get("date"));
        assertEquals(1, daily.get(1).get("traces"));
    }

    /**
     * Tests build overview empty data.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testBuildOverview_emptyData() throws Exception {
        String tracesJson = "{\"data\":[]}";
        String obsJson = "{\"data\":[]}";

        Method buildOverview = LangfuseService.class.getDeclaredMethod("buildOverview", String.class, String.class);
        buildOverview.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildOverview.invoke(service, tracesJson, obsJson);

        assertEquals(0, result.get("totalTraces"));
        assertEquals(0, result.get("totalObservations"));
        assertEquals(0.0, (double) result.get("totalCost"), 0.001);
        assertEquals(0.0, (double) result.get("avgLatency"), 0.001);
        assertEquals(0.0, (double) result.get("p95Latency"), 0.001);
        assertEquals(0, result.get("errorCount"));
    }

    /**
     * Tests build overview raw array format.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testBuildOverview_rawArrayFormat() throws Exception {
        // When Langfuse API returns data without "data" wrapper
        String tracesJson = "[{\"id\":\"t1\",\"latency\":3.0,\"totalCost\":0.01,"
            + "\"level\":\"DEFAULT\",\"timestamp\":\"2024-01-15T10:00:00Z\"}]";
        String obsJson = "[]";

        Method buildOverview = LangfuseService.class.getDeclaredMethod("buildOverview", String.class, String.class);
        buildOverview.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildOverview.invoke(service, tracesJson, obsJson);

        assertEquals(1, result.get("totalTraces"));
        assertEquals(3.0, (double) result.get("avgLatency"), 0.001);
    }

    /**
     * Tests parse traces normal data.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testParseTraces_normalData() throws Exception {
        String json = "{\"data\":[" + "{\"id\":\"t1\",\"name\":\"chat\",\"timestamp\":\"2024-01-15T10:00:00Z\","
            + "\"input\":\"hello\",\"latency\":2.5,\"totalCost\":0.01,"
            + "\"observations\":[{\"id\":\"o1\"},{\"id\":\"o2\"}],\"level\":\"DEFAULT\"},"
            + "{\"id\":\"t2\",\"name\":\"error-trace\",\"timestamp\":\"2024-01-15T11:00:00Z\","
            + "\"input\":{\"msg\":\"complex\"},\"latency\":5.0,\"totalCost\":0.02,"
            + "\"observations\":[],\"level\":\"ERROR\",\"output\":\"timeout error\"}" + "]}";

        Method parseTraces = LangfuseService.class.getDeclaredMethod("parseTraces", String.class);
        parseTraces.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) parseTraces.invoke(service, json);

        assertEquals(2, result.size());

        Map<String, Object> t1 = result.get(0);
        assertEquals("t1", t1.get("id"));
        assertEquals("chat", t1.get("name"));
        assertEquals("hello", t1.get("input"));
        assertEquals(2, t1.get("observationCount"));
        assertEquals(false, t1.get("hasError"));

        Map<String, Object> t2 = result.get(1);
        assertEquals("t2", t2.get("id"));
        assertEquals(true, t2.get("hasError"));
        assertEquals("timeout error", t2.get("errorMessage"));
        // Complex input is stringified
        assertTrue(((String) t2.get("input")).contains("complex"));
    }

    /**
     * Tests parse traces empty array.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testParseTraces_emptyArray() throws Exception {
        Method parseTraces = LangfuseService.class.getDeclaredMethod("parseTraces", String.class);
        parseTraces.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) parseTraces.invoke(service, "{\"data\":[]}");

        assertTrue(result.isEmpty());
    }

    /**
     * Tests parse traces non array data.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testParseTraces_nonArrayData() throws Exception {
        Method parseTraces = LangfuseService.class.getDeclaredMethod("parseTraces", String.class);
        parseTraces.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
            (List<Map<String, Object>>) parseTraces.invoke(service, "{\"data\":\"not-array\"}");

        assertTrue(result.isEmpty());
    }

    /**
     * Tests parse observations grouped by name.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testParseObservations_groupedByName() throws Exception {
        String json =
            "{\"data\":[" + "{\"name\":\"generation\",\"latency\":2.0,\"totalTokens\":100,\"totalCost\":0.01},"
                + "{\"name\":\"generation\",\"latency\":4.0,\"totalTokens\":200,\"totalCost\":0.02},"
                + "{\"name\":\"embedding\",\"latency\":0.5,\"totalTokens\":50,\"totalCost\":0.005}" + "]}";

        Method parseObs = LangfuseService.class.getDeclaredMethod("parseObservations", String.class);
        parseObs.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parseObs.invoke(service, json);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("observations");
        assertEquals(2, groups.size());

        Map<String, Object> genGroup = groups.get(0);
        assertEquals("generation", genGroup.get("name"));
        assertEquals(2, genGroup.get("count"));
        // (2+4)/2
        assertEquals(3.0, (double) genGroup.get("avgLatency"), 0.001);
        assertEquals(4.0, (double) genGroup.get("p95Latency"), 0.001);
        assertEquals(300L, genGroup.get("totalTokens"));
        assertEquals(0.03, (double) genGroup.get("totalCost"), 0.001);

        Map<String, Object> embGroup = groups.get(1);
        assertEquals("embedding", embGroup.get("name"));
        assertEquals(1, embGroup.get("count"));
    }

    /**
     * Tests parse observations empty data.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testParseObservations_emptyData() throws Exception {
        Method parseObs = LangfuseService.class.getDeclaredMethod("parseObservations", String.class);
        parseObs.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parseObs.invoke(service, "{\"data\":[]}");

        @SuppressWarnings("unchecked")
        List<?> groups = (List<?>) result.get("observations");
        assertTrue(groups.isEmpty());
    }

    /**
     * Tests parse observations fallback token count.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testParseObservations_fallbackTokenCount() throws Exception {
        // When totalTokens is missing, falls back to promptTokens + completionTokens
        String json = "{\"data\":["
            + "{\"name\":\"gen\",\"latency\":1.0,\"promptTokens\":50,\"completionTokens\":30,\"totalCost\":0.01}"
            + "]}";

        Method parseObs = LangfuseService.class.getDeclaredMethod("parseObservations", String.class);
        parseObs.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parseObs.invoke(service, json);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("observations");
        assertEquals(80L, groups.get(0).get("totalTokens"));
    }

    /**
     * Tests empty overview.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testEmptyOverview() throws Exception {
        Method emptyOverview = LangfuseService.class.getDeclaredMethod("emptyOverview");
        emptyOverview.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) emptyOverview.invoke(null);

        assertEquals(0, result.get("totalTraces"));
        assertEquals(0, result.get("totalObservations"));
        assertEquals(0.0, (double) result.get("totalCost"), 0.001);
        assertEquals(0.0, (double) result.get("avgLatency"), 0.001);
        assertEquals(0.0, (double) result.get("p95Latency"), 0.001);
        assertEquals(0, result.get("errorCount"));
        assertNotNull(result.get("daily"));
    }

    /**
     * Tests get traces formatted not configured returns empty list.
     */
    @Test
    public void testGetTracesFormatted_notConfigured_returnsEmptyList() {
        GatewayProperties props = new GatewayProperties();
        LangfuseService unconfiguredService = new LangfuseService(props);

        List<Map<String, Object>> result =
            unconfiguredService.getTracesFormatted("2024-01-01", "2024-01-02", 20, false).block();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Tests get observations formatted not configured returns empty map.
     */
    @Test
    public void testGetObservationsFormatted_notConfigured_returnsEmptyMap() {
        GatewayProperties props = new GatewayProperties();
        LangfuseService unconfiguredService = new LangfuseService(props);

        Map<String, Object> result = unconfiguredService.getObservationsFormatted("2024-01-01", "2024-01-02").block();
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<?> obs = (List<?>) result.get("observations");
        assertTrue(obs.isEmpty());
    }

    /**
     * Tests get overview not configured returns empty overview.
     */
    @Test
    public void testGetOverview_notConfigured_returnsEmptyOverview() {
        GatewayProperties props = new GatewayProperties();
        LangfuseService unconfiguredService = new LangfuseService(props);

        Map<String, Object> result = unconfiguredService.getOverview("2024-01-01", "2024-01-02").block();
        assertNotNull(result);
        assertEquals(0, result.get("totalTraces"));
    }

    /**
     * Tests get traces not configured returns empty array.
     */
    @Test
    public void testGetTraces_notConfigured_returnsEmptyArray() {
        GatewayProperties props = new GatewayProperties();
        LangfuseService unconfiguredService = new LangfuseService(props);

        String result = unconfiguredService.getTraces("2024-01-01", "2024-01-02", 20, false).block();
        assertEquals("[]", result);
    }

    /**
     * Tests get observations not configured returns empty array.
     */
    @Test
    public void testGetObservations_notConfigured_returnsEmptyArray() {
        GatewayProperties props = new GatewayProperties();
        LangfuseService unconfiguredService = new LangfuseService(props);

        String result = unconfiguredService.getObservations("2024-01-01", "2024-01-02").block();
        assertEquals("[]", result);
    }
}
