/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Indicator Normalize Data.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndicatorNormalizeData {
    private Long code;

    private String envCode;

    private String type;

    private BigDecimal indicatorValue;

    private Long timestamp;

    /**
     * Gets the code.
     *
     * @return the result
     */
    public Long getCode() {
        return code;
    }

    /**
     * Sets the code.
     *
     * @param code the code
     */
    public void setCode(Long code) {
        this.code = code;
    }

    /**
     * Gets the env code.
     *
     * @return the result
     */
    public String getEnvCode() {
        return envCode;
    }

    /**
     * Sets the env code.
     *
     * @param envCode the envCode
     */
    public void setEnvCode(String envCode) {
        this.envCode = envCode;
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
     * Gets the indicator value.
     *
     * @return the result
     */
    public BigDecimal getIndicatorValue() {
        return indicatorValue;
    }

    /**
     * Sets the indicator value.
     *
     * @param indicatorValue the indicatorValue
     */
    public void setIndicatorValue(BigDecimal indicatorValue) {
        this.indicatorValue = indicatorValue;
    }

    /**
     * Gets the timestamp.
     *
     * @return the result
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp the timestamp
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
