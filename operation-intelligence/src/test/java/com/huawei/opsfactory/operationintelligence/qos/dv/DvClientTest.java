/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class DvClientTest {

    private DvClient client;

    private DvSslContextFactory sslFactory;

    private DvAuthService authService;

    @BeforeEach
    void setUp() {
        sslFactory = new DvSslContextFactory();
        authService = new DvAuthService(sslFactory);
        client = new DvClient(authService, sslFactory);
    }

    @Test
    void parseChildren_nullResponse_returnsEmpty() {
        assertTrue(client.parseChildren(null).isEmpty());
    }

    @Test
    void parseChildren_emptyResponse_returnsEmpty() {
        assertTrue(client.parseChildren("").isEmpty());
    }

    @Test
    void parseChildren_validResponse_returnsChildren() {
        String json = "{\"result\":[{\"children\":[\"child1\",\"child2\"]}]}";
        List<String> result = client.parseChildren(json);
        assertEquals(2, result.size());
        assertEquals("child1", result.get(0));
    }

    @Test
    void parseChildren_invalidJson_returnsEmpty() {
        assertTrue(client.parseChildren("not json").isEmpty());
    }

    @Test
    void parsePerformanceResult_nullResponse_returnsEmpty() {
        assertTrue(client.parsePerformanceResult(null).isEmpty());
    }

    @Test
    void parsePerformanceResult_validResponse_returnsData() {
        String json =
            "{\"result\":{\"datas\":[{\"dn\":\"test-dn\",\"neName\":\"NE1\",\"period\":300,\"values\":{\"key1\":\"val1\"}}]}}";
        var result = client.parsePerformanceResult(json);
        assertEquals(1, result.size());
        assertEquals("test-dn", result.get(0).getDn());
    }

    @Test
    void parseAlarms_nullResponse_returnsEmpty() {
        assertTrue(client.parseAlarms(null).isEmpty());
    }

    @Test
    void parseAlarms_validResponse_returnsAlarms() {
        String json =
            "{\"hits\":[{\"alarmId\":\"ALM001\",\"alarmName\":\"Test Alarm\",\"severity\":\"CRITICAL\",\"nativeMeDn\":\"dn1\",\"count\":1}]}";
        var result = client.parseAlarms(json);
        assertEquals(1, result.size());
        assertEquals("ALM001", result.get(0).getAlarmId());
        assertEquals("CRITICAL", result.get(0).getSeverity());
    }

    @Test
    void executeWithRetry_success_returnsImmediately() {
        String result = client.executeWithRetry(() -> "ok", "testOp");
        assertEquals("ok", result);
    }

    @Test
    void executeWithRetry_alwaysFails_throws() {
        DvClient noSleepClient = new DvClient(authService, sslFactory) {
            @Override
            void sleepBeforeRetry(long delayMs) {
                /* no-op for testing */ }
        };
        assertThrows(RuntimeException.class, () -> noSleepClient.executeWithRetry(() -> {
            throw new RuntimeException("fail");
        }, "testOp"));
    }

    @Test
    void parsePerformanceResult_emptyDatas_returnsEmpty() {
        String json = "{\"result\":{\"datas\":[]}}";
        assertTrue(client.parsePerformanceResult(json).isEmpty());
    }

    @Test
    void parsePerformanceResult_invalidJson_returnsEmpty() {
        assertTrue(client.parsePerformanceResult("not json").isEmpty());
    }

    @Test
    void parseAlarms_invalidJson_returnsEmpty() {
        assertTrue(client.parseAlarms("not json").isEmpty());
    }

    @Test
    void parseAlarms_missingHits_returnsEmpty() {
        String json = "{\"status\":\"ok\"}";
        assertTrue(client.parseAlarms(json).isEmpty());
    }
}
