import requests
import json
import sys
import argparse
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader


class GetSubTopography:
    def __init__(self, root_alarm, related_alarms=None):
        self.loader = ConfigLoader()
        self.url = "/itom/machine/diagnosis/getSubTopology"
        self.root_alarm = root_alarm
        self.related_alarms = related_alarms


    def post_sub_topography(self):
        """
        调用 /itom/machine/diagnosis/getSubTopology POST 接口
        """
        try:
            username, password = self.loader.read_auth_from_config()
            env_code, mode = self.loader.read_monitor_info()
            base_url = self.loader.read_api_info()
            url = f"{base_url.rstrip('/')}{self.url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "rootAlarm": self.root_alarm
            }

            if self.related_alarms:
                payload["relatedAlarms"] = self.related_alarms

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
    parser = argparse.ArgumentParser(description="调用子拓扑查询接口")
    parser.add_argument("--root_alarm", required=True, type=str, help="根因告警JSON字符串")
    parser.add_argument("--related_alarms", required=False, type=str,
                        help="相关告警JSON数组字符串")

    args = parser.parse_args()

    # 解析 JSON
    try:
        root_alarm = json.loads(args.root_alarm)
    except json.JSONDecodeError as e:
        print(f"ERROR: root_alarm JSON 解析失败: {e}", file=sys.stderr)
        print(f"ERROR: 实际接收到的值: {repr(args.root_alarm)}", file=sys.stderr)
        sys.exit(1)

    related_alarms = None
    if args.related_alarms:
        related_alarms = args.related_alarms

    processor = GetSubTopography(root_alarm, related_alarms)
    result = processor.post_sub_topography()

    if result is not None:
        print("接口调用成功，响应：")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return result
    else:
        print("接口调用失败", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
