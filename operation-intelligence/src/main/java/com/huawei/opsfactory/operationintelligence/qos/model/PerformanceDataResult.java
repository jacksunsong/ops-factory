/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Performance Data Result.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerformanceDataResult {
    private String dn;

    private String moType;

    private String neName;

    private int period;

    private Map<String, String> values;

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
     * Gets the period.
     *
     * @return the result
     */
    public int getPeriod() {
        return period;
    }

    /**
     * Sets the period.
     *
     * @param period the period
     */
    public void setPeriod(int period) {
        this.period = period;
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
}
