/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes remote commands on hosts via SSH with command-prefix resolution, variable substitution, and whitelist
 * validation.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class RemoteExecutionService {
    private static final Logger log = LoggerFactory.getLogger(RemoteExecutionService.class);

    private final HostService hostService;

    private final CommandWhitelistService commandWhitelistService;

    private final GatewayProperties properties;

    private final ClusterService clusterService;

    private final ClusterTypeService clusterTypeService;

    /**
     * Creates the remote execution service instance.
     *
     * @param hostService service for resolving host credentials
     * @param commandWhitelistService service for validating commands against the whitelist
     * @param properties gateway configuration properties
     * @param clusterService service for resolving cluster data
     * @param clusterTypeService service for resolving cluster type command prefixes
     */
    public RemoteExecutionService(HostService hostService, CommandWhitelistService commandWhitelistService,
        GatewayProperties properties, ClusterService clusterService, ClusterTypeService clusterTypeService) {
        this.hostService = hostService;
        this.commandWhitelistService = commandWhitelistService;
        this.properties = properties;
        this.clusterService = clusterService;
        this.clusterTypeService = clusterTypeService;
    }

    /**
     * Execute a remote command on the specified host via SSH.
     *
     * @param hostId the host ID to connect to
     * @param command the shell command to execute
     * @param timeoutSeconds maximum execution time in seconds
     * @return result map with hostIp, username, hostName, exitCode, output, error, duration
     */
    public Map<String, Object> execute(String hostId, String command, int timeoutSeconds) {
        // Step 1: Get host with decrypted credential
        Map<String, Object> host;
        try {
            host = hostService.getHostWithCredential(hostId);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("hostIp", "");
            result.put("username", "");
            result.put("hostName", "");
            result.put("exitCode", -1);
            result.put("output", "");
            result.put("error", "Host not found: " + hostId);
            result.put("duration", 0L);
            return result;
        }

        String hostName = (String) host.getOrDefault("name", "");
        String hostname = (String) host.get("ip");
        int port = host.get("port") instanceof Number n ? n.intValue() : 22;
        String username = (String) host.get("username");
        String authType = (String) host.get("authType");
        String credential = (String) host.get("credential");

        // Step 2: Resolve ClusterType from Host -> Cluster -> ClusterType
        String commandPrefix = "";
        Map<String, String> envVars = new LinkedHashMap<>();
        Object clusterIdObj = host.get("clusterId");
        if (clusterIdObj != null) {
            try {
                Map<String, Object> cluster = clusterService.getCluster(clusterIdObj.toString());
                String typeName = cluster != null ? (String) cluster.get("type") : null;
                if (typeName != null) {
                    List<Map<String, Object>> allTypes = clusterTypeService.listClusterTypes();
                    for (Map<String, Object> ct : allTypes) {
                        if (typeName.equals(ct.get("name"))) {
                            Object prefix = ct.get("commandPrefix");
                            if (prefix != null && !prefix.toString().isBlank()) {
                                commandPrefix = prefix.toString().trim();
                            }
                            Object vars = ct.get("envVariables");
                            if (vars instanceof List<?> list) {
                                for (Object item : list) {
                                    if (item instanceof Map<?, ?> m) {
                                        String k = m.get("key") != null ? m.get("key").toString() : null;
                                        String v = m.get("value") != null ? m.get("value").toString() : "";
                                        if (k != null && !k.isEmpty())
                                            envVars.put(k, v);
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                log.debug("Could not resolve cluster type for host {}: {}", hostId, e.getMessage());
            }
        }

        // Step 2a: Replace ${VAR} and $VAR placeholders (sorted longest key first to avoid partial matches)
        String effectiveCommand = command;
        List<String> sortedKeys = new ArrayList<>(envVars.keySet());
        sortedKeys.sort((a, b) -> b.length() - a.length());
        for (String key : sortedKeys) {
            String value = envVars.get(key);
            effectiveCommand = effectiveCommand.replace("${" + key + "}", value);
            // Also replace $VAR when not followed by a valid identifier char
            effectiveCommand =
                effectiveCommand.replaceAll("\\$" + java.util.regex.Pattern.quote(key) + "(?![A-Za-z0-9_])",
                    java.util.regex.Matcher.quoteReplacement(value));
        }

        // Step 2b: Validate resolved command against whitelist
        List<String> rejected = commandWhitelistService.validateCommand(effectiveCommand);
        if (!rejected.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("hostIp", hostname);
            result.put("username", username);
            result.put("hostName", hostName);
            result.put("exitCode", -1);
            result.put("output", "");
            result.put("error",
                "Command rejected: the following commands are not in the whitelist: " + String.join(", ", rejected));
            result.put("rejectedCommands", rejected);
            result.put("duration", 0L);
            return result;
        }

        // Step 2c: Apply command prefix
        // Wrap in bash -c so the prefix applies to the entire command chain (&&, ||, ;)
        if (!commandPrefix.isEmpty()) {
            effectiveCommand = commandPrefix + " bash -c " + singleQuote(effectiveCommand);
        }

        // Step 3: Execute via SSH
        Session session = null;
        ChannelExec channel = null;
        long startTime = System.currentTimeMillis();

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, hostname, port);

            if ("key".equals(authType)) {
                jsch.addIdentity("remote-exec", credential.getBytes(StandardCharsets.UTF_8), null, null);
            } else {
                session.setPassword(credential);
            }

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("bash -l -c " + singleQuote(effectiveCommand));

            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();

            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();

            channel.connect();

            // Read streams with timeout
            long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1000;
            byte[] buf = new byte[4096];

            while (true) {
                if (channel.isClosed()) {
                    // Read any remaining data
                    while (in.available() > 0) {
                        int len = in.read(buf);
                        if (len > 0) {
                            outputBuffer.write(buf, 0, len);
                        }
                    }
                    while (err.available() > 0) {
                        int len = err.read(buf);
                        if (len > 0) {
                            errorBuffer.write(buf, 0, len);
                        }
                    }
                    break;
                }

                while (in.available() > 0) {
                    int len = in.read(buf);
                    if (len > 0) {
                        outputBuffer.write(buf, 0, len);
                    }
                }
                while (err.available() > 0) {
                    int len = err.read(buf);
                    if (len > 0) {
                        errorBuffer.write(buf, 0, len);
                    }
                }

                if (System.currentTimeMillis() > deadline) {
                    log.warn("Command execution timed out after {} seconds for host {}", timeoutSeconds, hostId);
                    channel.disconnect();
                    break;
                }

                Thread.sleep(50);
            }

            int exitCode = channel.getExitStatus();
            long duration = System.currentTimeMillis() - startTime;

            String output = outputBuffer.toString(StandardCharsets.UTF_8);
            String errorOutput = errorBuffer.toString(StandardCharsets.UTF_8);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("hostIp", hostname);
            result.put("username", username);
            result.put("hostName", hostName);
            result.put("command", command);
            result.put("effectiveCommand", effectiveCommand);
            result.put("exitCode", exitCode);
            result.put("output", output);
            result.put("error", errorOutput);
            result.put("duration", duration);
            return result;
        } catch (JSchException | IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SSH execution failed for host {}: {}", hostId, e.getMessage());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("hostIp", hostname);
            result.put("username", username);
            result.put("hostName", hostName);
            result.put("exitCode", -1);
            result.put("output", "");
            result.put("error", "SSH execution failed: " + e.getMessage());
            result.put("duration", duration);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            log.warn("SSH execution interrupted for host {}", hostId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hostId", hostId);
            result.put("hostIp", hostname);
            result.put("username", username);
            result.put("hostName", hostName);
            result.put("exitCode", -1);
            result.put("output", "");
            result.put("error", "SSH execution interrupted");
            result.put("duration", duration);
            return result;
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * Wrap a string in single quotes, escaping any embedded single quotes
     * using the standard POSIX technique: replace ' with '\''.
     */
    private String singleQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
