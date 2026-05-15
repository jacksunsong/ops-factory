/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that catches and normalizes errors from all controllers.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final GatewayProperties properties;

    /**
     * Creates a global exception handler with default gateway properties.
     */
    public GlobalExceptionHandler() {
        this(new GatewayProperties());
    }

    /**
     * Creates a global exception handler with the given gateway properties.
     */
    public GlobalExceptionHandler(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Handles request input errors such as invalid JSON body.
     *
     * @param ex ex
     * @return the handles request input errors such as invalid JSON body
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleInputException(ServerWebInputException ex) {
        log.warn("Request input error: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        if (ex.getCause() instanceof DecodingException) {
            body.put("error", "Invalid JSON body");
        } else {
            body.put("error", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        }
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles response status exceptions and returns a normalized error body.
     *
     * @param ex ex
     * @return the handles response status exceptions and returns a normalized error body
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("Request rejected with status={} reason={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Catch-all for goosed HTTP errors that controllers didn't handle.
     * Forwards the upstream status code with a sanitized error message.
     *
     * @param ex catch-all for goosed HTTP errors that controllers didn't handle
     * @return the catch-all for goosed HTTP errors that controllers didn't handle
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleWebClientResponse(WebClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getRawStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String message;
        if (status == HttpStatus.NOT_FOUND) {
            message = "Requested resource not found";
        } else if (status.series() == HttpStatus.Series.CLIENT_ERROR) {
            message = "Agent request failed: " + ex.getStatusText();
        } else {
            message = "Agent internal error";
        }

        String path = ex.getRequest() != null ? ex.getRequest().getURI().getPath() : "unknown";
        String responseBody = ex.getResponseBodyAsString();
        if (properties.getLogging().isIncludeUpstreamErrorBody()) {
            log.warn("Goosed returned {} for {} bodyLength={} body={}", ex.getRawStatusCode(), path,
                responseBody.length(), truncate(responseBody, 500));
        } else {
            log.warn("Goosed returned {} for {} bodyLength={}", ex.getRawStatusCode(), path, responseBody.length());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Last-resort catch-all for any unhandled exception.
     * Returns 500 with a generic message and never leaks internal details.
     *
     * @param ex last-resort catch-all for any unhandled exception
     * @return the last-resort catch-all for any unhandled exception
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
