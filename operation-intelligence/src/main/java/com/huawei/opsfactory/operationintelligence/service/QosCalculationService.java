/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.qos.model.AlarmInfo;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Qos Calculation Service.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Service
public class QosCalculationService {

    private static final BigDecimal[] DEFAULT_AVAILABILITY_THRESHOLDS =
        {new BigDecimal("0.9"), new BigDecimal("0.6"), new BigDecimal("0.3")};

    private static final BigDecimal[] DEFAULT_PERFORMANCE_THRESHOLDS =
        {new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("200")};

    /**
     * calculate Health Score.
     *
     * @param availabilityScore the availabilityScore
     * @param performanceScore the performanceScore
     * @param resourceScore the resourceScore
     * @param wA the wA
     * @param wP the wP
     * @param wR the wR
     * @return the result
     */
    public BigDecimal calculateHealthScore(BigDecimal availabilityScore, BigDecimal performanceScore,
        BigDecimal resourceScore, BigDecimal wA, BigDecimal wP, BigDecimal wR) {
        BigDecimal hs = BigDecimal.ZERO;
        hs = hs.add(availabilityScore.multiply(wA));
        hs = hs.add(performanceScore.multiply(wP));
        hs = hs.add(resourceScore.multiply(wR));
        return hs.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * calculate Availability Score.
     *
     * @param successRates the successRates
     * @param moTypeWeights the moTypeWeights
     * @return the result
     */
    public BigDecimal calculateAvailabilityScore(Map<String, BigDecimal> successRates,
        Map<String, BigDecimal> moTypeWeights) {
        return calculateAvailabilityScore(successRates, moTypeWeights, DEFAULT_AVAILABILITY_THRESHOLDS);
    }

    /**
     * calculate Availability Score.
     *
     * @param successRates the successRates
     * @param moTypeWeights the moTypeWeights
     * @param thresholds the thresholds
     * @return the result
     */
    public BigDecimal calculateAvailabilityScore(Map<String, BigDecimal> successRates,
        Map<String, BigDecimal> moTypeWeights, BigDecimal[] thresholds) {
        BigDecimal[] t = thresholds != null && thresholds.length == 3 ? thresholds : DEFAULT_AVAILABILITY_THRESHOLDS;
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : successRates.entrySet()) {
            String moType = entry.getKey();
            BigDecimal successRate = entry.getValue();
            BigDecimal score = tierAvailability(successRate, t);
            BigDecimal weight = moTypeWeights.getOrDefault(moType, BigDecimal.ZERO);
            totalScore = totalScore.add(score.multiply(weight));
        }
        return totalScore.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * calculate Performance Score.
     *
     * @param avgResponseTimes the avgResponseTimes
     * @param moTypeWeights the moTypeWeights
     * @return the result
     */
    public BigDecimal calculatePerformanceScore(Map<String, BigDecimal> avgResponseTimes,
        Map<String, BigDecimal> moTypeWeights) {
        return calculatePerformanceScore(avgResponseTimes, moTypeWeights, DEFAULT_PERFORMANCE_THRESHOLDS);
    }

    /**
     * calculate Performance Score.
     *
     * @param avgResponseTimes the avgResponseTimes
     * @param moTypeWeights the moTypeWeights
     * @param thresholds the thresholds
     * @return the result
     */
    public BigDecimal calculatePerformanceScore(Map<String, BigDecimal> avgResponseTimes,
        Map<String, BigDecimal> moTypeWeights, BigDecimal[] thresholds) {
        BigDecimal[] t = thresholds != null && thresholds.length == 3 ? thresholds : DEFAULT_PERFORMANCE_THRESHOLDS;
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : avgResponseTimes.entrySet()) {
            String moType = entry.getKey();
            BigDecimal avgRt = entry.getValue();
            BigDecimal score = tierPerformance(avgRt, t);
            BigDecimal weight = moTypeWeights.getOrDefault(moType, BigDecimal.ZERO);
            totalScore = totalScore.add(score.multiply(weight));
        }
        return totalScore.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * calculate Resource Score.
     *
     * @param alarms the alarms
     * @param alarmWeights the alarmWeights
     * @param alarmIdWeights the alarmIdWeights
     * @param iMax the iMax
     * @return the result
     */
    public BigDecimal calculateResourceScore(List<AlarmInfo> alarms, Map<String, BigDecimal> alarmWeights,
        Map<String, BigDecimal> alarmIdWeights, int iMax) {
        BigDecimal impact = BigDecimal.ZERO;
        for (AlarmInfo alarm : alarms) {
            String severity = alarm.getSeverity();
            if ("INFO".equalsIgnoreCase(severity) || "4".equals(severity)) {
                continue;
            }
            BigDecimal weight = alarmIdWeights != null && alarm.getAlarmId() != null
                ? alarmIdWeights.getOrDefault(alarm.getAlarmId(), alarmWeights.getOrDefault(severity, BigDecimal.ZERO))
                : alarmWeights.getOrDefault(severity, BigDecimal.ZERO);
            int count = alarm.getCount() != null ? alarm.getCount() : 1;
            impact = impact.add(weight.multiply(new BigDecimal(count)));
        }
        BigDecimal iMaxBd = new BigDecimal(iMax);
        if (impact.compareTo(iMaxBd) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = impact.divide(iMaxBd, 2, RoundingMode.HALF_UP);
        return BigDecimal.ONE.subtract(ratio).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal tierAvailability(BigDecimal successRate, BigDecimal[] t) {
        if (successRate.compareTo(t[0]) > 0)
            return BigDecimal.ONE;
        if (successRate.compareTo(t[1]) > 0)
            return new BigDecimal("0.6");
        if (successRate.compareTo(t[2]) > 0)
            return new BigDecimal("0.3");
        return BigDecimal.ZERO;
    }

    private BigDecimal tierPerformance(BigDecimal avgRtMs, BigDecimal[] t) {
        if (avgRtMs.compareTo(t[0]) <= 0)
            return BigDecimal.ONE;
        if (avgRtMs.compareTo(t[1]) <= 0)
            return new BigDecimal("0.6");
        if (avgRtMs.compareTo(t[2]) <= 0)
            return new BigDecimal("0.3");
        return BigDecimal.ZERO;
    }
}
