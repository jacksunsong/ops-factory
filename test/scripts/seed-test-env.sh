#!/usr/bin/env bash
# Seed "测试环境" resource tree via gateway REST API
#
# Creates:
#   - 2 business types (彩铃查询, 彩铃上报)
#   - 3 groups (测试环境 > 系统资源, 典型业务)
#   - 5 clusters (RCPA, NSLB, GMDB, RCPADB, GWDB) under 系统资源 — uses existing cluster types
#   - 5 hosts (one per cluster)
#   - 4 host relations (topology: NSLB→RCPA→{GWDB,GMDB,RCPADB})
#   - 2 business services (彩铃查询, 彩铃上报) under 典型业务, linked to NSLB host
#
# Prerequisites:
#   - curl installed
#   - Gateway running at BASE_URL (default http://127.0.0.1:3000/gateway)
#
# Usage:
#   bash test/scripts/seed-test-env.sh
#   bash test/scripts/seed-test-env.sh http://localhost:3000/gateway my-secret-key

set -euo pipefail

BASE="${1:-http://127.0.0.1:3000/gateway}"
SECRET="${2:-test}"
USER="admin"

# Temp dir for JSON body files (handles CJK encoding on Windows)
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

post() {
    local path="$1" json_file="$2"
    curl -s -X POST "${BASE}${path}" \
        -H "Content-Type: application/json; charset=utf-8" \
        -H "x-secret-key: $SECRET" \
        -H "x-user-id: $USER" \
        -d @"$json_file"
}

ok_count=0
fail_count=0

step() {
    echo ""
    echo "=== Step $1: $2 ==="
}

# Extract a nested JSON ID using grep+sed (no jq dependency).
# e.g. extract_id '{"success":true,"group":{"id":"abc"}}' "group"
extract_id() {
    local resp="$1" key="$2"
    echo "$resp" | grep -o "\"$key\":{\"id\":\"[^\"]*\"" | sed 's/.*"id":"\([^"]*\)".*/\1/'
}

created() {
    local label="$1" entity="$2" key="$3" resp="$4"
    local id
    id=$(extract_id "$resp" "$key")
    if [ -z "$id" ]; then
        echo "FAIL: could not extract $key.id from response for $label"
        echo "  Response: $(echo "$resp" | head -c 300)"
        fail_count=$((fail_count + 1))
        return 1
    fi
    echo "OK: $label → $id"
    ok_count=$((ok_count + 1))
    eval "$entity=\"\$id\""
}

