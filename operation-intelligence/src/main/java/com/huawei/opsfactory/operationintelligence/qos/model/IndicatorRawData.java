/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Indicator Raw Data.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndicatorRawData {
    private Long code;

    private String envCode;

    private String dn;

    private String moType;

    private String neName;

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
     * Gets the ne name.
     *
     * @return the result
     */
    public String getNeName() {
        return neName;
    }

    /**
     * Sets the ne name.
     *
     * @param neName the neName
     */
    public void setNeName(String neName) {
        this.neName = neName;
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
}
