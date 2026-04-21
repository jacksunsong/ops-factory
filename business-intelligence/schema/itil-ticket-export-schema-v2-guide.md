# ITIL 工单导出 Schema v2 使用指南

## 1. 这份 Excel 是什么

`itil-ticket-export-schema-v2.xlsx` 是一份面向 IT 运维、ITSM 数据治理和跨系统工单分析的标准化导出模型。

它覆盖 ITIL 四大流程：

- Incident
- Service Request
- Problem
- Change

它的目标不是替代 ServiceNow、Remedy、HPSM、iMOC eTicket、Freshservice 或 IBM ServiceDesk 的原生表结构，而是在这些系统之上定义一套统一的 **canonical schema**，用于承接不同系统导出的字段，并把它们归一化到同一套字段、枚举和质量规则中。

适合的使用场景包括：

- 多套 ITSM 系统工单数据合并
- 运维 KPI / SLA / OLA 报表
- Incident、Problem、Change 复盘分析
- 供应商和外包团队绩效考核
- 变更风险与失败变更分析
- 服务目录履约效率分析
- 工单数据湖、数据仓库或 BI 模型建设
- ITSM 系统迁移前的数据字段盘点

## 2. 文件概览

工作簿共有 24 个工作表。

核心规模：

- `Canonical_Schema_v2`：245 个标准字段
- `Incident_Schema_v2`：167 个 Incident 适用字段
- `Service_Request_Schema_v2`：136 个 Service Request 适用字段
- `Problem_Schema_v2`：141 个 Problem 适用字段
- `Change_Schema_v2`：159 个 Change 适用字段
- `Incident_Sample_Data`：8,977 条按 v2 schema 归一化后的 incident 样例，并嵌入原始 incident 导出的 16 个 source-specific 字段
- `SR_Sample_Data`：120 条 Service Request 模拟数据
- `Problem_Sample_Data`：120 条 Problem 模拟数据
- `Change_Sample_Data`：120 条 Change 模拟数据

## 3. 推荐阅读顺序

第一次使用时，建议按这个顺序看：

1. `README`
2. `Operational_Gap_Assessment`
3. `Canonical_Schema_v2`
4. 对应流程的 schema sheet，例如 `Incident_Schema_v2`
5. `Enum_Definitions`
6. `System_Header_Mapping_v2`
7. 对应系统的 header sheet，例如 `ServiceNow_Headers`
8. 对应 sample data sheet
9. `Data_Quality_Rules`
10. `Validation`

这样可以先理解设计目标，再看字段定义，最后看样例和质量规则。

## 4. 各工作表说明

### README

说明工作簿版本、用途、本次 v2 相比 v1 的主要增强点，以及样例数据范围。

重点看：

- `Version`
- `What changed from v1`
- `Incident sample inclusion`
- `Sample data`

### Sources

记录字段设计和系统表头映射所参考的来源。

这里用于区分：

- 厂商公开文档确认的字段
- 第三方公开表定义或接口文档确认的字段
- 用户提供的真实 incident 导出附件
- v2 新增的治理/派生字段

注意：iMOC eTicket 的完整字段字典没有公开资料，因此没有假装提供完整官方字段。iMOC 部分主要基于公开可查的 Huawei 资料和你提供的真实 incident 导出样例。

### Canonical_Schema_v2

这是最重要的工作表。

每一行代表一个标准字段。主要列如下：

| 列名 | 含义 |
|---|---|
| `field_id` | 标准字段英文 ID，用于 ETL、数据仓库、API、BI 建模 |
| `processes` | 字段适用流程，`All` 表示四类流程都适用 |
| `label_zh` | 中文字段名 |
| `label_en` | 英文字段名 |
| `data_type` | 数据类型 |
| `field_tier` | 字段等级 |
| `requirement_level` | 字段要求级别，取值为 `Required` 或 `Optional` |
| `allowed_values_or_format` | 枚举值或格式要求 |
| `example` | 示例值 |
| `field_domain` | 字段所属管理域 |
| `definition_or_note` | 字段定义 |
| `data_quality_rule` | 数据质量规则 |
| `normalization_rule` | 归一化规则 |

如果你只想理解标准字段定义，从这个 sheet 开始。

