---
name: root-cause-analysis
description: 通过查询告警数据、使用拓扑信息识别根因告警，并生成详细的故障诊断报告。分析告警关联关系、系统拓扑，并提供可操作的恢复建议。当用户提到"根因分析"、"根因定位"、"告警根因识别"、"故障原因"，要求诊断故障或告警等情况下，表示故障根因分析的意图时使用。
version: 1.0.0
---

# 根因告警分析

## 概述

执行全面的根因分析，通过查询告警数据、使用拓扑信息识别根因告警，并生成详细的故障诊断报告。分析告警关联关系、系统拓扑，并提供可操作的恢复建议。

## 适用场景

当以下情况时使用此技能：
- 用户请求系统问题的根因分析
- 用户想要识别问题源头
- 用户要求诊断故障或告警
- 用户提及故障定位或排查

## 分析流程

### 步骤1：解析并校验时间范围
#### 解析开始时间和结束时间

从用户输入中提取开始时间`${statr_time}`和结束时间`${end_time}`。如果未指定时间范围，默认`${statr_time}`为15分钟前，`${end_time}`为当前时间。

**解析格式要求**：
  - 用毫秒级时间戳格式
  - 使用当前所在时区


#### 校验开始时间和结束时间
时间范围必须满足要求，若不满足，则向用户确认需要分析的时间范围，并提示用户输入的时间具体违反了哪个要求。 

**校验规则**：
  - 开始时间不得早于48小时前
  - 结束时间必须大于等于开始时间
  - 开始时间与结束时间，时间跨度不得超过15分钟


### 步骤2：查询告警数据

获取指定时间范围的告警数据。

执行查询脚本：
```bash
python "./scripts/get_abnormal_data.py" --start_time=${statr_time} --end_time=${statr_time}
```

响应格式：
```json
{
  "available_abnormal_data": [
    {
      "clusterName": "Batch Executor Cluster",
      "dn": "10cf2b16-f2d1-46c8-91c7-645dd85cf15e_294_BatchExecutor",
      "neName": null,
      "alarmId": "A_5",
      "firstOccurTime": "2026-03-09 12:55:00 Z",
      "clearStatus": "Not cleaned",
      "severity": "次要",
      "alarmName": "定时任务处理成功率",
      "occurTime": "2026-03-09 12:57:00 Z",
      "cause": "定时任务处理成功率指标成功率小于0.9",
      "ip": null,
      "times": 2,
      "moType": "BatchExecutorCluster",
      "moduleName": "IndicatorAlarm",
      "additionalInformation": null
    }
  ],
  "performance_abnormal_data": [
    {
      "clusterName": "Batch Executor Cluster",
      "dn": "10cf2b16-f2d1-46c8-91c7-645dd85cf15e_294_BatchExecutor",
      "neName": null,
      "alarmId": "P_6",
      "firstOccurTime": "2026-03-09 12:54:00 Z",
      "clearStatus": "Not cleaned",
      "severity": "重要",
      "alarmName": "定时任务处理平均时长",
      "occurTime": "2026-03-09 12:57:00 Z",
      "cause": "定时任务处理平均时长指标P值小于0.7",
      "ip": null,
      "times": 5,
      "moType": "BatchExecutorCluster",
      "moduleName": "IndicatorAlarm",
      "additionalInformation": null
    }
  ],
  "component_abnormal_data": [],
  "startTime": "2026-03-09 12:53:15 Z",
  "endTime": "2026-03-09 13:03:16 Z",
  "envCode": "DigitalCRM.sit"
}
```

### 步骤3：检查告警

验证是否存在任何告警。如果没有告警，**则通知用户该对应时间段无告警数据，暂不需进行根因分析，并结束流程**。

### 步骤4：查询拓扑信息
执行拓扑数据查询：
```bash
python "./scripts/get_topography.py"
```

#### 步骤5：校验拓扑数据
检查是否获取到拓扑数据，如果没有拓扑，生成"无拓扑"报告并跳过根因识别。

### 步骤6：识别根因

将步骤2和步骤4查询到的告警数据`${alarms}`+拓扑数据`${topography}`作为输入，按照以下要求进行根因识别，得到结果`${identifyResult}`。

#### 分析策略与优先级

1. **预定义推理链匹配（最高优先级）:**
   将输入的告警与以下逻辑进行比较。注意：推理链中列出的所有触发条件和现象描述均仅从告警列表中的 `alarmName` 字段中提取。如果满足条件，则必须使用此结论：
