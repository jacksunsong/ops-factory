# 故障信息收集 API 接口规范

## API 基础配置

### Base URL

```
ip:port
```

### 认证方式

使用 Basic Authentication，需要在[config.ini](../config/config.ini)中配置用户名和密码。

---

## 获取故障日/告警接口

### 请求信息

- **URL**: `/itom/machine/qos/collectFaultInfoStart`
- **Method**: `POST`
- **Content-Type**: `application/json`

### 请求参数

| 参数名         | 类型     | 必填 | 说明                            |
|-------------|--------|----|-------------------------------|
| startTime   | string | 是  | 开始时间，格式：`yyyy-MM-dd HH:mm:ss` |
| endTime     | string | 是  | 结束时间，格式：`yyyy-MM-dd HH:mm:ss` |
| envCode     | string | 是  | 监控环境编码                        |
| clusterName | string | 是  | 集群名称                          |

### 请求示例

```json
{
    "envCode": "example.code",
    "startTime": "2026-03-11 02:00:00",
    "endTime": "2026-03-11 02:10:00",
    "clusterName": "example.name"
}
```

### 响应参数

下载故障告警信息的ID

### 响应示例

```
4a53f6c7-0341-412d-9299-26ec742b9ee5
```