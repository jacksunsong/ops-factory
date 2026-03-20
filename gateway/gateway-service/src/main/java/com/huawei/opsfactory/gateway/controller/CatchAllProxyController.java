package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.model.UserRole;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Catch-all proxy: forwards unmatched /agents/{agentId}/** requests to the goosed instance.
 * User-accessible routes: /system_info, /status
 * Admin-only routes: everything else (schedules, config/prompts, etc.)
 */
@RestController
@RequestMapping(value = "/ops-gateway")
@Order(999)
public class CatchAllProxyController {

    private static final Set<String> USER_ACCESSIBLE_PATHS = Set.of(
            "/system_info", "/status"
    );

    private final InstanceManager instanceManager;
    private final GoosedProxy goosedProxy;

    public CatchAllProxyController(InstanceManager instanceManager, GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
    }

    @RequestMapping("/agents/{agentId}/**")
    public Mono<Void> catchAll(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        String query = exchange.getRequest().getURI().getRawQuery();

<<<<<<< HEAD
        String gatewayPrefix = "/ops-gateway/agents/";
        if (!path.startsWith(gatewayPrefix)) {
=======
        // Remove /ops-gateway prefix if present
        // Full path format: /ops-gateway/agents/{agentId}/{remainder}
        // or: /agents/{agentId}/{remainder} (for direct method calls in tests)
        String pathToParse = path.startsWith("/ops-gateway")
                ? path.substring("/ops-gateway".length())
                : path;

        // Extract agentId and the remainder path
        // Path format: /agents/{agentId}/{remainder}
        String[] parts = pathToParse.split("/", 4);
        if (parts.length < 4) {
>>>>>>> 3df728d532d2949dab8297fcbadf3e5448a0637c
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String afterAgents = path.substring(gatewayPrefix.length());
        int slashIndex = afterAgents.indexOf('/');
        if (slashIndex < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        String agentId = afterAgents.substring(0, slashIndex);
        String remainderPath = afterAgents.substring(slashIndex);
        if (remainderPath.isEmpty()) {
            remainderPath = "/";
        }
        String proxyTarget = remainderPath;
        if (query != null && !query.isEmpty()) {
            proxyTarget = remainderPath + "?" + query;
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
                .flatMap(instance -> goosedProxy.proxy(
                        exchange.getRequest(), exchange.getResponse(),
                        instance.getPort(), target, instance.getSecretKey()));
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
}
