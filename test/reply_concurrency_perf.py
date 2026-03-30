#!/usr/bin/env python3
import argparse
import json
import math
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
from collections import deque
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_GATEWAY_LOG_FILE = REPO_ROOT / "gateway" / "logs" / "gateway.log"


@dataclass
class WorkerContext:
    worker_id: int
    slot_id: int
    user_id: str
    session_id: Optional[str] = None


@dataclass
class ReplyResult:
    scenario: str
    preset: str
    round_index: int
    worker_id: int
    slot_id: int
    user_id: str
    session_id: Optional[str]
    ok: bool
    status_code: int
    ttfb_ms: float
    total_ms: float
    data_lines: int
    bytes_read: int
    error: Optional[str]
    started_at_epoch_ms: int
    finished_at_epoch_ms: int
    request_label: str


@dataclass
class LogEvent:
    timestamp_epoch_ms: int
    stage: str
    agent_id: Optional[str]
    user_id: Optional[str]
    session_id: Optional[str]
    raw_line: str
    values: Dict[str, str]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:3000")
    parser.add_argument("--path-prefix", default="/ops-gateway")
    parser.add_argument("--agent-id", default="universal-agent")
    parser.add_argument("--secret-key", default="test")
    parser.add_argument("--user-prefix", default="perf-user")
    parser.add_argument("--shared-user-id", default="perf-shared-user")
    parser.add_argument("--concurrency", type=int, default=5)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--scenario", choices=["warm", "cold"], default="warm")
    parser.add_argument(
        "--preset",
        choices=["multi-user-same-agent", "single-user-multi-session"],
        default="multi-user-same-agent",
    )
    parser.add_argument("--reply-path", choices=["reply", "agent/reply"], default="reply")
    parser.add_argument("--message", default='Reply with only "ok".')
    parser.add_argument("--timeout-sec", type=float, default=120.0)
    parser.add_argument("--stagger-ms", type=int, default=0)
    parser.add_argument("--verify-tls", action="store_true")
    # 开启后会读取 gateway 本地日志，并尝试把每次压测请求与 REPLY-PERF 阶段日志做对齐。
    parser.add_argument("--align-reply-perf-log", action="store_true")
    parser.add_argument("--gateway-log-file", default=str(DEFAULT_GATEWAY_LOG_FILE))
    parser.add_argument("--log-tail-lines", type=int, default=4000)
    parser.add_argument("--log-window-before-ms", type=int, default=1500)
    parser.add_argument("--log-window-after-ms", type=int, default=5000)
    parser.add_argument("--log-preview-limit", type=int, default=10)
    parser.add_argument("--output-json")
    return parser


def normalize_path_prefix(path_prefix: str) -> str:
    if not path_prefix:
        return ""
    path = "/" + path_prefix.strip("/")
    return path.rstrip("/")


def join_url(base_url: str, path_prefix: str, path: str) -> str:
    base = base_url.rstrip("/")
    prefix = normalize_path_prefix(path_prefix)
    relative = "/" + path.lstrip("/")
    return f"{base}{prefix}{relative}"


def build_ssl_context(verify_tls: bool) -> Optional[ssl.SSLContext]:
    if verify_tls:
        return None
    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    return context


def make_user_message(text: str) -> dict[str, Any]:
    return {
        "role": "user",
        "created": int(time.time()),
        "content": [{"type": "text", "text": text}],
        "metadata": {"userVisible": True, "agentVisible": True},
    }


def build_headers(secret_key: str, user_id: str, accept: str) -> Dict[str, str]:
    return {
        "Content-Type": "application/json",
        "Accept": accept,
        "X-Secret-Key": secret_key,
        "X-User-Id": user_id,
    }


def decode_response_body(raw: bytes) -> str:
    return raw.decode("utf-8", errors="replace")


def now_epoch_ms() -> int:
    return time.time_ns() // 1_000_000


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any],
    secret_key: str,
    user_id: str,
    timeout_sec: float,
    ssl_context: Optional[ssl.SSLContext],
) -> Tuple[int, Dict[str, Any], str]:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers=build_headers(secret_key, user_id, "application/json"),
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_sec, context=ssl_context) as response:
            raw = response.read()
            text = decode_response_body(raw)
            try:
                return response.status, json.loads(text), text
            except json.JSONDecodeError:
                return response.status, {}, text
    except urllib.error.HTTPError as error:
        raw = error.read()
        text = decode_response_body(raw)
        try:
            return error.code, json.loads(text), text
        except json.JSONDecodeError:
            return error.code, {}, text


