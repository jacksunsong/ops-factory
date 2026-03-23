package com.huawei.opsfactory.gateway.common.model;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;

public enum UserRole {
    ADMIN,
    USER;

    public static UserRole fromUserId(String userId) {
        return GatewayConstants.SYSTEM_USER.equals(userId) ? ADMIN : USER;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
