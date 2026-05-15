/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Test coverage for Json Util.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class JsonUtilTest {
    // ---- extractStringField ----

    /**
     * Tests extract string field simple field.
     */
    @Test
    public void testExtractStringField_simpleField() {
        String json = "{\"name\": \"alice\", \"age\": 30}";
        assertEquals("alice", JsonUtil.extractStringField(json, "name"));
    }

    /**
     * Tests extract string field field not found.
     */
    @Test
    public void testExtractStringField_fieldNotFound() {
        String json = "{\"name\": \"alice\"}";
        assertNull(JsonUtil.extractStringField(json, "missing"));
    }

    /**
     * Tests extract string field multiple field names first match.
     */
    @Test
    public void testExtractStringField_multipleFieldNames_firstMatch() {
        String json = "{\"session_id\": \"abc123\"}";
        assertEquals("abc123", JsonUtil.extractStringField(json, "session_id", "sessionId"));
    }

    /**
     * Tests extract string field multiple field names second match.
     */
    @Test
    public void testExtractStringField_multipleFieldNames_secondMatch() {
        String json = "{\"sessionId\": \"def456\"}";
        assertEquals("def456", JsonUtil.extractStringField(json, "session_id", "sessionId"));
    }

    /**
     * Tests extract string field empty value.
     */
    @Test
    public void testExtractStringField_emptyValue() {
        String json = "{\"key\": \"\"}";
        assertEquals("", JsonUtil.extractStringField(json, "key"));
    }

    /**
     * Tests extract string field no field names.
     */
    @Test
    public void testExtractStringField_noFieldNames() {
        String json = "{\"key\": \"value\"}";
        assertNull(JsonUtil.extractStringField(json));
    }

    /**
     * Tests extract string field multiple fields.
     */
    @Test
    public void testExtractStringField_multipleFields() {
        String json = "{\"a\": \"1\", \"b\": \"2\", \"c\": \"3\"}";
        assertEquals("2", JsonUtil.extractStringField(json, "b"));
    }

    /**
     * Tests extract string field no colon after key.
     */
    @Test
    public void testExtractStringField_noColonAfterKey() {
        // Malformed JSON - key present but no colon
        String json = "{\"key\" \"value\"}";
        assertNull(JsonUtil.extractStringField(json, "key"));
    }

    /**
     * Tests extract string field no quote after colon.
     */
    @Test
    public void testExtractStringField_noQuoteAfterColon() {
        // Numeric value - no string quotes
        String json = "{\"count\": 42}";
        assertNull(JsonUtil.extractStringField(json, "count"));
    }

    // ---- extractSessionId ----

    /**
     * Tests extract session id snake case.
     */
    @Test
    public void testExtractSessionId_snakeCase() {
        String json = "{\"session_id\": \"sess-001\", \"message\": \"hello\"}";
        assertEquals("sess-001", JsonUtil.extractSessionId(json));
    }

    /**
     * Tests extract session id camel case.
     */
    @Test
    public void testExtractSessionId_camelCase() {
        String json = "{\"sessionId\": \"sess-002\", \"message\": \"hello\"}";
        assertEquals("sess-002", JsonUtil.extractSessionId(json));
    }

    /**
     * Tests extract session id not present.
     */
    @Test
    public void testExtractSessionId_notPresent() {
        String json = "{\"message\": \"hello\"}";
        assertNull(JsonUtil.extractSessionId(json));
    }

    /**
     * Tests extract session id prefers snake case.
     */
    @Test
    public void testExtractSessionId_prefersSnakeCase() {
        // When both present, snake_case is checked first
        String json = "{\"session_id\": \"snake\", \"sessionId\": \"camel\"}";
        assertEquals("snake", JsonUtil.extractSessionId(json));
    }

    /**
     * Tests extract string field with whitespace.
     */
    @Test
    public void testExtractStringField_withWhitespace() {
        String json = "{ \"key\" :  \"value with spaces\" }";
        assertEquals("value with spaces", JsonUtil.extractStringField(json, "key"));
    }
}