### Incident_Schema_v2

Incident 流程适用字段。

适合用于：

- 事件单导出模板
- 事件 SLA 报表
- 重大事件复盘
- 监控告警转 incident 的字段设计
- incident 数据入湖字段表

这张表包含所有 `processes = All` 和 `processes` 包含 `Incident` 的字段。

### Service_Request_Schema_v2

Service Request 流程适用字段。

适合用于：

- 服务目录请求导出
- 申请、审批、履约数据分析
- 自动化履约效果评估
- 服务目录成本和 chargeback 分析

重点关注这些字段域：

- `Request Fulfillment`
- `Governance & Risk`
- `Automation & Effort`
- `Financial & Effort`

### Problem_Schema_v2

Problem 流程适用字段。

适合用于：

- RCA 分析
- 已知错误库 KEDB
- 问题老化管理
- 复发问题分析
- 永久修复跟踪

重点关注这些字段：

- `root_cause`
- `workaround`
- `known_error`
- `rca_owner`
- `rca_due_at`
- `rca_completed_at`
- `permanent_fix_required`
- `permanent_fix_change_id`
- `recurrence_count`
- `prevented_incident_estimate`

### Change_Schema_v2

Change 流程适用字段。

适合用于：

- CAB 审批分析
- 变更成功率分析
- 失败变更复盘
- 高风险变更审计
- 变更冲突检查
- 变更导致 incident 的追踪

重点关注这些字段：

- `change_type`
- `risk`
- `approval_status`
- `cab_required`
- `planned_start_at`
- `planned_end_at`
- `actual_start_at`
- `actual_end_at`
- `implementation_plan`
- `test_plan`
- `backout_plan`
- `deployment_result`
- `backout_executed`
- `change_failure_reason`
- `caused_incident_ids`

### Enum_Definitions

定义标准枚举值，以及不同系统中的对应值。

典型用途：

- 把 ServiceNow、Remedy、HPSM、Freshservice 等系统的状态统一到 `New / Open / Pending / Resolved / Closed`
- 把不同系统的优先级统一到 `P1 / P2 / P3 / P4`
- 统一 Change Type、Approval Status、Risk、Support Tier 等管理口径

做 ETL 时，应优先使用这里的枚举映射。

### Data_Quality_Rules

定义数据质量校验规则。

典型规则包括：

- 核心字段不能为空
- 时间先后顺序必须合理
- SLA breach 标识和 breach minutes 必须一致
- Closed 工单必须有关闭证据
- Priority 必须归一化到 P1-P4
- Pending 工单应有等待原因或延迟责任方
- Change 必须有实施、测试、回退和审批证据
- Problem 关闭前应有 RCA 或永久修复信息

这张表适合交给数据工程、ETL、数据治理或 BI 团队使用。

### Operational_Gap_Assessment

说明 v1 存在哪些管理缺口，以及 v2 如何补强。

它不是字段定义表，而是帮助管理者理解为什么要增加这些字段。

覆盖的改进域包括：

- SLA/OLA
- Responsibility
- Vendor
- Major Incident
- Problem
- Change
- Service Request
- Data Quality

### System_Header_Mapping_v2

这是标准字段和各源系统字段之间的映射总表。

列包括：

- `canonical_field_id`
- `canonical_label_zh`
- `canonical_label_en`
- `ServiceNow`
- `Remedy/BMC Helix`
- `HPSM/Micro Focus SM`
- `iMOC eTicket/Huawei`
- `Freshservice`
- `IBM ServiceDesk/Control Desk`
- `Mapping notes`

使用方式：

1. 先确定目标标准字段，例如 `ticket_id`
2. 查看每个系统中对应的原生字段
3. 在 ETL 或导出模板中建立映射
4. 对于空白或标注为 derived/custom 的字段，不要强行要求源系统一定存在原生字段，可以通过审计日志、SLA 引擎、CMDB、审批历史或 ETL 派生

### 系统 Header 工作表

包括：

- `ServiceNow_Headers`
- `Remedy_Headers`
- `HPSM_Headers`
- `iMOC_eTicket_Headers`
- `Freshservice_Headers`
- `IBM_ServiceDesk_Headers`

这些表的作用是列出各系统中已查证或可追溯的原生字段/表头，并说明它们映射到哪个 canonical field。

