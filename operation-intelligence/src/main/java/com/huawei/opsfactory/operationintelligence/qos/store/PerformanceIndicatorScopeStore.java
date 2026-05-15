/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.PerformanceIndicatorScope;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Performance Indicator Scope Store.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class PerformanceIndicatorScopeStore {

    private final JsonFileStore<PerformanceIndicatorScope> store;

    /**
     * Performance Indicator Scope Store.
     *
     * @param properties the properties
     */
    public PerformanceIndicatorScopeStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot().resolve("qos").resolve("config");
        this.store = new JsonFileStore<>(dir, "performance_indicator_scope",
            new TypeReference<List<PerformanceIndicatorScope>>() {}, false, 0, 0);
        this.store.init();
    }

    /**
     * load All.
     *
     * @return the result
     */
    public List<PerformanceIndicatorScope> loadAll() {
        return store.loadAll();
    }

    /**
     * replace All.
     *
     * @param items the items
     */
    public void replaceAll(List<PerformanceIndicatorScope> items) {
        store.replaceAll(items);
    }
}
