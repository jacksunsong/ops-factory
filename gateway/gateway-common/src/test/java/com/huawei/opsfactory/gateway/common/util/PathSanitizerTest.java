/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Path;

/**
 * Test coverage for Path Sanitizer.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class PathSanitizerTest {

    /**
     * Tests is safe normal path.
     */
    @Test
    public void testIsSafe_normalPath() {
        Path base = Path.of("/home/user/data");
        assertTrue(PathSanitizer.isSafe(base, "file.txt"));
        assertTrue(PathSanitizer.isSafe(base, "subdir/file.txt"));
    }

    /**
     * Tests is safe traversal attack.
     */
    @Test
    public void testIsSafe_traversalAttack() {
        Path base = Path.of("/home/user/data");
        assertFalse(PathSanitizer.isSafe(base, "../etc/passwd"));
        assertFalse(PathSanitizer.isSafe(base, "../../secret"));
        assertFalse(PathSanitizer.isSafe(base, "subdir/../../etc/passwd"));
    }

    /**
     * Tests is safe null path.
     */
    @Test
    public void testIsSafe_nullPath() {
        Path base = Path.of("/home/user/data");
        assertFalse(PathSanitizer.isSafe(base, null));
    }

    /**
     * Tests sanitize filename normal.
     */
    @Test
    public void testSanitizeFilename_normal() {
        assertEquals("hello.txt", PathSanitizer.sanitizeFilename("hello.txt"));
        assertEquals("my-file_v2.pdf", PathSanitizer.sanitizeFilename("my-file_v2.pdf"));
    }

    /**
     * Tests sanitize filename removes path separators.
     */
    @Test
    public void testSanitizeFilename_removesPathSeparators() {
        // Now extracts basename after last separator
        assertEquals("passwd", PathSanitizer.sanitizeFilename("/etc/passwd"));
        assertEquals("system32", PathSanitizer.sanitizeFilename("C:\\Windows\\system32"));
    }

    /**
     * Tests sanitize filename removes traversal dots.
     */
    @Test
    public void testSanitizeFilename_removesTraversalDots() {
        String result = PathSanitizer.sanitizeFilename("../../../etc/passwd.txt");
        assertFalse("Should not contain '..'", result.contains(".."));
        assertTrue("Should contain 'passwd.txt'", result.contains("passwd.txt"));
    }

    /**
     * Tests sanitize filename removes special chars.
     */
    @Test
    public void testSanitizeFilename_removesSpecialChars() {
        assertEquals("file_name_.txt", PathSanitizer.sanitizeFilename("file name!.txt"));
    }

    /**
     * Tests sanitize filename null.
     */
    @Test
    public void testSanitizeFilename_null() {
        assertEquals("unnamed", PathSanitizer.sanitizeFilename(null));
    }

    /**
     * Tests sanitize filename chinese characters.
     */
    @Test
    public void testSanitizeFilename_chineseCharacters() {
        String result = PathSanitizer.sanitizeFilename("测试文件.txt");
        assertTrue(result.contains("测试文件"));
        assertTrue(result.endsWith(".txt"));
    }
}
