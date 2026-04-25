import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


CURRENT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = CURRENT_DIR.parent
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

import server  # noqa: E402


class RuntimeConfigTests(unittest.TestCase):
    def test_prefers_qos_password_over_gateway_password(self) -> None:
        with patch.dict(
            os.environ,
            {
                "QOS_BASE_URL": "https://example.com",
                "QOS_USERNAME": "qos-user",
                "QOS_PASSWORD": "qos-pass",
                "GATEWAY_API_PASSWORD": "gateway-pass",
            },
            clear=False,
        ):
            config = server.RuntimeConfig.from_env()

        self.assertEqual(config.qos_password, "qos-pass")

    def test_falls_back_to_gateway_password(self) -> None:
        with patch.dict(
            os.environ,
            {
                "QOS_BASE_URL": "https://example.com",
                "QOS_USERNAME": "qos-user",
                "QOS_PASSWORD": "",
                "GATEWAY_API_PASSWORD": "gateway-pass",
            },
            clear=False,
        ):
            config = server.RuntimeConfig.from_env()

        self.assertEqual(config.qos_password, "gateway-pass")

    def test_tls_verification_is_disabled_by_default(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            config = server.RuntimeConfig.from_env()

        self.assertFalse(config.verify_tls)


class PayloadBuilderTests(unittest.TestCase):
    @staticmethod
    def _config() -> server.RuntimeConfig:
        return server.RuntimeConfig(
            qos_base_url="https://example.com",
            qos_username="qos-user",
            qos_password="qos-pass",
            verify_tls=False,
            timeout_seconds=30,
            time_parse_format=server.DEFAULT_TIME_PARSE_FORMAT,
        )

    def test_seconds_timestamp_is_normalized_to_milliseconds(self) -> None:
        with patch.object(server, "current_time_ms", return_value=1_700_000_700_000):
            payload = server.build_abnormal_data_payload(
                {
                    "envCode": "VRBTL2.TEST",
                    "startTime": 1_700_000_000,
                    "endTime": 1_700_000_600,
                },
                self._config(),
            )

        self.assertEqual(payload["startTime"], 1_700_000_000_000)
        self.assertEqual(payload["endTime"], 1_700_000_600_000)

    def test_defaults_to_recent_30_minutes_when_time_range_is_omitted(self) -> None:
        now_ms = 1_700_000_000_000
        with patch.object(server, "current_time_ms", return_value=now_ms):
            payload = server.build_health_score_payload(
                {"envCode": "VRBTL2.TEST"},
                self._config(),
            )

        self.assertEqual(payload["endTime"], now_ms)
        self.assertEqual(payload["startTime"], now_ms - 30 * 60 * 1000)

    def test_defaults_end_time_to_now_when_only_start_time_is_provided(self) -> None:
        now_ms = 1_700_000_000_000
        start_ms = now_ms - 10 * 60 * 1000
        with patch.object(server, "current_time_ms", return_value=now_ms):
            payload = server.build_abnormal_data_payload(
                {
                    "envCode": "VRBTL2.TEST",
                    "startTime": start_ms,
                },
                self._config(),
            )

        self.assertEqual(payload["startTime"], start_ms)
        self.assertEqual(payload["endTime"], now_ms)

    def test_defaults_start_time_from_end_time_when_only_end_time_is_provided(self) -> None:
        end_ms = 1_700_000_000_000
        with patch.object(server, "current_time_ms", return_value=end_ms + 5 * 60 * 1000):
            payload = server.build_health_score_payload(
                {
                    "envCode": "VRBTL2.TEST",
                    "endTime": end_ms,
                },
                self._config(),
            )

        self.assertEqual(payload["endTime"], end_ms)
        self.assertEqual(payload["startTime"], end_ms - 30 * 60 * 1000)

    def test_rejects_start_time_earlier_than_48_hours(self) -> None:
        now_ms = 1_700_000_000_000
        too_old_start_ms = now_ms - (48 * 60 * 60 * 1000) - 1
        with patch.object(server, "current_time_ms", return_value=now_ms):
            with self.assertRaisesRegex(server.ToolExecutionError, "48 hours ago"):
                server.build_health_score_payload(
                    {
                        "envCode": "VRBTL2.TEST",
                        "startTime": too_old_start_ms,
                        "endTime": too_old_start_ms + 10 * 60 * 1000,
                    },
                    self._config(),
                )

    def test_rejects_time_range_longer_than_60_minutes(self) -> None:
        now_ms = 1_700_000_000_000
        start_ms = now_ms - 30 * 60 * 1000
        with patch.object(server, "current_time_ms", return_value=now_ms):
            with self.assertRaisesRegex(server.ToolExecutionError, "must not exceed 60 minutes"):
                server.build_abnormal_data_payload(
                    {
                        "envCode": "VRBTL2.TEST",
                        "startTime": start_ms,
                        "endTime": start_ms + 60 * 60 * 1000 + 1,
                    },
                    self._config(),
                )

    def test_allows_equal_start_and_end_time(self) -> None:
        now_ms = 1_700_000_000_000
        start_ms = now_ms - 5 * 60 * 1000
        with patch.object(server, "current_time_ms", return_value=now_ms):
            payload = server.build_health_score_payload(
                {
                    "envCode": "VRBTL2.TEST",
                    "startTime": start_ms,
                    "endTime": start_ms,
                },
                self._config(),
            )

        self.assertEqual(payload["startTime"], start_ms)
        self.assertEqual(payload["endTime"], start_ms)


if __name__ == "__main__":
    unittest.main()
