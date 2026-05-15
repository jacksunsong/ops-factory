/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.AlarmDetailData;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorDetailData;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorNormalizeData;
import com.huawei.opsfactory.operationintelligence.qos.model.ProductConfigRule;
import com.huawei.opsfactory.operationintelligence.qos.store.AlarmDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorNormalizeDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ProductConfigRuleStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class QosServiceFileStoreIntegrationTest {

    @TempDir
    Path dataRoot;

    private QosService service;

    private IndicatorNormalizeDataStore normalizeStore;

    private IndicatorDetailDataStore detailStore;

    private AlarmDetailDataStore alarmStore;

    private ProductConfigRuleStore ruleStore;

    private long baseTime;

    private static IndicatorNormalizeData normalize(String envCode, String type, String value, long timestamp) {
        IndicatorNormalizeData data = new IndicatorNormalizeData();
        data.setEnvCode(envCode);
        data.setType(type);
        data.setIndicatorValue(new BigDecimal(value));
        data.setTimestamp(timestamp);
        return data;
    }

    private static IndicatorDetailData detail(String envCode, String type, String name, long timestamp) {
        IndicatorDetailData data = new IndicatorDetailData();
        data.setEnvCode(envCode);
        data.setType(type);
        data.setIndicatorName(name);
        data.setTimestamp(timestamp);
        return data;
    }

    private static AlarmDetailData alarm(String envCode, String severity, long occurUtc) {
        AlarmDetailData data = new AlarmDetailData();
        data.setEnvCode(envCode);
        data.setSeverity(severity);
        data.setOccurUtc(occurUtc);
        return data;
    }

    @BeforeEach
    void setUp() {
        OperationIntelligenceProperties props = new OperationIntelligenceProperties();
        props.setDataRoot(dataRoot.toString());
        props.getQos().setRotationIntervalMs(60_000);
        props.getQos().setNormalizeDataRetentionDays(1);
        props.getQos().setDetailDataRetentionDays(1);
        props.getQos().setRawDataRetentionDays(1);
        baseTime = System.currentTimeMillis();

        normalizeStore = new IndicatorNormalizeDataStore(props);
        detailStore = new IndicatorDetailDataStore(props);
        alarmStore = new AlarmDetailDataStore(props);
        ruleStore = new ProductConfigRuleStore(props);
        service =
            new QosService(new QosCalculationService(), ruleStore, normalizeStore, detailStore, alarmStore, props);
    }

    @Test
    void healthIndicator_usesPersistedNormalizeDataAndPersistedWeights() {
        ProductConfigRule rule = new ProductConfigRule();
        rule.setAgentSolutionType("TYPE1");
        rule.setHealthWeight("0.6,0.3,0.1");
        ruleStore.replaceAll(List.of(rule));

        normalizeStore
            .appendAll(List.of(normalize("ENV1", "A", "1.0", baseTime), normalize("ENV1", "P", "0.5", baseTime),
                normalize("ENV1", "R", "0.2", baseTime), normalize("ENV2", "A", "0.1", baseTime)));

        List<Map<String, Object>> result = service.getHealthIndicator("ENV1", baseTime - 60_000, baseTime + 60_000);

        assertEquals(1, result.size());
        assertEquals(baseTime, result.get(0).get("timestamp"));
        assertEquals("0.77", result.get(0).get("value"));
    }

    @Test
    void resourceNormalize_returnsOnlyResourceDimensionForRequestedEnvironment() {
        normalizeStore.appendAll(List.of(normalize("ENV1", "R", "0.3", baseTime),
            normalize("ENV1", "A", "0.9", baseTime), normalize("ENV2", "R", "0.7", baseTime)));

        List<IndicatorNormalizeData> result =
            service.getResourceNormalize("ENV1", baseTime - 60_000, baseTime + 60_000);

        assertEquals(1, result.size());
        assertEquals("ENV1", result.get(0).getEnvCode());
        assertEquals("R", result.get(0).getType());
        assertEquals(0, new BigDecimal("0.3").compareTo(result.get(0).getIndicatorValue()));
    }

    @Test
    void indicatorDetail_filtersByTypeEnvironmentAndTimeRange() {
        detailStore
            .appendAll(List.of(detail("ENV1", "A", "in-range", baseTime), detail("ENV1", "P", "wrong-type", baseTime),
                detail("ENV2", "A", "wrong-env", baseTime), detail("ENV1", "A", "out-of-range", baseTime + 120_000)));

        Map<String, Object> result =
            service.getIndicatorDetail("ENV1", "A", baseTime - 60_000, baseTime + 60_000, 1, 10);

        assertEquals(1, result.get("total"));
        List<?> rows = (List<?>) result.get("results");
        assertEquals(1, rows.size());
        assertEquals("in-range", ((IndicatorDetailData) rows.get(0)).getIndicatorName());
    }

    @Test
    void alarmDetail_filtersByEnvironmentAndOccurTime() {
        alarmStore.appendAll(List.of(alarm("ENV1", "critical", baseTime), alarm("ENV2", "major", baseTime),
            alarm("ENV1", "minor", baseTime + 120_000)));

        Map<String, Object> result = service.getAlarmDetail("ENV1", baseTime - 60_000, baseTime + 60_000, 1, 10);

        assertEquals(1, result.get("total"));
        List<?> rows = (List<?>) result.get("results");
        assertEquals("critical", ((AlarmDetailData) rows.get(0)).getSeverity());
    }
}
