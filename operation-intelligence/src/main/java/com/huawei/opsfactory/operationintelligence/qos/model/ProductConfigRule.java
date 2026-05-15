/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Product Config Rule.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductConfigRule {
    private String agentSolutionType;

    private String excludeAlarmCode;

    private String alarmWeight;

    private Integer alarmScoreMax;

    private String healthWeight;

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
     * Gets the exclude alarm code.
     *
     * @return the result
     */
    public String getExcludeAlarmCode() {
        return excludeAlarmCode;
    }

    /**
     * Sets the exclude alarm code.
     *
     * @param excludeAlarmCode the excludeAlarmCode
     */
    public void setExcludeAlarmCode(String excludeAlarmCode) {
        this.excludeAlarmCode = excludeAlarmCode;
    }

    /**
     * Gets the alarm weight.
     *
     * @return the result
     */
    public String getAlarmWeight() {
        return alarmWeight;
    }

    /**
     * Sets the alarm weight.
     *
     * @param alarmWeight the alarmWeight
     */
    public void setAlarmWeight(String alarmWeight) {
        this.alarmWeight = alarmWeight;
    }

    /**
     * Gets the alarm score max.
     *
     * @return the result
     */
    public Integer getAlarmScoreMax() {
        return alarmScoreMax;
    }

    /**
     * Sets the alarm score max.
     *
     * @param alarmScoreMax the alarmScoreMax
     */
    public void setAlarmScoreMax(Integer alarmScoreMax) {
        this.alarmScoreMax = alarmScoreMax;
    }

    /**
     * Gets the health weight.
     *
     * @return the result
     */
    public String getHealthWeight() {
        return healthWeight;
    }

    /**
     * Sets the health weight.
     *
     * @param healthWeight the healthWeight
     */
    public void setHealthWeight(String healthWeight) {
        this.healthWeight = healthWeight;
    }
}
