/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static java.util.Map.entry;

import com.huawei.opsfactory.gateway.common.util.PathSanitizer;
import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides file browsing, upload validation, MIME-type resolution, and file-capsule persistence for the gateway.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class FileService {
    private final GatewayProperties gatewayProperties;

    /**
     * Creates the file service instance.
     *
     * @param gatewayProperties gateway configuration properties
     */
    public FileService(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    private static final Map<String,
        String> MIME_TYPES = Map.ofEntries(entry("json", "application/json"), entry("pdf", "application/pdf"),
            entry("xml", "application/xml"), entry("zip", "application/zip"), entry("csv", "text/csv"),
            entry("txt", "text/plain"), entry("md", "text/markdown"), entry("html", "text/html"),
            entry("css", "text/css"), entry("js", "text/javascript"), entry("ts", "text/typescript"),
            entry("java", "text/x-java-source"), entry("py", "text/x-python"), entry("yaml", "text/yaml"),
            entry("yml", "text/yaml"), entry("png", "image/png"), entry("jpg", "image/jpeg"),
            entry("jpeg", "image/jpeg"), entry("gif", "image/gif"), entry("svg", "image/svg+xml"),
            entry("webp", "image/webp"), entry("bmp", "image/bmp"), entry("doc", "application/msword"),
            entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            entry("xls", "application/vnd.ms-excel"),
            entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            entry("ppt", "application/vnd.ms-powerpoint"),
            entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private static final Set<String> SKIP_DIRS = Set.of("data", "state", "config", "node_modules", ".goose");

    private static final Set<String> SKIP_FILES = Set.of(".DS_Store", "AGENTS.md", ".gitkeep");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "json", "csv", "xml", "yaml", "yml",
        "html", "css", "js", "ts", "java", "py", "go", "rs", "rb", "sh", "pdf", "doc", "docx", "xls", "xlsx", "ppt",
        "pptx", "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "zip", "gz", "tar", "log");

    private static final Set<String> BLOCKED_EXTENSIONS =
        Set.of("exe", "bat", "cmd", "com", "msi", "dll", "sys", "scr", "vbs", "vbe", "wsf", "wsh", "ps1");

    private static final Set<String> EDITABLE_TEXT_EXTENSIONS = Set.of("txt", "log", "ini", "conf", "js", "ts", "jsx",
        "tsx", "mjs", "cjs", "py", "sh", "bash", "zsh", "yaml", "yml", "json", "toml", "css", "scss", "less", "xml",
        "sql", "graphql", "go", "rs", "java", "c", "cpp", "h", "hpp", "rb", "php", "swift", "kt", "scala", "csv", "tsv",
        "env", "gitignore", "dockerignore", "editorconfig", "prettierrc", "eslintrc", "babelrc", "dockerfile",
        "makefile", "vue", "svelte", "md", "markdown", "html", "htm");

    /**
     * List files for the Files module from configured scan roots.
     *
     * @param userAgentDir user agent working directory to scan from
     * @return list of file metadata maps
     */
    public List<Map<String, Object>> listFiles(Path userAgentDir) {
        try {
            List<Map<String, Object>> files = new ArrayList<>();
            Set<String> allowedExtensions = fileCapsuleAllowedExtensions();
            for (FileScanRoot root : fileScanRoots(userAgentDir)) {
                if (root.recursive()) {
                    long deadlineNanos = scanDeadlineNanos(root.scanTimeoutMs());
                    listFilesRecursive(root, root.path(), files, allowedExtensions, 0, deadlineNanos, files.size());
                } else {
                    listRootFiles(root, files, allowedExtensions);
                }
            }
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list files in " + userAgentDir, e);
        }
    }

    /**
     * List only top-level files under a directory, excluding subdirectories.
     *
     * @param dir directory to list top-level files from
     * @return list of file metadata maps for top-level files only
     */
    public List<Map<String, Object>> listTopLevelFiles(Path dir) {
        try {
            List<Map<String, Object>> files = new ArrayList<>();
            listRootFiles(new FileScanRoot("workingDir", dir, false, new HashSet<>(SKIP_DIRS), 6, 1000, 2000), files,
                fileCapsuleAllowedExtensions());
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list top-level files in " + dir, e);
        }
    }

    private void listRootFiles(FileScanRoot root, List<Map<String, Object>> files, Set<String> allowedExtensions)
        throws IOException {
        Path dir = root.path();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && !SKIP_FILES.contains(entry.getFileName().toString())) {
                    addFileEntry(root, entry, files, allowedExtensions);
                }
            }
        }
    }

    /**
     * List "user-facing" output files for chat file capsules:
     * - Same scan roots as the Files module
     *
     * @param dir user agent working directory to scan from
     * @return list of file metadata maps relevant to file capsules
     */
    public List<Map<String, Object>> listCapsuleRelevantFiles(Path dir) {
        return listFiles(dir);
    }

    private void listFilesRecursive(FileScanRoot root, Path current, List<Map<String, Object>> files,
        Set<String> allowedExtensions, int depth, long deadlineNanos, int rootStartCount) throws IOException {
        if (!Files.isDirectory(current)) {
            return;
        }
        if (isScanLimitReached(root, files, depth, deadlineNanos, rootStartCount)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                if (isScanLimitReached(root, files, depth, deadlineNanos, rootStartCount)) {
                    return;
                }
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    if (depth < root.maxDepth() && !root.excludeDirs().contains(name) && !name.startsWith(".")) {
                        listFilesRecursive(root, entry, files, allowedExtensions, depth + 1, deadlineNanos,
                            rootStartCount);
                    }
                } else {
                    if (!SKIP_FILES.contains(name)) {
                        addFileEntry(root, entry, files, allowedExtensions);
                    }
                }
            }
        }
    }

    private boolean isScanLimitReached(FileScanRoot root, List<Map<String, Object>> files, int depth,
        long deadlineNanos, int rootStartCount) {
        return depth > root.maxDepth() || (root.maxFiles() > 0 && files.size() - rootStartCount >= root.maxFiles())
            || (deadlineNanos > 0 && System.nanoTime() > deadlineNanos);
    }

    private long scanDeadlineNanos(long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }
        return System.nanoTime() + timeoutMs * 1_000_000L;
    }

    private void addFileEntry(FileScanRoot root, Path entry, List<Map<String, Object>> files,
        Set<String> allowedExtensions) throws IOException {
        String name = entry.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(ext)) {
            return;
        }
        String relativePath = toApiPath(root.path().relativize(entry));
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("rootId", root.id());
        file.put("name", name);
        file.put("path", relativePath);
        file.put("displayPath", displayPath(root, relativePath));
        file.put("size", Files.size(entry));
        file.put("type", ext);
        file.put("modifiedAt", Files.getLastModifiedTime(entry).toInstant().toString());
        files.add(file);
    }

    private Set<String> fileCapsuleAllowedExtensions() {
        Set<String> allowed = new HashSet<>();
        List<String> configured = gatewayProperties.getFileCapsules() != null
            ? gatewayProperties.getFileCapsules().getAllowedExtensions() : null;
        if (configured == null) {
            return allowed;
        }
        for (String ext : configured) {
            if (ext == null) {
                continue;
            }
            String normalized = ext.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                allowed.add(normalized);
            }
        }
        return allowed;
    }

    /**
     * Resolves a file scan root path by its identifier for the given user agent directory.
     *
     * @param userAgentDir user agent working directory
     * @param rootId scan root identifier to resolve
     * @return Optional containing the resolved path, or empty if not found
     */
    public Optional<Path> resolveFileScanRoot(Path userAgentDir, String rootId) {
        String normalizedRootId = normalizeRootId(rootId, 0);
        return fileScanRoots(userAgentDir).stream()
            .filter(root -> root.id().equals(normalizedRootId))
            .map(FileScanRoot::path)
            .findFirst();
    }

    private List<FileScanRoot> fileScanRoots(Path userAgentDir) {
        List<GatewayProperties.FileScanRoot> configured =
            gatewayProperties.getFiles() != null ? gatewayProperties.getFiles().getScanRoots() : null;
        if (configured == null || configured.isEmpty()) {
            configured = new GatewayProperties.FileBrowser().getScanRoots();
        }

        List<FileScanRoot> roots = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < configured.size(); i++) {
            GatewayProperties.FileScanRoot configuredRoot = configured.get(i);
            if (configuredRoot == null || configuredRoot.getPath() == null || configuredRoot.getPath().isBlank()) {
                continue;
            }
            String id = normalizeRootId(configuredRoot.getId(), i);
            if (!ids.add(id)) {
                continue;
            }
            roots.add(new FileScanRoot(id, resolveScanRootPath(userAgentDir, configuredRoot.getPath()),
                configuredRoot.isRecursive(), excludeDirs(configuredRoot.getExcludeDirs()),
                positiveOrDefault(configuredRoot.getMaxDepth(), 6),
                positiveOrDefault(configuredRoot.getMaxFiles(), 1000),
                positiveOrDefault(configuredRoot.getScanTimeoutMs(), 2000)));
        }
        return roots;
    }

    private Set<String> excludeDirs(List<String> configuredExcludeDirs) {
        Set<String> excludeDirs = new HashSet<>(SKIP_DIRS);
        if (configuredExcludeDirs != null) {
            for (String dir : configuredExcludeDirs) {
                if (dir == null) {
                    continue;
                }
                String normalized = dir.trim();
                if (!normalized.isEmpty()) {
                    excludeDirs.add(normalized);
                }
            }
        }
        return excludeDirs;
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private String normalizeRootId(String rootId, int index) {
        return rootId == null || rootId.isBlank() ? "root" + index : rootId.trim();
    }

    private Path resolveScanRootPath(Path userAgentDir, String configuredPath) {
        String expanded =
            configuredPath.replace("${userAgentDir}", userAgentDir.toAbsolutePath().normalize().toString());
        Path path = Path.of(expanded);
        return path.isAbsolute() ? path.normalize() : userAgentDir.resolve(path).normalize();
    }

    private String displayPath(FileScanRoot root, String relativePath) {
        if ("workingDir".equals(root.id())) {
            return relativePath;
        }
        return root.id() + "/" + relativePath;
    }

    private String toApiPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record FileScanRoot(String id, Path path, boolean recursive, Set<String> excludeDirs, int maxDepth,
        int maxFiles, long scanTimeoutMs) {
        private FileScanRoot {
            Objects.requireNonNull(id);
            Objects.requireNonNull(path);
            Objects.requireNonNull(excludeDirs);
        }
    }

    /**
     * Resolve and validate a file path within a base directory.
     *
     * @param baseDir base directory for path resolution
     * @param relativePath relative file path within the base directory
     * @return Resource for the resolved file, or null if unsafe or not found
     */
    public Resource resolveFile(Path baseDir, String relativePath) {
        if (!PathSanitizer.isSafe(baseDir, relativePath)) {
            return null;
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (Files.exists(resolved) && !Files.isDirectory(resolved)) {
            return new FileSystemResource(resolved);
        }
        return null;
    }

    /**
     * Deletes a file within the base directory at the given relative path.
     *
     * @param baseDir base directory containing the file
     * @param relativePath relative path of the file to delete
     * @return true if the file was deleted, false if unsafe or not found
     */
    public boolean deleteFile(Path baseDir, String relativePath) {
        if (!PathSanitizer.isSafe(baseDir, relativePath)) {
            return false;
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            return false;
        }
        try {
            Files.delete(resolved);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete file: " + relativePath, e);
        }
    }

    /**
     * Updates a text file within the base directory at the given relative path with new content.
     *
     * @param baseDir base directory containing the file
     * @param relativePath relative path of the text file
     * @param content new text content to write
     * @return true if updated successfully, false if unsafe or not a text file
     */
    public boolean updateTextFile(Path baseDir, String relativePath, String content) {
        if (!PathSanitizer.isSafe(baseDir, relativePath) || !isEditableTextFile(relativePath)) {
            return false;
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            return false;
        }
        try {
            Files.writeString(resolved, content != null ? content : "", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update file: " + relativePath, e);
        }
    }

    /**
     * Checks whether the given filename has an editable text extension.
     *
     * @param filename file name to check
     * @return true if the file extension is an editable text format
     */
    public boolean isEditableTextFile(String filename) {
        return EDITABLE_TEXT_EXTENSIONS.contains(getPolicyType(filename));
    }

    /**
     * Check if a file extension is allowed for upload.
     *
     * @param filename file name to check
     * @return true if the file extension is allowed for upload
     */
    public boolean isAllowedExtension(String filename) {
        String ext = getExtension(filename);
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(ext) || ext.isEmpty();
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String getPolicyType(String filename) {
        String normalized = filename == null ? "" : filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String baseName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String lowerBaseName = baseName.toLowerCase(Locale.ROOT);
        if ("dockerfile".equals(lowerBaseName)) {
            return "dockerfile";
        }
        if ("makefile".equals(lowerBaseName)) {
            return "makefile";
        }
        return getExtension(baseName);
    }

    /**
     * Resolves the MIME type for the given filename based on its extension.
     *
     * @param filename file name whose MIME type to resolve
     * @return MIME type string, defaults to application/octet-stream
     */
    public String getMimeType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * Whether this MIME type should be displayed inline (vs download).
     *
     * @param mimeType MIME type to check
     * @return true if the MIME type should be displayed inline
     */
    public boolean isInline(String mimeType) {
        return mimeType.startsWith("text/") || mimeType.startsWith("image/") || "application/json".equals(mimeType)
            || "application/pdf".equals(mimeType);
    }

    // ── File capsule persistence ────────────────────────────────────────

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CAPSULE_FILE = "file-capsules.json";

    /**
     * Compute the diff between two file snapshots.
     * Returns files that are new or have been modified (size or modifiedAt changed).
     *
     * @param before previous file snapshot list
     * @param after current file snapshot list
     * @return list of new or changed file entries
     */
    public List<Map<String, String>> diffFiles(List<Map<String, Object>> before, List<Map<String, Object>> after) {
        Set<String> allowedExtensions = new HashSet<>();
        List<String> configuredAllowed = gatewayProperties.getFileCapsules() != null
            ? gatewayProperties.getFileCapsules().getAllowedExtensions() : null;
        if (configuredAllowed != null) {
            for (String ext : configuredAllowed) {
                if (ext == null) {
                    continue;
                }
                String normalized = ext.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    allowedExtensions.add(normalized);
                }
            }
        }
        if (allowedExtensions.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> beforeMap = new HashMap<>();
        for (Map<String, Object> f : before) {
            beforeMap.put(fileIdentity(f), f);
        }

        List<Map<String, String>> changed = new ArrayList<>();
        for (Map<String, Object> f : after) {
            String path = (String) f.get("path");
            if (isInternalRuntimeArtifact(path)) {
                continue;
            }
            String ext = (String) f.get("type");
            String normalizedExt = ext != null ? ext.toLowerCase(Locale.ROOT) : "";
            if (!allowedExtensions.contains(normalizedExt)) {
                continue;
            }
            Map<String, Object> prev = beforeMap.get(fileIdentity(f));
            boolean isNew = prev == null;
            boolean isUpdated = prev != null
                && (!prev.get("modifiedAt").equals(f.get("modifiedAt")) || !prev.get("size").equals(f.get("size")));
            if (isNew || isUpdated) {
                String name = (String) f.get("name");
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("path", path);
                entry.put("name", name);
                entry.put("ext", ext != null ? ext : "");
                entry.put("rootId", String.valueOf(f.getOrDefault("rootId", "workingDir")));
                entry.put("displayPath", String.valueOf(f.getOrDefault("displayPath", path)));
                changed.add(entry);
            }
        }
        return changed;
    }

    private String fileIdentity(Map<String, Object> file) {
        return String.valueOf(file.getOrDefault("rootId", "workingDir")) + ":" + file.get("path");
    }

    private boolean isInternalRuntimeArtifact(String path) {
        return path != null && path.replace('\\', '/').startsWith("logs/mcp/");
    }

    /**
     * Persist file capsule entries for a session.
     * Merges new entries into existing data (read-modify-write).
     * Path: {workingDir}/data/{sessionId}/file-capsules.json
     *
     * @param workingDir agent working directory
     * @param sessionId session identifier
     * @param messageId message identifier for the capsule entry
     * @param files files
     */
    public void persistOutputFiles(Path workingDir, String sessionId, String messageId,
        List<Map<String, String>> files) {
        Path dir = workingDir.resolve("data").resolve(sessionId);
        Path file = dir.resolve(CAPSULE_FILE);
        try {
            Files.createDirectories(dir);

            // Read existing entries (if any)
            Map<String, List<Map<String, String>>> entries = new LinkedHashMap<>();
            if (Files.exists(file)) {
                Map<String, Object> existing =
                    MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), new TypeReference<>() {});
                Object raw = existing.get("entries");
                if (raw instanceof Map<?, ?> rawMap) {
                    for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                        if (e.getValue() instanceof List<?> list) {
                            List<Map<String, String>> typed = new ArrayList<>();
                            for (Object item : list) {
                                if (item instanceof Map<?, ?> m) {
                                    Map<String, String> entry = new LinkedHashMap<>();
                                    m.forEach((k, v) -> entry.put(String.valueOf(k), String.valueOf(v)));
                                    typed.add(entry);
                                }
                            }
                            entries.put(String.valueOf(e.getKey()), typed);
                        }
                    }
                }
            }

            // Add / replace entry for this messageId
            entries.put(messageId, files);

            // Write back
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("entries", entries);
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper),
                StandardCharsets.UTF_8);

            log.debug("Persisted {} file capsules for session {} message {}", files.size(), sessionId, messageId);
        } catch (IOException e) {
            log.warn("Failed to persist file capsules for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Load persisted file capsule entries for a session.
     * Returns messageId → files mapping, or empty map on any error.
     *
     * @param workingDir agent working directory
     * @param sessionId session identifier
     * @return messageId-to-files mapping, or empty map on error
     */
    public Map<String, List<Map<String, String>>> loadOutputFiles(Path workingDir, String sessionId) {
        Path file = workingDir.resolve("data").resolve(sessionId).resolve(CAPSULE_FILE);
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Map<String, Object> data =
                MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), new TypeReference<>() {});
            Object raw = data.get("entries");
            if (!(raw instanceof Map<?, ?> rawMap)) {
                return Map.of();
            }
            Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                if (e.getValue() instanceof List<?> list) {
                    List<Map<String, String>> typed = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, String> entry = new LinkedHashMap<>();
                            m.forEach((k, v) -> entry.put(String.valueOf(k), String.valueOf(v)));
                            typed.add(entry);
                        }
                    }
                    result.put(String.valueOf(e.getKey()), typed);
                }
            }
            return result;
        } catch (IOException e) {
            log.warn("Failed to read file capsules for session {}: {}", sessionId, e.getMessage());
            return Map.of();
        }
    }
}
