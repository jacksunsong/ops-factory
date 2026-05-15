/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.ProductConfigRule;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Product Config Rule Store.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class ProductConfigRuleStore {

    private final JsonFileStore<ProductConfigRule> store;

    /**
     * Product Config Rule Store.
     *
     * @param properties the properties
     */
    public ProductConfigRuleStore(OperationIntelligenceProperties properties) {
        Path dir = properties.resolveDataRoot().resolve("qos").resolve("config");
        this.store = new JsonFileStore<>(dir, "product_config_rule", new TypeReference<List<ProductConfigRule>>() {},
            false, 0, 0);
        this.store.init();
    }

    /**
     * load All.
     *
     * @return the result
     */
    public List<ProductConfigRule> loadAll() {
        return store.loadAll();
    }

    /**
     * replace All.
     *
     * @param items the items
     */
    public void replaceAll(List<ProductConfigRule> items) {
        store.replaceAll(items);
    }
}
