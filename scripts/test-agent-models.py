#!/usr/bin/env python3
"""
Agent 模型测试脚本

测试 qos-agent / qa-agent 对接大模型的效果与性能，
支持多轮测试，收集响应内容和性能指标，生成 Markdown 报告。

用法:
    # 使用默认配置（test-agent-models.json）
    python3 scripts/test-agent-models.py

    # 跑 3 轮
    python3 scripts/test-agent-models.py --rounds 3

    # 指定配置文件
    python3 scripts/test-agent-models.py -c my-config.json

    # 命令行覆盖参数
    python3 scripts/test-agent-models.py --base-url http://1.2.3.4:3000 --rounds 2

    # 指定输出目录
    python3 scripts/test-agent-models.py -o results/run1 --rounds 5

依赖:
    pip3 install requests
"""

import argparse
import json
import os
import sys
import time
import datetime
import threading
import uuid
from pathlib import Path

try:
    import requests
except ImportError:
    print("错误: 需要安装 requests 库")
    print("  pip3 install requests")
    sys.exit(1)


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CONFIG_PATH = os.path.join(SCRIPT_DIR, "test-agent-models.json")


def load_config(config_path):
    if config_path and os.path.exists(config_path):
        with open(config_path, "r", encoding="utf-8") as f:
            return json.load(f)
    if os.path.exists(DEFAULT_CONFIG_PATH):
        with open(DEFAULT_CONFIG_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    print(f"错误: 找不到配置文件 {config_path or DEFAULT_CONFIG_PATH}")
    sys.exit(1)


# ----------------------------------------------------------------
# Gateway API Client
# ----------------------------------------------------------------

class GatewayClient:
    """Gateway API 客户端"""

    def __init__(self, base_url, secret_key, user_id, timeout=600):
        self.base_url = base_url.rstrip("/")
        self.secret_key = secret_key
        self.user_id = user_id
        self.timeout = timeout

    def _headers(self, sse=False):
        h = {
            "Content-Type": "application/json",
            "x-secret-key": self.secret_key,
            "x-user-id": self.user_id,
        }
        if sse:
            h["Accept"] = "text/event-stream"
        return h

    def health_check(self):
        url = f"{self.base_url}/gateway/status"
        try:
            resp = requests.get(url, headers=self._headers(), timeout=10)
            return resp.status_code == 200, resp.status_code
        except requests.exceptions.ConnectionError:
            return False, "Connection refused"
        except requests.exceptions.Timeout:
            return False, "Timeout"
        except Exception as e:
            return False, str(e)

    def list_agents(self):
        url = f"{self.base_url}/gateway/agents"
        resp = requests.get(url, headers=self._headers(), timeout=10)
        resp.raise_for_status()
        return resp.json()

    def start_session(self, agent_id):
        url = f"{self.base_url}/gateway/agents/{agent_id}/agent/start"
        resp = requests.post(url, headers=self._headers(), json={}, timeout=120)
        resp.raise_for_status()
        return resp.json()

    def stop_session(self, _agent_id, _session_id):
        # 旧版 stop 端点已移除，当前会话生命周期由 gateway/goosed 自行管理。
        return

    def send_reply(self, agent_id, session_id, message):
        """按当前 gateway 协议执行 reply: 先订阅 events，再提交消息。"""
        events_url = f"{self.base_url}/gateway/agents/{agent_id}/sessions/{session_id}/events"
        reply_url = f"{self.base_url}/gateway/agents/{agent_id}/sessions/{session_id}/reply"
        request_id = str(uuid.uuid4())
        body = {
            "request_id": request_id,
            "user_message": {
                "role": "user",
                "created": int(time.time()),
                "content": [{"type": "text", "text": message}],
                "metadata": {"userVisible": True, "agentVisible": True},
            },
        }

        start_time = time.time()
        first_token_time = None
        first_text_time = None
        full_text = ""
        thinking_text = ""
        token_state = {}
        error_msg = None
        chunk_count = 0
        event_counts = {}
        tool_calls = []
        submit_error = None
        submit_response = None
        events_response = None

        def _submit_reply():
            nonlocal submit_error, submit_response, events_response
            try:
                resp = requests.post(
                    reply_url,
                    headers=self._headers(),
                    json=body,
                    timeout=self.timeout,
                )
                submit_response = resp
                resp.raise_for_status()
            except Exception as e:
                submit_error = e
                if events_response is not None:
                    try:
                        events_response.close()
                    except Exception:
                        pass

        try:
            events_response = requests.get(
                events_url,
                headers=self._headers(sse=True),
                stream=True,
                timeout=(10, self.timeout),
            )
            events_response.raise_for_status()
            events_response.encoding = "utf-8"

            submit_thread = threading.Thread(target=_submit_reply, daemon=True)
            submit_thread.start()

            for raw_line in events_response.iter_lines(decode_unicode=True):
                if raw_line is None:
                    continue
                elapsed = time.time() - start_time

                if not raw_line.startswith("data:"):
                    continue
                data_str = raw_line[5:].strip()
                if not data_str:
                    continue

                try:
                    event = json.loads(data_str)
                except json.JSONDecodeError:
                    continue

                event_request_id = event.get("chat_request_id") or event.get("request_id")
                if event_request_id and event_request_id != request_id:
                    continue

                event_type = event.get("type", "")
                if event_type in ("ActiveRequests", "Ping"):
                    continue
                event_counts[event_type] = event_counts.get(event_type, 0) + 1

                if event_type == "Message":
                    chunk_count += 1
                    if first_token_time is None:
                        first_token_time = elapsed

                    msg = event.get("message", {})
                    contents = msg.get("content", [])
                    has_text = False
                    for c in contents:
                        ctype = c.get("type", "")
                        if ctype == "text":
                            full_text += c.get("text", "")
                            has_text = True
                        elif ctype == "thinking":
                            thinking_text += c.get("thinking", "")
                            if first_token_time is None:
                                first_token_time = elapsed
                        elif ctype == "toolRequest":
                            tool_calls.append({
                                "name": c.get("name", ""),
                                "elapsed_sec": round(elapsed, 2),
                            })

                    if has_text and first_text_time is None:
                        first_text_time = elapsed

                    ts = event.get("token_state")
                    if ts:
                        token_state = ts

                elif event_type == "Finish":
                    ts = event.get("token_state")
                    if ts:
                        token_state = ts
                    break

                elif event_type == "Error":
                    error_msg = event.get("error", "Unknown error")
                    break

            submit_thread.join(timeout=2)
            if submit_error is not None:
                if isinstance(submit_error, requests.exceptions.HTTPError) and submit_response is not None:
                    submit_text = submit_response.text.strip()
                    error_msg = f"提交失败 ({submit_response.status_code}): {submit_text or submit_response.reason}"
                else:
                    error_msg = f"提交失败: {submit_error}"

        except requests.exceptions.Timeout:
            error_msg = f"请求超时 ({self.timeout}s)"
        except requests.exceptions.ConnectionError as e:
            error_msg = f"连接错误: {e}"
        except Exception as e:
            error_msg = f"异常: {e}"
        finally:
            if events_response is not None:
                try:
                    events_response.close()
                except Exception:
                    pass

        total_time = time.time() - start_time
        output_tokens = token_state.get("outputTokens", 0)

        metrics = {
            "total_time_sec": round(total_time, 2),
            "ttft_sec": round(first_token_time, 2) if first_token_time else None,
            "first_text_sec": round(first_text_time, 2) if first_text_time else None,
            "chunk_count": chunk_count,
            "input_tokens": token_state.get("inputTokens", 0),
            "output_tokens": output_tokens,
            "total_tokens": token_state.get("totalTokens", 0),
            "tokens_per_sec": (
                round(output_tokens / total_time, 2)
                if total_time > 0 and output_tokens else None
            ),
            "tool_calls": tool_calls,
            "tool_call_count": len(tool_calls),
            "event_counts": event_counts,
            "thinking_text": thinking_text,
            "error": error_msg,
        }

        return full_text, metrics


# ----------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------

def _make_result(agent_id, round_num, question_num, question, response, metrics):
    return {
        "agent": agent_id,
        "round": round_num,
        "question_num": question_num,
        "question": question,
        "response": response,
        "error": metrics.get("error"),
        "metrics": {k: v for k, v in metrics.items() if k != "error"},
    }


def _save_individual_response(output_dir, agent_id, round_num, q_num, timestamp,
                              question, response, metrics):
    fname = output_dir / f"{agent_id}_r{round_num}_q{q_num}_{timestamp}.md"
    lines = [
        f"# {agent_id} - 第{round_num}轮 问题 {q_num}\n",
        f"## 问题\n\n{question}\n",
        f"## 回答\n\n{response}\n",
    ]
    if metrics.get("error"):
        lines.append(f"## 错误\n\n{metrics['error']}\n")
    else:
        lines.append("## 性能指标\n")
        lines.append(f"- 总耗时: {metrics.get('total_time_sec', 'N/A')}s")
        lines.append(f"- 首 Token 时间: {metrics.get('ttft_sec') or 'N/A'}s")
        lines.append(f"- 输出 Tokens: {metrics.get('output_tokens', 'N/A')}")
        lines.append(f"- 吞吐量: {metrics.get('tokens_per_sec') or 'N/A'} tokens/s")
        lines.append(f"- 工具调用: {metrics.get('tool_call_count', 0)} 次")
        lines.append("")
    with open(fname, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


# ----------------------------------------------------------------
# Test runner
# ----------------------------------------------------------------

def run_test(config, output_dir):
    """执行完整测试流程"""
    client = GatewayClient(
        base_url=config["base_url"],
        secret_key=config["secret_key"],
        user_id=config.get("user_id", "admin"),
        timeout=config.get("timeout", 600),
    )

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    all_results = []
    rounds = config.get("rounds", 1)
    test_questions = config.get("test_questions", {})

    # ---- 健康检查 ----
    total_q = sum(len(qs) for qs in test_questions.values()) * rounds
    print("=" * 64)
    print("  Agent 模型测试")
    print("=" * 64)
    print(f"  网关地址: {config['base_url']}")
    print(f"  用户:     {config.get('user_id', 'admin')}")
    print(f"  测试轮数: {rounds}")
    print(f"  总测试量: {total_q} 次对话")
    print()

    print("[健康检查] ", end="", flush=True)
    ok, status = client.health_check()
    if not ok:
        print(f"失败 ({status})")
        print(f"  请确认网关地址 {config['base_url']} 是否可达")
        sys.exit(1)
    print("OK")

    try:
        agents_data = client.list_agents()
        agent_list = agents_data.get("agents", agents_data) if isinstance(agents_data, dict) else agents_data
        agent_ids = [a.get("id", "") for a in (agent_list if isinstance(agent_list, list) else [])]
        print(f"[可用 Agent] {', '.join(agent_ids)}")
    except Exception as e:
        print(f"[警告] 无法列出 agents: {e}")

    print()

    # ---- 多轮测试 ----
    for rnd in range(1, rounds + 1):
        if rounds > 1:
            print("=" * 64)
            print(f"  >>> 第 {rnd}/{rounds} 轮 <<<")
            print("=" * 64)

        for agent_id, questions in test_questions.items():
            print(f"\n  [{agent_id}] ({len(questions)} 个问题)")

            # 启动会话
            print(f"  [会话] 启动中...", end=" ", flush=True)
            try:
                session = client.start_session(agent_id)
                session_id = session.get("id")
                if not session_id:
                    print(f"失败 (响应无 id)")
                    for i, q in enumerate(questions, 1):
                        all_results.append(
                            _make_result(agent_id, rnd, i, q, "", {"error": "启动会话失败"})
                        )
                    continue
                print(f"OK  session={session_id[:16]}...")
            except Exception as e:
                print(f"失败 ({e})")
                for i, q in enumerate(questions, 1):
                    all_results.append(_make_result(agent_id, rnd, i, q, "", {"error": str(e)}))
                continue

            try:
                for i, question in enumerate(questions, 1):
                    q_label = question[:40] + ("..." if len(question) > 40 else "")
                    print(f"    [{i}/{len(questions)}] Q: {q_label}")
                    print(f"           发送中...", end="", flush=True)

                    response_text, metrics = client.send_reply(agent_id, session_id, question)

                    if metrics.get("error"):
                        status_icon = "X"
                        status_msg = f"错误: {metrics['error'][:60]}"
                    else:
                        status_icon = "OK"
                        status_msg = "完成"

                    print(
                        f"\r    [{i}/{len(questions)}] {status_icon} {status_msg}"
                        f"  | {metrics.get('total_time_sec', '?')}s"
                        f" | TTFT {metrics.get('ttft_sec') or '?'}s"
                        f" | {metrics.get('output_tokens', '?')} tokens"
                        f" | {metrics.get('tool_call_count', 0)} tools"
                    )

                    result = _make_result(agent_id, rnd, i, question, response_text, metrics)
                    all_results.append(result)

                    _save_individual_response(
                        output_dir, agent_id, rnd, i, timestamp,
                        question, response_text, metrics,
                    )

                    if i < len(questions):
                        time.sleep(2)

            finally:
                print(f"  [会话] 停止...", end=" ", flush=True)
                client.stop_session(agent_id, session_id)
                print("OK")

        if rnd < rounds:
            print(f"\n  轮间等待 3s...")
            time.sleep(3)

    # ---- 生成报告 ----
    report_path = generate_report(all_results, config, output_dir, timestamp)

    print()
    print("=" * 64)
    print(f"  测试完成! ({rounds} 轮 x {len(test_questions)} agent)")
    print(f"  报告: {report_path}")
    print("=" * 64)

    return all_results


# ----------------------------------------------------------------
# Report generator
# ----------------------------------------------------------------

def _stats(vals):
    if not vals:
        return ("-", "-", "-", "-")
    return (
        f"{min(vals):.2f}",
        f"{max(vals):.2f}",
        f"{sum(vals)/len(vals):.2f}",
        f"{sum(vals):.2f}",
    )


def _avg(vals):
    if not vals:
        return "-"
    return f"{sum(vals)/len(vals):.2f}"


def generate_report(results, config, output_dir, timestamp):
    """生成完整的 Markdown 测试报告"""
    report_file = output_dir / f"test_report_{timestamp}.md"
    rounds = config.get("rounds", 1)
    total = len(results)
    success_count = len([r for r in results if not r.get("error")])
    fail_count = total - success_count

    lines = []

    # ---- 报告头 ----
    lines.append("# Agent 模型测试报告\n")
    lines.append(f"> 自动生成于 {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

    lines.append("## 测试环境\n")
    lines.append("| 项目 | 值 |")
    lines.append("|------|-----|")
    lines.append(f"| 网关地址 | `{config['base_url']}` |")
    lines.append(f"| 测试用户 | `{config.get('user_id', 'admin')}` |")
    lines.append(f"| 测试时间 | {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')} |")
    lines.append(f"| 测试轮数 | {rounds} |")
    lines.append(f"| 总测试量 | {total} (成功 {success_count} / 失败 {fail_count}) |")
    lines.append("")

    # ---- 一、全量明细表 ----
    lines.append("## 一、全量性能明细\n")

    if rounds > 1:
        lines.append(
            "| Agent | 轮次 | # | 问题 | 状态 | 总耗时(s) | TTFT(s) | 首文本(s) | 输出Tokens | Tokens/s | 工具调用 |"
        )
        lines.append(
            "|-------|:----:|---|------|:----:|:---------:|:-------:|:---------:|:----------:|:--------:|:--------:|"
        )
    else:
        lines.append(
            "| Agent | # | 问题 | 状态 | 总耗时(s) | TTFT(s) | 首文本(s) | 输出Tokens | Tokens/s | 工具调用 |"
        )
        lines.append(
            "|-------|---|------|:----:|:---------:|:-------:|:---------:|:----------:|:--------:|:--------:|"
        )

    for r in results:
        m = r.get("metrics", {})
        status = "OK" if not r.get("error") else "X"
        q_short = r["question"][:25] + ("..." if len(r["question"]) > 25 else "")

        if rounds > 1:
            lines.append(
                f"| {r['agent']} | R{r['round']} | {r['question_num']} "
                f"| {q_short} "
                f"| {status} "
                f"| {m.get('total_time_sec', '-')} "
                f"| {m.get('ttft_sec') or '-'} "
                f"| {m.get('first_text_sec') or '-'} "
                f"| {m.get('output_tokens', '-')} "
                f"| {m.get('tokens_per_sec') or '-'} "
                f"| {m.get('tool_call_count', 0)} |"
            )
        else:
            lines.append(
                f"| {r['agent']} | {r['question_num']} "
                f"| {q_short} "
                f"| {status} "
                f"| {m.get('total_time_sec', '-')} "
                f"| {m.get('ttft_sec') or '-'} "
                f"| {m.get('first_text_sec') or '-'} "
                f"| {m.get('output_tokens', '-')} "
                f"| {m.get('tokens_per_sec') or '-'} "
                f"| {m.get('tool_call_count', 0)} |"
            )
    lines.append("")

    # ---- 二、按 Agent 多轮汇总 ----
    lines.append("## 二、按 Agent 性能统计（多轮平均）\n")

    agent_ids_in_order = list(config.get("test_questions", {}).keys())

    for agent_id in agent_ids_in_order:
        agent_results = [r for r in results if r["agent"] == agent_id]
        if not agent_results:
            continue

        ok_results = [r for r in agent_results if not r.get("error")]
        err_results = [r for r in agent_results if r.get("error")]
        num_questions = len(config["test_questions"][agent_id])

        lines.append(f"### {agent_id}  (成功 {len(ok_results)}/{len(agent_results)})\n")

        if not ok_results:
            lines.append("*所有测试均失败*\n")
            for r in err_results:
                lines.append(f"- R{r['round']} Q{r['question_num']}: {r.get('error', '未知错误')}")
            lines.append("")
            continue

        # -- 多轮平均汇总表 --
        # 按 question_num 分组，计算每个问题的多轮平均值
        # 然后再整体汇总
        times = [r["metrics"]["total_time_sec"] for r in ok_results]
        ttfts = [r["metrics"]["ttft_sec"] for r in ok_results if r["metrics"].get("ttft_sec")]
        first_texts = [r["metrics"]["first_text_sec"] for r in ok_results if r["metrics"].get("first_text_sec")]
        in_tokens = [r["metrics"]["input_tokens"] for r in ok_results]
        out_tokens = [r["metrics"]["output_tokens"] for r in ok_results]
        tps_list = [r["metrics"]["tokens_per_sec"] for r in ok_results if r["metrics"].get("tokens_per_sec")]
        tool_counts = [r["metrics"].get("tool_call_count", 0) for r in ok_results]

        lines.append("| 指标 | 最小 | 最大 | 平均 | 合计 |")
        lines.append("|------|------|------|------|------|")
        lines.append(f"| 总耗时 (s) | {' | '.join(_stats(times))} |")
        if ttfts:
            lines.append(f"| 首 Token 时间 TTFT (s) | {' | '.join(_stats(ttfts))} |")
        if first_texts:
            lines.append(f"| 首文本时间 (s) | {' | '.join(_stats(first_texts))} |")
        lines.append(f"| 输入 Tokens | {' | '.join(_stats(in_tokens))} |")
        lines.append(f"| 输出 Tokens | {' | '.join(_stats(out_tokens))} |")
        if tps_list:
            lines.append(f"| 吞吐量 (tokens/s) | {' | '.join(_stats(tps_list))} |")
        lines.append(
            f"| 工具调用次数 | {min(tool_counts)} | {max(tool_counts)}"
            f" | {sum(tool_counts)/len(tool_counts):.1f} | {sum(tool_counts)} |"
        )
        lines.append("")

        # -- 按问题维度的多轮均值（仅多轮时有意义） --
        if rounds > 1 and len(ok_results) >= num_questions:
            lines.append(f"**按问题维度（{rounds} 轮取均值）**:\n")
            lines.append(
                "| # | 问题 | 轮次成功率 | 平均耗时(s) | 平均TTFT(s) | 平均输出Tokens | 平均吞吐量(tokens/s) |"
            )
            lines.append(
                "|---|------|:----------:|:-----------:|:-----------:|:--------------:|:--------------------:|"
            )
            for q_num in range(1, num_questions + 1):
                q_results = [r for r in ok_results if r["question_num"] == q_num]
                q_err = [r for r in err_results if r["question_num"] == q_num]
                if not q_results:
                    continue
                q_text = config["test_questions"][agent_id][q_num - 1]
                q_short = q_text[:25] + ("..." if len(q_text) > 25 else "")
                success_rate = f"{len(q_results)}/{len(q_results) + len(q_err)}"

                q_times = [r["metrics"]["total_time_sec"] for r in q_results]
                q_ttfts = [r["metrics"]["ttft_sec"] for r in q_results if r["metrics"].get("ttft_sec")]
                q_out = [r["metrics"]["output_tokens"] for r in q_results]
                q_tps = [r["metrics"]["tokens_per_sec"] for r in q_results if r["metrics"].get("tokens_per_sec")]

                avg_time = _avg(q_times)
                avg_ttft = _avg(q_ttfts) if q_ttfts else "-"
                avg_out = _avg(q_out)
                avg_tps = _avg(q_tps) if q_tps else "-"

                lines.append(
                    f"| {q_num} | {q_short} | {success_rate} "
                    f"| {avg_time} | {avg_ttft} | {avg_out} | {avg_tps} |"
                )
            lines.append("")

    # ---- 三、详细问答记录 ----
    lines.append("## 三、详细问答记录\n")

    for agent_id in agent_ids_in_order:
        agent_results = [r for r in results if r["agent"] == agent_id]
        lines.append(f"### {agent_id}\n")

        # 按轮次分组
        for rnd in range(1, rounds + 1):
            rnd_results = [r for r in agent_results if r["round"] == rnd]

            if rounds > 1:
                lines.append(f"#### 第 {rnd} 轮\n")

            for r in rnd_results:
                q_label = f"问题 {r['question_num']}"
                if rounds == 1:
                    lines.append(f"#### {q_label}\n")
                else:
                    lines.append(f"##### {q_label}\n")

                lines.append(f"> {r['question']}\n")

                # 性能指标折叠
                m = r.get("metrics", {})
                lines.append("<details>")
                lines.append("<summary>性能指标</summary>\n")
                lines.append("| 指标 | 值 |")
                lines.append("|------|-----|")
                lines.append(f"| 总耗时 | {m.get('total_time_sec', '-')}s |")
                lines.append(f"| 首 Token 时间 (TTFT) | {m.get('ttft_sec') or '-'}s |")
                lines.append(f"| 首文本时间 | {m.get('first_text_sec') or '-'}s |")
                lines.append(f"| SSE 分块数 | {m.get('chunk_count', '-')} |")
                lines.append(f"| 输入 Tokens | {m.get('input_tokens', '-')} |")
                lines.append(f"| 输出 Tokens | {m.get('output_tokens', '-')} |")
                lines.append(f"| 总 Tokens | {m.get('total_tokens', '-')} |")
                lines.append(f"| 吞吐量 | {m.get('tokens_per_sec') or '-'} tokens/s |")
                lines.append(f"| 工具调用次数 | {m.get('tool_call_count', 0)} |")
                if m.get("tool_calls"):
                    for tc in m["tool_calls"]:
                        lines.append(f"| 工具 -> {tc['name']} | {tc['elapsed_sec']}s |")
                lines.append(f"| SSE 事件统计 | `{json.dumps(m.get('event_counts', {}), ensure_ascii=False)}` |")
                lines.append("")
                lines.append("</details>\n")

                # 思考过程折叠
                if m.get("thinking_text"):
                    lines.append("<details>")
                    lines.append("<summary>模型思考过程</summary>\n")
                    lines.append(m["thinking_text"])
                    lines.append("\n</details>\n")

                if r.get("error"):
                    lines.append(f"**错误**: `{r['error']}`\n")

                lines.append("**回答**:\n")
                lines.append(r["response"] if r["response"] else "*（无响应内容）*")
                lines.append("\n---\n")

    # ---- 写入文件 ----
    report_content = "\n".join(lines)
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(report_content)

    # 保存原始 JSON 数据
    json_file = output_dir / f"test_results_{timestamp}.json"
    with open(json_file, "w", encoding="utf-8") as f:
        json.dump(
            {
                "config": config,
                "timestamp": timestamp,
                "summary": {
                    "rounds": rounds,
                    "total": total,
                    "success": success_count,
                    "fail": fail_count,
                },
                "results": results,
            },
            f, ensure_ascii=False, indent=2,
        )

    return report_file


# ----------------------------------------------------------------
# CLI
# ----------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="测试 Gateway Agent 对接大模型的效果与性能（支持多轮）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python3 scripts/test-agent-models.py
  python3 scripts/test-agent-models.py --rounds 3
  python3 scripts/test-agent-models.py -c my-test.json -o results/run1
  python3 scripts/test-agent-models.py --base-url http://1.2.3.4:3000 --rounds 5
        """,
    )
    parser.add_argument("--config", "-c", help="JSON 配置文件路径")
    parser.add_argument("--output", "-o", default="test-output/agent-model-test",
                        help="报告输出目录")
    parser.add_argument("--rounds", "-r", type=int, help="测试轮数（覆盖配置文件）")
    parser.add_argument("--base-url", help="覆盖配置文件中的 base_url")
    parser.add_argument("--secret-key", help="覆盖配置文件中的 secret_key")
    parser.add_argument("--user-id", help="覆盖配置文件中的 user_id")
    args = parser.parse_args()

    config = load_config(args.config)

    if args.base_url:
        config["base_url"] = args.base_url
    if args.secret_key:
        config["secret_key"] = args.secret_key
    if args.user_id:
        config["user_id"] = args.user_id
    if args.rounds:
        config["rounds"] = args.rounds

    if config.get("rounds", 1) < 1:
        print("错误: rounds 必须 >= 1")
        sys.exit(1)

    run_test(config, args.output)


if __name__ == "__main__":
    main()
