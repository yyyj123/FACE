#!/usr/bin/env sh
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/prod-common.sh"

for command in docker openssl ss awk grep wc; do require_command "$command"; done
require_linux_engine
require_secret_files
mkdir -p "$FACE_ADAPTER_DIR" "$FACE_CHANNEL_EVIDENCE_DIR" "$FACE_BACKUP_DIR"

check_port_free "$FACE_HTTP_PORT"
check_port_free "$FACE_HTTPS_PORT"
check_port_free "$FACE_PROMETHEUS_PORT"
echo "SC9_PORT_PREFLIGHT=PASS;HTTP=$FACE_HTTP_PORT;HTTPS=$FACE_HTTPS_PORT;PROMETHEUS=$FACE_PROMETHEUS_PORT"

openssl x509 -in "$FACE_SECRET_DIR/tls_certificate.pem" -noout -checkend 604800 >/dev/null || {
  echo "TLS certificate is invalid or expires within seven days." >&2
  exit 1
}

compose config --quiet
compose up -d mysql object-store --wait --wait-timeout 600
compose --profile tools build migration
compose --profile tools run --rm migration flyway:migrate
compose --profile tools run --rm migration flyway:validate
compose up -d object-init
compose up -d --build --wait --wait-timeout 1200
"$SCRIPT_DIR/prod-go-no-go.sh"
echo "SC9_PRODUCTION_START=PASS"
