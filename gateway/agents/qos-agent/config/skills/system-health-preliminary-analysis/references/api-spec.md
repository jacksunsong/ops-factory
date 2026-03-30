# 健康分析 API 接口规范

## API 基础配置

### Base URL

```
ip:port
```

### 认证方式

使用 Basic Authentication，需要在[config.ini](../config/config.ini)中配置用户名和密码。

---

## 获取健康分数接口

### 请求信息

- **URL**: `/itom/machine/qos/getDiagnoseHealthScore`
- **Method**: `POST`
- **Content-Type**: `application/json`

### 请求参数

| 参数名       | 类型     | 必填 | 说明        |
|-----------|--------|----|-----------|
| startTime | long   | 是  | 开始时间戳（毫秒） |
| endTime   | long   | 是  | 结束时间戳（毫秒） |
| envCode   | string | 是  | 监控环境编码    |

### 请求示例

```json
{
  "envCode": "example.code",
  "startTime": 1764842992000,
  "endTime": 1764846592000
}
```

### 响应参数
这是一个由 n 个 [0,1] 数值组成的数组（折线图数据），具体字段说明如下：

| 参数名                          | 类型           | 说明                                 |
|------------------------------|--------------|------------------------------------|
| startTime                    | string       | 请求的开始时间, 格式：`yyyy-MM-dd HH:mm:ss`  |
| endTime                      | string       | 请求的结束时间戳, 格式：`yyyy-MM-dd HH:mm:ss` |
| envCode                      | string       | 监控环境编码                             |
| overall_score                | array[float] | 在开始时间和结束时间段内，每分钟的综合健康分             |
| available_health_score       | array[float] | 在开始时间和结束时间段内，每分钟的可用性健康分。           |
| performance_health_score     | array[float] | 在开始时间和结束时间段内，每分钟的性能健康分。            |
| component_health_score       | array[float] | 在开始时间和结束时间段内，每分钟的组件健康分。            |
| healthWeight                 | array[float] | 可用性、性能、组件三部分在综合健康分中的权重             |
| healthThresholds             | array[json]  | 可用性、性能、组件三部分的健康阈值                  |
| available_indicator_detail   | array[float] | 最新有效时刻的可用性健康分指标名称和指标值。             |
| performance_indicator_detail | array[json]  | 最新有效时刻的性能健康分指标名称和指标值。              |

### 响应示例

```json
{
  "overall_score": ["0.90", "0.90", "0.90", "0.90", "0.90", "0.89", "0.90", "0.90", "0.90", "0.90", "0.90"],
  "available_health_score": ["0.99", "1", "1", "1", "1", "0.96", "1", "1", "1", "1", "0.99"],
  "performance_health_score": [ "1", "0.99", "0.99", "0.99", "0.99", "1", "0.99", "0.99", "0.99", "0.99", "1"],
  "component_health_score": [ "0.52", "0.52", "0.52", "0.52", "0.52", "0.52", "0.52", "0.52", "0.52", "0.52", "0.52"],
  "healthWeight": "0.4,0.4,0.2",
  "healthThresholds": "0.9,0.7,0.5",
  "available_indicator_detail": [
    {"Api接入请求成功率（AccessOut）": 1.00},
    {"定时任务处理成功率": 0.99},
    {"Api接入请求成功率（AccessIn）": 1.00}
  ],
  "performance_indicator_detail": [
    {"定时任务处理平均时长": 1}
  ],
  "startTime": "2026-03-06 09:36:48",
  "endTime": "2026-03-06 09:46:48",
  "envCode": "example.code"
}
```