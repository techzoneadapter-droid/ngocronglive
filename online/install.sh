#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run as root.'; exit 1; }
[[ $# -eq 1 && $1 =~ ^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$ ]] || { echo 'Usage: bash install.sh PUBLIC_IP_OR_HOST'; exit 1; }
source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
target=/opt/nrolive-beta
[[ -f "$source_dir/server/app.jar" && -f "$source_dir/server/seed.sql" && -f "$source_dir/login/app.jar" ]] || { echo 'Extract the entire server package first.'; exit 1; }
[[ ! -e "$target" && ! -e /etc/nrolive-beta.env ]] || { echo 'Installation already exists. Stop; preserve it for backup/update.'; exit 1; }
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jre-headless mariadb-server python3 ufw
systemctl enable --now mariadb
exists=$(mariadb -N -B -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='nrolive_beta'")
[[ $exists == 0 ]] || { echo 'Beta database already exists; refusing to replace it.'; exit 1; }
if ! id nrolive >/dev/null 2>&1; then useradd --system --home-dir "$target" --shell /usr/sbin/nologin nrolive; fi
install -d -m 750 -o nrolive -g nrolive "$target"
cp -a "$source_dir"/. "$target"/
install -d -m 750 -o nrolive -g nrolive "$target/local-data" "$target/logs" "$target/backups"
umask 077
NRO_DB_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
NRO_ADMIN_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
NRO_GAME_HEAP=768
NRO_MAX_PLAYERS=20
if [[ $(awk '/MemTotal/{print $2}' /proc/meminfo) -lt 1500000 ]]; then NRO_GAME_HEAP=384; NRO_MAX_PLAYERS=10; fi
export NRO_DB_PASSWORD NRO_MAX_PLAYERS
printf 'NRO_DB_PASSWORD=%s\nNRO_ADMIN_PASSWORD=%s\nNRO_PUBLIC_HOST=%s\nNRO_GAME_BIND=0.0.0.0\nNRO_GAME_HEAP=%s\n' "$NRO_DB_PASSWORD" "$NRO_ADMIN_PASSWORD" "$1" "$NRO_GAME_HEAP" > /etc/nrolive-beta.env
mariadb <<SQL
CREATE DATABASE nrolive_beta CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'nrolive'@'localhost' IDENTIFIED BY '$NRO_DB_PASSWORD';
ALTER USER 'nrolive'@'localhost' IDENTIFIED BY '$NRO_DB_PASSWORD';
GRANT ALL ON nrolive_beta.* TO 'nrolive'@'localhost';
SQL
mariadb --default-character-set=utf8mb4 nrolive_beta < "$target/server/seed.sql"
python3 "$target/configure.py" "$target" "$1"
chown -R nrolive:nrolive "$target"
chmod 750 "$target"/*.sh
chmod 640 "$target/server/resources/config/server.properties" "$target/login/server.ini"
# Fresh 1GB trial VPS needs a buffer for bursts; swap is not extra player capacity.
if [[ $(awk '/MemTotal/{print $2}' /proc/meminfo) -lt 1500000 ]] && [[ $(awk 'END{print NR}' /proc/swaps) -eq 1 ]]; then
    [[ ! -e /nrolive.swap ]] || { echo 'Existing /nrolive.swap requires manual review.'; exit 1; }
    fallocate -l 2G /nrolive.swap
    chmod 600 /nrolive.swap
    mkswap /nrolive.swap >/dev/null
    swapon /nrolive.swap
    printf '/nrolive.swap none swap sw 0 0\n' >> /etc/fstab
fi
cat > /etc/systemd/system/nrolive-login.service <<'UNIT'
[Unit]
Description=NgocRongLive beta login
After=network.target mariadb.service
Requires=mariadb.service
[Service]
User=nrolive
Group=nrolive
WorkingDirectory=/opt/nrolive-beta/login
EnvironmentFile=/etc/nrolive-beta.env
ExecStart=/usr/bin/java -Dfile.encoding=UTF-8 -Xms32m -Xmx96m -Xss256k -jar app.jar
Restart=on-failure
RestartSec=5
UMask=0027
StandardOutput=append:/opt/nrolive-beta/logs/login.log
StandardError=append:/opt/nrolive-beta/logs/login-error.log
[Install]
WantedBy=multi-user.target
UNIT
cat > /etc/systemd/system/nrolive-game.service <<'UNIT'
[Unit]
Description=NgocRongLive beta game
After=nrolive-login.service
Requires=nrolive-login.service
[Service]
User=nrolive
Group=nrolive
WorkingDirectory=/opt/nrolive-beta/server
EnvironmentFile=/etc/nrolive-beta.env
ExecStartPre=/usr/bin/rm -f /opt/nrolive-beta/local-data/stop.request
ExecStart=/usr/bin/java -Dfile.encoding=UTF-8 -Xms128m -Xmx${NRO_GAME_HEAP}m -Xss256k -XX:ActiveProcessorCount=2 -jar app.jar
ExecStop=/bin/bash /opt/nrolive-beta/stop.sh $MAINPID
TimeoutStopSec=90
Restart=on-failure
RestartSec=10
UMask=0027
StandardOutput=append:/opt/nrolive-beta/logs/server.log
StandardError=append:/opt/nrolive-beta/logs/server-error.log
[Install]
WantedBy=multi-user.target
UNIT
cat > /etc/systemd/system/nrolive-backup.service <<'UNIT'
[Unit]
Description=Backup NgocRongLive beta database
[Service]
Type=oneshot
User=nrolive
Group=nrolive
ExecStart=/bin/bash /opt/nrolive-beta/backup.sh
UNIT
cat > /etc/systemd/system/nrolive-backup.timer <<'UNIT'
[Unit]
Description=Daily NgocRongLive beta backup
[Timer]
OnCalendar=daily
Persistent=true
[Install]
WantedBy=timers.target
UNIT
cat > /etc/logrotate.d/nrolive-beta <<'ROTATE'
/opt/nrolive-beta/logs/*.log {
    daily
    rotate 7
    size 10M
    compress
    missingok
    notifempty
    copytruncate
    su nrolive nrolive
}
ROTATE
ssh_connection=${SSH_CONNECTION:-}
ssh_port=${ssh_connection##* }
[[ $ssh_port =~ ^[0-9]+$ ]] || ssh_port=22
ufw allow "$ssh_port/tcp"
ufw allow 14445/tcp
ufw allow 14446/tcp
ufw default deny incoming
ufw default allow outgoing
ufw --force enable
systemctl daemon-reload
systemctl enable --now nrolive-login nrolive-game nrolive-backup.timer
echo 'Installed. Wait for game startup, then run: bash /opt/nrolive-beta/status.sh'
echo 'Owner console uses SSH tunnel only; read /etc/nrolive-beta.env locally for its password.'
