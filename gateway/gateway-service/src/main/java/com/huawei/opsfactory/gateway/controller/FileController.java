/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.util.PathSanitizer;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.service.AgentConfigService;
import com.huawei.opsfactory.gateway.service.FileService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for browsing, uploading, downloading, and editing agent workspace files.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestController
@RequestMapping("/gateway/agents/{agentId}/files")
public class FileController {
    private final InstanceManager instanceManager;

    private final AgentConfigService agentConfigService;

    private final FileService fileService;

    /**
     * Creates the file controller instance.
     */
    public FileController(InstanceManager instanceManager, AgentConfigService agentConfigService,
        FileService fileService) {
        this.instanceManager = instanceManager;
        this.agentConfigService = agentConfigService;
        this.fileService = fileService;
    }

    /**
     * Lists files in the agent workspace directory.
     *
     * @param agentId agent identifier
     * @param exchange server web exchange
     * @return the result
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> listFiles(@PathVariable("agentId") String agentId, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        return Mono.fromCallable(() -> Map.<String, Object> of("files", fileService.listFiles(workingDir)))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class,
                e -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list files"));
    }

    /**
     * Downloads or retrieves a file from the agent workspace.
     *
     * @param agentId downloads or retrieves a file from the agent workspace
     * @param exchange downloads or retrieves a file from the agent workspace
     * @return the downloads or retrieves a file from the agent workspace
     */
    @GetMapping("/**")
    public Mono<ResponseEntity<?>> getFile(@PathVariable("agentId") String agentId, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        Path rootDir = resolveRootOrThrow(workingDir, exchange);

        // Extract the file path after /gateway/agents/{agentId}/files/
        // getPath().value() returns the raw percent-encoded URI; decode so that
        // non-ASCII filenames (e.g. Chinese characters) resolve correctly on disk.
        String fullPath = exchange.getRequest().getPath().value();
        String prefix = "/gateway/agents/" + agentId + "/files/";
        String relativePath = URLDecoder.decode(fullPath.substring(prefix.length()), StandardCharsets.UTF_8);

        // Check for path traversal — return 403
        if (!PathSanitizer.isSafe(rootDir, relativePath)) {
            return Mono
                .just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "path traversal not allowed")));
        }

        return Mono.<ResponseEntity<?>> fromCallable(() -> {
            Resource resource = fileService.resolveFile(rootDir, relativePath);
            if (resource == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "file not found"));
            }

            String filename = resource.getFilename();
            String mimeType = fileService.getMimeType(filename != null ? filename : "");
            // Force attachment when ?download=true is present
            boolean forceDownload = "true".equals(exchange.getRequest().getQueryParams().getFirst("download"));
            String disposition = (!forceDownload && fileService.isInline(mimeType)) ? "inline" : "attachment";

            byte[] content = resource.getInputStream().readAllBytes();
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
                .body(content);
        })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class,
                e -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file"));
    }

    /**
     * Deletes a file from the agent workspace.
     *
     * @param agentId agent identifier
     * @param exchange server web exchange
     * @return the result
     */
    @DeleteMapping("/**")
    public Mono<ResponseEntity<Map<String, Object>>> deleteFile(@PathVariable("agentId") String agentId,
        ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        Path rootDir = resolveRootOrThrow(workingDir, exchange);

        String fullPath = exchange.getRequest().getPath().value();
        String prefix = "/gateway/agents/" + agentId + "/files/";
        String relativePath = URLDecoder.decode(fullPath.substring(prefix.length()), StandardCharsets.UTF_8);

        if (!PathSanitizer.isSafe(rootDir, relativePath)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.<String, Object> of("error", "path traversal not allowed")));
        }

        return Mono.fromCallable(() -> {
            boolean deleted = fileService.deleteFile(rootDir, relativePath);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.<String, Object> of("error", "file not found"));
            }
            return ResponseEntity.ok(Map.<String, Object> of("status", "deleted", "path", relativePath));
        })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class,
                e -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file"));
    }

    /**
     * Updates the content of an editable text file in the agent workspace.
     *
     * @param agentId the content of an editable text file in the agent workspace
     * @param request the content of an editable text file in the agent workspace
     * @param exchange the content of an editable text file in the agent workspace
     * @return the result
     */
    @PutMapping(value = "/**", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> updateFile(@PathVariable("agentId") String agentId,
        @RequestBody FileUpdateRequest request, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path workingDir = agentConfigService.getUserAgentDir(userId, agentId);
        Path rootDir = resolveRootOrThrow(workingDir, exchange);

        String fullPath = exchange.getRequest().getPath().value();
        String prefix = "/gateway/agents/" + agentId + "/files/";
        String relativePath = URLDecoder.decode(fullPath.substring(prefix.length()), StandardCharsets.UTF_8);

        if (!PathSanitizer.isSafe(rootDir, relativePath)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.<String, Object> of("error", "path traversal not allowed")));
        }

        if (!fileService.isEditableTextFile(relativePath)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.<String, Object> of("error", "file type is not editable")));
        }

        return Mono.fromCallable(() -> {
            boolean updated = fileService.updateTextFile(rootDir, relativePath, request.content());
            if (!updated) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.<String, Object> of("error", "file not found"));
            }
            return ResponseEntity.ok(Map.<String, Object> of("status", "updated", "path", relativePath));
        })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(IOException.class,
                e -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update file"));
    }

    /**
     * Uploads a file to the agent workspace for a specific session.
     *
     * @param agentId uploads a file to the agent workspace for a specific session
     * @param filePart uploads a file to the agent workspace for a specific session
     * @param sessionId uploads a file to the agent workspace for a specific session
     * @param exchange uploads a file to the agent workspace for a specific session
     * @return the uploads a file to the agent workspace for a specific session
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadFile(@PathVariable("agentId") String agentId, @RequestPart("file") FilePart filePart,
        @RequestPart("sessionId") String sessionId, ServerWebExchange exchange) {
        String userId = exchange.getAttribute(UserContextFilter.USER_ID_ATTR);
        Path uploadsDir = agentConfigService.getUserAgentDir(userId, agentId).resolve("uploads").resolve(sessionId);

        String originalName = filePart.filename();

        // Check file type
        if (!fileService.isAllowedExtension(originalName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File type not allowed: " + originalName);
        }

        try {
            Files.createDirectories(uploadsDir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create upload dir");
        }

        String safeName = System.currentTimeMillis() + "_" + PathSanitizer.sanitizeFilename(originalName);
        Path dest = uploadsDir.resolve(safeName);
        String mimeType = fileService.getMimeType(originalName);

        return filePart.transferTo(dest).then(Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "uploaded");
            result.put("filename", safeName);
            result.put("path", dest.toString());
            result.put("name", PathSanitizer.sanitizeFilename(originalName));
            result.put("type", mimeType);
            result.put("size", Files.size(dest));
            return result;
        }));
    }

    /**
     * Fallback for upload requests that are not multipart/form-data.
     *
     * @return the fallback for upload requests that are not multipart/form-data
     */
    @PostMapping(value = "/upload")
    public Mono<Map<String, Object>> uploadFileNotMultipart() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload requires multipart/form-data content type");
    }

    private record FileUpdateRequest(String content) {
    }

    private Path resolveRootOrThrow(Path workingDir, ServerWebExchange exchange) {
        String requestedRootId = exchange.getRequest().getQueryParams().getFirst("rootId");
        if (requestedRootId == null || requestedRootId.isBlank()) {
            return workingDir;
        }
        String rootId = requestedRootId.trim();
        return fileService.resolveFileScanRoot(workingDir, rootId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown file root: " + rootId));
    }
}
