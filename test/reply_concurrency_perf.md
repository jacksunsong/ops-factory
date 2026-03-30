# Reply 并发测试脚本使用说明

## 概述
该脚本用于对 Gateway 的 `/reply` SSE 接口进行并发压测，并提供端到端耗时统计、按轮次与按用户的聚合结果，同时可以从本地 Gateway 日志中抓取 `REPLY-PERF` 阶段日志并进行时间对齐，帮助定位性能瓶颈。

- 脚本路径：[reply_concurrency_perf.py](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py)
- 典型接口路径：`http://127.0.0.1:3000/ops-gateway/agents/<agentId>/reply`
- 日志文件默认位置：`gateway/logs/gateway.log`

## 环境要求
- Python 3.9 及以上（脚本已兼容 3.9：使用 `typing.Optional/Dict/List/Tuple`）
- 本地已启动 Gateway（默认监听 `http://127.0.0.1:3000`）
  - 可用根脚本启动：`./scripts/ctl.sh startup gateway`
  - 查看状态：`./scripts/ctl.sh status`
- 建议同时运行 Webapp 以便联动验证（可选）：`./scripts/ctl.sh startup webapp`

## 快速开始
```bash
python3 test/reply_concurrency_perf.py
```
默认值：
- base-url: `http://127.0.0.1:3000`
- path-prefix: `/ops-gateway`
- agent-id: `universal-agent`
- scenario: `warm`
- preset: `multi-user-same-agent`
- concurrency: `5`
- rounds: `3`

终端将输出：
- 每条请求的实时结果（`request / round / worker / user / session / ttfb / total / status / dataLines`）
- 总体汇总、按轮次统计、按用户统计
- 若开启日志对齐，会展示各阶段的耗时摘要

## 常用参数
```text
--concurrency <int>        并发槽位数（同时发起的请求数量）
--rounds <int>             总轮次（每轮会发送 concurrency 条请求）
--scenario warm|cold       会话策略：warm 复用 session；cold 每次请求新建并关闭 session
--preset                   预置用户分布场景：
  multi-user-same-agent    多用户并发访问同一个 agent
  single-user-multi-session 单用户并发多个 session
--align-reply-perf-log     开启日志对齐（从 gateway.log 抓取 REPLY-PERF 并对齐本次请求）
--output-json <file>       将结果写出为 JSON 文件（含 config/summary/by_round/by_user/log_alignment/results）
--message <text>           发送给 agent 的用户消息（会附带请求标签等信息）
--agent-id <id>            测试的 agent 标识
--base-url <url>           网关地址（默认 http://127.0.0.1:3000）
--path-prefix <path>       网关前缀（默认 /ops-gateway）
--reply-path reply|agent/reply 接口路径形式（默认 reply）
--timeout-sec <float>      单次请求超时（默认 120.0 秒）
--stagger-ms <int>         同轮次内的请求启动间隔（毫秒，默认 0）
--verify-tls               启用 TLS 校验（默认关闭）
--gateway-log-file <path>  Gateway 日志文件路径（默认 gateway/logs/gateway.log）
--log-tail-lines <int>     对齐时最多读取日志行数（默认 4000）
--log-window-before-ms <int> 对齐窗口：请求开始前的毫秒数（默认 1500）
--log-window-after-ms <int>  对齐窗口：请求结束后的毫秒数（默认 5000）
--log-preview-limit <int>  控制台展示的对齐请求数（默认 10）
```

## 预置场景
- multi-user-same-agent
  - 每个并发槽位使用不同用户（如 `perf-user-1`、`perf-user-2`）
  - 适合测“多租户同时访问同一 agent”
- single-user-multi-session
  - 所有并发槽位共用同一用户，但各自 session 不同
  - 适合测“同一用户多会话并发”
  - 共享用户 ID 使用 `--shared-user-id` 指定（默认 `perf-shared-user`）

用户生成逻辑见 [build_user_id](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L419-L422)。

## 会话模式
- warm
  - 压测前为每个槽位预建 session，压测过程中复用
  - 更适合测稳定对话场景，避免把 session 创建成本混入统计
- cold
  - 每次请求前创建 session，请求后立即停止
  - 更适合测冷启动/新会话开销

