/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DvSslContextFactoryTest {

    private final DvSslContextFactory factory = new DvSslContextFactory();

    @Test
    void createSslContext_nullContent_strictSsl_throws() {
        assertThrows(IllegalStateException.class, () -> factory.createSslContext(null, "cert.jks", true));
    }

    @Test
    void createSslContext_blankContent_strictSsl_throws() {
        assertThrows(IllegalStateException.class, () -> factory.createSslContext("  ", "cert.jks", true));
    }

    @Test
    void createSslContext_nullContent_looseSsl_returnsInsecure() {
        var ctx = factory.createSslContext(null, "cert.jks", false);
        assertNotNull(ctx);
    }

    @Test
    void createSslContext_blankContent_looseSsl_returnsInsecure() {
        var ctx = factory.createSslContext("", "cert.jks", false);
        assertNotNull(ctx);
    }

    @Test
    void createInsecureSslContext_returnsNonNull() {
        var ctx = factory.createInsecureSslContext();
        assertNotNull(ctx);
    }

    @Test
    void createSslContext_nullContent_looseSsl_returnsNonNullTwice() {
        var ctx1 = factory.createSslContext(null, "cert.jks", false);
        var ctx2 = factory.createSslContext(null, "cert.jks", false);
        assertNotNull(ctx1);
        assertNotNull(ctx2);
    }
}
