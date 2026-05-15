/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Dv Environment Info.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DvEnvironmentInfo {
    private String envCode;

    private String agentSolutionType;

    private String serverUrl;

    private String utmUser;

    private String utmPassword;

    private String crtContent;

    private String crtFileName;

    private String dns;

    private boolean strictSsl = true;

    /**
     * Dv Environment Info.
     */
    public DvEnvironmentInfo() {
    }

    /**
     * Dv Environment Info.
     *
     * @param envCode the envCode
     * @param agentSolutionType the agentSolutionType
     * @param serverUrl the serverUrl
     * @param utmUser the utmUser
     * @param utmPassword the utmPassword
     * @param crtContent the crtContent
     * @param crtFileName the crtFileName
     */
    public DvEnvironmentInfo(String envCode, String agentSolutionType, String serverUrl, String utmUser,
        String utmPassword, String crtContent, String crtFileName) {
        this.envCode = envCode;
        this.agentSolutionType = agentSolutionType;
        this.serverUrl = serverUrl;
        this.utmUser = utmUser;
        this.utmPassword = utmPassword;
        this.crtContent = crtContent;
        this.crtFileName = crtFileName;
    }

    /**
     * Dv Environment Info.
     *
     * @param envCode the envCode
     * @param agentSolutionType the agentSolutionType
     * @param serverUrl the serverUrl
     * @param utmUser the utmUser
     * @param utmPassword the utmPassword
     * @param crtContent the crtContent
     * @param crtFileName the crtFileName
     * @param dns the dns
     */
    public DvEnvironmentInfo(String envCode, String agentSolutionType, String serverUrl, String utmUser,
        String utmPassword, String crtContent, String crtFileName, String dns) {
        this(envCode, agentSolutionType, serverUrl, utmUser, utmPassword, crtContent, crtFileName);
        this.dns = dns;
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
     * Gets the server url.
     *
     * @return the result
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Sets the server url.
     *
     * @param serverUrl the serverUrl
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * Gets the utm user.
     *
     * @return the result
     */
    public String getUtmUser() {
        return utmUser;
    }

    /**
     * Sets the utm user.
     *
     * @param utmUser the utmUser
     */
    public void setUtmUser(String utmUser) {
        this.utmUser = utmUser;
    }

    /**
     * Gets the utm password.
     *
     * @return the result
     */
    @JsonIgnore
    public String getUtmPassword() {
        return utmPassword;
    }

    /**
     * Sets the utm password.
     *
     * @param utmPassword the utmPassword
     */
    public void setUtmPassword(String utmPassword) {
        this.utmPassword = utmPassword;
    }

    /**
     * Gets the crt content.
     *
     * @return the result
     */
    @JsonIgnore
    public String getCrtContent() {
        return crtContent;
    }

    /**
     * Sets the crt content.
     *
     * @param crtContent the crtContent
     */
    public void setCrtContent(String crtContent) {
        this.crtContent = crtContent;
    }

    /**
     * Gets the crt file name.
     *
     * @return the result
     */
    public String getCrtFileName() {
        return crtFileName;
    }

    /**
     * Sets the crt file name.
     *
     * @param crtFileName the crtFileName
     */
    public void setCrtFileName(String crtFileName) {
        this.crtFileName = crtFileName;
    }

    /**
     * Gets the dns.
     *
     * @return the result
     */
    public String getDns() {
        return dns;
    }

    /**
     * Sets the dns.
     *
     * @param dns the dns
     */
    public void setDns(String dns) {
        this.dns = dns;
    }

    /**
     * Checks whether the strict ssl.
     *
     * @return the result
     */
    public boolean isStrictSsl() {
        return strictSsl;
    }

    /**
     * Sets the strict ssl.
     *
     * @param strictSsl the strictSsl
     */
    public void setStrictSsl(boolean strictSsl) {
        this.strictSsl = strictSsl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "DvEnvironmentInfo{envCode='" + envCode + "', serverUrl='" + serverUrl + "', utmUser='" + utmUser
            + "', utmPassword='*****'}";
    }
}
