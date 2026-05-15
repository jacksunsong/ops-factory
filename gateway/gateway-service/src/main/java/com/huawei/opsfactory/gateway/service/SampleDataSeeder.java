/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Placeholder seeder — sample data is now injected via E2E test.
 * Kept as a no-op to preserve Spring injection wiring.
 */
@Service
public class SampleDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    /**
     * Creates the sample data seeder instance.
     */
    public SampleDataSeeder(GatewayProperties properties, HostService hostService, HostGroupService hostGroupService,
        ClusterService clusterService, HostRelationService hostRelationService) {
        // Dependencies kept for injection compatibility but not used
    }

    /**
     * No-op initialization kept for Spring injection compatibility.
     */
    @PostConstruct
    public void init() {
        log.info("SampleDataSeeder is a no-op,  data is injected via E2E tests");
    }
}
