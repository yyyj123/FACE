#!/usr/bin/env sh
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/prod-common.sh"

: "${1:?Usage: prod-restore-drill.sh <backup-directory>}"
backup_dir=$1
[ -d "$backup_dir" ] || { echo "Backup directory does not exist: $backup_dir" >&2; exit 1; }
backup_dir=$(CDPATH= cd -- "$backup_dir" && pwd)
allowed_root=$(resolve_from_root "$FACE_BACKUP_DIR")
case "$backup_dir/" in "$allowed_root/"*) ;; *) echo "Restore source must stay under $allowed_root" >&2; exit 1 ;; esac
case "$PROJECT_NAME" in *-restore-drill) ;; *) echo "Restore drill project must end with -restore-drill." >&2; exit 1 ;; esac

for command in docker gzip sha256sum; do require_command "$command"; done
(cd "$backup_dir" && sha256sum -c SHA256SUMS)

cleanup() { compose down --volumes --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM
compose down --volumes --remove-orphans
compose up -d mysql object-store --wait --wait-timeout 600
gzip -dc "$backup_dir/database.sql.gz" | compose exec -T mysql sh -ec 'MYSQL_PWD="$(cat /run/secrets/db_root_password)" exec mysql -uroot face_salon'
compose up -d object-init

stamp=$(basename "$backup_dir")
compose --profile tools run --rm -e RESTORE_STAMP="$stamp" object-cli '
  mc alias set face http://object-store:9000 "$(cat /run/secrets/object_store_access_key)" "$(cat /run/secrets/object_store_secret_key)" >/dev/null
  mc mirror --overwrite "/backup/$RESTORE_STAMP/objects" "face/${OBJECT_STORAGE_BUCKET:-face-production}"
'
actual=$(compose exec -T mysql sh -ec 'MYSQL_PWD="$(cat /run/secrets/db_root_password)" mysql -N -B -uroot face_salon -e "SELECT CONCAT((SELECT COUNT(*) FROM account),CHAR(58),(SELECT COUNT(*) FROM appointment),CHAR(58),(SELECT COUNT(*) FROM mall_order),CHAR(58),(SELECT COUNT(*) FROM flyway_schema_history));"' | tr -d '\r')
expected=$(tr -d '\r\n' < "$backup_dir/database.fingerprint")
[ "$actual" = "$expected" ] || { echo "Restore fingerprint mismatch: $expected -> $actual" >&2; exit 1; }

echo "SC9_RESTORE_DRILL=PASS;FINGERPRINT=$actual"
