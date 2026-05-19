/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.parser;

import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Append Info Parser Test.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@ExtendWith(MockitoExtension.class)
class AppendInfoParserTest {

    private final AppendInfoParser parser = new AppendInfoParser();

    @Test
    void testParseBasic() {
        String appendInfo = "seqNo=1,url=https://example.com,cost=10ms";
        Map<String, String> result = parser.parse(appendInfo);

        assertEquals(3, result.size());
        assertEquals("1", result.get("seqNo"));
        assertEquals("https://example.com", result.get("url"));
        assertEquals("10ms", result.get("cost"));
    }

    @Test
    void testParseField() {
        String appendInfo = "seqNo=1.1,menuId=6013101010002,operatorId=user123";
        assertEquals("1.1", parser.parseSeqNo(appendInfo));
        assertEquals("6013101010002", parser.parseField(appendInfo, "menuId"));
        assertEquals("user123", parser.parseField(appendInfo, "operatorId"));
    }

    @Test
    void testParseEmpty() {
        Map<String, String> result = parser.parse("");
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void testParseNull() {
        Map<String, String> result = parser.parse(null);
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
