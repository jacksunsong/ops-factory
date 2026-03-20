import argparse

import requests
import json
import sys
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader


class CollectFaultInfo:
    def __init__(self, start_time, end_time, cluster_name, info_type):
        self.loader = ConfigLoader()
        self.alarm_url = "/itom/machine/qos/collectFaultInfoStart"
        self.log_url = "/itom/machine/diagnosis/getLogsFileId"
        self.cluster_name = cluster_name
        self.start_time = start_time
        self.end_time = end_time
        self.info_type = info_type


    def collect_fault_info(self):
        """
        调用 /itom/machine/qos/collectFaultInfoStart
        POST 接口
        """
        try:
            username, password = self.loader.read_auth_from_config()
            env_code, mode = self.loader.read_monitor_info()
            base_url = self.loader.read_api_info()
            if self.info_type == "alarm":
                url = f"{base_url.rstrip('/')}{self.alarm_url}"
            else:
                url = f"{base_url.rstrip('/')}{self.log_url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "startTime": self.start_time,
                "endTime": self.end_time,
                "clusterName": self.cluster_name
            }

            response = requests.post(url, json=payload, headers=headers, auth=HTTPBasicAuth(username, password),
                                     verify=False)
            response.raise_for_status()
            return response.text
        except requests.exceptions.RequestException as e:
            print(f"请求异常: {e}", file=sys.stderr)
            return None
        except json.JSONDecodeError as e:
            print(f"JSON 解析失败: {e}", file=sys.stderr)
            return None


def main():
    parser = argparse.ArgumentParser(description="调用诊断健康评分接口，用户名密码从配置文件读取")
    parser.add_argument("--start_time", required=True, type=str, help="开始时间")
    parser.add_argument("--end_time", required=True, type=str, help="结束时间")
    parser.add_argument("--cluster_name", required=True, type=str, help="集群名称")
    parser.add_argument("--info_type", required=False, type=str, help="信息类型，alarm or log", default="log")

    args = parser.parse_args()

    processor = CollectFaultInfo(args.start_time, args.end_time, args.cluster_name, args.info_type)
    result = processor.collect_fault_info()

    if result is not None:
        print("接口调用成功，响应：")
        print(result)
        return result
    else:
        print("接口调用失败", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
