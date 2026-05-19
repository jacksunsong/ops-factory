/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;
import com.huawei.opsfactory.operationintelligence.qos.dv.DvClient;
import com.huawei.opsfactory.operationintelligence.qos.model.CallChainTree;
import com.huawei.opsfactory.operationintelligence.qos.model.ChainTypeConfig;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.qos.parser.TimeSplitStrategy;
import com.huawei.opsfactory.operationintelligence.qos.store.CallChainStore;
import com.huawei.opsfactory.operationintelligence.qos.store.ChainTypeConfigStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Call Chain Service.
 * Main service for call chain mining operations.
 *
 * @author call-chain
 * @since 2026-05-14
 */
@Service
public class CallChainService {

    private static final Logger log = LoggerFactory.getLogger(CallChainService.class);

    private final OperationIntelligenceProperties properties;
    private final DvClient dvClient;
    private final CallChainBuilder chainBuilder;
    private final CallChainStore chainStore;
    private final ChainTypeConfigStore configStore;
    private final TimeSplitStrategy timeSplitStrategy;

    /**
     * Call Chain Service.
     *
     * @param properties the properties
     * @param dvClient the DV client
     * @param chainBuilder the chain builder
     * @param chainStore the chain store
     * @param configStore the config store
     * @param timeSplitStrategy the time split strategy
     */
    public CallChainService(OperationIntelligenceProperties properties,
                            DvClient dvClient,
                            CallChainBuilder chainBuilder,
                            CallChainStore chainStore,
                            ChainTypeConfigStore configStore,
                            TimeSplitStrategy timeSplitStrategy) {
        this.properties = properties;
        this.dvClient = dvClient;
        this.chainBuilder = chainBuilder;
        this.chainStore = chainStore;
        this.configStore = configStore;
        this.timeSplitStrategy = timeSplitStrategy;
    }

    /**
     * Query call chain by conditions.
     *
     * @param solutionType the solution type (for DV environment selection)
     * @param conditions the list of conditions (each containing conditionKey and conditionValue)
     * @param startTime the start time in milliseconds
     * @param endTime the end time in milliseconds
     * @return the call chain tree
     */
    public Mono<CallChainTree> queryCallChain(String solutionType,
                                               List<Map<String, String>> conditions,
                                               long startTime,
                                               long endTime) {
        return Mono.fromCallable(() -> doQueryCallChain(solutionType, conditions, startTime, endTime))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Internal implementation of query call chain.
     */
    private CallChainTree doQueryCallChain(String solutionType,
                                          List<Map<String, String>> conditions,
                                          long startTime,
                                          long endTime) {
        log.info("Querying call chain with solutionType={}, {} conditions, timeRange=[{}, {}]",
            solutionType, conditions.size(),
            Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime));

        // Determine chainType by matching conditionKey with config
        String chainType = determineChainType(conditions);
        if (chainType == null) {
            log.warn("No matching chain type found for conditions");
            return createEmptyTree(null, conditions, startTime, endTime);
        }

        log.info("Determined chainType: {}", chainType);

        // Get config
        ChainTypeConfig config = getConfigByChainType(chainType);
        String conditionKey = config != null ? config.getConditionKey() : conditions.get(0).get("conditionKey");

        // Fetch entry logs (seqNo=1) with pagination and time splitting
        List<TraceLogRecord> entryLogs = fetchEntryLogsWithSplit(solutionType, chainType,
            conditionKey, conditions, config, startTime, endTime);

        if (entryLogs.isEmpty()) {
            log.info("No entry logs found for query");
            return createEmptyTree(chainType, conditions, startTime, endTime);
        }
        // Extract TraceIDs
        Set<String> traceIds = entryLogs.stream()
            .map(TraceLogRecord::getTraceId)
            .collect(Collectors.toSet());

        log.info("Found {} unique TraceIDs", traceIds.size());

        // Fetch complete call chains for each TraceID
        // entryLogs already contains entry logs (seqNo=1) from fetchEntryLogsWithSplit
        // fetchByTraceId returns complete trace including entry logs, so don't add entryLogs to allLogs
        List<TraceLogRecord> allLogs = new ArrayList<>();
        int querySize = properties.getCallChain().getQuerySize();
        for (String traceId : traceIds) {
            log.warn("Fetching logs for TraceID: {}", traceId);
            List<TraceLogRecord> traceLogs = dvClient.fetchByTraceId(solutionType, traceId, startTime, endTime, querySize);
            allLogs.addAll(traceLogs);
        }

        log.info("Fetched {} total trace log records", allLogs.size());

        // Use first condition for tree building
        Map<String, String> primaryCondition = conditions.get(0);
        String conditionValue = primaryCondition.get("conditionValue");

        // Build call chain tree
        CallChainTree tree = chainBuilder.build(chainType, conditionKey, conditionValue,
            allLogs, allLogs.size());

        // Set conditions
        List<CallChainTree.Condition> treeConditions = conditions.stream()
            .map(cond -> {
                CallChainTree.Condition c = new CallChainTree.Condition();
                c.setConditionKey(cond.get("conditionKey"));
                c.setConditionValue(cond.get("conditionValue"));
                return c;
            })
            .collect(Collectors.toList());
        tree.setConditions(treeConditions);

        // Set query time range
        CallChainTree.QueryTimeRange timeRange = new CallChainTree.QueryTimeRange();
        timeRange.setStartTime(formatTime(startTime));
        timeRange.setEndTime(formatTime(endTime));
        tree.setQueryTimeRange(timeRange);

        // Save to store
        chainStore.save(tree);

        return tree;
    }

