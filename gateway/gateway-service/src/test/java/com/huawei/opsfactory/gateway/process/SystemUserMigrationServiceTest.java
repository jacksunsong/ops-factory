package com.huawei.opsfactory.gateway.process;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemUserMigrationServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path usersDir;
    private SystemUserMigrationService migrationService;

    @Before
    public void setUp() throws IOException {
        Path gatewayRoot = tempFolder.getRoot().toPath().resolve("gateway");
        usersDir = gatewayRoot.resolve("users");
        Files.createDirectories(usersDir);

        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);

        migrationService = new SystemUserMigrationService(properties);
    }

    @Test
    public void migrateLegacySystemUser_renamesSysDirectory() throws IOException {
        Path legacyDir = usersDir.resolve(SystemUserMigrationService.LEGACY_SYSTEM_USER);
        Files.createDirectories(legacyDir.resolve("agents").resolve("test-agent"));

        migrationService.migrateLegacySystemUser();

        assertFalse(Files.exists(legacyDir));
        assertTrue(Files.isDirectory(usersDir.resolve(GatewayConstants.SYSTEM_USER).resolve("agents").resolve("test-agent")));
    }

    @Test
    public void migrateLegacySystemUser_skipsWhenNoLegacyDirectory() throws IOException {
        migrationService.migrateLegacySystemUser();

        assertFalse(Files.exists(usersDir.resolve(GatewayConstants.SYSTEM_USER)));
    }

    @Test
    public void migrateLegacySystemUser_mergesOnConflict() throws IOException {
        Path legacyFile = usersDir.resolve(SystemUserMigrationService.LEGACY_SYSTEM_USER)
                .resolve("agents").resolve("legacy-agent").resolve("state.txt");
        Files.createDirectories(legacyFile.getParent());
        Files.writeString(legacyFile, "legacy");

        Path existingFile = usersDir.resolve(GatewayConstants.SYSTEM_USER)
                .resolve("agents").resolve("current-agent").resolve("state.txt");
        Files.createDirectories(existingFile.getParent());
        Files.writeString(existingFile, "current");

        migrationService.migrateLegacySystemUser();

        assertFalse(Files.exists(usersDir.resolve(SystemUserMigrationService.LEGACY_SYSTEM_USER)));
        assertTrue(Files.exists(usersDir.resolve(GatewayConstants.SYSTEM_USER).resolve("agents").resolve("legacy-agent").resolve("state.txt")));
        assertTrue(Files.exists(existingFile));
    }
}
