/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.core.publisher.Mono;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Request hook that rejects request bodies exceeding the configured upload size limit.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
@Order(1)
public class BodyLimitHook implements RequestHook {
    private final long maxBytes;

    /**
     * Creates the body limit hook instance.
     *
     * @param properties gateway configuration properties providing upload size limit
     */
    public BodyLimitHook(GatewayProperties properties) {
        // Base64 overhead: ~33% larger than raw bytes
        this.maxBytes = (long) properties.getUpload().getMaxFileSizeMb() * 1024 * 1024 * 4 / 3;
    }

    /**
     * Rejects request bodies exceeding the configured upload size limit.
     *
     * @param ctx hook context containing the request body to validate
     * @return Mono emitting the unchanged context, or an error if the body exceeds the limit
     */
    @Override
    public Mono<HookContext> process(HookContext ctx) {
        if (ctx.getBody() != null && ctx.getBody().length() > maxBytes) {
            return Mono.error(
                new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds maximum allowed size"));
        }
        return Mono.just(ctx);
    }
}
