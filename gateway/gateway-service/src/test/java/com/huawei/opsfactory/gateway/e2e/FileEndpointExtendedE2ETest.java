/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;

import java.nio.file.Path;

/**
 * Extended E2E tests for FileController covering:
 * - Path traversal via controller (returns 403)
 * - Upload without multipart content type (returns 400)
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class FileEndpointExtendedE2ETest extends BaseE2ETest {

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        when(agentConfigService.getUserAgentDir(any(String.class), any(String.class)))
            .thenAnswer(inv -> Path.of("/tmp/test-gateway/gateway/users")
                .resolve(inv.getArgument(0, String.class))
                .resolve("agents")
                .resolve(inv.getArgument(1, String.class)));
    }

    /**
     * Returns the file path traversal returns403.
     */
    @Test
    public void getFile_pathTraversal_returns403() {
        // The PathSanitizer.isSafe check in the controller should block this
        // Since fileService is mocked, we need to verify the controller's own check
        // Path "../../etc/passwd" should be caught by PathSanitizer before reaching fileService
        webClient.get()
            .uri("/gateway/agents/test-agent/files/../../etc/passwd")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    /**
     * Executes the upload file not multipart returns400 operation.
     */
    @Test
    public void uploadFile_notMultipart_returns400() {
        webClient.post()
            .uri("/gateway/agents/test-agent/files/upload")
            .header(HEADER_SECRET_KEY, SECRET_KEY)
            .header(HEADER_USER_ID, "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"file\":\"not-multipart\"}")
            .exchange()
            .expectStatus()
            .isBadRequest();
    }
}
