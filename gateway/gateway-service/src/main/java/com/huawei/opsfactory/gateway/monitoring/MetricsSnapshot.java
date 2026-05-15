/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.monitoring;

/**
 * One 30-second metrics collection snapshot.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class MetricsSnapshot {
    private long timestamp;

    private int activeInstances;

    private long totalTokens;

    private long totalSessions;

    private int requestCount;

    private double avgLatencyMs;

    private double avgTtftMs;

    private double p95LatencyMs;

    private double p95TtftMs;

    private long totalBytes;

    private int errorCount;

    private double tokensPerSec;

    /**
     * Creates the metrics snapshot instance.
     */
    public MetricsSnapshot() {
    }

    /**
     * Gets the snapshot timestamp in milliseconds since epoch.
     *
     * @return the snapshot timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the snapshot timestamp in milliseconds since epoch.
     *
     * @param timestamp the snapshot timestamp in milliseconds since epoch
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the number of active goosed instances.
     *
     * @return the number of active goosed instances
     */
    public int getActiveInstances() {
        return activeInstances;
    }

    /**
     * Sets the number of active goosed instances.
     *
     * @param activeInstances the number of active goosed instances
     */
    public void setActiveInstances(int activeInstances) {
        this.activeInstances = activeInstances;
    }

    /**
     * Gets the total number of tokens consumed across all sessions.
     *
     * @return the total number of tokens consumed across all sessions
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    /**
     * Sets the total number of tokens consumed across all sessions.
     *
     * @param totalTokens the total number of tokens consumed across all sessions
     */
    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    /**
     * Gets the total number of sessions across all instances.
     *
     * @return the total number of sessions across all instances
     */
    public long getTotalSessions() {
        return totalSessions;
    }

    /**
     * Sets the total number of sessions across all instances.
     *
     * @param totalSessions the total number of sessions across all instances
     */
    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }

    /**
     * Gets the number of requests in this collection window.
     *
     * @return the number of requests in this collection window
     */
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Sets the number of requests in this collection window.
     *
     * @param requestCount the number of requests in this collection window
     */
    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    /**
     * Gets the average request latency in milliseconds.
     *
     * @return the average request latency in milliseconds
     */
    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    /**
     * Sets the average request latency in milliseconds.
     *
     * @param avgLatencyMs the average request latency in milliseconds
     */
    public void setAvgLatencyMs(double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    /**
     * Gets the average time-to-first-token in milliseconds.
     *
     * @return the average time-to-first-token in milliseconds
     */
    public double getAvgTtftMs() {
        return avgTtftMs;
    }

    /**
     * Sets the average time-to-first-token in milliseconds.
     *
     * @param avgTtftMs the average time-to-first-token in milliseconds
     */
    public void setAvgTtftMs(double avgTtftMs) {
        this.avgTtftMs = avgTtftMs;
    }

    /**
     * Gets the p95 request latency in milliseconds.
     *
     * @return the p95 request latency in milliseconds
     */
    public double getP95LatencyMs() {
        return p95LatencyMs;
    }

    /**
     * Sets the p95 request latency in milliseconds.
     *
     * @param p95LatencyMs the p95 request latency in milliseconds
     */
    public void setP95LatencyMs(double p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    /**
     * Gets the p95 time-to-first-token in milliseconds.
     *
     * @return the p95 time-to-first-token in milliseconds
     */
    public double getP95TtftMs() {
        return p95TtftMs;
    }

    /**
     * Sets the p95 time-to-first-token in milliseconds.
     *
     * @param p95TtftMs the p95 time-to-first-token in milliseconds
     */
    public void setP95TtftMs(double p95TtftMs) {
        this.p95TtftMs = p95TtftMs;
    }

    /**
     * Gets the total number of bytes transferred in this window.
     *
     * @return the total number of bytes transferred in this window
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Sets the total number of bytes transferred in this window.
     *
     * @param totalBytes the total number of bytes transferred in this window
     */
    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    /**
     * Gets the number of error responses in this window.
     *
     * @return the number of error responses in this window
     */
    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Sets the number of error responses in this window.
     *
     * @param errorCount the number of error responses in this window
     */
    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    /**
     * Gets the token throughput rate per second.
     *
     * @return the token throughput rate per second
     */
    public double getTokensPerSec() {
        return tokensPerSec;
    }

    /**
     * Sets the token throughput rate per second.
     *
     * @param tokensPerSec the token throughput rate per second
     */
    public void setTokensPerSec(double tokensPerSec) {
        this.tokensPerSec = tokensPerSec;
    }
}
