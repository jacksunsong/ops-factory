/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;

import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * Lightweight controller exposing health-check, current-user identity, and public config.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping(value = "/gateway")
public class StatusController {
    private final GatewayProperties properties;

    /**
     * Creates the status controller.
     *
     * @param properties gateway configuration properties
     */
    public StatusController(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns health check status.
     *
     * @return Mono emitting the string "ok"
     */
    @GetMapping("/status")
    public Mono<String> status() {
        return Mono.just("ok");
    }

    /**
     * Returns the current user's identity.
     *
     * @param exchange current HTTP exchange carrying user context attributes
     * @return Mono emitting a map containing "userId" and "role"
     */
    @GetMapping("/me")
    public Mono<Map<String, Object>> me(ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return Mono.just(Map.of("userId", userId != null ? userId : "unknown", "role", "user"));
    }

    /**
     * Returns public configuration such as Office preview settings.
     *
     * @return Mono emitting a map with Office preview configuration
     */
    @GetMapping("/config")
    public Mono<Map<String, Object>> config() {
        GatewayProperties.OfficePreview op = properties.getOfficePreview();
        return Mono.just(Map.of("officePreview", Map.of("enabled", op.isEnabled(), "onlyofficeUrl",
            op.getOnlyofficeUrl(), "fileBaseUrl", op.getFileBaseUrl())));
    }
}
