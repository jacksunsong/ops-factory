/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test coverage for Process Util.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class ProcessUtilTest {

    /**
     * Tests is alive running process.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testIsAlive_runningProcess() throws Exception {
        Process process = new ProcessBuilder("sleep", "10").start();
        try {
            assertTrue(ProcessUtil.isAlive(process));
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Tests is alive dead process.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testIsAlive_deadProcess() throws Exception {
        Process process = new ProcessBuilder("echo", "hello").start();
        process.waitFor();
        assertFalse(ProcessUtil.isAlive(process));
    }

    /**
     * Tests get pid returns positive.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testGetPid_returnsPositive() throws Exception {
        Process process = new ProcessBuilder("sleep", "5").start();
        try {
            long pid = ProcessUtil.getPid(process);
            assertTrue("PID should be positive", pid > 0);
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Tests stop gracefully.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testStopGracefully() throws Exception {
        Process process = new ProcessBuilder("sleep", "60").start();
        assertTrue(ProcessUtil.isAlive(process));

        ProcessUtil.stopGracefully(process, 100);

        // After stop, process should be dead
        assertFalse(ProcessUtil.isAlive(process));
    }

    /**
     * Tests stop gracefully already dead.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testStopGracefully_alreadyDead() throws Exception {
        Process process = new ProcessBuilder("echo", "done").start();
        process.waitFor();
        // Should not throw
        ProcessUtil.stopGracefully(process, 100);
        assertFalse(ProcessUtil.isAlive(process));
    }
}
