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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Test coverage for File Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class FileServiceTest {
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
     * Tests list files empty dir.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_emptyDir() throws IOException {
        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertTrue(files.isEmpty());
    }

    /**
     * Tests list files with files.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_withFiles() throws IOException {
        createFile("file1.txt", "hello");
        createFile("file2.json", "{}");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(2, files.size());
    }

    /**
     * Tests list files recursive.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_recursive() throws IOException {
        File subDir = tempFolder.newFolder("subdir");
        try (FileWriter w = new FileWriter(new File(subDir, "nested.txt"))) {
            w.write("nested content");
        }
        createFile("top.txt", "top");

        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
    }

    /**
     * Tests list top level files non recursive.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListTopLevelFiles_nonRecursive() throws IOException {
        File subDir = tempFolder.newFolder("subdir");
        try (FileWriter w = new FileWriter(new File(subDir, "nested.txt"))) {
            w.write("nested content");
        }
        createFile("top.txt", "top");

        List<Map<String, Object>> files = fileService.listTopLevelFiles(tempFolder.getRoot().toPath());
        assertEquals(1, files.size());
        assertEquals("top.txt", files.get(0).get("name"));
    }

    /**
     * Tests list files non existent dir.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testListFiles_nonExistentDir() throws IOException {
        List<Map<String, Object>> files = fileService.listFiles(tempFolder.getRoot().toPath().resolve("nonexistent"));
        assertTrue(files.isEmpty());
    }

    /**
     * Tests resolve file valid.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testResolveFile_valid() throws IOException {
        createFile("test.txt", "content");
        Resource resource = fileService.resolveFile(tempFolder.getRoot().toPath(), "test.txt");
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    /**
     * Tests resolve file traversal attack.
     */
    @Test
    public void testResolveFile_traversalAttack() {
        Resource resource = fileService.resolveFile(tempFolder.getRoot().toPath(), "../../../etc/passwd");
        assertNull(resource);
    }

    /**
     * Tests resolve file non existent.
     */
    @Test
    public void testResolveFile_nonExistent() {
        Resource resource = fileService.resolveFile(tempFolder.getRoot().toPath(), "missing.txt");
        assertNull(resource);
    }

    /**
     * Tests get mime type.
     */
    @Test
    public void testGetMimeType() {
        assertEquals("application/json", fileService.getMimeType("data.json"));
        assertEquals("image/png", fileService.getMimeType("image.png"));
        assertEquals("text/plain", fileService.getMimeType("readme.txt"));
        assertEquals("application/pdf", fileService.getMimeType("doc.pdf"));
        assertEquals("text/markdown", fileService.getMimeType("notes.md"));
        assertEquals("application/octet-stream", fileService.getMimeType("noext"));
        assertEquals("application/octet-stream", fileService.getMimeType("unknown.xyz"));
    }

    /**
     * Tests is inline.
     */
    @Test
    public void testIsInline() {
        assertTrue(fileService.isInline("text/plain"));
        assertTrue(fileService.isInline("text/html"));
        assertTrue(fileService.isInline("image/png"));
        assertTrue(fileService.isInline("application/json"));
        assertTrue(fileService.isInline("application/pdf"));
        assertFalse(fileService.isInline("application/zip"));
        assertFalse(fileService.isInline("application/octet-stream"));
    }

    /**
     * Tests is editable text file.
     */
    @Test
    public void testIsEditableTextFile() {
        assertTrue(fileService.isEditableTextFile("notes.md"));
        assertTrue(fileService.isEditableTextFile("output/config.yaml"));
        assertTrue(fileService.isEditableTextFile("Dockerfile"));
        assertFalse(fileService.isEditableTextFile("slides.pptx"));
        assertFalse(fileService.isEditableTextFile("image.png"));
    }

    /**
     * Tests update text file overwrites content.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateTextFile_overwritesContent() throws IOException {
        createFile("notes.md", "old");

        boolean updated = fileService.updateTextFile(tempFolder.getRoot().toPath(), "notes.md", "new");

        assertTrue(updated);
        assertEquals("new",
            Files.readString(tempFolder.getRoot().toPath().resolve("notes.md"), StandardCharsets.UTF_8));
    }

    /**
     * Tests update text file missing file.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateTextFile_missingFile() throws IOException {
        boolean updated = fileService.updateTextFile(tempFolder.getRoot().toPath(), "missing.md", "new");

        assertFalse(updated);
    }

    /**
     * Tests update text file rejects unsupported type.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testUpdateTextFile_rejectsUnsupportedType() throws IOException {
        createFile("slides.pptx", "old");

        boolean updated = fileService.updateTextFile(tempFolder.getRoot().toPath(), "slides.pptx", "new");

        assertFalse(updated);
    }

    private void createFile(String name, String content) throws IOException {
        File file = new File(tempFolder.getRoot(), name);
        try (FileWriter w = new FileWriter(file)) {
            w.write(content);
        }
    }
}
