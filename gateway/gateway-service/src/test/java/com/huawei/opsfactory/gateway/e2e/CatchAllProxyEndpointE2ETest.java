/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.ManagedInstance;

import reactor.core.publisher.Mono;

import org.junit.Test;

/**
 * E2E tests for CatchAllProxyController:
 * Verifies auth, admin/user access control, and proxy routing for /agents/{agentId}/** paths.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class CatchAllProxyEndpointE2ETest extends BaseE2ETest {

    /**
     * Executes the admin access to schedules proxies to goosed operation.
     */
    @Test
    public void adminAccessToSchedules_proxiesToGoosed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "admin", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "admin")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/schedules/list"), any())).thenReturn(Mono.empty());

        webClient.get()
            .uri("/gateway/agents/test-agent/schedules/list")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/schedules/list"), any());
    }

    /**
     * Executes the user access to system info allowed operation.
     */
    @Test
    public void userAccessToSystemInfo_allowed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/system_info"), any())).thenReturn(Mono.empty());

        webClient.get()
            .uri("/gateway/agents/test-agent/system_info")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();
    }

    /**
     * Executes the user access to status allowed operation.
     */
    @Test
    public void userAccessToStatus_allowed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/status"), any())).thenReturn(Mono.empty());

        webClient.get()
            .uri("/gateway/agents/test-agent/status")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();
    }

    /**
     * Executes the user access to admin route succeeds operation.
     */
    @Test
    public void userAccessToAdminRoute_succeeds() {
        ManagedInstance instance = new ManagedInstance("test-agent", "alice", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "alice")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/schedules/list"), any())).thenReturn(Mono.empty());

        webClient.get()
            .uri("/gateway/agents/test-agent/schedules/list")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/schedules/list"), any());
    }

    /**
     * Executes the user access to config prompts succeeds operation.
     */
    @Test
    public void userAccessToConfigPrompts_succeeds() {
        ManagedInstance instance = new ManagedInstance("test-agent", "bob", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "bob")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/config/prompts"), any())).thenReturn(Mono.empty());

        webClient.get()
            .uri("/gateway/agents/test-agent/config/prompts")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "bob")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/config/prompts"), any());
    }

    /**
     * Executes the admin access to legacy reply returns404 operation.
     */
    @Test
    public void adminAccessToLegacyReply_returns404() {
        webClient.post()
            .uri("/gateway/agents/test-agent/reply")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    /**
     * Executes the admin access to legacy agent stop returns404 operation.
     */
    @Test
    public void adminAccessToLegacyAgentStop_returns404() {
        webClient.post()
            .uri("/gateway/agents/test-agent/agent/stop")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    /**
     * Executes the unauthenticated returns401 operation.
     */
    @Test
    public void unauthenticated_returns401() {
        webClient.get().uri("/gateway/agents/test-agent/schedules/list").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Executes the query string forwarded to goosed operation.
     */
    @Test
    public void queryStringForwarded_toGoosed() {
        ManagedInstance instance = new ManagedInstance("test-agent", "admin", 9000, 123L, null, "test-secret");
        instance.setStatus(ManagedInstance.Status.RUNNING);
        when(instanceManager.getOrSpawn("test-agent", "admin")).thenReturn(Mono.just(instance));
        when(goosedProxy.proxy(any(), any(), eq(9000), eq("/schedules/list?limit=5"), any())).thenReturn(Mono.empty());

        webClient.get()
            .uri("/gateway/agents/test-agent/schedules/list?limit=5")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "admin")
            .exchange()
            .expectStatus()
            .isOk();

        verify(goosedProxy).proxy(any(), any(), eq(9000), eq("/schedules/list?limit=5"), any());
    }
}
