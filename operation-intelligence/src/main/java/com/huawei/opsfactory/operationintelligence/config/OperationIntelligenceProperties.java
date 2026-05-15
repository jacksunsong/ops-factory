/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

/**
 * Operation Intelligence Properties.
 *
 * @author x00000000
 * @since 2026-05-11
 */
@ConfigurationProperties(prefix = "operation-intelligence")
public class OperationIntelligenceProperties {

    private static final Logger log = LoggerFactory.getLogger(OperationIntelligenceProperties.class);

    private static final String CONFIG_PATH_KEY = "OI_CONFIG_PATH";

    private String secretKey = "test";

    private String corsOrigin = "*";

    private String dataRoot = "";

    private Qos qos = new Qos();

    private Logging logging = new Logging();

    /**
     * Gets the secret key.
     *
     * @return the result
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Sets the secret key.
     *
     * @param secretKey the secretKey
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Gets the cors origin.
     *
     * @return the result
     */
    public String getCorsOrigin() {
        return corsOrigin;
    }

    /**
     * Sets the cors origin.
     *
     * @param corsOrigin the corsOrigin
     */
    public void setCorsOrigin(String corsOrigin) {
        this.corsOrigin = corsOrigin;
    }

    /**
     * Gets the data root.
     *
     * @return the result
     */
    public String getDataRoot() {
        return dataRoot;
    }

    /**
     * Sets the data root.
     *
     * @param dataRoot the dataRoot
     */
    public void setDataRoot(String dataRoot) {
        this.dataRoot = dataRoot;
    }

    /**
     * Gets the qos.
     *
     * @return the result
     */
    public Qos getQos() {
        return qos;
    }

    /**
     * Sets the qos.
     *
     * @param qos the qos
     */
    public void setQos(Qos qos) {
        this.qos = qos;
    }

    /**
     * Gets the logging.
     *
     * @return the result
     */
    public Logging getLogging() {
        return logging;
    }

    /**
     * Sets the logging.
     *
     * @param logging the logging
     */
    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    /**
     * resolve Data Root.
     *
     * @return the result
     */
    public Path resolveDataRoot() {
        if (dataRoot != null && !dataRoot.isBlank()) {
            Path configured = Path.of(dataRoot);
            if (configured.isAbsolute()) {
                return configured.normalize();
            }
            return getConfigDirectory().resolve(configured).normalize();
        }
        return getConfigDirectory().resolve("data").normalize();
    }

