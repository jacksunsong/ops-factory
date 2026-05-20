package com.huawei.opsfactory.businessintelligence.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.huawei.opsfactory.businessintelligence.datasource.BiDataProvider;
import com.huawei.opsfactory.businessintelligence.datasource.BiRawData;
import com.huawei.opsfactory.businessintelligence.support.TestLogAppender;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RequestLoggingFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BiDataProvider dataProvider;

    @BeforeEach
    void setUp() {
        given(dataProvider.load()).willReturn(sampleData());
    }

    @Test
    void shouldGenerateRequestIdAndWriteAccessLog() throws Exception {
        try (TestLogAppender appender = TestLogAppender.attachTo(RequestLoggingFilter.class)) {
            MvcResult result = mockMvc.perform(get("/business-intelligence/overview"))
                .andExpect(status().isOk())
                .andReturn();

            String requestId = result.getResponse().getHeader(LoggingKeys.REQUEST_ID_HEADER);

            assertThat(requestId).isNotBlank();
            assertThat(appender.events())
                .anySatisfy(event -> {
                    String loggedRequestId = Objects.toString(event.getContextData().getValue(LoggingKeys.REQUEST_ID), null);
                    assertThat(event.getMessage().getFormattedMessage())
                        .contains("HTTP GET /business-intelligence/overview completed status=200");
                    assertThat(loggedRequestId).isEqualTo(requestId);
                });
        }
    }

    @Test
    void shouldReuseIncomingRequestIdHeader() throws Exception {
        try (TestLogAppender appender = TestLogAppender.attachTo(RequestLoggingFilter.class)) {
            String requestId = "req-fixed-123";

            MvcResult result = mockMvc.perform(get("/business-intelligence/overview")
                    .header(LoggingKeys.REQUEST_ID_HEADER, requestId))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(result.getResponse().getHeader(LoggingKeys.REQUEST_ID_HEADER)).isEqualTo(requestId);
            assertThat(appender.events())
                .anySatisfy(event -> {
                    String loggedRequestId = Objects.toString(event.getContextData().getValue(LoggingKeys.REQUEST_ID), null);
                    assertThat(loggedRequestId).isEqualTo(requestId);
                });
        }
    }

    private static BiRawData sampleData() {
        return new BiRawData(
            List.of(Map.of("ticket_id", "INC-001", "SLA Compliant", "Yes")),
            List.of(Map.of("priority", "P1", "response_sla_min", "15", "resolution_sla_min", "240")),
            List.of(Map.of("ticket_id", "CHG-001", "close_code", "Successful", "incident_ids", "")),
            List.of(Map.of("ticket_id", "REQ-001", "status", "Closed", "close_code", "Fulfilled")),
            List.of(Map.of("ticket_id", "PRB-001", "status", "Resolved")),
            List.of()
        );
    }
}
