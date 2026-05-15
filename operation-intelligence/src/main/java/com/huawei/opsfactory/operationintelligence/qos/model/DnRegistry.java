/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Dn Registry.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DnRegistry {
    private String envCode;

    private List<DnCluster> clusters;

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
     * Gets the clusters.
     *
     * @return the result
     */
    public List<DnCluster> getClusters() {
        return clusters;
    }

    /**
     * Sets the clusters.
     *
     * @param clusters the clusters
     */
    public void setClusters(List<DnCluster> clusters) {
        this.clusters = clusters;
    }
}
