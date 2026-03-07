package com.huawei.opsfactory.exporter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class Jsons {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Jsons() {
    }

    public static Map<String, Object> asMap(String json) throws IOException {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    public static Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMapSafe(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asListOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return List.of();
    }

    public static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