    /**
     * Gets the config path.
     *
     * @return the result
     */
    public Path getConfigPath() {
        String configuredPath = configuredConfigPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("config.yaml").toAbsolutePath().normalize();
        }
        Path configPath = Path.of(configuredPath);
        if (configPath.isAbsolute()) {
            return configPath.normalize();
        }
        return Path.of("").toAbsolutePath().resolve(configPath).normalize();
    }

    /**
     * Gets the config directory.
     *
     * @return the result
     */
    public Path getConfigDirectory() {
        Path configPath = getConfigPath();
        Path parent = configPath.getParent();
        if (parent != null) {
            return parent;
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    /**
     * Resolve the runtime config file path from the same OI_CONFIG_PATH source used by
     * Spring's {@code spring.config.import} in application.yml. This method does NOT load
     * or parse the config file (Spring handles that); it only resolves the filesystem
     * location so that {@link #getConfigDirectory()} and {@link #resolveDataRoot()} can
     * place the data directory relative to the config file.
     */
    private String configuredConfigPath() {
        String configuredPath = System.getProperty(CONFIG_PATH_KEY);
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = System.getenv(CONFIG_PATH_KEY);
        }
        return (configuredPath == null || configuredPath.isBlank()) ? null : configuredPath;
    }

    public static class Qos {
        private boolean enabled = true;

        private long collectionIntervalMs = 300000;

        private long rotationIntervalMs = 3600000;

        private long rawDataRetentionDays = 7;

        private long detailDataRetentionDays = 30;

        private long normalizeDataRetentionDays = 90;

        private Weights weights = new Weights();

        private Thresholds thresholds = new Thresholds();

        private List<DvEnvironment> dvEnvironments = List.of();

        /**
         * Checks whether the enabled.
         *
         * @return the result
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets the enabled.
         *
         * @param enabled the enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Gets the collection interval ms.
         *
         * @return the result
         */
        public long getCollectionIntervalMs() {
            return collectionIntervalMs;
        }

        /**
         * Sets the collection interval ms.
         *
         * @param collectionIntervalMs the collectionIntervalMs
         */
        public void setCollectionIntervalMs(long collectionIntervalMs) {
            this.collectionIntervalMs = collectionIntervalMs;
        }

        /**
         * Gets the rotation interval ms.
         *
         * @return the result
         */
        public long getRotationIntervalMs() {
            return rotationIntervalMs;
        }

        /**
         * Sets the rotation interval ms.
         *
         * @param rotationIntervalMs the rotationIntervalMs
         */
        public void setRotationIntervalMs(long rotationIntervalMs) {
            this.rotationIntervalMs = rotationIntervalMs;
        }

        /**
         * Gets the raw data retention days.
         *
         * @return the result
         */
        public long getRawDataRetentionDays() {
            return rawDataRetentionDays;
        }

        /**
         * Sets the raw data retention days.
         *
         * @param rawDataRetentionDays the rawDataRetentionDays
         */
        public void setRawDataRetentionDays(long rawDataRetentionDays) {
            this.rawDataRetentionDays = rawDataRetentionDays;
        }

        /**
         * Gets the detail data retention days.
         *
         * @return the result
         */
        public long getDetailDataRetentionDays() {
            return detailDataRetentionDays;
        }

        /**
         * Sets the detail data retention days.
         *
         * @param detailDataRetentionDays the detailDataRetentionDays
         */
        public void setDetailDataRetentionDays(long detailDataRetentionDays) {
            this.detailDataRetentionDays = detailDataRetentionDays;
        }

        /**
         * Gets the normalize data retention days.
         *
         * @return the result
         */
        public long getNormalizeDataRetentionDays() {
            return normalizeDataRetentionDays;
        }

        /**
         * Sets the normalize data retention days.
         *
         * @param normalizeDataRetentionDays the normalizeDataRetentionDays
         */
        public void setNormalizeDataRetentionDays(long normalizeDataRetentionDays) {
            this.normalizeDataRetentionDays = normalizeDataRetentionDays;
        }

        /**
         * Gets the weights.
         *
         * @return the result
         */
        public Weights getWeights() {
            return weights;
        }

        /**
         * Sets the weights.
         *
         * @param weights the weights
         */
        public void setWeights(Weights weights) {
            this.weights = weights;
        }

        /**
         * Gets the thresholds.
         *
         * @return the result
         */
        public Thresholds getThresholds() {
            return thresholds;
        }

        /**
         * Sets the thresholds.
         *
         * @param thresholds the thresholds
         */
        public void setThresholds(Thresholds thresholds) {
            this.thresholds = thresholds;
        }

        /**
         * Gets the dv environments.
         *
         * @return the result
         */
        public List<DvEnvironment> getDvEnvironments() {
            return dvEnvironments;
        }

        /**
         * Sets the dv environments.
         *
         * @param dvEnvironments the dvEnvironments
         */
        public void setDvEnvironments(List<DvEnvironment> dvEnvironments) {
            this.dvEnvironments = dvEnvironments;
        }

        public static class Weights {
            private double availability = 0.4;

            private double performance = 0.4;

            private double resource = 0.2;

            /**
             * Gets the availability.
             *
             * @return the result
             */
            public double getAvailability() {
                return availability;
            }

            /**
             * Sets the availability.
             *
             * @param availability the availability
             */
            public void setAvailability(double availability) {
                this.availability = availability;
            }

            /**
             * Gets the performance.
             *
             * @return the result
             */
            public double getPerformance() {
                return performance;
            }

            /**
             * Sets the performance.
             *
             * @param performance the performance
             */
            public void setPerformance(double performance) {
                this.performance = performance;
            }

            /**
             * Gets the resource.
             *
             * @return the result
             */
            public double getResource() {
                return resource;
            }

            /**
             * Sets the resource.
             *
             * @param resource the resource
             */
            public void setResource(double resource) {
                this.resource = resource;
            }
        }

        public static class Thresholds {
            private double good = 0.9;

            private double warning = 0.7;

            private double bad = 0.5;

            /**
             * Gets the good.
             *
             * @return the result
             */
            public double getGood() {
                return good;
            }

            /**
             * Sets the good.
             *
             * @param good the good
             */
            public void setGood(double good) {
                this.good = good;
            }

            /**
             * Gets the warning.
             *
             * @return the result
             */
            public double getWarning() {
                return warning;
            }

            /**
             * Sets the warning.
             *
             * @param warning the warning
             */
            public void setWarning(double warning) {
                this.warning = warning;
            }

            /**
             * Gets the bad.
             *
             * @return the result
             */
            public double getBad() {
                return bad;
            }

            /**
             * Sets the bad.
             *
             * @param bad the bad
             */
            public void setBad(double bad) {
                this.bad = bad;
            }
        }

        public static class DvEnvironment {
            private String envCode;

            private String envName;

            private String agentSolutionType;

            private String productTypeName;

            private String serverUrl;

            private String utmUser;

            private String utmPassword;

            private String crtContent;

            private String crtFileName;

            private String dns;

            private boolean strictSsl = true;

            /**
             * Gets the env code.
             *
             * @return the result
             */
            public String getEnvCode() {
                return envCode;
            }

            /**
             * Sets the env code.
             *
             * @param envCode the envCode
             */
            public void setEnvCode(String envCode) {
                this.envCode = envCode;
            }

            /**
             * Gets the env name.
             *
             * @return the result
             */
            public String getEnvName() {
                return envName;
            }

            /**
             * Sets the env name.
             *
             * @param envName the envName
             */
            public void setEnvName(String envName) {
                this.envName = envName;
            }

            /**
             * Gets the agent solution type.
             *
             * @return the result
             */
            public String getAgentSolutionType() {
                return agentSolutionType;
            }

            /**
             * Sets the agent solution type.
             *
             * @param agentSolutionType the agentSolutionType
             */
            public void setAgentSolutionType(String agentSolutionType) {
                this.agentSolutionType = agentSolutionType;
            }

            /**
             * Gets the product type name.
             *
             * @return the result
             */
            public String getProductTypeName() {
                return productTypeName;
            }

            /**
             * Sets the product type name.
             *
             * @param productTypeName the productTypeName
             */
            public void setProductTypeName(String productTypeName) {
                this.productTypeName = productTypeName;
            }

            /**
             * Gets the server url.
             *
             * @return the result
             */
            public String getServerUrl() {
                return serverUrl;
            }

            /**
             * Sets the server url.
             *
             * @param serverUrl the serverUrl
             */
            public void setServerUrl(String serverUrl) {
                this.serverUrl = serverUrl;
            }

            /**
             * Gets the utm user.
             *
             * @return the result
             */
            public String getUtmUser() {
                return utmUser;
            }

            /**
             * Sets the utm user.
             *
             * @param utmUser the utmUser
             */
            public void setUtmUser(String utmUser) {
                this.utmUser = utmUser;
            }

            /**
             * Gets the utm password.
             *
             * @return the result
             */
            @com.fasterxml.jackson.annotation.JsonIgnore
            public String getUtmPassword() {
                return utmPassword;
            }

            /**
             * Sets the utm password.
             *
             * @param utmPassword the utmPassword
             */
            public void setUtmPassword(String utmPassword) {
                this.utmPassword = utmPassword;
            }

            /**
             * Gets the crt content.
             *
             * @return the result
             */
            public String getCrtContent() {
                return crtContent;
            }

            /**
             * Sets the crt content.
             *
             * @param crtContent the crtContent
             */
            public void setCrtContent(String crtContent) {
                this.crtContent = crtContent;
            }

            /**
             * Gets the crt file name.
             *
             * @return the result
             */
            public String getCrtFileName() {
                return crtFileName;
            }

            /**
             * Sets the crt file name.
             *
             * @param crtFileName the crtFileName
             */
            public void setCrtFileName(String crtFileName) {
                this.crtFileName = crtFileName;
            }

            /**
             * Gets the dns.
             *
             * @return the result
             */
            public String getDns() {
                return dns;
            }

            /**
             * Sets the dns.
             *
             * @param dns the dns
             */
            public void setDns(String dns) {
                this.dns = dns;
            }

            /**
             * Checks whether the strict ssl.
             *
             * @return the result
             */
            public boolean isStrictSsl() {
                return strictSsl;
            }

            /**
             * Sets the strict ssl.
             *
             * @param strictSsl the strictSsl
             */
            public void setStrictSsl(boolean strictSsl) {
                this.strictSsl = strictSsl;
            }
        }
    }

    public static class Logging {
        private boolean accessLogEnabled = true;

        /**
         * Checks whether the access log enabled.
         *
         * @return the result
         */
        public boolean isAccessLogEnabled() {
            return accessLogEnabled;
        }

        /**
         * Sets the access log enabled.
         *
         * @param accessLogEnabled the accessLogEnabled
         */
        public void setAccessLogEnabled(boolean accessLogEnabled) {
            this.accessLogEnabled = accessLogEnabled;
        }
    }
}
