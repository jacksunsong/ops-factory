/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.BusinessServiceService;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test coverage for Host Group Controller.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(SpringRunner.class)
@WebFluxTest(HostGroupController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class HostGroupControllerTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private HostGroupService hostGroupService;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private BusinessServiceService businessServiceService;

    @MockBean
    private HostService hostService;

    @MockBean
    private PrewarmService prewarmService;

    // ── listGroups ──────────────────────────────────────────────

    /**
     * Tests list groups returns all.
     */
    @Test
    public void testListGroups_returnsAll() {
        when(hostGroupService.listGroups())
            .thenReturn(List.of(makeGroup("g1", "PROD", null, true), makeGroup("g2", "TEST", null, false)));

        webTestClient.get()
            .uri("/gateway/host-groups/")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.groups")
            .isArray()
            .jsonPath("$.groups.length()")
            .isEqualTo(2);
    }

    /**
     * Tests list groups enabled only filters disabled.
     */
    @Test
    public void testListGroups_enabledOnly_filtersDisabled() {
        Map<String, Object> g1 = makeGroup("g1", "PROD", null, false);
        Map<String, Object> g2 = makeGroup("g2", "TEST", null, true);

        when(hostGroupService.listGroups()).thenReturn(new ArrayList<>(List.of(g1, g2)));
        when(hostGroupService.getDisabledGroupIds(any())).thenReturn(Set.of("g1"));

        webTestClient.get()
            .uri("/gateway/host-groups/?enabledOnly=true")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.groups")
            .isArray()
            .jsonPath("$.groups.length()")
            .isEqualTo(1)
            .jsonPath("$.groups[0].id")
            .isEqualTo("g2");
    }

    /**
     * Tests list groups enabled only filters inherited disabled.
     */
    @Test
    public void testListGroups_enabledOnly_filtersInheritedDisabled() {
        Map<String, Object> g1 = makeGroup("g1", "PROD", null, false);
        Map<String, Object> g1Sub = makeGroup("g1-sub", "PROD-Sub", "g1", true);
        Map<String, Object> g2 = makeGroup("g2", "TEST", null, true);

        when(hostGroupService.listGroups()).thenReturn(new ArrayList<>(List.of(g1, g1Sub, g2)));
        when(hostGroupService.getDisabledGroupIds(any())).thenReturn(Set.of("g1", "g1-sub"));

        webTestClient.get()
            .uri("/gateway/host-groups/?enabledOnly=true")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.groups.length()")
            .isEqualTo(1)
            .jsonPath("$.groups[0].id")
            .isEqualTo("g2");
    }

    // ── getTree ─────────────────────────────────────────────────

    /**
     * Tests get tree returns all.
     */
    @Test
    public void testGetTree_returnsAll() {
        when(hostGroupService.listGroups()).thenReturn(new ArrayList<>(List.of(makeGroup("g1", "PROD", null, true))));
        when(clusterService.listClusters(isNull(), isNull())).thenReturn(new ArrayList<>());
        when(businessServiceService.listBusinessServices(isNull(), isNull())).thenReturn(new ArrayList<>());
        when(hostGroupService.getTree(anyList(), anyList(), anyList())).thenAnswer(inv -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tree", inv.getArgument(0));
            return result;
        });

        webTestClient.get()
            .uri("/gateway/host-groups/tree")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.tree")
            .isArray();
    }

    /**
     * Tests get tree enabled only filters disabled groups.
     */
    @Test
    public void testGetTree_enabledOnly_filtersDisabledGroups() {
        Map<String, Object> g1 = makeGroup("g1", "PROD", null, false);
        Map<String, Object> g2 = makeGroup("g2", "TEST", null, true);

        when(hostGroupService.listGroups()).thenReturn(new ArrayList<>(List.of(g1, g2)));
        when(clusterService.listClusters(isNull(), isNull())).thenReturn(new ArrayList<>());
        when(businessServiceService.listBusinessServices(isNull(), isNull())).thenReturn(new ArrayList<>());
        when(hostGroupService.getDisabledGroupIds(any())).thenReturn(Set.of("g1"));
        when(hostGroupService.getTree(anyList(), anyList(), anyList())).thenAnswer(inv -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tree", inv.getArgument(0));
            return result;
        });

        webTestClient.get()
            .uri("/gateway/host-groups/tree?enabledOnly=true")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.tree.length()")
            .isEqualTo(1);
    }

    /**
     * Tests get tree enabled only filters clusters in disabled group.
     */
    @Test
    public void testGetTree_enabledOnly_filtersClustersInDisabledGroup() {
        Map<String, Object> g1 = makeGroup("g1", "PROD", null, false);
        Map<String, Object> g2 = makeGroup("g2", "TEST", null, true);
        Map<String, Object> c1 = makeCluster("c1", "PROD-DB", "g1");
        Map<String, Object> c2 = makeCluster("c2", "TEST-DB", "g2");

        when(hostGroupService.listGroups()).thenReturn(new ArrayList<>(List.of(g1, g2)));
        when(clusterService.listClusters(isNull(), isNull())).thenReturn(new ArrayList<>(List.of(c1, c2)));
        when(businessServiceService.listBusinessServices(isNull(), isNull())).thenReturn(new ArrayList<>());
        when(hostGroupService.getDisabledGroupIds(any())).thenReturn(Set.of("g1"));
        when(hostGroupService.getTree(anyList(), anyList(), anyList())).thenAnswer(inv -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tree", inv.getArgument(0));
            return result;
        });

        webTestClient.get()
            .uri("/gateway/host-groups/tree?enabledOnly=true")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isOk();
    }

    // ── updateGroup (enabled toggle) ────────────────────────────

    /**
     * Tests update group set enabled false.
     */
    @Test
    public void testUpdateGroup_setEnabledFalse() {
        Map<String, Object> updated = makeGroup("g1", "PROD", null, false);
        when(hostGroupService.updateGroup(eq("g1"), any())).thenReturn(updated);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", false);

        webTestClient.put()
            .uri("/gateway/host-groups/g1")
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
            .jsonPath("$.group.enabled")
            .isEqualTo(false);
    }

    /**
     * Tests update group set enabled true.
     */
    @Test
    public void testUpdateGroup_setEnabledTrue() {
        Map<String, Object> updated = makeGroup("g1", "PROD", null, true);
        when(hostGroupService.updateGroup(eq("g1"), any())).thenReturn(updated);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", true);

        webTestClient.put()
            .uri("/gateway/host-groups/g1")
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
            .jsonPath("$.group.enabled")
            .isEqualTo(true);
    }

    /**
     * Tests update group not found.
     */
    @Test
    public void testUpdateGroup_notFound() {
        when(hostGroupService.updateGroup(eq("nonexistent"), any()))
            .thenThrow(new IllegalArgumentException("Host group not found: nonexistent"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", false);

        webTestClient.put()
            .uri("/gateway/host-groups/nonexistent")
            .header("x-secret-key", "test")
            .header("x-user-id", "admin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    // ── Auth ────────────────────────────────────────────────────

    /**
     * Tests list groups unauthorized no key.
     */
    @Test
    public void testListGroups_unauthorized_noKey() {
        webTestClient.get()
            .uri("/gateway/host-groups/")
            .header("x-user-id", "admin")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    /**
     * Tests list groups forbidden non admin.
     */
    @Test
    public void testListGroups_forbidden_nonAdmin() {
        webTestClient.get()
            .uri("/gateway/host-groups/")
            .header("x-secret-key", "test")
            .header("x-user-id", "regular-user")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    // ── Helpers ────────────────────────────────────────────────────

    private Map<String, Object> makeGroup(String id, String name, String parentId, boolean enabled) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id);
        g.put("name", name);
        g.put("parentId", parentId);
        g.put("description", "");
        g.put("code", "");
        g.put("enabled", enabled);
        g.put("createdAt", "2026-01-01T00:00:00Z");
        g.put("updatedAt", "2026-01-01T00:00:00Z");
        return g;
    }

    private Map<String, Object> makeCluster(String id, String name, String groupId) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("name", name);
        c.put("type", "DB");
        c.put("purpose", "");
        c.put("groupId", groupId);
        c.put("description", "");
        c.put("enabled", true);
        c.put("createdAt", "2026-01-01T00:00:00Z");
        c.put("updatedAt", "2026-01-01T00:00:00Z");
        return c;
    }
}
