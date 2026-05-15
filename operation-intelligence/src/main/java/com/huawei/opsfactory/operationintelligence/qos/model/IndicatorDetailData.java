/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Indicator Detail Data.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndicatorDetailData {
    private Long code;

    private String envCode;

    private String indicatorCode;

    private String indicatorName;

    private String type;

    private String dn;

    private BigDecimal dnIndicatorValue;

    private Map<String, String> values;

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
     * Gets the dn.
     *
     * @return the result
     */
    public String getDn() {
        return dn;
    }

    /**
     * Sets the dn.
     *
     * @param dn the dn
     */
    public void setDn(String dn) {
        this.dn = dn;
    }

    /**
     * Gets the values.
     *
     * @return the result
     */
    public Map<String, String> getValues() {
        return values;
    }

    /**
     * Sets the values.
     *
     * @param values the values
     */
    public void setValues(Map<String, String> values) {
        this.values = values;
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

    /**
     * Gets the dn indicator value.
     *
     * @return the result
     */
    public BigDecimal getDnIndicatorValue() {
        return dnIndicatorValue;
    }

    /**
     * Sets the dn indicator value.
     *
     * @param dnIndicatorValue the dnIndicatorValue
     */
    public void setDnIndicatorValue(BigDecimal dnIndicatorValue) {
        this.dnIndicatorValue = dnIndicatorValue;
    }
}
