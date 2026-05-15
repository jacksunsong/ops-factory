/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.process;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Allocates ephemeral ports by binding to port zero and immediately releasing the socket.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
public class PortAllocator {

    /**
     * Allocate an available ephemeral port by binding to port 0
     * and immediately releasing it.
     *
     * @return the allocated port number
     */
    public int allocate() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to allocate an available port", e);
        }
    }
}
