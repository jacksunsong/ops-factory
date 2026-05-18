/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.service.SessionTraceService;
import com.huawei.opsfactory.gateway.service.SessionTraceService.TraceJobSnapshot;

import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Admin controller for triggering session trace collection and downloading trace archives.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway")
public class SessionTraceController {
    private final SessionTraceService traceService;

    /**
     * Creates the session trace controller.
     *
     * @param traceService service handling trace collection jobs
     */
    public SessionTraceController(SessionTraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * Starts a trace collection job for a session.
     *
     * @param agentId agent instance identifier
     * @param sessionId session identifier to trace
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a snapshot of the created trace job
     */
    @PostMapping(value = "/agents/{agentId}/sessions/{sessionId}/trace", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TraceJobSnapshot> startTrace(@PathVariable("agentId") String agentId,
        @PathVariable("sessionId") String sessionId, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        return Mono.fromSupplier(() -> traceService.startTrace(userId, agentId, sessionId));
    }

    /**
     * Gets the status of a trace collection job.
     *
     * @param jobId trace job identifier
     * @param exchange current HTTP exchange carrying user context
     * @return Mono emitting a snapshot of the requested trace job
     */
    @GetMapping(value = "/session-traces/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TraceJobSnapshot> getTrace(@PathVariable("jobId") String jobId, ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> traceService.getJob(jobId));
    }

    /**
     * Downloads the trace archive for a completed trace job.
     *
     * @param jobId trace job identifier whose archive to download
     * @param exchange current HTTP exchange for writing the response
     */
    @GetMapping("/session-traces/{jobId}/download")
    public Mono<Void> downloadTrace(@PathVariable("jobId") String jobId, ServerWebExchange exchange) {
        Path archive = traceService.getArchive(jobId);
        if (!Files.isRegularFile(archive)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "trace archive not found"));
        }

        String filename = archive.getFileName().toString();
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);

        try {
            exchange.getResponse().getHeaders().setContentLength(Files.size(archive));
        } catch (IOException e) {
            // Content length is optional for download correctness.
        }
        exchange.getResponse().getHeaders().setContentType(mediaType);
        exchange.getResponse()
            .getHeaders()
            .set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename.replace("\"", "") + "\"; filename*=UTF-8''" + encodedFilename);

        return exchange.getResponse()
            .writeWith(DataBufferUtils.read(archive, exchange.getResponse().bufferFactory(), 64 * 1024))
            .doFinally(signal -> traceService.deleteJob(jobId));
    }
}