def stream_reply(
    url: str,
    payload: dict[str, Any],
    secret_key: str,
    user_id: str,
    timeout_sec: float,
    ssl_context: Optional[ssl.SSLContext],
) -> Tuple[int, float, float, int, int, str, int, int]:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers=build_headers(secret_key, user_id, "text/event-stream"),
    )
    perf_start = time.perf_counter()
    started_at_epoch_ms = now_epoch_ms()
    first_data_at = None
    data_lines = 0
    raw_chunks: List[bytes] = []
    try:
        with urllib.request.urlopen(request, timeout=timeout_sec, context=ssl_context) as response:
            while True:
                line = response.readline()
                if not line:
                    break
                raw_chunks.append(line)
                # 只把 SSE 中的 data 行视为有效流式输出，用于计算首包时间与结果完整性。
                if line.startswith(b"data:"):
                    data_lines += 1
                    if first_data_at is None:
                        first_data_at = time.perf_counter()
            finished_at_epoch_ms = now_epoch_ms()
            total_ms = (time.perf_counter() - perf_start) * 1000.0
            ttfb_ms = ((first_data_at or time.perf_counter()) - perf_start) * 1000.0
            body = decode_response_body(b"".join(raw_chunks))
            return (
                response.status,
                ttfb_ms,
                total_ms,
                data_lines,
                len(body.encode("utf-8")),
                body,
                started_at_epoch_ms,
                finished_at_epoch_ms,
            )
    except urllib.error.HTTPError as error:
        body = decode_response_body(error.read())
        finished_at_epoch_ms = now_epoch_ms()
        total_ms = (time.perf_counter() - perf_start) * 1000.0
        return (
            error.code,
            total_ms,
            total_ms,
            data_lines,
            len(body.encode("utf-8")),
            body,
            started_at_epoch_ms,
            finished_at_epoch_ms,
        )


def parse_error_message(status_code: int, body: str, data_lines: int) -> Optional[str]:
    if status_code >= 400:
        return f"http_{status_code}: {body[:300]}"
    if data_lines == 0:
        return "empty_sse_stream"
    lowered = body.lower()
    if '"type":"error"' in lowered or '"type": "error"' in lowered:
        return body[:300]
    return None


