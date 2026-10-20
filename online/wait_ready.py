import json
import socket
import time
import urllib.request

for attempt in range(90):
    try:
        with socket.create_connection(('127.0.0.1', 14445), timeout=1):
            pass
        with urllib.request.urlopen('http://127.0.0.1:14446/IPLOCAL', timeout=1) as response:
            listing = json.load(response)
        if not listing or listing[0]['port'] != 14445:
            raise ValueError('Unexpected bootstrap response')
        with socket.create_connection(('127.0.0.1', 14447), timeout=1):
            pass
        print('LINUX_BETA_READY — game, bootstrap and owner console are listening.')
        break
    except (OSError, ValueError):
        time.sleep(1)
else:
    raise SystemExit('Server not ready. Run: bash /opt/nrolive-beta/status.sh')
