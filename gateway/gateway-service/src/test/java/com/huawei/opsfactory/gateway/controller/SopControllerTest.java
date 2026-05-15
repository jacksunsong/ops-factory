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
import com.huawei.opsfactory.gateway.service.SopService;

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
 * Test coverage for Sop Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebFluxTest(SopController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class SopControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private SopService sopService;

    @MockBean
    private com.huawei.opsfactory.gateway.process.PrewarmService prewarmService;

    // ── listSops ─────────────────────────────────────────────────

    /**
     * Tests list sops empty.
     */
    @Test
    public void testListSops_empty() {
        when(sopService.listSops()).thenReturn(List.of());

        webTestClient.get()
            .uri("/gateway/sops/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.sops")
            .isArray()
            .jsonPath("$.sops")
            .isEmpty();
    }

    /**
     * Tests list sops with data.
     */
    @Test
    public void testListSops_withData() {
        Map<String, Object> sop = new LinkedHashMap<>();
        sop.put("id", "sop-1");
        sop.put("name", "RCPA诊断");
        when(sopService.listSops()).thenReturn(List.of(sop));

        webTestClient.get()
            .uri("/gateway/sops/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.sops[0].id")
            .isEqualTo("sop-1")
            .jsonPath("$.sops[0].name")
            .isEqualTo("RCPA诊断");
    }

    // ── getSop ───────────────────────────────────────────────────

    /**
     * Tests get sop existing.
     */
    @Test
    public void testGetSop_existing() {
        Map<String, Object> sop = new LinkedHashMap<>();
        sop.put("id", "sop-1");
        sop.put("name", "TestSOP");
        sop.put("nodes", List.of());
        when(sopService.getSop("sop-1")).thenReturn(sop);

        webTestClient.get()
            .uri("/gateway/sops/sop-1")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(true)
            .jsonPath("$.sop.id")
            .isEqualTo("sop-1");
    }

    /**
     * Tests get sop not found.
     */
    @Test
    public void testGetSop_notFound() {
        when(sopService.getSop("nonexistent")).thenThrow(new IllegalArgumentException("SOP not found: nonexistent"));

        webTestClient.get()
            .uri("/gateway/sops/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .is5xxServerError();
    }

    // ── createSop ────────────────────────────────────────────────

    /**
     * Tests create sop success.
     */
    @Test
    public void testCreateSop_success() {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "new-id");
        created.put("name", "NewSOP");
        when(sopService.createSop(any())).thenReturn(created);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "NewSOP");
        body.put("description", "Test");

        webTestClient.post()
            .uri("/gateway/sops/")
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
            .jsonPath("$.sop.id")
            .isEqualTo("new-id");
    }

    /**
     * Tests create sop error.
     */
    @Test
    public void testCreateSop_error() {
        when(sopService.createSop(any())).thenThrow(new RuntimeException("Write failed"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "SOP");

        webTestClient.post()
            .uri("/gateway/sops/")
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

    // ── updateSop ────────────────────────────────────────────────

    /**
     * Tests update sop success.
     */
    @Test
    public void testUpdateSop_success() {
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "sop-1");
        updated.put("name", "UpdatedSOP");
        when(sopService.updateSop(eq("sop-1"), any())).thenReturn(updated);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "UpdatedSOP");

        webTestClient.put()
            .uri("/gateway/sops/sop-1")
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
            .jsonPath("$.sop.name")
            .isEqualTo("UpdatedSOP");
    }

    /**
     * Tests update sop not found.
     */
    @Test
    public void testUpdateSop_notFound() {
        when(sopService.updateSop(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("SOP not found: nonexistent"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Updated");

        webTestClient.put()
            .uri("/gateway/sops/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isEqualTo(409);
    }

    // ── deleteSop ────────────────────────────────────────────────

    /**
     * Tests delete sop success.
     */
    @Test
    public void testDeleteSop_success() {
        when(sopService.deleteSop("sop-1")).thenReturn(true);

        webTestClient.delete()
            .uri("/gateway/sops/sop-1")
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
     * Tests delete sop not found.
     */
    @Test
    public void testDeleteSop_notFound() {
        when(sopService.deleteSop("nonexistent")).thenReturn(false);

        webTestClient.delete()
            .uri("/gateway/sops/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false);
    }

    /**
     * Tests create sop duplicate name returns conflict.
     */
    @Test
    public void testCreateSop_duplicateName_returnsConflict() {
        when(sopService.createSop(any())).thenThrow(new IllegalArgumentException("SOP name already exists: TestSOP"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "TestSOP");

        webTestClient.post()
            .uri("/gateway/sops/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.success")
            .isEqualTo(false)
            .jsonPath("$.error")
            .isEqualTo("SOP name already exists");
    }

    // ── Auth tests ───────────────────────────────────────────────

    /**
     * Tests list sops unauthorized no key.
     */
    @Test
    public void testListSops_unauthorized_noKey() {
        webTestClient.get()
            .uri("/gateway/sops/")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Tests list sops forbidden non admin.
     */
    @Test
    public void testListSops_forbidden_nonAdmin() {
        webTestClient.get()
            .uri("/gateway/sops/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Tests create sop forbidden non admin.
     */
    @Test
    public void testCreateSop_forbidden_nonAdmin() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "SOP");

        webTestClient.post()
            .uri("/gateway/sops/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}
