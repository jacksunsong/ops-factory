/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorDetailData;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Indicator Detail Data Store.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class IndicatorDetailDataStore {

    private final JsonFileStore<IndicatorDetailData> store;

    /**
     * Indicator Detail Data Store.
     *
     * @param properties the properties
     */
    public IndicatorDetailDataStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot().resolve("qos").resolve("detail");
        long rotationMs = properties.getQos().getRotationIntervalMs();
        long retentionMs = properties.getQos().getDetailDataRetentionDays() * 86400_000L;
        this.store = new JsonFileStore<>(dir, "indicator_detail_data",
            new TypeReference<List<IndicatorDetailData>>() {}, true, rotationMs, retentionMs);
        this.store.init();
    }

    /**
     * load Range.
     *
     * @param startMs the startMs
     * @param endMs the endMs
     * @return the result
     */
    public List<IndicatorDetailData> loadRange(long startMs, long endMs) {
        return store.loadRange(startMs, endMs);
    }

    /**
     * append.
     *
     * @param item the item
     */
    public void append(IndicatorDetailData item) {
        store.append(item);
    }

    /**
     * append All.
     *
     * @param items the items
     */
    public void appendAll(List<IndicatorDetailData> items) {
        store.appendAll(items);
    }

    /**
     * cleanup.
     */
    public void cleanup() {
        store.cleanup();
    }
}
