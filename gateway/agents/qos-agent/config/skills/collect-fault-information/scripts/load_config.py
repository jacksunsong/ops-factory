import configparser
import os


class ConfigLoader:
    ENV_VAR = 'GATEWAY_API_PASSWORD'

    def __init__(self):
        self.config = None
        self.load_config()

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


    def read_download_url(self):
        if not self.config.has_section('McpServer'):
            raise KeyError("配置文件中缺少 [McpServer] 节")

        download_url = self.config.get('McpServer', 'downloadUrl', fallback=None)

        if not download_url:
            raise ValueError("配置文件中缺少 downloadUrl")

        return download_url


    def load_config(self):
        config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config", "config.ini")
        if not os.path.exists(config_path):
            raise FileNotFoundError(f"配置文件不存在: {config_path}")
        self.config = configparser.ConfigParser()
        try:
            self.config.read(config_path, encoding='utf-8')
        except Exception as e:
            raise RuntimeError(f"读取配置文件失败: {e}")