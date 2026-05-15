/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Dn Element.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DnElement {
    private String dn;

    private String name;

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
     * Gets the name.
     *
     * @return the result
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }
}
