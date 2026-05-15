/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import org.junit.Test;

/**
 * E2E tests for the authentication filter chain.
 * Verifies AuthWebFilter and UserContextFilter behavior through real HTTP requests.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AuthFilterE2ETest extends BaseE2ETest {

    /**
     * Executes the status endpoint no auth returns401 operation.
     */
    @Test
    public void statusEndpoint_noAuth_returns401() {
        webClient.get().uri("/gateway/status").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Executes the protected endpoint no secret key returns401 operation.
     */
    @Test
    public void protectedEndpoint_noSecretKey_returns401() {
        webClient.get().uri("/gateway/me").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Executes the protected endpoint wrong secret key returns401 operation.
     */
    @Test
    public void protectedEndpoint_wrongSecretKey_returns401() {
        webClient.get()
            .uri("/gateway/me")
            .header(HEADER_SECRET_KEY, "wrong-key")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Executes the protected endpoint empty secret key returns401 operation.
     */
    @Test
    public void protectedEndpoint_emptySecretKey_returns401() {
        webClient.get().uri("/gateway/me").header(HEADER_SECRET_KEY, "").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Executes the protected endpoint valid secret key in header returns200 operation.
     */
    @Test
    public void protectedEndpoint_validSecretKeyInHeader_returns200() {
        webClient.get().uri("/gateway/me").header(HEADER_SECRET_KEY, SECRET_KEY).exchange().expectStatus().isOk();
    }

    /**
     * Executes the protected endpoint valid secret key in query param returns200 operation.
     */
    @Test
    public void protectedEndpoint_validSecretKeyInQueryParam_returns200() {
        webClient.get().uri("/gateway/me?key=" + SECRET_KEY).exchange().expectStatus().isOk();
    }

    /**
     * Executes the options request no auth passes through operation.
     */
    @Test
    public void optionsRequest_noAuth_passesThrough() {
        webClient.options().uri("/gateway/me").exchange().expectStatus().isNoContent();
    }

    /**
     * Executes the me endpoint no user id header returns unknown operation.
     */
    @Test
    public void meEndpoint_noUserIdHeader_returnsUnknown() {
        // /me is excluded from UserContextFilter's user-id requirement;
        // without the filter setting attributes, the controller returns defaults.
        webClient.get()
            .uri("/gateway/me")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo("unknown")
            .jsonPath("$.role")
            .isEqualTo("user");
    }

    /**
     * Executes the me endpoint sys user returns sys operation.
     */
    @Test
    public void meEndpoint_sysUser_returnsSys() {
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
            .isEqualTo("admin");
    }

    /**
     * Executes the me endpoint regular user returns user operation.
     */
    @Test
    public void meEndpoint_regularUser_returnsUser() {
        webClient.get()
            .uri("/gateway/me")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo("alice")
            .jsonPath("$.role")
            .isEqualTo("user");
    }

    /**
     * Executes the me endpoint blank user id header returns unknown operation.
     */
    @Test
    public void meEndpoint_blankUserIdHeader_returnsUnknown() {
        // /me is a system endpoint: blank x-user-id does not reject, falls back to unknown
        webClient.get()
            .uri("/gateway/me")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "  ")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo("unknown")
            .jsonPath("$.role")
            .isEqualTo("user");
    }

    /**
     * Executes the admin endpoint regular user returns403 operation.
     */
    @Test
    public void adminEndpoint_regularUser_returns403() {
        webClient.get()
            .uri("/gateway/runtime-source/system")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Executes the admin endpoint no auth returns401before forbidden operation.
     */
    @Test
    public void adminEndpoint_noAuth_returns401beforeForbidden() {
        // Auth filter runs before user context filter
        webClient.get().uri("/gateway/runtime-source/system").exchange().expectStatus().isUnauthorized();
    }
}