    /**
     * Determine chain type by matching conditionKey with config conditionKey.
     */
    private String determineChainType(List<Map<String, String>> conditions) {
        List<ChainTypeConfig> configs = configStore.loadAll();

        for (Map<String, String> condition : conditions) {
            String conditionKey = condition.get("conditionKey");
            if (conditionKey == null) {
                continue;
            }

            // Find config where conditionKey matches
            for (ChainTypeConfig config : configs) {
                if (conditionKey.equals(config.getConditionKey())) {
                    return config.getChainType();
                }
            }
        }

        return null;
    }

    /**
     * Get config by chain type.
     */
    private ChainTypeConfig getConfigByChainType(String chainType) {
        return configStore.getByChainType(chainType);
    }

    /**
     * Fetch entry logs with time range splitting support.
     */
    private List<TraceLogRecord> fetchEntryLogsWithSplit(String solutionType,
                                                          String chainType,
                                                          String conditionKey,
                                                          List<Map<String, String>> conditions,
                                                          ChainTypeConfig config,
                                                          long startTime,
                                                          long endTime) {
        List<TraceLogRecord> allLogs = new ArrayList<>();
        int querySize = properties.getCallChain().getQuerySize();

        // Initial query
        List<TraceLogRecord> initialLogs = dvClient.fetchTraceLogEntries(solutionType,
            chainType, conditionKey, conditions, config, startTime, endTime, querySize);

        allLogs.addAll(initialLogs);

        // Check if we hit the limit and need time splitting
        if (initialLogs.size() >= properties.getCallChain().getQueryLimit()) {
            log.warn("Query returned {} results (at limit), applying time splitting", initialLogs.size());
            List<TimeSplitStrategy.TimeRange> ranges = timeSplitStrategy.splitIfNeeded(startTime, endTime, initialLogs.size());

            for (TimeSplitStrategy.TimeRange range : ranges) {
                List<TraceLogRecord> rangeLogs = dvClient.fetchTraceLogEntries(solutionType,
                    chainType, conditionKey, conditions, config, range.startTime(), range.endTime(), querySize);
                allLogs.addAll(rangeLogs);

                // If still hitting limit, further degrade
                if (rangeLogs.size() >= properties.getCallChain().getQueryLimit() && timeSplitStrategy.canDegradeFurther(range.duration())) {
                    long degradedSplitMs = timeSplitStrategy.getNextDegradeSplitMs(range.duration());
                    List<TimeSplitStrategy.TimeRange> degradedRanges = timeSplitStrategy.split(
                        range.startTime(), range.endTime(), degradedSplitMs);
                    for (TimeSplitStrategy.TimeRange degradedRange : degradedRanges) {
                        List<TraceLogRecord> degradedLogs = dvClient.fetchTraceLogEntries(solutionType,
                                chainType, conditionKey, conditions, config,
                                degradedRange.startTime(), degradedRange.endTime(), querySize);
                        allLogs.addAll(degradedLogs);
                    }
                }
            }
        }

        // Filter to only seqNo=1 entry logs
        return allLogs.stream()
            .filter(log -> "1".equals(log.getSeqNo()))
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Import chain type configurations from text content.
     *
     * @param content the text content
     * @return number of configs imported
     */
    public int importChainTypeConfigs(String content) {
        List<ChainTypeConfig> configs = parseChainTypeConfigs(content);
        configStore.importConfigs(configs);
        log.info("Imported {} chain type configs", configs.size());
        return configs.size();
    }

    /**
     * Parse chain type configs from text content.
     */
    private List<ChainTypeConfig> parseChainTypeConfigs(String content) {
        return content.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(line -> line.split("\\|", 7))
            .filter(parts -> parts.length >= 3)
            .map(parts -> {
                ChainTypeConfig config = new ChainTypeConfig();
                config.setChainType(parts[0].trim());
                config.setDescription(parts[1].trim());
                config.setConditionKey(parts[2].trim());
                if (parts.length > 3) {
                    config.setExtractFields(parts[3].trim());
                }
                if (parts.length > 4) {
                    config.setClassifyField(parts[4].trim());
                }
                if (parts.length > 5) {
                    config.setSubClassifyField(parts[5].trim());
                }
                config.setEnabled(true);
                return config;
            })
            .toList();
    }

    /**
     * Create empty call chain tree.
     */
    private CallChainTree createEmptyTree(String chainType, List<Map<String, String>> conditions,
                                          long startTime, long endTime) {
        CallChainTree tree = new CallChainTree();
        tree.setChainType(chainType);

        // Convert conditions to tree format
        List<CallChainTree.Condition> treeConditions = conditions.stream()
            .map(cond -> {
                CallChainTree.Condition c = new CallChainTree.Condition();
                c.setConditionKey(cond.get("conditionKey"));
                c.setConditionValue(cond.get("conditionValue"));
                return c;
            })
            .collect(Collectors.toList());
        tree.setConditions(treeConditions);

        tree.setFlows(new ArrayList<>());
        tree.setTotalCount(0L);

        CallChainTree.QueryTimeRange timeRange = new CallChainTree.QueryTimeRange();
        timeRange.setStartTime(formatTime(startTime));
        timeRange.setEndTime(formatTime(endTime));
        tree.setQueryTimeRange(timeRange);

        return tree;
    }

    /**
     * Format timestamp to string.
     */
    private String formatTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