使用时要注意：

- 这些不是完整替代厂商数据字典
- 它们用于导出字段识别、字段映射和差异分析
- 如果你的系统有大量自定义字段，需要额外补充到映射表中

### Incident_Source_Profile

对用户提供的 incident 附件做字段画像。

它说明：

- 附件有多少行
- 有哪些字段
- 每个字段的非空情况
- 主要值域或范围
- 映射到哪个 canonical field

这张表用于证明 v2 schema 兼容原始 incident 附件。

### Incident_Raw_SLA_Criteria

保留原始附件里的 SLA 标准：

- Priority
- Response minutes
- Resolution hours

它是 incident 样例归一化时计算 SLA 相关字段的依据。

### Incident_Sample_Data

把原始 incident 附件按 v2 Incident schema 归一化后的结果。

这张表同时承担两类用途：

- 提供可直接用于入湖、ETL、BI 的标准化 incident 字段
- 通过 `incident_raw_*` source-specific 字段保留原始导出的 16 个字段值

因此工作簿不再单独保留 `Incident_Raw_Sample` tab，避免同一批 incident 数据在两个 tab 中重复存放。

例如：

| 原始字段 | 归一化字段 |
|---|---|
| `Order Number` | `ticket_id` |
| `Order Name` | `title` |
| `Begin Date` | `opened_at` |
| `End Date` | `closed_at` |
| `Current Phase` | `phase` |
| `Suspend Time(m)` | `suspend_time_minutes` / `paused_duration_minutes` |
| `Resolver` | `resolver` / `assigned_to` |
| `Category` | `category` / `service` |
| `Priority` | `priority` |
| `Order Status` | `status` |
| `Resolution Time(m)` | `resolution_time_minutes` |
| `Resolution Date` | `resolved_at` |
| `Response Time(m)` | `response_time_minutes` |
| `Duration(m)` | `business_duration_minutes` 或相关耗时字段 |

这张表可以直接作为 incident 数据入湖或 BI 样例，也可以用于和原始附件字段逐项对账。

### SR_Sample_Data

基于 v2 Service Request schema 生成的 120 条样例数据。

它展示了服务请求中常见的字段组合：

- catalog item
- approval
- fulfillment
- requested item variables
- cost center
- chargeback
- automation
- manual effort

### Problem_Sample_Data

基于 v2 Problem schema 生成的 120 条样例数据。

它展示了 Problem 管理中常见的字段组合：

- known error
- root cause
- workaround
- RCA owner
- RCA due/completed
- permanent fix change
- recurrence count
- related incident count

### Change_Sample_Data

基于 v2 Change schema 生成的 120 条样例数据。

它展示了 Change 管理中常见的字段组合：

- change type
- CAB approval
- planned/actual window
- conflict detection
- freeze window
- implementation/test/backout plan
- deployment result
- backout reason
- caused incidents

### Validation

自动校验结果。

当前校验显示：

- 245 个 canonical 字段
- 字段等级齐全
- `Incident_Raw_Sample` tab 已移除，原始 16 个字段已嵌入 `Incident_Sample_Data`
- Incident 归一化样例 8,977 条
- SR / Problem / Change 各 120 条
- 枚举和数据质量规则存在

## 5. 如何理解字段等级 field_tier

`field_tier` 是 v2 的关键设计。

### Core

最小必备字段。

如果系统只能先导出最小字段集，应优先满足 Core 字段。

典型字段：

- `source_system`
- `process_type`
- `ticket_id`
- `title`
- `status`
- `priority`
- `created_at`
- `opened_at`
- `assignment_group`

### Recommended

强烈建议字段。

这些字段通常用于 KPI、SLA、责任分析、流程治理和管理报表。

如果要做正式运维看板，Recommended 字段应尽量导出。

### Advanced

高级治理字段。

这些字段通常来自：

- 审批历史
- SLA 引擎
- 审计日志
- 供应商系统
- CMDB
- 自动化平台
- 重大事件管理模块
- 变更日历

Advanced 字段不一定每个源系统原生导出都有，但成熟的 ITSM 数据治理应该逐步补齐。

### Source Specific

源系统或附件专用字段。

例如 incident 附件中的：

