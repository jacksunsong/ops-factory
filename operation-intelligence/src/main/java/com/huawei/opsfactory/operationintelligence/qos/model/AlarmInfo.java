/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Alarm Info.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlarmInfo {
    private String alarmId;

    private String alarmName;

    private String severity;

    private String dn;

    private String meName;

    private Long occurUtc;

    private Integer count;

    private String moi;

    private String additionalInformation;

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
     * Gets the alarm name.
     *
     * @return the result
     */
    public String getAlarmName() {
        return alarmName;
    }

    /**
     * Sets the alarm name.
     *
     * @param alarmName the alarmName
     */
    public void setAlarmName(String alarmName) {
        this.alarmName = alarmName;
    }

    /**
     * Gets the severity.
     *
     * @return the result
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * Sets the severity.
     *
     * @param severity the severity
     */
    public void setSeverity(String severity) {
        this.severity = severity;
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
     * Gets the me name.
     *
     * @return the result
     */
    public String getMeName() {
        return meName;
    }

    /**
     * Sets the me name.
     *
     * @param meName the meName
     */
    public void setMeName(String meName) {
        this.meName = meName;
    }

    /**
     * Gets the occur utc.
     *
     * @return the result
     */
    public Long getOccurUtc() {
        return occurUtc;
    }

    /**
     * Sets the occur utc.
     *
     * @param occurUtc the occurUtc
     */
    public void setOccurUtc(Long occurUtc) {
        this.occurUtc = occurUtc;
    }

    /**
     * Gets the count.
     *
     * @return the result
     */
    public Integer getCount() {
        return count;
    }

    /**
     * Sets the count.
     *
     * @param count the count
     */
    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * Gets the moi.
     *
     * @return the result
     */
    public String getMoi() {
        return moi;
    }

    /**
     * Sets the moi.
     *
     * @param moi the moi
     */
    public void setMoi(String moi) {
        this.moi = moi;
    }

    /**
     * Gets the additional information.
     *
     * @return the result
     */
    public String getAdditionalInformation() {
        return additionalInformation;
    }

    /**
     * Sets the additional information.
     *
     * @param additionalInformation the additionalInformation
     */
    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }
}
