/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Alarm Weight.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlarmWeight {
    private String agentSolutionType;

    private String alarmId;

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
     * Gets the alarm id.
     *
     * @return the result
     */
    public String getAlarmId() {
        return alarmId;
    }

    /**
     * Sets the alarm id.
     *
     * @param alarmId the alarmId
     */
    public void setAlarmId(String alarmId) {
        this.alarmId = alarmId;
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
}
