/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test coverage for Gateway Properties.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class GatewayPropertiesTest {

    /**
     * Tests defaults.
     */
    @Test
    public void testDefaults() {
        GatewayProperties props = new GatewayProperties();

        assertEquals("test", props.getSecretKey());
        assertEquals("http://127.0.0.1:5173", props.getCorsOrigin());
        assertEquals("goosed", props.getGoosedBin());
    }

    /**
     * Tests path defaults.
     */
    @Test
    public void testPathDefaults() {
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        assertEquals("..", paths.getProjectRoot());
        assertEquals("agents", paths.getAgentsDir());
        assertEquals("users", paths.getUsersDir());
    }

    /**
     * Tests idle defaults.
     */
    @Test
    public void testIdleDefaults() {
        GatewayProperties.Idle idle = new GatewayProperties.Idle();
        assertEquals(15, idle.getTimeoutMinutes());
        assertEquals(60000L, idle.getCheckIntervalMs());
    }

    /**
     * Tests upload defaults.
     */
    @Test
    public void testUploadDefaults() {
        GatewayProperties.Upload upload = new GatewayProperties.Upload();
        assertEquals(50, upload.getMaxFileSizeMb());
        assertEquals(20, upload.getMaxImageSizeMb());
    }

    /**
     * Tests files scan root defaults.
     */
    @Test
    public void testFilesScanRootDefaults() {
        GatewayProperties.FileBrowser files = new GatewayProperties.FileBrowser();
        assertEquals(2, files.getScanRoots().size());
        assertEquals("workingDir", files.getScanRoots().get(0).getId());
        assertEquals("${userAgentDir}", files.getScanRoots().get(0).getPath());
        assertFalse(files.getScanRoots().get(0).isRecursive());
        assertTrue(files.getScanRoots().get(0).getExcludeDirs().isEmpty());
        assertEquals(6, files.getScanRoots().get(0).getMaxDepth());
        assertEquals(1000, files.getScanRoots().get(0).getMaxFiles());
        assertEquals(2000L, files.getScanRoots().get(0).getScanTimeoutMs());
        assertEquals("output", files.getScanRoots().get(1).getId());
        assertEquals("${userAgentDir}/output", files.getScanRoots().get(1).getPath());
        assertFalse(files.getScanRoots().get(1).isRecursive());
    }

    /**
     * Tests skill market defaults.
     */
    @Test
    public void testSkillMarketDefaults() {
        GatewayProperties.SkillMarket skillMarket = new GatewayProperties.SkillMarket();
        assertEquals("http://127.0.0.1:8095", skillMarket.getBaseUrl());
        assertEquals(10000, skillMarket.getRequestTimeoutMs());
        assertEquals(200, skillMarket.getMaxPackageSizeMb());
    }

    /**
     * Tests office preview defaults.
     */
    @Test
    public void testOfficePreviewDefaults() {
        GatewayProperties.OfficePreview op = new GatewayProperties.OfficePreview();
        assertFalse(op.isEnabled());
        assertEquals("", op.getOnlyofficeUrl());
    }

    /**
     * Tests logging defaults.
     */
    @Test
    public void testLoggingDefaults() {
        GatewayProperties.Logging logging = new GatewayProperties.Logging();
        assertTrue(logging.isAccessLogEnabled());
        assertFalse(logging.isIncludeUpstreamErrorBody());
        assertFalse(logging.isIncludeSseChunkPreview());
        assertEquals(160, logging.getSseChunkPreviewMaxChars());
    }

    /**
     * Tests setters.
     */
    @Test
    public void testSetters() {
        GatewayProperties props = new GatewayProperties();
        props.setSecretKey("new-key");
        props.setCorsOrigin("http://localhost:8080");
        props.setGoosedBin("/usr/bin/goosed");

        assertEquals("new-key", props.getSecretKey());
        assertEquals("http://localhost:8080", props.getCorsOrigin());
        assertEquals("/usr/bin/goosed", props.getGoosedBin());
    }

    /**
     * Tests logging setters.
     */
    @Test
    public void testLoggingSetters() {
        GatewayProperties.Logging logging = new GatewayProperties.Logging();
        logging.setAccessLogEnabled(false);
        logging.setIncludeUpstreamErrorBody(true);
        logging.setIncludeSseChunkPreview(true);
        logging.setSseChunkPreviewMaxChars(80);

        assertFalse(logging.isAccessLogEnabled());
        assertTrue(logging.isIncludeUpstreamErrorBody());
        assertTrue(logging.isIncludeSseChunkPreview());
        assertEquals(80, logging.getSseChunkPreviewMaxChars());
    }

    /**
     * Tests langfuse defaults.
     */
    @Test
    public void testLangfuseDefaults() {
        GatewayProperties.Langfuse langfuse = new GatewayProperties.Langfuse();
        assertEquals("", langfuse.getHost());
        assertEquals("", langfuse.getPublicKey());
        assertEquals("", langfuse.getSecretKey());
    }

    /**
     * Tests goose tls default true.
     */
    @Test
    public void testGooseTlsDefaultTrue() {
        GatewayProperties props = new GatewayProperties();
        assertTrue(props.isGooseTls());
    }

    /**
     * Tests goose tls set true.
     */
    @Test
    public void testGooseTlsSetTrue() {
        GatewayProperties props = new GatewayProperties();
        props.setGooseTls(true);
        assertTrue(props.isGooseTls());
    }

    /**
     * Tests goose scheme https when tls true.
     */
    @Test
    public void testGooseSchemeHttpsWhenTlsTrue() {
        GatewayProperties props = new GatewayProperties();
        props.setGooseTls(true);
        assertEquals("https", props.gooseScheme());
    }

    /**
     * Tests goose scheme http when tls false.
     */
    @Test
    public void testGooseSchemeHttpWhenTlsFalse() {
        GatewayProperties props = new GatewayProperties();
        props.setGooseTls(false);
        assertEquals("http", props.gooseScheme());
    }

    /**
     * Tests resolves paths relative to gateway config path.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testResolvesPathsRelativeToGatewayConfigPath() throws IOException {
        Path tempRoot = Files.createTempDirectory("gateway-props");
        Path gatewayRoot = tempRoot.resolve("gateway");
        Files.createDirectories(gatewayRoot);
        Files.writeString(gatewayRoot.resolve("config.yaml"), "server:\n  port: 3000\n");

        String previous = System.getProperty("GATEWAY_CONFIG_PATH");
        System.setProperty("GATEWAY_CONFIG_PATH", gatewayRoot.resolve("config.yaml").toString());
        try {
            GatewayProperties props = new GatewayProperties();
            GatewayProperties.Paths paths = new GatewayProperties.Paths();
            paths.setProjectRoot("..");
            props.setPaths(paths);

            assertEquals(tempRoot.normalize(), props.getProjectRootPath());
            assertEquals(gatewayRoot.normalize(), props.getGatewayRootPath());
        } finally {
            if (previous == null) {
                System.clearProperty("GATEWAY_CONFIG_PATH");
            } else {
                System.setProperty("GATEWAY_CONFIG_PATH", previous);
            }
        }
    }
}
