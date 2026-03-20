import requests
import json
import sys
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader


class GetTopography:
    def __init__(self):
        self.loader = ConfigLoader()
        self.url = "/itom/machine/diagnosis/getTopology"


    def post_topography(self):
        """
        调用 /itom/machine/diagnosis/getTopology POST 接口
        """
        try:
            username, password = self.loader.read_auth_from_config()
            env_code, mode = self.loader.read_monitor_info()
            base_url = self.loader.read_api_info()
            url = f"{base_url.rstrip('/')}{self.url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
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
    processor = GetTopography()
    result = processor.post_topography()

    if result is not None:
        print("接口调用成功，响应：")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return result
    else:
        print("接口调用失败", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
