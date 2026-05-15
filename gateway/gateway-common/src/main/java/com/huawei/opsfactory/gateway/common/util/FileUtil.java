/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File utility operations.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class FileUtil {
    private FileUtil() {
    }

    /**
     * Recursively delete a directory and all its contents.
     *
     * @param path file or directory path to delete
     * @throws IOException if an I/O error occurs during deletion
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
