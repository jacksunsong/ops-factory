# Control Center 部署指导

## 1. 文档目标

本文档用于指导开发、测试和运维人员部署 `control-center` 服务，并完成基础配置、启动验证和常见问题排查。

`control-center` 是 OpsFactory 的平台控制面服务，主要负责：

1. 查看受管服务健康状态
2. 查看受管服务日志和配置
3. 触发受管服务启动、停止、重启动作
4. 聚合 Gateway 运行时状态和可观测性数据
5. 为前端 Control Center 页面提供后端 API

如果需要了解它在整体架构中的职责边界，请参考：

- [docs/architecture/overview.md](/Users/buyangnie/Documents/GitHub/ops-factory/docs/architecture/overview.md)
- [docs/architecture/api-boundaries.md](/Users/buyangnie/Documents/GitHub/ops-factory/docs/architecture/api-boundaries.md)

## 2. 部署前置条件

### 2.1 基础环境

部署机器需要具备：

- Java 21
- Maven
- Bash
- Node.js
- curl

`control-center` 是 Java 21 + Spring Boot 服务，构建产物为：

```bash
control-center/target/control-center.jar
```

服务脚本会优先使用环境变量 `MVN` 指定的 Maven；如果没有指定，会尝试使用系统 `mvn`，以及少量内置候选路径。

### 2.2 目录要求

推荐从仓库根目录运行统一编排脚本：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
```

`control-center` 的受管服务控制依赖根目录脚本：

```bash
scripts/ctl.sh
```

因此不要只拷贝 `control-center/` 目录单独部署。它需要和仓库根目录的服务脚本、Gateway、Knowledge Service、Business Intelligence 等服务目录保持相对路径关系。

## 3. 关键目录与文件

| 路径 | 用途 |
| --- | --- |
| `control-center/config.yaml` | 实际运行时配置 |
| `control-center/config.yaml.example` | 配置样例 |
| `control-center/scripts/ctl.sh` | Control Center 单服务启停脚本 |
| `control-center/logs/control-center.log` | 后台启动时的服务日志 |
| `control-center/logs/control-center.pid` | 后台启动时的进程 PID 文件 |
| `control-center/data/config-backups/` | 通过 Control Center 修改受管服务配置时生成的备份 |
| `web-app/config.json` | 前端运行时配置，需和 Control Center 地址与密钥保持一致 |

## 4. 配置说明

### 4.1 服务端口

默认端口是 `8094`：

```yaml
server:
  port: 8094
```

也可以通过环境变量覆盖：

```bash
CONTROL_CENTER_PORT=18094 ./control-center/scripts/ctl.sh startup --background
```

脚本最终会以 `-Dserver.port=<port>` 启动 JAR。

### 4.2 Control Center 基础配置

核心配置位于 `control-center/config.yaml`：

```yaml
control-center:
  secret-key: "change-me"
  cors-origin: "http://localhost:5173"
  request-timeout-ms: 5000
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `control-center.secret-key` | 调用 `/control-center/**` API 时必须携带的 `x-secret-key` |
| `control-center.cors-origin` | 允许访问 Control Center API 的前端来源 |
| `control-center.request-timeout-ms` | Control Center 调用受管服务、Gateway、Langfuse 等外部接口的超时时间 |

注意：

- `/actuator/*` 健康检查接口不校验 `x-secret-key`。
- `/control-center/**` 接口都会校验 `x-secret-key`。
- 生产或共享环境不要使用默认密钥。

### 4.3 前端配置联动

前端页面通过 `web-app/config.json` 访问 Control Center：

```json
{
    "controlCenterUrl": "http://127.0.0.1:8094",
    "controlCenterSecretKey": "change-me"
}
```

要求：

- `controlCenterUrl` 指向 Control Center 的服务地址。
- `controlCenterSecretKey` 必须和 `control-center/config.yaml` 中的 `control-center.secret-key` 一致。
- 如果前端从 `http://127.0.0.1:5173` 访问，而 `cors-origin` 配成 `http://localhost:5173`，服务会自动同时允许 `127.0.0.1` 和 `localhost` 的同端口来源。

### 4.4 受管服务配置

`control-center.services[]` 声明可被 Control Center 监控和控制的服务：

