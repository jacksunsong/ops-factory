/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates registered request hooks, executing them in order and short-circuiting on errors.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
public class HookPipeline {
    private static final Logger log = LoggerFactory.getLogger(HookPipeline.class);

    private final List<RequestHook> hooks;

    /**
     * Creates the hook pipeline instance.
     *
     * @param hooks ordered list of request hooks to execute
     */
    public HookPipeline(List<RequestHook> hooks) {
        this.hooks = hooks;
        log.info("Registered {} request hooks", hooks.size());
    }

    /**
     * Run all request hooks in order. Returns the (possibly modified) body.
     * If any hook returns an error Mono, the pipeline short-circuits.
     *
     * @param ctx hook context carrying the request body and metadata
     * @return Mono emitting the potentially modified request body string
     */
    public Mono<String> executeRequest(HookContext ctx) {
        Mono<HookContext> chain = Mono.just(ctx);
        for (RequestHook hook : hooks) {
            chain = chain.flatMap(hook::process);
        }
        return chain.map(HookContext::getBody);
    }
}
