package com.huawei.opsfactory.gateway.common.model;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserRoleTest {

    @Test
    public void testFromUserId_sys() {
        assertEquals(UserRole.ADMIN, UserRole.fromUserId("admin"));
    }

    @Test
    public void testFromUserId_regularUser() {
        assertEquals(UserRole.USER, UserRole.fromUserId("user123"));
        assertEquals(UserRole.USER, UserRole.fromUserId("__default__"));
        assertEquals(UserRole.USER, UserRole.fromUserId(""));
    }

    @Test
    public void testFromUserId_withAdminSet_configuredAdmin() {
        Set<String> admins = Set.of("admin", "aiops");
        assertEquals(UserRole.ADMIN, UserRole.fromUserId("admin", admins));
        assertEquals(UserRole.ADMIN, UserRole.fromUserId("aiops", admins));
    }

    @Test
    public void testFromUserId_withAdminSet_nonAdmin() {
        Set<String> admins = Set.of("admin", "aiops");
        assertEquals(UserRole.USER, UserRole.fromUserId("other", admins));
        assertEquals(UserRole.USER, UserRole.fromUserId("user123", admins));
    }

    @Test
    public void testIsAdmin() {
        assertTrue(UserRole.ADMIN.isAdmin());
        assertFalse(UserRole.USER.isAdmin());
    }
}
