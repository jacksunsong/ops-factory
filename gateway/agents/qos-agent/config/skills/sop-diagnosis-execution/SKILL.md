---
name: sop-diagnosis-execution
description: 当用户明确要求进行远程诊断时（如"环境诊断"、"执行SOP"、"实时诊断"、"环境排查"、"远程诊断"），采用主SOP调度+子SOP执行的两层架构：根据告警列表按组网层级（Layer）由小到大调度子SOP，根据告警IP精确定位目标主机执行诊断，发现异常即中断后续轮次，生成综合诊断报告。
version: 4.0.0
---

# SOP 环境实时诊断

## 组网拓扑

```
Layer1: haproxy → Layer2: RCPA → Layer3: RCPADB / GMDB / KAFKA（同层可并行）
```

| 层级 | category | 说明 |
|------|----------|------|
| 1 | haproxy | 负载均衡 |
| 2 | RCPA | 应用层 |
| 3 | RCPADB / GMDB / KAFKA | 数据层 |

## MCP 工具

| 工具 | 用途 |
|------|------|
| `list_sops(level, category?)` | 列出子SOP，建立 category→sopId 映射 |
| `get_sop_detail(sopId)` | 获取子SOP完整定义（含mermaid流程图） |
| `get_hosts(tags?)` | 查询主机列表 |
| `execute_remote_command(hostId, command, timeout?)` | 远程执行命令（输出自动保存为附件） |
| browser-use 系列 | 浏览器操作（navigate/click/type/screenshot/extract_content 等） |

## 执行流程

### 1. 解析告警

从用户消息中提取告警列表，每条告警提取 **集群类型**（haproxy/RCPA/RCPADB/GMDB/KAFKA）和 **节点IP**。无告警则向用户确认。按层级分组：Layer1=haproxy, Layer2=RCPA, Layer3=RCPADB/GMDB/KAFKA。

### 2. 加载子SOP映射

调用 `list_sops(level="sub")` 建立 `category → sopId` 映射。

### 3. 主SOP调度（逐层执行）

> ⚠️ **必须逐层执行。healthy 时继续下一层，不能只执行一层就停止。** 发现异常则 break 跳转到步骤5。

按层级顺序，对每层告警：
1. 从映射表找到对应 sopId
2. 调用 `get_sop_detail(sopId)` — **必须向用户展示返回的 mermaid 流程图**
3. 执行子SOP（步骤4）
4. 结果判定：全部 healthy → 继续下一层；发现异常 → break

Layer3 同层多个 category 可并行执行。

### 4. 执行子SOP

**每个子SOP按其 nodes 数组中配置的节点依次执行，严格遵循节点定义的 transitions 分支逻辑。**

#### 准备
- 根据子SOP的 `hostTags` 调用 `get_hosts`，**仅保留 IP 匹配告警的主机**
- 无匹配主机则跳过该子SOP

#### 每个节点的执行

**type=start 或 analysis：**
1. 读取 `command` 模板，替换 `{{变量}}`（优先用上下文推断，其次用 `defaultValue`）
2. 对每台目标主机调用 `execute_remote_command`
3. 根据 `analysisInstruction` 和 `outputFormat` 分析输出
4. 分支判断 → 见下方"分支判断规则"

**type=browser：**
1. `browser_navigate` 打开 `browserUrl` → 立即 `browser_screenshot`
2. `browser_get_state` 获取元素列表
3. 根据 `browserAction` 描述逐步操作（click/type/scroll等），每步完成后 `browser_screenshot`
4. `browser_extract_content` 提取数据 → `browser_screenshot`
5. `browser_close_all` 关闭浏览器
6. 根据 `analysisInstruction` 分析结果
7. 分支判断 → 同上

**type=end：** 该分支立即终止，标记"流程正常结束"。

#### 分支判断规则

评估当前节点的 `transitions`，逐条严格匹配：
- **条件满足 + `requireHumanConfirm: true`** → ⛔ **立即停止**，输出确认消息后结束本轮对话：
  ```
  ⏸️ 请确认是否继续检查「{后续节点名称}」？回复「继续」或「否」。
  ```
  用户回复后继续执行对应 nextNodes。
- **条件满足 + 无确认标记** → 执行对应 `nextNodes`
- **所有条件不满足** → 该分支终止
- **禁止**条件不满足时自行继续

### 5. 生成诊断报告

保存为 `./output/sop-diagnosis-report-{yyyyMMddHHmmss}.md`，结构：

```markdown
# SOP环境实时诊断报告
## 诊断概述
- 告警队列、已执行子SOP、首个异常子SOP

## 子SOP执行结果
### Layer{N}：{category}环境诊断
- 执行状态：healthy / 异常
- 涉及主机
- 每个节点的：执行命令、日志附件路径、分析结论

## 综合分析
## 附件清单（必须填写实际文件路径）
## 处理建议
```

附件必须包含所有 `sop-exec-*.log` 和浏览器截图文件路径。

## 安全约束
- 命令必须在白名单内（ps/tail/grep/cat/ls/df/free/netstat/top/iostat/ping等只读命令）
- 禁止执行修改类命令（rm/mv/chmod/reboot/service等）
- 主机连接失败或命令超时：记录并继续
