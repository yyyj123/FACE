#!/usr/bin/env sh
set -eu
. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/prod-common.sh"

compose down --remove-orphans
echo "SC9_PRODUCTION_STOP=PASS"
