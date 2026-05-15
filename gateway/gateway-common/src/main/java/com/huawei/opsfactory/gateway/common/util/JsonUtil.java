/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

/**
 * Lightweight JSON helpers for extracting values without a full parse.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class JsonUtil {
    private JsonUtil() {
    }

    /**
     * Extracts the value of a string field from a JSON body.
     * Handles both snake_case and camelCase field names.
     *
     * @param json raw JSON string to search
     * @param fieldNames candidate field names to look up (tried in order)
     * @return the extracted field value, or null if no match is found
     */
    public static String extractStringField(String json, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String key = "\"" + fieldName + "\"";
            int idx = json.indexOf(key);
            if (idx < 0) {
                continue;
            }
            int colonIdx = json.indexOf(':', idx + key.length());
            if (colonIdx < 0) {
                continue;
            }
            int startQuote = json.indexOf('"', colonIdx + 1);
            if (startQuote < 0) {
                continue;
            }
            int endQuote = json.indexOf('"', startQuote + 1);
            if (endQuote < 0) {
                continue;
            }
            return json.substring(startQuote + 1, endQuote);
        }
        return null;
    }

    /**
     * Extracts the session identifier from a JSON request body.
     *
     * @param json raw JSON string containing a session_id or sessionId field
     * @return the session identifier string, or null if not found
     */
    public static String extractSessionId(String json) {
        return extractStringField(json, "session_id", "sessionId");
    }
}
