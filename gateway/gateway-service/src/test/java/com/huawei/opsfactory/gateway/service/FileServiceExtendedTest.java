/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extended tests for FileService covering:
 * - isAllowedExtension
 * - SKIP_DIRS filtering in listFiles
 * - SKIP_FILES filtering in listFiles
 * - resolveFile fallback search
 * - Hidden directory filtering
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class FileServiceExtendedTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private FileService fileService;

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        fileService = new FileService(new GatewayProperties());
    }

    /**
     * Tests is allowed extension allowed types.
     */
    @Test
    public void testIsAllowedExtension_allowedTypes() {
        assertTrue(fileService.isAllowedExtension("report.txt"));
        assertTrue(fileService.isAllowedExtension("data.json"));
        assertTrue(fileService.isAllowedExtension("image.png"));
        assertTrue(fileService.isAllowedExtension("doc.pdf"));
        assertTrue(fileService.isAllowedExtension("script.py"));
        assertTrue(fileService.isAllowedExtension("code.java"));
        assertTrue(fileService.isAllowedExtension("archive.zip"));
        assertTrue(fileService.isAllowedExtension("log.log"));
    }

    /**
     * Tests is allowed extension blocked types.
     */
    @Test
    public void testIsAllowedExtension_blockedTypes() {
        assertFalse(fileService.isAllowedExtension("virus.exe"));
        assertFalse(fileService.isAllowedExtension("script.bat"));
        assertFalse(fileService.isAllowedExtension("script.cmd"));
        assertFalse(fileService.isAllowedExtension("library.dll"));
        assertFalse(fileService.isAllowedExtension("script.vbs"));
        assertFalse(fileService.isAllowedExtension("script.ps1"));
        assertFalse(fileService.isAllowedExtension("driver.sys"));
        assertFalse(fileService.isAllowedExtension("screensaver.scr"));
    }

    /**
     * Tests is allowed extension no extension allowed.
     */
    @Test
    public void testIsAllowedExtension_noExtension_allowed() {
        assertTrue(fileService.isAllowedExtension("Makefile"));
        assertTrue(fileService.isAllowedExtension("Dockerfile"));
    }

    /**
     * Tests is allowed extension unknown extension rejected.
     */
    @Test
    public void testIsAllowedExtension_unknownExtension_rejected() {
        // Unknown extension is not in ALLOWED set → rejected (unless blocked explicitly)
        assertFalse(fileService.isAllowedExtension("file.xyz"));
        assertFalse(fileService.isAllowedExtension("file.abc"));
    }

    /**
     * Tests is allowed extension case insensitive.
     */
    @Test
    public void testIsAllowedExtension_caseInsensitive() {
        assertTrue(fileService.isAllowedExtension("image.PNG"));
        assertTrue(fileService.isAllowedExtension("doc.PDF"));
        assertFalse(fileService.isAllowedExtension("virus.EXE"));
    }

    /**
     * Tests list files skips dirs data.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsDirs_data() throws IOException {
        File dataDir = tempFolder.newFolder("data");
        createFile(new File(dataDir, "secret.json"), "{}");
        createFile(new File(tempFolder.getRoot(), "visible.txt"), "hello");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
        assertEquals("visible.txt", files.get(0).get("name"));
    }

    /**
     * Tests list files skips dirs state.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsDirs_state() throws IOException {
        File stateDir = tempFolder.newFolder("state");
        createFile(new File(stateDir, "state.db"), "data");
        createFile(new File(tempFolder.getRoot(), "file.txt"), "content");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
    }

    /**
     * Tests list files skips dirs config.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsDirs_config() throws IOException {
        File configDir = tempFolder.newFolder("config");
        createFile(new File(configDir, "config.yaml"), "key: val");
        createFile(new File(tempFolder.getRoot(), "readme.md"), "# Title");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
        assertEquals("readme.md", files.get(0).get("name"));
    }

    /**
     * Tests list files skips dirs node modules.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsDirs_nodeModules() throws IOException {
        File nodeModules = tempFolder.newFolder("node_modules");
        createFile(new File(nodeModules, "package.json"), "{}");
        createFile(new File(tempFolder.getRoot(), "index.txt"), "hello");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
    }

    /**
     * Tests list files skips dirs dot goose.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsDirs_dotGoose() throws IOException {
        File gooseDir = tempFolder.newFolder(".goose");
        createFile(new File(gooseDir, "session.json"), "{}");
        createFile(new File(tempFolder.getRoot(), "main.txt"), "hello");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
    }

    /**
     * Tests list files skips hidden dirs.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsHiddenDirs() throws IOException {
        File hiddenDir = tempFolder.newFolder(".hidden");
        createFile(new File(hiddenDir, "secret.txt"), "hidden");
        createFile(new File(tempFolder.getRoot(), "visible.txt"), "visible");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
        assertEquals("visible.txt", files.get(0).get("name"));
    }

    /**
     * Tests list files skips dsstore.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsDSStore() throws IOException {
        createFile(new File(tempFolder.getRoot(), ".DS_Store"), "");
        createFile(new File(tempFolder.getRoot(), "file.txt"), "content");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
        assertEquals("file.txt", files.get(0).get("name"));
    }

    /**
     * Tests list files skips agentsmd.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsAGENTSmd() throws IOException {
        createFile(new File(tempFolder.getRoot(), "AGENTS.md"), "# Agents");
        createFile(new File(tempFolder.getRoot(), "notes.md"), "# Notes");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
        assertEquals("notes.md", files.get(0).get("name"));
    }

    /**
     * Tests list files skips gitkeep.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_skipsGitkeep() throws IOException {
        createFile(new File(tempFolder.getRoot(), ".gitkeep"), "");
        createFile(new File(tempFolder.getRoot(), "code.txt"), "pass");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
    }

    /**
     * Tests resolve file fallback search finds in subdir.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testResolveFile_fallbackSearch_findsInSubdir() throws IOException {
        File subDir = tempFolder.newFolder("uploads");
        createFile(new File(subDir, "report.pdf"), "pdf-content");

        // Ask for "report.pdf" at root — fallback search is not supported
        Resource resource = fileService.resolveFile(tempFolder.getRoot().toPath(), "report.pdf");
        assertNull(resource);
    }

    /**
     * Tests resolve file fallback search skips skip dirs.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testResolveFile_fallbackSearch_skipsSkipDirs() throws IOException {
        File dataDir = tempFolder.newFolder("data");
        createFile(new File(dataDir, "hidden.txt"), "should not find");

        // File only exists in a skip-dir; should not be found by fallback search
        Resource resource = fileService.resolveFile(tempFolder.getRoot().toPath(), "hidden.txt");
        assertNull(resource);
    }

    /**
     * Tests resolve file direct path exists in subdir.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testResolveFile_directPath_existsInSubdir() throws IOException {
        File subDir = tempFolder.newFolder("docs");
        createFile(new File(subDir, "readme.txt"), "hello");

        Resource resource = fileService.resolveFile(tempFolder.getRoot().toPath(), "docs/readme.txt");
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    /**
     * Tests diff files ignores internal mcp logs.
     */
    @Test
    public void testDiffFiles_ignoresInternalMcpLogs() {
        List<Map<String, Object>> before =
            List.of(snapshot("logs/mcp/control_center.log", "control_center.log", "log", 100L, "2026-04-08T06:58:57Z"));
        List<Map<String, Object>> after =
            List.of(snapshot("logs/mcp/control_center.log", "control_center.log", "log", 120L, "2026-04-08T07:00:27Z"));

        List<Map<String, String>> changed = fileService.diffFiles(before, after);

        assertTrue(changed.isEmpty());
    }

    /**
     * Tests diff files keeps user generated files.
     */
    @Test
    public void testDiffFiles_keepsUserGeneratedFiles() {
        List<Map<String, Object>> before = new ArrayList<>();
        List<Map<String, Object>> after =
            List.of(snapshot("reports/platform-status.md", "platform-status.md", "md", 256L, "2026-04-08T07:00:27Z"));

        List<Map<String, String>> changed = fileService.diffFiles(before, after);

        assertEquals(1, changed.size());
        assertEquals("reports/platform-status.md", changed.get(0).get("path"));
        assertEquals("platform-status.md", changed.get(0).get("name"));
        assertEquals("md", changed.get(0).get("ext"));
    }

    /**
     * Tests diff files keeps same relative path from different roots.
     */
    @Test
    public void testDiffFiles_keepsSameRelativePathFromDifferentRoots() {
        List<Map<String, Object>> before = new ArrayList<>();
        List<Map<String, Object>> after = List.of(
            snapshotWithRoot("workingDir", "example-file.md", "example-file.md", "example-file.md", "md", 128L,
                "2026-04-21T14:20:00Z"),
            snapshotWithRoot("output", "example-file.md", "output/example-file.md", "example-file.md", "md", 128L,
                "2026-04-21T14:20:00Z"));

        List<Map<String, String>> changed = fileService.diffFiles(before, after);

        assertEquals(2, changed.size());
        assertEquals("workingDir", changed.get(0).get("rootId"));
        assertEquals("example-file.md", changed.get(0).get("displayPath"));
        assertEquals("output", changed.get(1).get("rootId"));
        assertEquals("output/example-file.md", changed.get(1).get("displayPath"));
    }

    /**
     * Tests list top level files skips files in subdirectories.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListTopLevelFiles_skipsFilesInSubdirectories() throws IOException {
        createFile(new File(tempFolder.getRoot(), "summary.md"), "# Summary");
        createFile(new File(tempFolder.newFolder("reports"), "platform-status.md"), "# Report");

        List<Map<String, Object>> files = fileService.listTopLevelFiles(tempFolder.getRoot().toPath());

        assertEquals(1, files.size());
        assertEquals("summary.md", files.get(0).get("name"));
    }

    /**
     * Tests list files default scan roots include output non recursive.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_defaultScanRootsIncludeOutputNonRecursive() throws IOException {
        createFile(new File(tempFolder.getRoot(), "summary.md"), "# Summary");
        File outputDir = tempFolder.newFolder("output");
        createFile(new File(outputDir, "report.html"), "<h1>Report</h1>");
        createFile(new File(outputDir, "assets/chart.png"), "png");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        Map<String, Map<String, Object>> byName =
            files.stream().collect(java.util.stream.Collectors.toMap(file -> (String) file.get("name"), file -> file));

        assertEquals(2, files.size());
        assertEquals("workingDir", byName.get("summary.md").get("rootId"));
        assertEquals("summary.md", byName.get("summary.md").get("displayPath"));
        assertEquals("output", byName.get("report.html").get("rootId"));
        assertEquals("report.html", byName.get("report.html").get("path"));
        assertEquals("output/report.html", byName.get("report.html").get("displayPath"));
        assertFalse(byName.containsKey("chart.png"));
    }

    /**
     * Tests list capsule relevant files uses files scan roots.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListCapsuleRelevantFiles_usesFilesScanRoots() throws IOException {
        createFile(new File(tempFolder.getRoot(), "example-file.md"), "# Root");
        File outputDir = tempFolder.newFolder("output");
        createFile(new File(outputDir, "example-file.md"), "# Output");

        List<Map<String, Object>> files = fileService.listCapsuleRelevantFiles(tempFolder.getRoot().toPath());

        assertEquals(2, files.size());
        assertEquals("workingDir", files.get(0).get("rootId"));
        assertEquals("example-file.md", files.get(0).get("path"));
        assertEquals("output", files.get(1).get("rootId"));
        assertEquals("example-file.md", files.get(1).get("path"));
        assertEquals("output/example-file.md", files.get(1).get("displayPath"));
    }

    /**
     * Tests list files recursive scan root includes nested files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_recursiveScanRootIncludesNestedFiles() throws IOException {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.FileBrowser filesConfig = new GatewayProperties.FileBrowser();
        filesConfig
            .setScanRoots(List.of(new GatewayProperties.FileScanRoot("reports", "${userAgentDir}/reports", true)));
        properties.setFiles(filesConfig);
        FileService recursiveFileService = new FileService(properties);

        createFile(new File(tempFolder.getRoot(), "reports/summary.md"), "# Summary");
        createFile(new File(tempFolder.getRoot(), "reports/monthly/detail.md"), "# Detail");
        createFile(new File(tempFolder.getRoot(), "reports/.hidden/secret.md"), "# Secret");

        List<Map<String, Object>> files = recursiveFileService.listFiles(tempFolder.getRoot().toPath());
        Map<String, Map<String, Object>> byPath =
            files.stream().collect(java.util.stream.Collectors.toMap(file -> (String) file.get("path"), file -> file));

        assertEquals(2, files.size());
        assertTrue(byPath.containsKey("summary.md"));
        assertTrue(byPath.containsKey("monthly/detail.md"));
        assertFalse(byPath.containsKey(".hidden/secret.md"));
    }

    /**
     * Tests list files recursive scan root honors configured exclude dirs.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_recursiveScanRootHonorsConfiguredExcludeDirs() throws IOException {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.FileBrowser filesConfig = new GatewayProperties.FileBrowser();
        GatewayProperties.FileScanRoot root = new GatewayProperties.FileScanRoot("workingDir", "${userAgentDir}", true);
        root.setExcludeDirs(List.of("tmp-output"));
        filesConfig.setScanRoots(List.of(root));
        properties.setFiles(filesConfig);
        FileService recursiveFileService = new FileService(properties);

        createFile(new File(tempFolder.getRoot(), "docs/visible.md"), "# Visible");
        createFile(new File(tempFolder.getRoot(), "tmp-output/hidden.md"), "# Hidden");

        List<Map<String, Object>> files = recursiveFileService.listFiles(tempFolder.getRoot().toPath());
        Map<String, Map<String, Object>> byPath =
            files.stream().collect(java.util.stream.Collectors.toMap(file -> (String) file.get("path"), file -> file));

        assertEquals(1, files.size());
        assertTrue(byPath.containsKey("docs/visible.md"));
        assertFalse(byPath.containsKey("tmp-output/hidden.md"));
    }

    /**
     * Tests list files recursive scan root honors max depth.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_recursiveScanRootHonorsMaxDepth() throws IOException {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.FileBrowser filesConfig = new GatewayProperties.FileBrowser();
        GatewayProperties.FileScanRoot root = new GatewayProperties.FileScanRoot("workingDir", "${userAgentDir}", true);
        root.setMaxDepth(1);
        filesConfig.setScanRoots(List.of(root));
        properties.setFiles(filesConfig);
        FileService recursiveFileService = new FileService(properties);

        createFile(new File(tempFolder.getRoot(), "top.md"), "# Top");
        createFile(new File(tempFolder.getRoot(), "level-one/visible.md"), "# Visible");
        createFile(new File(tempFolder.getRoot(), "level-one/level-two/hidden.md"), "# Hidden");

        List<Map<String, Object>> files = recursiveFileService.listFiles(tempFolder.getRoot().toPath());
        Map<String, Map<String, Object>> byPath =
            files.stream().collect(java.util.stream.Collectors.toMap(file -> (String) file.get("path"), file -> file));

        assertEquals(2, files.size());
        assertTrue(byPath.containsKey("top.md"));
        assertTrue(byPath.containsKey("level-one/visible.md"));
        assertFalse(byPath.containsKey("level-one/level-two/hidden.md"));
    }

    /**
     * Tests list files recursive scan root honors max files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_recursiveScanRootHonorsMaxFiles() throws IOException {
        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.FileBrowser filesConfig = new GatewayProperties.FileBrowser();
        GatewayProperties.FileScanRoot root = new GatewayProperties.FileScanRoot("workingDir", "${userAgentDir}", true);
        root.setMaxFiles(2);
        filesConfig.setScanRoots(List.of(root));
        properties.setFiles(filesConfig);
        FileService recursiveFileService = new FileService(properties);

        createFile(new File(tempFolder.getRoot(), "one.md"), "# One");
        createFile(new File(tempFolder.getRoot(), "two.md"), "# Two");
        createFile(new File(tempFolder.getRoot(), "three.md"), "# Three");

        List<Map<String, Object>> files = recursiveFileService.listFiles(tempFolder.getRoot().toPath());

        assertEquals(2, files.size());
    }

    private Map<String, Object> snapshot(String path, String name, String type, long size, String modifiedAt) {
        return Map.of("path", path, "name", name, "type", type, "size", size, "modifiedAt", modifiedAt);
    }

    private Map<String, Object> snapshotWithRoot(String rootId, String path, String displayPath, String name,
        String type, long size, String modifiedAt) {
        return Map.of("rootId", rootId, "path", path, "displayPath", displayPath, "name", name, "type", type, "size",
            size, "modifiedAt", modifiedAt);
    }

    private void createFile(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(file)) {
            w.write(content);
        }
    }
}
