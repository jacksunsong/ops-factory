package com.huawei.opsfactory.gateway.process;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class RuntimePreparer {

    private static final Logger log = LoggerFactory.getLogger(RuntimePreparer.class);

    private final GatewayProperties properties;

    public RuntimePreparer(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * Prepare the per-user runtime directory for an agent instance.
     * Creates directories and symlinks to shared agent config.
     *
     * @return the runtime root path for this (agentId, userId)
     */
    public Path prepare(String agentId, String userId) throws IOException {
        Path gatewayRoot = properties.getGatewayRootPath();
        Path agentsDir = gatewayRoot.resolve(properties.getPaths().getAgentsDir());
        Path usersDir = gatewayRoot.resolve(properties.getPaths().getUsersDir());

        Path userAgentDir = usersDir.resolve(userId).resolve("agents").resolve(agentId);
        Files.createDirectories(userAgentDir);
        Files.createDirectories(userAgentDir.resolve("home"));

        cleanDisallowedSkillDirs(userAgentDir);

        // Symlink config -> shared agent config
        Path configLink = userAgentDir.resolve("config");
        Path agentConfigDir = agentsDir.resolve(agentId).resolve("config");
        if (!Files.exists(configLink) && Files.exists(agentConfigDir)) {
            Path relative = userAgentDir.relativize(agentConfigDir);
            Files.createSymbolicLink(configLink, relative);
            log.info("Created config symlink: {} -> {}", configLink, relative);
        }

        // Symlink AGENTS.md
        Path agentsMdLink = userAgentDir.resolve("AGENTS.md");
        Path agentsMdSource = agentsDir.resolve(agentId).resolve("AGENTS.md");
        if (!Files.exists(agentsMdLink) && Files.exists(agentsMdSource)) {
            Path relative = userAgentDir.relativize(agentsMdSource);
            Files.createSymbolicLink(agentsMdLink, relative);
        }

        // Create data and uploads dirs
        Files.createDirectories(userAgentDir.resolve("data"));
        Files.createDirectories(userAgentDir.resolve("uploads"));

        return userAgentDir;
    }

    private void cleanDisallowedSkillDirs(Path userAgentDir) throws IOException {
        List<Path> skillDirs = List.of(
                userAgentDir.resolve(".goose").resolve("skills"),
                userAgentDir.resolve(".claude").resolve("skills"),
                userAgentDir.resolve(".agents").resolve("skills"),
                userAgentDir.resolve("home").resolve(".agents").resolve("skills"),
                userAgentDir.resolve("home").resolve(".claude").resolve("skills"),
                userAgentDir.resolve("home").resolve(".config").resolve("agents").resolve("skills"));

        for (Path skillDir : skillDirs) {
            deleteIfExists(skillDir);
        }
    }

    private void deleteIfExists(Path path) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path);
            return;
        }

        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : paths) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
