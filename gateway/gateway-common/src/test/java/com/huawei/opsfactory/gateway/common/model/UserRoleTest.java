/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

/**
 * Test coverage for User Role.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class UserRoleTest {

    /**
     * Tests from user id sys.
     */
    @Test
    public void testFromUserId_sys() {
        assertEquals(UserRole.ADMIN, UserRole.fromUserId("admin"));
    }

    /**
     * Tests from user id regular user.
     */
    @Test
    public void testFromUserId_regularUser() {
        assertEquals(UserRole.USER, UserRole.fromUserId("user123"));
        assertEquals(UserRole.USER, UserRole.fromUserId("__default__"));
        assertEquals(UserRole.USER, UserRole.fromUserId(""));
    }

    /**
     * Tests from user id with admin set configured admin.
     */
    @Test
    public void testFromUserId_withAdminSet_configuredAdmin() {
        Set<String> admins = Set.of("admin", "aiops");
        assertEquals(UserRole.ADMIN, UserRole.fromUserId("admin", admins));
        assertEquals(UserRole.ADMIN, UserRole.fromUserId("aiops", admins));
    }

    /**
     * Tests from user id with admin set non admin.
     */
    @Test
    public void testFromUserId_withAdminSet_nonAdmin() {
        Set<String> admins = Set.of("admin", "aiops");
        assertEquals(UserRole.USER, UserRole.fromUserId("other", admins));
        assertEquals(UserRole.USER, UserRole.fromUserId("user123", admins));
    }

    /**
     * Tests is admin.
     */
    @Test
    public void testIsAdmin() {
        assertTrue(UserRole.ADMIN.isAdmin());
        assertFalse(UserRole.USER.isAdmin());
    }
}
