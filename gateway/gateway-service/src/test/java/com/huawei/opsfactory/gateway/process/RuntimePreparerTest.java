package com.huawei.opsfactory.gateway.process;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class RuntimePreparerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private RuntimePreparer preparer;
    private Path gatewayRoot;

    @Before
    public void setUp() throws IOException {
        gatewayRoot = tempFolder.getRoot().toPath().resolve("gateway");
        Files.createDirectories(gatewayRoot.resolve("agents").resolve("test-agent").resolve("config"));
        Files.writeString(gatewayRoot.resolve("agents").resolve("test-agent").resolve("config").resolve("config.yaml"),
                "GOOSE_PROVIDER: openai\n");
        Files.writeString(gatewayRoot.resolve("agents").resolve("test-agent").resolve("AGENTS.md"),
                "# Test\n");
        Files.createDirectories(gatewayRoot.resolve("users"));

        GatewayProperties properties = new GatewayProperties();
        GatewayProperties.Paths paths = new GatewayProperties.Paths();
        paths.setProjectRoot(tempFolder.getRoot().getAbsolutePath());
        properties.setPaths(paths);

        preparer = new RuntimePreparer(properties);
    }

    @Test
    public void testPrepare_createsDirectories() throws IOException {
        Path result = preparer.prepare("test-agent", "user1");

        assertTrue(Files.isDirectory(result));
        assertTrue(Files.isDirectory(result.resolve("data")));
        assertTrue(Files.isDirectory(result.resolve("uploads")));
        assertTrue(Files.isDirectory(result.resolve("home")));
    }

    @Test
    public void testPrepare_createsConfigSymlink() throws IOException {
        Path result = preparer.prepare("test-agent", "user1");

        Path configLink = result.resolve("config");
        assertTrue(Files.exists(configLink));
        assertTrue(Files.isSymbolicLink(configLink));
    }

    @Test
    public void testPrepare_createsAgentsMdSymlink() throws IOException {
        Path result = preparer.prepare("test-agent", "user1");

        Path mdLink = result.resolve("AGENTS.md");
        assertTrue(Files.exists(mdLink));
        assertTrue(Files.isSymbolicLink(mdLink));
    }

    @Test
    public void testPrepare_idempotent() throws IOException {
        Path result1 = preparer.prepare("test-agent", "user1");
        Path result2 = preparer.prepare("test-agent", "user1");

        assertEquals(result1, result2);
        assertTrue(Files.isSymbolicLink(result2.resolve("config")));
    }

    @Test
    public void testPrepare_removesDisallowedSkillDirectories() throws IOException {
        Path runtimeRoot = gatewayRoot.resolve("users").resolve("user1").resolve("agents").resolve("test-agent");
        Files.createDirectories(runtimeRoot.resolve(".goose").resolve("skills").resolve("local-skill"));
        Files.createDirectories(runtimeRoot.resolve(".claude").resolve("skills").resolve("local-skill"));
        Files.createDirectories(runtimeRoot.resolve(".agents").resolve("skills").resolve("local-skill"));
        Files.createDirectories(runtimeRoot.resolve("home").resolve(".agents").resolve("skills").resolve("global-skill"));
        Files.createDirectories(runtimeRoot.resolve("home").resolve(".claude").resolve("skills").resolve("global-skill"));
        Files.createDirectories(runtimeRoot.resolve("home").resolve(".config").resolve("agents").resolve("skills").resolve("global-skill"));
        Files.createDirectories(runtimeRoot.resolve(".goose").resolve("memory"));
        Files.writeString(runtimeRoot.resolve(".goose").resolve("memory").resolve("notes.txt"), "keep");

        Path result = preparer.prepare("test-agent", "user1");

        org.junit.Assert.assertFalse(Files.exists(result.resolve(".goose").resolve("skills")));
        org.junit.Assert.assertFalse(Files.exists(result.resolve(".claude").resolve("skills")));
        org.junit.Assert.assertFalse(Files.exists(result.resolve(".agents").resolve("skills")));
        org.junit.Assert.assertFalse(Files.exists(result.resolve("home").resolve(".agents").resolve("skills")));
        org.junit.Assert.assertFalse(Files.exists(result.resolve("home").resolve(".claude").resolve("skills")));
        org.junit.Assert.assertFalse(Files.exists(result.resolve("home").resolve(".config").resolve("agents").resolve("skills")));
        assertTrue(Files.exists(result.resolve(".goose").resolve("memory").resolve("notes.txt")));
    }

    private void assertEquals(Path expected, Path actual) {
        org.junit.Assert.assertEquals(expected.toString(), actual.toString());
    }
}
