/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.UserRole;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;

import reactor.core.publisher.Mono;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.Set;

/**
 * Catch-all proxy: forwards unmatched /agents/{agentId}/** requests to the goosed instance.
 * User-accessible routes: /system_info, /status
 * Admin-only routes: everything else (schedules, config/prompts, etc.)
 */
@RestController
@RequestMapping(value = "/gateway")
@Order(999)
public class CatchAllProxyController {
    private static final Set<String> USER_ACCESSIBLE_PATHS = Set.of("/system_info", "/status");

    private static final Set<String> REMOVED_CHAT_PATHS = Set.of("/reply", "/agent/reply", "/agent/stop", "/stop");

    private final InstanceManager instanceManager;

    private final GoosedProxy goosedProxy;

    /**
     * Creates the catch all proxy controller instance.
     *
     * @param instanceManager manages goosed process instances
     * @param goosedProxy forwards requests to goosed instances
     */
    public CatchAllProxyController(InstanceManager instanceManager, GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
    }

    /**
     * Forwards unmatched agent requests to the appropriate goosed instance.
     *
     * @param exchange current HTTP exchange containing request path and user context
     * @return Mono that completes when the proxy response has been written
     */
    @RequestMapping("/agents/{agentId}/**")
    public Mono<Void> catchAll(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        String query = exchange.getRequest().getURI().getRawQuery();

        // Remove /gateway prefix if present
        // Full path format: /gateway/agents/{agentId}/{remainder}
        // or: /agents/{agentId}/{remainder} (for direct method calls in tests)
        String pathToParse = path.startsWith("/gateway") ? path.substring("/gateway".length()) : path;

        // Extract agentId and the remainder path
        // Path format: /agents/{agentId}/{remainder}
        String[] parts = pathToParse.split("/", 4);
        if (parts.length < 4) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String agentId = parts[2];
        String remainderPath = "/" + parts[3];
        if (remainderPath.isEmpty()) {
            remainderPath = "/";
        }
        String proxyTarget = remainderPath;
        if (query != null && !query.isEmpty()) {
            proxyTarget = remainderPath + "?" + query;
        }

        if (isRemovedChatPath(remainderPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "legacy chat endpoint removed");
        }

        // Check if user has access (using path only, without query string)
        UserRole role = exchange.getAttribute(UserContextFilter.USER_ROLE_ATTR);
        boolean isAdmin = role != null && role.isAdmin();

        if (!isAdmin && !isUserAccessible(remainderPath)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin access required");
        }

        // All requests use the authenticated user's instance
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);

        final String target = proxyTarget;
        return instanceManager.getOrSpawn(agentId, userId)
            .flatMap(instance -> goosedProxy.proxy(exchange.getRequest(), exchange.getResponse(), instance.getPort(),
                target, instance.getSecretKey()));
    }

    private boolean isUserAccessible(String remainder) {
        for (String allowed : USER_ACCESSIBLE_PATHS) {
            if (remainder.equals(allowed) || remainder.startsWith(allowed + "/")
                || remainder.startsWith(allowed + "?")) {
                return true;
            }
        }
        return false;
    }

    private boolean isRemovedChatPath(String remainder) {
        String normalized = remainder != null && remainder.length() > 1 && remainder.endsWith("/")
            ? remainder.substring(0, remainder.length() - 1) : remainder;
        return REMOVED_CHAT_PATHS.contains(normalized);
    }
}
