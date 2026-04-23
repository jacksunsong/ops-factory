# ITIL 工单导出 Schema v1 使用指南

## 1. 这份 Excel 是什么

`itil-ticket-export-schema-v1.xlsx` 是一个 MVP 级别的 ITIL 工单标准导出文件定义。它的目标是让不同 ITSM 平台通过 adapter 输出同一结构的 Excel 文件，而不是一次性覆盖所有审计、治理、BI、供应商和自动化场景。

覆盖流程：

- Incident
- Service Request
- Problem
- Change

覆盖平台：

- ServiceNow
- BMC Remedy / BMC Helix
- HPSM / Micro Focus Service Manager
- OpenText Service Management Automation X (SMAX)
- iMOC eTicket / Huawei
- Freshservice
- IBM ServiceDesk / IBM Control Desk / Maximo

## 2. 文件概览

工作簿是精简 MVP 版本，核心规模如下：

- `Canonical_Schema`：64 个字段，全流程字段全集
- `Incident_Schema`：38 个字段
- `Service_Request_Schema`：41 个字段
- `Problem_Schema`：42 个字段
- `Change_Schema`：47 个字段
- `Incident_Sample_Data`：8,977 条真实 incident 样例归一化数据
- `SR_Sample_Data` / `Problem_Sample_Data` / `Change_Sample_Data`：各 120 条 MVP 模拟数据

## 3. 设计原则

本版本只保留最小可验证字段：

- 头部平台都能稳定输出或通过 adapter 映射的字段
- 四大流程 MVP 所需的最小流程差异字段
- 能保留原始数据回溯能力的 `source_raw_fields`

以下内容不进入主 schema：

- SLA breach 结果和超时分钟等派生指标
- 数据质量评分、字段映射置信度、record hash
- 审计轨迹、供应商生命周期、war room、PIR、自动化和成本字段
- 每个源系统的原始字段展开列

## 4. 各工作表说明

- `README`：版本、范围和文件说明
- `Sources`：设计依据和来源
- `Canonical_Schema`：全流程字段全集
- `Incident_Schema`、`Service_Request_Schema`、`Problem_Schema`、`Change_Schema`：各流程字段定义
- `Enum_Definitions`：标准枚举和平台枚举映射
- `System_Header_Mapping`：标准字段到各平台字段的映射
- 平台 header sheets：各平台关键原生字段和标准字段映射
- `Incident_Source_Profile`：现有 incident 附件字段画像
- `Incident_Raw_SLA_Criteria`：现有 incident 附件中的 SLA 标准
- sample data sheets：各流程样例数据
- `Validation`：结构和样例数据校验摘要

## 5. 如何理解 Required 和 Optional

`requirement_level = Required` 表示 adapter 输出标准 Excel 时必须尽量填充。MVP 的 Required 字段很少，主要是：

- 来源、流程和工单编号
- 标题、状态、优先级、分类
- 打开和更新时间
- `source_raw_fields`

`Optional` 字段不是不重要，而是不同平台、不同租户、不同流程配置下可能不存在。adapter 应能留空，但不要删除列。

## 6. source_raw_fields 的作用

`source_raw_fields` 是 MVP 版的关键字段。它用 JSON/Text 保存源系统原始字段快照。

这样做的原因：

- 不把每个源系统字段都展开成标准列
- 不丢失原始导出信息
- 方便排查映射问题
- 支持源系统差异和租户自定义字段

对于现有 incident 样例，原始的 16 个字段都进入 `source_raw_fields`。

## 7. 平台适配接口

各流程平台的标准适配接口是文件接口。adapter 的职责是调用平台 API 或导出接口，最终生成符合本 schema 的 Excel 文件。

典型 adapter 流程：

1. 调用平台 API 拉取 Incident / Service Request / Problem / Change 数据。
2. 按 `System_Header_Mapping` 做字段映射。
3. 按 `Enum_Definitions` 做枚举归一化。
4. 填充 Required 字段。
5. 把源系统原始字段放入 `source_raw_fields`。
6. 输出标准 Excel 文件。

SMAX adapter 需要特别注意：OpenText SMAX 的 mandatory fields 会受租户配置影响，所以 adapter 应支持配置化 mapping 和字段发现，不应把租户私有字段直接变成标准 schema 字段。

## 8. 如何使用样例数据

- 用 `Incident_Sample_Data` 验证真实 incident 数据兼容性。
- 用 `SR_Sample_Data` 验证服务请求 adapter 输出。
- 用 `Problem_Sample_Data` 验证 Problem / RCA 基础字段。
- 用 `Change_Sample_Data` 验证 Change 审批、计划、实施和回退字段。

样例数据用于验证文件结构和字段填值逻辑，不代表真实业务分布。
