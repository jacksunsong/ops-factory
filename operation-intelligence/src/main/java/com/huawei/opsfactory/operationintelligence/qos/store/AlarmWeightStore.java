/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.AlarmWeight;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Alarm Weight Store.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class AlarmWeightStore {

    private final JsonFileStore<AlarmWeight> store;

    /**
     * Alarm Weight Store.
     *
     * @param properties the properties
     */
    public AlarmWeightStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot().resolve("qos").resolve("config");
        this.store = new JsonFileStore<>(dir, "alarm_weight", new TypeReference<List<AlarmWeight>>() {}, false, 0, 0);
        this.store.init();
    }

    /**
     * load All.
     *
     * @return the result
     */
    public List<AlarmWeight> loadAll() {
        return store.loadAll();
    }

    /**
     * replace All.
     *
     * @param items the items
     */
    public void replaceAll(List<AlarmWeight> items) {
        store.replaceAll(items);
    }
}
