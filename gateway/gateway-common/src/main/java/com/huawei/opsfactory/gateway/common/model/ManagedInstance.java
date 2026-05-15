/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Managed instance model.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class ManagedInstance {

    private final String agentId;

    private final String userId;

    private final int port;

    private final long pid;

    private final String secretKey;

    /** Sessions that have been resumed (provider+extensions loaded) on this instance. */
    private final Set<String> resumedSessions = ConcurrentHashMap.newKeySet();

    private volatile Status status;

    private volatile long lastActivity;

    private volatile int restartCount = 0;

    private volatile long lastRestartTime = 0;

    private transient Process process;

    /**
     * Creates a managed instance descriptor.
     *
     * @param agentId agent instance identifier
     * @param userId user identifier
     * @param port allocated port number
     * @param pid native process identifier
     * @param process underlying OS process
     * @param secretKey secret key for authenticating with the goosed instance
     */
    public ManagedInstance(String agentId, String userId, int port, long pid, Process process, String secretKey) {
        this.agentId = agentId;
        this.userId = userId;
        this.port = port;
        this.pid = pid;
        this.secretKey = secretKey;
        this.process = process;
        this.status = Status.STARTING;
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * Builds a composite key from the given agent and user identifiers.
     *
     * @param agentId agent identifier
     * @param userId user identifier
     * @return composite key in the format agentId:userId
     */
    public static String buildKey(String agentId, String userId) {
        return agentId + ":" + userId;
    }

    /**
     * Gets the agent identifier of this managed instance.
     *
     * @return agent identifier
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Gets the user identifier of this managed instance.
     *
     * @return user identifier
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the port number assigned to this managed instance.
     *
     * @return allocated port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the process identifier (PID) of this managed instance.
     *
     * @return native process identifier
     */
    public long getPid() {
        return pid;
    }

    /**
     * Gets the secret key used for authenticating with this managed instance.
     *
     * @return secret key string
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Gets the current status of this managed instance.
     *
     * @return current instance status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets the status of this managed instance.
     *
     * @param status new status to set
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Gets the timestamp of the last activity on this managed instance.
     *
     * @return timestamp in milliseconds of the last activity
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Updates the last activity timestamp to the current time.
     */
    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    /**
     * Gets the underlying process of this managed instance.
     *
     * @return the underlying OS process
     */
    public Process getProcess() {
        return process;
    }

    /**
     * Marks a session as resumed on this instance.
     *
     * @param sessionId session identifier to mark as resumed
     */
    public void markSessionResumed(String sessionId) {
        resumedSessions.add(sessionId);
    }

    /**
     * Removes the session from the resumed sessions set.
     *
     * @param sessionId session identifier to remove from the resumed set
     */
    public void unmarkSessionResumed(String sessionId) {
        if (sessionId != null) {
            resumedSessions.remove(sessionId);
        }
    }

    /**
     * Checks whether the given session has already been resumed on this instance.
     *
     * @param sessionId session identifier to check
     * @return true if the session has been resumed on this instance
     */
    public boolean isSessionResumed(String sessionId) {
        return sessionId != null && resumedSessions.contains(sessionId);
    }

    /**
     * Gets the number of times this managed instance has been restarted.
     *
     * @return number of times the instance has been restarted
     */
    public int getRestartCount() {
        return restartCount;
    }

    /**
     * Sets the restart count for this managed instance.
     *
     * @param restartCount new restart count value
     */
    public void setRestartCount(int restartCount) {
        this.restartCount = restartCount;
    }

    /**
     * Resets the restart count to zero.
     */
    public void resetRestartCount() {
        this.restartCount = 0;
    }

    /**
     * Gets the timestamp of the last restart.
     *
     * @return timestamp in milliseconds of the last restart
     */
    public long getLastRestartTime() {
        return lastRestartTime;
    }

    /**
     * Sets the timestamp of the last restart.
     *
     * @param lastRestartTime timestamp in milliseconds of the last restart
     */
    public void setLastRestartTime(long lastRestartTime) {
        this.lastRestartTime = lastRestartTime;
    }

    /**
     * Gets the composite key for this managed instance.
     *
     * @return composite key in the format agentId:userId
     */
    public String getKey() {
        return buildKey(agentId, userId);
    }

    /**
     * Runtime status of a managed instance.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public enum Status {
        STARTING,
        RUNNING,
        STOPPED,
        ERROR
    }
}
