/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages host entities with AES-GCM encrypted credentials, SSH connection testing, and cluster-based filtering.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class HostService {
    private static final Logger log = LoggerFactory.getLogger(HostService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";

    private static final int GCM_IV_LENGTH = 12;

    private static final int GCM_TAG_LENGTH = 128;

    private final GatewayProperties properties;

    private Path gatewayRoot;

    private Path hostsDir;

    private SecretKeySpec aesKey;

    private HostRelationService hostRelationService;

    private HostGroupService hostGroupService;

    private ClusterService clusterService;

    private BusinessServiceService businessServiceService;

    private ClusterTypeService clusterTypeService;

    private ClusterRelationService clusterRelationService;

    /**
     * Creates the host service instance.
     *
     * @param properties gateway configuration properties
     */
    public HostService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Sets the host relation service via lazy injection.
     *
     * @param hostRelationService host relation service for managing host relationships
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    public void setHostRelationService(HostRelationService hostRelationService) {
        this.hostRelationService = hostRelationService;
    }

    /**
     * Sets the business service service via lazy injection.
     *
     * @param businessServiceService business service for managing service-to-host associations
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    public void setBusinessServiceService(BusinessServiceService businessServiceService) {
        this.businessServiceService = businessServiceService;
    }

    /**
     * Sets the host group service via lazy injection.
     *
     * @param hostGroupService host group service for managing group-based host queries
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    public void setHostGroupService(HostGroupService hostGroupService) {
        this.hostGroupService = hostGroupService;
    }

    /**
     * Sets the cluster service via lazy injection.
     *
     * @param clusterService cluster service for resolving cluster information
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * Sets the cluster type service via lazy injection.
     *
     * @param clusterTypeService cluster type service for resolving cluster modes
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    public void setClusterTypeService(ClusterTypeService clusterTypeService) {
        this.clusterTypeService = clusterTypeService;
    }

    /**
     * Sets the cluster relation service via lazy injection.
     *
     * @param clusterRelationService cluster relation service for syncing cluster-host relationships
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    public void setClusterRelationService(ClusterRelationService clusterRelationService) {
        this.clusterRelationService = clusterRelationService;
    }

    /**
     * Initializes the hosts data directory and AES encryption key at startup.
     */
    @PostConstruct
    public void init() {
        this.gatewayRoot = properties.getGatewayRootPath();
        this.hostsDir = gatewayRoot.resolve("data").resolve("hosts");

        // Derive AES key from configuration (ensure exactly 32 bytes for AES-256)
        String keyStr = properties.getCredentialEncryptionKey();
        byte[] keyBytes = new byte[32];
        byte[] rawKeyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawKeyBytes, 0, keyBytes, 0, Math.min(rawKeyBytes.length, 32));
        this.aesKey = new SecretKeySpec(keyBytes, "AES");

        try {
            Files.createDirectories(hostsDir);
        } catch (IOException e) {
            log.error("Failed to create hosts directory: {}", hostsDir, e);
        }

        log.info("HostService initialized, hostsDir={}", hostsDir);
    }

    // ── CRUD Operations ──────────────────────────────────────────────

    /**
     * Validate host role against the cluster type mode.
     * - If cluster type mode is "peer", role must be null.
     * - If cluster type mode is "primary-backup", role must be "primary", "backup", or null.
     *
     * @param host host data map to validate
     */
    private void validateHostRole(Map<String, Object> host) {
        Object roleObj = host.get("role");
        String role = roleObj != null ? roleObj.toString() : null;
        if (role == null || role.isEmpty()) {
            // null role is always valid
            return;
        }

        Object clusterIdObj = host.get("clusterId");
        if (clusterIdObj == null || clusterIdObj.toString().isEmpty()) {
            throw new IllegalArgumentException("Host role requires a cluster assignment.");
        }

        String clusterId = clusterIdObj.toString();
        String mode = resolveClusterMode(clusterId);
        if ("peer".equals(mode)) {
            throw new IllegalArgumentException(
                "Host role is not allowed in peer cluster mode. Cluster ID: " + clusterId);
        }
        if ("primary-backup".equals(mode)) {
            if (!"primary".equals(role) && !"backup".equals(role)) {
                throw new IllegalArgumentException(
                    "Invalid host role '" + role + "'. Must be 'primary' or " + "'backup' for primary-backup cluster.");
            }
        }
    }

    private String resolveClusterMode(String clusterId) {
        try {
            Map<String, Object> cluster = clusterService.getCluster(clusterId);
            String typeName = cluster.get("type") != null ? cluster.get("type").toString() : null;
            if (typeName == null || typeName.isEmpty() || clusterTypeService == null) {
                return "peer";
            }
            for (Map<String, Object> ct : clusterTypeService.listClusterTypes()) {
                String ctName = ct.get("name") != null ? ct.get("name").toString() : "";
                if (typeName.equals(ctName)) {
                    Object modeObj = ct.get("mode");
                    return modeObj != null ? modeObj.toString() : "peer";
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug("Unable to resolve cluster mode for missing cluster {}", clusterId);
        }
        return "peer";
    }

    @SuppressWarnings("unchecked")
    private void syncClusterTypeToTags(Map<String, Object> host) {
        if (clusterService == null) {
            return;
        }
        Object clusterIdObj = host.get("clusterId");
        if (clusterIdObj == null || clusterIdObj.toString().isEmpty()) {
            return;
        }

        String clusterId = clusterIdObj.toString();
        String clusterTypeRaw = null;
        try {
            Map<String, Object> cluster = clusterService.getCluster(clusterId);
            if (cluster != null && cluster.get("type") != null) {
                clusterTypeRaw = cluster.get("type").toString();
            }
        } catch (IllegalArgumentException e) {
            log.debug("Skipping missing cluster {} while syncing host tags", clusterId);
        }

        final String clusterType = clusterTypeRaw;

        // Get current tags
        List<Object> tags = new ArrayList<>();
        Object tagsObj = host.get("tags");
        if (tagsObj instanceof List<?>) {
            tags = new ArrayList<>((List<Object>) tagsObj);
        }

        // Remove any existing cluster type tags (exact match)
        if (clusterType != null) {
            final String ct = clusterType;
            tags.removeIf(t -> t.toString().equals(ct));
        }

        // Add current cluster type tag
        if (clusterType != null && !clusterType.isEmpty()) {
            if (!tags.stream().anyMatch(t -> t.toString().equals(clusterType))) {
                tags.add(clusterType);
            }
        }

        host.put("tags", tags);
    }

    /**
     * Lists hosts optionally filtered by tags.
     *
     * @param tags optional tag filter; only hosts containing at least one matching tag are returned
     * @return list of host maps with credentials masked
     */
    public List<Map<String, Object>> listHosts(String[] tags) {
        List<Map<String, Object>> hosts = new ArrayList<>();
        if (!Files.isDirectory(hostsDir)) {
            return hosts;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(hostsDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> host = readHostFile(file);
                if (host != null) {
                    // Mask credential for listing
                    host.put("credential", "***");

                    // Filter by tags if provided
                    if (tags != null && tags.length > 0) {
                        Object hostTagsObj = host.get("tags");
                        if (!(hostTagsObj instanceof List<?> hostTags)) {
                            continue;
                        }
                        boolean matches = false;
                        for (String tag : tags) {
                            if (hostTags.stream().anyMatch(ht -> String.valueOf(ht).equalsIgnoreCase(tag))) {
                                matches = true;
                                break;
                            }
                        }
                        if (!matches) {
                            continue;
                        }
                    }
                    hosts.add(host);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list hosts from {}", hostsDir, e);
        }
        return hosts;
    }

    /**
     * Gets a host by its ID with the credential masked.
     *
     * @param id host identifier
     * @return host data map with credential masked
     */
    public Map<String, Object> getHost(String id) {
        Path file = hostsDir.resolve(id + ".json");
        Map<String, Object> host = readHostFile(file);
        if (host == null) {
            throw new IllegalArgumentException("Host not found: " + id);
        }
        host.put("credential", "***");
        return host;
    }

    /**
     * Gets a host by its ID with the decrypted credential for internal use.
     *
     * @param id host identifier
     * @return host data map with decrypted credential for internal use
     */
    public Map<String, Object> getHostWithCredential(String id) {
        Path file = hostsDir.resolve(id + ".json");
        Map<String, Object> host = readHostFile(file);
        if (host == null) {
            log.warn("Host not found when loading with credential id={}", id);
            throw new IllegalArgumentException("Host not found: " + id);
        }
        // Decrypt credential for internal use
        Object credentialObj = host.get("credential");
        if (credentialObj instanceof String credentialValue && !credentialValue.isEmpty()) {
            try {
                host.put("credential", decrypt(credentialValue));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.warn("Failed to decrypt credential for host {}: {}", id, e.getMessage());
                // Leave the encrypted value as-is
            }
        }
        return host;
    }

    /**
     * Creates a new host from the provided field map with encrypted credential.
     *
     * @param body request body containing host fields
     * @return the newly created host map with credential masked
     */
    public Map<String, Object> createHost(Map<String, Object> body) {
        String name = body.getOrDefault("name", "").toString();
        for (Map<String, Object> existing : listHosts(null)) {
            if (name.equalsIgnoreCase(String.valueOf(existing.get("name")))) {
                throw new IllegalArgumentException("Host name already exists: " + name);
            }
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> host = new LinkedHashMap<>();
        host.put("id", id);
        host.put("name", body.getOrDefault("name", ""));
        host.put("hostname", body.getOrDefault("hostname", null));
        host.put("ip", body.getOrDefault("ip", ""));
        host.put("businessIp", body.getOrDefault("businessIp", null));
        host.put("port", body.getOrDefault("port", 22));
        host.put("os", body.getOrDefault("os", null));
        host.put("location", body.getOrDefault("location", null));
        host.put("username", body.getOrDefault("username", ""));
        host.put("authType", body.getOrDefault("authType", "password"));
        host.put("business", body.getOrDefault("business", null));
        host.put("clusterId", body.getOrDefault("clusterId", null));
        host.put("purpose", body.getOrDefault("purpose", null));
        host.put("tags", body.getOrDefault("tags", List.of()));
        host.put("description", body.getOrDefault("description", ""));
        host.put("customAttributes", body.getOrDefault("customAttributes", List.of()));
        host.put("role", body.getOrDefault("role", null));
        host.put("createdAt", now);
        host.put("updatedAt", now);

        // Encrypt credential
        Object credentialObj = body.get("credential");
        String rawCredential = credentialObj != null ? credentialObj.toString() : "";
        try {
            host.put("credential", encrypt(rawCredential));
        } catch (GeneralSecurityException e) {
            log.error("Failed to encrypt credential for new host {}", id, e);
            throw new RuntimeException("Failed to encrypt credential", e);
        }

        syncClusterTypeToTags(host);
        validateHostRole(host);
        writeHostFile(id, host);
        log.info("Created host: id={}, name={}", id, host.get("name"));

        // Sync cluster→host membership relation
        if (clusterRelationService != null) {
            String cid = host.get("clusterId") != null ? host.get("clusterId").toString() : null;
            clusterRelationService.syncHostClusterRelation(id, cid);
        }

        // Return with masked credential
        Map<String, Object> result = new LinkedHashMap<>(host);
        result.put("credential", "***");
        return result;
    }

    /**
     * Updates an existing host with the provided field map, re-encrypting the credential if changed.
     *
     * @param id host identifier
     * @param body request body containing updated host fields
     * @return the updated host map with credential masked
     */
    public Map<String, Object> updateHost(String id, Map<String, Object> body) {
        Path file = hostsDir.resolve(id + ".json");
        Map<String, Object> host = readHostFile(file);
        if (host == null) {
            throw new IllegalArgumentException("Host not found: " + id);
        }

        // Check name uniqueness if name is being updated
        if (body.containsKey("name")) {
            String newName = String.valueOf(body.get("name"));
            for (Map<String, Object> existing : listHosts(null)) {
                if (!id.equals(existing.get("id")) && newName.equalsIgnoreCase(String.valueOf(existing.get("name")))) {
                    throw new IllegalArgumentException("Host name already exists: " + newName);
                }
            }
        }

        // Update mutable fields
        if (body.containsKey("name")) {
            host.put("name", body.get("name"));
        }
        if (body.containsKey("hostname")) {
            host.put("hostname", body.get("hostname"));
        }
        if (body.containsKey("ip")) {
            host.put("ip", body.get("ip"));
        }
        if (body.containsKey("port")) {
            host.put("port", body.get("port"));
        }
        if (body.containsKey("os")) {
            host.put("os", body.get("os"));
        }
        if (body.containsKey("location")) {
            host.put("location", body.get("location"));
        }
        if (body.containsKey("username")) {
            host.put("username", body.get("username"));
        }
        if (body.containsKey("authType")) {
            host.put("authType", body.get("authType"));
        }
        if (body.containsKey("business")) {
            host.put("business", body.get("business"));
        }
        if (body.containsKey("clusterId")) {
            host.put("clusterId", body.get("clusterId"));
        }
        if (body.containsKey("purpose")) {
            host.put("purpose", body.get("purpose"));
        }
        if (body.containsKey("tags")) {
            host.put("tags", body.get("tags"));
        }
        if (body.containsKey("description")) {
            host.put("description", body.get("description"));
        }
        if (body.containsKey("customAttributes")) {
            host.put("customAttributes", body.get("customAttributes"));
        }
        if (body.containsKey("businessIp")) {
            host.put("businessIp", body.get("businessIp"));
        }
        if (body.containsKey("role")) {
            host.put("role", body.get("role"));
        }
        if (body.containsKey("credential")) {
            Object credentialObj = body.get("credential");
            String rawCredential = credentialObj != null ? credentialObj.toString() : "";
            // Skip update when the frontend sends back the masked sentinel value
            if (!"***".equals(rawCredential)) {
                try {
                    host.put("credential", encrypt(rawCredential));
                } catch (GeneralSecurityException e) {
                    log.error("Failed to encrypt credential for host {}", id, e);
                    throw new RuntimeException("Failed to encrypt credential", e);
                }
            }
        }

        host.put("updatedAt", Instant.now().toString());
        syncClusterTypeToTags(host);
        validateHostRole(host);
        writeHostFile(id, host);
        log.info("Updated host: id={}", id);

        // Sync cluster→host membership relation if clusterId changed
        if (body.containsKey("clusterId") && clusterRelationService != null) {
            String cid = host.get("clusterId") != null ? host.get("clusterId").toString() : null;
            clusterRelationService.syncHostClusterRelation(id, cid);
        }

        // Return with masked credential
        Map<String, Object> result = new LinkedHashMap<>(host);
        result.put("credential", "***");
        return result;
    }

    /**
     * Deletes a host by ID with cascade deletion of related relations.
     *
     * @param id host identifier
     * @return true if the host was deleted, false if not found
     */
    public boolean deleteHost(String id) {
        // Cascade delete relations first
        if (hostRelationService != null) {
            hostRelationService.deleteRelationsByHost(id);
        }

        // Delete cluster→host membership relation
        if (clusterRelationService != null) {
            clusterRelationService.deleteConstituteRelationByHost(id);
        }

        // Remove host from all business services' hostIds
        if (businessServiceService != null) {
            businessServiceService.removeHostFromAllBusinessServices(id);
        }

        Path file = hostsDir.resolve(id + ".json");
        try {
            if (Files.exists(file)) {
                Files.delete(file);
                log.info("Deleted host: id={}", id);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete host file: {}", file, e);
            return false;
        }
    }

    /**
     * List hosts filtered by clusterId.
     *
     * @param clusterId cluster identifier to filter hosts by
     * @return list of host maps belonging to the specified cluster, with credentials masked
     */
    public List<Map<String, Object>> listHostsByCluster(String clusterId) {
        List<Map<String, Object>> hosts = new ArrayList<>();
        if (!Files.isDirectory(hostsDir)) {
            return hosts;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(hostsDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Map<String, Object> host = readHostFile(file);
                if (host != null) {
                    Object hostClusterId = host.get("clusterId");
                    if (clusterId.equals(hostClusterId)) {
                        host.put("credential", "***");
                        hosts.add(host);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to list hosts from {}", hostsDir, e);
        }
        return hosts;
    }

    /**
     * List hosts filtered by groupId (via cluster lookup).
     * Recursively resolves sub-groups so a top-level group finds all descendant hosts.
     *
     * @param groupId group identifier to look up hosts for
     * @param clusterService cluster service for resolving clusters within groups
     * @return list of host maps belonging to all clusters under the group and its sub-groups
     */
    public List<Map<String, Object>> listHostsByGroup(String groupId, ClusterService clusterService) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectHostsByGroup(groupId, clusterService, new LinkedHashSet<>(), result);
        return result;
    }

    private void collectHostsByGroup(String groupId, ClusterService clusterService, Set<String> visited,
        List<Map<String, Object>> result) {
        if (!visited.add(groupId)) {
            // avoid cycles
            return;
        }
        // Direct clusters under this group
        List<Map<String, Object>> clusters = clusterService.listClusters(groupId, null);
        for (Map<String, Object> cluster : clusters) {
            String clusterId = (String) cluster.get("id");
            result.addAll(listHostsByCluster(clusterId));
        }
        // Recurse into sub-groups
        if (hostGroupService != null) {
            for (Map<String, Object> sub : hostGroupService.listGroups()) {
                Object parentId = sub.get("parentId");
                if (parentId != null && groupId.equals(parentId.toString())) {
                    collectHostsByGroup((String) sub.get("id"), clusterService, visited, result);
                }
            }
        }
    }

    /**
     * Returns all unique tags across all hosts.
     *
     * @return list of all unique tag strings across all hosts
     */
    public List<String> getAllTags() {
        LinkedHashSet<String> allTags = new LinkedHashSet<>();
        List<Map<String, Object>> hosts = listHosts(null);
        for (Map<String, Object> host : hosts) {
            Object tagsObj = host.get("tags");
            if (tagsObj instanceof List<?> tags) {
                for (Object tag : tags) {
                    if (tag != null) {
                        allTags.add(tag.toString());
                    }
                }
            }
        }
        return new ArrayList<>(allTags);
    }

    /**
     * Find a host by IP address, checking both the ip (SSH) and businessIp fields.
     * Returns the first matching host map (with masked credential) or null.
     *
     * @param ip IP address to search for
     * @return the first matching host map with credential masked, or null if none found
     */
    public Map<String, Object> findByIp(String ip) {
        List<Map<String, Object>> hosts = listHosts(new String[0]);
        for (Map<String, Object> host : hosts) {
            if (ip.equals(host.get("ip")) || ip.equals(host.get("businessIp"))) {
                return host;
            }
        }
        return null;
    }

    /**
     * Tests the SSH connection to a host by its ID and returns connection status and latency.
     *
     * @param id host identifier to test connectivity for
     * @return connection result map containing success status, message, and latency
     */
    public Map<String, Object> testConnection(String id) {
        Map<String, Object> host;
        try {
            host = getHostWithCredential(id);
        } catch (IllegalArgumentException e) {
            log.warn("SSH connection test skipped hostId={} reason=host-not-found", id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("message", "Host not found: " + id);
            return result;
        }

        String hostname = (String) host.get("ip");
        int port = host.get("port") instanceof Number n ? n.intValue() : 22;
        String username = (String) host.get("username");
        String authType = (String) host.get("authType");
        String credential = (String) host.get("credential");

        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        log.info("SSH connection test started hostId={} ip={} port={} authType={}", id, hostname, port, authType);

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, hostname, port);

            if ("key".equals(authType)) {
                jsch.addIdentity("test-connection", credential.getBytes(StandardCharsets.UTF_8), null, null);
            } else {
                session.setPassword(credential);
            }

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            long latency = System.currentTimeMillis() - start;
            session.disconnect();

            result.put("success", true);
            result.put("message", "Connection successful");
            result.put("latency", latency + "ms");
            log.info("SSH connection test succeeded hostId={} ip={} port={} latencyMs={}", id, hostname, port, latency);
        } catch (JSchException | RuntimeException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("SSH connection test failed hostId={} ip={} port={} latencyMs={} error={}", id, hostname, port,
                latency, e.getMessage());
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
            result.put("latency", latency + "ms");
        }

        return result;
    }

    // ── File I/O Helpers ─────────────────────────────────────────────

    private Map<String, Object> readHostFile(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            log.error("Failed to read host file: {}", file, e);
            return null;
        }
    }

    private void writeHostFile(String id, Map<String, Object> host) {
        try {
            Files.createDirectories(hostsDir);
            Path file = hostsDir.resolve(id + ".json");
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(host);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write host file for id={}", id, e);
            throw new RuntimeException("Failed to save host", e);
        }
    }

    // ── AES-GCM Encryption ───────────────────────────────────────────

    /**
     * Encrypts plaintext using AES-GCM and returns a Base64-encoded ciphertext with prepended IV.
     *
     * @param plaintext the plain text to encrypt
     * @return Base64-encoded string containing IV followed by ciphertext
     * @throws GeneralSecurityException if encryption fails
     */
    private String encrypt(String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to ciphertext
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts a Base64-encoded AES-GCM ciphertext (IV prepended) back to plaintext.
     *
     * @param encryptedBase64 Base64-encoded string containing IV followed by ciphertext
     * @return the decrypted plain text
     * @throws GeneralSecurityException if decryption fails
     */
    private String decrypt(String encryptedBase64) throws GeneralSecurityException {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
