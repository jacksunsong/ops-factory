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
        return Mono.fromCallable(() -> {
            // Validate request
            if (request.getSolutionType() == null || request.getSolutionType().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "solutionType is required");
            }

            if (request.getCondition() == null || request.getCondition().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "condition is required and must not be empty");
            }

            // Validate condition has required fields
            for (QueryCallChainRequest.Condition condition : request.getCondition()) {
                if (condition.getConditionKey() == null || condition.getConditionValue() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each condition must have conditionKey and conditionValue");
                }
            }

            // Validate time range
            validateTimeRange(request.getStartTime(), request.getEndTime());

            // Convert to internal format
            List<Map<String, String>> conditions = request.getCondition().stream()
                .map(c -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("conditionKey", c.getConditionKey());
                    map.put("conditionValue", c.getConditionValue());
                    return map;
                })
                .toList();

            // Query call chain
            CallChainTree tree = callChainService.queryCallChain(
                request.getSolutionType(), conditions,
                request.getStartTime(), request.getEndTime()).block();

            // Build response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("chainType", tree.getChainType());
            response.put("conditions", tree.getConditions());
            response.put("totalCount", tree.getTotalCount());
            response.put("queryTimeRange", tree.getQueryTimeRange());
            response.put("flows", tree.getFlows());

            return ResponseEntity.ok(response);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
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

    /**
     * Parse long from object.
     */
    private long parseLong(Object val) {
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        if (val instanceof String) {
            try {
                return Long.parseLong((String) val);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid numeric value");
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid numeric value");
    }
}
