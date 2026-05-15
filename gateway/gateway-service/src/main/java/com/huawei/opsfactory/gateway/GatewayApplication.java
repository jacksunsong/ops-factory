/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OpsFactory Gateway Spring Boot application.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * Starts the OpsFactory Gateway application.
     *
     * @param args args
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
