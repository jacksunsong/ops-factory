package com.huawei.opsfactory.exporter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GatewayMetricsCollectorTest {

    private static final long NOW = System.currentTimeMillis();

    @Test
    void collectsGatewayAndInstanceMetrics() throws Exception {
        ExporterProperties props = new ExporterProperties();
        props.setGatewayUrl("http://unused");
        props.setGatewaySecretKey("test");
        props.setCollectTimeoutMs(2000);

        GatewayMetricsCollector collector = new GatewayMetricsCollector(props, path -> {
            if ("/monitoring/system".equals(path)) {
                return Map.of(
                    "gateway", Map.of("uptimeMs", 123456, "host", "127.0.0.1", "port", 3000),
                    "agents", Map.of("configured", 3),
                    "idle", Map.of("timeoutMs", 900000)
                );
            }
            if ("/monitoring/instances".equals(path)) {
                return Map.of(
                    "totalInstances", 3,
                    "runningInstances", 2,
                    "byAgent", List.of(
                        Map.of(
                            "agentId", "a1",
                            "instances", List.of(
                                Map.<String, Object>of("userId", "alice", "port", 50001, "pid", 1001, "status", "running", "lastActivity", NOW - 5000),
                                Map.<String, Object>of("userId", "bob", "port", 50002, "pid", 1002, "status", "running", "lastActivity", NOW - 30000)
                            )
                        ),
                        Map.of(
                            "agentId", "a2",
                            "instances", List.of(
                                Map.<String, Object>of("userId", "alice", "port", 50003, "pid", 1003, "status", "error", "lastActivity", NOW - 60000)
                            )
                        )
                    )
                );
            }
            if ("/monitoring/status".equals(path)) {
                return Map.of("enabled", true);
            }
            return Map.of();
        });

        collector.collect();
        String metrics = collector.renderMetrics();

        assertContains(metrics, "opsfactory_gateway_up 1.0");
        assertContains(metrics, "opsfactory_gateway_uptime_seconds 123.456");
        assertContains(metrics, "opsfactory_agents_configured_total 3.0");
        assertContains(metrics, "opsfactory_instances_total{status=\"running\",} 2.0");
        assertContains(metrics, "opsfactory_instances_total{status=\"error\",} 1.0");
        // idle seconds are computed from (now - lastActivity), so they should be > 0
        assertMetricExists(metrics, "opsfactory_instance_idle_seconds{agent_id=\"a1\",user_id=\"alice\",}");
        assertMetricExists(metrics, "opsfactory_instance_idle_seconds{agent_id=\"a1\",user_id=\"bob\",}");
        assertMetricExists(metrics, "opsfactory_instance_idle_seconds{agent_id=\"a2\",user_id=\"alice\",}");
        assertContains(metrics, "opsfactory_instance_info{agent_id=\"a2\",user_id=\"alice\",port=\"50003\",status=\"error\",} 1.0");
        assertContains(metrics, "opsfactory_langfuse_configured 1.0");
        assertContains(metrics, "opsfactory_exporter_process_cpu");
        assertContains(metrics, "opsfactory_exporter_nodejs_heap");
    }

    @Test
    void langfuseDisabledWhenStatusReturnsFalse() throws Exception {
        ExporterProperties props = new ExporterProperties();
        props.setGatewayUrl("http://unused");
        props.setGatewaySecretKey("test");
        props.setCollectTimeoutMs(2000);

        GatewayMetricsCollector collector = new GatewayMetricsCollector(props, path -> {
            if ("/monitoring/system".equals(path)) {
                return Map.of(
                    "gateway", Map.of("uptimeMs", 1000),
                    "agents", Map.of("configured", 0),
                    "idle", Map.of("timeoutMs", 900000)
                );
            }
            if ("/monitoring/instances".equals(path)) {
                return Map.of("byAgent", List.of());
            }
            if ("/monitoring/status".equals(path)) {
                return Map.of("enabled", false);
            }
            return Map.of();
        });

        collector.collect();
        String metrics = collector.renderMetrics();

        assertContains(metrics, "opsfactory_langfuse_configured 0.0");
    }

    @Test
    void marksGatewayDownWhenUnreachable() throws Exception {
        ExporterProperties props = new ExporterProperties();
        props.setGatewayUrl("http://unused");
        props.setGatewaySecretKey("test");
        props.setCollectTimeoutMs(500);

        GatewayMetricsCollector collector = new GatewayMetricsCollector(props, path -> {
            throw new IOException("gateway down");
        });

        collector.collect();
        String metrics = collector.renderMetrics();

        assertContains(metrics, "opsfactory_gateway_up 0.0");
    }

    private static void assertContains(String metrics, String expected) {
        Assertions.assertTrue(metrics.contains(expected), () -> "Missing metric line: " + expected + "\n" + metrics);
    }

    private static void assertMetricExists(String metrics, String metricPrefix) {
        Assertions.assertTrue(
            metrics.lines().anyMatch(line -> line.startsWith(metricPrefix)),
            () -> "Missing metric starting with: " + metricPrefix + "\n" + metrics
        );
    }
}
