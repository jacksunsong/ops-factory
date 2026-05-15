/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Dn Cluster.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DnCluster {
    private String clusterDn;

    private List<DnElement> elements;

    /**
     * Gets the cluster dn.
     *
     * @return the result
     */
    public String getClusterDn() {
        return clusterDn;
    }

    /**
     * Sets the cluster dn.
     *
     * @param clusterDn the clusterDn
     */
    public void setClusterDn(String clusterDn) {
        this.clusterDn = clusterDn;
    }

    /**
     * Gets the elements.
     *
     * @return the result
     */
    public List<DnElement> getElements() {
        return elements;
    }

    /**
     * Sets the elements.
     *
     * @param elements the elements
     */
    public void setElements(List<DnElement> elements) {
        this.elements = elements;
    }
}
