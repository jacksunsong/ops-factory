/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;

import java.util.Set;

/**
 * User role enumeration.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public enum UserRole {
    ADMIN,
    USER;

    /**
     * Resolves the user role from the given user identifier.
     *
     * @param userId user identifier
     * @return the result
     */
    public static UserRole fromUserId(String userId) {
        return GatewayConstants.SYSTEM_USER.equals(userId) ? ADMIN : USER;
    }

    /**
     * Resolves the user role from the given user identifier and admin user set.
     *
     * @param userId user identifier
     * @param adminUsers admin users
     * @return the result
     */
    public static UserRole fromUserId(String userId, Set<String> adminUsers) {
        return adminUsers.contains(userId) ? ADMIN : USER;
    }

    /**
     * Checks whether this role represents an administrator.
     *
     * @return true if this role represents an administrator
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}
