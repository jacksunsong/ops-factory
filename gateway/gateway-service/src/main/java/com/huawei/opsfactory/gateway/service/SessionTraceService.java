/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Collects session debug traces as tar.gz archives with background job management and automatic cleanup.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
public class SessionTraceService implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(SessionTraceService.class);

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]+");

    private static final Duration JOB_TTL = Duration.ofHours(1);

    private static final Duration COLLECTION_TIMEOUT = Duration.ofMinutes(10);

    private static final long CLEANUP_FIXED_DELAY_MS = 15 * 60 * 1000L;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final Map<String, TraceJob> jobs = new ConcurrentHashMap<>();

    private final Map<String, String> runningBySession = new ConcurrentHashMap<>();

    private final Path repoRoot;

    private final Path gatewayRoot;

    private final Path scriptPath;

    private final Path traceRoot;

    /**
     * Creates the session trace service instance.
     */
    public SessionTraceService() {
        this.repoRoot = resolveRepoRoot();
        this.gatewayRoot = repoRoot.resolve("gateway").normalize();
        this.scriptPath = resolveScriptPath(repoRoot);
        this.traceRoot = gatewayRoot.resolve("data").resolve("session-traces").normalize();
        cleanupExpiredTraceDirectories();
    }

    /**
     * Starts a background trace collection job for the given session.
     *
     * @param userId user identifier
     * @param agentId agent identifier
     * @param sessionId session identifier
     * @return the starts a background trace collection job for the given session
     */
    public synchronized TraceJobSnapshot startTrace(String userId, String agentId, String sessionId) {
        validateId("userId", userId);
        validateId("agentId", agentId);
        validateId("sessionId", sessionId);
        cleanupExpiredJobs();

        String sessionKey = sessionKey(userId, agentId, sessionId);
        String existingJobId = runningBySession.get(sessionKey);
        if (existingJobId != null) {
            TraceJob existing = jobs.get(existingJobId);
            if (existing != null && existing.status == TraceStatus.RUNNING) {
                return existing.snapshot();
            }
            runningBySession.remove(sessionKey, existingJobId);
        }

        String jobId = UUID.randomUUID().toString();
        Path jobDir = traceRoot.resolve(jobId).normalize();
        Path outDir = jobDir.resolve("bundle").normalize();
        TraceJob job = new TraceJob(jobId, sessionKey, userId, agentId, sessionId, jobDir, outDir);
        jobs.put(jobId, job);
        runningBySession.put(sessionKey, jobId);

        CompletableFuture.runAsync(() -> collect(job), executor);
        return job.snapshot();
    }

    /**
     * Gets the current status snapshot of a trace collection job.
     *
     * @param jobId job id
     * @return the current status snapshot of a trace collection job
     */
    public TraceJobSnapshot getJob(String jobId) {
        cleanupExpiredJobs();
        TraceJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "trace job not found");
        }
        return job.snapshot();
    }

    /**
     * Returns the path to the collected trace archive for a completed job.
     *
     * @param jobId returns the path to the collected trace archive for a completed job
     * @return the path to the collected trace archive for a completed job
     */
    public Path getArchive(String jobId) {
        TraceJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "trace job not found");
        }
        if (job.status == TraceStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "trace job is still running");
        }
        if (job.status == TraceStatus.FAILED || job.archivePath == null || !Files.isRegularFile(job.archivePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "trace archive not available");
        }
        return job.archivePath;
    }

    /**
     * Deletes a trace job and its associated files.
     *
     * @param jobId job id
     */
    public void deleteJob(String jobId) {
        TraceJob job = jobs.remove(jobId);
        if (job == null) {
            return;
        }
        runningBySession.remove(job.sessionKey, jobId);
        deleteRecursively(job.jobDir);
    }

    private void collect(TraceJob job) {
        try {
            Files.createDirectories(job.jobDir);
            if (!Files.isRegularFile(scriptPath)) {
                throw new IllegalStateException("trace script not found: " + scriptPath);
            }

            Path stdout = job.jobDir.resolve("collector.stdout.log");
            Path stderr = job.jobDir.resolve("collector.stderr.log");
            List<String> command = List.of("bash", scriptPath.toString(), "--session", job.sessionId, "--user",
                job.userId, "--agent", job.agentId, "--root", repoRoot.toString(), "--gateway-root",
                gatewayRoot.toString(), "--out-dir", job.outDir.toString());

            ProcessBuilder pb = new ProcessBuilder(command).directory(repoRoot.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());

            log.info("[SESSION-TRACE] start jobId={} userId={} agentId={} sessionId={}", job.jobId, job.userId,
                job.agentId, job.sessionId);
            Process process = pb.start();
            boolean finished =
                process.waitFor(COLLECTION_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("trace collection timed out");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                throw new IllegalStateException("trace collection failed with exit code " + exit);
            }

            Path generatedArchive = Path.of(job.outDir.toString() + ".tar.gz");
            if (!Files.isRegularFile(generatedArchive)) {
                throw new IllegalStateException("trace archive was not created");
            }
            String fileName =
                "session-trace-" + job.userId + "-" + job.agentId + "-" + job.sessionId + "-" + job.jobId + ".tar.gz";
            Path archive = job.jobDir.resolve(fileName).normalize();
            Files.move(generatedArchive, archive, StandardCopyOption.REPLACE_EXISTING);
            job.archivePath = archive.normalize();
            job.fileName = fileName;
            job.status = TraceStatus.SUCCEEDED;
            job.message = "trace collection complete";
            log.info("[SESSION-TRACE] succeeded jobId={} archive={}", job.jobId, archive);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.status = TraceStatus.FAILED;
            job.message = "trace collection interrupted";
            log.warn("[SESSION-TRACE] interrupted jobId={}", job.jobId);
        } catch (IOException | IllegalStateException e) {
            job.status = TraceStatus.FAILED;
            job.message = e.getMessage() == null ? "trace collection failed" : e.getMessage();
            log.warn("[SESSION-TRACE] failed jobId={} error={}", job.jobId, job.message);
        } finally {
            job.completedAt = Instant.now();
            runningBySession.remove(job.sessionKey, job.jobId);
        }
    }

    /**
     * Periodically cleans up expired trace jobs and trace directories.
     */
    @Scheduled(fixedDelay = CLEANUP_FIXED_DELAY_MS)
    public void cleanupExpiredTraces() {
        cleanupExpiredJobs();
        cleanupExpiredTraceDirectories();
    }

    private synchronized void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.values()
            .stream()
            .filter(
                job -> job.status != TraceStatus.RUNNING && job.completedAt != null && job.completedAt.isBefore(cutoff))
            .map(job -> job.jobId)
            .toList()
            .forEach(this::deleteJob);
    }

    private void cleanupExpiredTraceDirectories() {
        if (!Files.isDirectory(traceRoot)) {
            return;
        }
        Instant cutoff = Instant.now().minus(JOB_TTL);
        List<Path> liveJobDirs = jobs.values().stream().map(job -> job.jobDir).toList();
        try (var stream = Files.list(traceRoot)) {
            stream.filter(Files::isDirectory)
                .filter(path -> liveJobDirs.stream().noneMatch(path::equals))
                .filter(path -> isOlderThan(path, cutoff))
                .forEach(path -> {
                    log.info("[SESSION-TRACE] deleting expired trace directory {}", path);
                    deleteRecursively(path);
                });
        } catch (IOException e) {
            log.warn("[SESSION-TRACE] failed to scan trace root {}: {}", traceRoot, e.getMessage());
        }
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("gateway")) && Files.isDirectory(cwd.resolve("web-app"))) {
            return cwd;
        }
        if ("gateway".equals(cwd.getFileName() != null ? cwd.getFileName().toString() : "")) {
            return cwd.getParent() != null ? cwd.getParent() : cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("gateway"))
            && Files.isDirectory(parent.resolve("web-app"))) {
            return parent;
        }
        return cwd;
    }

    private static Path resolveScriptPath(Path repoRoot) {
        Path rootScript =
            repoRoot.resolve("gateway").resolve("scripts").resolve("collect-session-debug.sh").normalize();
        if (Files.isRegularFile(rootScript)) {
            return rootScript;
        }
        return repoRoot.resolve("scripts").resolve("collect-session-debug.sh").normalize();
    }

    private static String sessionKey(String userId, String agentId, String sessionId) {
        return userId + "\u0000" + agentId + "\u0000" + sessionId;
    }

    private static void validateId(String name, String value) {
        if (value == null || value.isBlank() || !SAFE_ID.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " contains unsupported characters");
        }
    }

    private static boolean isOlderThan(Path path, Instant cutoff) {
        try {
            FileTime modifiedAt = Files.getLastModifiedTime(path);
            return modifiedAt.toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException e) {
                    // best-effort cleanup
                }
            });
        } catch (IOException e) {
            // best-effort cleanup
        }
    }

    /**
     * Shuts down the background executor on bean disposal.
     */
    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    /**
     * Type definition for Trace Status.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public enum TraceStatus {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    /**
     * Type definition for Trace Job Snapshot.
     *
     * @author x00000000
     * @since 2026-05-09
     */
    public record TraceJobSnapshot(String jobId, String status, String userId, String agentId, String sessionId,
        String fileName, String message) {
    }

    private static final class TraceJob {
        private final String jobId;

        private final String sessionKey;

        private final String userId;

        private final String agentId;

        private final String sessionId;

        private final Path jobDir;

        private final Path outDir;

        private volatile Instant completedAt;

        private volatile TraceStatus status = TraceStatus.RUNNING;

        private volatile Path archivePath;

        private volatile String fileName;

        private volatile String message = "trace collection running";

        private TraceJob(String jobId, String sessionKey, String userId, String agentId, String sessionId, Path jobDir,
            Path outDir) {
            this.jobId = jobId;
            this.sessionKey = sessionKey;
            this.userId = userId;
            this.agentId = agentId;
            this.sessionId = sessionId;
            this.jobDir = jobDir;
            this.outDir = outDir;
        }

        private TraceJobSnapshot snapshot() {
            return new TraceJobSnapshot(jobId, status.name().toLowerCase(Locale.ROOT), userId, agentId, sessionId,
                fileName, message);
        }
    }
}
