/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test coverage for File Util.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class FileUtilTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * Tests delete recursively single file.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteRecursively_singleFile() throws IOException {
        File file = tempFolder.newFile("test.txt");
        assertTrue(file.exists());
        FileUtil.deleteRecursively(file.toPath());
        assertFalse(file.exists());
    }

    /**
     * Tests delete recursively empty directory.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteRecursively_emptyDirectory() throws IOException {
        File dir = tempFolder.newFolder("emptyDir");
        assertTrue(dir.exists());
        FileUtil.deleteRecursively(dir.toPath());
        assertFalse(dir.exists());
    }

    /**
     * Tests delete recursively nested directories.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteRecursively_nestedDirectories() throws IOException {
        File dir = tempFolder.newFolder("parent");
        File child = new File(dir, "child");
        child.mkdir();
        File grandchild = new File(child, "grandchild");
        grandchild.mkdir();

        // Create files at each level
        writeFile(new File(dir, "a.txt"), "a");
        writeFile(new File(child, "b.txt"), "b");
        writeFile(new File(grandchild, "c.txt"), "c");

        assertTrue(dir.exists());
        FileUtil.deleteRecursively(dir.toPath());
        assertFalse(dir.exists());
    }

    /**
     * Tests delete recursively non existent path.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteRecursively_nonExistentPath() throws IOException {
        Path nonExistent = tempFolder.getRoot().toPath().resolve("does-not-exist");
        assertFalse(Files.exists(nonExistent));
        // Should not throw
        FileUtil.deleteRecursively(nonExistent);
    }

    /**
     * Tests delete recursively directory with mixed content.
     *
     * @throws IOException if the operation fails
     */
    @Test
    public void testDeleteRecursively_directoryWithMixedContent() throws IOException {
        File dir = tempFolder.newFolder("mixed");
        File subDir1 = new File(dir, "sub1");
        File subDir2 = new File(dir, "sub2");
        subDir1.mkdir();
        subDir2.mkdir();
        writeFile(new File(dir, "root.txt"), "root");
        writeFile(new File(subDir1, "file1.txt"), "file1");
        writeFile(new File(subDir2, "file2.txt"), "file2");
        writeFile(new File(subDir2, "file3.log"), "file3");

        FileUtil.deleteRecursively(dir.toPath());
        assertFalse(dir.exists());
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter w = new FileWriter(file)) {
            w.write(content);
        }
    }
}
