/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.channel;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring-managed registry that collects all {@link ChannelAdapter} beans and resolves them by channel type.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ChannelAdapterRegistry {
    private final Map<String, ChannelAdapter> adaptersByType;

    /**
     * Creates the channel adapter registry instance.
     */
    public ChannelAdapterRegistry(List<ChannelAdapter> adapters) {
        this.adaptersByType = adapters.stream().collect(Collectors.toMap(ChannelAdapter::type, Function.identity()));
    }

    /**
     * Resolves the adapter for the given channel type, throwing if not found.
     *
     * @param type type filter
     * @return the result
     */
    public ChannelAdapter require(String type) {
        ChannelAdapter adapter = adaptersByType.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No channel adapter registered for type '" + type + "'");
        }
        return adapter;
    }
}