def percentile(values: List[float], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * p
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return ordered[lower]
    weight = rank - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


def build_metric_summary(values: List[float]) -> Dict[str, float]:
    if not values:
        return {
            "count": 0,
            "avg": 0.0,
            "p50": 0.0,
            "p95": 0.0,
            "p99": 0.0,
            "max": 0.0,
            "min": 0.0,
        }
    return {
        "count": float(len(values)),
        "avg": sum(values) / len(values),
        "p50": percentile(values, 0.50),
        "p95": percentile(values, 0.95),
        "p99": percentile(values, 0.99),
        "max": max(values),
        "min": min(values),
    }


def build_result_summary(results: List[ReplyResult]) -> Dict[str, Any]:
    success_results = [item for item in results if item.ok]
    return {
        "total_requests": len(results),
        "success_count": len(success_results),
        "failure_count": len(results) - len(success_results),
        "ttfb_ms": build_metric_summary([item.ttfb_ms for item in success_results]),
        "total_ms": build_metric_summary([item.total_ms for item in success_results]),
    }


def print_metric_line(name: str, metrics: Dict[str, float]) -> None:
    if metrics["count"] == 0:
        print(f"{name}: 无成功样本")
        return
    print(
        f"{name}: avg={metrics['avg']:.2f} p50={metrics['p50']:.2f} "
        f"p95={metrics['p95']:.2f} p99={metrics['p99']:.2f} max={metrics['max']:.2f}"
    )


def print_summary(results: List[ReplyResult]) -> Dict[str, Any]:
    summary = build_result_summary(results)
    print("")
    print("=== Reply 并发测试汇总 ===")
    print(f"总请求数: {summary['total_requests']}")
    print(f"成功数: {summary['success_count']}")
    print(f"失败数: {summary['failure_count']}")
    print_metric_line("TTFB(ms)", summary["ttfb_ms"])
    print_metric_line("Total(ms)", summary["total_ms"])
    failed_results = [item for item in results if not item.ok]
    if failed_results:
        print("")
        print("=== 失败样本 ===")
        for item in failed_results[:10]:
            print(
                f"request={item.request_label} round={item.round_index} worker={item.worker_id} "
                f"user={item.user_id} status={item.status_code} error={item.error}"
            )
    success_results = [item for item in results if item.ok]
    slowest = sorted(success_results, key=lambda item: item.total_ms, reverse=True)[:10]
    if slowest:
        print("")
        print("=== 最慢请求 Top10 ===")
        for item in slowest:
            print(
                f"request={item.request_label} round={item.round_index} worker={item.worker_id} "
                f"user={item.user_id} session={item.session_id} "
                f"ttfb={item.ttfb_ms:.2f}ms total={item.total_ms:.2f}ms dataLines={item.data_lines}"
            )
    return summary


def group_results_by_round(results: List[ReplyResult]) -> Dict[str, Any]:
    grouped: Dict[int, List[ReplyResult]] = {}
    for item in results:
        grouped.setdefault(item.round_index, []).append(item)
    summary: Dict[str, Any] = {}
    print("")
    print("=== 按轮次统计 ===")
    for round_index in sorted(grouped):
        items = grouped[round_index]
        round_summary = build_result_summary(items)
        summary[str(round_index)] = round_summary
        print(
            f"round={round_index} total={round_summary['total_requests']} "
            f"success={round_summary['success_count']} fail={round_summary['failure_count']}"
        )
        print_metric_line("  TTFB(ms)", round_summary["ttfb_ms"])
        print_metric_line("  Total(ms)", round_summary["total_ms"])
    return summary


def group_results_by_user(results: List[ReplyResult]) -> Dict[str, Any]:
    grouped: Dict[str, List[ReplyResult]] = {}
    for item in results:
        grouped.setdefault(item.user_id, []).append(item)
    summary: Dict[str, Any] = {}
    print("")
    print("=== 按用户统计 ===")
    for user_id in sorted(grouped):
        items = grouped[user_id]
        user_summary = build_result_summary(items)
        summary[user_id] = user_summary
        print(
            f"user={user_id} total={user_summary['total_requests']} "
            f"success={user_summary['success_count']} fail={user_summary['failure_count']}"
        )
        print_metric_line("  TTFB(ms)", user_summary["ttfb_ms"])
        print_metric_line("  Total(ms)", user_summary["total_ms"])
    return summary


def create_session(
    args: argparse.Namespace,
    ssl_context: Optional[ssl.SSLContext],
    user_id: str,
) -> str:
    start_url = join_url(args.base_url, args.path_prefix, f"/agents/{args.agent_id}/agent/start")
    status_code, payload, raw_text = request_json(
        "POST",
        start_url,
        {},
        args.secret_key,
        user_id,
        args.timeout_sec,
        ssl_context,
    )
    if status_code >= 400:
        raise RuntimeError(f"create_session_failed status={status_code} body={raw_text[:300]}")
    session_id = payload.get("id")
    if not session_id:
        raise RuntimeError(f"create_session_missing_id body={raw_text[:300]}")
    return str(session_id)


def stop_session(
    args: argparse.Namespace,
    ssl_context: Optional[ssl.SSLContext],
    user_id: str,
    session_id: str,
) -> None:
    stop_url = join_url(args.base_url, args.path_prefix, f"/agents/{args.agent_id}/agent/stop")
    request_json(
        "POST",
        stop_url,
        {"session_id": session_id},
        args.secret_key,
        user_id,
        args.timeout_sec,
        ssl_context,
    )


def build_user_id(args: argparse.Namespace, index: int) -> str:
    # single-user-multi-session 场景下，所有并发槽位共用一个 userId，仅 session 不同。
    if args.preset == "single-user-multi-session":
        return args.shared_user_id
    return f"{args.user_prefix}-{index + 1}"


def build_request_label(worker: WorkerContext, round_index: int, session_id: Optional[str]) -> str:
    session_part = session_id if session_id else "no-session"
    return f"round-{round_index}-worker-{worker.worker_id}-slot-{worker.slot_id}-{session_part}"


def run_reply_once(
    args: argparse.Namespace,
    ssl_context: Optional[ssl.SSLContext],
    worker: WorkerContext,
    round_index: int,
) -> ReplyResult:
    session_id = worker.session_id
    stop_after = False
    started_at_epoch_ms = now_epoch_ms()
    request_label = build_request_label(worker, round_index, session_id)
    try:
        # cold 模式下每次请求都新建 session；warm 模式复用 prepare_workers 中预建的 session。
        if args.scenario == "cold" or not session_id:
            session_id = create_session(args, ssl_context, worker.user_id)
            stop_after = args.scenario == "cold"
            request_label = build_request_label(worker, round_index, session_id)
        message = (
            f"{args.message} "
            f"[request={request_label} round={round_index} worker={worker.worker_id} slot={worker.slot_id}]"
        )
        reply_url = join_url(args.base_url, args.path_prefix, f"/agents/{args.agent_id}/{args.reply_path}")
        (
            status_code,
            ttfb_ms,
            total_ms,
            data_lines,
            bytes_read,
            body,
            started_at_epoch_ms,
            finished_at_epoch_ms,
        ) = stream_reply(
            reply_url,
            {"session_id": session_id, "user_message": make_user_message(message)},
            args.secret_key,
            worker.user_id,
            args.timeout_sec,
            ssl_context,
        )
        error = parse_error_message(status_code, body, data_lines)
        return ReplyResult(
            scenario=args.scenario,
            preset=args.preset,
            round_index=round_index,
            worker_id=worker.worker_id,
            slot_id=worker.slot_id,
            user_id=worker.user_id,
            session_id=session_id,
            ok=error is None,
            status_code=status_code,
            ttfb_ms=ttfb_ms,
            total_ms=total_ms,
            data_lines=data_lines,
            bytes_read=bytes_read,
            error=error,
            started_at_epoch_ms=started_at_epoch_ms,
            finished_at_epoch_ms=finished_at_epoch_ms,
            request_label=request_label,
        )
    except Exception as error:
        return ReplyResult(
            scenario=args.scenario,
            preset=args.preset,
            round_index=round_index,
            worker_id=worker.worker_id,
            slot_id=worker.slot_id,
            user_id=worker.user_id,
            session_id=session_id,
            ok=False,
            status_code=0,
            ttfb_ms=0.0,
            total_ms=0.0,
            data_lines=0,
            bytes_read=0,
            error=str(error),
            started_at_epoch_ms=started_at_epoch_ms,
            finished_at_epoch_ms=now_epoch_ms(),
            request_label=request_label,
        )
    finally:
        if stop_after and session_id:
            try:
                stop_session(args, ssl_context, worker.user_id, session_id)
            except Exception:
                pass


def prepare_workers(args: argparse.Namespace, ssl_context: Optional[ssl.SSLContext]) -> List[WorkerContext]:
    workers = [
        WorkerContext(
            worker_id=index + 1,
            slot_id=index + 1,
            user_id=build_user_id(args, index),
        )
        for index in range(args.concurrency)
    ]
    if args.scenario == "warm":
        # warm 模式在压测前先把每个槽位的 session 建好，避免把建连成本混入每轮请求。
        for worker in workers:
            worker.session_id = create_session(args, ssl_context, worker.user_id)
    return workers


def cleanup_workers(args: argparse.Namespace, ssl_context: Optional[ssl.SSLContext], workers: List[WorkerContext]) -> None:
    if args.scenario != "warm":
        return
    for worker in workers:
        if worker.session_id:
            try:
                stop_session(args, ssl_context, worker.user_id, worker.session_id)
            except Exception:
                pass


def run_test(args: argparse.Namespace) -> List[ReplyResult]:
    ssl_context = build_ssl_context(args.verify_tls)
    workers = prepare_workers(args, ssl_context)
    results: List[ReplyResult] = []
    print(
        f"开始执行 reply 并发测试: preset={args.preset} scenario={args.scenario} "
        f"concurrency={args.concurrency} rounds={args.rounds} agent={args.agent_id}"
    )
    try:
        for round_index in range(1, args.rounds + 1):
            round_start = time.perf_counter()
            futures = []
            with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
                for worker in workers:
                    futures.append(executor.submit(run_reply_once, args, ssl_context, worker, round_index))
                    if args.stagger_ms > 0:
                        time.sleep(args.stagger_ms / 1000.0)
                for future in as_completed(futures):
                    result = future.result()
                    results.append(result)
                    status = "OK" if result.ok else "FAIL"
                    print(
                        f"[{status}] request={result.request_label} round={result.round_index} worker={result.worker_id} "
                        f"user={result.user_id} session={result.session_id} "
                        f"ttfb={result.ttfb_ms:.2f}ms total={result.total_ms:.2f}ms "
                        f"status={result.status_code} dataLines={result.data_lines}"
                        + (f" error={result.error}" if result.error else "")
                    )
            round_elapsed_ms = (time.perf_counter() - round_start) * 1000.0
            print(f"round={round_index} 完成，耗时 {round_elapsed_ms:.2f}ms")
    finally:
        cleanup_workers(args, ssl_context, workers)
    return results


def tail_lines(file_path: Path, max_lines: int) -> List[str]:
    with file_path.open("r", encoding="utf-8", errors="replace") as handle:
        return list(deque(handle, maxlen=max_lines))


def parse_log_timestamp_ms(line: str) -> Optional[int]:
    if len(line) < 23:
        return None
    try:
        return int(datetime.strptime(line[:23], "%Y-%m-%d %H:%M:%S.%f").timestamp() * 1000)
    except ValueError:
        return None


def extract_value(payload: str, key: str) -> Optional[str]:
    match = re.search(rf"{re.escape(key)}=([^ ]+)", payload)
    if not match:
        return None
    value = match.group(1)
    if value == "null":
        return None
    return value


def load_reply_perf_events(log_file: Path, max_lines: int) -> List[LogEvent]:
    if not log_file.exists():
        return []
    events: List[LogEvent] = []
    for raw_line in tail_lines(log_file, max_lines):
        if "[REPLY-PERF]" not in raw_line:
            continue
        timestamp_epoch_ms = parse_log_timestamp_ms(raw_line)
        if timestamp_epoch_ms is None:
            continue
        payload = raw_line.split("[REPLY-PERF]", 1)[1].strip()
        stage = extract_value(payload, "stage")
        if not stage:
            continue
        values = {
            "stage": stage,
            "agentId": extract_value(payload, "agentId") or "",
            "userId": extract_value(payload, "userId") or "",
            "sessionId": extract_value(payload, "sessionId") or "",
            "elapsed": extract_value(payload, "elapsed") or "",
            "stageMs": extract_value(payload, "stageMs") or "",
            "totalMs": extract_value(payload, "totalMs") or "",
            "postRelayReadyMs": extract_value(payload, "postRelayReadyMs") or "",
            "bodyLen": extract_value(payload, "bodyLen") or "",
            "port": extract_value(payload, "port") or "",
            "required": extract_value(payload, "required") or "",
            "generated": extract_value(payload, "generated") or "",
            "bytes": extract_value(payload, "bytes") or "",
            "error": extract_value(payload, "error") or "",
        }
        events.append(
            LogEvent(
                timestamp_epoch_ms=timestamp_epoch_ms,
                stage=stage,
                agent_id=values["agentId"] or None,
                user_id=values["userId"] or None,
                session_id=values["sessionId"] or None,
                raw_line=raw_line.rstrip("\n"),
                values=values,
            )
        )
    return events


def event_matches_result(args: argparse.Namespace, result: ReplyResult, event: LogEvent) -> bool:
    if event.user_id != result.user_id:
        return False
    if event.agent_id and event.agent_id != args.agent_id:
        return False
    if event.session_id and result.session_id and event.session_id != result.session_id:
        return False
    # hooks 阶段可能早于请求流真正返回，outputFiles 又可能稍晚结束，所以这里保留前后时间窗口。
    start_lower = result.started_at_epoch_ms - args.log_window_before_ms
    end_upper = result.finished_at_epoch_ms + args.log_window_after_ms
    return start_lower <= event.timestamp_epoch_ms <= end_upper


def event_metric_ms(event: LogEvent) -> Optional[float]:
    # 不同阶段日志的耗时字段名不一致，这里按优先级提取一个可展示的统一毫秒值。
    for key in ("totalMs", "stageMs", "elapsed", "postRelayReadyMs"):
        value = event.values.get(key)
        if not value:
            continue
        normalized = value.removesuffix("ms")
        try:
            return float(normalized)
        except ValueError:
            continue
    return None


def align_reply_perf_logs(args: argparse.Namespace, results: List[ReplyResult]) -> Dict[str, Any]:
    if not args.align_reply_perf_log:
        return {}
    log_file = Path(args.gateway_log_file)
    events = load_reply_perf_events(log_file, args.log_tail_lines)
    if not events:
        print("")
        print(f"=== REPLY-PERF 日志对齐 ===")
        print(f"未找到可对齐日志，logFile={log_file}")
        return {
            "log_file": str(log_file),
            "events_loaded": 0,
            "requests": {},
        }
    aligned_requests: Dict[str, Any] = {}
    for result in results:
        matched = [event for event in events if event_matches_result(args, result, event)]
        matched.sort(key=lambda item: item.timestamp_epoch_ms)
        stage_summary = {}
        for event in matched:
            metric_ms = event_metric_ms(event)
            # 同一 stage 若出现多条日志，保留时间上最后一次，便于直接查看最终阶段结果。
            stage_summary[event.stage] = {
                "timestamp_epoch_ms": event.timestamp_epoch_ms,
                "metric_ms": metric_ms,
                "raw_line": event.raw_line,
            }
        aligned_requests[result.request_label] = {
            "user_id": result.user_id,
            "session_id": result.session_id,
            "matched_event_count": len(matched),
            "stages": stage_summary,
            "raw_lines": [event.raw_line for event in matched],
        }
    print("")
    print("=== REPLY-PERF 日志对齐 ===")
    print(f"logFile={log_file} eventsLoaded={len(events)}")
    preview_targets = sorted(
        [item for item in results if item.ok],
        key=lambda item: item.total_ms,
        reverse=True,
    )[: args.log_preview_limit]
    for result in preview_targets:
        aligned = aligned_requests[result.request_label]
        stage_bits = []
        for stage in ("hooks", "getOrSpawn", "resume", "firstChunk", "relayComplete", "outputFiles", "relayError"):
            stage_data = aligned["stages"].get(stage)
            if not stage_data:
                continue
            metric_ms = stage_data["metric_ms"]
            if metric_ms is None:
                stage_bits.append(stage)
            else:
                stage_bits.append(f"{stage}={metric_ms:.0f}ms")
        stage_text = ", ".join(stage_bits) if stage_bits else "无匹配阶段"
        print(
            f"request={result.request_label} user={result.user_id} session={result.session_id} "
            f"matched={aligned['matched_event_count']} stages={stage_text}"
        )
    return {
        "log_file": str(log_file),
        "events_loaded": len(events),
        "requests": aligned_requests,
    }


def save_results(
    output_path: str,
    args: argparse.Namespace,
    results: List[ReplyResult],
    overall_summary: Dict[str, Any],
    by_round_summary: Dict[str, Any],
    by_user_summary: Dict[str, Any],
    log_alignment: Dict[str, Any],
) -> None:
    payload = {
        "config": {
            "base_url": args.base_url,
            "path_prefix": args.path_prefix,
            "agent_id": args.agent_id,
            "scenario": args.scenario,
            "preset": args.preset,
            "concurrency": args.concurrency,
            "rounds": args.rounds,
            "reply_path": args.reply_path,
            "message": args.message,
            "gateway_log_file": args.gateway_log_file,
            "align_reply_perf_log": args.align_reply_perf_log,
        },
        "summary": overall_summary,
        "by_round": by_round_summary,
        "by_user": by_user_summary,
        "log_alignment": log_alignment,
        "results": [asdict(item) for item in results],
    }
    with open(output_path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if args.concurrency <= 0:
        parser.error("--concurrency 必须大于 0")
    if args.rounds <= 0:
        parser.error("--rounds 必须大于 0")
    results = run_test(args)
    overall_summary = print_summary(results)
    by_round_summary = group_results_by_round(results)
    by_user_summary = group_results_by_user(results)
    log_alignment = align_reply_perf_logs(args, results)
    if args.output_json:
        save_results(
            args.output_json,
            args,
            results,
            overall_summary,
            by_round_summary,
            by_user_summary,
            log_alignment,
        )
        print(f"已写出结果文件: {args.output_json}")
    failed = any(not item.ok for item in results)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
