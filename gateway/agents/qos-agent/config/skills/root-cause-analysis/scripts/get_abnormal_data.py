import requests
import json
import argparse
import sys
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader


class GetAbnormalData:
    def __init__(self, start_time, end_time):
        self.loader = ConfigLoader()
        self.start_time = start_time
        self.end_time = end_time
        self.url = "/itom/machine/qos/getDiagnoseAbnormalData"


    def post_diagnose_abnormal_data(self):
        """
        调用 /itom/machine/qos/getDiagnoseAbnormalData POST 接口
        """
        try:
            username, password = self.loader.read_auth_from_config()
            env_code, mode = self.loader.read_monitor_info()
            base_url = self.loader.read_api_info()
            url = f"{base_url.rstrip('/')}{self.url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "startTime": self.start_time,
                "endTime": self.end_time
            }

            response = requests.post(url, json=payload, headers=headers, auth=HTTPBasicAuth(username, password),
                                     verify=False)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"请求异常: {e}", file=sys.stderr)
            return None
        except json.JSONDecodeError as e:
            print(f"JSON 解析失败: {e}", file=sys.stderr)
            return None


def main():
    parser = argparse.ArgumentParser(description="调用诊断健康评分接口，用户名密码从配置文件读取")
    parser.add_argument("--start_time", required=True, type=int, help="开始时间戳 (整数)")
    parser.add_argument("--end_time", required=True, type=int, help="结束时间戳 (整数)")

    args = parser.parse_args()
    processor = GetAbnormalData(args.start_time, args.end_time)
    result = processor.post_diagnose_abnormal_data()

    if result is not None:
        print("接口调用成功，响应：")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return result
    else:
        print("接口调用失败", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
