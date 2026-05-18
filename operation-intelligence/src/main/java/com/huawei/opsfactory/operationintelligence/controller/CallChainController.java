/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.QueryCallChainRequest;
import com.huawei.opsfactory.operationintelligence.service.CallChainService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Call Chain Controller.
 * REST API for call chain mining operations.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@RestController
@RequestMapping("/operation-intelligence/call-chain")
public class CallChainController {

    private static final Logger log = LoggerFactory.getLogger(CallChainController.class);

    private final CallChainService callChainService;

    private final com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties properties;

    /**
     * Call Chain Controller.
     *
     * @param callChainService the call chain service
     * @param properties the properties
     */
    public CallChainController(CallChainService callChainService,
                               com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties properties) {
        this.callChainService = callChainService;
        this.properties = properties;
    }

    /**
     * Query call chain.
     *
     * @param request the request body
     * @return the call chain tree
     */
    @PostMapping("/query")
    public Mono<ResponseEntity<Map<String, Object>>> queryCallChain(@RequestBody QueryCallChainRequest request) {
        // 验证在响应式流外部完成（快速同步操作）
        if (request.getSolutionType() == null || request.getSolutionType().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "solutionType is required"));
        }

        if (request.getCondition() == null || request.getCondition().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "condition is required and must not be empty"));
        }

        for (QueryCallChainRequest.Condition condition : request.getCondition()) {
            if (condition.getConditionKey() == null || condition.getConditionValue() == null) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each condition must have conditionKey and conditionValue"));
            }
        }

        validateTimeRange(request.getStartTime(), request.getEndTime());

        // 转换为内部格式
        List<Map<String, String>> conditions = request.getCondition().stream()
            .map(c -> {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("conditionKey", c.getConditionKey());
                map.put("conditionValue", c.getConditionValue());
                return map;
            })
            .toList();

        // 响应式链式调用：查询 -> 转换 -> 返回
        return callChainService.queryCallChain(request.getSolutionType(), conditions,
                request.getStartTime(), request.getEndTime())
            .map(tree -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("chainType", tree.getChainType());
                response.put("conditions", tree.getConditions());
                response.put("totalCount", tree.getTotalCount());
                response.put("queryTimeRange", tree.getQueryTimeRange());
                response.put("flows", tree.getFlows());
                return ResponseEntity.ok(response);
            });
    }

    /**
     * Validate time range.
     */
    private void validateTimeRange(long startTime, long endTime) {
        if (startTime <= 0 || endTime <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "startTime and endTime are required and must be positive");
        }
        if (endTime <= startTime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "endTime must be greater than startTime");
        }

        long maxSpanMs = properties.getCallChain().getMaxTimeRangeMs();
        if (endTime - startTime > maxSpanMs) {
            long maxMinutes = maxSpanMs / 60000;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "time range must not exceed " + maxMinutes + " minutes");
        }
    }
}
