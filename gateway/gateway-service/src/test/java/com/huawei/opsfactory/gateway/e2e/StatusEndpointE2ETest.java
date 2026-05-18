/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import org.junit.Test;

/**
 * E2E tests for StatusController endpoints: /status, /me, /config.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class StatusEndpointE2ETest extends BaseE2ETest {
    /**
     * Returns the status returns ok.
     */
    @Test
    public void getStatus_returnsOk() {
        webClient.get()
            .uri("/gateway/status")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .isEqualTo("ok");
    }

    /**
     * Returns the me sys user returns user role.
     */
    @Test
    public void getMe_sysUser_returnsUserRole() {
        webClient.get()
            .uri("/gateway/me")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo("admin")
            .jsonPath("$.role")
            .isEqualTo("user");
    }

    /**
     * Returns the me regular user returns user.
     */
    @Test
    public void getMe_regularUser_returnsUser() {
        webClient.get()
            .uri("/gateway/me")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "user-123")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo("user-123")
            .jsonPath("$.role")
            .isEqualTo("user");
    }

    /**
     * Returns the config returns office preview defaults.
     */
    @Test
    public void getConfig_returnsOfficePreviewDefaults() {
        webClient.get()
            .uri("/gateway/config")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.officePreview.enabled")
            .isEqualTo(false)
            .jsonPath("$.officePreview.onlyofficeUrl")
            .isEqualTo("")
            .jsonPath("$.officePreview.fileBaseUrl")
            .isEqualTo("");
    }

    /**
     * Returns the config unauthenticated returns401.
     */
    @Test
    public void getConfig_unauthenticated_returns401() {
        webClient.get().uri("/gateway/config").exchange().expectStatus().isUnauthorized();
    }
}