```yaml
control-center:
  services:
    - id: gateway
      name: Gateway
      base-url: "http://127.0.0.1:3000"
      required: true
      health-path: "/gateway/status"
      ctl-component: "gateway"
      config-path: "gateway/config.yaml"
      log-path: "gateway/logs/gateway.log"
      auth:
        type: "secret-key"
        secret-key: "test"
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 服务唯一标识，前端路由和 API 路径会使用 |
| `name` | 页面展示名称 |
| `base-url` | Control Center 访问该服务的基础地址 |
| `required` | 是否为必需服务，用于页面状态表达 |
| `health-path` | 健康检查路径 |
| `ctl-component` | 根目录 `scripts/ctl.sh` 中的组件名 |
| `config-path` | 受管服务配置文件路径，相对仓库根目录 |
| `log-path` | 受管服务日志文件路径，相对仓库根目录 |
| `auth.type` | 健康检查是否需要鉴权，支持 `none` 和 `secret-key` |
| `auth.secret-key` | 当 `auth.type` 为 `secret-key` 时发送到受管服务的 `x-secret-key` |

默认样例包含：

- `gateway`
- `knowledge-service`
- `business-intelligence`

如果新增受管服务，需要同时确认：

1. `ctl-component` 是根目录 `scripts/ctl.sh` 支持的组件名。
2. `health-path` 能返回 2xx。
3. `config-path` 和 `log-path` 指向真实可读写文件。
4. 鉴权密钥与目标服务配置一致。

### 4.5 Langfuse 可观测性配置

如果需要在 Control Center 中查看 Langfuse 相关可观测性数据，配置：

```yaml
control-center:
  langfuse:
    host: "http://127.0.0.1:3001"
    public-key: "<public-key>"
    secret-key: "<secret-key>"
```

如果 `host` 为空，Control Center 会认为可观测性能力未启用。

## 5. 构建与启动

### 5.1 推荐：通过根目录统一编排启动

启动全部服务：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh startup all
```

只启动 Control Center：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh startup control-center
```

查看状态：

```bash
./scripts/ctl.sh status control-center
```

重启：

```bash
./scripts/ctl.sh restart control-center
```

停止：

```bash
./scripts/ctl.sh shutdown control-center
```

统一编排启动 `all` 时，Control Center 是必需服务，启动顺序在 Gateway、Knowledge Service、Business Intelligence 和 Exporter 之后、Webapp 之前。

### 5.2 单服务脚本启动

也可以直接使用 Control Center 自己的脚本：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory/control-center
./scripts/ctl.sh startup --background
./scripts/ctl.sh status
```

前台启动：

```bash
./scripts/ctl.sh startup --foreground
```

前台启动适合本地调试，终端关闭会结束服务。

### 5.3 手动构建

脚本会自动判断是否需要构建。如果需要手动构建：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory/control-center
mvn package -DskipTests
```

构建完成后确认：

```bash
ls target/control-center.jar
```

## 6. 启动验证

### 6.1 验证进程和健康检查

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh status control-center
curl -fsS http://127.0.0.1:8094/actuator/health
```

健康检查成功时，`ctl.sh status` 会显示服务正在运行，并给出端口和 PID。

### 6.2 验证 Control Center API

`/control-center/**` API 需要带 `x-secret-key`：

```bash
curl -fsS \
  -H 'x-secret-key: change-me' \
  http://127.0.0.1:8094/control-center/status
```

期望返回：

```json
{"status":"ok"}
```

查看受管服务列表：

```bash
curl -fsS \
  -H 'x-secret-key: change-me' \
  http://127.0.0.1:8094/control-center/services
```

查看指定服务日志：

```bash
curl -fsS \
  -H 'x-secret-key: change-me' \
  'http://127.0.0.1:8094/control-center/services/gateway/logs?lines=100'
```

### 6.3 验证前端入口

