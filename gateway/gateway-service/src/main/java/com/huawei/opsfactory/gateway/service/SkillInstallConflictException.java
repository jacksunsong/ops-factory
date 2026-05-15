/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

/**
 * Thrown when a skill is already installed for the target agent.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class SkillInstallConflictException extends RuntimeException {

    /**
     * Creates the skill install conflict exception instance.
     */
    public SkillInstallConflictException(String message) {
        super(message);
    }
}
