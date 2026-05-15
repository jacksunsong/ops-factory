/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries the request body and contextual state through the request hook pipeline.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class HookContext {
    private final String agentId;

    private final String userId;

    private final Map<String, Object> state = new HashMap<>();

    private String body;

    /**
     * Creates the hook context instance.
     *
     * @param body raw request body string
     * @param agentId agent instance identifier
     * @param userId user identifier
     */
    public HookContext(String body, String agentId, String userId) {
        this.body = body;
        this.agentId = agentId;
        this.userId = userId;
    }

    /**
     * Gets the request body.
     *
     * @return raw request body string
     */
    public String getBody() {
        return body;
    }

    /**
     * Sets the request body.
     *
     * @param body new request body string
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Gets the agent identifier.
     *
     * @return agent instance identifier
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Gets the user identifier.
     *
     * @return user identifier
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the mutable state map shared across hooks.
     *
     * @return mutable state map for inter-hook data passing
     */
    public Map<String, Object> getState() {
        return state;
    }
}