启动前端：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh startup webapp
```

访问：

```text
http://127.0.0.1:5173/control-center
```

检查点：

- 页面可以打开 Control Center。
- Gateway、Knowledge Service、Business Intelligence 等服务卡片能显示健康状态。
- 管理员用户可以进入服务详情页查看配置和日志。
- 如果页面返回 401，优先检查 `web-app/config.json` 的 `controlCenterSecretKey`。

## 7. 日常运维操作

### 7.1 查看日志

后台启动时查看 Control Center 日志：

```bash
tail -f /Users/buyangnie/Documents/GitHub/ops-factory/control-center/logs/control-center.log
```

排查启动失败：

```bash
rg "ERROR|Exception|Failed" /Users/buyangnie/Documents/GitHub/ops-factory/control-center/logs/control-center.log
```

### 7.2 修改配置后重启

修改 `control-center/config.yaml` 后：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh restart control-center
```

修改受管服务配置后，需要重启对应受管服务。例如 Gateway：

```bash
./scripts/ctl.sh restart gateway
```

### 7.3 通过 Control Center 修改受管服务配置

Control Center 支持读取和写入受管服务配置：

- `GET /control-center/services/{id}/config`
- `PUT /control-center/services/{id}/config`

写入前会把旧配置备份到：

```bash
control-center/data/config-backups/
```

每个服务配置文件默认保留最近 5 份备份。

注意：

- 备份只覆盖通过 Control Center 写入配置的场景。
- 手工修改文件不会自动生成备份。
- 配置写入后通常还需要重启对应服务，配置才会生效。

### 7.4 服务启停权限

Control Center 的服务启停动作会调用：

```bash
scripts/ctl.sh <startup|shutdown|restart> <ctl-component>
```

因此运行 Control Center 的用户必须具备：

- 执行 `scripts/ctl.sh` 的权限
- 读取受管服务配置和日志的权限
- 写入受管服务配置和备份目录的权限
- 启停相关进程所需的系统权限

