#!/usr/bin/env sh
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/prod-common.sh"

for command in docker curl openssl grep wc; do require_command "$command"; done
require_linux_engine
require_secret_files

config=$(compose config)
printf '%s' "$config" | grep -q 'SPRING_PROFILES_ACTIVE: prod' || { echo "NO-GO: prod profile is not active" >&2; exit 1; }
printf '%s' "$config" | grep -q 'FACE_PAYMENT_DEMO_MOCK_ENABLED: "false"' || { echo "NO-GO: DEMO_MOCK is not explicitly disabled" >&2; exit 1; }
if printf '%s' "$config" | grep -Eiq 'SPRING_PROFILES_ACTIVE:.*demo|SMS_MODE: demo|DEMO_MOCK_ENABLED: "true"'; then
  echo "NO-GO: production configuration contains DEMO fallback" >&2
  exit 1
fi

openssl x509 -in "$FACE_SECRET_DIR/tls_certificate.pem" -noout -checkend 604800 >/dev/null || { echo "NO-GO: TLS certificate expires too soon" >&2; exit 1; }
curl -kfsS --resolve "$FACE_DOMAIN:$FACE_HTTPS_PORT:127.0.0.1" "https://$FACE_DOMAIN:$FACE_HTTPS_PORT/healthz" >/dev/null
curl -kfsS --resolve "$FACE_DOMAIN:$FACE_HTTPS_PORT:127.0.0.1" "https://$FACE_DOMAIN:$FACE_HTTPS_PORT/client/" | grep -q '<div id="app"'
curl -kfsS --resolve "$FACE_DOMAIN:$FACE_HTTPS_PORT:127.0.0.1" "https://$FACE_DOMAIN:$FACE_HTTPS_PORT/admin/" | grep -q '<div id="app"'

readiness=$(compose exec -T backend curl --fail --silent http://127.0.0.1:8090/face-next/actuator/health/readiness)
printf '%s' "$readiness" | grep -q '"status":"UP"' || { echo "NO-GO: backend readiness is not UP" >&2; exit 1; }
curl -fsS "http://127.0.0.1:$FACE_PROMETHEUS_PORT/-/ready" >/dev/null
curl -fsS "http://127.0.0.1:$FACE_PROMETHEUS_PORT/api/v1/targets" | grep -q '"health":"up"'
curl -fsS "http://127.0.0.1:$FACE_PROMETHEUS_PORT/api/v1/rules" | grep -q 'FaceBackendDown'

echo "SC9_GO_NO_GO=PASS;PAYMENT=${FACE_PROD_PAYMENT_CHANNEL:-disabled};SMS=${SMS_MODE:-disabled};LOGISTICS=${FACE_PROD_LOGISTICS_CHANNEL:-disabled}"
