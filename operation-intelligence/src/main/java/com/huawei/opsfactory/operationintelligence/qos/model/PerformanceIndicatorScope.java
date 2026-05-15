/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Performance Indicator Scope.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerformanceIndicatorScope {
    private String agentSolutionType;

    private String indicatorCode;

    private String indicatorName;

    private String type;

    private String moType;

    private String measUnitKey;

    private String measObject;

    private String measTypeKeys;

    private String thresholds;

    private BigDecimal weight;

    /**
     * Gets the agent solution type.
     *
     * @return the result
     */
    public String getAgentSolutionType() {
        return agentSolutionType;
    }

    /**
     * Sets the agent solution type.
     *
     * @param agentSolutionType the agentSolutionType
     */
    public void setAgentSolutionType(String agentSolutionType) {
        this.agentSolutionType = agentSolutionType;
    }

    /**
     * Gets the indicator code.
     *
     * @return the result
     */
    public String getIndicatorCode() {
        return indicatorCode;
    }

    /**
     * Sets the indicator code.
     *
     * @param indicatorCode the indicatorCode
     */
    public void setIndicatorCode(String indicatorCode) {
        this.indicatorCode = indicatorCode;
    }

    /**
     * Gets the indicator name.
     *
     * @return the result
     */
    public String getIndicatorName() {
        return indicatorName;
    }

    /**
     * Sets the indicator name.
     *
     * @param indicatorName the indicatorName
     */
    public void setIndicatorName(String indicatorName) {
        this.indicatorName = indicatorName;
    }

    /**
     * Gets the type.
     *
     * @return the result
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the mo type.
     *
     * @return the result
     */
    public String getMoType() {
        return moType;
    }

    /**
     * Sets the mo type.
     *
     * @param moType the moType
     */
    public void setMoType(String moType) {
        this.moType = moType;
    }

    /**
     * Gets the meas unit key.
     *
     * @return the result
     */
    public String getMeasUnitKey() {
        return measUnitKey;
    }

    /**
     * Sets the meas unit key.
     *
     * @param measUnitKey the measUnitKey
     */
    public void setMeasUnitKey(String measUnitKey) {
        this.measUnitKey = measUnitKey;
    }

    /**
     * Gets the meas object.
     *
     * @return the result
     */
    public String getMeasObject() {
        return measObject;
    }

    /**
     * Sets the meas object.
     *
     * @param measObject the measObject
     */
    public void setMeasObject(String measObject) {
        this.measObject = measObject;
    }

    /**
     * Gets the meas type keys.
     *
     * @return the result
     */
    public String getMeasTypeKeys() {
        return measTypeKeys;
    }

    /**
     * Sets the meas type keys.
     *
     * @param measTypeKeys the measTypeKeys
     */
    public void setMeasTypeKeys(String measTypeKeys) {
        this.measTypeKeys = measTypeKeys;
    }

    /**
     * Gets the thresholds.
     *
     * @return the result
     */
    public String getThresholds() {
        return sortThresholds(thresholds);
    }

    /**
     * Sets the thresholds.
     *
     * @param thresholds the thresholds
     */
    public void setThresholds(String thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * Gets the weight.
     *
     * @return the result
     */
    public BigDecimal getWeight() {
        return weight;
    }

    /**
     * Sets the weight.
     *
     * @param weight the weight
     */
    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    private String sortThresholds(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 分割并排序
        return Arrays.stream(input.split(";"))
            .map(entry -> entry.split(":"))
            .filter(parts -> parts.length == 2) // 确保有key和value
            .sorted(Comparator.comparingDouble(a -> Double.parseDouble(a[0])))
            .map(parts -> parts[0] + ":" + parts[1])
            .collect(Collectors.joining(";"));
    }
}
