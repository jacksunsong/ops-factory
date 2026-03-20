---
name: collect-fault-information
description: 收集对应时间段内根因告警相关的故障日志或者故障告警信息（默认返回日志信息），当用户提到“收集故障信息”、“收集故障日志”等表示“收集故障/日志信息”意图时，使用此技能
version: 1.0.0
---

# 收集故障信息

## 概述
收集对应时间段内根因告警相关的故障日志或者故障告警信息（默认收集日志信息），给用户提供可下载数据的ID。在用户已经进行了根因告警分析后，才可收集故障节点信息，否则提示用户先进行根因告警分析。

## 适用场景

当以下情况时使用此技能：
- 用户想要查看故障信息
- 用户想要了解故障日志信息

## 分析流程

### 步骤1：提取`${clusterName}`属性

1. 从当前会话上下文中提取根因分析结果中的根因告警`rootAlarm`，若无根因识别结果，则通知用户先进行根因识别，并结束流程。
2. 从`${rootAlarm}`对象中提取`${clusterName}`属性

### 步骤2：解析并校验时间范围
#### 解析开始时间和结束时间

从用户输入中提取开始时间`${statr_time}`和结束时间`${end_time}`。如果未指定时间范围，默认`${statr_time}`为15分钟前，`${end_time}`为当前时间。**时间格式要求**：- `yyyy-MM-dd HH:mm:ss`格式


#### 校验开始时间和结束时间
时间范围必须满足要求，若不满足，则向用户确认需要分析的时间范围，并提示用户输入的时间具体违反了哪个要求。 

**校验规则**：
  - 开始时间不得早于48小时前
  - 结束时间必须大于等于开始时间
  - 开始时间与结束时间，时间跨度不得超过15分钟


### 步骤3：收集故障信息
分析用户输入语句，判断用户是想收集故障告警信息还是收集故障日志信息。若用户没有明确表示收集故障告警信息，则默认执行收集故障日志信息。

#### 收集故障告警信息

执行脚本：

```bash
python "./scripts/collect_fault_info.py" --start_time=${statr_time} --end_time=${statr_time} --info_type='alarm'
```

示例：

```bash
python "./scripts/collect_fault_info.py" --start_time='2026-03-13 12:00:00' --end_time='2026-03-13 12:15:00' --info_type='alarm'
```
#### 收集故障日志信息

执行脚本：

```bash
python "./scripts/collect_fault_info.py" --start_time=${statr_time} --end_time=${statr_time} --info_type='log'
```

示例：

```bash
python "./scripts/collect_fault_info.py" --start_time='2026-03-13 12:00:00' --end_time='2026-03-13 12:15:00' --info_type='log'
```
#### 响应示例
```
https://192.11.1.1:8080/itom/api/file/download?fileId=afa8fa62-8bb9-44c9-bdbb-aec86a6794ba
```

### 步骤4: 返回故障信息ID
将脚本执行获取到的下载链接返回给用户。提示用户可通过此链接下载数据。

## 异常处理

- **无告警**：生成"无相关信息"报告，通知用户
- **脚本执行失败**：再次尝试查询，如果两次都失败，则提示查询接口失败，但不能暴露系统内部信息

## 额外资源

### 参考文档
查看详细信息，请参考：
- **`references/api-spec.md`** - 查询故障告警/日志API规范
