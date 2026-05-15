/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.AlarmDetailData;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorDetailData;
import com.huawei.opsfactory.operationintelligence.qos.model.IndicatorNormalizeData;
import com.huawei.opsfactory.operationintelligence.qos.model.ProductConfigRule;
import com.huawei.opsfactory.operationintelligence.qos.store.AlarmDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorDetailDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.IndicatorNormalizeDataStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ProductConfigRuleStore;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Qos Service.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Service
public class QosService {

    private final QosCalculationService calculationService;

    private final ProductConfigRuleStore productConfigRuleStore;

    private final IndicatorNormalizeDataStore normalizeDataStore;

    private final IndicatorDetailDataStore detailDataStore;

    private final AlarmDetailDataStore alarmDetailDataStore;

    private final OperationIntelligenceProperties properties;

    /**
     * Qos Service.
     *
     * @param calculationService the calculationService
     * @param productConfigRuleStore the productConfigRuleStore
     * @param normalizeDataStore the normalizeDataStore
     * @param detailDataStore the detailDataStore
     * @param alarmDetailDataStore the alarmDetailDataStore
     * @param properties the properties
     */
    public QosService(QosCalculationService calculationService, ProductConfigRuleStore productConfigRuleStore,
        IndicatorNormalizeDataStore normalizeDataStore, IndicatorDetailDataStore detailDataStore,
        AlarmDetailDataStore alarmDetailDataStore, OperationIntelligenceProperties properties) {
        this.calculationService = calculationService;
        this.productConfigRuleStore = productConfigRuleStore;
        this.normalizeDataStore = normalizeDataStore;
        this.detailDataStore = detailDataStore;
        this.alarmDetailDataStore = alarmDetailDataStore;
        this.properties = properties;
    }