## 8. API 快速参考

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/actuator/health` | Spring Boot 健康检查，不需要 `x-secret-key` |
| `GET` | `/control-center/status` | Control Center 基础状态 |
| `GET` | `/control-center/services` | 查看受管服务列表和健康状态 |
| `GET` | `/control-center/services/{id}` | 查看单个受管服务状态 |
| `POST` | `/control-center/services/{id}/actions/start` | 启动受管服务 |
| `POST` | `/control-center/services/{id}/actions/stop` | 停止受管服务 |
| `POST` | `/control-center/services/{id}/actions/restart` | 重启受管服务 |
| `GET` | `/control-center/services/{id}/config` | 读取受管服务配置 |
| `PUT` | `/control-center/services/{id}/config` | 写入受管服务配置 |
| `GET` | `/control-center/services/{id}/logs?lines=200` | 查看受管服务日志 |
| `GET` | `/control-center/events` | 查看近期服务事件 |
| `GET` | `/control-center/runtime/system` | 查看 Gateway 系统运行时信息 |
| `GET` | `/control-center/runtime/instances` | 查看 Gateway 实例运行时信息 |
| `GET` | `/control-center/runtime/agents` | 查看 Gateway Agent 信息 |
| `GET` | `/control-center/runtime/metrics` | 查看 Gateway 运行时指标 |
| `GET` | `/control-center/observability/status` | 查看可观测性能力状态 |
| `GET` | `/control-center/observability/overview` | 查看可观测性概览 |
| `GET` | `/control-center/observability/traces` | 查看 trace 列表 |
| `GET` | `/control-center/observability/observations` | 查看 observation 汇总 |

除 `/actuator/health` 外，以上 `/control-center/**` 接口都需要请求头：

```text
x-secret-key: <control-center.secret-key>
```

## 9. 常见问题排查

### 9.1 服务无法启动

优先检查：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh status control-center
lsof -i :8094
tail -n 200 control-center/logs/control-center.log
```

常见原因：

- Java 版本不是 21。
- Maven 构建失败。
- `8094` 端口被占用。
- `control-center/config.yaml` YAML 格式错误。
- 启动用户没有写入 `control-center/logs/` 或 `control-center/data/` 的权限。

### 9.2 健康检查失败但进程存在

现象：

```bash
control-center process running but health check failed
```

处理步骤：

1. 查看 `control-center/logs/control-center.log`。
2. 确认 `server.port` 或 `CONTROL_CENTER_PORT` 是否符合预期。
3. 确认 `curl http://127.0.0.1:8094/actuator/health` 是否能返回。
4. 如果端口打开但健康检查失败，重启服务：

```bash
./scripts/ctl.sh restart control-center
```

### 9.3 前端访问 Control Center 返回 401

原因通常是密钥不一致。

检查：

```bash
rg "secret-key|controlCenterSecretKey" control-center/config.yaml web-app/config.json
```

要求：

- `control-center/config.yaml` 的 `control-center.secret-key`
- `web-app/config.json` 的 `controlCenterSecretKey`

两者必须一致。

### 9.4 前端跨域失败

检查 `control-center/config.yaml`：

```yaml
control-center:
  cors-origin: "http://localhost:5173"
```

要求：

- 协议、主机、端口要和浏览器访问前端的来源一致。
- 本地 `localhost` 和 `127.0.0.1` 同端口会自动兼容。
- 如果前端部署到其他域名，需要更新 `cors-origin` 并重启 Control Center。

### 9.5 受管服务显示 down

先确认 Control Center 自己正常：

```bash
curl -fsS http://127.0.0.1:8094/actuator/health
```

再确认目标服务健康检查：

```bash
curl -fsS http://127.0.0.1:3000/gateway/status
curl -fsS http://127.0.0.1:8092/actuator/health
curl -fsS http://127.0.0.1:8093/actuator/health
```

如果 Gateway 需要密钥：

```bash
curl -fsS \
  -H 'x-secret-key: test' \
  http://127.0.0.1:3000/gateway/status
```

然后检查 `control-center.services[]` 中对应服务的：

- `base-url`
- `health-path`
- `auth.type`
- `auth.secret-key`

### 9.6 启停受管服务失败

Control Center 通过根目录编排脚本执行服务动作。先手工验证：

```bash
cd /Users/buyangnie/Documents/GitHub/ops-factory
./scripts/ctl.sh restart gateway
./scripts/ctl.sh status gateway
```

如果手工执行也失败，问题不在 Control Center，而在对应服务脚本或服务本身。

如果手工执行成功但页面执行失败，检查：

- 运行 Control Center 的用户是否有执行脚本权限。
- `control-center.services[].ctl-component` 是否写成根脚本支持的组件名。
- `control-center/logs/control-center.log` 中是否有脚本执行错误。

### 9.7 无法查看配置或日志

检查配置：

```yaml
config-path: "gateway/config.yaml"
log-path: "gateway/logs/gateway.log"
```

要求：

- 路径相对仓库根目录。
- 文件存在或所在目录可访问。
- 运行 Control Center 的用户具备读取权限。
- 如果要通过页面保存配置，还需要写入权限。

### 9.8 Runtime 或 Observability 页面失败

Runtime 接口依赖 Gateway：

- `/gateway/runtime-source/system`
- `/gateway/runtime-source/instances`
- `/gateway/agents`
- `/gateway/runtime-source/metrics`

排查顺序：

1. 确认 Gateway 正常运行。
2. 确认 `gateway` 受管服务的 `auth.secret-key` 和 Gateway 密钥一致。
3. 确认 `base-url` 指向真实 Gateway 地址。
4. 查看 `control-center/logs/control-center.log` 中的 `Failed to fetch gateway runtime source` 错误。

Observability 接口依赖 `control-center.langfuse.*` 配置和 Langfuse 服务。如果未配置 `langfuse.host`，页面应显示可观测性未启用，而不是按服务故障处理。

## 10. 部署检查清单

上线或交付前确认：

- `java -version` 显示 Java 21。
- `control-center/config.yaml` 已从样例调整为目标环境配置。
- `control-center.secret-key` 不使用默认值。
- `web-app/config.json` 的 `controlCenterUrl` 和 `controlCenterSecretKey` 与服务端一致。
- `control-center.services[]` 中的健康检查地址都能从部署机访问。
- `ctl-component` 都是根目录 `scripts/ctl.sh` 支持的组件名。
- `config-path` 和 `log-path` 都指向真实路径。
- `./scripts/ctl.sh status control-center` 通过。
- `curl http://127.0.0.1:8094/actuator/health` 通过。
- 前端 `/control-center` 页面可以正常打开并显示受管服务状态。
