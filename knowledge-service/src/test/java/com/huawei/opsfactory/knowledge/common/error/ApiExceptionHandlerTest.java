package com.huawei.opsfactory.knowledge.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new FailingController())
        .setControllerAdvice(new ApiExceptionHandler())
        .build();

    @Test
    void shouldMapRetrievalConfigurationExceptionToServerErrorInsteadOfNotFound() throws Exception {
        mockMvc.perform(get("/__test__/retrieval-config-error").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("RETRIEVAL_CONFIGURATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Embedding dimension mismatch"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/__test__/retrieval-config-error")
        void throwRetrievalConfigurationException() {
            throw new RetrievalConfigurationException("Embedding dimension mismatch");
        }
    }
}