实现位置：
- 预建/复用： [prepare_workers](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L515-L527)
- 单次请求处理： [run_reply_once](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L430-L512)

## 日志对齐（REPLY-PERF）
开启 `--align-reply-perf-log` 后：
- 读取 `gateway/logs/gateway.log` 中的 `[REPLY-PERF]` 行
- 基于 `userId + sessionId + 时间窗口` 对齐每次请求
- 计算并展示各阶段耗时：`hooks/getOrSpawn/resume/firstChunk/relayComplete/outputFiles` 等

实现位置：
- 对齐入口： [align_reply_perf_logs](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L669-L731)
- 时间窗口说明： [event_matches_result](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L644-L653)
- 阶段耗时提取： [event_metric_ms](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L656-L666)

说明：
- `hooks` 可能早于流返回；`outputFiles` 可能稍晚结束，因此保留开始前与结束后一定的时间窗口以匹配日志。
- Gateway 已在 `hooks/getOrSpawn` 阶段补充了 `sessionId`，详见 [ReplyController.java:L84-L114](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/gateway/gateway-service/src/main/java/com/huawei/opsfactory/gateway/controller/ReplyController.java#L84-L114)。

## 输出结果结构
控制台：
- 单请求执行明细
- 总体汇总：总请求数、成功/失败数、TTFB/Total 的 `avg/p50/p95/p99/max`
- 按轮次统计
- 按用户统计
- 日志对齐摘要（可选）

JSON 文件（启用 `--output-json`）：
- `config`：本次压测参数
- `summary`：总体汇总
- `by_round`：按轮次聚合
- `by_user`：按用户聚合
- `log_alignment`：日志对齐结果
- `results`：每条请求的完整明细（含时间戳、请求标签、用户/会话、耗时等）
- 写出逻辑见 [save_results](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L734-L764)

## 示例命令
多用户并发压测（warm）：
```bash
python3 test/reply_concurrency_perf.py \
  --concurrency 10 \
  --rounds 3 \
  --scenario warm \
  --preset multi-user-same-agent
```

单用户多会话压测（warm）：
```bash
python3 test/reply_concurrency_perf.py \
  --concurrency 10 \
  --rounds 3 \
  --scenario warm \
  --preset single-user-multi-session \
  --shared-user-id perf-shared-user
```

冷启动场景压测（cold）：
```bash
python3 test/reply_concurrency_perf.py \
  --concurrency 5 \
  --rounds 2 \
  --scenario cold \
  --preset multi-user-same-agent
```

开启日志对齐与写出 JSON：
```bash
python3 test/reply_concurrency_perf.py \
  --concurrency 5 \
  --rounds 2 \
  --scenario warm \
  --preset multi-user-same-agent \
  --align-reply-perf-log \
  --output-json /tmp/reply-perf-result.json
```

## 故障排查
- Python 报类型注解错误（如 `type | None` 报错）：
  - 请使用 Python 3.9+，脚本已使用 `Optional[...]` 等兼容写法
- 无法对齐日志：
  - 确认已启动 Gateway，且 `gateway/logs/gateway.log` 中存在 `[REPLY-PERF]` 行
  - 调整 `--log-window-before-ms` / `--log-window-after-ms` 扩大窗口
  - 指定正确的 `--gateway-log-file` 路径
- SSE 流为空：
  - 终端会输出 `empty_sse_stream`，检查网关接口返回与代理链路

## 附：关键入口与代码位置
- 参数解析：[build_parser](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L63-L90)
- SSE 流处理与耗时统计：[stream_reply](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L176-L220)
- 汇总输出：
  - 总体汇总：[print_summary](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L306-L335)
  - 按轮次统计：[group_results_by_round](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L338-L355)
  - 按用户统计：[group_results_by_user](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L358-L375)
- 日志对齐：
  - 加载与过滤：[load_reply_perf_events](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L600-L641)
  - 匹配策略与窗口：[event_matches_result](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L644-L653)
  - 阶段耗时提取：[event_metric_ms](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L656-L666)
  - 对齐汇总与展示：[align_reply_perf_logs](file:///Users/zlj/Documents/ZLJ/works/code/ops-factory/test/reply_concurrency_perf.py#L669-L731)

