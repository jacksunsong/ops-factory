---
name: system-health-preliminary-analysis
description: 当用户提出“分析系统健康状况”、“检查健康状态”、“健康曲线分析”、“分析健康评分”、“系统整体状态”、“系统健康概览”或提及健康评分和系统健康时，应使用此技能。此时需进行系统健康初步分析，包括趋势分析和影响因素评估。
version: 1.0.0
---

# 系统健康初步分析

## 概述

执行初步系统健康分析，收集健康分数数据并生成分析报告。分析可用性、性能和综合健康状态，提供趋势评估和影响因子识别。

## 适用场景

当以下情况时使用此技能：
- 用户请求系统健康分析
- 用户要求初步健康评估
- 用户想要检查某段时间的系统状态
- 用户提及健康分数或系统指标

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


### 步骤2：获取健康分数数据
使用解析出来的`${statr_time}`和`${end_time}`，作为脚本输入参数，调用脚本，获取健康分数信息`${health_score}`。

执行`./scripts/get_health_score.py`脚本：
```bash
python "./scripts/get_health_score.py" --start_time=`${statr_time}` --end_time=`${end_time}`
```

- 响应字段说明：
  * 这是一个由 n 个 [0,1] 数值组成的数组（折线图数据）。
  * `overall_score`: 在开始时间和结束时间段内，每分钟的综合健康分。
  * `available_health_score`: 在开始时间和结束时间段内，每分钟的可用性健康分。
  * `performance_health_score`: 在开始时间和结束时间段内，每分钟的性能健康分。
  * `component_health_score`: 在开始时间和结束时间段内，每分钟的组件健康分。
  * `healthWeight`: 可用性、性能、组件三部分在综合健康分中的权重。
  * `startTime`: 开始时间，年月日时分秒。
  * `endTime`: 结束时间，年月日时分秒。
  * `envCode`: 环境编码。
  * `available_indicator_detail`: 最新有效时刻的可用性健康分指标名称和指标值。
  * `performance_indicator_detail`: 最新有效时刻的性能健康分指标名称和指标值。
- 响应示例：
    ```json
    {
      "overall_score": [],
      "available_health_score": [],
      "performance_health_score": [],
      "component_health_score": [],
      "healthWeight": "0.4,0.4,0.2",
      "healthThresholds": "0.9,0.7,0.5",
      "available_indicator_detail": [],
      "performance_indicator_detail": [],
      "startTime": "2025-12-04 10:09:52",
      "endTime": "2025-12-04 11:09:52",
      "envCode": "DigitalCRM.sit"
    }
    ```

### 步骤3：执行健康分析

根据健康分数信息`${health_score}`及**阈值判定逻辑**，分析系统健康情况。阈值判定逻辑如下：

**阈值判定逻辑 (Strict Rule)**:

请提取分数数组中的**最新值**或**最低值**进行判定：
* `> 0.9`: **健康 (Healthy)**
* `> 0.7 且 <= 0.9`: **亚健康 (Sub-healthy)**
* `> 0.5 且 <= 0.7`: **异常 (Abnormal)**
* `<= 0.5`: **严重异常 (Severe/Critical)**

### 步骤4：生成分析报告

根据`${health_score}`信息和`阈值判定逻辑`，给出在`开始时间`和`结束时间`这段时间内的健康度分析。

* **输出内容(极度严格)**:

  1. **系统健康状态结论**：基于最新有效时刻的数据，判断系统的健康状态。同时用`available_detail`和`performance_detail`描述业务影响。
  2. **系统健康状态解释**：系统健康度得分由哪些因素决定，不同因素的重要程度是什么样的。如何得到上面的系统健康状态结论。解释部分结论要和**系统健康状态结论**结论保持一致。
  3. **趋势分析**：分析后续系统健康得分会有什么样的变化趋势，并给出准确的分析过程。

* **输出要求**:

  1. 只能包含以上三个章节内容，不能发挥，输出不能冗余。
  2. 输出报告要求必须是markdown格式，报告结构清晰，内容简洁。

* **输出报告示例**:

  1. 参考 [sample-report.md](references/sample-report.md)

### 步骤5：保存报告到本地

**务必将报告保存为本地文件**，具体要求如下：

1. 报告文件存储路径: `./output`
2. 必须保存为markdown格式
3. 获取系统当下时间`${nowTime}`，转换为`yyyyMMddHHmmss`格式
4. 存储文件名格式：`system-health-overwiew-${nowTime}.md`,例如：`system-health-overwiew-20260301123000.md`


## 错误处理

处理错误场景：
- **无效时间范围**：使用默认1小时并通知用户
- **API连接失败**：通知用户查询健康分数数据失败
- **空数据**：通知用户无可用健康数据
- **报告保存失败**：直接向用户显示报告内容，并通知用户未能成功保存本地文件

## 额外资源

### 参考文档

查看详细信息，请参考：
- [api-spec.md](references/api-spec.md) - 完整的API接口规范
- [sample-report.md](references/sample-report.md) - 示例健康分析报告

## 最佳实践

- 在API调用前始提取开始时间和结束时间，禁止随意生成时间
- 分析时仅使用健康分数信息`${health_score}`作为输入，并严格遵守阈值判定逻辑
- 报告内容严格遵守输出要求
- 返回给用户
- 将markdown报告保存到`./output`路径下
