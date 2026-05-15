/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import reactor.netty.http.client.HttpClient;

import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP client for the Skill Market API that fetches skill metadata and downloads packages.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class SkillMarketClient {
    private final GatewayProperties properties;

    private final WebClient webClient;

    /**
     * Creates the skill market client instance.
     *
     * @param properties gateway configuration properties providing skill market settings
     */
    public SkillMarketClient(GatewayProperties properties) {
        this.properties = properties;
        int maxBytes = properties.getSkillMarket().getMaxPackageSizeMb() * 1024 * 1024;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxBytes))
            .build();
        this.webClient = WebClient.builder()
            .exchangeStrategies(strategies)
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
            .build();
    }

    /**
     * Fetches skill metadata from the skill market by skill ID.
     *
     * @param skillId skill identifier to look up
     * @return map containing skill metadata fields
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSkill(String skillId) {
        Object response = webClient.get()
            .uri(baseUrl() + "/skill-market/skills/{skillId}", skillId)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(status -> status.isError(),
                res -> res.bodyToMono(String.class)
                    .map(body -> new IllegalStateException(
                        "Skill Market detail request failed: HTTP " + res.statusCode().value() + " " + body)))
            .bodyToMono(Map.class)
            .block(timeout());
        if (!(response instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Skill Market returned an invalid skill detail response");
        }
        return (Map<String, Object>) map;
    }

    /**
     * Downloads a skill package as a byte array from the skill market.
     *
     * @param skillId skill identifier whose package to download
     * @return raw ZIP package bytes
     */
    public byte[] downloadPackage(String skillId) {
        try {
            byte[] data = webClient.get()
                .uri(baseUrl() + "/skill-market/skills/{skillId}/package", skillId)
                .accept(MediaType.parseMediaType("application/zip"))
                .retrieve()
                .onStatus(status -> status.isError(),
                    res -> res.bodyToMono(String.class)
                        .map(body -> new IllegalStateException(
                            "Skill Market package request failed: HTTP " + res.statusCode().value() + " " + body)))
                .bodyToMono(byte[].class)
                .block(timeout());
            if (data == null || data.length == 0) {
                throw new IllegalStateException("Skill Market returned an empty package");
            }
            return data;
        } catch (DataBufferLimitException e) {
            throw new IllegalStateException("Skill package exceeds gateway download limit", e);
        }
    }

    private String baseUrl() {
        String baseUrl = properties.getSkillMarket().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://127.0.0.1:8095";
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private Duration timeout() {
        return Duration.ofMillis(properties.getSkillMarket().getRequestTimeoutMs());
    }
}
