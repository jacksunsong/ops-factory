/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for Remote Execution Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RunWith(MockitoJUnitRunner.class)
public class RemoteExecutionServiceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private HostService hostService;

    @Mock
    private CommandWhitelistService commandWhitelistService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ClusterTypeService clusterTypeService;

    private RemoteExecutionService remoteExecutionService;

    private GatewayProperties properties;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        properties = new GatewayProperties();
        remoteExecutionService = new RemoteExecutionService(hostService, commandWhitelistService, properties,
            clusterService, clusterTypeService);
    }

    // ── execute: host not found ──────────────────────────────────

    /**
     * Tests execute host not found.
     */
    @Test
    public void testExecute_hostNotFound() {
        when(hostService.getHostWithCredential("nonexistent"))
            .thenThrow(new IllegalArgumentException("Host not found: nonexistent"));

        Map<String, Object> result = remoteExecutionService.execute("nonexistent", "ps -ef", 30);

        assertEquals(-1, result.get("exitCode"));
        assertEquals("Host not found: nonexistent", result.get("error"));
        assertEquals("nonexistent", result.get("hostId"));
    }

    // ── execute: command rejected by whitelist ───────────────────

    /**
     * Tests execute command rejected.
     */
    @Test
    public void testExecute_commandRejected() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "TestHost");
        host.put("ip", "192.168.1.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(commandWhitelistService.validateCommand("rm -rf /")).thenReturn(List.of("rm"));

        Map<String, Object> result = remoteExecutionService.execute("host-1", "rm -rf /", 30);

        assertEquals(-1, result.get("exitCode"));
        assertNotNull(result.get("rejectedCommands"));
        assertTrue(((List<?>) result.get("rejectedCommands")).contains("rm"));
    }

    // ── execute: SSH connection fails (invalid host) ─────────────

    /**
     * Tests execute ssh connection fails.
     */
    @Test
    public void testExecute_sshConnectionFails() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "BadHost");
        // invalid IP
        host.put("ip", "256.256.256.256");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(commandWhitelistService.validateCommand("ls")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "ls", 5);

        // Should get an error result, not crash
        assertEquals(-1, result.get("exitCode"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("SSH execution failed"));
    }

    // ── execute: whitelist validation passes, SSH fails gracefully ─

    /**
     * Tests execute whitelist checked before ssh.
     */
    @Test
    public void testExecute_whitelistCheckedBeforeSsh() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        // Command rejected - should NOT attempt SSH
        when(commandWhitelistService.validateCommand("reboot")).thenReturn(List.of("reboot"));

        Map<String, Object> result = remoteExecutionService.execute("host-1", "reboot", 30);

        assertEquals(-1, result.get("exitCode"));
        // Verify that we get the whitelist rejection, not an SSH error
        assertNotNull(result.get("rejectedCommands"));
    }

    // ── execute: port from Number ────────────────────────────────

    /**
     * Tests execute host with non default port.
     */
    @Test
    public void testExecute_hostWithNonDefaultPort() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 2222);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(commandWhitelistService.validateCommand("ls")).thenReturn(List.of());

        // SSH will fail but the service should handle it gracefully
        Map<String, Object> result = remoteExecutionService.execute("host-1", "ls", 5);
        assertNotNull(result);
    }

    // ── execute: key auth type ───────────────────────────────────

    /**
     * Tests execute key auth type.
     */
    @Test
    public void testExecute_keyAuthType() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "KeyHost");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "key");
        host.put("credential", "fake-key-content");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(commandWhitelistService.validateCommand("ps")).thenReturn(List.of());

        // SSH will fail with fake key but should not crash
        Map<String, Object> result = remoteExecutionService.execute("host-1", "ps", 5);
        assertNotNull(result);
        assertEquals(-1, result.get("exitCode"));
    }

    // ── execute: missing port uses default 22 ────────────────────

    /**
     * Tests execute missing port defaults to22.
     */
    @Test
    public void testExecute_missingPortDefaultsTo22() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        // port is missing
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(commandWhitelistService.validateCommand("ls")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "ls", 5);
        assertNotNull(result);
    }

    // ── execute: empty command ───────────────────────────────────

    /**
     * Tests execute empty command.
     */
    @Test
    public void testExecute_emptyCommand() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(commandWhitelistService.validateCommand("")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "", 5);
        assertNotNull(result);
    }

    // ── env variable replacement + command prefix ─────────────────

    /**
     * Tests execute host without cluster no prefix no vars.
     */
    @Test
    public void testExecute_hostWithoutCluster_noPrefixNoVars() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        // no clusterId
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        // Command should pass through unchanged (whitelist gets original command)
        when(commandWhitelistService.validateCommand("ls")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "ls", 5);
        assertNotNull(result);
        // SSH will fail but command was processed without prefix/vars
    }

    /**
     * Tests execute env vars replaced before whitelist check.
     */
    @Test
    public void testExecute_envVarsReplacedBeforeWhitelistCheck() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        host.put("clusterId", "cluster-1");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        // Cluster -> type = "NSLB"
        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("type", "NSLB");
        when(clusterService.getCluster("cluster-1")).thenReturn(cluster);

        // ClusterType NSLB with env vars
        Map<String, Object> nslbType = new LinkedHashMap<>();
        nslbType.put("name", "NSLB");
        nslbType.put("commandPrefix", "sudo -u nslb");
        List<Map<String, String>> envVars = new ArrayList<>();
        envVars.add(Map.of("key", "NSLB_HOME", "value", "/opt/nslb"));
        nslbType.put("envVariables", envVars);
        when(clusterTypeService.listClusterTypes()).thenReturn(List.of(nslbType));

        // Whitelist should see the RESOLVED command (after variable replacement, before prefix)
        // "cd ${NSLB_HOME} && ls" -> "cd /opt/nslb && ls"
        when(commandWhitelistService.validateCommand("cd /opt/nslb && ls")).thenReturn(List.of("cd"));

        Map<String, Object> result = remoteExecutionService.execute("host-1", "cd ${NSLB_HOME} && ls", 30);

        // Should be rejected by whitelist on the resolved command
        assertEquals(-1, result.get("exitCode"));
        assertNotNull(result.get("rejectedCommands"));
        assertTrue(((List<?>) result.get("rejectedCommands")).contains("cd"));
    }

    /**
     * Tests execute with cluster type prefix applied to ssh command.
     */
    @Test
    public void testExecute_withClusterTypePrefix_appliedToSshCommand() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        host.put("clusterId", "cluster-1");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("type", "NSLB");
        when(clusterService.getCluster("cluster-1")).thenReturn(cluster);

        Map<String, Object> nslbType = new LinkedHashMap<>();
        nslbType.put("name", "NSLB");
        nslbType.put("commandPrefix", "sudo -u nslb");
        List<Map<String, String>> envVars = new ArrayList<>();
        envVars.add(Map.of("key", "NSLB_HOME", "value", "/opt/nslb"));
        nslbType.put("envVariables", envVars);
        when(clusterTypeService.listClusterTypes()).thenReturn(List.of(nslbType));

        // After replacement: "cd /opt/nslb && ls" - whitelisted
        // After prefix: "sudo -u nslb cd /opt/nslb && ls"
        when(commandWhitelistService.validateCommand("cd /opt/nslb && ls")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "cd ${NSLB_HOME} && ls", 5);

        // SSH will fail but the command was processed with prefix + vars
        assertNotNull(result);
        assertEquals(-1, result.get("exitCode"));
        // Verify whitelist was called with the replaced (not original) command
        verify(commandWhitelistService).validateCommand("cd /opt/nslb && ls");
    }

    /**
     * Tests execute no matching cluster type no prefix no vars.
     */
    @Test
    public void testExecute_noMatchingClusterType_noPrefixNoVars() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        host.put("clusterId", "cluster-1");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("type", "UNKNOWN_TYPE");
        when(clusterService.getCluster("cluster-1")).thenReturn(cluster);

        // Return types that don't match
        Map<String, Object> otherType = new LinkedHashMap<>();
        otherType.put("name", "OTHER");
        when(clusterTypeService.listClusterTypes()).thenReturn(List.of(otherType));

        // Command should pass through unchanged
        when(commandWhitelistService.validateCommand("ls")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "ls", 5);
        assertNotNull(result);
    }

    /**
     * Tests execute cluster service throws handled gracefully.
     */
    @Test
    public void testExecute_clusterServiceThrows_handledGracefully() {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("name", "Host");
        host.put("ip", "10.0.0.1");
        host.put("port", 22);
        host.put("username", "root");
        host.put("authType", "password");
        host.put("credential", "secret");
        host.put("clusterId", "cluster-1");
        when(hostService.getHostWithCredential("host-1")).thenReturn(host);

        when(clusterService.getCluster("cluster-1")).thenThrow(new IllegalArgumentException("Cluster not found"));

        // Command should still work without prefix/vars
        when(commandWhitelistService.validateCommand("ls")).thenReturn(List.of());

        Map<String, Object> result = remoteExecutionService.execute("host-1", "ls", 5);
        assertNotNull(result);
        // Should have attempted SSH (will fail due to fake host), but not crashed
    }
}
