#!/usr/bin/env bash
set -euo pipefail
umask 077
stamp=$(date -u +%Y%m%dT%H%M%SZ)
output=/opt/nrolive-beta/backups/beta-$stamp.sql.gz
mariadb-dump --defaults-extra-file=/opt/nrolive-beta/local-data/client.ini --single-transaction --skip-lock-tables --no-tablespaces nrolive_beta | gzip > "$output.tmp"
mv "$output.tmp" "$output"
echo "Backup created: $output"
