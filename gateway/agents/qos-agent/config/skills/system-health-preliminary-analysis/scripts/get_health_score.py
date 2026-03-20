import requests
import json
import argparse
import sys
import base64
import configparser
import os
from requests.auth import HTTPBasicAuth


class GetDiagnoseHealthScore:
    ENV_VAR = 'GATEWAY_API_PASSWORD'

    def __init__(self, start_time, end_time):
        self.config = None
        self.load_config()
        self.start_time = start_time
        self.end_time = end_time


    def post_diagnose_health_score(self):
        """
        调用 /itom/machine/qos/getDiagnoseHealthScore POST 接口
        """
        try:
            username, password = self.read_auth_from_config()
            env_code, mode = self.read_monitor_info()
            base_url = self.read_api_info()
            url = f"{base_url.rstrip('/')}/itom/machine/qos/getDiagnoseHealthScore"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "startTime": self.start_time,
                "endTime": self.end_time,
                "mode": mode
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


    def read_auth_from_config(self):
        """
        从配置文件读取用户名密码
        配置文件格式示例 (config.ini):
        [Auth]
        username = your_username
        password = your_password

        返回 (username, password) 元组，如果缺少则抛出异常
        """

        if not self.config.has_section('Auth'):
            raise KeyError("配置文件中缺少 [Auth] 节")

        username = self.config.get('Auth', 'username', fallback=None)
        password = os.getenv(ENV_VAR)

        if not username or not password:
            raise ValueError("配置文件中缺少 username 或 password 字段")

        return username, password


    def read_api_info(self):
        if not self.config.has_section('McpServer'):
            raise KeyError("配置文件中缺少 [McpServer] 节")

        base_url = self.config.get('McpServer', 'baseUrl', fallback=None)

        if not base_url:
            raise ValueError("配置文件中缺少 baseUrl")

        return base_url

    def read_monitor_info(self):
        if not self.config.has_section('Monitor'):
            raise KeyError("配置文件中缺少 [Monitor] 节")

        env_code = self.config.get('Monitor', 'envCode', fallback=None)
        mode = self.config.get('Monitor', 'mode', fallback=None)

        if not env_code or not mode:
            raise ValueError("配置文件中缺少 env_code 或 mode")

        return env_code, mode


    def load_config(self):
        config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config", "config.ini")
        if not os.path.exists(config_path):
            raise FileNotFoundError(f"配置文件不存在: {config_path}")
        self.config = configparser.ConfigParser()
        try:
            self.config.read(config_path, encoding='utf-8')
        except Exception as e:
            raise RuntimeError(f"读取配置文件失败: {e}")


def main():
    parser = argparse.ArgumentParser(description="调用诊断健康评分接口，用户名密码从配置文件读取")
    parser.add_argument("--start_time", required=True, type=int, help="开始时间戳 (整数)")
    parser.add_argument("--end_time", required=True, type=int, help="结束时间戳 (整数)")

    args = parser.parse_args()
    processor = GetDiagnoseHealthScore(args.start_time, args.end_time)
    result = processor.post_diagnose_health_score()

    if result is not None:
        print("接口调用成功，响应：")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return result
    else:
        print("接口调用失败", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
