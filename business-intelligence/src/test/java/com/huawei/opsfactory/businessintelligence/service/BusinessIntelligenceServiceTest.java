package com.huawei.opsfactory.businessintelligence.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.opsfactory.businessintelligence.config.BusinessIntelligenceRuntimeProperties;
import com.huawei.opsfactory.businessintelligence.datasource.BiDataProvider;
import com.huawei.opsfactory.businessintelligence.datasource.BiRawData;
import com.huawei.opsfactory.businessintelligence.model.BiModels.Snapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class BusinessIntelligenceServiceTest {

    @Test
    void shouldCacheOverviewWhenCacheEnabled() {
        AtomicInteger loads = new AtomicInteger();
        BusinessIntelligenceMetricsService metricsService = new BusinessIntelligenceMetricsService(new CountingProvider(loads), runtimeProperties(true));
        BusinessIntelligenceService service = new BusinessIntelligenceService(new CountingProvider(loads), runtimeProperties(true), metricsService);

        Snapshot first = service.getOverview(null, null);
        Snapshot second = service.getOverview(null, null);

        assertEquals(1, loads.get());
        assertEquals(first, second);
        assertEquals(8, first.tabs().size());
        assertNotNull(first.tabContents().get("executive-summary"));
    }

    @Test
    void shouldRefreshAndExportWorkbook() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        BusinessIntelligenceMetricsService metricsService = new BusinessIntelligenceMetricsService(new CountingProvider(loads), runtimeProperties(true));
        BusinessIntelligenceService service = new BusinessIntelligenceService(new CountingProvider(loads), runtimeProperties(true), metricsService);

        Snapshot refreshed = service.refresh(null, null);
        byte[] workbookBytes = service.exportCurrentWorkbook();

        assertEquals(1, loads.get());
        assertTrue(workbookBytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(workbookBytes))) {
            assertEquals("执行摘要", workbook.getSheetAt(0).getSheetName());
            assertEquals("SLA分析", workbook.getSheetAt(1).getSheetName());
        }

        assertArrayEquals(
            refreshed.tabs().stream().map(tab -> tab.id()).toArray(),
            service.getOverview(null, null).tabs().stream().map(tab -> tab.id()).toArray()
        );
    }

    private static BusinessIntelligenceRuntimeProperties runtimeProperties(boolean cacheEnabled) {
        BusinessIntelligenceRuntimeProperties properties = new BusinessIntelligenceRuntimeProperties();
        properties.setCacheEnabled(cacheEnabled);
        return properties;
    }

    private static final class CountingProvider implements BiDataProvider {

        private final AtomicInteger loads;

        private CountingProvider(AtomicInteger loads) {
            this.loads = loads;
        }

        @Override
        public BiRawData load() {
            loads.incrementAndGet();
            return new BiRawData(
                List.of(
                    Map.of(
                        "ticket_id", "INC-001",
                        "title", "Database unavailable",
                        "priority", "P1",
                        "status", "Open",
                        "assigned_to", "Alice",
                        "category", "Database",
                        "SLA Compliant", "No"
                    ),
                    Map.of(
                        "ticket_id", "INC-002",
                        "title", "Network alert",
                        "priority", "P2",
                        "status", "Resolved",
                        "assigned_to", "Bob",
                        "category", "Network",
                        "SLA Compliant", "Yes"
                    )
                ),
                List.of(
                    Map.of(
                        "priority", "P1",
                        "response_sla_min", "15",
                        "resolution_sla_min", "240"
                    )
                ),
                List.of(
                    Map.of(
                        "ticket_id", "CHG-001",
                        "title", "Database patch",
                        "change_type", "Emergency",
                        "status", "Failed",
                        "close_code", "Failed",
                        "incident_ids", "INC-001",
                        "assigned_to", "Carol"
                    )
                ),
                List.of(
                    Map.of(
                        "ticket_id", "REQ-001",
                        "catalog_item", "Access",
                        "status", "Closed",
                        "close_code", "Fulfilled",
                        "assigned_to", "Dora",
                        "requester_dept", "Finance",
                        "satisfaction_score", "4.5"
                    )
                ),
                List.of(
                    Map.of(
                        "ticket_id", "PRB-001",
                        "title", "Recurring database saturation",
                        "status", "Under Investigation",
                        "known_error", "true",
                        "root_cause", "Capacity issue",
                        "cause_code", "Technical Defect",
                        "related_incident_count", "3",
                        "assigned_to", "Evan"
                    )
                ),
                List.of()
            );
        }
    }
}
