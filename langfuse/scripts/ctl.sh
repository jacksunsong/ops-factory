#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Langfuse service control (Docker Compose)
#
# Usage: ./ctl.sh <action>
#   action: startup | shutdown | status | restart
#
# Configuration source: config.yaml > default
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(dirname "${SCRIPT_DIR}")"

# --- Configuration ---
COMPOSE_FILE="${SERVICE_DIR}/docker-compose.yml"

yaml_val() {
    local key="$1" file="${SERVICE_DIR}/config.yaml"
    [ -f "${file}" ] || return 0
    awk -F': ' -v k="${key}" '$1==k {print $2}' "${file}" | head -n1 | sed 's/^["'"'"']//;s/["'"'"']$//'
}

yaml_nested_val() {
    local section="$1" key="$2" file="${SERVICE_DIR}/config.yaml"
    [ -f "${file}" ] || return 0
    awk -F': ' -v section="${section}" -v key="${key}" '
      $0 ~ "^" section ":" { in_section=1; next }
      in_section && $0 ~ "^[^[:space:]]" { in_section=0 }
      in_section && $1 ~ "^[[:space:]]+" key "$" { print $2; exit }
    ' "${file}" | sed 's/^["'"'"']//;s/["'"'"']$//'
}

# --- Logging ---
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
log_fail()  { echo -e "${RED}[FAIL]${NC}  $1"; }

# --- Generate .env from config.yaml ---
generate_env_file() {
    local env_file="${SERVICE_DIR}/.env"
    local port pg_db pg_user pg_password pg_port nextauth_secret salt telemetry_enabled
    local init_org_id init_org_name init_project_id init_project_name init_project_public_key init_project_secret_key
    local init_user_email init_user_name init_user_password

    port="$(yaml_val port)"
    pg_db="$(yaml_nested_val postgres db)"
    pg_user="$(yaml_nested_val postgres user)"
    pg_password="$(yaml_nested_val postgres password)"
    pg_port="$(yaml_nested_val postgres port)"
    nextauth_secret="$(yaml_val nextauthSecret)"
    salt="$(yaml_val salt)"
    telemetry_enabled="$(yaml_val telemetryEnabled)"
    init_org_id="$(yaml_nested_val init orgId)"
    init_org_name="$(yaml_nested_val init orgName)"
    init_project_id="$(yaml_nested_val init projectId)"
    init_project_name="$(yaml_nested_val init projectName)"
    init_project_public_key="$(yaml_nested_val init projectPublicKey)"
    init_project_secret_key="$(yaml_nested_val init projectSecretKey)"
    init_user_email="$(yaml_nested_val init userEmail)"
    init_user_name="$(yaml_nested_val init userName)"
    init_user_password="$(yaml_nested_val init userPassword)"

    [ -n "${port}" ] || port="3100"
    [ -n "${pg_db}" ] || pg_db="langfuse"
    [ -n "${pg_user}" ] || pg_user="langfuse"
    [ -n "${pg_password}" ] || pg_password="langfuse"
    [ -n "${pg_port}" ] || pg_port="5432"
    [ -n "${nextauth_secret}" ] || nextauth_secret="opsfactory-langfuse-secret-key"
    [ -n "${salt}" ] || salt="opsfactory-langfuse-salt"
    [ -n "${telemetry_enabled}" ] || telemetry_enabled="false"
    [ -n "${init_org_id}" ] || init_org_id="opsfactory"
    [ -n "${init_org_name}" ] || init_org_name="ops-factory"
    [ -n "${init_project_id}" ] || init_project_id="opsfactory-agents"
    [ -n "${init_project_name}" ] || init_project_name="ops-factory-agents"
    [ -n "${init_project_public_key}" ] || init_project_public_key="pk-lf-opsfactory"
    [ -n "${init_project_secret_key}" ] || init_project_secret_key="sk-lf-opsfactory"
    [ -n "${init_user_email}" ] || init_user_email="admin@opsfactory.local"
    [ -n "${init_user_name}" ] || init_user_name="admin"
    [ -n "${init_user_password}" ] || init_user_password="opsfactory"

    cat > "${env_file}" <<EOF
LANGFUSE_PORT=${port}
POSTGRES_DB=${pg_db}
POSTGRES_USER=${pg_user}
POSTGRES_PASSWORD=${pg_password}
POSTGRES_PORT=${pg_port}
NEXTAUTH_SECRET=${nextauth_secret}
SALT=${salt}
TELEMETRY_ENABLED=${telemetry_enabled}
LANGFUSE_INIT_ORG_ID=${init_org_id}
LANGFUSE_INIT_ORG_NAME=${init_org_name}
LANGFUSE_INIT_PROJECT_ID=${init_project_id}
LANGFUSE_INIT_PROJECT_NAME=${init_project_name}
LANGFUSE_INIT_PROJECT_PUBLIC_KEY=${init_project_public_key}
LANGFUSE_INIT_PROJECT_SECRET_KEY=${init_project_secret_key}
LANGFUSE_INIT_USER_EMAIL=${init_user_email}
LANGFUSE_INIT_USER_NAME=${init_user_name}
LANGFUSE_INIT_USER_PASSWORD=${init_user_password}
EOF

    LANGFUSE_PORT="${port}"
}

