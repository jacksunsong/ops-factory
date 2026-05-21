#!/usr/bin/env python3
"""Tests for bi-data-service MCP server.

Validates the server's input validation, JSON-RPC protocol handling,
and tool parameter checking. Does not require the BI backend to be running.
"""
import json
import sys
import os
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from server import (
    RuntimeConfig, ToolExecutionError,
    VALID_DOMAINS, VALID_METRICS_DOMAINS, VALID_METRICS, VALID_INTERVALS,
    handle_request, format_tool_result,
    _handle_query_tickets, _handle_compute_metric, _handle_trace_ticket_lineage,
    _handle_get_all_metrics, _handle_analyze_sla_rate, _handle_analyze_request_sla_rate,
)


def _config() -> RuntimeConfig:
    return RuntimeConfig(
        bi_service_url="http://127.0.0.1:1",
        timeout_seconds=5,
    )


# ── Constants ──────────────────────────────────────────────────────────

class TestConstants(unittest.TestCase):
    def test_valid_domains(self):
        self.assertEqual(set(VALID_DOMAINS), {"incidents", "changes", "requests", "problems"})

    def test_valid_metrics_domains(self):
        for d in ["executive", "sla", "incidents", "changes", "requests", "problems", "cross-process", "workforce"]:
            self.assertIn(d, VALID_METRICS_DOMAINS)

    def test_valid_metrics(self):
        self.assertEqual(VALID_METRICS, {"count", "avg", "sum", "percentage", "distribution"})

    def test_valid_intervals(self):
        self.assertEqual(VALID_INTERVALS, {"hour", "day", "week", "month"})


# ── Tool parameter validation ──────────────────────────────────────────

class TestToolValidation(unittest.TestCase):
    def test_query_tickets_rejects_invalid_source(self):
        with self.assertRaises(ToolExecutionError) as ctx:
            _handle_query_tickets({"source": "invalid_domain"}, _config())
        self.assertIn("Invalid source", str(ctx.exception))

    def test_compute_metric_rejects_invalid_source(self):
        with self.assertRaises(ToolExecutionError) as ctx:
            _handle_compute_metric({"source": "bogus", "metric": "count"}, _config())
        self.assertIn("Invalid source", str(ctx.exception))

    def test_compute_metric_rejects_invalid_metric(self):
        with self.assertRaises(ToolExecutionError) as ctx:
            _handle_compute_metric({"source": "incidents", "metric": "median"}, _config())
        self.assertIn("Invalid metric", str(ctx.exception))

    def test_trace_lineage_rejects_invalid_domain(self):
        with self.assertRaises(ToolExecutionError) as ctx:
            _handle_trace_ticket_lineage({"source_domain": "nope", "ticket_id": "T-001"}, _config())
        self.assertIn("Invalid source_domain", str(ctx.exception))

    def test_analyze_sla_rate_rejects_invalid_interval(self):
        with self.assertRaises(ToolExecutionError) as ctx:
            _handle_analyze_sla_rate({"interval": "decade"}, _config())
        self.assertIn("Invalid interval", str(ctx.exception))

    def test_analyze_request_sla_rate_rejects_invalid_interval(self):
        with self.assertRaises(ToolExecutionError) as ctx:
            _handle_analyze_request_sla_rate({"interval": "quarter"}, _config())
        self.assertIn("Invalid interval", str(ctx.exception))


# ── JSON-RPC protocol ──────────────────────────────────────────────────

class TestJsonRpc(unittest.TestCase):
    def test_initialize(self):
        resp = handle_request({
            "jsonrpc": "2.0", "id": 1, "method": "initialize",
            "params": {"capabilities": {}, "clientInfo": {"name": "test"}}
        }, _config())
        self.assertEqual(resp["id"], 1)
        self.assertIn("result", resp)
        self.assertIn("serverInfo", resp["result"])

    def test_ping(self):
        resp = handle_request({
            "jsonrpc": "2.0", "id": 2, "method": "notifications/initialized"
        }, _config())
        # initialized notification returns None (no response)
        self.assertIsNone(resp)

    def test_tools_list(self):
        resp = handle_request({
            "jsonrpc": "2.0", "id": 3, "method": "tools/list"
        }, _config())
        tools = resp["result"]["tools"]
        tool_names = {t["name"] for t in tools}
        expected = {
            "get_all_metrics", "query_tickets", "compute_metric", "trace_ticket_lineage",
            "analyze_sla_rate", "analyze_request_sla_rate",
            "analyze_incident_volume", "analyze_mttr",
            "analyze_change_success_rate", "analyze_request_performance",
            "analyze_problem_metrics", "analyze_workforce_performance",
        }
        self.assertEqual(tool_names, expected)

    def test_unknown_method(self):
        resp = handle_request({
            "jsonrpc": "2.0", "id": 4, "method": "nonexistent/method"
        }, _config())
        self.assertIn("error", resp)
        self.assertEqual(resp["error"]["code"], -32601)

    def test_unknown_tool(self):
        resp = handle_request({
            "jsonrpc": "2.0", "id": 5, "method": "tools/call",
            "params": {"name": "bogus_tool", "arguments": {}}
        }, _config())
        self.assertIn("error", resp)
        self.assertEqual(resp["error"]["code"], -32601)


# ── format_tool_result ─────────────────────────────────────────────────

class TestFormatToolResult(unittest.TestCase):
    def test_string_result(self):
        result = format_tool_result("hello")
        self.assertEqual(len(result["content"]), 1)
        self.assertEqual(result["content"][0]["type"], "text")
        self.assertIn("hello", result["content"][0]["text"])

    def test_dict_result(self):
        result = format_tool_result({"key": "value"})
        parsed = json.loads(result["content"][0]["text"])
        self.assertEqual(parsed["key"], "value")

    def test_error_result(self):
        result = format_tool_result("something broke", is_error=True)
        self.assertTrue(result["isError"])

    def test_truncation(self):
        long_text = "x" * 25_000
        result = format_tool_result(long_text)
        text = result["content"][0]["text"]
        self.assertLess(len(text), 25_000)
        self.assertIn("truncated", text.lower())


if __name__ == "__main__":
    unittest.main()
