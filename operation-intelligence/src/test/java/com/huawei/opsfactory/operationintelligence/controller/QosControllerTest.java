/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.service.QosService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WebFluxTest(QosController.class)
@Import(QosControllerTest.TestConfig.class)
class QosControllerTest {

    private static final String SECRET_KEY = "test";

    private static final long NOW = System.currentTimeMillis();

    private static final long ONE_HOUR = 3600_000L;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private QosService qosService;

    @BeforeEach
    void setUp() {
        reset(qosService);
    }

    @Test
    void toLong_numberInput() {
        assertEquals(Long.valueOf(123L), Long.valueOf(QosController.toLong(123)));
    }

    // --- toLong / toInt unit tests ---

    @Test
    void toLong_stringInput() {
        assertEquals(Long.valueOf(456L), Long.valueOf(QosController.toLong("456")));
    }

    @Test
    void toLong_invalidString_throws400() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> QosController.toLong("abc"));
    }

    @Test
    void toLong_null_throws400() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> QosController.toLong(null));
    }

    @Test
    void toInt_numberInput() {
        assertEquals(Integer.valueOf(10), Integer.valueOf(QosController.toInt(10)));
    }

    @Test
    void toInt_stringInput() {
        assertEquals(Integer.valueOf(20), Integer.valueOf(QosController.toInt("20")));
    }

    @Test
    void toInt_invalidString_throws400() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> QosController.toInt("abc"));
    }

    @Test
    void toInt_null_throws400() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> QosController.toInt(null));
    }

    @Test
    void getHealthIndicator_validRequest_returns200() {
        when(qosService.getHealthIndicator(eq("ENV1"), anyLong(), anyLong()))
            .thenReturn(List.of(Map.of("timestamp", 1000L, "value", "0.9")));

        webTestClient.post()
            .uri("/operation-intelligence/qos/getHealthIndicator")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "ENV1", "startTime", NOW - ONE_HOUR, "endTime", NOW))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.results")
            .isArray();
    }

    // --- HTTP endpoint tests ---

    @Test
    void getHealthIndicator_missingEnvCode_returns400() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getHealthIndicator")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("startTime", NOW - ONE_HOUR, "endTime", NOW))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void getHealthIndicator_invalidTimeRange_returns400() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getHealthIndicator")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "ENV1", "startTime", NOW, "endTime", NOW - ONE_HOUR))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void getHealthIndicator_exceedsMaxSpan_returns400() {
        long ninetyOneDays = 91L * 24 * 60 * 60 * 1000;
        webTestClient.post()
            .uri("/operation-intelligence/qos/getHealthIndicator")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "ENV1", "startTime", 1, "endTime", ninetyOneDays))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    void getHealthIndicator_noAuth_returns401() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getHealthIndicator")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "ENV1", "startTime", NOW - ONE_HOUR, "endTime", NOW))
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void getEnvironments_returns200() {
        when(qosService.getEnvironments()).thenReturn(List.of(Map.of("envCode", "ENV1", "envName", "Test")));

        webTestClient.get()
            .uri("/operation-intelligence/qos/getEnvironments")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.results")
            .isArray();
    }

    @Test
    void getEnvironments_noAuth_returns401() {
        webTestClient.get()
            .uri("/operation-intelligence/qos/getEnvironments")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void getProductConfigRule_found_returns200() {
        when(qosService.getProductConfigRule("TYPE1"))
            .thenReturn(Optional.of(new com.huawei.opsfactory.operationintelligence.qos.model.ProductConfigRule()));

        webTestClient.post()
            .uri("/operation-intelligence/qos/getProductConfigRule")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("agentSolutionType", "TYPE1"))
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void getProductConfigRule_notFound_returns404() {
        when(qosService.getProductConfigRule("UNKNOWN")).thenReturn(Optional.empty());

        webTestClient.post()
            .uri("/operation-intelligence/qos/getProductConfigRule")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("agentSolutionType", "UNKNOWN"))
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void getAlarmIndicatorDetail_validRequest_returns200() {
        Map<String, Object> alarmResult = new LinkedHashMap<>();
        alarmResult.put("total", 0);
        alarmResult.put("results", List.of());
        when(qosService.getAlarmDetail(eq("ENV1"), anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(alarmResult);

        webTestClient.post()
            .uri("/operation-intelligence/qos/getAlarmIndicatorDetail")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of("envCode", "ENV1", "startTime", NOW - ONE_HOUR, "endTime", NOW, "pageIndex", 1, "pageSize", 10))
            .exchange()
            .expectStatus()
            .isOk();
    }

    @TestConfiguration
    static class TestConfig {
        /**
         * qos Service.
         *
         * @return the result
         */
        @Bean
        public QosService qosService() {
            return mock(QosService.class);
        }

        /**
         * properties.
         *
         * @return the result
         */
        @Bean
        public OperationIntelligenceProperties properties() {
            OperationIntelligenceProperties props = new OperationIntelligenceProperties();
            props.setSecretKey(SECRET_KEY);
            return props;
        }
    }
}