    /**
     * Gets the health indicator.
     *
     * @param envCode the envCode
     * @param startTime the startTime
     * @param endTime the endTime
     * @return the result
     */
    public List<Map<String, Object>> getHealthIndicator(String envCode, long startTime, long endTime) {
        List<IndicatorNormalizeData> data = normalizeDataStore.loadRange(startTime, endTime);
        Map<Long,
            List<IndicatorNormalizeData>> byTimestamp = data.stream()
                .filter(d -> envCode == null || envCode.equals(d.getEnvCode()))
                .filter(d -> d.getTimestamp() != null)
                .collect(Collectors.groupingBy(IndicatorNormalizeData::getTimestamp));

        ProductConfigRule weightRule = productConfigRuleStore.loadAll().stream().findFirst().orElse(null);
        BigDecimal wA = resolveWeight("A", weightRule);
        BigDecimal wP = resolveWeight("P", weightRule);
        BigDecimal wR = resolveWeight("R", weightRule);

        List<Map<String, Object>> results = new ArrayList<>();
        byTimestamp.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<IndicatorNormalizeData> items = entry.getValue();
            BigDecimal a = findValue(items, "A");
            BigDecimal p = findValue(items, "P");
            BigDecimal r = findValue(items, "R");
            BigDecimal hs = calculationService.calculateHealthScore(a != null ? a : BigDecimal.ZERO,
                p != null ? p : BigDecimal.ZERO, r != null ? r : BigDecimal.ZERO, wA, wP, wR);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", entry.getKey());
            point.put("value", hs.toPlainString());
            results.add(point);
        });
        return results;
    }

    /**
     * Gets the indicator detail.
     *
     * @param envCode the envCode
     * @param type the type
     * @param startTime the startTime
     * @param endTime the endTime
     * @param pageIndex the pageIndex
     * @param pageSize the pageSize
     * @return the result
     */
    public Map<String, Object> getIndicatorDetail(String envCode, String type, long startTime, long endTime,
        int pageIndex, int pageSize) {
        if (pageIndex < 1)
            pageIndex = 1;
        if (pageSize < 1 || pageSize > 1000)
            pageSize = 10;
        List<IndicatorDetailData> data = detailDataStore.loadRange(startTime, endTime);
        List<IndicatorDetailData> filtered = data.stream()
            .filter(d -> envCode == null || envCode.equals(d.getEnvCode()))
            .filter(d -> type == null || type.equals(d.getType()))
            .filter(d -> d.getTimestamp() != null && d.getTimestamp() >= startTime && d.getTimestamp() <= endTime)
            .collect(Collectors.toList());

        int total = filtered.size();
        int from = (pageIndex - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<IndicatorDetailData> page = from < total ? filtered.subList(from, to) : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("pageIndex", pageIndex);
        result.put("pageSize", pageSize);
        result.put("results", page);
        return result;
    }

    /**
     * Gets the product config rule.
     *
     * @param agentSolutionType the agentSolutionType
     * @return the result
     */
    public Optional<ProductConfigRule> getProductConfigRule(String agentSolutionType) {
        return productConfigRuleStore.loadAll()
            .stream()
            .filter(r -> agentSolutionType == null || agentSolutionType.equals(r.getAgentSolutionType()))
            .findFirst();
    }

    /**
     * Gets the alarm detail.
     *
     * @param envCode the envCode
     * @param startTime the startTime
     * @param endTime the endTime
     * @param pageIndex the pageIndex
     * @param pageSize the pageSize
     * @return the result
     */
    public Map<String, Object> getAlarmDetail(String envCode, long startTime, long endTime, int pageIndex,
        int pageSize) {
        if (pageIndex < 1)
            pageIndex = 1;
        if (pageSize < 1 || pageSize > 1000)
            pageSize = 10;
        List<AlarmDetailData> data = alarmDetailDataStore.loadRange(startTime, endTime);
        List<AlarmDetailData> filtered = data.stream()
            .filter(d -> envCode == null || envCode.equals(d.getEnvCode()))
            .filter(d -> d.getOccurUtc() != null && d.getOccurUtc() >= startTime && d.getOccurUtc() <= endTime)
            .collect(Collectors.toList());

        int total = filtered.size();
        int from = (pageIndex - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        List<AlarmDetailData> page = from < total ? filtered.subList(from, to) : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("pageIndex", pageIndex);
        result.put("pageSize", pageSize);
        result.put("results", page);
        return result;
    }

    /**
     * Gets the environments.
     *
     * @return the result
     */
    public List<Map<String, String>> getEnvironments() {
        return properties.getQos().getDvEnvironments().stream().map(env -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("envCode", env.getEnvCode());
            item.put("envName", env.getEnvName() != null ? env.getEnvName() : env.getEnvCode());
            item.put("agentSolutionType", env.getAgentSolutionType());
            item.put("productTypeName",
                env.getProductTypeName() != null ? env.getProductTypeName() : env.getAgentSolutionType());
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * Gets the contribution data.
     *
     * @param envCode the envCode
     * @param startTime the startTime
     * @param endTime the endTime
     * @return the result
     */
    public List<Map<String, Object>> getContributionData(String envCode, long startTime, long endTime) {
        List<IndicatorNormalizeData> data = normalizeDataStore.loadRange(startTime, endTime);
        List<IndicatorNormalizeData> filtered =
            data.stream().filter(d -> envCode == null || envCode.equals(d.getEnvCode())).collect(Collectors.toList());

        List<Map<String, Object>> results = new ArrayList<>();
        ProductConfigRule weightRule = productConfigRuleStore.loadAll().stream().findFirst().orElse(null);
        for (String type : List.of("A", "P", "R")) {
            List<BigDecimal> values = filtered.stream()
                .filter(d -> type.equals(d.getType()))
                .map(IndicatorNormalizeData::getIndicatorValue)
                .collect(Collectors.toList());
            BigDecimal avg = values.isEmpty() ? BigDecimal.ZERO
                : values.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal weight = resolveWeight(type, weightRule);
            BigDecimal contribution = avg.multiply(weight).setScale(4, java.math.RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("contribution", contribution);
            results.add(item);
        }
        return results;
    }

    /**
     * Gets the resource normalize.
     *
     * @param envCode the envCode
     * @param startTime the startTime
     * @param endTime the endTime
     * @return the result
     */
    public List<IndicatorNormalizeData> getResourceNormalize(String envCode, long startTime, long endTime) {
        List<IndicatorNormalizeData> data = normalizeDataStore.loadRange(startTime, endTime);
        return data.stream()
            .filter(d -> "R".equals(d.getType()))
            .filter(d -> envCode == null || envCode.equals(d.getEnvCode()))
            .collect(Collectors.toList());
    }

    private BigDecimal resolveWeight(String dimension, ProductConfigRule rule) {
        if (rule != null && rule.getHealthWeight() != null && !rule.getHealthWeight().isBlank()) {
            String[] parts = rule.getHealthWeight().split(",");
            if (parts.length == 3) {
                int idx = "A".equals(dimension) ? 0 : "P".equals(dimension) ? 1 : 2;
                try {
                    return new BigDecimal(parts[idx].trim());
                } catch (NumberFormatException e) {
                    // fall through to config default
                }
            }
        }
        OperationIntelligenceProperties.Qos.Weights weights = properties.getQos().getWeights();
        double val = "A".equals(dimension) ? weights.getAvailability()
            : "P".equals(dimension) ? weights.getPerformance() : weights.getResource();
        return BigDecimal.valueOf(val);
    }

    private BigDecimal findValue(List<IndicatorNormalizeData> items, String type) {
        return items.stream()
            .filter(d -> type.equals(d.getType()))
            .map(IndicatorNormalizeData::getIndicatorValue)
            .findFirst()
            .orElse(null);
    }
}
