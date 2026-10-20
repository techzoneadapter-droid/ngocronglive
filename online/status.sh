#!/usr/bin/env bash
set -u
systemctl --no-pager --full status nrolive-login nrolive-game
free -h
df -h /opt/nrolive-beta
ss -lntp | awk 'NR==1 || /:14445 |:14446 |:14447 |:3105 |:3306 /'
tail -n 25 /opt/nrolive-beta/logs/server-error.log
