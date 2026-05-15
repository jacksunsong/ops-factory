/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.filter;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.common.model.UserRole;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.process.PrewarmService;

import reactor.core.publisher.Mono;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Web filter that resolves the authenticated user identity and role from request headers.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
@Order(3)
public class UserContextFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private static final String CHANNEL_WEBHOOK_PREFIX = "/gateway/channels/webhooks/";

    public static final String USER_ID_ATTR = "userId";

    public static final String USER_ROLE_ATTR = "userRole";

    private final PrewarmService prewarmService;

    private final GatewayProperties gatewayProperties;

    // Cached set for fast lookup; refreshed when the underlying list changes.
    private volatile Set<String> adminUserSet = Set.of();

    private volatile List<String> cachedAdminList = List.of();

    /**
     * Creates the user context filter instance.
     *
     * @param prewarmService service for pre-warming agent instances
     * @param gatewayProperties gateway configuration properties including admin user list
     */
    public UserContextFilter(PrewarmService prewarmService, GatewayProperties gatewayProperties) {
        this.prewarmService = prewarmService;
        this.gatewayProperties = gatewayProperties;
    }

    private Set<String> resolveAdminUserSet() {
        List<String> current = gatewayProperties.getAdminUsers();
        if (current != cachedAdminList) {
            cachedAdminList = current;
            adminUserSet = current == null ? Set.of() : new HashSet<>(current);
        }
        return adminUserSet;
    }

    private static boolean isSystemEndpoint(String path) {
        return path.equals("/status") || path.equals("/me") || path.equals("/config") || path.equals("/gateway/status")
            || path.equals("/gateway/me") || path.equals("/gateway/config");
    }

    private static boolean isTraceEndpoint(String path) {
        if (path.startsWith("/gateway/session-traces/")) {
            return true;
        }
        if (!path.startsWith("/gateway/agents/") || !path.endsWith("/trace")) {
            return false;
        }
        return path.substring("/gateway/agents/".length()).contains("/sessions/");
    }

    /**
     * Resolves the authenticated user identity and role from request headers.
     *
     * @param exchange current HTTP exchange
     * @param chain filter chain to continue processing
     * @return Mono that completes when filtering is done
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (path.startsWith(CHANNEL_WEBHOOK_PREFIX)) {
            return chain.filter(exchange);
        }

        String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            userId = request.getQueryParams().getFirst(GatewayConstants.QUERY_UID);
        }
        if (userId == null || userId.isBlank()) {
            // System endpoints don't require user context
            if (isSystemEndpoint(path)) {
                return chain.filter(exchange);
            }
            log.warn("Rejecting request path={} reason=missing-user-id", path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        UserRole role = UserRole.fromUserId(userId, resolveAdminUserSet());

        exchange.getAttributes().put(USER_ID_ATTR, userId);
        exchange.getAttributes().put(USER_ROLE_ATTR, role);
        ThreadContext.put("userId", userId);

        // Trigger pre-warm for authenticated users, except diagnostics that must not mutate runtime state.
        if (!isTraceEndpoint(path)) {
            prewarmService.onUserActivity(userId);
        }

        return chain.filter(exchange);
    }

    /**
     * Shared admin check — throws 403 if the current user is not an admin.
     *
     * @param exchange current HTTP exchange carrying user role attribute
     */
    public static void requireAdmin(ServerWebExchange exchange) {
        UserRole role = exchange.getAttribute(USER_ROLE_ATTR);
        if (role == null || !role.isAdmin()) {
            LoggerFactory.getLogger(UserContextFilter.class)
                .warn("Rejecting request path={} reason=admin-access-required userRole={}",
                    exchange.getRequest().getURI().getPath(), role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin access required");
        }
    }
}
