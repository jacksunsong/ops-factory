# Knowledge Import/Export Test Report

- Report timestamp: 2026-03-27 16:21:51 +0800
- Test execution finished: 2026-03-27 16:22:02 +0800
- Repository: `/Users/buyangnie/Documents/GitHub/ops-factory`
- Module: `knowledge-service`
- Test class: `com.huawei.opsfactory.knowledge.api.KnowledgeUploadFlowIntegrationTest`
- Test method: `shouldImportInputFilesIntoForTestSourceAndExportAllMarkdownArtifacts`
- Command: `mvn -Dtest=KnowledgeUploadFlowIntegrationTest#shouldImportInputFilesIntoForTestSourceAndExportAllMarkdownArtifacts test`
- Target knowledge source name: `for-test`
- Result: `BUILD SUCCESS`
- Tests run: `1`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

## Input Directory

`/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles`

## Output Directory

`/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles`

Note:
This directory was cleared before export by the test. You did not see files earlier because when the whole test class runs, later test cases execute `@BeforeEach` again and clear `outputFiles`.

## Imported Files

| # | Source file | Size (bytes) | Modified time |
|---|---|---:|---|
| 1 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/Comprehensive_Quality_Report_20260204_132159_EN_matplotlib_chart.xlsx` | 1803945 | 2026-03-24 13:51:58 +0800 |
| 2 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/Major_Incident_Analysis_INC20250115001_EN.docx` | 40339 | 2026-03-24 13:51:58 +0800 |
| 3 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/SLA_Violation_Analysis_Report_CN.html` | 16803 | 2026-03-24 13:51:58 +0800 |
| 4 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/sample-knowledge.pdf` | 627 | 2026-03-24 14:13:33 +0800 |
| 5 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/sample-knowledge.pptx` | 28286 | 2026-03-24 14:10:33 +0800 |
| 6 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/sample-metrics.csv` | 71 | 2026-03-24 14:10:33 +0800 |
| 7 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/sample-operations-note.md` | 100 | 2026-03-24 14:13:33 +0800 |
| 8 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/inputFiles/sample-runbook.txt` | 80 | 2026-03-24 14:10:33 +0800 |

## Exported Markdown Files

| # | Exported file | Size (bytes) | Export time |
|---|---|---:|---|
| 1 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/Comprehensive_Quality_Report_20260204_132159_EN_matplotlib_chart.xlsx.md` | 39099 | 2026-03-27 16:22:01 +0800 |
| 2 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/Major_Incident_Analysis_INC20250115001_EN.docx.md` | 6597 | 2026-03-27 16:22:01 +0800 |
| 3 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/SLA_Violation_Analysis_Report_CN.html.md` | 16803 | 2026-03-27 16:22:01 +0800 |
| 4 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/sample-knowledge.pdf.md` | 52 | 2026-03-27 16:22:01 +0800 |
| 5 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/sample-knowledge.pptx.md` | 75 | 2026-03-27 16:22:01 +0800 |
| 6 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/sample-metrics.csv.md` | 70 | 2026-03-27 16:22:01 +0800 |
| 7 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/sample-operations-note.md.md` | 99 | 2026-03-27 16:22:01 +0800 |
| 8 | `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/src/test/resources/outputFiles/sample-runbook.txt.md` | 79 | 2026-03-27 16:22:01 +0800 |

## Exported Markdown Preview

### `Comprehensive_Quality_Report_20260204_132159_EN_matplotlib_chart.xlsx.md`

```md
Executive Summary
	Executive Summary
	2024-04-19 — 2025-07-06

	KPI Summary
	KPI	Value	Status
	Health Score	33	At Risk
	Incident SLA Rate	64.2%	At Risk
```

### `Major_Incident_Analysis_INC20250115001_EN.docx.md`

```md
Major Incident Analysis Report
INC20250115001 - 核心交易数据库主节点宕机导致全站交易中断

Incident Overview
Title: 核心交易数据库主节点宕机导致全站交易中断
Priority: P1
Category: Database
Status: Resolved
```

### `SLA_Violation_Analysis_Report_CN.html.md`

```md
<!DOCTYPE html>
<html lang="cn">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SLA违约归因分析报告</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
```

### `sample-knowledge.pdf.md`

```md
Knowledge Service PDF Sample with fetch and retrieve
```

### `sample-knowledge.pptx.md`

```md
Knowledge Service PPTX Sample
Search fetch retrieve chunk profile job stats
```

### `sample-metrics.csv.md`

```md
metric,value
ingested_documents,8
chunk_count,24
retrieval_mode,hybrid
```

### `sample-operations-note.md.md`

```md
# Sample Operations Note

Knowledge service supports search, fetch, retrieve, and chunk management.
```

### `sample-runbook.txt.md`

```md
Runbook for knowledge-service
1. Upload files
2. Search incident
3. Fetch chunk
```

## Report Artifacts

- Surefire text report: `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/target/surefire-reports/com.huawei.opsfactory.knowledge.api.KnowledgeUploadFlowIntegrationTest.txt`
- Surefire XML report: `/Users/buyangnie/Documents/GitHub/ops-factory/knowledge-service/target/surefire-reports/TEST-com.huawei.opsfactory.knowledge.api.KnowledgeUploadFlowIntegrationTest.xml`
- Snapshot copy of exported markdown files: `/Users/buyangnie/Documents/GitHub/ops-factory/test/report/outputFiles-20260327_162151`
