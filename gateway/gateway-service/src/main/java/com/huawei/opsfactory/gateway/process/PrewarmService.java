/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.process;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Triggers eager agent instance spawning on first user activity to reduce cold-start latency.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class PrewarmService {
    private static final Logger log = LoggerFactory.getLogger(PrewarmService.class);

    private final InstanceManager instanceManager;

    private final GatewayProperties properties;

    private final Set<String> warmedUsers = ConcurrentHashMap.newKeySet();

    /**
     * Creates the prewarm service instance.
     *
     * @param instanceManager manages goosed process instances
     * @param properties gateway configuration properties
     */
    public PrewarmService(InstanceManager instanceManager, GatewayProperties properties) {
        this.instanceManager = instanceManager;
        this.properties = properties;
    }

    /**
     * Called on every authenticated request. Triggers a fire-and-forget spawn
     * of the default agent for first-time users in this gateway lifecycle.
     *
     * @param userId user identifier to check for pre-warming
     */
    public void onUserActivity(String userId) {
        if (!properties.getPrewarm().isEnabled()) {
            return;
        }
        if (GatewayConstants.SYSTEM_USER.equals(userId)) {
            return;
        }
        if (!warmedUsers.add(userId)) {
            // already warmed
            return;
        }

        String agentId = properties.getPrewarm().getDefaultAgentId();
        log.info("Pre-warming {} for user {}", agentId, userId);
        instanceManager.getOrSpawn(agentId, userId)
            .subscribe(inst -> log.info("Pre-warm complete: {}:{} on port {}", agentId, userId, inst.getPort()),
                err -> log.warn("Pre-warm failed for {}:{}: {}", agentId, userId, err.getMessage()));
    }

    /**
     * Reset pre-warm state for a user (called when all their instances are reaped).
     *
     * @param userId user identifier whose pre-warm state to clear
     */
    public void clearUser(String userId) {
        warmedUsers.remove(userId);
    }
}