# Write JSON body to a temp file, substituting @VAR@ placeholders.
# Usage: body "template with @GROUP_ID@" GROUP_ID "$GROUP_ID" ...
body() {
    local template="$1"; shift
    local file="$TMPDIR/_body.json"
    local result="$template"
    while [ $# -ge 2 ]; do
        local varname="$1"; shift
        local varval="$1"; shift
        result="${result//@$varname@/$varval}"
    done
    printf '%s' "$result" > "$file"
    echo "$file"
}

# ── Step 1: Create business types ──────────────────────────────────────────────
step 1 "Create business types"

F=$(body '{"name":"彩铃查询","code":"color_ring_query","description":"彩铃查询业务类型"}')
RESP=$(post "/business-types" "$F")
created "彩铃查询 BT" BT_QUERY_ID "businessType" "$RESP"

F=$(body '{"name":"彩铃上报","code":"color_ring_report","description":"彩铃上报业务类型"}')
RESP=$(post "/business-types" "$F")
created "彩铃上报 BT" BT_REPORT_ID "businessType" "$RESP"

# ── Step 2: Create group hierarchy (测试环境 > 系统资源, 典型业务) ────────────
step 2 "Create group hierarchy"

F=$(body '{"name":"测试环境","description":"测试环境资源管理"}')
RESP=$(post "/host-groups" "$F")
created "测试环境 group" ROOT_GROUP_ID "group" "$RESP"

F=$(body '{"name":"系统资源","parentId":"@ROOT_GROUP_ID@","description":""}' ROOT_GROUP_ID "$ROOT_GROUP_ID")
RESP=$(post "/host-groups" "$F")
created "系统资源 group" SYS_GROUP_ID "group" "$RESP"

F=$(body '{"name":"典型业务","parentId":"@ROOT_GROUP_ID@","description":""}' ROOT_GROUP_ID "$ROOT_GROUP_ID")
RESP=$(post "/host-groups" "$F")
created "典型业务 group" BIZ_GROUP_ID "group" "$RESP"

# ── Step 3: Create clusters under 系统资源 ─────────────────────────────────────
step 3 "Create clusters"

F=$(body '{"name":"RCPA","type":"RCPA","groupId":"@SYS_GROUP_ID@","description":"RCPA集群"}' SYS_GROUP_ID "$SYS_GROUP_ID")
RESP=$(post "/clusters" "$F")
created "RCPA cluster" RCPA_CLUSTER_ID "cluster" "$RESP"

F=$(body '{"name":"NSLB","type":"NSLB","groupId":"@SYS_GROUP_ID@","description":"NSLB集群"}' SYS_GROUP_ID "$SYS_GROUP_ID")
RESP=$(post "/clusters" "$F")
created "NSLB cluster" NSLB_CLUSTER_ID "cluster" "$RESP"

F=$(body '{"name":"GMDB","type":"GMDB","groupId":"@SYS_GROUP_ID@","description":"GMDB集群"}' SYS_GROUP_ID "$SYS_GROUP_ID")
RESP=$(post "/clusters" "$F")
created "GMDB cluster" GMDB_CLUSTER_ID "cluster" "$RESP"

F=$(body '{"name":"RCPADB","type":"RCPADB","groupId":"@SYS_GROUP_ID@","description":"RCPADB集群"}' SYS_GROUP_ID "$SYS_GROUP_ID")
RESP=$(post "/clusters" "$F")
created "RCPADB cluster" RCPADB_CLUSTER_ID "cluster" "$RESP"

F=$(body '{"name":"GWDB","type":"GWDB","groupId":"@SYS_GROUP_ID@","description":"GWDB集群"}' SYS_GROUP_ID "$SYS_GROUP_ID")
RESP=$(post "/clusters" "$F")
created "GWDB cluster" GWDB_CLUSTER_ID "cluster" "$RESP"

# ── Step 4: Create hosts ──────────────────────────────────────────────────────
step 4 "Create hosts"

F=$(body '{"name":"RCPA","ip":"2409:808c:8a:109::35","port":22,"os":"Linux","username":"rcpa","authType":"password","credential":"MGhwnfv_1105","clusterId":"@RCPA_CLUSTER_ID@","purpose":"RCPA处理节点","description":"RCPA主机"}' RCPA_CLUSTER_ID "$RCPA_CLUSTER_ID")
RESP=$(post "/hosts" "$F")
created "RCPA host" RCPA_HOST_ID "host" "$RESP"

F=$(body '{"name":"NSLB","ip":"2409:808c:8a:109::20","port":22,"os":"Linux","username":"nslb","authType":"password","credential":"MGhwnfv_1105","clusterId":"@NSLB_CLUSTER_ID@","purpose":"NSLB负载均衡节点","description":"NSLB主机"}' NSLB_CLUSTER_ID "$NSLB_CLUSTER_ID")
RESP=$(post "/hosts" "$F")
created "NSLB host" NSLB_HOST_ID "host" "$RESP"

F=$(body '{"name":"GMDB","ip":"2409:808c:8a:109::40","port":22,"os":"Linux","username":"omm","authType":"password","credential":"MIGUJZBK#E*I3e8i","clusterId":"@GMDB_CLUSTER_ID@","purpose":"GMDB数据库节点","description":"GMDB主机"}' GMDB_CLUSTER_ID "$GMDB_CLUSTER_ID")
RESP=$(post "/hosts" "$F")
created "GMDB host" GMDB_HOST_ID "host" "$RESP"

F=$(body '{"name":"RCPADB","ip":"2409:808c:8a:109::39","port":22,"os":"Linux","username":"omm","authType":"password","credential":"MIGUJZBK#E*I3e8i","clusterId":"@RCPADB_CLUSTER_ID@","purpose":"RCPADB数据库节点","description":"RCPADB主机"}' RCPADB_CLUSTER_ID "$RCPADB_CLUSTER_ID")
RESP=$(post "/hosts" "$F")
created "RCPADB host" RCPADB_HOST_ID "host" "$RESP"

F=$(body '{"name":"GWDB","ip":"2409:808c:8a:109::39","port":22,"os":"Linux","username":"omm","authType":"password","credential":"MIGUJZBK#E*I3e8i","clusterId":"@GWDB_CLUSTER_ID@","purpose":"GWDB数据库节点","description":"GWDB主机"}' GWDB_CLUSTER_ID "$GWDB_CLUSTER_ID")
RESP=$(post "/hosts" "$F")
created "GWDB host" GWDB_HOST_ID "host" "$RESP"

# ── Step 5: Create host relations ─────────────────────────────────────────────
step 5 "Create host relations"

F=$(body '{"sourceHostId":"@NSLB_HOST_ID@","targetHostId":"@RCPA_HOST_ID@","description":"负载均衡转发"}' NSLB_HOST_ID "$NSLB_HOST_ID" RCPA_HOST_ID "$RCPA_HOST_ID")
RESP=$(post "/host-relations" "$F")
created "NSLB→RCPA" _ "relation" "$RESP"

F=$(body '{"sourceHostId":"@RCPA_HOST_ID@","targetHostId":"@GWDB_HOST_ID@","description":"读写网关数据库"}' RCPA_HOST_ID "$RCPA_HOST_ID" GWDB_HOST_ID "$GWDB_HOST_ID")
RESP=$(post "/host-relations" "$F")
created "RCPA→GWDB" _ "relation" "$RESP"

F=$(body '{"sourceHostId":"@RCPA_HOST_ID@","targetHostId":"@GMDB_HOST_ID@","description":"读写音乐数据库"}' RCPA_HOST_ID "$RCPA_HOST_ID" GMDB_HOST_ID "$GMDB_HOST_ID")
RESP=$(post "/host-relations" "$F")
created "RCPA→GMDB" _ "relation" "$RESP"

F=$(body '{"sourceHostId":"@RCPA_HOST_ID@","targetHostId":"@RCPADB_HOST_ID@","description":"读写RCPA自身数据库"}' RCPA_HOST_ID "$RCPA_HOST_ID" RCPADB_HOST_ID "$RCPADB_HOST_ID")
RESP=$(post "/host-relations" "$F")
created "RCPA→RCPADB" _ "relation" "$RESP"

# ── Step 6: Create business services under 典型业务 ────────────────────────────
step 6 "Create business services"

F=$(body '{"name":"彩铃查询","code":"color_ring_query","businessTypeId":"@BT_QUERY_ID@","groupId":"@BIZ_GROUP_ID@","hostIds":["@NSLB_HOST_ID@"],"priority":"P2","description":"彩铃查询业务，经NSLB负载分发到RCPA处理"}' BT_QUERY_ID "$BT_QUERY_ID" BIZ_GROUP_ID "$BIZ_GROUP_ID" NSLB_HOST_ID "$NSLB_HOST_ID")
RESP=$(post "/business-services" "$F")
created "彩铃查询 service" _ "businessService" "$RESP"

F=$(body '{"name":"彩铃上报","code":"color_ring_report","businessTypeId":"@BT_REPORT_ID@","groupId":"@BIZ_GROUP_ID@","hostIds":["@NSLB_HOST_ID@"],"priority":"P1","description":"彩铃上报业务，经NSLB分发到RCPA处理"}' BT_REPORT_ID "$BT_REPORT_ID" BIZ_GROUP_ID "$BIZ_GROUP_ID" NSLB_HOST_ID "$NSLB_HOST_ID")
RESP=$(post "/business-services" "$F")
created "彩铃上报 service" _ "businessService" "$RESP"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  Seed complete: $ok_count created, $fail_count failed"
echo "========================================"
echo ""
echo "Verify at: ${BASE%/gateway}/#/host-resource"
echo ""
echo "Created IDs:"
echo "  ROOT GROUP = $ROOT_GROUP_ID"
echo "  SYS  GROUP = $SYS_GROUP_ID"
echo "  BIZ  GROUP = $BIZ_GROUP_ID"
echo "  RCPA host  = $RCPA_HOST_ID"
echo "  NSLB host  = $NSLB_HOST_ID"
echo "  GMDB host  = $GMDB_HOST_ID"
echo "  RCPADB host= $RCPADB_HOST_ID"
echo "  GWDB host  = $GWDB_HOST_ID"

if [ "$fail_count" -gt 0 ]; then
    exit 1
fi
