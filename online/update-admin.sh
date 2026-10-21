#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run as root.'; exit 1; }
source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
target=/opt/nrolive-beta
[[ -f "$target/server/app.jar" && -f /etc/nrolive-beta.env ]] || { echo 'Existing beta install not found. Stop.'; exit 1; }
(cd "$source_dir" && sha256sum -c SHA256SUMS)
[[ -f "$source_dir/app.jar" ]] || exit 1
stamp=$(date -u +%Y%m%dT%H%M%SZ)-$$
backup="$target/backups/admin-$stamp.jar"
# Stop gracefully before the consistent database snapshot. No seed/config/credential replacement.
systemctl stop nrolive-game
if ! runuser -u nrolive -- bash "$target/backup.sh"; then systemctl start nrolive-game; echo 'Backup failed; update cancelled.'; exit 1; fi
cp -p "$target/server/app.jar" "$backup"
install -o nrolive -g nrolive -m 640 "$source_dir/app.jar" "$target/server/app.jar"
systemctl start nrolive-game
if ! python3 "$target/wait_ready.py"; then
    systemctl stop nrolive-game
    cp -p "$backup" "$target/server/app.jar"
    systemctl start nrolive-game
    echo 'New build did not become ready; previous JAR restored. Additive admin tables retained.'
    exit 1
fi
echo 'ADMIN_UPDATE_READY. Accounts, players, passwords, resources and server settings preserved.'
echo "Previous server JAR: $backup"
