# 故障根因分析API 接口规范

## API 基础配置

### Base URL

```
ip:port
```

### 认证方式

使用 Basic Authentication，需要在[config.ini](../config/config.ini)中配置用户名和密码。

---

## 查询告警接口

### 请求信息

- **URL**: `/itom/machine/qos/getDiagnoseAbnormalData`
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
  "startTime": 1773135049000,
  "endTime": 1773135590000
}
```
### 响应参数

### 响应示例
```json
{
    "available_abnormal_data": [
        {
            "clusterName": "Batch Executor Cluster",
            "dn": "10cf2b16-f2d1-46c8-91c7-645dd85cf15e_294_BatchExecutor",
            "neName": null,
            "alarmId": "A_5",
            "firstOccurTime": "2026-03-10 09:31:00 Z",
            "clearStatus": "Not cleaned",
            "severity": "次要",
            "alarmName": "定时任务处理成功率",
            "occurTime": "2026-03-10 09:33:00 Z",
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
            "firstOccurTime": "2026-03-10 09:31:00 Z",
            "clearStatus": "Not cleaned",
            "severity": "重要",
            "alarmName": "定时任务处理平均时长",
            "occurTime": "2026-03-10 09:31:00 Z",
            "cause": "定时任务处理平均时长指标P值小于0.7",
            "ip": null,
            "times": 4,
            "moType": "BatchExecutorCluster",
            "moduleName": "IndicatorAlarm",
            "additionalInformation": null
        }
    ],
    "component_abnormal_data": [],
    "startTime": "2026-03-10 09:30:49 Z",
    "endTime": "2026-03-10 09:39:50 Z",
    "envCode": "example.code"
}
```

## 查询拓扑信息接口

### 请求信息

- **URL**: `/itom/machine/diagnosis/getTopology`
- **Method**: `POST`
- **Content-Type**: `application/json`

### 请求参数

| 参数名     | 类型     | 必填 | 说明     |
|---------|--------|----|--------|
| envCode | string | 是  | 监控环境编码 |

### 请求示例

```json
{
  "envCode": "example.code"
}
```

### 响应参数

| 参数名          | 类型                   | 必填 | 说明           |
|--------------|----------------------|----|--------------|
| clusterNodes | Set<ClusterInstance> | 是  | 拓扑图节点集合      |
| relations    | Set<Relation>        | 是  | 拓扑图节点之间的关系数据 |

- ClusterInstance

| 参数名          | 类型                | 必填 | 说明                                    |
|--------------|-------------------|----|---------------------------------------|
| clusterName	 | String	           | 是	 | 集群名称                                  |
| group	       | String	           | 是	 | 两种枚举值：External System、Business System |
| desc	        | 	String           | 否	 | 集群描述                                  |
| neInstances	 | 	List<NeInstance> | 是	 | DV网元实例集合                              |

- NeInstance

| 参数名   | 类型      | 必填 | 说明       |
|-------|---------|----|----------|
| dn	   | String	 | 是	 | DV网元dn属性 |
| name	 | String	 | 是	 | DV网元名称   |

- Relation

| 参数名             | 类型      | 必填 | 说明       |
|-----------------|---------|----|----------|
| desc	           | 	String | 否	 | 描述       |
| srcClusterName	 | 	String | 是	 | 连线起点节点名称 |
| dstClusterName	 | 	String | 是	 | 连线终点节点名称 |

### 响应示例

```json
{
  "clusterNodes": [
    {
      "clusterName": "LBNSLBSHOP",
      "group": "Business System",
      "desc": "节点对营业前台和管理前台页面暴露接口请求地址"
    },
    {
      "clusterName": "NSLBSHOP",
      "group": "Business System",
      "desc": "节点对外部系统暴露接口请求地址"
    }
  ],
  "relations": [
    {
      "srcClusterName": "LBNSLBSHOP",
      "dstClusterName": "NSLBSHOP",
      "desc": "营业前台和管理前台应用负载均衡节点"
    },
    {
      "srcClusterName": "RedisSentinel Cluster",
      "dstClusterName": "Redis Cluster",
      "desc": "Redis数据节点,数据缓存主节点。"
    }
  ]
}
```

## 查询子拓扑信息接口

### 请求信息

- **URL**: `/itom/machine/diagnosis/getSubTopology`
- **Method**: `POST`
- **Content-Type**: `application/json`

### 请求参数

| 参数名           | 类型     | 必填 | 说明              |
|---------------|--------|----|-----------------|
| envCode       | String | 是  | 监控环境编码          |
| rootAlarm     | Json   | 是  | 根因告警            |
| relatedAlarms | String | 否  | 衍生告警（JSON压缩转换后） |
| tenantId      | String | 否  | 租户ID            |

### 请求示例

```json
{
  "envCode": "example.code",
  "tenantId": "101",
  "relatedAlarms": "[{\"clusterName\":\"Batch Executor Cluster\",\"dn\":\"10cf2b16-f2d1-46c8-91c7-645dd85cf15e_294_BatchExecutor\",\"neName\":null,\"alarmId\":\"P_6\",\"firstOccurTime\":\"2026-03-10 09:31:00 Z\",\"clearStatus\":\"Not cleaned\",\"severity\":\"重要\",\"alarmName\":\"定时任务处理平均时长\",\"occurTime\":\"2026-03-10 09:31:00 Z\",\"cause\":\"定时任务处理平均时长指标P值小于0.7\",\"ip\":null,\"times\":4,\"moType\":\"BatchExecutorCluster\",\"moduleName\":\"IndicatorAlarm\"}]",
  "rootAlarm": {
    "clusterName": "LBNSLBIF",
    "dn": "10cf2b16-f2d1-46c8-91c7-645dd85cf15e_406_LBNSLBSHOP",
    "neName": null,
    "alarmId": "P_2",
    "firstOccurTime": "2026-03-10 09:32:00 Z",
    "clearStatus": "Not cleaned",
    "severity": "次要",
    "alarmName": "Web处理平均时延",
    "occurTime": "2026-03-10 09:34:00 Z",
    "cause": "Web处理平均时延指标P值小于0.9",
    "ip": null,
    "times": 3,
    "moType": "com.huawei.itpaas.platformservice.nslb",
    "moduleName": "IndicatorAlarm"
  }
}
```

### 响应参数
| 参数名                 | 类型                  | 必填 | 说明     |
|---------------------|---------------------|----|--------|
| getTopologyResponse | getTopologyResponse | 是  | 拓扑子图信息 |
| derivedAlarms       | Set<Alarm>          | 否  | 衍生告警   |

- getTopologyResponse

| 参数名          | 类型                   | 必填 | 说明           |
|--------------|----------------------|----|--------------|
| clusterNodes | Set<ClusterInstance> | 是  | 拓扑图节点集合      |
| relations    | Set<Relation>        | 是  | 拓扑图节点之间的关系数据 |

- ClusterInstance

| 参数名          | 类型                | 必填 | 说明                                    |
|--------------|-------------------|----|---------------------------------------|
| clusterName	 | String	           | 是	 | 集群名称                                  |
| group	       | String	           | 是	 | 两种枚举值：External System、Business System |
| desc	        | 	String           | 否	 | 集群描述                                  |
| neInstances	 | 	List<NeInstance> | 是	 | DV网元实例集合                              |

- NeInstance

| 参数名   | 类型      | 必填 | 说明       |
|-------|---------|----|----------|
| dn	   | String	 | 是	 | DV网元dn属性 |
| name	 | String	 | 是	 | DV网元名称   |

- Relation

| 参数名             | 类型      | 必填 | 说明       |
|-----------------|---------|----|----------|
| desc	           | 	String | 否	 | 描述       |
| srcClusterName	 | 	String | 是	 | 连线起点节点名称 |
| dstClusterName	 | 	String | 是	 | 连线终点节点名称 |

- Alarm

| 参数名                   | 类型      | 必填 | 说明                                                        |
|-----------------------|---------|----|-----------------------------------------------------------|
| dn	                   | String  | 否  | 资源标识，即网元dn属性                                              |
| neName	               | String  | 是  | 告警源，即网元名称                                                 |
| alarmId	              | String  | 是	 | 告警ID                                                      |
| firstOccurTime	       | String  | 是  | 首次发生时间                                                    |
| clearStatus	          | String  | 是	 | 清除状态                                                      |
| severity	             | String  | 是  | 级别                                                        |
| alarmName             | String  | 是  | 名称                                                        |
| occurTime	            | String  | 是  | 发生时间                                                      |
| cause	                | String  | 是  | 定位信息                                                      |
| ip	                   | String  | 是  | IP地址                                                      |
| times                 | Integer | 是	 | 告警次数                                                      |
| moType                | String  | 是  | 告警源类型，看看DV接口返回情况，优先使用moType，如果没有moType，那就存放moDisplayType。 |
| moduleName            | String  | 是  | 模块名，性能告警时此字段为Performance                                  |
| additionalInformation | String  | 是  | 附加信息                                                      |

### 响应示例

```json
{
  "getTopologyResponse": {
    "clusterNodes": [
      {
        "clusterName": "A",
        "group": "Business System",
        "desc": "节点对外部系统暴露接口请求地址"
      },
      {
        "clusterName": "B",
        "group": "External System",
        "desc": "自渠业务;数据业务;短消息业务"
      }
    ],
    "relations": [
      {
        "srcClusterName": "A",
        "dstClusterName": "B",
        "desc": "节点对外部系统暴露接口请求地址"
      }
    ]
  },
  "derivedAlarms": []
}
```