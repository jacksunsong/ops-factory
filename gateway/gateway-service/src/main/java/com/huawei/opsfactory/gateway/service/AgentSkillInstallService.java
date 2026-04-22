package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.common.model.AgentRegistryEntry;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class AgentSkillInstallService {

    private static final Logger log = LoggerFactory.getLogger(AgentSkillInstallService.class);
    private static final Pattern SKILL_ID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$");

    private final AgentConfigService agentConfigService;
    private final SkillMarketClient skillMarketClient;
    private final GatewayProperties properties;
    private final Yaml yaml = new Yaml();

    public AgentSkillInstallService(
            AgentConfigService agentConfigService,
            SkillMarketClient skillMarketClient,
            GatewayProperties properties) {
        this.agentConfigService = agentConfigService;
        this.skillMarketClient = skillMarketClient;
        this.properties = properties;
    }

    public Map<String, Object> install(String agentId, String requestedSkillId) throws IOException {
        AgentRegistryEntry agent = agentConfigService.findAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent '" + agentId + "' not found");
        }

        String skillId = validateSkillId(requestedSkillId);
        Map<String, Object> marketSkill = skillMarketClient.getSkill(skillId);
        byte[] packageBytes = skillMarketClient.downloadPackage(skillId);
        long maxPackageBytes = properties.getSkillMarket().getMaxPackageSizeMb() * 1024L * 1024L;
        if (packageBytes.length > maxPackageBytes) {
            throw new IllegalArgumentException("Skill package exceeds gateway installation limit");
        }

        String expectedChecksum = stringValue(marketSkill, "checksum");
        String actualChecksum = "sha256:" + sha256(packageBytes);
        if (!expectedChecksum.isBlank() && !expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            throw new IllegalArgumentException("Skill package checksum does not match market metadata");
        }

        Path skillsDir = agentConfigService.getAgentConfigDir(agentId).resolve("skills");
        Path destination = skillsDir.resolve(skillId);
        if (Files.exists(destination)) {
            throw new SkillInstallConflictException("Skill '" + skillId + "' is already installed for agent '" + agentId + "'");
        }

        Files.createDirectories(skillsDir);
        Path tempDir = Files.createTempDirectory(skillsDir, skillId + "-install-");
        try {
            extractPackage(packageBytes, tempDir);
            Path skillMd = tempDir.resolve("SKILL.md");
            if (!Files.isRegularFile(skillMd) || Files.size(skillMd) == 0) {
                throw new IllegalArgumentException("Skill package must contain a non-empty SKILL.md");
            }
            rejectSymbolicLinks(tempDir);
            writeInstallMetadata(tempDir, skillId, actualChecksum);
            Files.move(tempDir, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tempDir);
            throw e;
        }

        agentConfigService.invalidateCache(agentId);
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("id", skillId);
        skill.put("name", stringValue(marketSkill, "name"));
        skill.put("description", stringValue(marketSkill, "description"));
        skill.put("path", "skills/" + skillId);
        skill.put("source", "skill-market");
        skill.put("checksum", actualChecksum);

        log.info("Installed skill id={} agentId={} checksum={}", skillId, agentId, actualChecksum);
        return Map.of(
                "success", true,
                "skill", skill,
                "restartRequired", true);
    }

    public Map<String, Object> uninstall(String agentId, String requestedSkillId) throws IOException {
        AgentRegistryEntry agent = agentConfigService.findAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent '" + agentId + "' not found");
        }

        String skillId = validateSkillId(requestedSkillId);
        Path skillDir = agentConfigService.getAgentConfigDir(agentId).resolve("skills").resolve(skillId).normalize();
        Path skillsDir = agentConfigService.getAgentConfigDir(agentId).resolve("skills").normalize();
        if (!skillDir.startsWith(skillsDir)) {
            throw new IllegalArgumentException("Skill id must use lowercase letters, numbers, and hyphens");
        }
        if (!Files.isDirectory(skillDir)) {
            throw new IllegalArgumentException("Skill '" + skillId + "' is not installed for agent '" + agentId + "'");
        }

        deleteRecursively(skillDir);
        agentConfigService.invalidateCache(agentId);

        log.info("Uninstalled skill id={} agentId={}", skillId, agentId);
        return Map.of(
                "success", true,
                "skillId", skillId,
                "restartRequired", true);
    }

    private void extractPackage(byte[] packageBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packageBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String safeName = safeZipName(entry.getName());
                if (safeName.startsWith("__MACOSX/") || safeName.endsWith("/.DS_Store") || ".DS_Store".equals(safeName)) {
                    continue;
                }
                Path destination = targetDir.resolve(safeName).normalize();
                if (!destination.startsWith(targetDir)) {
                    throw new IllegalArgumentException("Skill package contains unsafe file path");
                }
                Files.createDirectories(destination.getParent());
                try (OutputStream out = Files.newOutputStream(destination)) {
                    zis.transferTo(out);
                }
            }
        }
    }

    private String safeZipName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Skill package contains an empty file name");
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Skill package contains absolute file path");
        }
        for (String part : name.split("/")) {
            if (part.equals("..")) {
                throw new IllegalArgumentException("Skill package contains unsafe parent path");
            }
        }
        return name;
    }

    private void writeInstallMetadata(Path skillDir, String skillId, String checksum) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "skill-market");
        metadata.put("skillId", skillId);
        metadata.put("checksum", checksum);
        metadata.put("installedAt", Instant.now().toString());
        Files.writeString(skillDir.resolve(".opsfactory-skill.yaml"), yaml.dump(metadata));
    }

    private String validateSkillId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Skill id is required");
        }
        String id = value.trim().toLowerCase(Locale.ROOT);
        if (!SKILL_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Skill id must use lowercase letters, numbers, and hyphens");
        }
        return id;
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(new ByteArrayInputStream(data), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to calculate package checksum", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void rejectSymbolicLinks(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            boolean hasSymlink = stream.anyMatch(Files::isSymbolicLink);
            if (hasSymlink) {
                throw new IllegalArgumentException("Skill package must not contain symbolic links");
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || Files.notExists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String stringValue(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        return raw == null ? "" : raw.toString();
    }
}
