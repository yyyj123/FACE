#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE=${FACE_ENV_FILE:-$ROOT/.env.prod}
PROJECT_NAME=${FACE_PROJECT_NAME:-face-production}
COMPOSE_FILE=$ROOT/docker-compose.prod.yml

case "$PROJECT_NAME" in
  face-prod-*|face-production) ;;
  *) echo "Project name must be face-production or start with face-prod-." >&2; exit 1 ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Production environment file is missing: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

: "${FACE_DOMAIN:?FACE_DOMAIN is required}"
: "${FACE_PUBLIC_ORIGIN:?FACE_PUBLIC_ORIGIN is required}"
: "${FACE_HTTP_PORT:?FACE_HTTP_PORT is required}"
: "${FACE_HTTPS_PORT:?FACE_HTTPS_PORT is required}"
: "${FACE_PROMETHEUS_PORT:?FACE_PROMETHEUS_PORT is required}"
: "${FACE_SECRET_DIR:?FACE_SECRET_DIR is required}"
: "${FACE_ADAPTER_DIR:?FACE_ADAPTER_DIR is required}"
: "${FACE_CHANNEL_EVIDENCE_DIR:?FACE_CHANNEL_EVIDENCE_DIR is required}"
: "${FACE_BACKUP_DIR:?FACE_BACKUP_DIR is required}"

resolve_from_root() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s/%s\n' "$ROOT" "${1#./}" ;;
  esac
}

FACE_SECRET_DIR=$(resolve_from_root "$FACE_SECRET_DIR")
FACE_ADAPTER_DIR=$(resolve_from_root "$FACE_ADAPTER_DIR")
FACE_CHANNEL_EVIDENCE_DIR=$(resolve_from_root "$FACE_CHANNEL_EVIDENCE_DIR")
FACE_BACKUP_DIR=$(resolve_from_root "$FACE_BACKUP_DIR")
export FACE_SECRET_DIR FACE_ADAPTER_DIR FACE_CHANNEL_EVIDENCE_DIR FACE_BACKUP_DIR

compose() {
  docker compose --project-name "$PROJECT_NAME" --project-directory "$ROOT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command is unavailable: $1" >&2; exit 1; }
}

require_linux_engine() {
  [ "$(docker info --format '{{.OSType}}')" = "linux" ] || {
    echo "SC9 production deployment requires a Linux Docker engine." >&2
    exit 1
  }
}

require_secret_files() {
  for name in db_password db_root_password object_store_access_key object_store_secret_key sms_http_bearer_token alert_webhook_url tls_certificate.pem tls_private_key.pem; do
    path=$FACE_SECRET_DIR/$name
    [ -s "$path" ] || { echo "Required production Secret is missing or empty: $path" >&2; exit 1; }
    case "$name" in
      tls_certificate.pem|tls_private_key.pem) ;;
      *)
        line_count=$(wc -l < "$path")
        if [ "$line_count" -ne 0 ] || LC_ALL=C grep -q '[[:cntrl:]]' "$path"; then
          echo "Secret must be a single value without control or newline bytes: $name" >&2
          exit 1
        fi
        ;;
    esac
  done
  if [ "${FACE_SC9_ACCEPTANCE_SELF_SIGNED:-false}" != "true" ]; then
    for name in db_password db_root_password object_store_access_key object_store_secret_key sms_http_bearer_token alert_webhook_url tls_private_key.pem; do
      mode=$(stat -c '%a' "$FACE_SECRET_DIR/$name")
      [ "$mode" = "600" ] || [ "$mode" = "400" ] || {
        echo "Secret file permissions must be 600 or 400: $name ($mode)" >&2
        exit 1
      }
    done
  fi
}

check_port_free() {
  port=$1
  if ss -ltnH 2>/dev/null | awk '{print $4}' | grep -Eq "(^|:)$port$"; then
    echo "Requested host port is occupied: $port. Automatic random selection is disabled." >&2
    exit 1
  fi
}
