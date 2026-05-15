/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.opsfactory.operationintelligence.qos.model.AlarmInfo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class QosCalculationServiceTest {

    private final QosCalculationService service = new QosCalculationService();

    // ---- calculateHealthScore ----

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    @Test
    void calculateHealthScore_weightedSum() {
        BigDecimal score =
            service.calculateHealthScore(bd("0.8"), bd("0.9"), bd("0.7"), bd("0.4"), bd("0.4"), bd("0.2"));
        assertEquals(0, bd("0.82").compareTo(score));
    }

    @Test
    void calculateHealthScore_fullScore() {
        BigDecimal score = service.calculateHealthScore(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, bd("0.4"),
            bd("0.4"), bd("0.2"));
        assertEquals(0, BigDecimal.ONE.compareTo(score));
    }

    // ---- calculateAvailabilityScore ----

    @Test
    void calculateHealthScore_zeroScore() {
        BigDecimal score = service.calculateHealthScore(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("0.4"),
            bd("0.4"), bd("0.2"));
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void calculateAvailabilityScore_allHigh() {
        Map<String, BigDecimal> rates = Map.of("MO_TYPE_A", bd("0.95"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculateAvailabilityScore(rates, weights);
        assertEquals(0, BigDecimal.ONE.compareTo(score));
    }

    @Test
    void calculateAvailabilityScore_medium() {
        Map<String, BigDecimal> rates = Map.of("MO_TYPE_A", bd("0.7"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculateAvailabilityScore(rates, weights);
        assertEquals(0, bd("0.60").compareTo(score));
    }

    @Test
    void calculateAvailabilityScore_low() {
        Map<String, BigDecimal> rates = Map.of("MO_TYPE_A", bd("0.5"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculateAvailabilityScore(rates, weights);
        assertEquals(0, bd("0.30").compareTo(score));
    }

    @Test
    void calculateAvailabilityScore_critical() {
        Map<String, BigDecimal> rates = Map.of("MO_TYPE_A", bd("0.2"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculateAvailabilityScore(rates, weights);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void calculateAvailabilityScore_multipleMoTypes() {
        Map<String, BigDecimal> rates = Map.of("MO_TYPE_A", bd("0.95"), "MO_TYPE_B", bd("0.5"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("0.6"), "MO_TYPE_B", bd("0.4"));
        BigDecimal score = service.calculateAvailabilityScore(rates, weights);
        // 1.0*0.6 + 0.3*0.4 = 0.72
        assertEquals(0, bd("0.72").compareTo(score));
    }

    // ---- calculatePerformanceScore ----

    @Test
    void calculateAvailabilityScore_unknownMoTypeGetsZeroWeight() {
        Map<String, BigDecimal> rates = Map.of("UNKNOWN", bd("0.95"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculateAvailabilityScore(rates, weights);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void calculatePerformanceScore_fast() {
        Map<String, BigDecimal> rts = Map.of("MO_TYPE_A", bd("30"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculatePerformanceScore(rts, weights);
        assertEquals(0, BigDecimal.ONE.compareTo(score));
    }

    @Test
    void calculatePerformanceScore_medium() {
        Map<String, BigDecimal> rts = Map.of("MO_TYPE_A", bd("80"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculatePerformanceScore(rts, weights);
        assertEquals(0, bd("0.60").compareTo(score));
    }

    @Test
    void calculatePerformanceScore_slow() {
        Map<String, BigDecimal> rts = Map.of("MO_TYPE_A", bd("150"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculatePerformanceScore(rts, weights);
        assertEquals(0, bd("0.30").compareTo(score));
    }

    // ---- calculateResourceScore ----

    @Test
    void calculatePerformanceScore_verySlow() {
        Map<String, BigDecimal> rts = Map.of("MO_TYPE_A", bd("500"));
        Map<String, BigDecimal> weights = Map.of("MO_TYPE_A", bd("1.0"));
        BigDecimal score = service.calculatePerformanceScore(rts, weights);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void calculateResourceScore_noAlarms() {
        BigDecimal score = service.calculateResourceScore(List.of(), Map.of(), Map.of(), 100);
        assertEquals(0, BigDecimal.ONE.compareTo(score));
    }

    @Test
    void calculateResourceScore_infoAlarmsIgnored() {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setSeverity("INFO");
        alarm.setCount(10);
        BigDecimal score = service.calculateResourceScore(List.of(alarm), Map.of("INFO", bd("10")), Map.of(), 100);
        assertEquals(0, BigDecimal.ONE.compareTo(score));
    }

    @Test
    void calculateResourceScore_maxImpact() {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setSeverity("CRITICAL");
        alarm.setCount(10);
        BigDecimal score = service.calculateResourceScore(List.of(alarm), Map.of("CRITICAL", bd("10")), Map.of(), 100);
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void calculateResourceScore_partialImpact() {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setSeverity("MAJOR");
        alarm.setCount(5);
        BigDecimal score = service.calculateResourceScore(List.of(alarm), Map.of("MAJOR", bd("10")), Map.of(), 100);
        // impact = 10*5 = 50, ratio = 50/100 = 0.50, score = 1-0.5 = 0.50
        assertEquals(0, bd("0.50").compareTo(score));
    }

    // ---- boundary / edge cases ----

    @Test
    void calculateResourceScore_alarmIdWeightOverrides() {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setSeverity("CRITICAL");
        alarm.setAlarmId("ALM_001");
        alarm.setCount(1);
        Map<String, BigDecimal> severityWeights = Map.of("CRITICAL", bd("10"));
        Map<String, BigDecimal> alarmIdWeights = Map.of("ALM_001", bd("5"));
        BigDecimal score = service.calculateResourceScore(List.of(alarm), severityWeights, alarmIdWeights, 100);
        // impact = 5*1 = 5, ratio = 5/100 = 0.05, score = 1-0.05 = 0.95
        assertEquals(0, bd("0.95").compareTo(score));
    }

    @Test
    void calculateHealthScore_asymmetricWeights() {
        BigDecimal score = service.calculateHealthScore(bd("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, bd("1.0"),
            BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ONE.compareTo(score));
    }

    @Test
    void calculateAvailabilityScore_emptyRates() {
        BigDecimal score = service.calculateAvailabilityScore(Map.of(), Map.of());
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }

    @Test
    void calculatePerformanceScore_emptyResponseTimes() {
        BigDecimal score = service.calculatePerformanceScore(Map.of(), Map.of());
        assertEquals(0, BigDecimal.ZERO.compareTo(score));
    }
}
