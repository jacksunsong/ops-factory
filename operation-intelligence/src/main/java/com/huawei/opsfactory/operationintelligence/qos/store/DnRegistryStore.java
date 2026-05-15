/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.DnRegistry;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Dn Registry Store.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class DnRegistryStore {

    private final JsonFileStore<DnRegistry> store;

    /**
     * Dn Registry Store.
     *
     * @param properties the properties
     */
    public DnRegistryStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot().resolve("qos").resolve("config");
        this.store = new JsonFileStore<>(dir, "dn_registry", new TypeReference<List<DnRegistry>>() {}, false, 0, 0);
        this.store.init();
    }

    /**
     * load All.
     *
     * @return the result
     */
    public List<DnRegistry> loadAll() {
        return store.loadAll();
    }

    /**
     * replace All.
     *
     * @param items the items
     */
    public void replaceAll(List<DnRegistry> items) {
        store.replaceAll(items);
    }
}
