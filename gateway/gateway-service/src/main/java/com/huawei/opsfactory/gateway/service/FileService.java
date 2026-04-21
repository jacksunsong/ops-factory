package com.huawei.opsfactory.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.common.util.PathSanitizer;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;

@Service
public class FileService {

    private final GatewayProperties gatewayProperties;

    public FileService(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            entry("json", "application/json"),
            entry("pdf", "application/pdf"),
            entry("xml", "application/xml"),
            entry("zip", "application/zip"),
            entry("csv", "text/csv"),
            entry("txt", "text/plain"),
            entry("md", "text/markdown"),
            entry("html", "text/html"),
            entry("css", "text/css"),
            entry("js", "text/javascript"),
            entry("ts", "text/typescript"),
            entry("java", "text/x-java-source"),
            entry("py", "text/x-python"),
            entry("yaml", "text/yaml"),
            entry("yml", "text/yaml"),
            entry("png", "image/png"),
            entry("jpg", "image/jpeg"),
            entry("jpeg", "image/jpeg"),
            entry("gif", "image/gif"),
            entry("svg", "image/svg+xml"),
            entry("webp", "image/webp"),
            entry("bmp", "image/bmp"),
            entry("doc", "application/msword"),
            entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            entry("xls", "application/vnd.ms-excel"),
            entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            entry("ppt", "application/vnd.ms-powerpoint"),
            entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private static final Set<String> SKIP_DIRS = Set.of(
            "data", "state", "config", "node_modules", ".goose");

    private static final Set<String> SKIP_FILES = Set.of(
            ".DS_Store", "AGENTS.md", ".gitkeep");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "md", "json", "csv", "xml", "yaml", "yml",
            "html", "css", "js", "ts", "java", "py", "go", "rs", "rb", "sh",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp",
            "zip", "gz", "tar", "log");

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "dll", "sys", "scr",
            "vbs", "vbe", "wsf", "wsh", "ps1");

    private static final Set<String> EDITABLE_TEXT_EXTENSIONS = Set.of(
            "txt", "log", "ini", "conf",
            "js", "ts", "jsx", "tsx", "mjs", "cjs",
            "py", "sh", "bash", "zsh",
            "yaml", "yml", "json", "toml",
            "css", "scss", "less",
            "xml", "sql", "graphql",
            "go", "rs", "java", "c", "cpp", "h", "hpp",
            "rb", "php", "swift", "kt", "scala",
            "csv", "tsv",
            "env", "gitignore", "dockerignore", "editorconfig", "prettierrc",
            "eslintrc", "babelrc",
            "dockerfile", "makefile",
            "vue", "svelte",
            "md", "markdown", "html", "htm");

    /**
     * List files for the Files module from configured scan roots.
     */
    public List<Map<String, Object>> listFiles(Path userAgentDir) throws IOException {
        List<Map<String, Object>> files = new ArrayList<>();
        for (FileScanRoot root : fileScanRoots(userAgentDir)) {
            if (root.recursive()) {
                listFilesRecursive(root, root.path(), files);
            } else {
                listRootFiles(root, files);
            }
        }
        return files;
    }

    /**
     * List only top-level files under a directory, excluding subdirectories.
     */
    public List<Map<String, Object>> listTopLevelFiles(Path dir) throws IOException {
        List<Map<String, Object>> files = new ArrayList<>();
        listRootFiles(new FileScanRoot("workingDir", dir, false), files);
        return files;
    }

    private void listRootFiles(FileScanRoot root, List<Map<String, Object>> files) throws IOException {
        Path dir = root.path();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && !SKIP_FILES.contains(entry.getFileName().toString())) {
                    addFileEntry(root, entry, files);
                }
            }
        }
    }

    /**
     * List "user-facing" output files for chat file capsules:
     * - Same scan roots as the Files module
     */
    public List<Map<String, Object>> listCapsuleRelevantFiles(Path dir) throws IOException {
        return listFiles(dir);
    }

    private void listFilesRecursive(FileScanRoot root, Path current, List<Map<String, Object>> files) throws IOException {
        if (!Files.isDirectory(current)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    if (!SKIP_DIRS.contains(name) && !name.startsWith(".")) {
                        listFilesRecursive(root, entry, files);
                    }
                } else {
                    if (!SKIP_FILES.contains(name)) {
                        addFileEntry(root, entry, files);
                    }
                }
            }
        }
    }

    private void addFileEntry(FileScanRoot root, Path entry, List<Map<String, Object>> files) throws IOException {
        String name = entry.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
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

    public Optional<Path> resolveFileScanRoot(Path userAgentDir, String rootId) {
        String normalizedRootId = normalizeRootId(rootId, 0);
        return fileScanRoots(userAgentDir).stream()
                .filter(root -> root.id().equals(normalizedRootId))
                .map(FileScanRoot::path)
                .findFirst();
    }

    private List<FileScanRoot> fileScanRoots(Path userAgentDir) {
        List<GatewayProperties.FileScanRoot> configured = gatewayProperties.getFiles() != null
                ? gatewayProperties.getFiles().getScanRoots()
                : null;
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
            roots.add(new FileScanRoot(
                    id,
                    resolveScanRootPath(userAgentDir, configuredRoot.getPath()),
                    configuredRoot.isRecursive()));
        }
        return roots;
    }

    private String normalizeRootId(String rootId, int index) {
        return rootId == null || rootId.isBlank() ? "root" + index : rootId.trim();
    }

    private Path resolveScanRootPath(Path userAgentDir, String configuredPath) {
        String expanded = configuredPath.replace("${userAgentDir}", userAgentDir.toAbsolutePath().normalize().toString());
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

    private record FileScanRoot(String id, Path path, boolean recursive) {
        private FileScanRoot {
            Objects.requireNonNull(id);
            Objects.requireNonNull(path);
        }
    }

    /**
     * Resolve and validate a file path within a base directory.
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

    public boolean deleteFile(Path baseDir, String relativePath) throws IOException {
        if (!PathSanitizer.isSafe(baseDir, relativePath)) {
            return false;
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            return false;
        }
        Files.delete(resolved);
        return true;
    }

    public boolean updateTextFile(Path baseDir, String relativePath, String content) throws IOException {
        if (!PathSanitizer.isSafe(baseDir, relativePath) || !isEditableTextFile(relativePath)) {
            return false;
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            return false;
        }
        Files.writeString(resolved, content != null ? content : "", StandardCharsets.UTF_8);
        return true;
    }

    public boolean isEditableTextFile(String filename) {
        return EDITABLE_TEXT_EXTENSIONS.contains(getPolicyType(filename));
    }

    /**
     * Check if a file extension is allowed for upload.
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
        return filename.substring(dot + 1).toLowerCase();
    }

    private String getPolicyType(String filename) {
        String normalized = filename == null ? "" : filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String baseName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String lowerBaseName = baseName.toLowerCase();
        if ("dockerfile".equals(lowerBaseName)) return "dockerfile";
        if ("makefile".equals(lowerBaseName)) return "makefile";
        return getExtension(baseName);
    }

    public String getMimeType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * Whether this MIME type should be displayed inline (vs download).
     */
    public boolean isInline(String mimeType) {
        return mimeType.startsWith("text/")
                || mimeType.startsWith("image/")
                || "application/json".equals(mimeType)
                || "application/pdf".equals(mimeType);
    }

    // ── File capsule persistence ────────────────────────────────────────

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CAPSULE_FILE = "file-capsules.json";

    /**
     * Compute the diff between two file snapshots.
     * Returns files that are new or have been modified (size or modifiedAt changed).
     */
    public List<Map<String, String>> diffFiles(List<Map<String, Object>> before,
                                                List<Map<String, Object>> after) {
        Set<String> allowedExtensions = new HashSet<>();
        List<String> configuredAllowed = gatewayProperties.getFileCapsules() != null
                ? gatewayProperties.getFileCapsules().getAllowedExtensions()
                : null;
        if (configuredAllowed != null) {
            for (String ext : configuredAllowed) {
                if (ext == null) continue;
                String normalized = ext.trim().toLowerCase();
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
            String normalizedExt = ext != null ? ext.toLowerCase() : "";
            if (!allowedExtensions.contains(normalizedExt)) {
                continue;
            }
            Map<String, Object> prev = beforeMap.get(fileIdentity(f));
            boolean isNew = prev == null;
            boolean isUpdated = prev != null && (
                    !prev.get("modifiedAt").equals(f.get("modifiedAt"))
                            || !prev.get("size").equals(f.get("size")));
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
                Map<String, Object> existing = MAPPER.readValue(
                        Files.readString(file, StandardCharsets.UTF_8),
                        new TypeReference<>() {});
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
        } catch (Exception e) {
            log.warn("Failed to persist file capsules for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Load persisted file capsule entries for a session.
     * Returns messageId → files mapping, or empty map on any error.
     */
    public Map<String, List<Map<String, String>>> loadOutputFiles(Path workingDir, String sessionId) {
        Path file = workingDir.resolve("data").resolve(sessionId).resolve(CAPSULE_FILE);
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Map<String, Object> data = MAPPER.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    new TypeReference<>() {});
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
        } catch (Exception e) {
            log.warn("Failed to read file capsules for session {}: {}", sessionId, e.getMessage());
            return Map.of();
        }
    }
}
