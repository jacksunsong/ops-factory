/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Json File Store.
 *
 * @author x00000000
 * @since 2026-05-11
 */
public class JsonFileStore<T> {

    private static final Logger log = LoggerFactory.getLogger(JsonFileStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path directory;

    private final String basename;

    private final TypeReference<List<T>> typeRef;

    private final boolean rotating;

    private final long rotationIntervalMs;

    private final long retentionMs;

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Json File Store.
     *
     * @param directory the directory
     * @param basename the basename
     * @param typeRef the typeRef
     * @param rotating the rotating
     * @param rotationIntervalMs the rotationIntervalMs
     * @param retentionMs the retentionMs
     */
    public JsonFileStore(Path directory, String basename, TypeReference<List<T>> typeRef, boolean rotating,
        long rotationIntervalMs, long retentionMs) {
        this.directory = directory;
        this.basename = basename;
        this.typeRef = typeRef;
        this.rotating = rotating;
        this.rotationIntervalMs = rotationIntervalMs;
        this.retentionMs = retentionMs;
    }

    static long parseTimestampFromName(String fileName) {
        // basename_yyyyMMddHHmmss.json
        int lastUnderscore = fileName.lastIndexOf('_');
        int dot = fileName.lastIndexOf('.');
        if (lastUnderscore < 0 || dot < 0)
            return 0;
        String tsStr = fileName.substring(lastUnderscore + 1, dot);
        try {
            LocalDateTime ldt = LocalDateTime.parse(tsStr, TS_FORMAT);
            return ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * init.
     */
    public void init() {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            log.warn("Failed to create directory {}: {}", directory, e.getMessage());
        }
        if (!rotating) {
            Path file = directory.resolve(basename + ".json");
            if (!Files.exists(file)) {
                try {
                    MAPPER.writeValue(file.toFile(), List.of());
                } catch (IOException e) {
                    log.warn("Failed to init config file {}: {}", file, e.getMessage());
                }
            }
        }
    }

    /**
     * load All.
     *
     * @return the result
     */
    public List<T> loadAll() {
        rwLock.readLock().lock();
        try {
            if (!rotating) {
                return readFile(directory.resolve(basename + ".json"));
            }
            Path active = findActiveFile();
            if (active == null)
                return new ArrayList<>();
            return readFile(active);
        } catch (IOException e) {
            log.warn("Failed to load data for {}: {}", basename, e.getMessage());
            return new ArrayList<>();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * load Range.
     *
     * @param startMs the startMs
     * @param endMs the endMs
     * @return the result
     */
    public List<T> loadRange(long startMs, long endMs) {
        rwLock.readLock().lock();
        try {
            List<T> result = new ArrayList<>();
            List<Path> files = listDataFiles();
            for (Path file : files) {
                long fileTs = parseTimestampFromName(file.getFileName().toString());
                if (fileTs + rotationIntervalMs < startMs)
                    continue;
                if (fileTs > endMs)
                    continue;
                List<T> items = readFile(file);
                result.addAll(items);
            }
            return result;
        } catch (IOException e) {
            log.warn("Failed to load range for {}: {}", basename, e.getMessage());
            return new ArrayList<>();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * append.
     *
     * @param item the item
     */
    public void append(T item) {
        rwLock.writeLock().lock();
        try {
            if (rotating)
                rotateIfNeeded();
            Path target = rotating ? findOrCreateActiveFile() : directory.resolve(basename + ".json");
            List<T> items = readFile(target);
            items.add(item);
            writeFile(target, items);
        } catch (IOException e) {
            log.warn("Failed to append to {}: {}", basename, e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * append All.
     *
     * @param newItems the newItems
     */
    public void appendAll(List<T> newItems) {
        if (newItems == null || newItems.isEmpty())
            return;
        rwLock.writeLock().lock();
        try {
            if (rotating)
                rotateIfNeeded();
            Path target = rotating ? findOrCreateActiveFile() : directory.resolve(basename + ".json");
            List<T> items = readFile(target);
            items.addAll(newItems);
            writeFile(target, items);
        } catch (IOException e) {
            log.warn("Failed to append all to {}: {}", basename, e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * replace All.
     *
     * @param items the items
     */
    public void replaceAll(List<T> items) {
        rwLock.writeLock().lock();
        try {
            Path target = directory.resolve(basename + ".json");
            writeFile(target, items != null ? items : List.of());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * rotate If Needed.
     */
    public void rotateIfNeeded() {
        if (!rotating)
            return;
        try {
            Path active = findActiveFile();
            if (active == null)
                return;
            long fileTs = parseTimestampFromName(active.getFileName().toString());
            if (System.currentTimeMillis() - fileTs > rotationIntervalMs) {
                log.info("Rotating {} - active file {} is older than {}ms", basename, active.getFileName(),
                    rotationIntervalMs);
            }
        } catch (Exception e) {
            log.warn("Rotation check failed for {}: {}", basename, e.getMessage());
        }
    }

    /**
     * cleanup.
     */
    public void cleanup() {
        if (!rotating || retentionMs <= 0)
            return;
        rwLock.writeLock().lock();
        try {
            long cutoff = System.currentTimeMillis() - retentionMs;
            List<Path> files = listDataFiles();
            for (Path file : files) {
                long fileTs = parseTimestampFromName(file.getFileName().toString());
                if (fileTs < cutoff) {
                    Files.deleteIfExists(file);
                    log.info("Cleaned up expired file: {}", file.getFileName());
                }
            }
        } catch (Exception e) {
            log.warn("Cleanup failed for {}: {}", basename, e.getMessage());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private Path findActiveFile() throws IOException {
        List<Path> files = listDataFiles();
        return files.isEmpty() ? null : files.get(files.size() - 1);
    }

    private Path findOrCreateActiveFile() throws IOException {
        Path active = findActiveFile();
        if (active != null) {
            long fileTs = parseTimestampFromName(active.getFileName().toString());
            if (System.currentTimeMillis() - fileTs <= rotationIntervalMs) {
                return active;
            }
        }
        String ts = LocalDateTime.now(ZoneOffset.UTC).format(TS_FORMAT);
        Path newFile = directory.resolve(basename + "_" + ts + ".json");
        writeFile(newFile, List.of());
        return newFile;
    }

    private List<Path> listDataFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        String prefix = basename + "_";
        try (DirectoryStream<Path> stream =
            Files.newDirectoryStream(directory, entry -> entry.getFileName().toString().startsWith(prefix)
                && entry.getFileName().toString().endsWith(".json"))) {
            stream.forEach(files::add);
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return files;
    }

    private List<T> readFile(Path file) {
        try {
            if (!Files.exists(file))
                return new ArrayList<>();
            return MAPPER.readValue(file.toFile(), typeRef);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", file, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeFile(Path file, List<T> items) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), items);
        } catch (IOException e) {
            log.warn("Failed to write {}: {}", file, e.getMessage());
        }
    }
}
