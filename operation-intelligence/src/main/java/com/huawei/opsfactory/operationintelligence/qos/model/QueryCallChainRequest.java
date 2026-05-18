/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Query Call Chain Request DTO.
 *
 * @author call-chain
 * @since 2026-05-18
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryCallChainRequest {

    private String solutionType;

    private List<Condition> condition;

    private Long startTime;

    private Long endTime;

    /**
     * Gets the solution type.
     *
     * @return the solution type
     */
    public String getSolutionType() {
        return solutionType;
    }

    /**
     * Sets the solution type.
     *
     * @param solutionType the solution type
     */
    public void setSolutionType(String solutionType) {
        this.solutionType = solutionType;
    }

    /**
     * Gets the conditions.
     *
     * @return the conditions
     */
    public List<Condition> getCondition() {
        return condition;
    }

    /**
     * Sets the conditions.
     *
     * @param condition the conditions
     */
    public void setCondition(List<Condition> condition) {
        this.condition = condition;
    }

    /**
     * Gets the start time.
     *
     * @return the start time in milliseconds
     */
    public Long getStartTime() {
        return startTime;
    }

    /**
     * Sets the start time.
     *
     * @param startTime the start time in milliseconds
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    /**
     * Gets the end time.
     *
     * @return the end time in milliseconds
     */
    public Long getEndTime() {
        return endTime;
    }

    /**
     * Sets the end time.
     *
     * @param endTime the end time in milliseconds
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    /**
     * Condition.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        private String conditionKey;
        private String conditionValue;

        /**
         * Gets the condition key.
         *
         * @return the condition key
         */
        public String getConditionKey() {
            return conditionKey;
        }

        /**
         * Sets the condition key.
         *
         * @param conditionKey the condition key
         */
        public void setConditionKey(String conditionKey) {
            this.conditionKey = conditionKey;
        }

        /**
         * Gets the condition value.
         *
         * @return the condition value
         */
        public String getConditionValue() {
            return conditionValue;
        }

        /**
         * Sets the condition value.
         *
         * @param conditionValue the condition value
         */
        public void setConditionValue(String conditionValue) {
            this.conditionValue = conditionValue;
        }
    }
}