# --- Utilities ---
wait_http_ok() {
    local name="$1" url="$2" attempts="${3:-60}" delay="${4:-1}"
    for ((i=1; i<=attempts; i++)); do
        curl -fsS "${url}" >/dev/null 2>&1 && return 0
        sleep "${delay}"
    done
    log_error "${name} health check failed: ${url}"
    return 1
}

# --- Langfuse actions ---
do_startup() {
    generate_env_file

    if docker ps --format '{{.Names}}' | grep -q '^langfuse$'; then
        log_info "Langfuse already running"
    else
        log_info "Starting Langfuse (port ${LANGFUSE_PORT})..."
        docker compose -f "${COMPOSE_FILE}" up -d
    fi

    log_info "Checking Langfuse readiness (timeout: 60s)..."
    if ! wait_http_ok "Langfuse" "http://127.0.0.1:${LANGFUSE_PORT}/api/public/health" 60 1; then
        log_error "Langfuse health check failed"
        return 1
    fi
    log_info "Langfuse ready at http://localhost:${LANGFUSE_PORT}"
}

do_shutdown() {
    if docker ps --format '{{.Names}}' | grep -q '^langfuse$'; then
        log_info "Stopping Langfuse..."
        docker compose -f "${COMPOSE_FILE}" down
    fi
}

do_status() {
    generate_env_file
    local port="${LANGFUSE_PORT}"
    if docker ps --format '{{.Names}}' | grep -q '^langfuse$'; then
        if curl -fsS "http://127.0.0.1:${port}/api/public/health" >/dev/null 2>&1; then
            log_ok "Langfuse running (http://localhost:${port})"
        else
            log_warn "Langfuse container running but health check failed"
            return 1
        fi
    else
        log_fail "Langfuse is not running"
        return 1
    fi
}

do_restart() {
    do_shutdown
    do_startup
}

# --- Main ---
usage() {
    cat <<EOF
Usage: $(basename "$0") <action>

Actions:
  startup     Start Langfuse (Docker Compose)
  shutdown    Stop Langfuse
  status      Check Langfuse status
  restart     Restart Langfuse
EOF
    exit 1
}

ACTION="${1:-}"
[ -z "${ACTION}" ] && usage

case "${ACTION}" in
    startup)  do_startup ;;
    shutdown) do_shutdown ;;
    status)   do_status ;;
    restart)  do_restart ;;
    -h|--help|help) usage ;;
    *) log_error "Unknown action: ${ACTION}"; usage ;;
esac
