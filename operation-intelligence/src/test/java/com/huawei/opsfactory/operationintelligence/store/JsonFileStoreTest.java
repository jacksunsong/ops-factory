/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorRawData;
import com.huawei.opsfactory.operationintelligence.qos.store.JsonFileStore;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

class JsonFileStoreTest {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // Use a recent timestamp that loadRange can match against file names
    private static final long BASE_TS = System.currentTimeMillis() - 60000;
    @TempDir
    Path tempDir;
    private JsonFileStore<IndicatorRawData> store;
    private Path storeDir;

    @BeforeEach
    void setUp() throws IOException {
        storeDir = tempDir.resolve("raw");
        Files.createDirectories(storeDir);

        store = new JsonFileStore<>(storeDir, "indicator_raw_data", new TypeReference<List<IndicatorRawData>>() {},
            true, 3600000L, 86400000L * 7);
        store.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
        });
    }

    @Test
    void appendAndLoad_singleRecord() {
        IndicatorRawData data = createRawData(BASE_TS, "ENV1");
        store.append(data);

        List<IndicatorRawData> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("ENV1", loaded.get(0).getEnvCode());
    }

    @Test
    void appendAllAndLoad_multipleRecords() {
        store.appendAll(List.of(createRawData(BASE_TS, "ENV1"), createRawData(BASE_TS + 1000, "ENV2"),
            createRawData(BASE_TS + 2000, "ENV3")));

        List<IndicatorRawData> loaded = store.loadAll();
        assertEquals(3, loaded.size());
    }

    @Test
    void loadRange_filtersByFileName() {
        store.appendAll(List.of(createRawData(BASE_TS, "ENV1"), createRawData(BASE_TS + 1000, "ENV2")));

        // loadRange filters by file timestamp, not record timestamp
        // Query within the file's range should return data
        List<IndicatorRawData> loaded = store.loadRange(BASE_TS - 3600000L, BASE_TS + 3600000L);
        assertFalse(loaded.isEmpty());
    }

    @Test
    void loadRange_emptyWhenOutOfRange() {
        store.append(createRawData(BASE_TS, "ENV1"));

        // Query a range far before the file timestamp
        List<IndicatorRawData> loaded = store.loadRange(0L, 1000L);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadAll_emptyWhenNoData() {
        // init() may create an empty active file, but loadAll returns empty list
        List<IndicatorRawData> loaded = store.loadAll();
        assertTrue(loaded.isEmpty());
    }

    private IndicatorRawData createRawData(long timestamp, String envCode) {
        IndicatorRawData data = new IndicatorRawData();
        data.setTimestamp(timestamp);
        data.setEnvCode(envCode);
        return data;
    }
}
