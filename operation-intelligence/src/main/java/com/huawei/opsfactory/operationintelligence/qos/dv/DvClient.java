/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.model.AlarmInfo;
import com.huawei.opsfactory.operationintelligence.qos.model.PerformanceDataResult;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;

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

    private final OperationIntelligenceProperties properties;

    private final int maxConnections;

    private final Duration requestTimeout;

    private final int queryLimit;

    private final ConcurrentHashMap<String, WebClient> clientCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ConnectionProvider> providerCache = new ConcurrentHashMap<>();

    /**
     * Dv Client.
     *
     * @param authService the authService
     * @param sslFactory the sslFactory
     * @param properties the properties
     */
    public DvClient(DvAuthService authService, DvSslContextFactory sslFactory,
                    OperationIntelligenceProperties properties) {
        this.authService = authService;
        this.sslFactory = sslFactory;
        this.properties = properties;

        this.maxConnections = 10;
        this.requestTimeout = Duration.ofSeconds(60);
        this.queryLimit = properties.getCallChain().getQueryLimit();
    }

    /**
     * Get DV environment by solution type (envCode).
     *
     * @param solutionType the solution type (envCode)
     * @return the DV environment info, or null if not found
     */
    private DvEnvironmentInfo getDvEnvironment(String solutionType) {
        var environments = properties.getQos().getDvEnvironments();
        if (environments == null || environments.isEmpty()) {
            return null;
        }

        for (var config : environments) {
            if (solutionType.equals(config.getAgentSolutionType())) {
                return new DvEnvironmentInfo(
                    config.getEnvCode(),
                    config.getAgentSolutionType(),
                    config.getServerUrl(),
                    config.getUtmUser(),
                    config.getUtmPassword(),
                    config.getCrtContent(),
                    config.getCrtFileName(),
                    config.getDns()
                );
            }
        }

        return null;
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
                .block(requestTimeout);

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
                .block(requestTimeout);

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
                .block(requestTimeout);

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
                long delayMs = (1L << retryCount) * 1000;
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
            ConnectionProvider provider = ConnectionProvider.builder("dv-" + url.hashCode())
                .maxConnections(maxConnections).build();
            providerCache.put(url, provider);
            HttpClient httpClient = HttpClient.create(provider)
                .secure(t -> t.sslContext(sslContext).handshakeTimeout(Duration.ofSeconds(10)))
                .responseTimeout(requestTimeout);
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

    // --- Call Chain: TraceLog Support ---

    /**
     * Fetch TraceLog entries with time range splitting support.
     *
     * @param solutionType the solution type (envCode)
     * @param chainType the chain type (BES/API/BPM/JOB)
     * @param conditionKey the primary condition key (for backward compatibility)
     * @param conditions the list of conditions (each with conditionKey and conditionValue)
     * @param config the chain type configuration
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @param querySize the query page size
     * @return list of trace log records
     */
    public List<TraceLogRecord> fetchTraceLogEntries(String solutionType,
                                                      String chainType,
                                                      String conditionKey,
                                                      List<Map<String, String>> conditions,
                                                      com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig config,
                                                      long startTime,
                                                      long endTime,
                                                      int querySize) {
        DvEnvironmentInfo env = getDvEnvironment(solutionType);
        if (env == null) {
            throw new IllegalArgumentException("No DV environment found for solutionType: " + solutionType);
        }
        return executeWithRetry(() -> doFetchTraceLogEntries(env, chainType, conditionKey, conditions, config,
            startTime, endTime, querySize), "fetchTraceLogEntries[" + env.getEnvCode() + "]");
    }

    /**
     * Fetch TraceLog by TraceID.
     *
     * @param solutionType the solution type (envCode)
     * @param traceId the trace ID
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @param querySize the query page size
     * @return list of trace log records
     */
    public List<TraceLogRecord> fetchByTraceId(String solutionType,
                                               String traceId,
                                               long startTime,
                                               long endTime,
                                               int querySize) {
        DvEnvironmentInfo env = getDvEnvironment(solutionType);
        if (env == null) {
            throw new IllegalArgumentException("No DV environment found for solutionType: " + solutionType);
        }
        return executeWithRetry(() -> doFetchByTraceId(env, traceId, startTime, endTime, querySize),
            "fetchByTraceId[" + env.getEnvCode() + "]");
    }

    /**
     * Internal implementation of fetching trace log entries.
     */
    private List<TraceLogRecord> doFetchTraceLogEntries(DvEnvironmentInfo env,
                                                          String chainType,
                                                          String conditionKey,
                                                          List<Map<String, String>> conditions,
                                                          com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig config,
                                                          long startTime,
                                                          long endTime,
                                                          int querySize) {
        try {
            WebClient webClient = getOrCreateWebClient(env);
            Map<String, String> headers = authService.buildAuthHeaders(env);

            String url = env.getServerUrl() + "/cmp/api/logmatrix/v1/logdata/tracelog";
            Map<String, Object> body = buildTraceLogQuery(chainType, conditionKey, conditions, config,
                env.getAgentSolutionType(), startTime, endTime, querySize);

            String jsonBody = MAPPER.writeValueAsString(body);
            String response = webClient.post()
                .uri(url)
                .headers(h -> headers.forEach(h::add))
                .body(Mono.just(jsonBody), String.class)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block(requestTimeout);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseTraceLogResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch tracelog from " + env.getServerUrl() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Internal implementation of fetching by trace ID.
     */
    private List<TraceLogRecord> doFetchByTraceId(DvEnvironmentInfo env,
                                                   String traceId,
                                                   long startTime,
                                                   long endTime,
                                                   int querySize) {
        try {
            WebClient webClient = getOrCreateWebClient(env);
            Map<String, String> headers = authService.buildAuthHeaders(env);

            String url = env.getServerUrl() + "/cmp/api/logmatrix/v1/logdata/tracelog";
            Map<String, Object> body = buildTraceLogQueryByTraceId(traceId, env.getAgentSolutionType(), startTime, endTime, querySize);

            String jsonBody = MAPPER.writeValueAsString(body);

            log.info("[TraceLog Request] URL: {}, TraceId: {}", url, traceId);

            String response = webClient.post()
                .uri(url)
                .headers(h -> headers.forEach(h::add))
                .body(Mono.just(jsonBody), String.class)
                .retrieve()
                .onStatus(
                    status -> !status.is2xxSuccessful(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(errorBody -> {
                            log.error("DV request failed with status {}", clientResponse.statusCode());
                            return new RuntimeException("DV request failed with status " + clientResponse.statusCode());
                        })
                )
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block(requestTimeout);

            log.info("[TraceLog Response] URL: {}, Status: {}", url, response != null && !response.isBlank() ? "OK" : "Empty");

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseTraceLogResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch tracelog by traceId from " + env.getServerUrl() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Build tracelog query request body.
     */
    private Map<String, Object> buildTraceLogQuery(String chainType,
                                                      String conditionKey,
                                                      List<Map<String, String>> conditions,
                                                      com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig config,
                                                      String agentSolutionType,
                                                      long startTime,
                                                      long endTime,
                                                      int querySize) {
        List<Map<String, Object>> sort = List.of(
            Map.of("fieldName", "Time", "order", "desc")
        );

        List<Map<String, Object>> customIndex = List.of(
            Map.of("solutionType", agentSolutionType, "logtype", "tracelog")
        );

        Map<String, Object> fieldCondition = new LinkedHashMap<>();
        Map<String, Object> boolCondition = new LinkedHashMap<>();
        Map<String, Object> must = new LinkedHashMap<>();

        // TraceID prefix filter
        must.put("TraceID", chainType + "*");

        // Build AppendInfo filter for entry logs
        // Start with seqNo=1, then append conditions based on conditionKeyOnAppendInfo config
        StringBuilder appendInfoFilter = new StringBuilder("*seqNo=1*");

        // Get condition keys that should be appended to AppendInfo
        String conditionKeyOnAppendInfo = config != null ? config.getConditionKeyOnAppendInfo() : null;
        List<String> appendInfoKeys = new ArrayList<>();
        if (conditionKeyOnAppendInfo != null && !conditionKeyOnAppendInfo.isEmpty()) {
            String[] keys = conditionKeyOnAppendInfo.split(",");
            for (String key : keys) {
                appendInfoKeys.add(key.trim());
            }
        }

        // Add all conditions to must clause
        if (conditions != null && !conditions.isEmpty()) {
            for (Map<String, String> condition : conditions) {
                String key = condition.get("conditionKey");
                String value = condition.get("conditionValue");
                if (key != null && value != null && !value.isEmpty()) {
                    // If this key should be appended to AppendInfo filter
                    if (appendInfoKeys.contains(key)) {
                        appendInfoFilter.append(key).append("=").append(value).append("*");
                    } else {
                        // Otherwise add as separate must clause
                        must.put(key, value);
                    }
                }
            }
        }

        // Set AppendInfo filter
        must.put("AppendInfo", appendInfoFilter.toString());

        boolCondition.put("must", must);
        fieldCondition.put("boolCondition", boolCondition);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sort", sort);
        body.put("solutionType", agentSolutionType);
        body.put("customIndex", customIndex);
        body.put("from", "0");
        body.put("size", String.valueOf(querySize));
        body.put("beginTime", formatTime(startTime));
        body.put("endTime", formatTime(endTime));
        body.put("timePattern", "yyyy-MM-dd HH:mm:ss.SSS");
        body.put("fieldCondition", fieldCondition);

        return body;
    }

    /**
     * Build tracelog query request body by TraceID.
     */
    private Map<String, Object> buildTraceLogQueryByTraceId(String traceId,
                                                             String agentSolutionType,
                                                             long startTime,
                                                             long endTime,
                                                             int querySize) {
        List<Map<String, Object>> sort = List.of(
            Map.of("fieldName", "Time", "order", "desc")
        );

        // Use LinkedHashMap to preserve field order
        Map<String, Object> customIndexItem = new LinkedHashMap<>();
        customIndexItem.put("logtype", "tracelog");
        customIndexItem.put("solutionType", agentSolutionType);
        List<Map<String, Object>> customIndex = List.of(customIndexItem);

        Map<String, Object> fieldCondition = new LinkedHashMap<>();
        Map<String, Object> boolCondition = new LinkedHashMap<>();
        Map<String, Object> must = new LinkedHashMap<>();

        // Exact TraceID match
        must.put("TraceID", traceId);

        boolCondition.put("must", must);
        fieldCondition.put("boolCondition", boolCondition);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sort", sort);
        body.put("solutionType", agentSolutionType);
        body.put("customIndex", customIndex);
        body.put("from", "0");
        body.put("size", String.valueOf(querySize));
        body.put("beginTime", formatTime(startTime));
        body.put("endTime", formatTime(endTime));
        body.put("timePattern", "yyyy-MM-dd HH:mm:ss.SSS");
        body.put("fieldCondition", fieldCondition);

        return body;
    }

    /**
     * Format timestamp to DV time format.
     */
    private String formatTime(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(java.time.ZoneOffset.UTC)
            .format(instant);
    }

    /**
     * Parse tracelog API response.
     */
    private List<TraceLogRecord> parseTraceLogResponse(String response) {
        List<TraceLogRecord> results = new ArrayList<>();
        if (response == null) {
            return results;
        }
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode logsNode = root.get("logs");
            if (logsNode == null || !logsNode.isArray()) {
                log.warn("No logs array found in tracelog response");
                return results;
            }

            for (JsonNode item : logsNode) {
                TraceLogRecord record = new TraceLogRecord();
                record.setTraceId(textVal(item, "TraceID"));
                record.setIp(textVal(item, "ServerIP"));
                record.setCluster(textVal(item, "ClusterType"));
                record.setLogMessage(textVal(item, "LogMessage"));
                record.setLogTime(textVal(item, "Time"));
                record.setCost(parseCost(textVal(item, "cost")));
                record.setMoi(textVal(item, "moi"));

                // Field priority: url > serviceName
                String url = safeValue(textVal(item, "url"));
                String serviceName = safeValue(textVal(item, "serviceName"));

                if (url != null) {
                    record.setUrl(url);
                } else if (serviceName != null) {
                    record.setServiceName(serviceName);
                    record.setOperationName(textVal(item, "operationName"));
                }

                // Parse top-level fields
                record.setMenuId(textVal(item, "menuId"));
                record.setOperatorId(textVal(item, "operatorId"));
                record.setJobDefinedId(textVal(item, "jobDefinedId"));

                // Parse AppendInfo
                String appendInfo = textVal(item, "AppendInfo");
                if (appendInfo != null) {
                    parseAppendInfo(record, appendInfo);
                }

                results.add(record);
            }
        } catch (Exception e) {
            log.warn("Failed to parse tracelog response: {}", e.getMessage());
        }
        return results;
    }

    /**
     * Parse AppendInfo into record.
     */
    private void parseAppendInfo(TraceLogRecord record, String appendInfo) {
        String[] parts = appendInfo.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                switch (key) {
                    case "seqNo" -> record.setSeqNo(value);
                    case "menuId" -> record.setMenuId(value);
                    case "busiCode" -> record.setBusiCode(value);
                    case "jobDefinedId" -> record.setJobDefinedId(value);
                    case "operatorId" -> record.setOperatorId(value);
                    case "processName" -> record.setProcessName(value);
                    case "elementName" -> record.setElementName(value);
                    case "elementType" -> record.setElementType(value);
                    case "topic" -> record.setTopic(value);
                    case "eventName" -> record.setEventName(value);
                    case "serviceName" -> {
                        if (record.getServiceName() == null) {
                            record.setServiceName(value);
                        }
                    }
                    case "operationName" -> {
                        if (record.getOperationName() == null) {
                            record.setOperationName(value);
                        }
                    }
                }
            }
        }
    }

    /**
     * Parse cost string to Long.
     */
    private Long parseCost(String cost) {
        if (cost == null || cost.isEmpty()) {
            return null;
        }
        try {
            String numeric = cost.replaceAll("[^0-9]", "");
            if (numeric.isEmpty()) {
                return null;
            }
            return Long.parseLong(numeric);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Return null if value is "null" string.
     */
    private String safeValue(String value) {
        return (value != null && !value.equals("null")) ? value : null;
    }
}
