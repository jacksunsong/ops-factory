/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.qos.dv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.ssl.SslContext;

import jakarta.annotation.PreDestroy;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dv Auth Service.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@Component
public class DvAuthService {

    private static final Logger log = LoggerFactory.getLogger(DvAuthService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long TOKEN_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private final DvSslContextFactory sslFactory;

    private final ConcurrentHashMap<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, WebClient> clientCache = new ConcurrentHashMap<>();

    /**
     * Dv Auth Service.
     *
     * @param sslFactory the sslFactory
     */
    public DvAuthService(DvSslContextFactory sslFactory) {
        this.sslFactory = sslFactory;
    }

    /**
     * Gets the ssotoken.
     *
     * @param env the env
     * @return the result
     */
    public synchronized TokenInfo getSSOToken(DvEnvironmentInfo env) {
        String cacheKey = env.getServerUrl() + ":" + env.getUtmUser();
        TokenInfo cached = tokenCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        TokenInfo newToken = fetchNewToken(env);
        tokenCache.put(cacheKey, newToken);
        return newToken;
    }

    /**
     * build Auth Headers.
     *
     * @param env the env
     * @return the result
     */
    public Map<String, String> buildAuthHeaders(DvEnvironmentInfo env) {
        String cacheKey = env.getServerUrl() + ":" + env.getUtmUser();
        TokenInfo info = tokenCache.get(cacheKey);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("X-Auth-Token", getSSOToken(env).token);
        headers.put("roaRand", getSSOToken(env).roaRand);
        return headers;
    }

    /**
     * clear Cache.
     */
    public void clearCache() {
        tokenCache.clear();
    }

    private TokenInfo fetchNewToken(DvEnvironmentInfo env) {
        try {
            WebClient webClient = clientCache.computeIfAbsent(env.getServerUrl(), url -> {
                SslContext sslCtx =
                    sslFactory.createSslContext(env.getCrtContent(), env.getCrtFileName(), env.isStrictSsl());
                HttpClient httpClient = HttpClient.create()
                    .secure(t -> t.sslContext(sslCtx).handshakeTimeout(Duration.ofSeconds(10)))
                    .responseTimeout(Duration.ofSeconds(30));
                return WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .baseUrl(url)
                    .build();
            });

            String loginBody = MAPPER.writeValueAsString(
                Map.of("grantType", "password", "userName", env.getUtmUser(), "value", env.getUtmPassword()));

            String response = webClient.put()
                .uri("/rest/plat/smapp/v1/sessions")
                .header("Content-Type", "application/json")
                .body(Mono.just(loginBody), String.class)
                .retrieve()
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(30));

            if (response == null || response.isBlank()) {
                throw new RuntimeException("Empty response from SSO login at " + env.getServerUrl());
            }

            JsonNode json = MAPPER.readTree(response);
            String token = json.has("accessSession") ? json.get("accessSession").asText() : null;
            if (token == null || token.isBlank()) {
                throw new RuntimeException("No token in login response from " + env.getServerUrl());
            }

            String roaRand = json.has("roaRand") ? json.get("roaRand").asText() : null;
            if (roaRand == null || roaRand.isBlank()) {
                throw new RuntimeException("No roaRand in login response from " + env.getServerUrl());
            }

            long ttlMs = TOKEN_TTL_MS;
            if (json.has("expires") && !json.get("expires").isNull()) {
                try {
                    ttlMs = json.get("expires").asLong() * 1000L;
                } catch (NumberFormatException e) {
                    log.warn("Invalid expires value in login response, using default TTL");
                }
            }
            log.info("DV SSO token acquired for {}", env.getEnvCode());
            return new TokenInfo(token, roaRand, System.currentTimeMillis(), ttlMs);
        } catch (Exception e) {
            log.error("Failed to get SSO token from {}: {}", env.getServerUrl(), e.getMessage());
            throw new RuntimeException("DV SSO login failed", e);
        }
    }

    /**
     * shutdown.
     */
    @PreDestroy
    public void shutdown() {
        clientCache.clear();
    }

    private static class TokenInfo {
        final String token;

        final String roaRand;

        final long createdAt;

        final long ttlMs;

        TokenInfo(String token, String roaRand, long createdAt, long ttlMs) {
            this.token = token;
            this.roaRand = roaRand;
            this.createdAt = createdAt;
            this.ttlMs = ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }
    }
}
