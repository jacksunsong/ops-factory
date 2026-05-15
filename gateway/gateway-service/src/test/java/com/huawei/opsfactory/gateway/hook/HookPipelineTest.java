/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.hook;

import static org.junit.Assert.assertEquals;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Test coverage for Hook Pipeline.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class HookPipelineTest {

    /**
     * Tests empty pipeline.
     */
    @Test
    public void testEmptyPipeline() {
        HookPipeline pipeline = new HookPipeline(List.of());
        HookContext ctx = new HookContext("{\"test\": true}", "agent1", "user1");

        StepVerifier.create(pipeline.executeRequest(ctx)).expectNext("{\"test\": true}").verifyComplete();
    }

    /**
     * Tests single hook pass through.
     */
    @Test
    public void testSingleHook_passThrough() {
        RequestHook hook = c -> Mono.just(c);
        HookPipeline pipeline = new HookPipeline(List.of(hook));
        HookContext ctx = new HookContext("body", "agent1", "user1");

        StepVerifier.create(pipeline.executeRequest(ctx)).expectNext("body").verifyComplete();
    }

    /**
     * Tests multiple hooks executed in order.
     */
    @Test
    public void testMultipleHooks_executedInOrder() {
        RequestHook hook1 = c -> {
            c.setBody(c.getBody() + "-hook1");
            return Mono.just(c);
        };
        RequestHook hook2 = c -> {
            c.setBody(c.getBody() + "-hook2");
            return Mono.just(c);
        };

        HookPipeline pipeline = new HookPipeline(List.of(hook1, hook2));
        HookContext ctx = new HookContext("start", "agent1", "user1");

        StepVerifier.create(pipeline.executeRequest(ctx)).expectNext("start-hook1-hook2").verifyComplete();
    }

    /**
     * Tests hook error short circuits.
     */
    @Test
    public void testHookError_shortCircuits() {
        RequestHook hook1 = c -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "rejected"));
        RequestHook hook2 = c -> {
            c.setBody("should-not-reach");
            return Mono.just(c);
        };

        HookPipeline pipeline = new HookPipeline(List.of(hook1, hook2));
        HookContext ctx = new HookContext("body", "agent1", "user1");

        StepVerifier.create(pipeline.executeRequest(ctx)).expectError(ResponseStatusException.class).verify();

        // Verify hook2 was never called
        assertEquals("body", ctx.getBody());
    }
}
