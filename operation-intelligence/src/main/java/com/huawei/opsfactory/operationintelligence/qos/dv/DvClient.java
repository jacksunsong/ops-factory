/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import com.huawei.opsfactory.operationintelligence.qos.model.AlarmInfo;
import com.huawei.opsfactory.operationintelligence.qos.model.PerformanceDataResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;

import jakarta.annotation.PreDestroy;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dv Client.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class DvClient {

    private static final Logger log = LoggerFactory.getLogger(DvClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_RETRIES = 3;

    private static final int ALARM_BATCH_SIZE = 500;

    private final DvAuthService authService;

    private final DvSslContextFactory sslFactory;

    private final ConcurrentHashMap<String, WebClient> clientCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ConnectionProvider> providerCache = new ConcurrentHashMap<>();

    /**
     * Dv Client.
     *
     * @param authService the authService
     * @param sslFactory the sslFactory
     */
    public DvClient(DvAuthService authService, DvSslContextFactory sslFactory) {
        this.authService = authService;
        this.sslFactory = sslFactory;
    }

    // --- MO 查询（性能数据前置接口） ---

    private static String textVal(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    /**
     * fetch Mos.
     *
     * @param env the env
     * @param dns the dns
     * @return the result
     */
    public List<String> fetchMos(DvEnvironmentInfo env, List<String> dns) {
        return executeWithRetry(() -> doFetchMos(env, dns), "fetchMos[" + env.getEnvCode() + "]");
    }

    // --- 11.3.1 性能指标查询 ---

    private List<String> doFetchMos(DvEnvironmentInfo env, List<String> dns) {
        try {
            WebClient webClient = getOrCreateWebClient(env);
            Map<String, String> headers = authService.buildAuthHeaders(env);

            String url = env.getServerUrl() + "/rest/eammimservice/v1/openapi/mit/mos";
            String jsonBody = MAPPER.writeValueAsString(dns);

            String response = webClient.post()
                .uri(url)
                .headers(h -> headers.forEach(h::add))
                .body(Mono.just(jsonBody), String.class)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(60));

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseChildren(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch MOs from " + env.getServerUrl() + ": " + e.getMessage(), e);
        }
    }

    /**
     * fetch Performance Data.
     *
     * @param env the env
     * @param moType the moType
     * @param measUnitKey the measUnitKey
     * @param dns the dns
     * @param startTime the startTime
     * @param endTime the endTime
     * @return the result
     */
    public List<PerformanceDataResult> fetchPerformanceData(DvEnvironmentInfo env, String moType, String measUnitKey,
        List<String> dns, long startTime, long endTime) {
        return executeWithRetry(() -> doFetchPerformanceData(env, moType, measUnitKey, dns, startTime, endTime),
            "fetchPerformanceData[" + env.getEnvCode() + "]");
    }

    // --- 11.3.2 当前告警查询（scroll） ---

    private List<PerformanceDataResult> doFetchPerformanceData(DvEnvironmentInfo env, String moType, String measUnitKey,
        List<String> dns, long startTime, long endTime) {
        try {
            WebClient webClient = getOrCreateWebClient(env);
            Map<String, String> headers = authService.buildAuthHeaders(env);

            String url = env.getServerUrl() + "/rest/dvpmservice/v1/openapi/monitor/history/data";

            Map<String, Object> timeRanges = new LinkedHashMap<>();
            timeRanges.put(String.valueOf(startTime), endTime);

            Map<String, Object> dnMap = new LinkedHashMap<>();
            for (String dn : dns) {
                dnMap.put(dn, Map.of());
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("moType", moType);
            body.put("measUnitKey", measUnitKey);
            body.put("timeRanges", timeRanges);
            body.put("dnOriginalValueMeasTypeCalTypes", dnMap);

            String jsonBody = MAPPER.writeValueAsString(body);

            String response = webClient.post()
                .uri(url)
                .headers(h -> headers.forEach(h::add))
                .body(Mono.just(jsonBody), String.class)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(60));

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parsePerformanceResult(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch performance data from " + env.getServerUrl() + " moType="
                + moType + ": " + e.getMessage(), e);
        }
    }

    /**
     * fetch Current Alarms.
     *
     * @param env the env
     * @param startTime the startTime
     * @param endTime the endTime
     * @param severities the severities
     * @param dns the dns
     * @return the result
     */
    public List<AlarmInfo> fetchCurrentAlarms(DvEnvironmentInfo env, long startTime, long endTime,
        List<String> severities, List<String> dns) {
        return executeWithRetry(() -> doFetchCurrentAlarms(env, startTime, endTime, severities, dns),
            "fetchCurrentAlarms[" + env.getEnvCode() + "]");
    }

    private List<AlarmInfo> doFetchCurrentAlarms(DvEnvironmentInfo env, long startTime, long endTime,
        List<String> severities, List<String> dns) {
        try {
            WebClient webClient = getOrCreateWebClient(env);
            Map<String, String> headers = authService.buildAuthHeaders(env);

            String url = env.getServerUrl() + "/rest/fault/v1/current-alarms/scroll";
            String jsonBody = MAPPER.writeValueAsString(buildAlarmQuery(startTime, endTime, severities, dns));

            String response = webClient.post()
                .uri(url)
                .headers(h -> headers.forEach(h::add))
                .body(Mono.just(jsonBody), String.class)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(60));

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseAlarms(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch alarms from " + env.getServerUrl() + ": " + e.getMessage(), e);
        }
    }

    // --- 11.8 通用重试机制 ---

    void sleepBeforeRetry(long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
    }

    // --- 内部工具方法 ---

    <T> T executeWithRetry(Supplier<T> action, String operationName) {
        int retryCount = 0;
        Exception lastException = null;
        while (retryCount <= MAX_RETRIES) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    break;
                }
                long delayMs = (long) Math.pow(2, retryCount) * 1000;
                log.warn("{} failed, retry {}/{} in {}ms: {}", operationName, retryCount, MAX_RETRIES, delayMs,
                    e.getMessage());
                try {
                    sleepBeforeRetry(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(operationName + " interrupted during retry", ie);
                }
            }
        }
        log.error("{} failed after {} retries: {}", operationName, MAX_RETRIES, lastException.getMessage());
        throw new RuntimeException(operationName + " failed after " + MAX_RETRIES + " retries", lastException);
    }

    private WebClient getOrCreateWebClient(DvEnvironmentInfo env) {
        return clientCache.computeIfAbsent(env.getServerUrl(), url -> {
            SslContext sslContext =
                sslFactory.createSslContext(env.getCrtContent(), env.getCrtFileName(), env.isStrictSsl());
            ConnectionProvider provider = ConnectionProvider.builder("dv-" + url.hashCode()).maxConnections(10).build();
            providerCache.put(url, provider);
            HttpClient httpClient = HttpClient.create(provider)
                .secure(t -> t.sslContext(sslContext).handshakeTimeout(Duration.ofSeconds(10)))
                .responseTimeout(Duration.ofSeconds(60));
            return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        });
    }

    /**
     * shutdown.
     */
    @PreDestroy
    public void shutdown() {
        clientCache.clear();
        providerCache.values().forEach(ConnectionProvider::dispose);
        providerCache.clear();
    }

    private Map<String, Object> buildAlarmQuery(long startTime, long endTime, List<String> severities,
        List<String> dns) {
        List<Map<String, Object>> filters = new ArrayList<>();

        Map<String, Object> timeFilter = new LinkedHashMap<>();
        timeFilter.put("name", "OCCURUTC");
        timeFilter.put("field", "OCCURUTC");
        timeFilter.put("operator", "BETWEEN");
        timeFilter.put("values", List.of(startTime, endTime));
        filters.add(timeFilter);

        if (severities != null && !severities.isEmpty()) {
            Map<String, Object> severityFilter = new LinkedHashMap<>();
            severityFilter.put("name", "SEVERITY");
            severityFilter.put("field", "SEVERITY");
            severityFilter.put("operator", "IN");
            severityFilter.put("values", severities);
            filters.add(severityFilter);
        }

        if (dns != null && !dns.isEmpty()) {
            Map<String, Object> dnFilter = new LinkedHashMap<>();
            dnFilter.put("name", "NATIVEMEDN");
            dnFilter.put("field", "NATIVEMEDN");
            dnFilter.put("operator", "IN");
            dnFilter.put("values", dns);
            filters.add(dnFilter);
        }

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("filters", filters);
        query.put("expression", "and");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("sort", List.of(Collections.singletonMap("field", "CSN")));
        body.put("fields", List.of("alarmId", "alarmName", "severity", "nativeMeDn", "meName", "occurUtc", "count",
            "moi", "additionalInformation"));
        body.put("size", ALARM_BATCH_SIZE);
        return body;
    }

    List<PerformanceDataResult> parsePerformanceResult(String response) {
        List<PerformanceDataResult> results = new ArrayList<>();
        if (response == null)
            return results;
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode resultNode = root.has("result") ? root.get("result") : root;
            JsonNode datas = resultNode.has("datas") ? resultNode.get("datas") : resultNode;
            if (datas.isArray()) {
                for (JsonNode item : datas) {
                    PerformanceDataResult r = new PerformanceDataResult();
                    r.setDn(textVal(item, "dn"));
                    r.setMoType(textVal(item, "neName"));
                    r.setNeName(textVal(item, "neName"));
                    r.setPeriod(item.has("period") ? item.get("period").asInt() : 0);
                    if (item.has("values") && item.get("values").isObject()) {
                        Map<String, String> vals = new LinkedHashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = item.get("values").fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            vals.put(entry.getKey(), entry.getValue().asText());
                        }
                        r.setValues(vals);
                    }
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse performance result: {}", e.getMessage());
        }
        return results;
    }

    List<AlarmInfo> parseAlarms(String response) {
        List<AlarmInfo> alarms = new ArrayList<>();
        if (response == null)
            return alarms;
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode hits = root.get("hits");
            if (hits == null || !hits.isArray())
                return alarms;
            for (JsonNode hit : hits) {
                AlarmInfo alarm = new AlarmInfo();
                alarm.setAlarmId(textVal(hit, "alarmId"));
                alarm.setAlarmName(textVal(hit, "alarmName"));
                alarm.setSeverity(textVal(hit, "severity"));
                alarm.setDn(textVal(hit, "nativeMeDn"));
                alarm.setMeName(textVal(hit, "meName"));
                alarm.setOccurUtc(hit.has("occurUtc") ? hit.get("occurUtc").asLong() : null);
                alarm.setCount(hit.has("count") ? hit.get("count").asInt() : 1);
                alarm.setMoi(textVal(hit, "moi"));
                alarm.setAdditionalInformation(textVal(hit, "additionalInformation"));
                alarms.add(alarm);
            }
        } catch (Exception e) {
            log.warn("Failed to parse alarm response: {}", e.getMessage());
        }
        return alarms;
    }

    List<String> parseChildren(String response) {
        List<String> children = new ArrayList<>();
        if (response == null)
            return children;
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode resultNode = root.has("result") ? root.get("result") : root;
            if (resultNode.isArray()) {
                for (JsonNode item : resultNode) {
                    if (item.has("children") && item.get("children").isArray()) {
                        for (JsonNode child : item.get("children")) {
                            children.add(child.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse MO children response: {}", e.getMessage());
        }
        return children;
    }
}
