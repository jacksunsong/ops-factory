/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import java.nio.file.Path;

/**
 * Path sanitization utility for security.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class PathSanitizer {
    private PathSanitizer() {
    }

    /**
     * Checks that the resolved path stays within the allowed base directory.
     *
     * @param base allowed base directory
     * @param relativePath relative path to validate against the base
     * @return true if the resolved path is within the base directory
     */
    public static boolean isSafe(Path base, String relativePath) {
        if (relativePath == null || relativePath.contains("..")) {
            return false;
        }
        Path resolved = base.resolve(relativePath).normalize();
        return resolved.startsWith(base.normalize());
    }

    /**
     * Sanitizes a filename by removing path separators and unsupported characters.
     *
     * @param filename raw filename to sanitize
     * @return a safe filename containing only alphanumeric, dot, dash, underscore, and CJK characters
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        // Extract just the basename (after last separator)
        String basename = filename;
        int lastSlash = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            basename = basename.substring(lastSlash + 1);
        }
        // Remove non-safe characters, keeping alphanumeric, dot, dash, underscore, CJK
        String sanitized = basename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
        // Collapse consecutive dots to prevent ".." traversal patterns
        sanitized = sanitized.replaceAll("\\.{2,}", ".");
        // Strip leading dots/underscores
        sanitized = sanitized.replaceAll("^[._]+", "");
        if (sanitized.isEmpty()) {
            return "unnamed";
        }
        return sanitized;
    }
}
