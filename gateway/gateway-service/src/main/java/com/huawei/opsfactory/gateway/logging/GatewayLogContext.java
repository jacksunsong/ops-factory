/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.logging;

import org.apache.logging.log4j.ThreadContext;

import java.util.function.Supplier;

/**
 * Utility that temporarily sets and restores Log4j ThreadContext entries for request logging.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class GatewayLogContext {
    private GatewayLogContext() {
    }

    /**
     * Runs an action with the given request and user context set in the MDC.
     *
     * @param requestId runs an action with the given request and user context set in the MDC
     * @param userId runs an action with the given request and user context set in the MDC
     * @param action runs an action with the given request and user context set in the MDC
     */
    public static void run(String requestId, String userId, Runnable action) {
        run(requestId, userId, null, action);
    }

    /**
     * Runs an action with the given request, user, and session context set in the MDC.
     *
     * @param requestId runs an action with the given request, user, and session context set in the MDC
     * @param userId runs an action with the given request, user, and session context set in the MDC
     * @param sessionId runs an action with the given request, user, and session context set in the MDC
     * @param action runs an action with the given request, user, and session context set in the MDC
     */
    public static void run(String requestId, String userId, String sessionId, Runnable action) {
        String previousRequestId = ThreadContext.get("requestId");
        String previousUserId = ThreadContext.get("userId");
        String previousSessionId = ThreadContext.get("sessionId");
        try {
            putIfText("requestId", requestId);
            putIfText("userId", userId);
            putIfText("sessionId", sessionId);
            action.run();
        } finally {
            restore("requestId", previousRequestId);
            restore("userId", previousUserId);
            restore("sessionId", previousSessionId);
        }
    }

    /**
     * Calls a supplier with the given request and user context set in the MDC.
     *
     * @param requestId calls a supplier with the given request and user context set in the MDC
     * @param userId calls a supplier with the given request and user context set in the MDC
     * @param action calls a supplier with the given request and user context set in the MDC
     * @return the calls a supplier with the given request and user context set in the MDC
     */
    public static <T> T call(String requestId, String userId, Supplier<T> action) {
        return call(requestId, userId, null, action);
    }

    /**
     * Calls a supplier with the given request, user, and session context set in the MDC.
     *
     * @param requestId calls a supplier with the given request, user, and session context set in the MDC
     * @param userId calls a supplier with the given request, user, and session context set in the MDC
     * @param sessionId calls a supplier with the given request, user, and session context set in the MDC
     * @param action calls a supplier with the given request, user, and session context set in the MDC
     * @return the calls a supplier with the given request, user, and session context set in the MDC
     */
    public static <T> T call(String requestId, String userId, String sessionId, Supplier<T> action) {
        String previousRequestId = ThreadContext.get("requestId");
        String previousUserId = ThreadContext.get("userId");
        String previousSessionId = ThreadContext.get("sessionId");
        try {
            putIfText("requestId", requestId);
            putIfText("userId", userId);
            putIfText("sessionId", sessionId);
            return action.get();
        } finally {
            restore("requestId", previousRequestId);
            restore("userId", previousUserId);
            restore("sessionId", previousSessionId);
        }
    }

    private static void putIfText(String key, String value) {
        if (value == null || value.isBlank()) {
            ThreadContext.remove(key);
        } else {
            ThreadContext.put(key, value);
        }
    }

    private static void restore(String key, String previousValue) {
        if (previousValue == null || previousValue.isBlank()) {
            ThreadContext.remove(key);
        } else {
            ThreadContext.put(key, previousValue);
        }
    }
}