- **推理链 1（外部接口成功率）：**
  - **触发条件：** 同时观察到“Web处理成功率异常”、“Web处理平均延迟”、“Cluster RSP服务调用平均时长”和“AccessOut集群API访问成功率”告警。
  - **根本原因：** AccessOut集群访问成功率告警。
  - **推理逻辑：** AccessOut集群访问失败导致RSP调用失败，进而引起LBNSLBSHOP Web成功率下降。

- **推理链 2（BHF集群应用节点健康状况）：**
  - **触发条件：** 同时观察到“BHF集群应用节点健康检查异常（告警ID为 601000101）”和“RSP集群服务调用平均时长告警（告警ID为 601750142）”。
  - **根本原因：** BHF集群应用节点健康检查异常（告警ID为 601000101）。
  - **推理逻辑：** BHF集群应用节点健康检查异常导致RSP集群服务调用平均时长增加。

- **多链选择规则：** 如果当前告警列表同时匹配**多个**推理链的触发条件，则比较每个匹配链中识别的“根本原因告警”。选择其中根本原因告警的 `firstOccurTime` **最新（最近）** 的推理链。

2. **通用逻辑（备用策略）：**
   如果没有预定义的推理链匹配：
- 根据提供的**拓扑图**和**告警列表**，严格识别出**唯一**一个能最好解释故障的根本原因告警。
- **根本原因选择约束（严格）：**
  - **规则 1（ID过滤）：** 告警ID以 **“888”** 开头的告警被视为阈值/性能指标告警，**不得**被选为根本原因。
  - **规则 2（模块过滤）：** 告警中 `"moduleName": "Performance"` 的告警**不得**被选为根本原因。
  - **例外：** 只有当告警列表中的所有告警均为888前缀告警或Performance告警时，才能忽略规则 1 和 2。

#### 输出约束
- 输出到`${identifyResult}`变量。
- 输出的**必须为有效的JSON对象**。
- `reasoning` 字段必须简洁（≤50个字符）。
- 所使用的拓扑图必须完全从输入字段中提取。

#### 所需输出格式
```json
{
  "identityResults": {
    "reasoning": "Concise root cause explanation (≤50 characters).",
    "rootAlarm": {
        "clusterName": "...",
        "dn": "...",
        "neName": "...",
        "alarmId": "...",
        "firstOccurTime": 0,
        "clearStatus": "...",
        "severity": "...",
        "alarmName": "...",
        "occurTime": 0,
        "cause": "...",
        "ip": "...",
        "moType": "...",
        "moduleName": "...",
        "additionalInformation": "..."
    },
    "relatedAlarms": [
      {
        "clusterName": "...",
        "dn": "...",
        "neName": "...",
        "alarmId": "...",
        "firstOccurTime": 0,
        "clearStatus": "...",
        "severity": "...",
        "alarmName": "...",
        "occurTime": 0,
        "cause": "...",
        "ip": "...",
        "moType": "...",
        "moduleName": "...",
        "additionalInformation": "..."
      }
    ]
  },
  "status": "SUCCESS or UNCERTAIN"
}
```

### 步骤7：查询子拓扑信息

将根因识别结果中的`${rootAlarm}`字段和`${relatedAlarms}`字段的值作为输入，执行子拓扑数据查询，得到子拓扑`${subTopology}`

- 执行查询脚本：

```bash
python "./scripts/get_subtopography.py" --root_alarm=${rootAlarm} --related_alarms=${relatedAlarms}
```
- 响应示例
```json
{
    "getTopologyResponse": {
        "clusterNodes": [
            {
                "clusterName": "LBNSLBIF",
                "group": "Business System",
                "desc": "节点对外部系统暴露接口请求地址"
            },
            {
                "clusterName": "eChannel&USSD&SMSC",
                "group": "External System",
                "desc": "自渠业务;数据业务;短消息业务"
            }
        ],
        "relations": [
            {
                "srcClusterName": "eChannel&USSD&SMSC",
                "dstClusterName": "LBNSLBIF",
                "desc": "节点对外部系统暴露接口请求地址"
            }
        ]
    },
    "derivedAlarms": []
}
```

### 步骤8：生成根因分析报告
根据根因识别结果和子拓扑`${subTopology}`，生成根因分析报告。报告生成的**核心规则**、**生成流程**及**输出格式要求**如下。

