#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Stability Test: 20-round conversation + 10-round resume
# Tests custom_opsagentllm provider with various tool usage through gateway
# ==============================================================================

GATEWAY="https://localhost:3000"
SECRET="test"
AGENT="universal-agent"
SESSION_ID="stability_test_$(date +%Y%m%d_%H%M%S)"
REPORT_FILE="/Users/buyangnie/Documents/GitHub/ops-factory/test/stability-report.md"
MAX_WAIT=120  # max seconds per reply

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }

# Initialize report
cat > "$REPORT_FILE" <<EOF
# Stability Test Report

- **Date**: $(date '+%Y-%m-%d %H:%M:%S')
- **Session ID**: $SESSION_ID
- **Agent**: $AGENT
- **Provider**: custom_opsagentllm (kimi-k2-turbo-preview)
- **Gateway**: $GATEWAY

---

EOF

PASS_COUNT=0
FAIL_COUNT=0
TOTAL_ROUNDS=0

# Send a reply and capture response
# Usage: send_reply <round> <phase> <message> [<expected_tool>]
send_reply() {
    local round="$1"
    local phase="$2"
    local message="$3"
    local expected_tool="${4:-none}"
    TOTAL_ROUNDS=$((TOTAL_ROUNDS + 1))

    log_info "[$phase] Round $round: Sending message..."
    echo -e "  ${YELLOW}Message:${NC} $message"

    local start_time=$(date +%s)

    # Build the session reply body
    local request_id
    request_id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local body="{\"request_id\":\"${request_id}\",\"user_message\":{\"role\":\"user\",\"created\":$(date +%s),\"content\":[{\"type\":\"text\",\"text\":$(echo "$message" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))')}],\"metadata\":{\"userVisible\":true,\"agentVisible\":true}}}"

    # Subscribe to session events, then submit the reply.
    local tmpfile=$(mktemp)
    curl -sk --max-time $MAX_WAIT \
        -N "${GATEWAY}/agents/${AGENT}/sessions/${SESSION_ID}/events" \
        -H "X-Secret-Key: ${SECRET}" -H "X-User-Id: admin" \
        > "$tmpfile" 2>/dev/null &
    local events_pid=$!
    sleep 0.5

    local http_code
    http_code=$(curl -sk --max-time $MAX_WAIT \
        -w "%{http_code}" \
        -o /dev/null \
        -X POST "${GATEWAY}/agents/${AGENT}/sessions/${SESSION_ID}/reply" \
        -H "Content-Type: application/json" \
        -H "X-Secret-Key: ${SECRET}" -H "X-User-Id: admin" \
        -d "$body" 2>/dev/null) || true

    local waited=0
    while [ "$waited" -lt "$MAX_WAIT" ]; do
        if grep -q '"type":"Finish"\|"type":"Error"' "$tmpfile" 2>/dev/null; then
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done
    kill "$events_pid" 2>/dev/null || true

    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))

    # Parse SSE response - extract text content and tool calls
    local response_text=""
    local tools_used=""
    local has_error=false
    local chunk_count=0

    if [ -f "$tmpfile" ] && [ -s "$tmpfile" ]; then
        chunk_count=$(grep -c "^data:" "$tmpfile" 2>/dev/null || echo "0")

        # Extract text content from SSE events
        response_text=$(grep "^data:" "$tmpfile" | sed 's/^data: //' | \
            python3 -c '
import sys, json
texts = []
tools = []
errors = []
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    try:
        obj = json.loads(line)
        t = obj.get("type", "")
        if t == "Message":
            for c in obj.get("content", []):
                if c.get("type") == "text":
                    texts.append(c.get("text", ""))
                elif c.get("type") == "toolUse":
                    tools.append(c.get("name", "unknown"))
                elif c.get("type") == "toolResult":
                    pass  # skip tool results
        elif t == "Error":
            errors.append(obj.get("error", "unknown error"))
    except:
        pass
print("TEXT:" + " ".join(texts)[:500])
print("TOOLS:" + ",".join(set(tools)))
print("ERRORS:" + ";".join(errors))
' 2>/dev/null || echo "TEXT:parse_error")

        local text_part=$(echo "$response_text" | grep "^TEXT:" | sed 's/^TEXT://')
        local tools_part=$(echo "$response_text" | grep "^TOOLS:" | sed 's/^TOOLS://')
        local errors_part=$(echo "$response_text" | grep "^ERRORS:" | sed 's/^ERRORS://')

        response_text="$text_part"
        tools_used="$tools_part"

        if [ -n "$errors_part" ]; then
            has_error=true
            response_text="ERROR: $errors_part"
        fi
    else
        has_error=true
        response_text="No response received (HTTP $http_code)"
    fi

    rm -f "$tmpfile"

    # Determine pass/fail
    local status="PASS"
    if [ "$has_error" = true ] || [ -z "$response_text" ] || [ "$response_text" = "parse_error" ]; then
        status="FAIL"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        log_error "Round $round FAILED (${elapsed}s, HTTP $http_code, chunks=$chunk_count)"
    else
        PASS_COUNT=$((PASS_COUNT + 1))
        log_ok "Round $round OK (${elapsed}s, chunks=$chunk_count, tools=${tools_used:-none})"
    fi

    # Truncate response for display
    local display_text="${response_text:0:200}"
    [ ${#response_text} -gt 200 ] && display_text="${display_text}..."

    echo -e "  ${YELLOW}Response:${NC} $display_text"
    echo ""

    # Append to report
    cat >> "$REPORT_FILE" <<ROUND
## $phase Round $round [$status]

- **Input**: $message
- **Response** (${elapsed}s, ${chunk_count} chunks): ${display_text}
- **Tools Used**: ${tools_used:-none}
- **HTTP**: $http_code
- **Status**: $status

ROUND
}

# ==============================================================================
# Phase 1: Create session and do 20 rounds
# ==============================================================================
echo ""
echo "=============================================="
echo "  Phase 1: 20-Round Conversation Test"
echo "  Session: $SESSION_ID"
echo "=============================================="
echo ""

# Step 1: Start session (create via goosed /agent/start)
log_info "Creating session $SESSION_ID..."
START_RESP=$(curl -sk --max-time 30 -w "\n%{http_code}" \
    -X POST "${GATEWAY}/agents/${AGENT}/agent/start" \
    -H "Content-Type: application/json" \
    -H "X-Secret-Key: ${SECRET}" -H "X-User-Id: admin" \
    -d "{\"working_dir\":\"/Users/buyangnie/Documents/GitHub/ops-factory/gateway/users/admin/agents/${AGENT}\"}" 2>&1)
START_CODE=$(echo "$START_RESP" | tail -1)
START_BODY=$(echo "$START_RESP" | sed '$d')
log_info "Start response: HTTP $START_CODE"

# Step 2: Resume session (load provider + extensions)
log_info "Resuming session $SESSION_ID (loading provider + extensions)..."
RESUME_RESP=$(curl -sk --max-time 120 -w "\n%{http_code}" \
    -X POST "${GATEWAY}/agents/${AGENT}/agent/resume" \
    -H "Content-Type: application/json" \
    -H "X-Secret-Key: ${SECRET}" -H "X-User-Id: admin" \
    -d "{\"session_id\":\"${SESSION_ID}\",\"load_model_and_extensions\":true}" 2>&1)
RESUME_CODE=$(echo "$RESUME_RESP" | tail -1)
RESUME_BODY=$(echo "$RESUME_RESP" | sed '$d')
log_info "Resume response: HTTP $RESUME_CODE"

if [ "$RESUME_CODE" != "200" ]; then
    log_error "Resume failed with HTTP $RESUME_CODE"
    echo "$RESUME_BODY"
    # Try to continue anyway
fi

echo "$RESUME_BODY" | head -3

cat >> "$REPORT_FILE" <<SETUP
## Session Setup

- **Start**: HTTP $START_CODE
- **Resume**: HTTP $RESUME_CODE
- **Resume Body**: $(echo "$RESUME_BODY" | head -1)

---

# Phase 1: 20-Round Conversation

SETUP

# 20 rounds with various tool-triggering prompts
send_reply 1 "Phase1" "你好，请自我介绍一下，你是什么 AI，用什么模型？"
send_reply 2 "Phase1" "请列出当前工作目录下的文件和文件夹"
send_reply 3 "Phase1" "请在当前目录创建一个文件 test_stability.txt，内容是: Hello from stability test $(date +%Y%m%d)"
send_reply 4 "Phase1" "读取 test_stability.txt 的内容，确认创建成功"
send_reply 5 "Phase1" "计算 fibonacci 数列的第 20 项，用 Python 写代码并执行"
send_reply 6 "Phase1" "请查看当前系统时间和操作系统信息"
send_reply 7 "Phase1" "创建一个目录 test_dir，然后在里面创建 3 个文件：a.txt, b.txt, c.txt"
send_reply 8 "Phase1" "列出 test_dir 目录的内容"
send_reply 9 "Phase1" "写一个 bash 脚本 hello.sh，内容是打印 Hello World 10 次，然后执行它"
send_reply 10 "Phase1" "删除刚才创建的 hello.sh 文件"
send_reply 11 "Phase1" "当前的 PATH 环境变量是什么？"
send_reply 12 "Phase1" "用 Python 计算 100 以内所有质数的和"
send_reply 13 "Phase1" "请把 test_stability.txt 的内容追加一行：Round 13 completed"
send_reply 14 "Phase1" "读取 test_stability.txt 确认追加成功"
send_reply 15 "Phase1" "用 Python 生成一个 5x5 的乘法表并打印"
send_reply 16 "Phase1" "查看当前目录的磁盘使用情况"
send_reply 17 "Phase1" "请解释什么是 MapReduce，不需要执行任何命令"
send_reply 18 "Phase1" "创建文件 summary.txt，内容是这次对话的摘要"
send_reply 19 "Phase1" "列出当前目录下所有 .txt 文件"
send_reply 20 "Phase1" "清理测试文件：删除 test_stability.txt, summary.txt 和 test_dir 目录"

echo ""
echo "=============================================="
echo "  Phase 1 Complete: $PASS_COUNT pass, $FAIL_COUNT fail"
echo "=============================================="
echo ""

cat >> "$REPORT_FILE" <<P1END

---

## Phase 1 Summary

- **Passed**: $PASS_COUNT / 20
- **Failed**: $FAIL_COUNT / 20

---

# Phase 2: Resume + 10-Round Conversation

P1END

# ==============================================================================
# Phase 2: Resume and continue 10 more rounds
# ==============================================================================
echo "=============================================="
echo "  Phase 2: Resume + 10-Round Conversation"
echo "  Resuming session: $SESSION_ID"
echo "=============================================="
echo ""

# Resume the session
log_info "Resuming session $SESSION_ID for Phase 2..."
RESUME2_RESP=$(curl -sk --max-time 120 -w "\n%{http_code}" \
    -X POST "${GATEWAY}/agents/${AGENT}/agent/resume" \
    -H "Content-Type: application/json" \
    -H "X-Secret-Key: ${SECRET}" -H "X-User-Id: admin" \
    -d "{\"session_id\":\"${SESSION_ID}\",\"load_model_and_extensions\":true}" 2>&1)
RESUME2_CODE=$(echo "$RESUME2_RESP" | tail -1)
log_info "Phase 2 Resume: HTTP $RESUME2_CODE"

cat >> "$REPORT_FILE" <<SETUP2
## Phase 2 Resume

- **Resume**: HTTP $RESUME2_CODE

SETUP2

PHASE1_PASS=$PASS_COUNT
PHASE1_FAIL=$FAIL_COUNT
PASS_COUNT=0
FAIL_COUNT=0

send_reply 1 "Phase2" "你还记得我们之前的对话吗？请简要回顾一下我们做了什么"
send_reply 2 "Phase2" "请在当前目录创建 phase2_test.txt，内容是: Phase 2 started"
send_reply 3 "Phase2" "用 Python 写一个函数计算两个数的最大公约数，并测试 gcd(48, 18)"
send_reply 4 "Phase2" "查看当前工作目录的绝对路径"
send_reply 5 "Phase2" "写一个 Python 脚本 sort_test.py，实现冒泡排序并排序 [64, 34, 25, 12, 22, 11, 90]"
send_reply 6 "Phase2" "执行 sort_test.py 并查看结果"
send_reply 7 "Phase2" "当前有哪些 goosed 进程在运行？用 ps 命令查看"
send_reply 8 "Phase2" "请用一句话总结 Python 和 Java 的区别"
send_reply 9 "Phase2" "删除 phase2_test.txt 和 sort_test.py"
send_reply 10 "Phase2" "总结这次完整的测试过程，包括 Phase 1 和 Phase 2 的所有操作"

echo ""
echo "=============================================="
echo "  Phase 2 Complete: $PASS_COUNT pass, $FAIL_COUNT fail"
echo "=============================================="
echo ""

PHASE2_PASS=$PASS_COUNT
PHASE2_FAIL=$FAIL_COUNT

TOTAL_PASS=$((PHASE1_PASS + PHASE2_PASS))
TOTAL_FAIL=$((PHASE1_FAIL + PHASE2_FAIL))

cat >> "$REPORT_FILE" <<FINAL

---

## Phase 2 Summary

- **Passed**: $PHASE2_PASS / 10
- **Failed**: $PHASE2_FAIL / 10

---

# Final Summary

| Metric | Value |
|--------|-------|
| Total Rounds | $TOTAL_ROUNDS |
| Phase 1 Pass | $PHASE1_PASS / 20 |
| Phase 1 Fail | $PHASE1_FAIL / 20 |
| Phase 2 Pass | $PHASE2_PASS / 10 |
| Phase 2 Fail | $PHASE2_FAIL / 10 |
| **Total Pass** | **$TOTAL_PASS / $TOTAL_ROUNDS** |
| **Total Fail** | **$TOTAL_FAIL / $TOTAL_ROUNDS** |
| **Result** | $([ $TOTAL_FAIL -eq 0 ] && echo "ALL PASSED ✅" || echo "HAS FAILURES ❌") |

FINAL

echo "=============================================="
echo "  FINAL RESULT"
echo "  Phase 1: $PHASE1_PASS/20 pass, $PHASE1_FAIL/20 fail"
echo "  Phase 2: $PHASE2_PASS/10 pass, $PHASE2_FAIL/10 fail"
echo "  Total:   $TOTAL_PASS/$TOTAL_ROUNDS pass"
if [ $TOTAL_FAIL -eq 0 ]; then
    log_ok "ALL TESTS PASSED"
else
    log_error "$TOTAL_FAIL TESTS FAILED"
fi
echo "  Report: $REPORT_FILE"
echo "=============================================="
