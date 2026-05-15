/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

class QosCollectionSchedulerTest {

    @Test
    void interpolateThreshold_valueBelowFirstThreshold_returnsFirstScore() {
        BigDecimal result =
            QosCollectionScheduler.interpolateThreshold("0.3:0;0.6:0.3;0.9:0.6;0.95:0.95", new BigDecimal("0.1"));
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void interpolateThreshold_valueAtThreshold_returnsExactScore() {
        BigDecimal result = QosCollectionScheduler.interpolateThreshold("0.3:0;0.6:0.3;0.9:0.6", new BigDecimal("0.6"));
        assertEquals(0, new BigDecimal("0.3").compareTo(result));
    }

    @Test
    void interpolateThreshold_valueBetweenThresholds_interpolates() {
        BigDecimal result = QosCollectionScheduler.interpolateThreshold("0:0;1:1", new BigDecimal("0.5"));
        assertEquals(0, new BigDecimal("0.5").compareTo(result.setScale(4, RoundingMode.HALF_UP)));
    }

    @Test
    void interpolateThreshold_valueAboveAllThresholds_returnsLastScore() {
        BigDecimal result = QosCollectionScheduler.interpolateThreshold("0.3:0;0.6:0.3;0.9:0.6", new BigDecimal("1.0"));
        assertEquals(0, new BigDecimal("0.6").compareTo(result));
    }

    @Test
    void interpolateThreshold_malformedPair_skipped() {
        BigDecimal result = QosCollectionScheduler.interpolateThreshold("bad;0:0;1:1", new BigDecimal("0.5"));
        assertEquals(0, new BigDecimal("0.5").compareTo(result.setScale(4, RoundingMode.HALF_UP)));
    }

    @Test
    void interpolateThreshold_singleThreshold_returnsScoreAtOrBelow() {
        BigDecimal result = QosCollectionScheduler.interpolateThreshold("0.5:0.8", new BigDecimal("0.3"));
        // value 0.3 < threshold 0.5, ratio = 0.3/0.5 = 0.6, score = 0 + 0.6*(0.8-0) = 0.48
        assertEquals(0, new BigDecimal("0.48").compareTo(result.setScale(2, RoundingMode.HALF_UP)));
    }
}
