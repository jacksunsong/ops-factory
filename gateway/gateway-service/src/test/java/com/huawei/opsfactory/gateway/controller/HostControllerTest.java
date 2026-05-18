/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.ClusterService;
import com.huawei.opsfactory.gateway.service.HostGroupService;
import com.huawei.opsfactory.gateway.service.HostService;

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
 * Test coverage for Host Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebFluxTest(HostController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class HostControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private HostService hostService;

    @MockBean
    private com.huawei.opsfactory.gateway.service.BusinessServiceService businessServiceService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private HostGroupService hostGroupService;

    // ── listHosts ────────────────────────────────────────────────

    /**
     * Tests list hosts empty.
     */
    @Test
    public void testListHosts_empty() {
        when(hostService.listHosts(any())).thenReturn(List.of());

        webTestClient.get()
            .uri("/gateway/hosts/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.hosts")
            .isArray()
            .jsonPath("$.hosts")
            .isEmpty();
    }

    /**
     * Tests list hosts with hosts.
     */
    @Test
    public void testListHosts_withHosts() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", "host-1");
        host.put("name", "Server1");
        host.put("credential", "***");
        when(hostService.listHosts(any())).thenReturn(List.of(host));

        webTestClient.get()
            .uri("/gateway/hosts/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.hosts[0].id")
            .isEqualTo("host-1")
            .jsonPath("$.hosts[0].name")
            .isEqualTo("Server1");
    }

    /**
     * Tests list hosts with tags filter.
     */
    @Test
    public void testListHosts_withTagsFilter() {
        when(hostService.listHosts(any())).thenReturn(List.of());

        webTestClient.get()
            .uri("/gateway/hosts/?tags=RCPA,GMDB")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk();
    }

    // ── getHost ──────────────────────────────────────────────────

    /**
     * Tests get host existing.
     */
    @Test
    public void testGetHost_existing() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", "host-1");
        host.put("name", "Server1");
        host.put("credential", "***");
        when(hostService.getHost("host-1")).thenReturn(host);

        webTestClient.get()
            .uri("/gateway/hosts/host-1")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.host.id")
            .isEqualTo("host-1");
    }

    /**
     * Tests get host not found.
     */
    @Test
    public void testGetHost_notFound() {
        when(hostService.getHost("nonexistent")).thenThrow(new IllegalArgumentException("Host not found: nonexistent"));

        webTestClient.get()
            .uri("/gateway/hosts/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    // ── createHost ───────────────────────────────────────────────

    /**
     * Tests create host success.
     */
    @Test
    public void testCreateHost_success() {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "NewHost");
        created.put("credential", "***");
        when(hostService.createHost(any())).thenReturn(created);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "NewHost");
        body.put("ip", "10.0.0.1");

        webTestClient.post()
            .uri("/gateway/hosts/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.host.id")
            .isEqualTo("new-id");
    }

    /**
     * Tests create host error.
     */
    @Test
    public void testCreateHost_error() {
        when(hostService.createHost(any())).thenThrow(new RuntimeException("Encryption failed"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Host");

        webTestClient.post()
            .uri("/gateway/hosts/")
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

    // ── updateHost ───────────────────────────────────────────────

    /**
     * Tests update host success.
     */
    @Test
    public void testUpdateHost_success() {
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "host-1");
        updated.put("name", "Updated");
        when(hostService.updateHost(eq("host-1"), any())).thenReturn(updated);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Updated");

        webTestClient.put()
            .uri("/gateway/hosts/host-1")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.host.name")
            .isEqualTo("Updated");
    }

    /**
     * Tests update host not found.
     */
    @Test
    public void testUpdateHost_notFound() {
        when(hostService.updateHost(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("Host not found: nonexistent"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Updated");

        webTestClient.put()
            .uri("/gateway/hosts/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    // ── deleteHost ───────────────────────────────────────────────

    /**
     * Tests delete host success.
     */
    @Test
    public void testDeleteHost_success() {
        when(hostService.deleteHost("host-1")).thenReturn(true);

        webTestClient.delete()
            .uri("/gateway/hosts/host-1")
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
     * Tests delete host not found.
     */
    @Test
    public void testDeleteHost_notFound() {
        when(hostService.deleteHost("nonexistent")).thenReturn(false);

        webTestClient.delete()
            .uri("/gateway/hosts/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false);
    }

    // ── getTags ──────────────────────────────────────────────────

    /**
     * Tests get tags.
     */
    @Test
    public void testGetTags() {
        when(hostService.getAllTags()).thenReturn(List.of("RCPA", "GMDB", "ALL"));

        webTestClient.get()
            .uri("/gateway/hosts/tags")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.tags[0]")
            .isEqualTo("RCPA")
            .jsonPath("$.tags[1]")
            .isEqualTo("GMDB")
            .jsonPath("$.tags[2]")
            .isEqualTo("ALL");
    }

    // ── testConnectivity ─────────────────────────────────────────

    /**
     * Tests connectivity success.
     */
    @Test
    public void testConnectivity_success() {
        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("success", true);
        testResult.put("reachable", true);
        testResult.put("latencyMs", 45);
        when(hostService.testConnection("host-1")).thenReturn(testResult);

        webTestClient.post()
            .uri("/gateway/hosts/host-1/test")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.reachable")
            .isEqualTo(true);
    }

    /**
     * Tests connectivity failure.
     */
    @Test
    public void testConnectivity_failure() {
        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("success", false);
        testResult.put("error", "Connection refused");
        when(hostService.testConnection("host-1")).thenReturn(testResult);

        webTestClient.post()
            .uri("/gateway/hosts/host-1/test")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false);
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests list hosts unauthorized no key.
     */
    @Test
    public void testListHosts_unauthorized_noKey() {
        webTestClient.get()
            .uri("/gateway/hosts/")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Tests list hosts succeeds for any authenticated user.
     */
    @Test
    public void testListHosts_succeeds_forAnyUser() {
        when(hostService.listHosts(any())).thenReturn(List.of());

        webTestClient.get()
            .uri("/gateway/hosts/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isOk();
    }

    /**
     * Tests create host succeeds for any authenticated user.
     */
    @Test
    public void testCreateHost_succeeds_forAnyUser() {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "Host");
        created.put("credential", "***");
        when(hostService.createHost(any())).thenReturn(created);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Host");

        webTestClient.post()
            .uri("/gateway/hosts/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isCreated();
    }
}
