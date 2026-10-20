"""Real MariaDB + Linux JVM protocol test. Does not run the Windows GUI client."""
import importlib.util
import json
import os
import socket
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from configure import configure

root = Path(sys.argv[1]).resolve()
repo = Path(__file__).resolve().parents[1]
password = os.environ['NRO_DB_PASSWORD']
admin = os.environ['NRO_ADMIN_PASSWORD']
configure(root, '160.250.180.107', 13307)
defaults = '--defaults-file=' + str(root / 'local-data/client.ini')
def sql(statement):
    return subprocess.check_output(['mariadb', defaults, '-N', '-B', 'nrolive_beta', '-e', statement], text=True).strip()
sql("INSERT INTO account(username,password,active,server_login) VALUES ('tester','beta-fixture-password',1,1)")
env = dict(os.environ, NRO_PUBLIC_HOST='160.250.180.107', NRO_GAME_BIND='0.0.0.0')
processes, handles = [], []
def start(component, heap):
    out = (root / 'logs' / (component + '.log')).open('w')
    err = (root / 'logs' / (component + '-error.log')).open('w')
    handles.extend([out, err])
    proc = subprocess.Popen(['java', '-Dfile.encoding=UTF-8', '-Xms32m', '-Xmx'+heap, '-Xss256k', '-XX:ActiveProcessorCount=2', '-jar', 'app.jar'], cwd=root / component, env=env, stdout=out, stderr=err)
    processes.append(proc)
    return proc
def wait(port, proc):
    for _ in range(180):
        if proc.poll() is not None: raise RuntimeError(f'Process exited before port {port}: {proc.returncode}')
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=.3): return
        except OSError: time.sleep(.5)
    raise TimeoutError(port)
try:
    login = start('login', '96m'); wait(3105, login)
    game = start('server', '768m'); wait(14445, game); wait(14447, game)
    with urllib.request.urlopen('http://127.0.0.1:14446/IPLOCAL', timeout=5) as response:
        listing = json.load(response)
    assert listing[0]['address'] == '160.250.180.107'
    listeners = {}
    for line in Path('/proc/net/tcp').read_text().splitlines()[1:]:
        fields = line.split()
        if fields[3] == '0A':
            ip, port = fields[1].split(':'); listeners[int(port, 16)] = ip
    assert listeners[14445] == listeners[14446] == '00000000', listeners
    assert listeners[14447] == listeners[3105] == '0100007F', listeners
    print('PASS: game/bootstrap public IPv4 bind; owner/login remain loopback')
    spec = importlib.util.spec_from_file_location('protocol', repo / 'yanii/protocol_smoke.py')
    protocol = importlib.util.module_from_spec(spec); spec.loader.exec_module(protocol)
    c = protocol.Client(); c.login('beta-fixture-password'); c.until(lambda code, body: code == 2)
    c.send(-28, bytes([2]) + protocol.utf('pcprobe') + bytes([0, 64]))
    map_id, x, y = c.world()
    for delta in (8, 16, 24):
        import struct
        c.send(-7, bytes([0]) + struct.pack('>hh', x+delta, y)); time.sleep(.15)
    c.sock.close(); time.sleep(5)
    assert json.loads(sql("SELECT data_location FROM player WHERE name='pcprobe'"))[:3] == [x+24, y, map_id]
    assert json.loads(sql("SELECT data_task FROM player WHERE name='pcprobe'")) == [0, 0, 0]
    c = protocol.Client(); c.login('beta-fixture-password'); assert c.world()[0] == map_id; c.sock.close(); time.sleep(5)
    assert json.loads(sql("SELECT data_task FROM player WHERE name='pcprobe'")) == [0, 0, 0]
    print('PASS: Linux login, create, map, movement, quest zero and persistence across relogin')
    for proc in processes:
        status = Path(f'/proc/{proc.pid}/status').read_text()
        print('JVM memory:', next(line for line in status.splitlines() if line.startswith('VmRSS:')))
    (root / 'local-data/stop.request').write_text('stop')
    game.wait(timeout=75)
    assert game.returncode == 0, game.returncode
    print('PASS: Linux owner stop request shuts down game cleanly')
finally:
    for proc in reversed(processes):
        if proc.poll() is None: proc.terminate()
        try: proc.wait(timeout=10)
        except subprocess.TimeoutExpired: proc.kill(); proc.wait()
    for handle in handles: handle.close()
    for component in ('login', 'server'):
        path = root / 'logs' / (component + '-error.log')
        if path.exists(): print(component + ' stderr:\n' + path.read_text(errors='replace')[-8000:])
