/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

/**
 * Manages cluster type definitions including mode, command prefix, and environment variables.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class ClusterTypeService {
    private static final Logger log = LoggerFactory.getLogger(ClusterTypeService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayProperties properties;

    private Path clusterTypesDir;

    /**
     * Creates the cluster type service instance.
     */
    public ClusterTypeService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Initializes the cluster types data directory at startup.
     */
    @PostConstruct
    public void init() {
        Path gatewayRoot = properties.getGatewayRootPath();
        this.clusterTypesDir = gatewayRoot.resolve("data").resolve("cluster-types");
        try {
            Files.createDirectories(clusterTypesDir);
        } catch (IOException e) {
            log.error("Failed to create cluster-types directory: {}", clusterTypesDir, e);
        }
        log.info("ClusterTypeService initialized, clusterTypesDir={}", clusterTypesDir);
    }

    // ── CRUD Operations ──────────────────────────────────────────────

    /**
     * Lists all cluster types.
     *
     * @return the result
     */
    public List<Map<String, Object>> listClusterTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        if (!Files.isDirectory(clusterTypesDir)) {
            return types;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(clusterTypesDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> ct = readFile(file);
                if (ct != null) {
                    types.add(ct);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list cluster-types from {}", clusterTypesDir, e);
        }
        return types;
    }

    /**
     * Gets a cluster type by its ID.
     *
     * @param id entity identifier
     * @return a cluster type by its ID
     */
    public Map<String, Object> getClusterType(String id) {
        Path file = clusterTypesDir.resolve(id + ".json");
        Map<String, Object> ct = readFile(file);
        if (ct == null) {
            throw new IllegalArgumentException("Cluster type not found: " + id);
        }
        return ct;
    }

    /**
     * Creates a new cluster type from the provided field map.
     *
     * @param body request body
     * @return the result
     */
    public Map<String, Object> createClusterType(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> ct = new LinkedHashMap<>();
        ct.put("id", id);
        ct.put("name", body.getOrDefault("name", ""));
        ct.put("code", body.getOrDefault("code", ""));
        ct.put("description", body.getOrDefault("description", ""));
        ct.put("color", body.getOrDefault("color", "#10b981"));
        ct.put("knowledge", body.getOrDefault("knowledge", ""));
        ct.put("commandPrefix", body.getOrDefault("commandPrefix", null));
        ct.put("envVariables", body.getOrDefault("envVariables", null));
        ct.put("mode", body.getOrDefault("mode", "peer"));
        ct.put("createdAt", now);
        ct.put("updatedAt", now);

        writeEntityFile(id, ct);
        log.info("Created cluster type: id={}, name={}, code={}", id, ct.get("name"), ct.get("code"));
        return ct;
    }

    /**
     * Updates an existing cluster type with the provided field map.
     *
     * @param id an existing cluster type with the provided field map
     * @param body an existing cluster type with the provided field map
     * @return the result
     */
    public Map<String, Object> updateClusterType(String id, Map<String, Object> body) {
        Path file = clusterTypesDir.resolve(id + ".json");
        Map<String, Object> ct = readFile(file);
        if (ct == null) {
            throw new IllegalArgumentException("Cluster type not found: " + id);
        }

        if (body.containsKey("name")) {
            ct.put("name", body.get("name"));
        }
        if (body.containsKey("code")) {
            ct.put("code", body.get("code"));
        }
        if (body.containsKey("description")) {
            ct.put("description", body.get("description"));
        }
        if (body.containsKey("color")) {
            ct.put("color", body.get("color"));
        }
        if (body.containsKey("knowledge")) {
            ct.put("knowledge", body.get("knowledge"));
        }
        if (body.containsKey("commandPrefix")) {
            ct.put("commandPrefix", body.get("commandPrefix"));
        }
        if (body.containsKey("envVariables")) {
            ct.put("envVariables", body.get("envVariables"));
        }
        if (body.containsKey("mode")) {
            String mode = (String) body.get("mode");
            if (!"peer".equals(mode) && !"primary-backup".equals(mode)) {
                throw new IllegalArgumentException("Invalid mode: " + mode + ". Must be 'peer' or 'primary-backup'.");
            }
            ct.put("mode", mode);
        }

        ct.put("updatedAt", Instant.now().toString());
        writeEntityFile(id, ct);
        log.info("Updated cluster type: id={}", id);
        return ct;
    }

    /**
     * Deletes a cluster type by its ID.
     *
     * @param id entity identifier
     * @return the result
     */
    public boolean deleteClusterType(String id) {
        Path file = clusterTypesDir.resolve(id + ".json");
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted cluster type: id={}", id);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete cluster-type file: {}", file, e);
            return false;
        }
    }

    // ── File I/O Helpers ─────────────────────────────────────────────

    private Map<String, Object> readFile(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            log.error("Failed to read cluster-type file: {}", file, e);
            return null;
        }
    }

    private void writeEntityFile(String id, Map<String, Object> entity) {
        try {
            Files.createDirectories(clusterTypesDir);
            Path file = clusterTypesDir.resolve(id + ".json");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write cluster-type file for id={}", id, e);
            throw new RuntimeException("Failed to save cluster type", e);
        }
    }
}
