/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.QueryCallChainRequest;
import com.huawei.opsfactory.operationintelligence.service.CallChainService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Call Chain Controller Test.
 *
 * @author call-chain
 * @since 2026-05-18
 */
@WebFluxTest(
    controllers = CallChainController.class,
    properties = {
        "operation-intelligence.secret-key=test",
        "operation-intelligence.call-chain.max-time-range-ms=1800000"
    }
)
class CallChainControllerTest {

    private static final String SECRET_KEY = "test";

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private CallChainService callChainService;

    @Test
    void testQueryCallChainSuccess() {
        CallChainTree tree = new CallChainTree();
        tree.setChainType("BES");
        tree.setTotalCount(100L);
        tree.setFlows(List.of());

        when(callChainService.queryCallChain(anyString(), anyList(), anyLong(), anyLong()))
            .thenReturn(Mono.just(tree));

        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746057600000L);
        request.setEndTime(1746058000000L);

        webClient.post()
            .uri("/operation-intelligence/call-chain/query")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.chainType").isEqualTo("BES")
            .jsonPath("$.totalCount").isEqualTo(100);
    }

    @Test
    void testQueryCallChainMissingSolutionType() {
        QueryCallChainRequest request = new QueryCallChainRequest();
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746057600000L);
        request.setEndTime(1746662400000L);

        webClient.post()
            .uri("/operation-intelligence/call-chain/query")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testQueryCallChainMissingCondition() {
        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        request.setCondition(List.of());
        request.setStartTime(1746057600000L);
        request.setEndTime(1746058000000L);

        webClient.post()
            .uri("/operation-intelligence/call-chain/query")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testQueryCallChainInvalidTimeRange() {
        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746662400000L);
        request.setEndTime(1746057600000L);

        webClient.post()
            .uri("/operation-intelligence/call-chain/query")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testQueryCallChainNoAuth() {
        QueryCallChainRequest request = new QueryCallChainRequest();
        request.setSolutionType("DigitalCRM.sit");
        QueryCallChainRequest.Condition condition = new QueryCallChainRequest.Condition();
        condition.setConditionKey("menuId");
        condition.setConditionValue("604015020");
        request.setCondition(List.of(condition));
        request.setStartTime(1746057600000L);
        request.setEndTime(1746662400000L);

        webClient.post()
            .uri("/operation-intelligence/call-chain/query")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized();
    }
}