#### 核心规则
1.  **完整拓扑图**: Mermaid图必须包含 `${subTopology}` 中**所有**的组件（节点）和连接（边）。不做任何裁剪、过滤或编辑。
2.  **告警标记**: 图中需要标记出与告警相关的节点，但不改变拓扑结构本身。
3.  **方向性规则**: **每个箭头 (`-->`) 必须从 `srcNode` 的 `clusterName` 指向 `dstNode` 的 `clusterName`**，严格遵循 `${subTopology}` JSON 数据中的定义。绝不反转或猜测方向。
4.  **Mermaid语法**: 仅使用**简单、确定的语法**：
  *   节点格式: `NODE_ID[Cluster Name]`  # Cluster Name 保持原始输入，不翻译
  *   边格式: `NODE_ID_A -- relation desc text --> NODE_ID_B`  # desc 字段保持原始输入，不翻译
  *   **禁止使用**: `subgraph`, `flowchart`, `style` declarations on the same line, 或其他复杂的 Mermaid 构造。
5.  **图布局**: 使用**自上而下 (`graph TD`)** 布局。不使要用左到右 (`graph LR`)。
6.  **语言策略**:
  *   报告正文（标题、分析、描述）使用100%中文。
  *   **Mermaid图中的所有内容保持原始语言**：
    - 节点名称 (`clusterName`)：保持输入的原样（可能是英文、中文或混合）
    - 边描述 (`desc`字段)：保持输入的原样（可能是英文、中文或混合）
      
#### 生成流程
1.  **解析输入**:
  - 从根因识别结果`${identifyResult}`中提取 `rootAlarm`
  - 解析子拓扑查询结果`${subTopology}` (JSON字符串)。
2.  **告警选择**: 仅用于标记节点，不用于裁剪拓扑。
  *   保留所有 `rootAlarm` 条目。
  *   从 `relatedAlarms` 中，选择与根因告警因果联系最强的**最多三个**。
  *   **总告警行数 ≤ 4**。
3.  **构建节点与边映射**:
  *   从 `${subTopology}` 创建**完整节点映射**: `{clusterId: {"name": clusterName, "hasAlarm": boolean}}`。`hasAlarm: true` 仅当该 `clusterId` 出现在保留的告警中。
  *   **节点ID创建**: 为每个 `clusterName` 使用 `clusterId` 生成**稳定的唯一ID**（例如 `CLUSTER_123`）。此ID必须在整个Mermaid代码中保持一致。
  *   创建**完整边列表**: 对于 `${subTopology}` 中的**每个**连接，添加条目: `{srcId: srcClusterId, dstId: dstClusterId, desc: descField}`。
4.  **生成Mermaid代码**:
  *   以 `graph TD` 和新行开始。
  *   **首先声明所有节点**: 对于节点映射中的每个条目，写入: `NODE_ID[Cluster Name]`。（例如 `CLUSTER_123[K8s-Product-Cluster]`）。
  *   **然后声明所有边**: 对于边列表中的每个条目，写入: `SRC_NODE_ID -- desc text --> DST_NODE_ID`。（例如 `CLUSTER_123 -- depends_on MySQL --> CLUSTER_456`）。
  *   **最后单独添加样式块**: 在所有节点和边定义之后，仅为 `hasAlarm: true` 的节点添加样式行: `style NODE_ID fill:#ffffff,stroke:#ff0000,stroke-width:2px`。其他节点使用默认样式（无样式行）。
5.  **填充报告模板**: 仅使用输入数据和上述导出的映射/列表填充所有部分。

#### 输出格式要求
**报告正文语言**: 全篇语言统一，使用与用户输入语句相同的语言。
**Mermaid图语言**: 保持原始输入语言（不翻译）。
**严格遵循此模板结构和标题**：参考报告模板文件[sample-report.md](references/sample-report.md)

#### 保存报告
必须将报告保存成markdown文件
- 存储文件名格式：`root-cause-report-{当前时间}.md`，当前时间使用`yyyyMMddHHmmss`格式，例如：`root-cause-report-20260301123000.md`
- 存储路径: `./output`

## 异常处理

- **无告警**：生成"系统正常"报告，通知用户
- **脚本执行失败**：再次尝试查询，如果两次都失败，则提示查询接口失败，但不能暴露系统内部信息

## 额外资源

### 参考文档
查看详细信息，请参考：
- **`references/api-spec.md`** - 告警查询、拓扑查询、子拓扑查询API规范
- **`references/sample-report.md`** - 示例根因分析报告
