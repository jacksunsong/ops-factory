/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence;

import com.huawei.opsfactory.operationintelligence.qos.model.AlarmDetailData;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorDetailData;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorNormalizeData;
import com.huawei.opsfactory.operationintelligence.qos.model.ProductConfigRule;
import com.huawei.opsfactory.operationintelligence.qos.store.AlarmDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorNormalizeDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ProductConfigRuleStore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OperationIntelligenceHttpIntegrationTest {

    private static final String SECRET_KEY = "integration-secret";

    private static final Path DATA_ROOT = createTempDataRoot();

    private static final long BASE_TIME = System.currentTimeMillis();

    private static final long START_TIME = BASE_TIME - 60_000;

    private static final long END_TIME = BASE_TIME + 60_000;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private IndicatorNormalizeDataStore normalizeDataStore;

    @Autowired
    private IndicatorDetailDataStore detailDataStore;

    @Autowired
    private AlarmDetailDataStore alarmDetailDataStore;

    @Autowired
    private ProductConfigRuleStore productConfigRuleStore;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("operation-intelligence.secret-key", () -> SECRET_KEY);
        registry.add("operation-intelligence.data-root", DATA_ROOT::toString);
        registry.add("operation-intelligence.qos.enabled", () -> "false");
        registry.add("operation-intelligence.qos.dv-environments[0].env-code", () -> "ENV1");
        registry.add("operation-intelligence.qos.dv-environments[0].env-name", () -> "Environment One");
        registry.add("operation-intelligence.qos.dv-environments[0].agent-solution-type", () -> "TYPE1");
        registry.add("operation-intelligence.qos.dv-environments[0].product-type-name", () -> "Product One");
    }

    @AfterAll
    static void tearDown() throws IOException {
        deleteRecursively(DATA_ROOT);
    }

    private static IndicatorNormalizeData normalize(String envCode, String type, String value, long timestamp) {
        IndicatorNormalizeData data = new IndicatorNormalizeData();
        data.setEnvCode(envCode);
        data.setType(type);
        data.setIndicatorValue(new BigDecimal(value));
        data.setTimestamp(timestamp);
        return data;
    }

    private static IndicatorDetailData detail(String envCode, String type, String name, long timestamp) {
        IndicatorDetailData data = new IndicatorDetailData();
        data.setEnvCode(envCode);
        data.setType(type);
        data.setIndicatorName(name);
        data.setDn("dn-" + name);
        data.setTimestamp(timestamp);
        return data;
    }

    private static AlarmDetailData alarm(String envCode, String severity, long occurUtc) {
        AlarmDetailData data = new AlarmDetailData();
        data.setEnvCode(envCode);
        data.setSeverity(severity);
        data.setAlarmName("alarm-" + severity);
        data.setOccurUtc(occurUtc);
        return data;
    }

    private static ProductConfigRule productRule(String type, String healthWeight) {
        ProductConfigRule rule = new ProductConfigRule();
        rule.setAgentSolutionType(type);
        rule.setHealthWeight(healthWeight);
        return rule;
    }

    private static Path createTempDataRoot() {
        try {
            return Files.createTempDirectory("operation-intelligence-http-test-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test data root", e);
        }
    }

    private static void clearDataRoot() throws IOException {
        deleteRecursively(DATA_ROOT);
        Files.createDirectories(DATA_ROOT);
        Files.createDirectories(DATA_ROOT.resolve("qos").resolve("normalize"));
        Files.createDirectories(DATA_ROOT.resolve("qos").resolve("detail"));
        Files.createDirectories(DATA_ROOT.resolve("qos").resolve("raw"));
        Files.createDirectories(DATA_ROOT.resolve("qos").resolve("config"));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root))
            return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete " + path, e);
                }
            });
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        clearDataRoot();
        productConfigRuleStore.replaceAll(List.of(productRule("TYPE1", "0.5,0.3,0.2")));
        normalizeDataStore.appendAll(List.of(normalize("ENV1", "A", "0.90", BASE_TIME),
            normalize("ENV1", "P", "0.80", BASE_TIME), normalize("ENV1", "R", "0.70", BASE_TIME),
            normalize("ENV2", "A", "0.10", BASE_TIME), normalize("ENV1", "R", "0.60", BASE_TIME + 1000)));
        detailDataStore.appendAll(List.of(detail("ENV1", "A", "available", BASE_TIME),
            detail("ENV1", "P", "latency", BASE_TIME + 100), detail("ENV2", "A", "other", BASE_TIME)));
        alarmDetailDataStore.appendAll(List.of(alarm("ENV1", "critical", BASE_TIME),
            alarm("ENV1", "warning", BASE_TIME + 1000), alarm("ENV2", "major", BASE_TIME)));
    }

    @Test
    void actuatorHealth_isReachableWithoutSecret() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP");
    }

    @Test
    void qosEndpoints_requireSecret() {
        webTestClient.get()
            .uri("/operation-intelligence/qos/getEnvironments")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void getEnvironments_usesConfiguredRuntimeEnvironments() {
        webTestClient.get()
            .uri("/operation-intelligence/qos/getEnvironments")
            .header("x-secret-key", SECRET_KEY)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.results[0].envCode")
            .isEqualTo("ENV1")
            .jsonPath("$.results[0].envName")
            .isEqualTo("Environment One")
            .jsonPath("$.results[0].agentSolutionType")
            .isEqualTo("TYPE1")
            .jsonPath("$.results[0].productTypeName")
            .isEqualTo("Product One");
    }

    @Test
    void getHealthIndicator_readsRealStoreAndAppliesProductWeights() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getHealthIndicator")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "ENV1", "startTime", START_TIME, "endTime", END_TIME))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(2)
            .jsonPath("$.results[0].timestamp")
            .isEqualTo(BASE_TIME)
            .jsonPath("$.results[0].value")
            .isEqualTo("0.83")
            .jsonPath("$.results[1].timestamp")
            .isEqualTo(BASE_TIME + 1000)
            .jsonPath("$.results[1].value")
            .isEqualTo("0.12");
    }

    @Test
    void getContributionData_readsRealStoreAndFiltersEnvironment() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getContributionData")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("envCode", "ENV1", "startTime", START_TIME, "endTime", END_TIME))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.results[0].type")
            .isEqualTo("A")
            .jsonPath("$.results[0].contribution")
            .isEqualTo(0.45)
            .jsonPath("$.results[1].type")
            .isEqualTo("P")
            .jsonPath("$.results[1].contribution")
            .isEqualTo(0.24)
            .jsonPath("$.results[2].type")
            .isEqualTo("R")
            .jsonPath("$.results[2].contribution")
            .isEqualTo(0.13);
    }

    @Test
    void getIndicatorDetail_pagesAndFiltersRealDetailData() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getAvailableIndicatorDetail")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of("envCode", "ENV1", "startTime", START_TIME, "endTime", END_TIME, "pageIndex", 1, "pageSize", 1))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.total")
            .isEqualTo(1)
            .jsonPath("$.pageSize")
            .isEqualTo(1)
            .jsonPath("$.results[0].indicatorName")
            .isEqualTo("available");
    }

    @Test
    void getAlarmIndicatorDetail_pagesAndFiltersRealAlarmData() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getAlarmIndicatorDetail")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                Map.of("envCode", "ENV1", "startTime", START_TIME, "endTime", END_TIME, "pageIndex", 2, "pageSize", 1))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.total")
            .isEqualTo(2)
            .jsonPath("$.pageIndex")
            .isEqualTo(2)
            .jsonPath("$.results[0].severity")
            .isEqualTo("warning");
    }

    @Test
    void getProductConfigRule_readsRealConfigStore() {
        webTestClient.post()
            .uri("/operation-intelligence/qos/getProductConfigRule")
            .header("x-secret-key", SECRET_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("agentSolutionType", "TYPE1"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.result.agentSolutionType")
            .isEqualTo("TYPE1")
            .jsonPath("$.result.healthWeight")
            .isEqualTo("0.5,0.3,0.2");
    }
}
