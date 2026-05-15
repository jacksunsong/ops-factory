/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test coverage for Agent Skill Install Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class AgentSkillInstallServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private AgentConfigService agentConfigService;

    private SkillMarketClient skillMarketClient;

    private AgentSkillInstallService service;

    private Path configDir;

    /**
     * Sets the up.
     *
     * @throws IOException if the operation fails
     */
    @Before
    public void setUp() throws IOException {
        agentConfigService = Mockito.mock(AgentConfigService.class);
        skillMarketClient = Mockito.mock(SkillMarketClient.class);
        GatewayProperties properties = new GatewayProperties();
        properties.getSkillMarket().setMaxPackageSizeMb(10);
        service = new AgentSkillInstallService(agentConfigService, skillMarketClient, properties);

        configDir = temporaryFolder.newFolder("agent-config").toPath();
        when(agentConfigService.findAgent("agent1")).thenReturn(new AgentRegistryEntry("agent1", "Agent One"));
        when(agentConfigService.getAgentConfigDir("agent1")).thenReturn(configDir);
    }

    /**
     * Executes the install copies package into agent skills directory operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installCopiesPackageIntoAgentSkillsDirectory() throws Exception {
        byte[] zip = zipBytes(entry("SKILL.md", "# Log Analysis\n"), entry("scripts/analyze.py", "print('ok')\n"));
        when(skillMarketClient.getSkill("log-analysis")).thenReturn(
            Map.of("name", "Log Analysis", "description", "Analyze logs", "checksum", "sha256:" + sha256(zip)));
        when(skillMarketClient.downloadPackage("log-analysis")).thenReturn(zip);

        Map<String, Object> result = service.install("agent1", "log-analysis");

        Path installed = configDir.resolve("skills/log-analysis");
        assertEquals(true, result.get("success"));
        assertTrue(Files.isRegularFile(installed.resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(installed.resolve("scripts/analyze.py")));
        assertTrue(Files.isRegularFile(installed.resolve(".opsfactory-skill.yaml")));
        String metadata = Files.readString(installed.resolve(".opsfactory-skill.yaml"));
        assertTrue(metadata.contains("source: skill-market"));
        assertTrue(metadata.contains("skillId: log-analysis"));
    }

    /**
     * Executes the install rejects duplicate skill operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installRejectsDuplicateSkill() throws Exception {
        Files.createDirectories(configDir.resolve("skills/log-analysis"));
        byte[] zip = zipBytes(entry("SKILL.md", "# Log Analysis\n"));
        when(skillMarketClient.getSkill("log-analysis")).thenReturn(Map.of("checksum", "sha256:" + sha256(zip)));
        when(skillMarketClient.downloadPackage("log-analysis")).thenReturn(zip);

        assertThrows(SkillInstallConflictException.class, () -> service.install("agent1", "log-analysis"));
    }

    /**
     * Executes the install rejects checksum mismatch operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installRejectsChecksumMismatch() throws Exception {
        byte[] zip = zipBytes(entry("SKILL.md", "# Log Analysis\n"));
        when(skillMarketClient.getSkill("log-analysis")).thenReturn(Map.of("checksum", "sha256:bad"));
        when(skillMarketClient.downloadPackage("log-analysis")).thenReturn(zip);

        assertThrows(IllegalArgumentException.class, () -> service.install("agent1", "log-analysis"));
    }

    /**
     * Executes the install rejects unsafe package path operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void installRejectsUnsafePackagePath() throws Exception {
        byte[] zip = zipBytes(entry("../SKILL.md", "# Unsafe\n"));
        when(skillMarketClient.getSkill("unsafe-skill")).thenReturn(Map.of("checksum", "sha256:" + sha256(zip)));
        when(skillMarketClient.downloadPackage("unsafe-skill")).thenReturn(zip);

        assertThrows(IllegalArgumentException.class, () -> service.install("agent1", "unsafe-skill"));
    }

    /**
     * Executes the install requires existing agent operation.
     */
    @Test
    public void installRequiresExistingAgent() {
        when(agentConfigService.findAgent("missing")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.install("missing", "log-analysis"));
    }

    /**
     * Executes the uninstall deletes installed skill directory operation.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void uninstallDeletesInstalledSkillDirectory() throws Exception {
        Path skillDir = configDir.resolve("skills/log-analysis");
        Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(skillDir.resolve("SKILL.md"), "# Log Analysis\n");
        Files.writeString(skillDir.resolve("scripts/analyze.py"), "print('ok')\n");

        Map<String, Object> result = service.uninstall("agent1", "log-analysis");

        assertEquals(true, result.get("success"));
        assertEquals("log-analysis", result.get("skillId"));
        assertFalse(Files.exists(skillDir));
        verify(agentConfigService).invalidateCache("agent1");
    }

    /**
     * Executes the uninstall rejects missing skill operation.
     */
    @Test
    public void uninstallRejectsMissingSkill() {
        assertThrows(IllegalArgumentException.class, () -> service.uninstall("agent1", "missing-skill"));
    }

    private byte[] zipBytes(ZipTestEntry... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (ZipTestEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.name()));
                zos.write(entry.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private ZipTestEntry entry(String name, String content) {
        return new ZipTestEntry(name, content);
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream in = new DigestInputStream(new ByteArrayInputStream(data), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record ZipTestEntry(String name, String content) {
    }
}
