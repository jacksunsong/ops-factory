package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.config.GatewayProperties;
import com.huawei.opsfactory.gateway.filter.AuthWebFilter;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.PrewarmService;
import com.huawei.opsfactory.gateway.service.AgentSkillInstallService;
import com.huawei.opsfactory.gateway.service.SkillInstallConflictException;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

@RunWith(SpringRunner.class)
@WebFluxTest(AgentSkillController.class)
@Import({GatewayProperties.class, AuthWebFilter.class, UserContextFilter.class})
public class AgentSkillControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PrewarmService prewarmService;

    @MockBean
    private AgentSkillInstallService installService;

    @Test
    public void installSkill_asAdmin() throws Exception {
        Mockito.when(installService.install("agent1", "log-analysis"))
                .thenReturn(Map.of(
                        "success", true,
                        "skill", Map.of("id", "log-analysis"),
                        "restartRequired", true));

        webTestClient.post().uri("/gateway/agents/agent1/skills/install")
                .header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"skillId\":\"log-analysis\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.skill.id").isEqualTo("log-analysis")
                .jsonPath("$.restartRequired").isEqualTo(true);
    }

    @Test
    public void installSkill_nonAdminForbidden() {
        webTestClient.post().uri("/gateway/agents/agent1/skills/install")
                .header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"skillId\":\"log-analysis\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    public void installSkill_conflict() throws Exception {
        Mockito.when(installService.install("agent1", "log-analysis"))
                .thenThrow(new SkillInstallConflictException("Skill already installed"));

        webTestClient.post().uri("/gateway/agents/agent1/skills/install")
                .header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"skillId\":\"log-analysis\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    public void uninstallSkill_asAdmin() throws Exception {
        Mockito.when(installService.uninstall("agent1", "log-analysis"))
                .thenReturn(Map.of(
                        "success", true,
                        "skillId", "log-analysis",
                        "restartRequired", true));

        webTestClient.delete().uri("/gateway/agents/agent1/skills/log-analysis")
                .header("x-secret-key", "test")
                .header("x-user-id", "admin")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.skillId").isEqualTo("log-analysis")
                .jsonPath("$.restartRequired").isEqualTo(true);
    }

    @Test
    public void uninstallSkill_nonAdminForbidden() {
        webTestClient.delete().uri("/gateway/agents/agent1/skills/log-analysis")
                .header("x-secret-key", "test")
                .header("x-user-id", "regular-user")
                .exchange()
                .expectStatus().isForbidden();
    }
}
