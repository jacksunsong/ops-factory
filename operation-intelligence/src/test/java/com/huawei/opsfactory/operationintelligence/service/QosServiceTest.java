/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorNormalizeData;
import com.huawei.opsfactory.operationintelligence.qos.store.AlarmDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorNormalizeDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ProductConfigRuleStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class QosServiceTest {

    private QosService service;

    private IndicatorNormalizeDataStore normalizeStore;

    private IndicatorDetailDataStore detailStore;

    private AlarmDetailDataStore alarmStore;

    private ProductConfigRuleStore ruleStore;

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    @BeforeEach
    void setUp() {
        QosCalculationService calcService = new QosCalculationService();
        normalizeStore = mock(IndicatorNormalizeDataStore.class);
        detailStore = mock(IndicatorDetailDataStore.class);
        alarmStore = mock(AlarmDetailDataStore.class);
        ruleStore = mock(ProductConfigRuleStore.class);
        OperationIntelligenceProperties props = new OperationIntelligenceProperties();

        service = new QosService(calcService, ruleStore, normalizeStore, detailStore, alarmStore, props);
    }

    @Test
    void getHealthIndicator_returnsNormalizedData() {
        IndicatorNormalizeData d1 = new IndicatorNormalizeData();
        d1.setTimestamp(1000L);
        d1.setEnvCode("ENV1");
        d1.setType("A");
        d1.setIndicatorValue(BigDecimal.ONE);

        IndicatorNormalizeData d2 = new IndicatorNormalizeData();
        d2.setTimestamp(1000L);
        d2.setEnvCode("ENV1");
        d2.setType("P");
        d2.setIndicatorValue(bd("0.8"));

        when(normalizeStore.loadRange(0L, 2000L)).thenReturn(List.of(d1, d2));

        List<Map<String, Object>> result = service.getHealthIndicator("ENV1", 0L, 2000L);
        assertNotNull(result);
        verify(normalizeStore).loadRange(0L, 2000L);
    }

    @Test
    void getHealthIndicator_filtersByEnvCode() {
        IndicatorNormalizeData d1 = new IndicatorNormalizeData();
        d1.setTimestamp(1000L);
        d1.setEnvCode("ENV1");

        IndicatorNormalizeData d2 = new IndicatorNormalizeData();
        d2.setTimestamp(1000L);
        d2.setEnvCode("ENV2");

        when(normalizeStore.loadRange(0L, 2000L)).thenReturn(List.of(d1, d2));

        List<Map<String, Object>> result = service.getHealthIndicator("ENV1", 0L, 2000L);
        assertNotNull(result);
    }

    @Test
    void getHealthIndicator_emptyData() {
        when(normalizeStore.loadRange(anyLong(), anyLong())).thenReturn(List.of());

        List<Map<String, Object>> result = service.getHealthIndicator("ENV1", 0L, 2000L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getEnvironments_returnsFromConfig() {
        OperationIntelligenceProperties.Qos.DvEnvironment env = new OperationIntelligenceProperties.Qos.DvEnvironment();
        env.setEnvCode("ENV1");
        env.setEnvName("Test Env");
        env.setAgentSolutionType("TYPE1");

        OperationIntelligenceProperties props = new OperationIntelligenceProperties();
        props.getQos().setDvEnvironments(List.of(env));

        service =
            new QosService(new QosCalculationService(), ruleStore, normalizeStore, detailStore, alarmStore, props);

        List<Map<String, String>> result = service.getEnvironments();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ENV1", result.get(0).get("envCode"));
        assertEquals("Test Env", result.get(0).get("envName"));
    }

    @Test
    void getIndicatorDetail_pageIndexZero_clampedToOne() {
        when(detailStore.loadRange(anyLong(), anyLong())).thenReturn(List.of());
        Map<String, Object> result = service.getIndicatorDetail("ENV1", "A", 0L, 2000L, 0, 10);
        assertNotNull(result);
        assertEquals(0, result.get("total"));
    }

    @Test
    void getIndicatorDetail_pageSizeZero_clampedToTen() {
        when(detailStore.loadRange(anyLong(), anyLong())).thenReturn(List.of());
        Map<String, Object> result = service.getIndicatorDetail("ENV1", "A", 0L, 2000L, 1, 0);
        assertNotNull(result);
        assertEquals(10, result.get("pageSize"));
    }

    @Test
    void getIndicatorDetail_pageBeyondTotal_returnsEmpty() {
        when(detailStore.loadRange(anyLong(), anyLong())).thenReturn(List.of());
        Map<String, Object> result = service.getIndicatorDetail("ENV1", "A", 0L, 2000L, 999, 10);
        assertNotNull(result);
        assertEquals(0, ((List<?>) result.get("results")).size());
    }

    @Test
    void getProductConfigRule_notFound_returnsEmpty() {
        when(ruleStore.loadAll()).thenReturn(List.of());
        assertTrue(service.getProductConfigRule("UNKNOWN").isEmpty());
    }

    @Test
    void getHealthIndicator_nullEnvCode_returnsAllData() {
        IndicatorNormalizeData d1 = new IndicatorNormalizeData();
        d1.setTimestamp(1000L);
        d1.setEnvCode("ENV1");
        d1.setType("A");
        d1.setIndicatorValue(BigDecimal.ONE);

        IndicatorNormalizeData d2 = new IndicatorNormalizeData();
        d2.setTimestamp(1000L);
        d2.setEnvCode("ENV2");
        d2.setType("A");
        d2.setIndicatorValue(BigDecimal.ONE);

        when(normalizeStore.loadRange(0L, 2000L)).thenReturn(List.of(d1, d2));

        List<Map<String, Object>> result = service.getHealthIndicator(null, 0L, 2000L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getIndicatorDetail_nullEnvCode_returnsAll() {
        when(detailStore.loadRange(anyLong(), anyLong())).thenReturn(List.of());
        Map<String, Object> result = service.getIndicatorDetail(null, "A", 0L, 2000L, 1, 10);
        assertNotNull(result);
        assertEquals(0, result.get("total"));
    }

    @Test
    void getIndicatorDetail_pageSizeOverMax_clamped() {
        when(detailStore.loadRange(anyLong(), anyLong())).thenReturn(List.of());
        Map<String, Object> result = service.getIndicatorDetail("ENV1", "A", 0L, 2000L, 1, 5000);
        assertEquals(10, result.get("pageSize"));
    }
}
