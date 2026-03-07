package com.huawei.opsfactory.exporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

@Configuration
public class ExporterConfigLoader {

    @Bean
    public ExporterProperties exporterProperties() {
        ExporterProperties properties = new ExporterProperties();
        loadYaml(properties);
        overrideByEnv(properties);
        validate(properties);
        return properties;
    }

    private void loadYaml(ExporterProperties properties) {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            return;
        }

        Yaml yaml = new Yaml();
        try (InputStream input = Files.newInputStream(configPath)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> config)) {
                return;
            }

            Object port = config.get("port");
            if (port != null) {
                properties.setPort(parseIntSafe(port, "port"));
            }

            Object gatewayUrl = config.get("gatewayUrl");
            if (gatewayUrl != null) {
                properties.setGatewayUrl(trimTrailingSlash(gatewayUrl.toString()));
            }

            Object gatewaySecretKey = config.get("gatewaySecretKey");
            if (gatewaySecretKey != null) {
                properties.setGatewaySecretKey(gatewaySecretKey.toString());
            }

            Object collectTimeoutMs = config.get("collectTimeoutMs");
            if (collectTimeoutMs != null) {
                properties.setCollectTimeoutMs(parseIntSafe(collectTimeoutMs, "collectTimeoutMs"));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read exporter config: " + configPath, e);
        }
    }

    private void overrideByEnv(ExporterProperties properties) {
        String envPort = System.getenv("EXPORTER_PORT");
        if (envPort != null && !envPort.isBlank()) {
            properties.setPort(parseIntSafe(envPort, "EXPORTER_PORT"));
        }

        String envGatewayUrl = System.getenv("GATEWAY_URL");
        if (envGatewayUrl != null && !envGatewayUrl.isBlank()) {
            properties.setGatewayUrl(trimTrailingSlash(envGatewayUrl));
        }

        String envSecretKey = System.getenv("GATEWAY_SECRET_KEY");
        if (envSecretKey != null && !envSecretKey.isBlank()) {
            properties.setGatewaySecretKey(envSecretKey);
        }

        String envTimeout = System.getenv("COLLECT_TIMEOUT_MS");
        if (envTimeout != null && !envTimeout.isBlank()) {
            properties.setCollectTimeoutMs(parseIntSafe(envTimeout, "COLLECT_TIMEOUT_MS"));
        }
    }

    private void validate(ExporterProperties properties) {
        if (properties.getGatewayUrl() == null || properties.getGatewayUrl().isBlank()) {
            throw new IllegalStateException("Missing required config: set \"gatewayUrl\" in prometheus-exporter/config.yaml");
        }
        if (properties.getGatewaySecretKey() == null || properties.getGatewaySecretKey().isBlank()) {
            throw new IllegalStateException("Missing required config: set \"gatewaySecretKey\" in prometheus-exporter/config.yaml");
        }
    }

    private Path getConfigPath() {
        String configured = System.getenv("CONFIG_PATH");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return Paths.get("config.yaml");
    }

    private int parseIntSafe(Object value, String name) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer value for \"" + name + "\": " + value, e);
        }
    }

    private String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
