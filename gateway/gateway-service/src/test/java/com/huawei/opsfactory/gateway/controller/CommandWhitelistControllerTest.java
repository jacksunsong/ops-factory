/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.CommandWhitelistService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Command Whitelist Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebFluxTest(CommandWhitelistController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class CommandWhitelistControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CommandWhitelistService commandWhitelistService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    // ── getWhitelist ─────────────────────────────────────────────

    /**
     * Tests get whitelist.
     */
    @Test
    public void testGetWhitelist() {
        Map<String, Object> whitelist = new LinkedHashMap<>();
        whitelist.put("commands", List.of(Map.of("pattern", "ps", "description", "查看进程", "enabled", true),
            Map.of("pattern", "tail", "description", "查看日志", "enabled", true)));
        when(commandWhitelistService.getWhitelist()).thenReturn(whitelist);

        webTestClient.get()
            .uri("/gateway/command-whitelist/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.commands")
            .isArray()
            .jsonPath("$.commands[0].pattern")
            .isEqualTo("ps");
    }

    // ── addCommand ───────────────────────────────────────────────

    /**
     * Tests add command success.
     */
    @Test
    public void testAddCommand_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pattern", "iostat");
        body.put("description", "IO统计");
        body.put("enabled", true);

        webTestClient.post()
            .uri("/gateway/command-whitelist/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests add command error.
     */
    @Test
    public void testAddCommand_error() {
        doThrow(new RuntimeException("Write failed")).when(commandWhitelistService).addCommand(any());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pattern", "test");

        webTestClient.post()
            .uri("/gateway/command-whitelist/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .is5xxServerError()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false)
            .jsonPath("$.error")
            .isEqualTo("Internal server error");
    }

    // ── updateCommand ────────────────────────────────────────────

    /**
     * Tests update command success.
     */
    @Test
    public void testUpdateCommand_success() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "updated desc");
        body.put("enabled", false);

        webTestClient.put()
            .uri("/gateway/command-whitelist/ps")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests update command not found.
     */
    @Test
    public void testUpdateCommand_notFound() {
        doThrow(new IllegalArgumentException("Command pattern not found: unknown")).when(commandWhitelistService)
            .updateCommand(eq("unknown"), any());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "test");

        webTestClient.put()
            .uri("/gateway/command-whitelist/unknown")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false);
    }

    // ── deleteCommand ────────────────────────────────────────────

    /**
     * Tests delete command success.
     */
    @Test
    public void testDeleteCommand_success() {
        webTestClient.delete()
            .uri("/gateway/command-whitelist/ps")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true);
    }

    /**
     * Tests delete command not found.
     */
    @Test
    public void testDeleteCommand_notFound() {
        doThrow(new IllegalArgumentException("Command pattern not found: unknown")).when(commandWhitelistService)
            .deleteCommand("unknown");

        webTestClient.delete()
            .uri("/gateway/command-whitelist/unknown")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false);
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests get whitelist unauthorized no key.
     */
    @Test
    public void testGetWhitelist_unauthorized_noKey() {
        webTestClient.get()
            .uri("/gateway/command-whitelist/")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Tests get whitelist forbidden non admin.
     */
    @Test
    public void testGetWhitelist_forbidden_nonAdmin() {
        webTestClient.get()
            .uri("/gateway/command-whitelist/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Tests add command forbidden non admin.
     */
    @Test
    public void testAddCommand_forbidden_nonAdmin() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pattern", "test");

        webTestClient.post()
            .uri("/gateway/command-whitelist/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}
