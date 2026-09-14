#!/usr/bin/env sh
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/prod-common.sh"

for command in docker gzip sha256sum date find sort xargs; do require_command "$command"; done
backup_root=$(resolve_from_root "$FACE_BACKUP_DIR")
allowed_root=$(resolve_from_root "backups")
case "$backup_root/" in "$allowed_root/"*) ;; *) echo "Backups must stay under $allowed_root" >&2; exit 1 ;; esac

stamp=$(date -u +%Y%m%dT%H%M%SZ)
target=$backup_root/$stamp
mkdir -p "$target/objects"

compose exec -T mysql sh -ec 'MYSQL_PWD="$(cat /run/secrets/db_root_password)" exec mysqldump --single-transaction --routines --triggers --no-tablespaces -uroot face_salon' | gzip -9 > "$target/database.sql.gz"
compose --profile tools run --rm -e BACKUP_STAMP="$stamp" object-cli '
  mc alias set face http://object-store:9000 "$(cat /run/secrets/object_store_access_key)" "$(cat /run/secrets/object_store_secret_key)" >/dev/null
  mc mirror --overwrite "face/${OBJECT_STORAGE_BUCKET:-face-production}" "/backup/$BACKUP_STAMP/objects"
'

compose exec -T mysql sh -ec 'MYSQL_PWD="$(cat /run/secrets/db_root_password)" mysql -N -B -uroot face_salon -e "SELECT CONCAT((SELECT COUNT(*) FROM account),CHAR(58),(SELECT COUNT(*) FROM appointment),CHAR(58),(SELECT COUNT(*) FROM mall_order),CHAR(58),(SELECT COUNT(*) FROM flyway_schema_history));"' > "$target/database.fingerprint"
printf 'backup_timestamp=%s\nproject=%s\n' "$stamp" "$PROJECT_NAME" > "$target/manifest.properties"
(cd "$target" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)

printf 'face_backup_last_success_unixtime %s\n' "$(date -u +%s)" | compose exec -T pushgateway wget -qO- --post-file=- http://127.0.0.1:9091/metrics/job/face-production-backup >/dev/null
echo "SC9_BACKUP=PASS;PATH=$target"
