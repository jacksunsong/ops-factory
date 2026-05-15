/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test coverage for Session Trace Service.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public class SessionTraceServiceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String previousUserDir;

    private static String archiveScript(String beforeArchive) {
        return """
            #!/usr/bin/env bash
            out_dir=""
            while [ "$#" -gt 0 ]; do
              case "$1" in
                --out-dir) out_dir="$2"; shift 2 ;;
                *) shift ;;
              esac
            done
            %s
            mkdir -p "$out_dir"
            tar -czf "$out_dir.tar.gz" -C "$out_dir" .
            """.formatted(beforeArchive);
    }

    private static void waitForDone(SessionTraceService service, String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            var job = service.getJob(jobId);
            if (!"running".equals(job.status())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("trace job did not finish: " + jobId);
    }

    /**
     * Sets the up.
     */
    @Before
    public void setUp() {
        previousUserDir = System.getProperty("user.dir");
    }

    /**
     * Executes the tear down operation.
     */
    @After
    public void tearDown() {
        if (previousUserDir != null) {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    /**
     * Tests start trace reuses running job for same session.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testStartTrace_reusesRunningJobForSameSession() throws Exception {
        Path repoRoot = createRepoRoot();
        Files.writeString(repoRoot.resolve("gateway/scripts/collect-session-debug.sh"), archiveScript("sleep 1"));
        System.setProperty("user.dir", repoRoot.toString());
        SessionTraceService service = new SessionTraceService();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.startTrace("admin", "qa-agent", "20260429_2");
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return service.startTrace("admin", "qa-agent", "20260429_2");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            String firstJobId = first.get(5, TimeUnit.SECONDS).jobId();
            String secondJobId = second.get(5, TimeUnit.SECONDS).jobId();

            assertEquals(firstJobId, secondJobId);
            waitForDone(service, firstJobId);
        } finally {
            executor.shutdownNow();
            service.destroy();
        }
    }

    /**
     * Tests start trace allows new job after previous completes.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testStartTrace_allowsNewJobAfterPreviousCompletes() throws Exception {
        Path repoRoot = createRepoRoot();
        Files.writeString(repoRoot.resolve("gateway/scripts/collect-session-debug.sh"), archiveScript(""));
        System.setProperty("user.dir", repoRoot.toString());
        SessionTraceService service = new SessionTraceService();
        try {
            String firstJobId = service.startTrace("admin", "qa-agent", "20260429_2").jobId();
            waitForDone(service, firstJobId);

            String secondJobId = service.startTrace("admin", "qa-agent", "20260429_2").jobId();
            waitForDone(service, secondJobId);

            assertNotEquals(firstJobId, secondJobId);
        } finally {
            service.destroy();
        }
    }

    /**
     * Tests constructor deletes expired trace directories.
     *
     * @throws Exception if the operation fails
     */
    @Test
    public void testConstructorDeletesExpiredTraceDirectories() throws Exception {
        Path repoRoot = createRepoRoot();
        Files.writeString(repoRoot.resolve("gateway/scripts/collect-session-debug.sh"), "#!/usr/bin/env bash\n");
        Path oldTraceDir = repoRoot.resolve("gateway/data/session-traces/old-job");
        Files.createDirectories(oldTraceDir);
        Files.writeString(oldTraceDir.resolve("bundle.tar.gz"), "old");
        Files.setLastModifiedTime(oldTraceDir, FileTime.from(Instant.now().minusSeconds(7200)));
        System.setProperty("user.dir", repoRoot.toString());

        SessionTraceService service = new SessionTraceService();
        try {
            assertFalse(Files.exists(oldTraceDir));
        } finally {
            service.destroy();
        }
    }

    private Path createRepoRoot() throws Exception {
        Path repoRoot = tempFolder.newFolder().toPath();
        Files.createDirectories(repoRoot.resolve("gateway/scripts"));
        Files.createDirectories(repoRoot.resolve("web-app"));
        return repoRoot;
    }
}