- `incident_raw_num`
- `incident_raw_order_name`
- `incident_raw_order_number`
- `incident_raw_begin_date`
- `incident_raw_end_date`
- `incident_raw_current_phase`
- `incident_raw_suspend_time_minutes`
- `incident_raw_total_time_minutes`
- `incident_raw_resolver`
- `incident_raw_category`
- `incident_raw_priority`
- `incident_raw_order_status`
- `incident_raw_resolution_time_minutes`
- `incident_raw_resolution_date`
- `incident_raw_response_time_minutes`
- `incident_raw_duration_minutes`

这些字段用于保留源数据兼容性，不一定作为跨系统通用报表字段。

## 6. 如何按阶段落地

### 第一阶段：最小可用导出

目标：先让所有系统能进入统一数据模型。

建议范围：

- 所有 Core 字段
- 基础时间字段
- 基础分类字段
- 基础责任字段
- `System_Header_Mapping_v2` 中已有明确映射的字段

输出结果：

- 可以合并多系统工单
- 可以做基础工单量、状态、优先级、处理组统计

### 第二阶段：SLA 和责任分析

目标：能准确回答 SLA 是否违约、为什么违约、谁负责。

建议补齐：

- `first_response_at`
- `acknowledged_at`
- `response_due_at`
- `resolution_due_at`
- `response_sla_breached`
- `resolution_sla_breached`
- `response_breach_minutes`
- `resolution_breach_minutes`
- `breach_reason`
- `delay_owner`
- `initial_assignment_group`
- `final_assignment_group`
- `resolver_group`
- `accountable_group`

输出结果：

- SLA 报表
- 超时原因分析
- 处理组绩效
- 等待客户 / 等待供应商 / 内部延误拆分

### 第三阶段：流程治理

目标：让 Incident、Problem、Change、Service Request 具备完整管理闭环。

Incident 补齐：

- major incident
- outage
- communication
- PIR

Problem 补齐：

- RCA
- known error
- workaround
- permanent fix
- recurrence

Change 补齐：

- approval
- CAB
- risk
- conflict
- backout
- failure reason

Service Request 补齐：

- catalog owner
- fulfillment
- cost
- automation
- manual effort

输出结果：

- 重大事件复盘
- Problem 治理看板
- CAB 和失败变更分析
- 服务目录效率与自动化 ROI

### 第四阶段：审计、供应商和数据质量

目标：让数据可审计、可追责、可持续运营。

建议补齐：

- `vendor_*`
- `contract_id`
- `underpinning_contract_id`
- `audit_required`
- `audit_trail_url`
- `source_exported_at`
- `source_export_batch_id`
- `record_hash`
- `field_mapping_confidence`
- `data_quality_score`
- `raw_payload_reference`

输出结果：

- 供应商 SLA 绩效
- 合同履约分析
- 审计取证
- 数据质量监控

## 7. 如何做源系统映射

推荐做法：

1. 选定源系统，例如 ServiceNow
2. 打开对应的 header sheet，例如 `ServiceNow_Headers`
3. 打开 `System_Header_Mapping_v2`
4. 对每个 `canonical_field_id` 判断源系统是否有原生字段
5. 对没有原生字段的字段，标记为：
   - `Derived`：可由时间、状态、审计历史计算
   - `Custom`：需要源系统配置自定义字段
   - `Not Available`：当前阶段不采集
   - `Manual`：人工维护或来自线下复盘

建议不要把所有字段都强制要求源系统一次性导出。更实际的做法是按 `field_tier` 分阶段推进。

## 8. 如何使用样例数据

样例数据有两个用途：

1. 帮助理解字段应如何填值
2. 用于测试导入、ETL、BI 报表和数据质量校验逻辑

建议使用方式：

- 用 `Incident_Sample_Data` 测试真实数据兼容性
- 用 `SR_Sample_Data` 测试服务目录和履约报表
- 用 `Problem_Sample_Data` 测试 RCA、KEDB、复发问题分析
- 用 `Change_Sample_Data` 测试 CAB、变更窗口、失败变更、回退分析

不要把模拟数据当作真实业务分布。它们的目的是覆盖字段组合和流程逻辑，不是反映真实工单量或真实 SLA 表现。
