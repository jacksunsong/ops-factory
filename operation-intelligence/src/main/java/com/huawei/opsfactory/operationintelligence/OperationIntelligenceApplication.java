/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence;

import com.huawei.opsfactory.operationintelligence.config.OperationIntelligenceProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Operation Intelligence Application.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@SpringBootApplication
@EnableConfigurationProperties(OperationIntelligenceProperties.class)
@EnableScheduling
public class OperationIntelligenceApplication {

    /**
     * main.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        SpringApplication.run(OperationIntelligenceApplication.class, args);
    }
}
