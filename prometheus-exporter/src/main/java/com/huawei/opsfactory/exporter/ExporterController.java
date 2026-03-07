package com.huawei.opsfactory.exporter;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ExporterController {

    private final GatewayMetricsCollector collector;

    public ExporterController(GatewayMetricsCollector collector) {
        this.collector = collector;
    }

    @GetMapping("/metrics")
    public ResponseEntity<String> metrics() {
        try {
            collector.collect();
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(collector.metricsContentType()))
                .body(collector.renderMetrics());
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Error collecting metrics: " + e.getMessage() + "\n");
        }
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return "<html><body><h1>Ops Factory Prometheus Exporter</h1><p><a href=\"/metrics\">Metrics</a></p></body></html>";
    }
}
