package com.huawei.opsfactory.businessintelligence.service;

import com.huawei.opsfactory.businessintelligence.config.BusinessIntelligenceRuntimeProperties;
import com.huawei.opsfactory.businessintelligence.datasource.BiDataProvider;
import com.huawei.opsfactory.businessintelligence.datasource.BiRawData;
import com.huawei.opsfactory.businessintelligence.model.BiModels;
import com.huawei.opsfactory.businessintelligence.model.BiModels.ChartDatum;
import com.huawei.opsfactory.businessintelligence.model.BiModels.ChartSection;
import com.huawei.opsfactory.businessintelligence.model.BiModels.MetricCard;
import com.huawei.opsfactory.businessintelligence.model.BiModels.Snapshot;
import com.huawei.opsfactory.businessintelligence.model.BiModels.TabContent;
import com.huawei.opsfactory.businessintelligence.model.BiModels.TabMeta;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class BusinessIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(BusinessIntelligenceService.class);

    private static final List<TabMeta> TABS = List.of(
        new TabMeta("executive-summary", "执行摘要"),
        new TabMeta("sla-analysis", "SLA分析"),
        new TabMeta("incident-analysis", "事件分析"),
        new TabMeta("change-analysis", "变更分析"),
        new TabMeta("request-analysis", "请求分析"),
        new TabMeta("problem-analysis", "问题分析"),
        new TabMeta("cross-process", "跨流程关联"),
        new TabMeta("personnel-efficiency", "人员与效率")
    );
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"),
        DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );
    private static final List<String> PRIORITY_ORDER = List.of("P1", "P2", "P3", "P4");

    private final BiDataProvider dataProvider;
    private final BusinessIntelligenceRuntimeProperties runtimeProperties;
    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    private record IncidentSlaRecord(
        String orderNumber,
        String orderName,
        String priority,
        String category,
        String resolver,
        LocalDateTime beginDate,
        double responseMinutes,
        double resolutionMinutes,
        boolean responseMet,
        boolean resolutionMet
    ) {
        private boolean overallMet() {
            return responseMet && resolutionMet;
        }

        private boolean anyBreached() {
            return !overallMet();
        }

        private String violationType() {
            if (!responseMet && !resolutionMet) {
                return "双违约";
            }
            if (!responseMet) {
                return "响应违约";
            }
            if (!resolutionMet) {
                return "解决违约";
            }
            return "达标";
        }
    }

    public BusinessIntelligenceService(BiDataProvider dataProvider, BusinessIntelligenceRuntimeProperties runtimeProperties) {
        this.dataProvider = dataProvider;
        this.runtimeProperties = runtimeProperties;
    }

    public Snapshot getOverview() {
        Snapshot snapshot = cache.get();
        if (snapshot != null && runtimeProperties.isCacheEnabled()) {
            log.debug(
                "Returning cached business intelligence snapshot refreshedAt={} tabCount={}",
                snapshot.refreshedAt(),
                snapshot.tabs().size()
            );
            return snapshot;
        }
        return refresh();
    }

    public synchronized Snapshot refresh() {
        long startedAt = System.currentTimeMillis();
        try {
            BiRawData rawData = dataProvider.load();
            Snapshot snapshot = buildSnapshot(rawData);
            cache.set(snapshot);
            log.info(
                "Refreshed business intelligence snapshot incidents={} incidentSlaCriteria={} changes={} requests={} problems={} tabCount={} durationMs={}",
                rawData.incidents().size(),
                rawData.incidentSlaCriteria().size(),
                rawData.changes().size(),
                rawData.requests().size(),
                rawData.problems().size(),
                snapshot.tabs().size(),
                System.currentTimeMillis() - startedAt
            );
            return snapshot;
        } catch (RuntimeException ex) {
            log.error(
                "Failed to refresh business intelligence snapshot durationMs={}",
                System.currentTimeMillis() - startedAt,
                ex
            );
            throw ex;
        }
    }

    public TabContent getTab(String tabId) {
        Snapshot snapshot = getOverview();
        TabContent content = snapshot.tabContents().get(tabId);
        if (content == null) {
            throw new IllegalArgumentException("Unknown tab: " + tabId);
        }
        log.debug("Resolved business intelligence tab tabId={} label={}", tabId, content.label());
        return content;
    }

    public byte[] exportCurrentWorkbook() {
        Snapshot snapshot = getOverview();
        long startedAt = System.currentTimeMillis();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (TabMeta tab : snapshot.tabs()) {
                TabContent content = snapshot.tabContents().get(tab.id());
                if (content == null) {
                    continue;
                }
                XSSFSheet sheet = workbook.createSheet(safeSheetName(content.label()));
                int rowIndex = 0;
                rowIndex = writeTitle(sheet, rowIndex, content.label(), content.description());
                rowIndex = writeCards(sheet, rowIndex, content.cards());
                rowIndex = writeCharts(sheet, rowIndex, content.charts());
                writeTables(sheet, rowIndex, content.tables());
                for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
                    sheet.autoSizeColumn(columnIndex);
                }
            }
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            log.info(
                "Exported business intelligence workbook refreshedAt={} tabCount={} byteSize={} durationMs={}",
                snapshot.refreshedAt(),
                snapshot.tabs().size(),
                bytes.length,
                System.currentTimeMillis() - startedAt
            );
            return bytes;
        } catch (IOException exception) {
            log.error(
                "Failed to export business intelligence workbook refreshedAt={} tabCount={} durationMs={}",
                snapshot.refreshedAt(),
                snapshot.tabs().size(),
                System.currentTimeMillis() - startedAt,
                exception
            );
            throw new IllegalStateException("Failed to export business intelligence workbook", exception);
        }
    }

    private Snapshot buildSnapshot(BiRawData rawData) {
        Map<String, TabContent> contents = new LinkedHashMap<>();
        contents.put("executive-summary", buildExecutiveSummary(rawData));
        contents.put("sla-analysis", buildSlaAnalysis(rawData));
        contents.put("incident-analysis", buildIncidentAnalysis(rawData));
        contents.put("change-analysis", buildChangeAnalysis(rawData));
        contents.put("request-analysis", buildRequestAnalysis(rawData));
        contents.put("problem-analysis", buildProblemAnalysis(rawData));
        contents.put("cross-process", buildCrossProcess(rawData));
        contents.put("personnel-efficiency", buildPersonnelEfficiency(rawData));
        return new Snapshot(Instant.now(), TABS, contents);
    }

    private TabContent buildExecutiveSummary(BiRawData rawData) {
        long incidentSlaBreached = countByValue(rawData.incidents(), "SLA Compliant", "No");
        long changeFailures = rawData.changes().stream().filter(row -> !isYes(row.get("Success"))).count();
        long requestOpen = rawData.requests().stream().filter(row -> !"Fulfilled".equalsIgnoreCase(clean(row.get("Status")))).count();
        long problemOpen = rawData.problems().stream().filter(row -> !matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed"))).count();

        return new TabContent(
            "executive-summary",
            "执行摘要",
            "聚合四类 ITIL 数据的核心规模与风险摘要。",
            buildExecutiveSummaryContent(rawData, incidentSlaBreached, changeFailures, requestOpen, problemOpen),
            null,
            List.of(
                card("incident-sla-rate", "事件 SLA 达成率", percentage(countByValue(rawData.incidents(), "SLA Compliant", "Yes"), rawData.incidents().size()), toneFromScore(percentageValue(countByValue(rawData.incidents(), "SLA Compliant", "Yes"), rawData.incidents().size()), 0.9, 0.75)),
                card("incident-mttr", "MTTR", formatHours(average(rawData.incidents(), "Resolution Time(m)") / 60.0), toneFromInverse(average(rawData.incidents(), "Resolution Time(m)") / 60.0, 12, 24)),
                card("change-success-rate", "变更成功率", percentage(countByValue(rawData.changes(), "Success", "Yes"), rawData.changes().size()), toneFromScore(percentageValue(countByValue(rawData.changes(), "Success", "Yes"), rawData.changes().size()), 0.9, 0.8)),
                card("change-incident-rate", "变更致事件率", percentage(countByValue(rawData.changes(), "Incident Caused", "Yes"), rawData.changes().size()), toneFromInverse(percentageValue(countByValue(rawData.changes(), "Incident Caused", "Yes"), rawData.changes().size()), 0.05, 0.1)),
                card("request-csat", "请求满意度", formatNumber(average(rawData.requests(), "Satisfaction Score")), toneFromScore(average(rawData.requests(), "Satisfaction Score") / 5.0, 0.8, 0.7)),
                card("problem-closure-rate", "问题关闭率", percentage(rawData.problems().stream().filter(row -> matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed"))).count(), rawData.problems().size()), toneFromScore(percentageValue(rawData.problems().stream().filter(row -> matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed"))).count(), rawData.problems().size()), 0.75, 0.55))
            ),
            List.of(),
            List.of(
                table("summary-risks", "关键关注项", List.of("指标", "当前值", "说明"), List.of(
                    List.of("事件 SLA 违约", String.valueOf(incidentSlaBreached), "基于 incidents 数据中的 SLA Compliant=No"),
                    List.of("失败变更", String.valueOf(changeFailures), "基于 changes 数据中的 Success!=Yes"),
                    List.of("未完成请求", String.valueOf(requestOpen), "基于 requests 的非 Fulfilled 状态"),
                    List.of("未关闭问题", String.valueOf(problemOpen), "基于 problems 的非 Resolved/Closed 状态")
                ))
            )
        );
    }

    private TabContent buildSlaAnalysis(BiRawData rawData) {
        List<IncidentSlaRecord> incidents = buildIncidentSlaRecords(rawData);
        BiModels.SlaAnalysisSummary summary = buildSlaAnalysisSummary(incidents);
        return new TabContent(
            "sla-analysis",
            "SLA分析",
            "以响应与解决双维度观察事件履约状态、风险分层与违约对象。",
            null,
            summary,
            List.of(
                card("sla-overall", "综合达成率", summary.hero().overallComplianceRate(), toneFromRate(summary.hero().overallComplianceRate())),
                card("sla-response", "响应达成率", summary.hero().responseComplianceRate(), summary.response().tone()),
                card("sla-resolution", "解决达成率", summary.hero().resolutionComplianceRate(), summary.resolution().tone()),
                card("sla-breached", "违约总数", summary.hero().breachedCount(), summary.hero().breachedCount() > 0 ? "warning" : "success"),
                card("sla-high-priority", "P1/P2达成率", summary.hero().highPriorityComplianceRate(), toneFromRate(summary.hero().highPriorityComplianceRate()))
            ),
            List.of(
                chart("priority-resolution", "优先级解决达成率", summary.priorityRows().stream()
                    .map(row -> new ChartDatum(row.priority(), parsePercentage(row.resolutionComplianceRate())))
                    .toList()),
                chart("violation-breakdown", "违约类型分布", List.of(
                    new ChartDatum("响应违约", summary.violationBreakdown().responseBreached()),
                    new ChartDatum("解决违约", summary.violationBreakdown().resolutionBreached()),
                    new ChartDatum("双违约", summary.violationBreakdown().bothBreached())
                ))
            ),
            List.of(
                table("sla-priority-table", "优先级履约分层", List.of("优先级", "工单量", "响应达成率", "解决达成率", "违约数", "平均解决时长"), summary.priorityRows().stream()
                    .map(row -> List.of(
                        row.priority(),
                        String.valueOf(row.totalCount()),
                        row.responseComplianceRate(),
                        row.resolutionComplianceRate(),
                        String.valueOf(row.breachedCount()),
                        row.averageResolutionDuration()
                    )).toList()),
                table("sla-violation-samples", "违约样本", List.of("编号", "标题", "优先级", "类别", "处理人", "响应时长", "解决时长", "违约类型"), summary.violationSamples().stream()
                    .map(sample -> List.of(
                        sample.orderNumber(),
                        sample.orderName(),
                        sample.priority(),
                        sample.category(),
                        sample.resolver(),
                        sample.responseDuration(),
                        sample.resolutionDuration(),
                        sample.violationType()
                    )).toList())
            )
        );
    }

    private TabContent buildIncidentAnalysis(BiRawData rawData) {
        return new TabContent(
            "incident-analysis",
            "事件分析",
            "展示事件量、优先级、类别和处理人分布。",
            null,
            null,
            List.of(
                card("incident-total", "事件总数", rawData.incidents().size(), "neutral"),
                card("incident-high-priority", "P1/P2 事件", rawData.incidents().stream().filter(row -> matchesAny(clean(row.get("Priority")), List.of("P1", "P2"))).count(), "warning"),
                card("incident-open", "未解决事件", rawData.incidents().stream().filter(row -> !"Resolved".equalsIgnoreCase(clean(row.get("Order Status")))).count(), "warning")
            ),
            List.of(
                chart("incident-priority-chart", "优先级分布", topCounts(rawData.incidents(), "Priority", 6)),
                chart("incident-category-chart", "类别分布", topCounts(rawData.incidents(), "Category", 8))
            ),
            List.of(
                table("incident-resolver-table", "处理人工作量", List.of("处理人", "事件数"), rowsFromChart(topCounts(rawData.incidents(), "Resolver", 10))),
                table("incident-recent-table", "近期事件样本", List.of("编号", "标题", "优先级", "状态"), rawData.incidents().stream().limit(10)
                    .map(row -> List.of(
                        defaultLabel(row.get("Order Number"), "—"),
                        defaultLabel(row.get("Order Name"), "—"),
                        defaultLabel(row.get("Priority"), "—"),
                        defaultLabel(row.get("Order Status"), "—")
                    )).toList())
            )
        );
    }

    private TabContent buildChangeAnalysis(BiRawData rawData) {
        long successCount = countByValue(rawData.changes(), "Success", "Yes");
        long emergencyCount = countByValue(rawData.changes(), "Change Type", "Emergency");
        long incidentCount = countByValue(rawData.changes(), "Incident Caused", "Yes");
        return new TabContent(
            "change-analysis",
            "变更分析",
            "展示变更成功率、紧急变更占比和失败样本。",
            null,
            null,
            List.of(
                card("change-total", "变更总数", rawData.changes().size(), "neutral"),
                card("change-success", "成功率", percentage(successCount, rawData.changes().size()), "success"),
                card("change-emergency", "紧急变更", emergencyCount, emergencyCount > 0 ? "warning" : "success"),
                card("change-incident", "引发事件的变更", incidentCount, incidentCount > 0 ? "warning" : "success")
            ),
            List.of(
                chart("change-type-chart", "变更类型", topCounts(rawData.changes(), "Change Type", 6)),
                chart("change-status-chart", "变更状态", topCounts(rawData.changes(), "Status", 8))
            ),
            List.of(
                table("change-failed-table", "失败或回退样本", List.of("编号", "标题", "状态", "是否成功", "是否回退"), rawData.changes().stream()
                    .filter(row -> !isYes(row.get("Success")) || isYes(row.get("Backout Performed")))
                    .limit(10)
                    .map(row -> List.of(
                        defaultLabel(row.get("Change Number"), "—"),
                        defaultLabel(row.get("Change Title"), "—"),
                        defaultLabel(row.get("Status"), "—"),
                        defaultLabel(row.get("Success"), "—"),
                        defaultLabel(row.get("Backout Performed"), "—")
                    )).toList())
            )
        );
    }

    private TabContent buildRequestAnalysis(BiRawData rawData) {
        long fulfilled = countByValue(rawData.requests(), "Status", "Fulfilled");
        long slaMet = countByValue(rawData.requests(), "SLA Met", "Yes");
        double averageCsat = average(rawData.requests(), "Satisfaction Score");
        return new TabContent(
            "request-analysis",
            "请求分析",
            "展示请求履约、SLA 和满意度概况。",
            null,
            null,
            List.of(
                card("request-total", "请求总数", rawData.requests().size(), "neutral"),
                card("request-fulfilled", "已完成请求", fulfilled, "success"),
                card("request-sla", "SLA 达成率", percentage(slaMet, rawData.requests().size()), "success"),
                card("request-csat", "平均满意度", formatNumber(averageCsat), averageCsat >= 4 ? "success" : "warning")
            ),
            List.of(
                chart("request-type-chart", "请求类型", topCounts(rawData.requests(), "Request Type", 8)),
                chart("request-dept-chart", "部门分布", topCounts(rawData.requests(), "Requester Dept", 8))
            ),
            List.of(
                table("request-assignee-table", "处理人工作量", List.of("处理人", "请求数"), rowsFromChart(topCounts(rawData.requests(), "Assignee", 10)))
            )
        );
    }

    private TabContent buildProblemAnalysis(BiRawData rawData) {
        long closed = rawData.problems().stream().filter(row -> matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed"))).count();
        long rcaComplete = rawData.problems().stream().filter(row -> !clean(row.get("Root Cause")).isBlank()).count();
        long knownError = countByValue(rawData.problems(), "Known Error", "Yes");
        return new TabContent(
            "problem-analysis",
            "问题分析",
            "展示问题闭环、根因分析和已知错误分布。",
            null,
            null,
            List.of(
                card("problem-total", "问题总数", rawData.problems().size(), "neutral"),
                card("problem-closed", "已关闭问题", closed, "success"),
                card("problem-rca", "已完成 RCA", rcaComplete, "success"),
                card("problem-known-error", "已知错误", knownError, "warning")
            ),
            List.of(
                chart("problem-status-chart", "问题状态", topCounts(rawData.problems(), "Status", 8)),
                chart("problem-category-chart", "根因类别", topCounts(rawData.problems(), "Root Cause Category", 8))
            ),
            List.of(
                table("problem-open-table", "未关闭问题", List.of("编号", "标题", "状态", "关联事件"), rawData.problems().stream()
                    .filter(row -> !matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed")))
                    .limit(10)
                    .map(row -> List.of(
                        defaultLabel(row.get("Problem Number"), "—"),
                        defaultLabel(row.get("Problem Title"), "—"),
                        defaultLabel(row.get("Status"), "—"),
                        defaultLabel(row.get("Related Incidents"), "0")
                    )).toList())
            )
        );
    }

    private TabContent buildCrossProcess(BiRawData rawData) {
        long changeIncidentCount = countByValue(rawData.changes(), "Incident Caused", "Yes");
        long relatedProblemIncidents = rawData.problems().stream().mapToLong(row -> parseLong(row.get("Related Incidents"))).sum();
        long linkedChanges = rawData.changes().stream().filter(row -> !clean(row.get("Related Incidents")).isBlank()).count();
        return new TabContent(
            "cross-process",
            "跨流程关联",
            "展示变更、事件和问题之间的关联规模。",
            null,
            null,
            List.of(
                card("cross-change-incident", "引发事件的变更", changeIncidentCount, changeIncidentCount > 0 ? "warning" : "success"),
                card("cross-problem-incident", "问题关联事件数", relatedProblemIncidents, "neutral"),
                card("cross-linked-changes", "有事件关联的变更", linkedChanges, "neutral")
            ),
            List.of(
                chart("cross-overview-chart", "跨流程关联规模", List.of(
                    new ChartDatum("变更引发事件", changeIncidentCount),
                    new ChartDatum("问题关联事件", relatedProblemIncidents),
                    new ChartDatum("有事件关联的变更", linkedChanges)
                ))
            ),
            List.of(
                table("cross-change-table", "变更与事件关联样本", List.of("变更编号", "标题", "相关事件", "是否引发事件"), rawData.changes().stream()
                    .filter(row -> !clean(row.get("Related Incidents")).isBlank() || isYes(row.get("Incident Caused")))
                    .limit(10)
                    .map(row -> List.of(
                        defaultLabel(row.get("Change Number"), "—"),
                        defaultLabel(row.get("Change Title"), "—"),
                        defaultLabel(row.get("Related Incidents"), "—"),
                        defaultLabel(row.get("Incident Caused"), "—")
                    )).toList())
            )
        );
    }

    private TabContent buildPersonnelEfficiency(BiRawData rawData) {
        return new TabContent(
            "personnel-efficiency",
            "人员与效率",
            "聚焦处理人、实施人和负责人工作量分布。",
            null,
            null,
            List.of(
                card("resolver-count", "事件处理人数", distinctCount(rawData.incidents(), "Resolver"), "neutral"),
                card("implementer-count", "变更实施人数", distinctCount(rawData.changes(), "Implementer"), "neutral"),
                card("assignee-count", "请求处理人数", distinctCount(rawData.requests(), "Assignee"), "neutral"),
                card("problem-owner-count", "问题处理人数", distinctCount(rawData.problems(), "Resolver"), "neutral")
            ),
            List.of(
                chart("resolver-workload-chart", "事件处理人工作量", topCounts(rawData.incidents(), "Resolver", 10)),
                chart("request-assignee-workload-chart", "请求处理人工作量", topCounts(rawData.requests(), "Assignee", 10))
            ),
            List.of(
                table("change-implementer-table", "变更实施人工作量", List.of("实施人", "变更数"), rowsFromChart(topCounts(rawData.changes(), "Implementer", 10))),
                table("problem-resolver-table", "问题处理人工作量", List.of("处理人", "问题数"), rowsFromChart(topCounts(rawData.problems(), "Resolver", 10)))
            )
        );
    }

    private MetricCard card(String id, String label, Object value, String tone) {
        return new MetricCard(id, label, String.valueOf(value), tone);
    }

    private BiModels.ExecutiveSummary buildExecutiveSummaryContent(BiRawData rawData, long incidentSlaBreached, long changeFailures, long requestOpen, long problemOpen) {
        double incidentSlaRate = percentageValue(countByValue(rawData.incidents(), "SLA Compliant", "Yes"), rawData.incidents().size());
        double incidentMttrHours = average(rawData.incidents(), "Resolution Time(m)") / 60.0;
        double changeSuccessRate = percentageValue(countByValue(rawData.changes(), "Success", "Yes"), rawData.changes().size());
        double changeIncidentRate = percentageValue(countByValue(rawData.changes(), "Incident Caused", "Yes"), rawData.changes().size());
        double requestSlaRate = percentageValue(countByValue(rawData.requests(), "SLA Met", "Yes"), rawData.requests().size());
        double requestCsat = average(rawData.requests(), "Satisfaction Score");
        double problemClosureRate = percentageValue(rawData.problems().stream().filter(row -> matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed"))).count(), rawData.problems().size());
        double backlogRate = percentageValue(problemOpen + requestOpen, rawData.problems().size() + rawData.requests().size());

        double incidentHealth = weightedAverage(List.of(
            scoreHigherBetter(incidentSlaRate, 0.95, 0.85),
            scoreLowerBetter(incidentMttrHours, 12, 24),
            scoreLowerBetter(percentageValue(rawData.incidents().stream().filter(row -> matchesAny(clean(row.get("Priority")), List.of("P1", "P2"))).count(), rawData.incidents().size()), 0.12, 0.22)
        ));
        double changeHealth = weightedAverage(List.of(
            scoreHigherBetter(changeSuccessRate, 0.95, 0.85),
            scoreLowerBetter(changeIncidentRate, 0.05, 0.12)
        ));
        double requestHealth = weightedAverage(List.of(
            scoreHigherBetter(requestSlaRate, 0.9, 0.75),
            scoreHigherBetter(requestCsat / 5.0, 0.82, 0.7)
        ));
        double problemHealth = weightedAverage(List.of(
            scoreHigherBetter(problemClosureRate, 0.75, 0.55),
            scoreLowerBetter(backlogRate, 0.25, 0.45)
        ));

        double healthScore = weightedScore(Map.of(
            "incident", incidentHealth,
            "change", changeHealth,
            "request", requestHealth,
            "problem", problemHealth
        ));
        String grade = gradeForScore(healthScore);

        List<BiModels.ExecutiveRisk> risks = buildExecutiveRisks(incidentSlaBreached, changeFailures, requestOpen, problemOpen, changeIncidentRate, requestCsat, problemClosureRate);
        BiModels.RiskSummary riskSummary = new BiModels.RiskSummary(
            (int) risks.stream().filter(risk -> "Critical".equals(risk.priority())).count(),
            (int) risks.stream().filter(risk -> "Warning".equals(risk.priority())).count(),
            (int) risks.stream().filter(risk -> "Attention".equals(risk.priority())).count(),
            risks.stream().limit(5).toList()
        );

        List<BiModels.ProcessHealth> processHealths = List.of(
            new BiModels.ProcessHealth("incident", "事件", formatScore(incidentHealth), toneFromNormalizedScore(incidentHealth), incidentHealthSummary(incidentSlaRate, incidentMttrHours)),
            new BiModels.ProcessHealth("change", "变更", formatScore(changeHealth), toneFromNormalizedScore(changeHealth), "成功率 " + percentage(changeSuccessRate) + "，致事件率 " + percentage(changeIncidentRate)),
            new BiModels.ProcessHealth("request", "请求", formatScore(requestHealth), toneFromNormalizedScore(requestHealth), "SLA " + percentage(requestSlaRate) + "，满意度 " + formatNumber(requestCsat)),
            new BiModels.ProcessHealth("problem", "问题", formatScore(problemHealth), toneFromNormalizedScore(problemHealth), "关闭率 " + percentage(problemClosureRate) + "，积压 " + (requestOpen + problemOpen))
        );

        List<BiModels.TrendPoint> trendPoints = buildTrendPoints(rawData);
        String summary = buildExecutiveSummarySentence(grade, riskSummary, processHealths);
        String changeHint = buildTrendHint(trendPoints);
        String periodLabel = buildPeriodLabel(rawData);

        return new BiModels.ExecutiveSummary(
            new BiModels.ExecutiveHero(formatScore(healthScore), grade, summary, changeHint, periodLabel),
            processHealths,
            riskSummary,
            new BiModels.TrendSection("月度健康趋势", "健康分与高优先级事件同步观察。", trendPoints)
        );
    }

    private ChartSection chart(String id, String title, List<ChartDatum> items) {
        return new ChartSection(id, title, "bar", items);
    }

    private BiModels.TableSection table(String id, String title, List<String> columns, List<List<String>> rows) {
        return new BiModels.TableSection(id, title, columns, rows);
    }

    private BiModels.SlaAnalysisSummary buildSlaAnalysisSummary(List<IncidentSlaRecord> incidents) {
        long responseBreached = incidents.stream().filter(record -> !record.responseMet()).count();
        long resolutionBreached = incidents.stream().filter(record -> !record.resolutionMet()).count();
        long bothBreached = incidents.stream().filter(record -> !record.responseMet() && !record.resolutionMet()).count();
        long overallBreached = incidents.stream().filter(IncidentSlaRecord::anyBreached).count();
        double overallRate = percentageValue(incidents.stream().filter(IncidentSlaRecord::overallMet).count(), incidents.size());
        double responseRate = percentageValue(incidents.stream().filter(IncidentSlaRecord::responseMet).count(), incidents.size());
        double resolutionRate = percentageValue(incidents.stream().filter(IncidentSlaRecord::resolutionMet).count(), incidents.size());
        List<IncidentSlaRecord> highPriority = incidents.stream().filter(record -> matchesAny(record.priority(), List.of("P1", "P2"))).toList();
        double highPriorityRate = percentageValue(highPriority.stream().filter(IncidentSlaRecord::overallMet).count(), highPriority.size());

        List<BiModels.SlaPriorityRow> priorityRows = PRIORITY_ORDER.stream()
            .map(priority -> buildPriorityRow(priority, incidents.stream().filter(record -> priority.equalsIgnoreCase(record.priority())).toList()))
            .filter(Objects::nonNull)
            .toList();

        List<BiModels.SlaRiskRow> categoryRisks = rankSlaRisks(incidents, IncidentSlaRecord::category);
        List<BiModels.SlaRiskRow> resolverRisks = rankSlaRisks(incidents, IncidentSlaRecord::resolver);
        List<BiModels.SlaTrendPoint> trends = buildSlaTrendPoints(incidents);

        String summary = buildSlaSummarySentence(responseRate, resolutionRate, priorityRows, categoryRisks);
        return new BiModels.SlaAnalysisSummary(
            new BiModels.SlaHero(
                summary,
                percentage(overallRate),
                percentage(responseRate),
                percentage(resolutionRate),
                overallBreached,
                percentage(highPriorityRate)
            ),
            buildSlaDimensionCard("响应 SLA", responseRate, incidents.stream().mapToDouble(IncidentSlaRecord::responseMinutes).average().orElse(0), percentile(incidents.stream().map(IncidentSlaRecord::responseMinutes).toList(), 0.9), responseBreached),
            buildSlaDimensionCard("解决 SLA", resolutionRate, incidents.stream().mapToDouble(IncidentSlaRecord::resolutionMinutes).average().orElse(0), percentile(incidents.stream().map(IncidentSlaRecord::resolutionMinutes).toList(), 0.9), resolutionBreached),
            priorityRows,
            new BiModels.SlaComparisonChart("优先级响应 vs 解决对比", priorityRows.stream()
                .map(row -> new BiModels.SlaComparisonDatum(
                    row.priority(),
                    parsePercentage(row.responseComplianceRate()),
                    parsePercentage(row.resolutionComplianceRate())
                )).toList()),
            categoryRisks,
            resolverRisks,
            trends,
            new BiModels.SlaViolationBreakdown(responseBreached, resolutionBreached, bothBreached),
            incidents.stream()
                .filter(IncidentSlaRecord::anyBreached)
                .sorted(Comparator
                    .comparing((IncidentSlaRecord record) -> priorityRank(record.priority()))
                    .thenComparing((IncidentSlaRecord record) -> violationSeverity(record.violationType()))
                    .thenComparing(IncidentSlaRecord::resolutionMinutes, Comparator.reverseOrder()))
                .limit(12)
                .map(record -> new BiModels.SlaViolationSample(
                    defaultLabel(record.orderNumber(), "—"),
                    defaultLabel(record.orderName(), "—"),
                    defaultLabel(record.priority(), "—"),
                    defaultLabel(record.category(), "未标注"),
                    defaultLabel(record.resolver(), "未分配"),
                    formatMinutes(record.responseMinutes()),
                    formatHours(record.resolutionMinutes() / 60.0),
                    record.violationType()
                )).toList()
        );
    }

    private List<IncidentSlaRecord> buildIncidentSlaRecords(BiRawData rawData) {
        Map<String, Double> responseCriteria = buildIncidentCriteriaMap(rawData.incidentSlaCriteria(), List.of("Response （minutes）", "Response (minutes)", "Response"));
        Map<String, Double> resolutionCriteria = buildIncidentCriteriaMap(rawData.incidentSlaCriteria(), List.of("Resolution （hours）", "Resolution (hours)", "Resolution"));
        return rawData.incidents().stream()
            .map(row -> {
                String priority = clean(row.get("Priority"));
                Double responseTarget = responseCriteria.get(priority);
                Double resolutionTarget = resolutionCriteria.get(priority);
                if (priority.isBlank() || responseTarget == null || resolutionTarget == null) {
                    return null;
                }
                double responseMinutes = parseDouble(row.get("Response Time(m)"));
                double resolutionMinutes = parseDouble(row.get("Resolution Time(m)"));
                return new IncidentSlaRecord(
                    row.get("Order Number"),
                    row.get("Order Name"),
                    priority,
                    row.get("Category"),
                    row.get("Resolver"),
                    parseDate(row.get("Begin Date")),
                    responseMinutes,
                    resolutionMinutes,
                    responseMinutes <= responseTarget,
                    resolutionMinutes / 60.0 <= resolutionTarget
                );
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private Map<String, Double> buildIncidentCriteriaMap(List<Map<String, String>> rows, List<String> candidateKeys) {
        return rows.stream()
            .filter(row -> !clean(row.get("Priority")).isBlank())
            .collect(Collectors.toMap(
                row -> clean(row.get("Priority")),
                row -> parseDouble(findFirstValue(row, candidateKeys)),
                (left, right) -> right,
                LinkedHashMap::new
            ));
    }

    private String findFirstValue(Map<String, String> row, List<String> candidateKeys) {
        for (String key : candidateKeys) {
            String value = clean(row.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private BiModels.SlaPriorityRow buildPriorityRow(String priority, List<IncidentSlaRecord> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        return new BiModels.SlaPriorityRow(
            priority,
            rows.size(),
            percentage(percentageValue(rows.stream().filter(IncidentSlaRecord::responseMet).count(), rows.size())),
            percentage(percentageValue(rows.stream().filter(IncidentSlaRecord::resolutionMet).count(), rows.size())),
            rows.stream().filter(IncidentSlaRecord::anyBreached).count(),
            formatHours(rows.stream().mapToDouble(IncidentSlaRecord::resolutionMinutes).average().orElse(0) / 60.0)
        );
    }

    private BiModels.SlaDimensionCard buildSlaDimensionCard(String title, double complianceRate, double averageMinutes, double p90Minutes, long breachedCount) {
        return new BiModels.SlaDimensionCard(
            title,
            percentage(complianceRate),
            title.contains("响应") ? formatMinutes(averageMinutes) : formatHours(averageMinutes / 60.0),
            title.contains("响应") ? formatMinutes(p90Minutes) : formatHours(p90Minutes / 60.0),
            breachedCount,
            toneFromScore(complianceRate, 0.9, 0.75),
            complianceRate >= 0.9 ? "整体稳定" : (complianceRate >= 0.75 ? "需重点关注" : "当前主要风险")
        );
    }

    private List<BiModels.SlaRiskRow> rankSlaRisks(List<IncidentSlaRecord> incidents, Function<IncidentSlaRecord, String> classifier) {
        return incidents.stream()
            .collect(Collectors.groupingBy(record -> defaultLabel(classifier.apply(record), "未标注"), LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> {
                List<IncidentSlaRecord> rows = entry.getValue();
                long breachedCount = rows.stream().filter(IncidentSlaRecord::anyBreached).count();
                double resolutionRate = percentageValue(rows.stream().filter(IncidentSlaRecord::resolutionMet).count(), rows.size());
                return Map.entry(
                    breachedCount * 1000 + Math.round((1 - resolutionRate) * 100) + rows.size(),
                    new BiModels.SlaRiskRow(
                        entry.getKey(),
                        rows.size(),
                        percentage(percentageValue(rows.stream().filter(IncidentSlaRecord::responseMet).count(), rows.size())),
                        percentage(resolutionRate),
                        breachedCount,
                        formatHours(rows.stream().mapToDouble(IncidentSlaRecord::resolutionMinutes).average().orElse(0) / 60.0)
                    )
                );
            })
            .sorted((left, right) -> Long.compare(right.getKey(), left.getKey()))
            .limit(5)
            .map(Map.Entry::getValue)
            .toList();
    }

    private List<BiModels.SlaTrendPoint> buildSlaTrendPoints(List<IncidentSlaRecord> incidents) {
        return incidents.stream()
            .filter(record -> record.beginDate() != null)
            .collect(Collectors.groupingBy(record -> YearMonth.from(record.beginDate()), LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                List<IncidentSlaRecord> rows = entry.getValue();
                return new BiModels.SlaTrendPoint(
                    entry.getKey().toString(),
                    parsePercentage(percentage(percentageValue(rows.stream().filter(IncidentSlaRecord::overallMet).count(), rows.size()))),
                    parsePercentage(percentage(percentageValue(rows.stream().filter(IncidentSlaRecord::responseMet).count(), rows.size()))),
                    parsePercentage(percentage(percentageValue(rows.stream().filter(IncidentSlaRecord::resolutionMet).count(), rows.size()))),
                    rows.stream().filter(IncidentSlaRecord::anyBreached).count()
                );
            })
            .toList();
    }

    private String buildSlaSummarySentence(double responseRate, double resolutionRate, List<BiModels.SlaPriorityRow> priorityRows, List<BiModels.SlaRiskRow> categoryRisks) {
        BiModels.SlaPriorityRow weakestPriority = priorityRows.stream()
            .min(Comparator.comparingDouble(row -> parsePercentage(row.resolutionComplianceRate())))
            .orElse(null);
        String priorityLabel = weakestPriority == null ? "高优先级" : weakestPriority.priority();
        String categoryLabel = categoryRisks.isEmpty() ? "重点类别" : categoryRisks.getFirst().label();
        if (resolutionRate < 0.75 && responseRate >= 0.9) {
            return "响应履约整体稳定，但解决履约在" + priorityLabel + "与" + categoryLabel + "上持续承压。";
        }
        if (responseRate < 0.9 && resolutionRate < 0.75) {
            return "响应与解决双环节均存在压力，当前需优先收敛" + priorityLabel + "工单的履约风险。";
        }
        return "整体履约可控，建议持续盯防" + priorityLabel + "和" + categoryLabel + "的波动。";
    }

    private List<ChartDatum> topCounts(List<Map<String, String>> rows, String key, int limit) {
        return rows.stream()
            .map(row -> defaultLabel(row.get(key), "未标注"))
            .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> new ChartDatum(entry.getKey(), entry.getValue()))
            .toList();
    }

    private List<ChartDatum> topCharts(Map<String, Double> values) {
        return values.entrySet().stream()
            .map(entry -> new ChartDatum(entry.getKey(), entry.getValue()))
            .toList();
    }

    private List<List<String>> rowsFromChart(List<ChartDatum> items) {
        return items.stream()
            .map(item -> List.of(item.label(), formatNumber(item.value())))
            .toList();
    }

    private long distinctCount(List<Map<String, String>> rows, String key) {
        return rows.stream().map(row -> clean(row.get(key))).filter(value -> !value.isBlank()).distinct().count();
    }

    private long countByValue(List<Map<String, String>> rows, String key, String value) {
        return rows.stream().filter(row -> clean(row.get(key)).equalsIgnoreCase(value)).count();
    }

    private boolean isYes(String value) {
        return clean(value).equalsIgnoreCase("Yes");
    }

    private boolean matchesAny(String value, List<String> candidates) {
        return candidates.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(value));
    }

    private String percentage(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private String percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0.0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", numerator * 100.0 / denominator);
    }

    private double percentageValue(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return numerator * 1.0 / denominator;
    }

    private double average(List<Map<String, String>> rows, String key) {
        return rows.stream()
            .map(row -> parseDouble(row.get(key)))
            .filter(value -> value > 0)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
    }

    private String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatScore(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private String formatHours(double value) {
        return String.format(Locale.ROOT, "%.1fh", value);
    }

    private String formatMinutes(double value) {
        return String.format(Locale.ROOT, "%.1fm", value);
    }

    private double percentile(List<Double> values, double percentile) {
        List<Double> filtered = values.stream().filter(value -> value >= 0).sorted().toList();
        if (filtered.isEmpty()) {
            return 0;
        }
        int index = Math.min(filtered.size() - 1, (int) Math.floor((filtered.size() - 1) * percentile));
        return filtered.get(index);
    }

    private double parsePercentage(String percentageText) {
        return parseDouble(percentageText.replace("%", ""));
    }

    private long parseLong(String value) {
        try {
            return Math.round(Double.parseDouble(clean(value)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(clean(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultLabel(String value, String fallback) {
        String normalized = clean(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private double scoreHigherBetter(double value, double goodThreshold, double warningThreshold) {
        if (value >= goodThreshold) {
            return 90 + Math.min((value - goodThreshold) / Math.max(1 - goodThreshold, 0.0001) * 10, 10);
        }
        if (value >= warningThreshold) {
            return 65 + (value - warningThreshold) / Math.max(goodThreshold - warningThreshold, 0.0001) * 25;
        }
        return Math.max(value / Math.max(warningThreshold, 0.0001) * 65, 10);
    }

    private double scoreLowerBetter(double value, double goodThreshold, double warningThreshold) {
        if (value <= goodThreshold) {
            return 95;
        }
        if (value <= warningThreshold) {
            return 65 + (warningThreshold - value) / Math.max(warningThreshold - goodThreshold, 0.0001) * 25;
        }
        return Math.max(20, 65 - Math.min((value - warningThreshold) / Math.max(warningThreshold, 0.0001) * 40, 45));
    }

    private double weightedAverage(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double weightedScore(Map<String, Double> scores) {
        return scores.getOrDefault("incident", 0.0) * 0.35
            + scores.getOrDefault("change", 0.0) * 0.25
            + scores.getOrDefault("request", 0.0) * 0.2
            + scores.getOrDefault("problem", 0.0) * 0.2;
    }

    private String gradeForScore(double score) {
        if (score >= 85) {
            return "Stable";
        }
        if (score >= 70) {
            return "Watch";
        }
        return "Risk";
    }

    private String toneFromNormalizedScore(double score) {
        if (score >= 85) {
            return "success";
        }
        if (score >= 70) {
            return "warning";
        }
        return "danger";
    }

    private String toneFromScore(double score, double goodThreshold, double warningThreshold) {
        if (score >= goodThreshold) {
            return "success";
        }
        if (score >= warningThreshold) {
            return "warning";
        }
        return "danger";
    }

    private String toneFromRate(String percentageText) {
        return toneFromScore(parsePercentage(percentageText) / 100.0, 0.9, 0.75);
    }

    private int priorityRank(String priority) {
        int index = PRIORITY_ORDER.indexOf(priority.toUpperCase(Locale.ROOT));
        return index >= 0 ? index : PRIORITY_ORDER.size();
    }

    private int violationSeverity(String violationType) {
        return switch (violationType) {
        case "双违约" -> 0;
        case "解决违约" -> 1;
        case "响应违约" -> 2;
        default -> 3;
        };
    }

    private String toneFromInverse(double value, double goodThreshold, double warningThreshold) {
        if (value <= goodThreshold) {
            return "success";
        }
        if (value <= warningThreshold) {
            return "warning";
        }
        return "danger";
    }

    private String incidentHealthSummary(double slaRate, double mttrHours) {
        return "SLA " + percentage(slaRate) + "，MTTR " + formatHours(mttrHours);
    }

    private List<BiModels.ExecutiveRisk> buildExecutiveRisks(long incidentSlaBreached, long changeFailures, long requestOpen, long problemOpen, double changeIncidentRate, double requestCsat, double problemClosureRate) {
        List<BiModels.ExecutiveRisk> risks = new ArrayList<>();
        if (changeFailures >= 5) {
            risks.add(new BiModels.ExecutiveRisk("change-failure", "Critical", "变更失败率偏高", "发布稳定性下降，需优先排查高风险变更。", "变更", String.valueOf(changeFailures)));
        }
        if (problemClosureRate < 0.55) {
            risks.add(new BiModels.ExecutiveRisk("problem-closure", "Warning", "问题关闭率不足", "根因与永久修复积压，风险会持续放大。", "问题", percentage(problemClosureRate)));
        }
        if (requestOpen >= 15) {
            risks.add(new BiModels.ExecutiveRisk("request-open", "Warning", "未完成请求积压", "履约体验承压，用户等待时间会拉长。", "请求", String.valueOf(requestOpen)));
        }
        if (changeIncidentRate >= 0.1) {
            risks.add(new BiModels.ExecutiveRisk("change-incident", "Warning", "变更引发事件偏多", "上线质量与变更验证存在薄弱点。", "变更", percentage(changeIncidentRate)));
        }
        if (requestCsat > 0 && requestCsat < 3.8) {
            risks.add(new BiModels.ExecutiveRisk("request-csat", "Attention", "请求满意度下滑", "服务体验有波动，建议复盘高频诉求。", "请求", formatNumber(requestCsat)));
        }
        if (incidentSlaBreached > 0) {
            risks.add(new BiModels.ExecutiveRisk("incident-sla", "Attention", "事件 SLA 出现违约", "核心事件响应存在超时情况。", "事件", String.valueOf(incidentSlaBreached)));
        }
        if (problemOpen >= 20) {
            risks.add(new BiModels.ExecutiveRisk("problem-open", "Attention", "未关闭问题偏多", "问题池持续扩大，会拖累稳定性治理。", "问题", String.valueOf(problemOpen)));
        }
        return risks.stream().sorted(Comparator.comparingInt(this::riskPriorityOrder)).toList();
    }

    private int riskPriorityOrder(BiModels.ExecutiveRisk risk) {
        return switch (risk.priority()) {
        case "Critical" -> 0;
        case "Warning" -> 1;
        default -> 2;
        };
    }

    private String buildExecutiveSummarySentence(String grade, BiModels.RiskSummary riskSummary, List<BiModels.ProcessHealth> processHealths) {
        BiModels.ProcessHealth weakest = processHealths.stream().min(Comparator.comparingDouble(process -> parseDouble(process.score()))).orElse(null);
        if ("Stable".equals(grade)) {
            return "整体运行稳定，但仍需持续关注" + (weakest != null ? weakest.label() : "重点流程") + "的波动。";
        }
        if ("Watch".equals(grade)) {
            return "整体运行可控，但" + (weakest != null ? weakest.label() : "部分流程") + "已进入重点关注区，建议优先处理前列风险。";
        }
        return "整体健康存在风险，当前需优先收敛" + (riskSummary.topRisks().isEmpty() ? "关键问题" : riskSummary.topRisks().getFirst().title()) + "。";
    }

    private String buildTrendHint(List<BiModels.TrendPoint> trendPoints) {
        if (trendPoints.size() < 2) {
            return "趋势数据正在准备中。";
        }
        BiModels.TrendPoint previous = trendPoints.get(trendPoints.size() - 2);
        BiModels.TrendPoint current = trendPoints.getLast();
        double delta = current.score() - previous.score();
        if (delta > 2) {
            return "较上期提升 " + String.format(Locale.ROOT, "%.1f", delta) + " 分。";
        }
        if (delta < -2) {
            return "较上期下降 " + String.format(Locale.ROOT, "%.1f", Math.abs(delta)) + " 分。";
        }
        return "整体趋势基本持平。";
    }

    private String buildPeriodLabel(BiRawData rawData) {
        List<LocalDateTime> dates = collectDates(rawData);
        if (dates.isEmpty()) {
            return "固定样例数据";
        }
        LocalDate min = dates.stream().min(LocalDateTime::compareTo).orElseThrow().toLocalDate();
        LocalDate max = dates.stream().max(LocalDateTime::compareTo).orElseThrow().toLocalDate();
        return min + " 至 " + max;
    }

    private List<BiModels.TrendPoint> buildTrendPoints(BiRawData rawData) {
        Map<YearMonth, List<Map<String, String>>> incidentsByMonth = groupByMonth(rawData.incidents(), "Begin Date");
        Map<YearMonth, List<Map<String, String>>> changesByMonth = groupByMonth(rawData.changes(), "Requested Date");
        Map<YearMonth, List<Map<String, String>>> requestsByMonth = groupByMonth(rawData.requests(), "Requested Date");
        Map<YearMonth, List<Map<String, String>>> problemsByMonth = groupByMonth(rawData.problems(), "Logged Date");

        List<YearMonth> months = new ArrayList<>();
        months.addAll(incidentsByMonth.keySet());
        months.addAll(changesByMonth.keySet());
        months.addAll(requestsByMonth.keySet());
        months.addAll(problemsByMonth.keySet());

        return months.stream()
            .distinct()
            .sorted()
            .limit(Math.max(months.size(), 6))
            .map(month -> {
                List<Map<String, String>> monthIncidents = incidentsByMonth.getOrDefault(month, List.of());
                List<Map<String, String>> monthChanges = changesByMonth.getOrDefault(month, List.of());
                List<Map<String, String>> monthRequests = requestsByMonth.getOrDefault(month, List.of());
                List<Map<String, String>> monthProblems = problemsByMonth.getOrDefault(month, List.of());
                double score = weightedScore(Map.of(
                    "incident", weightedAverage(List.of(
                        scoreHigherBetter(percentageValue(countByValue(monthIncidents, "SLA Compliant", "Yes"), monthIncidents.size()), 0.95, 0.85),
                        scoreLowerBetter(average(monthIncidents, "Resolution Time(m)") / 60.0, 12, 24)
                    )),
                    "change", weightedAverage(List.of(
                        scoreHigherBetter(percentageValue(countByValue(monthChanges, "Success", "Yes"), monthChanges.size()), 0.95, 0.85),
                        scoreLowerBetter(percentageValue(countByValue(monthChanges, "Incident Caused", "Yes"), monthChanges.size()), 0.05, 0.12)
                    )),
                    "request", weightedAverage(List.of(
                        scoreHigherBetter(percentageValue(countByValue(monthRequests, "SLA Met", "Yes"), monthRequests.size()), 0.9, 0.75),
                        scoreHigherBetter(average(monthRequests, "Satisfaction Score") / 5.0, 0.82, 0.7)
                    )),
                    "problem", scoreHigherBetter(percentageValue(monthProblems.stream().filter(row -> matchesAny(clean(row.get("Status")), List.of("Resolved", "Closed"))).count(), monthProblems.size()), 0.75, 0.55)
                ));
                double signal = monthIncidents.stream().filter(row -> matchesAny(clean(row.get("Priority")), List.of("P1", "P2"))).count();
                return new BiModels.TrendPoint(month.toString(), score, signal);
            })
            .sorted(Comparator.comparing(BiModels.TrendPoint::label))
            .toList();
    }

    private Map<YearMonth, List<Map<String, String>>> groupByMonth(List<Map<String, String>> rows, String key) {
        return rows.stream()
            .map(row -> {
                LocalDateTime parsedDate = parseDate(row.get(key));
                if (parsedDate == null) {
                    return null;
                }
                return Map.entry(parsedDate, row);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(entry -> YearMonth.from(entry.getKey()), LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private List<LocalDateTime> collectDates(BiRawData rawData) {
        List<LocalDateTime> dates = new ArrayList<>();
        addDates(dates, rawData.incidents(), "Begin Date");
        addDates(dates, rawData.changes(), "Requested Date");
        addDates(dates, rawData.requests(), "Requested Date");
        addDates(dates, rawData.problems(), "Logged Date");
        return dates;
    }

    private void addDates(List<LocalDateTime> target, List<Map<String, String>> rows, String key) {
        rows.stream().map(row -> parseDate(row.get(key))).filter(Objects::nonNull).forEach(target::add);
    }

    private LocalDateTime parseDate(String value) {
        String normalized = clean(value);
        if (normalized.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(normalized).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        try {
            double excelDate = Double.parseDouble(normalized);
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Math.round((excelDate - 25569) * 86400000L)), ZoneOffset.UTC);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private String safeSheetName(String value) {
        return value.replace("/", "-");
    }

    private int writeTitle(XSSFSheet sheet, int rowIndex, String title, String description) {
        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.createCell(0).setCellValue(title);
        Row descriptionRow = sheet.createRow(rowIndex++);
        descriptionRow.createCell(0).setCellValue(description);
        return rowIndex + 1;
    }

    private int writeCards(XSSFSheet sheet, int rowIndex, List<MetricCard> cards) {
        if (cards.isEmpty()) {
            return rowIndex;
        }
        Row header = sheet.createRow(rowIndex++);
        header.createCell(0).setCellValue("指标");
        header.createCell(1).setCellValue("值");
        for (MetricCard card : cards) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(card.label());
            row.createCell(1).setCellValue(card.value());
        }
        return rowIndex + 1;
    }

    private int writeCharts(XSSFSheet sheet, int rowIndex, List<ChartSection> charts) {
        for (ChartSection chart : charts) {
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.createCell(0).setCellValue(chart.title());
            Row headerRow = sheet.createRow(rowIndex++);
            headerRow.createCell(0).setCellValue("标签");
            headerRow.createCell(1).setCellValue("值");
            for (ChartDatum item : chart.items()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.label());
                Cell valueCell = row.createCell(1);
                valueCell.setCellValue(item.value());
            }
            rowIndex++;
        }
        return rowIndex;
    }

    private int writeTables(XSSFSheet sheet, int rowIndex, List<BiModels.TableSection> tables) {
        for (BiModels.TableSection table : tables) {
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.createCell(0).setCellValue(table.title());
            Row headerRow = sheet.createRow(rowIndex++);
            for (int index = 0; index < table.columns().size(); index++) {
                headerRow.createCell(index).setCellValue(table.columns().get(index));
            }
            for (List<String> values : table.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int index = 0; index < values.size(); index++) {
                    row.createCell(index).setCellValue(values.get(index));
                }
            }
            rowIndex++;
        }
        return rowIndex;
    }
}
